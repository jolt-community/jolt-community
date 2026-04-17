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

import java.util.LinkedHashMap;
import java.util.Map;

public class EnrichrTestHelper {

    public static Object uppercase( Object value ) {
        return String.valueOf( value ).toUpperCase();
    }

    public static Object describe( Object value, Object input, Map<String, Object> context ) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        enriched.put( "original", value );
        enriched.put( "inputType", input == null ? null : input.getClass().getSimpleName() );
        enriched.put( "tenant", context == null ? null : context.get( "tenant" ) );
        return enriched;
    }
}
