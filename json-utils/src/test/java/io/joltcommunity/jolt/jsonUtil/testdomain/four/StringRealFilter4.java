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
package io.joltcommunity.jolt.jsonUtil.testdomain.four;

import io.joltcommunity.jolt.jsonUtil.testdomain.QueryParam;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StringRealFilter4 extends BaseRealFilter4<String> {

    private final String value;

    @JsonCreator
    public StringRealFilter4(@JsonProperty("queryParam") QueryParam queryParam,
            @JsonProperty("value") String value) {
        super(queryParam);
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
