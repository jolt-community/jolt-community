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
package io.joltcommunity.jolt.jsonUtil.testdomain.five;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class IntegerRealFilter5 extends RealFilter5<Integer> {

    private final List<Integer> values;

    @JsonCreator
    public IntegerRealFilter5(@JsonProperty("field") Field field,
            @JsonProperty("operator") Operator op,
            @JsonProperty("values") List<Integer> values) {
        super(field, op);
        this.values = values;
    }

    @Override
    public List<Integer> getValues() {
        return values;
    }

    @Override
    public Type getType() {
        return Type.INTEGER;
    }
}
