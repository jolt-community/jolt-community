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
import io.joltcommunity.jolt.removr.Removr;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class RemovrTest {

    @DataProvider
    public Object[][] getTestCaseNames() {
        return new Object[][]{
                {"firstSample"},
                {"boundaryConditions"},
                {"removrWithWildcardSupport"},
                {"multiStarSupport"},
                {"starDoublePathElementBoundaryConditions"},
                // Array tests
                {"array_canPassThruNestedArrays"},
                {"array_canHandleTopLevelArray"},
                {"array_nonStarInArrayDoesNotDie"},
                {"array_removeAnArrayIndex"},
                {"array_removeJsonArrayFields"}
        };
    }

    @Test(dataProvider = "getTestCaseNames")
    public void runTestCases(String testCaseName) throws IOException {

        String testPath = "/json/removr/" + testCaseName;
        Map<String, Object> testUnit = JsonUtils.classpathToMap(testPath + ".json");

        Object input = testUnit.get("input");
        Object spec = testUnit.get("spec");
        Object expected = testUnit.get("expected");

        Removr removr = new Removr(spec);
        Object actual = removr.transform(input);

        JoltTestUtil.runDiffy("failed case " + testPath, expected, actual);
    }

    @DataProvider
    public Object[][] getNegativeTestCaseNames() {
        return new Object[][]{
                {"negativeTestCases"}
        };
    }

    @Test(dataProvider = "getNegativeTestCaseNames", expectedExceptions = SpecException.class)
    public void runNegativeTestCases(String testCaseName) throws IOException {

        String testPath = "/json/removr/" + testCaseName;
        Map<String, Object> testUnit = JsonUtils.classpathToMap(testPath + ".json");

        Object spec = testUnit.get("spec");
        new Removr(spec);
    }

    @DataProvider
    public Object[][] badSpecs() throws IOException {
        return new Object[][]{
                {
                        "Null Spec",
                        null,
                },
                {
                        "List Spec",
                        new ArrayList<>(),
                },
                {
                        "Invalid rhs string",
                        JsonUtils.javason("{ 'tuna' : 'marlin[-1]' }"),
                },
                {
                        "Invalid rhs type - not a Map (number)",
                        JsonUtils.javason("{ 'tuna' : 123 }"),
                },
                {
                        "Invalid rhs type - not a Map (array)",
                        JsonUtils.javason("{ 'tuna' : [] }"),
                }
        };
    }

    @Test(dataProvider = "badSpecs", expectedExceptions = SpecException.class)
    public void failureUnitTest(String testName, Object spec) {
        new Removr(spec);
    }
}
