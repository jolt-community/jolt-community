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

import io.joltcommunity.jolt.enrich.EnrichrExecutionMode;
import io.joltcommunity.jolt.enrich.EnrichrManager;
import io.joltcommunity.jolt.enrich.EnrichrPendingEnrichment;
import io.joltcommunity.jolt.exception.SpecException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final List<EnrichrManager> enrichments;
    private final EnrichrExecutionMode executionMode;

    @SuppressWarnings( "unchecked" )
    public Enrichr( Object spec ) {
        if ( spec == null ) {
            throw new SpecException( "Enrichr expected a spec of Map type, got 'null'." );
        }
        if ( ! ( spec instanceof Map ) ) {
            throw new SpecException( "Enrichr expected a spec of Map type, got " + spec.getClass().getSimpleName() );
        }

        Map<String, Object> enrichrSpec = (Map<String, Object>) spec;
        executionMode = EnrichrExecutionMode.fromSpec( enrichrSpec.get( EXECUTION_MODE_KEY ) );

        Object enrichmentsObj = enrichrSpec.get( ENRICHMENTS_KEY );
        if ( ! ( enrichmentsObj instanceof List ) ) {
            throw new SpecException( "Enrichr expected '" + ENRICHMENTS_KEY + "' to be a List." );
        }

        List<EnrichrManager> parsed = new ArrayList<>();
        List<Object> specs = (List<Object>) enrichmentsObj;
        for ( int i = 0; i < specs.size(); i++ ) {
            parsed.add( new EnrichrManager( specs.get( i ), i ) );
        }
        if ( parsed.isEmpty() ) {
            throw new SpecException( "Enrichr requires at least one enrichment rule." );
        }

        enrichments = Collections.unmodifiableList( parsed );
    }

    @Override
    public Object transform( Object input, Map<String, Object> context ) {
        if ( executionMode == EnrichrExecutionMode.ASYNC ) {
            List<EnrichrPendingEnrichment> pendingEnrichments = new ArrayList<>();
            for ( EnrichrManager enrichment : enrichments ) {
                EnrichrPendingEnrichment pendingEnrichment = enrichment.prepare( input, context );
                if ( pendingEnrichment != null ) {
                    pendingEnrichments.add( pendingEnrichment );
                }
            }

            for ( EnrichrPendingEnrichment pendingEnrichment : pendingEnrichments ) {
                pendingEnrichment.apply();
            }
            return input;
        }

        for ( EnrichrManager enrichment : enrichments ) {
            EnrichrPendingEnrichment pendingEnrichment = enrichment.prepare( input, context );
            if ( pendingEnrichment != null ) {
                pendingEnrichment.apply();
            }
        }
        return input;
    }
}
