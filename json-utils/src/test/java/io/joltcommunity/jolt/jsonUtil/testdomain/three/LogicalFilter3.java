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
package io.joltcommunity.jolt.jsonUtil.testdomain.three;

import io.joltcommunity.jolt.jsonUtil.testdomain.QueryFilter;
import io.joltcommunity.jolt.jsonUtil.testdomain.QueryParam;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonSerialize(using = LogicalFilter3.LogicalFilter3Serializer.class)
@JsonDeserialize(using = LogicalFilter3.LogicalFilter4Deserializer.class)
public class LogicalFilter3 implements QueryFilter {

    private final QueryParam queryParam;
    private final Map<QueryParam, QueryFilter> filters;

    public LogicalFilter3(QueryParam queryParam, List<QueryFilter> filters) {
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

    public static class LogicalFilter3Serializer extends JsonSerializer<LogicalFilter3> {

        @Override
        public void serialize(LogicalFilter3 filter, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeObjectField(filter.queryParam().toString(), filter.filters().values());
            jgen.writeEndObject();
        }
    }

    public static class LogicalFilter4Deserializer extends JsonDeserializer<LogicalFilter3> {

        @Override
        public LogicalFilter3 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {

            ObjectCodec objectCodec = jp.getCodec();
            ObjectNode root = jp.readValueAsTree();

            // We assume it is a LogicalFilter
            Iterator<String> iter = root.fieldNames();
            String key = iter.next();

            JsonNode arrayNode = root.iterator().next();
            if (arrayNode == null || arrayNode.isMissingNode() || !arrayNode.isArray()) {
                throw new RuntimeException("Invalid format of LogicalFilter encountered.");
            }

            // pass in our objectCodec so that the subJsonParser knows about our configured Modules and Annotations
            JsonParser subJsonParser = arrayNode.traverse(objectCodec);
            List<QueryFilter> childrenQueryFilters = subJsonParser.readValueAs(new TypeReference<List<QueryFilter>>() {
            });

            return new LogicalFilter3(QueryParam.valueOf(key), childrenQueryFilters);
        }
    }
}
