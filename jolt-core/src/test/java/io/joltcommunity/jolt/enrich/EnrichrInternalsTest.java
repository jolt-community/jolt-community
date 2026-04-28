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
import io.joltcommunity.jolt.traversr.SimpleTraversr;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class EnrichrInternalsTest {

    @Test( expectedExceptions = SpecException.class )
    public void executionMode_rejectsNonStringValues() {
        EnrichrExecutionMode.fromSpec( 1 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void executionMode_rejectsBlankValues() {
        EnrichrExecutionMode.fromSpec( "  " );
    }

    @Test( expectedExceptions = SpecException.class )
    public void manager_rejectsNonMapSpecs() {
        new EnrichrManager( "not-a-map", 0 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void manager_rejectsBlankRequiredStrings() {
        new EnrichrManager( rule( "   ", null, EnrichrTestHelper.class.getName(), null, "uppercase" ), 0 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void manager_rejectsNonStringRequiredStrings() {
        Map<String, Object> rule = rule( "name", null, EnrichrTestHelper.class.getName(), null, "uppercase" );
        rule.put( "path", 1 );

        new EnrichrManager( rule, 0 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void manager_rejectsBlankOptionalStrings() {
        new EnrichrManager( rule( "name", "   ", EnrichrTestHelper.class.getName(), null, "uppercase" ), 0 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void manager_rejectsNonStringOptionalStrings() {
        Map<String, Object> rule = rule( "name", null, EnrichrTestHelper.class.getName(), null, "uppercase" );
        rule.put( "outputPath", 1 );

        new EnrichrManager( rule, 0 );
    }

    @Test
    public void manager_returnsNullWhenTheSourcePathIsMissing() {
        EnrichrManager manager = new EnrichrManager( rule( "customer.id", null, EnrichrTestHelper.class.getName(), null, "uppercase" ), 0 );

        Assert.assertTrue( manager.match( new LinkedHashMap<>() ).isEmpty() );
    }

    @Test
    public void manager_supportsLeadingDotPaths() {
        EnrichrManager manager = new EnrichrManager( rule( ".name", null, EnrichrTestHelper.class.getName(), null, "uppercase" ), 0 );
        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "name", "alice" );

        List<EnrichrPathMatch> matches = manager.match( input );

        Assert.assertEquals( matches.size(), 1 );
        Assert.assertNotNull( manager.prepare( matches.get( 0 ), input, null ) );
    }

    @Test( expectedExceptions = SpecException.class )
    public void methodInvoker_requiresEitherAClassNameOrContextKey() {
        new EnrichrMethodInvoker( "uppercase", null, null, 0 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void methodInvoker_rejectsClassNameAndContextKeyTogether() {
        new EnrichrMethodInvoker( "uppercase", "bean", EnrichrTestHelper.class.getName(), 0 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void methodInvoker_wrapsClassLoadingFailures() {
        new EnrichrMethodInvoker( "uppercase", null, "does.not.Exist", 0 );
    }

    @Test( expectedExceptions = SpecException.class )
    public void methodInvoker_rejectsUnsupportedMethodSignatures() {
        new EnrichrMethodInvoker( "invalid", null, InvalidSignatureHelper.class.getName(), 0 );
    }

    @Test
    public void methodInvoker_supportsInstanceMethodsLoadedByClassName() throws Exception {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "instanceUppercase", null, InstanceHelper.class.getName(), 0 );

        Assert.assertEquals( invoker.invokeAsync( "alice", null, null ).toCompletableFuture().get(), "ALICE" );
    }

    @Test
    public void methodInvoker_supportsTwoArgumentMethods() throws Exception {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "appendInputSuffix", null, StaticHelper.class.getName(), 0 );
        Map<String, Object> input = new LinkedHashMap<>();
        input.put( "suffix", "tail" );

        Assert.assertEquals( invoker.invokeAsync( "alice", input, null ).toCompletableFuture().get(), "alice-tail" );
    }

    @Test
    public void methodInvoker_convertsNullResultsToCompletedStages() throws Exception {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "returnNull", null, StaticHelper.class.getName(), 0 );

        Assert.assertNull( invoker.invokeAsync( "alice", null, null ).toCompletableFuture().get() );
    }

    @Test
    public void methodInvoker_rejectsNullContextForContextBeans() {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "instanceUppercase", "bean", null, 0 );

        try {
            invoker.invokeAsync( "alice", null, null );
            Assert.fail( "Expected a TransformException" );
        }
        catch ( TransformException e ) {
            Assert.assertTrue( e.getMessage().contains( "transform context is null" ) );
        }
    }

    @Test
    public void methodInvoker_rejectsMissingContextTargets() {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "instanceUppercase", "bean", null, 0 );

        try {
            invoker.invokeAsync( "alice", null, new LinkedHashMap<>() );
            Assert.fail( "Expected a TransformException" );
        }
        catch ( TransformException e ) {
            Assert.assertTrue( e.getMessage().contains( "contextKey 'bean'" ) );
        }
    }

    @Test
    public void methodInvoker_reusesCachedContextMethods() throws Exception {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "instanceUppercase", "bean", null, 0 );
        Map<String, Object> context = new LinkedHashMap<>();
        context.put( "bean", new InstanceHelper() );

        Assert.assertEquals( invoker.invokeAsync( "alice", null, context ).toCompletableFuture().get(), "ALICE" );
        Assert.assertEquals( invoker.invokeAsync( "bob", null, context ).toCompletableFuture().get(), "BOB" );
    }

    @Test
    public void methodInvoker_wrapsInvocationFailures() {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "explode", null, StaticHelper.class.getName(), 0 );

        try {
            invoker.invokeAsync( "alice", null, null );
            Assert.fail( "Expected a TransformException" );
        }
        catch ( TransformException e ) {
            Assert.assertTrue( e.getCause() instanceof IllegalStateException );
        }
    }

    @Test
    public void methodInvoker_wrapsIllegalAccessFailures() {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker(
                "inaccessibleStatic",
                null,
                "io.joltcommunity.jolt.InaccessibleEnrichrMethodHelper",
                0
        );

        try {
            invoker.invokeAsync( "alice", null, null );
            Assert.fail( "Expected a TransformException" );
        }
        catch ( TransformException e ) {
            Assert.assertTrue( e.getCause() instanceof IllegalAccessException );
        }
    }

    @Test
    public void methodInvoker_rejectsPublishersThatEmitMoreThanOneValue() throws Exception {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "multiValuePublisher", null, StaticHelper.class.getName(), 0 );

        try {
            invoker.invokeAsync( "alice", null, null ).toCompletableFuture().get();
            Assert.fail( "Expected a failure from the multi-value publisher" );
        }
        catch ( ExecutionException e ) {
            Assert.assertTrue( e.getCause() instanceof TransformException );
            Assert.assertTrue( e.getCause().getMessage().contains( "at most one value" ) );
        }
    }

    @Test
    public void methodInvoker_surfacesPublisherErrors() throws Exception {
        EnrichrMethodInvoker invoker = new EnrichrMethodInvoker( "errorPublisher", null, StaticHelper.class.getName(), 0 );

        try {
            invoker.invokeAsync( "alice", null, null ).toCompletableFuture().get();
            Assert.fail( "Expected a failure from the error publisher" );
        }
        catch ( ExecutionException e ) {
            Assert.assertTrue( e.getCause() instanceof IllegalArgumentException );
            Assert.assertEquals( e.getCause().getMessage(), "publisher failure" );
        }
    }

    @Test
    public void pendingEnrichment_rethrowsRuntimeFailures() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        future.completeExceptionally( new IllegalStateException( "boom" ) );

        try {
            newPendingEnrichment( future, "value" ).apply();
            Assert.fail( "Expected the runtime exception to be rethrown" );
        }
        catch ( IllegalStateException e ) {
            Assert.assertEquals( e.getMessage(), "boom" );
        }
    }

    @Test
    public void pendingEnrichment_wrapsCheckedFailures() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        future.completeExceptionally( new IOException( "checked" ) );

        try {
            newPendingEnrichment( future, "value" ).apply();
            Assert.fail( "Expected a TransformException" );
        }
        catch ( TransformException e ) {
            Assert.assertTrue( e.getCause() instanceof IOException );
            Assert.assertTrue( e.getMessage().contains( "outputPath 'value'" ) );
        }
    }

    @Test
    public void pendingEnrichment_restoresInterruptStatusWhenInterrupted() {
        CompletableFuture<Object> future = new CompletableFuture<>();

        try {
            Thread.currentThread().interrupt();
            newPendingEnrichment( future, "value" ).apply();
            Assert.fail( "Expected a TransformException" );
        }
        catch ( TransformException e ) {
            Assert.assertTrue( Thread.currentThread().isInterrupted() );
            Assert.assertTrue( e.getMessage().contains( "interrupted" ) );
        }
        finally {
            Thread.interrupted();
        }
    }

    private Map<String, Object> rule( String path, String outputPath, String className, String contextKey, String method ) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put( "path", path );
        if ( outputPath != null ) {
            rule.put( "outputPath", outputPath );
        }
        if ( className != null ) {
            rule.put( "className", className );
        }
        if ( contextKey != null ) {
            rule.put( "contextKey", contextKey );
        }
        rule.put( "method", method );
        return rule;
    }

    private EnrichrPendingEnrichment newPendingEnrichment( CompletableFuture<Object> future, String outputPath ) {
        return new EnrichrPendingEnrichment(
                new LinkedHashMap<>(),
                new SimpleTraversr<>( outputPath ),
                Collections.singletonList( outputPath ),
                future,
                outputPath
        );
    }

    public static final class InstanceHelper {
        public Object instanceUppercase( Object value ) {
            return String.valueOf( value ).toUpperCase();
        }
    }

    public static final class StaticHelper {

        public static Object appendInputSuffix( Object value, Object input ) {
            @SuppressWarnings( "unchecked" )
            Map<String, Object> inputMap = (Map<String, Object>) input;
            return value + "-" + inputMap.get( "suffix" );
        }

        public static Object returnNull( Object value ) {
            return null;
        }

        public static Object explode( Object value ) {
            throw new IllegalStateException( "boom" );
        }

        public static Publisher<Object> multiValuePublisher( Object value, Object input, Map<String, Object> context ) {
            return subscriber -> subscriber.onSubscribe( new Subscription() {
                private boolean completed;

                @Override
                public void request( long n ) {
                    if ( completed ) {
                        return;
                    }

                    completed = true;
                    subscriber.onNext( value );
                    subscriber.onNext( String.valueOf( value ).toUpperCase() );
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    completed = true;
                }
            } );
        }

        public static Publisher<Object> errorPublisher( Object value, Object input, Map<String, Object> context ) {
            return subscriber -> subscriber.onSubscribe( new Subscription() {
                private boolean completed;

                @Override
                public void request( long n ) {
                    if ( completed ) {
                        return;
                    }

                    completed = true;
                    subscriber.onError( new IllegalArgumentException( "publisher failure" ) );
                }

                @Override
                public void cancel() {
                    completed = true;
                }
            } );
        }
    }

    public static final class InvalidSignatureHelper {

        public static Object invalid() {
            return "invalid";
        }

        public static Object invalid( Object value, int primitive ) {
            return "invalid";
        }

        public static Object invalid( Object value, Object input, List<?> context ) {
            return "invalid";
        }

        public static Object invalid( Object one, Object two, Object three, Object four ) {
            return "invalid";
        }
    }
}
