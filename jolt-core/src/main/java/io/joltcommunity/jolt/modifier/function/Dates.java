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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.List;

@SuppressWarnings("deprecated")
public class Dates {

    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    // Default to ISO8601 format at UTC timezone
    public static final String defaultDatePattern = "yyyy-MM-dd'T'HH:mm:ssX";

    public static final Function now = args -> now();

    public static final class now extends Function.ListFunction {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 2) {
                return Optional.empty();
            } else {
                return now(input.get(0), input.get(1));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static final class fromEpochMilli extends Function.BaseFunction<String> {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 3) {
                return Optional.empty();
            } else {
                return (Optional) fromEpochMilli(input.get(0), input.get(1), input.get(2));
            }
        }

        @Override
        protected Optional<String> applySingle(Object arg) {
            return fromEpochMilli(arg, defaultDatePattern, "UTC");
        }
    }

    public static final class toEpochMilli extends Function.ListFunction {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 3) {
                return Optional.empty();
            } else {
                return toEpochMilli(input.get(0), input.get(1), input.get(2));
            }
        }
    }

    public static final class format extends Function.ListFunction {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 4) {
                return Optional.empty();
            } else {
                return format(input.get(0), input.get(1), input.get(2), input.get(3));
            }
        }
    }

    public static final class dateAdd extends Function.ListFunction {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 4) {
                return Optional.empty();
            } else {
                return dateAdd(input.get(0), input.get(1), input.get(2), input.get(3));
            }
        }
    }

    public static final class dateSubstract extends Function.ListFunction {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 4) {
                return Optional.empty();
            } else {
                return dateSubstract(input.get(0), input.get(1), input.get(2), input.get(3));
            }
        }
    }

    /**
     * This function returns current time, in EPOCH, of the <b>current locale/timezone</b>.
     */
    private static Optional<Object> now() {
        return Optional.of(Instant.now().toEpochMilli());
    }

    /**
     * Returns the current time formatted with the provided pattern. If no pattern is provided,
     * it defaults to {@link Dates#defaultDatePattern} (ISO8601 format at UTC timezone)
     */
    private static Optional<Object> now(Object pattern, Object zoneId) {
        if (!((pattern instanceof String patternStr)
                && zoneId instanceof String zoneIdStr))
            return Optional.empty();

        try {
            Instant instant = Instant.now();
            DateTimeFormatter formatter = createFormatterWithTimeZone(patternStr, zoneIdStr);
            return Optional.of(formatter.format(instant));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Given a {@link java.lang.Number} representing an EPOCH in milliseconds and the pattern and time-zone in which
     * it has to be converted returns a String following that representation.
     */
    private static Optional<String> fromEpochMilli(Object arg, Object format, Object zoneId) {
        Optional<Long> optEpoch = castToLong(arg);
        if (arg == null
                || !(format instanceof String sdfFormat)
                || !optEpoch.isPresent()
                || !(zoneId instanceof String zoneIdStr))
            return Optional.empty();

        Long epoch = optEpoch.get();
        try {
            Instant instant = Instant.ofEpochMilli(epoch);
            DateTimeFormatter formatter = createFormatterWithTimeZone(sdfFormat, zoneIdStr);
            return Optional.of(formatter.format(instant));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Given a String representing a {@link java.util.Date} and the pattern used to represent it,
     * returns the EPOCH in milliseconds of that date. The pattern uses the same pattern in
     * {@link java.time.format.DateTimeFormatter}.
     */
    private static Optional<Object> toEpochMilli(Object date, Object format, Object zoneId) {
        if (!((date instanceof String dateStr)
                && (format instanceof String formatStr)
                && (zoneId instanceof String zoneIdStr)))
            return Optional.empty();

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatStr);
            TemporalAccessor temporal = formatter.parse(dateStr);
            Instant instant = parseToInstant(temporal, zoneIdStr);
            return Optional.of(instant.toEpochMilli());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Transforms a date from one pattern to another.
     */
    private static Optional<Object> format(Object date, Object fromPattern, Object toPattern, Object zoneId) {
        if (!((date instanceof String dateStr)
                && (fromPattern instanceof String fromPatternStr)
                && (toPattern instanceof String toPatternStr)
                && (zoneId instanceof String zoneIdStr)))
            return Optional.empty();

        try {
            DateTimeFormatter fromFormatter = DateTimeFormatter.ofPattern(fromPatternStr);
            DateTimeFormatter toFormatter = DateTimeFormatter.ofPattern(toPatternStr).withZone(ZoneId.of(zoneIdStr));
            TemporalAccessor temporal = fromFormatter.parse(dateStr);
            Instant instant = parseToInstant(temporal, zoneIdStr);
            return Optional.of(toFormatter.format(instant));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Object> modifyDate(Object date, Object pattern, Object duration, Object zoneId, boolean add) {
        if (!((date instanceof String dateStr)
                && (pattern instanceof String patternStr)
                && (duration instanceof String durationStr)
                && (zoneId instanceof String zoneIdStr)))
            return Optional.empty();

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(patternStr);
            TemporalAccessor temporal = formatter.parse(dateStr);
            TemporalAmount period = computePeriodDuration(durationStr);
            Instant instant = parseToInstant(temporal, zoneIdStr);

            LocalDateTime resultDateTime = LocalDateTime.ofInstant(instant, ZoneId.of(zoneIdStr));
            resultDateTime = add ? resultDateTime.plus(period) : resultDateTime.minus(period);

            return Optional.of(formatter.format(resultDateTime.atZone(ZoneId.of(zoneIdStr))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Adds a duration to a date, with duration expressed in ISO8601 format (e.g., P1D).
     */
    private static Optional<Object> dateAdd(Object date, Object pattern, Object duration, Object zoneId) {
        return modifyDate(date, pattern, duration, zoneId,true);
    }

    /**
     * Subtracts a duration from a date, with duration expressed in ISO8601 format (e.g., P1D).
     */
    private static Optional<Object> dateSubstract(Object date, Object pattern, Object duration, Object zoneId) {
        return modifyDate(date, pattern, duration, zoneId,  false);
    }

    private static DateTimeFormatter createFormatterWithTimeZone(String pattern, String zoneId) {
        return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.of(zoneId));
    }

    private static Optional<Long> castToLong(Object obj) {
        if (obj instanceof Number) {
            return Optional.of(((Number) obj).longValue());
        }
        return Optional.empty();
    }

    /**
     * Parses a {@link TemporalAccessor} object into an {@link Instant}.
     * The method checks the available fields in the provided {@code temporal}
     * and determines the most appropriate way to convert it to an {@code Instant}.
     */
    private static Instant parseToInstant(TemporalAccessor temporal, String zoneId) {
        if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
            return Instant.from(temporal);
        } else if (temporal.isSupported(ChronoField.HOUR_OF_DAY)) {
            LocalDateTime localDateTime = LocalDateTime.from(temporal);
            return localDateTime.atZone(ZoneId.of(zoneId)).toInstant();
        } else {
            LocalDate localDate = LocalDate.from(temporal);
            return localDate.atStartOfDay(ZoneId.of(zoneId)).toInstant();
        }
    }

    private static TemporalAmount computePeriodDuration(String periodDuration) {
        if (periodDuration == null) {
            return Period.ZERO;
        }

        String[] splitted = periodDuration.split("T");
        if (splitted.length == 1) {
            // has only date-based fields
            return Period.parse(periodDuration);
        } else {
            Duration duration = Duration.parse("PT" + splitted[1]);
            if ("P".equals(splitted[0])) {
                // has only time-based fields
                return duration;
            } else {
                return Period.parse(splitted[0]).plus(duration);
            }
        }
    }
}
