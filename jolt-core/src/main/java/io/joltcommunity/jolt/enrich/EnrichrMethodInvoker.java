/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.joltcommunity.jolt.enrich;

import io.joltcommunity.jolt.exception.SpecException;
import io.joltcommunity.jolt.exception.TransformException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class EnrichrMethodInvoker {

    private final Method method;
    private final Object target;
    private final String methodName;
    private final String contextKey;
    private final int index;
    private final Map<Class<?>, Method> contextMethodCache;

    EnrichrMethodInvoker( String methodName, String contextKey, String className, int index ) {
        this.methodName = methodName;
        this.index = index;
        this.contextKey = contextKey;
        this.contextMethodCache = new ConcurrentHashMap<>();

        if ( className == null && contextKey == null ) {
            throw new SpecException( "Enrichr enrichment at index:" + index + " requires either 'className' or 'contextKey'." );
        }
        if ( className != null && contextKey != null ) {
            throw new SpecException( "Enrichr enrichment at index:" + index + " supports only one of 'className' or 'contextKey'." );
        }

        if ( className == null ) {
            method = null;
            target = null;
            return;
        }

        try {
            Class<?> clazz = Class.forName( className );
            method = findMethod( clazz, this.methodName, index );

            if ( java.lang.reflect.Modifier.isStatic( method.getModifiers() ) ) {
                target = null;
            }
            else {
                target = clazz.getDeclaredConstructor().newInstance();
            }
        }
        catch ( SpecException e ) {
            throw e;
        }
        catch ( Exception e ) {
            throw new SpecException( "Enrichr could not initialize enrichment at index:" + index + ".", e );
        }
    }

    CompletionStage<Object> invokeAsync( Object value, Object input, Map<String, Object> context ) {
        try {
            InvocationTarget invocationTarget = resolveInvocationTarget( context );
            Class<?>[] parameterTypes = invocationTarget.method.getParameterTypes();
            Object invocationResult;

            if ( parameterTypes.length == 1 ) {
                invocationResult = invocationTarget.method.invoke( invocationTarget.target, value );
            }
            else if ( parameterTypes.length == 2 ) {
                invocationResult = invocationTarget.method.invoke( invocationTarget.target, value, input );
            }
            else {
                invocationResult = invocationTarget.method.invoke( invocationTarget.target, value, input, context );
            }

            return toCompletionStage( invocationResult );
        }
        catch ( IllegalAccessException | InvocationTargetException e ) {
            Throwable cause = e instanceof InvocationTargetException ? ( (InvocationTargetException) e ).getCause() : e;
            throw new TransformException( "Enrichr failed invoking " + methodName + ".", cause );
        }
    }

    private InvocationTarget resolveInvocationTarget( Map<String, Object> context ) {
        if ( method != null ) {
            return new InvocationTarget( method, target );
        }

        if ( context == null ) {
            throw new TransformException( "Enrichr could not resolve contextKey '" + contextKey + "' because transform context is null." );
        }

        Object contextTarget = context.get( contextKey );
        if ( contextTarget == null ) {
            throw new TransformException( "Enrichr could not resolve contextKey '" + contextKey + "' at index:" + index + "." );
        }

        Method contextMethod = contextMethodCache.get( contextTarget.getClass() );
        if ( contextMethod == null ) {
            contextMethod = findMethod( contextTarget.getClass(), methodName, index );
            contextMethodCache.put( contextTarget.getClass(), contextMethod );
        }

        return new InvocationTarget( contextMethod, contextTarget );
    }

    @SuppressWarnings( "unchecked" )
    private static CompletionStage<Object> toCompletionStage( Object invocationResult ) {
        if ( invocationResult == null ) {
            return CompletableFuture.completedFuture( null );
        }
        if ( invocationResult instanceof CompletionStage ) {
            return (CompletionStage<Object>) invocationResult;
        }
        if ( invocationResult instanceof Publisher ) {
            return publisherToCompletionStage( (Publisher<?>) invocationResult );
        }
        return CompletableFuture.completedFuture( invocationResult );
    }

    private static CompletionStage<Object> publisherToCompletionStage( Publisher<?> publisher ) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        publisher.subscribe( new Subscriber<Object>() {
            private Subscription subscription;
            private boolean hasValue;
            private Object value;

            @Override
            public void onSubscribe( Subscription subscription ) {
                this.subscription = subscription;
                subscription.request( Long.MAX_VALUE );
            }

            @Override
            public void onNext( Object nextValue ) {
                if ( hasValue ) {
                    subscription.cancel();
                    future.completeExceptionally( new TransformException( "Enrichr reactive enrichments must emit at most one value." ) );
                    return;
                }

                hasValue = true;
                value = nextValue;
            }

            @Override
            public void onError( Throwable throwable ) {
                future.completeExceptionally( throwable );
            }

            @Override
            public void onComplete() {
                future.complete( value );
            }
        } );
        return future;
    }

    private static Method findMethod( Class<?> clazz, String methodName, int index ) {
        for ( Method candidate : clazz.getMethods() ) {
            if ( ! candidate.getName().equals( methodName ) ) {
                continue;
            }

            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if ( parameterTypes.length < 1 || parameterTypes.length > 3 ) {
                continue;
            }
            if ( parameterTypes.length >= 2 && ! Object.class.isAssignableFrom( parameterTypes[1] ) ) {
                continue;
            }
            if ( parameterTypes.length == 3 && ! Map.class.isAssignableFrom( parameterTypes[2] ) ) {
                continue;
            }

            return candidate;
        }

        throw new SpecException(
                "Enrichr could not find a public method named '" + methodName + "' on " + clazz.getName() +
                        " with signature (Object), (Object,Object), or (Object,Object,Map) at index:" + index + "."
        );
    }

    private static final class InvocationTarget {
        private final Method method;
        private final Object target;

        private InvocationTarget( Method method, Object target ) {
            this.method = method;
            this.target = target;
        }
    }
}
