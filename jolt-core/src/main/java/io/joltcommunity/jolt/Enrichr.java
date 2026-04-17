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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Enrich fields in a JSON document by invoking user supplied Java methods.
 *
 * Spec shape:
 *
 * {
 *   "enrichments" : [
 *     {
 *       "path" : "customer.id",
 *       "className" : "com.acme.CustomerLookup",
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
 * Methods may be static or instance methods with a public no-arg constructor.
 */
public class Enrichr implements SpecDriven, ContextualTransform {

    private static final String ENRICHMENTS_KEY = "enrichments";
    private final List<Enrichment> enrichments;

    @SuppressWarnings( "unchecked" )
    public Enrichr( Object spec ) {
        if ( spec == null ) {
            throw new SpecException( "Enrichr expected a spec of Map type, got 'null'." );
        }
        if ( ! ( spec instanceof Map ) ) {
            throw new SpecException( "Enrichr expected a spec of Map type, got " + spec.getClass().getSimpleName() );
        }

        Object enrichmentsObj = ( (Map<String, Object>) spec ).get( ENRICHMENTS_KEY );
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
        for ( Enrichment enrichment : enrichments ) {
            enrichment.apply( input, context );
        }
        return input;
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
            invoker = new MethodInvoker(
                    requiredString( rule, "className", index ),
                    requiredString( rule, "method", index ),
                    index
            );
        }

        private void apply( Object input, Map<String, Object> context ) {
            Optional<Object> inputValue = inputTraversr.get( input, inputKeys );

            if ( ! inputValue.isPresent() ) {
                return;
            }

            Object enrichedValue = invoker.invoke( inputValue.get(), input, context );
            outputTraversr.set( input, outputKeys, enrichedValue );
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

        private MethodInvoker( String className, String methodName, int index ) {
            try {
                Class<?> clazz = Class.forName( className );
                method = findMethod( clazz, methodName, index );

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

        private Object invoke( Object value, Object input, Map<String, Object> context ) {
            try {
                Class<?>[] parameterTypes = method.getParameterTypes();

                if ( parameterTypes.length == 1 ) {
                    return method.invoke( target, value );
                }
                if ( parameterTypes.length == 2 ) {
                    return method.invoke( target, value, input );
                }
                return method.invoke( target, value, input, context );
            }
            catch ( IllegalAccessException | InvocationTargetException e ) {
                throw new TransformException( "Enrichr failed invoking " + method.getDeclaringClass().getName() + "#" + method.getName() + ".", e );
            }
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
    }
}
