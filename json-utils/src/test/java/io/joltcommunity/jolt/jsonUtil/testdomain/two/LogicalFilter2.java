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
package io.joltcommunity.jolt.jsonUtil.testdomain.two;

import io.joltcommunity.jolt.jsonUtil.testdomain.QueryFilter;
import io.joltcommunity.jolt.jsonUtil.testdomain.QueryParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Note this class does not have any Jackson markup as all the work is being done
 * in the Jackson Module inside CustomObjectMapperTest2...
 * <p>
 * This is an improvement over LogicalFilter1 in that we write out an Array but still
 * have a Map in memory, for easy filter lookup.
 */
public class LogicalFilter2 implements QueryFilter {

    private final QueryParam queryParam;
    private final Map<QueryParam, QueryFilter> filters;

    public LogicalFilter2(QueryParam queryParam, List<QueryFilter> filters) {
        this.queryParam = queryParam;

        this.filters = new LinkedHashMap<>();
        for (QueryFilter queryFilter : filters) {
            this.filters.put(queryFilter.queryParam(), queryFilter);
        }
    }

    @Override
    public Map<QueryParam, QueryFilter> filters() {
        return filters;
    }

    @Override
    public QueryParam queryParam() {
        return queryParam;
    }

    @Override
    public String value() {
        return null;
    }

    @Override
    public boolean isLogical() {
        return true;
    }

    @Override
    public boolean isReal() {
        return false;
    }
}
