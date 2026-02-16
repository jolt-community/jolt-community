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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.List;

@SuppressWarnings("deprecated")
public class Dates {

    public static final String defaultDateFormat = "yyyyMMdd";

    public static Optional<Long> castToLong(Object obj) {
        if (obj instanceof Number) {
            return Optional.of(((Number) obj).longValue());
        }
        return Optional.empty();
    }

    /**
     * Given a {@link java.lang.Number} representing an EPOCH in milliseconds and the pattern in which
     * it has to be converted returns a String following that representation.
     *
     * @param arg    EPOCH to be converted.
     * @param format pattern in which the EPOCH has to be converted; it uses
     *               {@link java.time.format.DateTimeFormatter} format.
     * @return String representing the date in <b>UTC timezone</b>, wrapped in an {@link Optional} object.
     */
    public static Optional<String> fromEpochMilli(Object arg, Object format) {
        Optional<Long> optEpoch = castToLong(arg);
        if (arg == null || !(format instanceof String sdfFormat) || !optEpoch.isPresent()) {
            return Optional.empty();
        }

        Long epoch = optEpoch.get();
        try {
            Instant instant = Instant.ofEpochMilli(epoch);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(sdfFormat)
                    .withZone(java.time.ZoneId.of("UTC"));
            return Optional.of(formatter.format(instant));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Given a String representing a {@link java.util.Date} and the pattern used to represent it,
     * returns the EPOCH in milliseconds of that date. The pattern uses the same pattern in
     * {@link java.time.format.DateTimeFormatter}.
     *
     * @param date   String representing the date
     * @param format String representing the pattern
     * @return Long representing the EPOCH in <b>UTC timezone</b>, wrapped in {@link Optional}.
     */
    public static Optional<Long> toEpochMilli(Object date, Object format) {
        if (!((date instanceof String dateStr) && (format instanceof String formatStr))) {
            return Optional.empty();
        }

        try {
            java.time.ZoneId utcZone = java.time.ZoneId.of("UTC");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatStr);
            TemporalAccessor temporal = formatter.parse(dateStr);
            Instant instant;

            // Try to parse with timezone information if present
            if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
                instant = Instant.from(temporal);
            } else if (temporal.isSupported(ChronoField.HOUR_OF_DAY)) {
                // Has time information
                LocalDateTime localDateTime = LocalDateTime.from(temporal);
                instant = localDateTime.atZone(utcZone).toInstant();
            } else {
                // Date only
                LocalDate localDate = LocalDate.from(temporal);
                instant = localDate.atStartOfDay(utcZone).toInstant();
            }
            return Optional.of(instant.toEpochMilli());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * This function returns current time, in EPOCH, of the <b>current locale/timezone</b>.
     *
     * @return Long representing current EPOCH, wrapped by {@link Optional}
     * @see Instant#now()
     */
    public static Optional<Long> now() {
        return Optional.of(Instant.now().toEpochMilli());
    }

    public static final class now implements Function {
        @Override
        @SuppressWarnings("unchecked")
        public Optional<Object> apply(Object... args) {
            return (Optional) now();
        }
    }

    @SuppressWarnings("unchecked")
    public static final class fromEpochMilli extends Function.BaseFunction<Object> {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 2) {
                return Optional.empty();
            } else {
                return (Optional) fromEpochMilli(input.get(0), input.get(1));
            }
        }

        @Override
        protected Optional<Object> applySingle(Object arg) {
            return (Optional) fromEpochMilli(arg, defaultDateFormat);
        }
    }

    @SuppressWarnings("unchecked")
    public static final class toEpochMilli extends Function.BaseFunction<Object> {

        @Override
        protected Optional<Object> applyList(List<Object> input) {
            if (input == null || input.size() != 2) {
                return Optional.empty();
            } else {
                return (Optional) toEpochMilli(input.get(0), input.get(1));
            }
        }

        @Override
        protected Optional<Object> applySingle(Object arg) {
            return Optional.empty();
        }
    }
}
