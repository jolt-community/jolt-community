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
package io.joltcommunity.jolt;

import io.joltcommunity.jolt.common.Optional;
import io.joltcommunity.jolt.exception.SpecException;
import io.joltcommunity.jolt.exception.TransformException;
import io.joltcommunity.jolt.traversr.SimpleTraversr;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Enrich fields in a JSON document by invoking user supplied Java methods or context supplied beans.
 *
 * Spec shape:
 *
 * {
 *   "executionMode" : "sync", // optional, defaults to sync. "async" runs all enrichments concurrently.
 *   "enrichments" : [
 *     {
 *       "path" : "customer.id",
 *       "className" : "com.acme.CustomerLookup",
 *       "contextKey" : "customerLookup", // optional alternative to className, resolved from transform context
 *       "method" : "enrich",
 *       "outputPath" : "customer.details" // optional, defaults to path
 *     }
 *   ]
 * }
 *
 * Supported method signatures are:
 * - Object method( Object fieldValue )
 * - Object method( Object fieldValue, Object input )
 * - Object method( Object fieldValue, Object input, Map<String, Object> context )
 *
 * Supported return types are:
 * - Object
 * - CompletionStage<Object>
 * - Publisher<Object> such as a Reactor Mono returned by Spring WebFlux WebClient
 *
 * When className is used, methods may be static or instance methods with a public no-arg constructor.
 * When contextKey is used, the target instance is pulled from the supplied transform context.
 */
public class Enrichr implements SpecDriven, ContextualTransform {

    private static final String ENRICHMENTS_KEY = "enrichments";
    private static final String EXECUTION_MODE_KEY = "executionMode";
    private final List<Enrichment> enrichments;
    private final ExecutionMode executionMode;

    @SuppressWarnings( "unchecked" )
    public Enrichr( Object spec ) {
        if ( spec == null ) {
            throw new SpecException( "Enrichr expected a spec of Map type, got 'null'." );
        }
        if ( ! ( spec instanceof Map ) ) {
            throw new SpecException( "Enrichr expected a spec of Map type, got " + spec.getClass().getSimpleName() );
        }

        Map<String, Object> enrichrSpec = (Map<String, Object>) spec;
        executionMode = ExecutionMode.fromSpec( enrichrSpec.get( EXECUTION_MODE_KEY ) );

        Object enrichmentsObj = enrichrSpec.get( ENRICHMENTS_KEY );
        if ( ! ( enrichmentsObj instanceof List ) ) {
            throw new SpecException( "Enrichr expected '" + ENRICHMENTS_KEY + "' to be a List." );
        }

        List<Enrichment> parsed = new ArrayList<>();
        List<Object> specs = (List<Object>) enrichmentsObj;
        for ( int i = 0; i < specs.size(); i++ ) {
            parsed.add( new Enrichment( specs.get( i ), i ) );
        }
        if ( parsed.isEmpty() ) {
            throw new SpecException( "Enrichr requires at least one enrichment rule." );
        }

        enrichments = Collections.unmodifiableList( parsed );
    }

    @Override
    public Object transform( Object input, Map<String, Object> context ) {
        if ( executionMode == ExecutionMode.ASYNC ) {
            List<PendingEnrichment> pendingEnrichments = new ArrayList<>();
            for ( Enrichment enrichment : enrichments ) {
                PendingEnrichment pendingEnrichment = enrichment.prepare( input, context );
                if ( pendingEnrichment != null ) {
                    pendingEnrichments.add( pendingEnrichment );
                }
            }

            for ( PendingEnrichment pendingEnrichment : pendingEnrichments ) {
                pendingEnrichment.apply();
            }
            return input;
        }

        for ( Enrichment enrichment : enrichments ) {
            PendingEnrichment pendingEnrichment = enrichment.prepare( input, context );
            if ( pendingEnrichment != null ) {
                pendingEnrichment.apply();
            }
        }
        return input;
    }

    private enum ExecutionMode {
        SYNC,
        ASYNC;

        private static ExecutionMode fromSpec( Object rawValue ) {
            if ( rawValue == null ) {
                return SYNC;
            }
            if ( ! ( rawValue instanceof String ) ) {
                throw new SpecException( "Enrichr optional '" + EXECUTION_MODE_KEY + "' must be a String when provided." );
            }

            String normalizedValue = ( (String) rawValue ).trim();
            if ( normalizedValue.isEmpty() ) {
                throw new SpecException( "Enrichr optional '" + EXECUTION_MODE_KEY + "' must not be blank when provided." );
            }
            if ( "sync".equalsIgnoreCase( normalizedValue ) ) {
                return SYNC;
            }
            if ( "async".equalsIgnoreCase( normalizedValue ) ) {
                return ASYNC;
            }

            throw new SpecException( "Enrichr optional '" + EXECUTION_MODE_KEY + "' must be either 'sync' or 'async'." );
        }
    }

    private static final class Enrichment {

        private final String path;
        private final String outputPath;
        private final MethodInvoker invoker;
        private final SimpleTraversr<Object> inputTraversr;
        private final SimpleTraversr<Object> outputTraversr;
        private final List<String> inputKeys;
        private final List<String> outputKeys;

        @SuppressWarnings( "unchecked" )
        private Enrichment( Object spec, int index ) {
            if ( ! ( spec instanceof Map ) ) {
                throw new SpecException( "Enrichr enrichment at index:" + index + " must be a Map." );
            }

            Map<String, Object> rule = (Map<String, Object>) spec;
            path = requiredString( rule, "path", index );
            outputPath = optionalString( rule, "outputPath", path );

            inputTraversr = new SimpleTraversr<>( path );
            outputTraversr = new SimpleTraversr<>( outputPath );
            inputKeys = toTraversrKeys( path );
            outputKeys = toTraversrKeys( outputPath );
            invoker = new MethodInvoker( rule, index );
        }

        private PendingEnrichment prepare( Object input, Map<String, Object> context ) {
            Optional<Object> inputValue = inputTraversr.get( input, inputKeys );

            if ( ! inputValue.isPresent() ) {
                return null;
            }

            CompletionStage<Object> enrichedValueStage = invoker.invokeAsync( inputValue.get(), input, context );
            return new PendingEnrichment( input, outputTraversr, outputKeys, enrichedValueStage, outputPath );
        }

        private static String requiredString( Map<String, Object> spec, String key, int index ) {
            Object value = spec.get( key );
            if ( ! ( value instanceof String ) || ( (String) value ).trim().isEmpty() ) {
                throw new SpecException( "Enrichr enrichment at index:" + index + " requires a non-blank '" + key + "'." );
            }
            return ( (String) value ).trim();
        }

        private static String optionalString( Map<String, Object> spec, String key, String defaultValue ) {
            Object value = spec.get( key );
            if ( value == null ) {
                return defaultValue;
            }
            if ( ! ( value instanceof String ) || ( (String) value ).trim().isEmpty() ) {
                throw new SpecException( "Enrichr optional '" + key + "' must be a non-blank String when provided." );
            }
            return ( (String) value ).trim();
        }

        private static List<String> toTraversrKeys( String humanPath ) {
            String intermediatePath = humanPath.replace( "[", ".[" ).replace( "..", "." );
            if ( intermediatePath.charAt( 0 ) == '.' ) {
                intermediatePath = intermediatePath.substring( 1 );
            }

            String[] rawKeys = intermediatePath.split( "\\." );
            List<String> keys = new ArrayList<>( rawKeys.length );
            Collections.addAll( keys, rawKeys );
            return keys;
        }
    }

    private static final class MethodInvoker {

        private final Method method;
        private final Object target;
        private final String methodName;
        private final String contextKey;
        private final int index;
        private final Map<Class<?>, Method> contextMethodCache;

        private MethodInvoker( Map<String, Object> rule, int index ) {
            this.methodName = Enrichment.optionalString( rule, "method", null );
            if ( methodName == null ) {
                throw new SpecException( "Enrichr enrichment at index:" + index + " requires a non-blank 'method'." );
            }

            this.index = index;
            this.contextKey = Enrichment.optionalString( rule, "contextKey", null );
            this.contextMethodCache = new ConcurrentHashMap<>();

            String className = Enrichment.optionalString( rule, "className", null );
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

        private CompletionStage<Object> invokeAsync( Object value, Object input, Map<String, Object> context ) {
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

    private static final class PendingEnrichment {

        private final Object input;
        private final SimpleTraversr<Object> outputTraversr;
        private final List<String> outputKeys;
        private final CompletionStage<Object> enrichedValueStage;
        private final String outputPath;

        private PendingEnrichment(
                Object input,
                SimpleTraversr<Object> outputTraversr,
                List<String> outputKeys,
                CompletionStage<Object> enrichedValueStage,
                String outputPath
        ) {
            this.input = input;
            this.outputTraversr = outputTraversr;
            this.outputKeys = outputKeys;
            this.enrichedValueStage = enrichedValueStage;
            this.outputPath = outputPath;
        }

        private void apply() {
            Object enrichedValue = resolveValue( enrichedValueStage, outputPath );
            outputTraversr.set( input, outputKeys, enrichedValue );
        }

        private static Object resolveValue( CompletionStage<Object> enrichedValueStage, String outputPath ) {
            try {
                return enrichedValueStage.toCompletableFuture().get();
            }
            catch ( InterruptedException e ) {
                Thread.currentThread().interrupt();
                throw new TransformException( "Enrichr asynchronous enrichment was interrupted for outputPath '" + outputPath + "'.", e );
            }
            catch ( ExecutionException e ) {
                Throwable cause = e.getCause();
                if ( cause instanceof RuntimeException ) {
                    throw (RuntimeException) cause;
                }
                throw new TransformException( "Enrichr asynchronous enrichment failed for outputPath '" + outputPath + "'.", cause );
            }
        }
    }
}
