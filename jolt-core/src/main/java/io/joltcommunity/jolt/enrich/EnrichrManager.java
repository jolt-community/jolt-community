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

import io.joltcommunity.jolt.common.Optional;
import io.joltcommunity.jolt.exception.SpecException;
import io.joltcommunity.jolt.traversr.SimpleTraversr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class EnrichrManager {

    private final String outputPath;
    private final EnrichrMethodInvoker invoker;
    private final SimpleTraversr<Object> inputTraversr;
    private final SimpleTraversr<Object> outputTraversr;
    private final List<String> inputKeys;
    private final List<String> outputKeys;

    @SuppressWarnings( "unchecked" )
    public EnrichrManager( Object spec, int index ) {
        if ( ! ( spec instanceof Map ) ) {
            throw new SpecException( "Enrichr enrichment at index:" + index + " must be a Map." );
        }

        Map<String, Object> rule = (Map<String, Object>) spec;
        String path = requiredString( rule, "path", index );
        outputPath = optionalString( rule, "outputPath", path );

        String methodName = requiredString( rule, "method", index );
        String contextKey = optionalString( rule, "contextKey", null );
        String className = optionalString( rule, "className", null );

        inputTraversr = new SimpleTraversr<>( path );
        outputTraversr = new SimpleTraversr<>( outputPath );
        inputKeys = toTraversrKeys( path );
        outputKeys = toTraversrKeys( outputPath );
        invoker = new EnrichrMethodInvoker( methodName, contextKey, className, index );
    }

    public EnrichrPendingEnrichment prepare( Object input, Map<String, Object> context ) {
        Optional<Object> inputValue = inputTraversr.get( input, inputKeys );

        if ( ! inputValue.isPresent() ) {
            return null;
        }

        CompletionStage<Object> enrichedValueStage = invoker.invokeAsync( inputValue.get(), input, context );
        return new EnrichrPendingEnrichment( input, outputTraversr, outputKeys, enrichedValueStage, outputPath );
    }

    static String requiredString( Map<String, Object> spec, String key, int index ) {
        Object value = spec.get( key );
        if ( ! ( value instanceof String ) || ( (String) value ).trim().isEmpty() ) {
            throw new SpecException( "Enrichr enrichment at index:" + index + " requires a non-blank '" + key + "'." );
        }
        return ( (String) value ).trim();
    }

    static String optionalString( Map<String, Object> spec, String key, String defaultValue ) {
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
