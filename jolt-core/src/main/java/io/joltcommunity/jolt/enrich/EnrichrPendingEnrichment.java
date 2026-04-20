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

import io.joltcommunity.jolt.exception.TransformException;
import io.joltcommunity.jolt.traversr.SimpleTraversr;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class EnrichrPendingEnrichment {

    private final Object input;
    private final SimpleTraversr<Object> outputTraversr;
    private final List<String> outputKeys;
    private final CompletionStage<Object> enrichedValueStage;
    private final String outputPath;

    EnrichrPendingEnrichment(
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

    public void apply() {
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
