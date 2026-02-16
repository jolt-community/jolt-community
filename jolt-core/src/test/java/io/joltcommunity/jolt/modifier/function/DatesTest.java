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
package io.joltcommunity.jolt.modifier.function;

import io.joltcommunity.jolt.common.Optional;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("deprecated")
public class DatesTest extends AbstractTester {

    @Override
    @DataProvider(parallel = true)
    public Iterator<Object[]> getTestCases() {
        List<Object[]> testCases = new LinkedList<>();
        Function TO_EPOCH = new Dates.toEpochMilli();
        Function FROM_EPOCH = new Dates.fromEpochMilli();

        testCases.add(new Object[]{"fromEpoch-default-long", FROM_EPOCH, new Object[]{1L}, Optional.of("19700101")});
        testCases.add(new Object[]{"fromEpoch-pattern-long", FROM_EPOCH, new Object[]{1L, "yyyy"}, Optional.of("1970")});
        testCases.add(new Object[]{"fromEpoch-default-int", FROM_EPOCH, new Object[]{1}, Optional.of("19700101")});
        testCases.add(new Object[]{"fromEpoch-pattern-int", FROM_EPOCH, new Object[]{1, "yyyy"}, Optional.of("1970")});
        testCases.add(new Object[]{"fromEpoch-pattern-iso8601", FROM_EPOCH, new Object[]{1771176362001L, "yyyy-MM-dd'T'HH:mm:ssX"}, Optional.of("2026-02-15T17:26:02Z")});
        testCases.add(new Object[]{"fromEpoch-pattern-invalid-patter", FROM_EPOCH, new Object[]{1, "ABCD"}, Optional.empty()});
        testCases.add(new Object[]{"fromEpoch-default-string", FROM_EPOCH, new Object[]{"1"}, Optional.empty()});
        testCases.add(new Object[]{"fromEpoch-pattern-string", FROM_EPOCH, new Object[]{"1", "yyyy"}, Optional.empty()});

        testCases.add(new Object[]{"toEpoch-pattern", TO_EPOCH, new Object[]{"2000-01-01", "yyyy-MM-dd"}, Optional.of(946684800000L)});
        testCases.add(new Object[]{"toEpoch-pattern", TO_EPOCH, new Object[]{"2000-01-01T00:00:00Z", "yyyy-MM-dd'T'HH:mm:ss'Z'"}, Optional.of(946684800000L)});
        testCases.add(new Object[]{"toEpoch-pattern", TO_EPOCH, new Object[]{"1970-01-01"}, Optional.empty()});

        return testCases.iterator();
    }

    @Test
    public void nowReturnsEpoch() {
        Optional<Object> opt = (new Dates.now()).apply((Object) null);
        assert (opt.isPresent() && (opt.get() instanceof Long));
    }
}
