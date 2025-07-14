/*
 * Copyright 2013-2023 Bazaarvoice, Inc.
 * Copyright 2025 Jolt Community
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

import io.joltcommunity.jolt.exception.SpecException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CardinalityTransformTest {
    @DataProvider
    public Object[][] invalidSpecs() {
        return new Object[][]{
                {null},
                {"InvalidSpecType"},
                {new HashMap<>()}
        };
    }

    @Test(dataProvider = "invalidSpecs", expectedExceptions = SpecException.class)
    public void throwsSpecExceptionForInvalidSpecs(Object invalidSpec) {
        new CardinalityTransform(invalidSpec);
    }

    @Test
    public void initializesSuccessfullyWithValidSpec() {
        Map<String, Object> validSpec = new HashMap<>() {{
            put("key", "ONE");
        }};
        CardinalityTransform transform = new CardinalityTransform(validSpec);
        Assert.assertNotNull(transform);
    }

    @DataProvider
    public Object[][] getTestCaseUnits() {
        return new Object[][]{
                {"oneLiteralTestData"},
                {"manyLiteralTestData"},
                {"starTestData"},
                {"starRegexTestData"},
                {"thisLevelIsNull"},
                {"scalarInputData"},
                {"nullScalarInputData"},
                {"atTestData"}
        };
    }

    @Test(dataProvider = "getTestCaseUnits")
    public void runTestUnits(String testCaseName) throws IOException {

        String testPath = "/json/cardinality/" + testCaseName;
        Map<String, Object> testUnit = JsonUtils.classpathToMap(testPath + ".json");

        Object input = testUnit.get("input");
        Object spec = testUnit.get("spec");
        Object expected = testUnit.get("expected");

        CardinalityTransform cardinalityTransform = new CardinalityTransform(spec);
        Object actual = cardinalityTransform.transform(input);

        JoltTestUtil.runDiffy("failed case " + testPath, expected, actual);
    }

    @Test(expectedExceptions = SpecException.class)
    public void testSpecExceptions() throws IOException {
        String testPath = "/json/cardinality/failCardinalityType";
        Map<String, Object> testUnit = JsonUtils.classpathToMap(testPath + ".json");

        Object spec = testUnit.get("spec");

        // Should throw exception
        new CardinalityTransform(spec);
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void shouldThrowClassCastException() {
        String testPath = "/json/cardinality/exceptionScalarInput";
        Map<String, Object> testUnit = JsonUtils.classpathToMap(testPath + ".json");

        Object input = testUnit.get("input");
        Object spec = testUnit.get("spec");

        CardinalityTransform cardinalityTransform = new CardinalityTransform(spec);
        // should throw ClassCastException because input is a String and spec expects a Map
        Object actual = cardinalityTransform.transform(input);
    }

    @Test
    public void testArrayCardinalityOne() throws IOException {
        // The above tests cover cardinality on elements that are Lists, this test covers elements that are arrays
        Map<String, Object> input = new HashMap<>() {{
            put("input", new Integer[]{5, 4});
        }};

        Map<String, Object> spec = new HashMap<>() {{
            put("input", "ONE");
        }};

        Map<String, Object> expected = new HashMap<>() {{
            put("input", 5);
        }};

        CardinalityTransform cardinalityTransform = new CardinalityTransform(spec);
        Object actual = cardinalityTransform.transform(input);
        JoltTestUtil.runDiffy("failed array test", expected, actual);
    }

    @Test
    public void testArrayCardinalityMany() throws IOException {
        // The above tests cover cardinality on elements that are Lists, this test covers elements that are arrays
        Map<String, Object> input = new HashMap<>() {{
            put("input", new Integer[]{5, 4});
        }};

        Map<String, Object> spec = new HashMap<>() {{
            put("input", "MANY");
        }};

        Map<String, Object> expected = new HashMap<>() {{
            put("input", new Integer[]{5, 4});
        }};

        CardinalityTransform cardinalityTransform = new CardinalityTransform(spec);
        Object actual = cardinalityTransform.transform(input);
        JoltTestUtil.runDiffy("failed array test", expected, actual);
    }
}
