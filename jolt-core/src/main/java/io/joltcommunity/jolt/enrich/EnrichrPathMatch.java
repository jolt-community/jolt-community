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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EnrichrPathMatch {

    private final Object value;
    private final List<String> resolvedInputKeys;
    private final List<String> wildcardBindings;
    private final String resolvedInputPath;

    EnrichrPathMatch( Object value, List<String> resolvedInputKeys, List<String> wildcardBindings, String resolvedInputPath ) {
        this.value = value;
        this.resolvedInputKeys = Collections.unmodifiableList( new ArrayList<>( resolvedInputKeys ) );
        this.wildcardBindings = Collections.unmodifiableList( new ArrayList<>( wildcardBindings ) );
        this.resolvedInputPath = resolvedInputPath;
    }

    Object getValue() {
        return value;
    }

    List<String> getResolvedInputKeys() {
        return resolvedInputKeys;
    }

    List<String> getWildcardBindings() {
        return wildcardBindings;
    }

    String getResolvedInputPath() {
        return resolvedInputPath;
    }
}
