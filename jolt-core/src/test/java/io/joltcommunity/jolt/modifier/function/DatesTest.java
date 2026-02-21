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
        Function FORMAT = new Dates.format();
        Function DATE_ADD = new Dates.dateAdd();
        Function DATE_SUBSTRACT = new Dates.dateSubstract();

        testCases.add(new Object[]{"fromEpoch-default-long", FROM_EPOCH, new Object[]{1L}, Optional.of("1970-01-01T00:00:00Z")});
        testCases.add(new Object[]{"fromEpoch-pattern-long", FROM_EPOCH, new Object[]{1L, "yyyy", "UTC"}, Optional.of("1970")});
        testCases.add(new Object[]{"fromEpoch-default-int", FROM_EPOCH, new Object[]{1}, Optional.of("1970-01-01T00:00:00Z")});
        testCases.add(new Object[]{"fromEpoch-pattern-int", FROM_EPOCH, new Object[]{1, "yyyy", "UTC"}, Optional.of("1970")});
        testCases.add(new Object[]{"fromEpoch-pattern-iso8601", FROM_EPOCH, new Object[]{1771176362001L, "yyyy-MM-dd'T'HH:mm:ssX", "UTC"}, Optional.of("2026-02-15T17:26:02Z")});
        testCases.add(new Object[]{"fromEpoch-pattern-iso8601", FROM_EPOCH, new Object[]{1771176362001L, "yyyy-MM-dd'T'HH:mm:ssX", "Europe/Paris"}, Optional.of("2026-02-15T18:26:02+01")});

        testCases.add(new Object[]{"fromEpoch-pattern-null", FROM_EPOCH, null, Optional.empty()});
        testCases.add(new Object[]{"fromEpoch-pattern-numeric-pattern", FROM_EPOCH, new Object[]{1, 1}, Optional.empty()});
        testCases.add(new Object[]{"fromEpoch-pattern-invalid-pattern", FROM_EPOCH, new Object[]{1, "ABCD"}, Optional.empty()});
        testCases.add(new Object[]{"fromEpoch-default-string", FROM_EPOCH, new Object[]{"1"}, Optional.empty()});
        testCases.add(new Object[]{"fromEpoch-pattern-string", FROM_EPOCH, new Object[]{"1", "yyyy"}, Optional.empty()});

        testCases.add(new Object[]{"toEpoch-pattern-day", TO_EPOCH, new Object[]{"2000-01-01", "yyyy-MM-dd", "UTC"}, Optional.of(946684800000L)});
        testCases.add(new Object[]{"toEpoch-pattern-seconds", TO_EPOCH, new Object[]{"2000-01-01T00:00:00Z", "yyyy-MM-dd'T'HH:mm:ss'Z'", "UTC"}, Optional.of(946684800000L)});
        testCases.add(new Object[]{"toEpoch-pattern-seconds", TO_EPOCH, new Object[]{"2000-01-01T00:00:00Z", "yyyy-MM-dd'T'HH:mm:ss'Z'", "Europe/Paris"}, Optional.of(946681200000L)});
        testCases.add(new Object[]{"toEpoch-pattern-milliseconds", TO_EPOCH, new Object[]{"2000-01-01T00:00:00.000Z", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "UTC"}, Optional.of(946684800000L)});
        testCases.add(new Object[]{"toEpoch-pattern-no-pattern", TO_EPOCH, new Object[]{"1970-01-01"}, Optional.empty()});
        testCases.add(new Object[]{"toEpoch-pattern-invalid-data", TO_EPOCH, new Object[]{1}, Optional.empty()});
        testCases.add(new Object[]{"toEpoch-pattern-null", TO_EPOCH, null, Optional.empty()});

        testCases.add(new Object[]{"format-date-only", FORMAT, new Object[]{"2000-01-01", "yyyy-MM-dd", "yyyyMMdd", "UTC"}, Optional.of("20000101")});
        testCases.add(new Object[]{"format-with-time", FORMAT, new Object[]{"2000-01-01T12:30:45", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "UTC"}, Optional.of("2000-01-01 12:30:45")});
        testCases.add(new Object[]{"format-with-time", FORMAT, new Object[]{"2000-01-01T12:30:45", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "Europe/Paris"}, Optional.of("2000-01-01 12:30:45")});
        testCases.add(new Object[]{"format-to-iso8601", FORMAT, new Object[]{"20000101", "yyyyMMdd", "yyyy-MM-dd'T'HH:mm:ss'Z'", "UTC"}, Optional.of("2000-01-01T00:00:00Z")});
        testCases.add(new Object[]{"format-invalid-args", FORMAT, new Object[]{"2000-01-01", "yyyy-MM-dd"}, Optional.empty()});
        testCases.add(new Object[]{"format-invalid-pattern", FORMAT, new Object[]{"2000-01-01", "INVALID", "yyyy-MM-dd"}, Optional.empty()});
        testCases.add(new Object[]{"format-null", FORMAT, null, Optional.empty()});

        testCases.add(new Object[]{"dateAdd-one-day", DATE_ADD, new Object[]{"2000-01-01", "yyyy-MM-dd", "P1D", "UTC"}, Optional.of("2000-01-02")});
        testCases.add(new Object[]{"dateAdd-one-month", DATE_ADD, new Object[]{"2000-01-01", "yyyy-MM-dd", "P1M", "UTC"}, Optional.of("2000-02-01")});
        testCases.add(new Object[]{"dateAdd-one-year", DATE_ADD, new Object[]{"2000-01-01", "yyyy-MM-dd", "P1Y", "UTC"}, Optional.of("2001-01-01")});
        testCases.add(new Object[]{"dateAdd-complex", DATE_ADD, new Object[]{"2000-01-01", "yyyy-MM-dd", "P1Y2M3D", "UTC"}, Optional.of("2001-03-04")});
        testCases.add(new Object[]{"dateAdd-with-time", DATE_ADD, new Object[]{"2000-01-01T12:30:45", "yyyy-MM-dd'T'HH:mm:ss", "P1D", "UTC"}, Optional.of("2000-01-02T12:30:45")});
        testCases.add(new Object[]{"dateAdd-with-time", DATE_ADD, new Object[]{"2000-01-01T12:30:45", "yyyy-MM-dd'T'HH:mm:ss", "P1D", "Europe/Paris"}, Optional.of("2000-01-02T12:30:45")});
        testCases.add(new Object[]{"dateAdd-with-time-one-hour", DATE_ADD, new Object[]{"2000-01-01T12:30:45", "yyyy-MM-dd'T'HH:mm:ss", "PT1H", "UTC"}, Optional.of("2000-01-01T13:30:45")});
        testCases.add(new Object[]{"dateAdd-invalid-args", DATE_ADD, new Object[]{"2000-01-01", "yyyy-MM-dd"}, Optional.empty()});
        testCases.add(new Object[]{"dateAdd-invalid-duration", DATE_ADD, new Object[]{"2000-01-01", "yyyy-MM-dd", "INVALID"}, Optional.empty()});
        testCases.add(new Object[]{"dateAdd-null", DATE_ADD, null, Optional.empty()});

        testCases.add(new Object[]{"dateSubstract-one-day", DATE_SUBSTRACT, new Object[]{"2000-01-02", "yyyy-MM-dd", "P1D", "UTC"}, Optional.of("2000-01-01")});
        testCases.add(new Object[]{"dateSubstract-one-month", DATE_SUBSTRACT, new Object[]{"2000-02-01", "yyyy-MM-dd", "P1M", "UTC"}, Optional.of("2000-01-01")});
        testCases.add(new Object[]{"dateSubstract-one-year", DATE_SUBSTRACT, new Object[]{"2001-01-01", "yyyy-MM-dd", "P1Y", "UTC"}, Optional.of("2000-01-01")});
        testCases.add(new Object[]{"dateSubstract-complex", DATE_SUBSTRACT, new Object[]{"2001-03-04", "yyyy-MM-dd", "P1Y2M3D", "UTC"}, Optional.of("2000-01-01")});
        testCases.add(new Object[]{"dateSubstract-with-time", DATE_SUBSTRACT, new Object[]{"2000-01-02T12:30:45", "yyyy-MM-dd'T'HH:mm:ss", "P1D", "UTC"}, Optional.of("2000-01-01T12:30:45")});
        testCases.add(new Object[]{"dateSubstract-with-time-one-hour", DATE_SUBSTRACT, new Object[]{"2000-01-01T12:30:45", "yyyy-MM-dd'T'HH:mm:ss", "PT1H", "UTC"}, Optional.of("2000-01-01T11:30:45")});
        testCases.add(new Object[]{"dateSubstract-invalid-args", DATE_SUBSTRACT, new Object[]{"2000-01-01", "yyyy-MM-dd"}, Optional.empty()});
        testCases.add(new Object[]{"dateSubstract-invalid-duration", DATE_SUBSTRACT, new Object[]{"2000-01-01", "yyyy-MM-dd", "INVALID"}, Optional.empty()});
        testCases.add(new Object[]{"dateSubstract-null", DATE_SUBSTRACT, null, Optional.empty()});

        return testCases.iterator();
    }

    @Test
    public void nowEpochMillis() {
        Optional<Object> opt = Dates.now.apply();
        assert (opt.isPresent() && (opt.get() instanceof Long));
    }

    @Test
    public void nowReturnsEmptyIfNoPatternAndTimeZoneIsProvided() {
        Optional<Object> opt = (new Dates.now()).apply();
        assert !opt.isPresent();
    }

    @Test
    public void nowReturnsEmptyIfNoTimeZoneIsProvided() {
        Optional<Object> opt = (new Dates.now()).apply("yyyy-MM-dd");
        assert !opt.isPresent();
    }

    @Test
    public void nowReturnsFormattedDate() {
        Optional<Object> opt = (new Dates.now()).apply("yyyy-MM-dd", "UTC");
        assert (opt.isPresent() && (opt.get() instanceof String));
        String date = (String) opt.get();
        assert (date.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void nowReturnsFormattedDateWithUtcTimeZone() {
        Optional<Object> opt = (new Dates.now()).apply("yyyy-MM-dd'T'HH:mm:ssX", "UTC");
        assert (opt.isPresent() && (opt.get() instanceof String));
        String date = (String) opt.get();
        assert (date.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
    }

    @Test
    public void nowReturnsFormattedDateWithParisTimeZone() {
        Optional<Object> opt = (new Dates.now()).apply("yyyy-MM-dd'T'HH:mm:ssZ", "Europe/Paris");
        assert (opt.isPresent() && (opt.get() instanceof String));
        String date = (String) opt.get();
        assert (date.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+0100"));
    }
}
