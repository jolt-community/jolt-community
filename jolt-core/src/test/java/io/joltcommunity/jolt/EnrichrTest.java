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

import io.joltcommunity.jolt.chainr.spec.ChainrEntry;
import io.joltcommunity.jolt.enrich.EnrichrTestHelper;
import io.joltcommunity.jolt.exception.SpecException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnrichrTest {

    @Test( expectedExceptions = SpecException.class )
    public void enrich_itRejectsNullSpec() {
        new Enrichr( null );
    }

    @Test( expectedExceptions = SpecException.class )
    public void enrich_itRejectsNonMapSpec() {
        new Enrichr( "not-a-map" );
    }

    @Test( expectedExceptions = SpecException.class )
    public void enrich_itRejectsNonListEnrichments() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put( "enrichments", "not-a-list" );
        new Enrichr( spec );
    }

    @Test( expectedExceptions = SpecException.class )
    public void enrich_itRejectsBlankExecutionMode() {
        new Enrichr( newEnrichSpec( "   ", enrichmentRule( "name", null, "uppercase" ) ) );
    }

    @Test( expectedExceptions = SpecException.class )
    public void enrich_itRejectsNonStringExecutionMode() {
        Map<String, Object> spec = newEnrichSpec( null, enrichmentRule( "name", null, "uppercase" ) );
        spec.put( "executionMode", Boolean.TRUE );
        new Enrichr( spec );
    }

    @Test
    public void enrich_itOverwritesTheSourceField() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "name", "alice" );

        Chainr chainr = Chainr.fromSpec( newChainrSpec( null, enrichmentRule( "name", null, "uppercase" ) ) );

        Object output = chainr.transform( input, null );

        Assert.assertSame( output, input );
        Assert.assertEquals( input.get( "name" ), "ALICE" );
    }

    @Test
    public void enrich_itCanWriteToAnotherFieldAndUseContext() {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put( "id", "cust-123" );

        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "customer", customer );

        Map<String, Object> context = new LinkedHashMap<>();
        context.put( "tenant", "acme" );

        Chainr chainr = Chainr.fromSpec( newChainrSpec( null, enrichmentRule( "customer.id", "customer.profile", "describe" ) ) );

        Object output = chainr.transform( input, context );

        Assert.assertSame( output, input );

        @SuppressWarnings( "unchecked" )
        Map<String, Object> profile = (Map<String, Object>) customer.get( "profile" );
        Assert.assertEquals( profile.get( "original" ), "cust-123" );
        Assert.assertEquals( profile.get( "inputType" ), "LinkedHashMap" );
        Assert.assertEquals( profile.get( "tenant" ), "acme" );
    }

    @Test( expectedExceptions = SpecException.class )
    public void enrich_itRejectsMissingEnrichments() {
        new Enrichr( newEnrichSpec( null ) );
    }

    @Test
    public void enrich_itCanUseATargetResolvedFromContext() {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put( "id", "cust-123" );

        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "customer", customer );

        Map<String, Object> context = new LinkedHashMap<>();
        context.put( "tenant", "acme" );
        context.put( "lookupBean", new EnrichrTestHelper() );

        Chainr chainr = Chainr.fromSpec( newChainrSpec( null, contextEnrichmentRule( "customer.id", "customer.profile", "lookupBean", "describeViaBean" ) ) );

        Object output = chainr.transform( input, context );

        Assert.assertSame( output, input );

        @SuppressWarnings( "unchecked" )
        Map<String, Object> profile = (Map<String, Object>) customer.get( "profile" );
        Assert.assertEquals( profile.get( "original" ), "cust-123" );
        Assert.assertEquals( profile.get( "tenant" ), "acme" );
    }

    @Test
    public void enrich_itResolvesCompletionStageResults() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "name", "alice" );

        Chainr chainr = Chainr.fromSpec( newChainrSpec( "sync", enrichmentRule( "name", null, "asyncUppercase" ) ) );

        Object output = chainr.transform( input, null );

        Assert.assertSame( output, input );
        Assert.assertEquals( input.get( "name" ), "ALICE" );
    }

    @Test
    public void enrich_itResolvesPublisherResultsInAsyncMode() {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put( "id", "cust-123" );

        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "customer", customer );

        Map<String, Object> context = new LinkedHashMap<>();
        context.put( "tenant", "acme" );

        Chainr chainr = Chainr.fromSpec( newChainrSpec( "async", enrichmentRule( "customer.id", "customer.profile", "publisherDescribe" ) ) );

        Object output = chainr.transform( input, context );

        Assert.assertSame( output, input );

        @SuppressWarnings( "unchecked" )
        Map<String, Object> profile = (Map<String, Object>) customer.get( "profile" );
        Assert.assertEquals( profile.get( "original" ), "cust-123" );
        Assert.assertEquals( profile.get( "tenant" ), "acme" );
    }

    @Test
    public void enrich_itIgnoresMissingPathsInSyncMode() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "name", "alice" );

        Chainr chainr = Chainr.fromSpec( newChainrSpec( "sync", enrichmentRule( "customer.id", "customer.profile", "describe" ) ) );

        Object output = chainr.transform( input, null );

        Assert.assertSame( output, input );
        Assert.assertEquals( input.size(), 1 );
        Assert.assertFalse( input.containsKey( "customer" ) );
    }

    @Test
    public void enrich_itIgnoresMissingPathsInAsyncMode() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "name", "alice" );

        Chainr chainr = Chainr.fromSpec( newChainrSpec( "async", enrichmentRule( "customer.id", "customer.profile", "publisherDescribe" ) ) );

        Object output = chainr.transform( input, null );

        Assert.assertSame( output, input );
        Assert.assertEquals( input.size(), 1 );
        Assert.assertFalse( input.containsKey( "customer" ) );
    }

    @Test( expectedExceptions = SpecException.class )
    public void enrich_itRejectsUnsupportedExecutionMode() {
        Chainr.fromSpec( newChainrSpec( "parallel", enrichmentRule( "name", null, "uppercase" ) ) );
    }

    @SafeVarargs
    private final Map<String, Object> newEnrichSpec( String executionMode, Map<String, Object>... rules ) {
        Map<String, Object> enrichSpec = new LinkedHashMap<>();
        if ( executionMode != null ) {
            enrichSpec.put( "executionMode", executionMode );
        }

        List<Map<String, Object>> enrichments = new ArrayList<>();
        for ( Map<String, Object> rule : rules ) {
            enrichments.add( rule );
        }
        enrichSpec.put( "enrichments", enrichments );
        return enrichSpec;
    }

    private List<Map<String, Object>> newChainrSpec( String executionMode, Map<String, Object> rule ) {
        List<Map<String, Object>> spec = new ArrayList<>();
        Map<String, Object> enrichOperation = new LinkedHashMap<>();
        enrichOperation.put( ChainrEntry.OPERATION_KEY, "enrich" );

        enrichOperation.put( ChainrEntry.SPEC_KEY, newEnrichSpec( executionMode, rule ) );
        spec.add( enrichOperation );
        return spec;
    }

    private Map<String, Object> enrichmentRule( String path, String outputPath, String methodName ) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put( "path", path );
        if ( outputPath != null ) {
            rule.put( "outputPath", outputPath );
        }
        rule.put( "className", EnrichrTestHelper.class.getName() );
        rule.put( "method", methodName );
        return rule;
    }

    private Map<String, Object> contextEnrichmentRule( String path, String outputPath, String contextKey, String methodName ) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put( "path", path );
        if ( outputPath != null ) {
            rule.put( "outputPath", outputPath );
        }
        rule.put( "contextKey", contextKey );
        rule.put( "method", methodName );
        return rule;
    }
}
