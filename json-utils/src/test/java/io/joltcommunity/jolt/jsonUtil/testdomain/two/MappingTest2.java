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

import io.joltcommunity.jolt.Diffy;
import io.joltcommunity.jolt.JsonUtil;
import io.joltcommunity.jolt.JsonUtils;
import io.joltcommunity.jolt.jsonUtil.testdomain.QueryFilter;
import io.joltcommunity.jolt.jsonUtil.testdomain.QueryParam;
import io.joltcommunity.jolt.jsonUtil.testdomain.RealFilter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MappingTest2 {

    private Diffy diffy = new Diffy();

    @Test
    public void testPolymorphicJacksonSerializationAndDeserialization() {
        ObjectMapper mapper = new ObjectMapper();

        SimpleModule testModule = new SimpleModule("testModule", new Version(1, 0, 0, null, null, null))
                .addDeserializer(QueryFilter.class, new QueryFilterDeserializer())
                .addSerializer(LogicalFilter2.class, new LogicalFilter2Serializer());

        mapper.registerModule(testModule);

        // Verifying that we can pass in a custom Mapper and create a new JsonUtil
        JsonUtil jsonUtil = JsonUtils.customJsonUtil(mapper);

        String testFixture = "/jsonUtils/testdomain/two/queryFilter-realAndLogical2.json";

        // TEST JsonUtil and our deserialization logic
        QueryFilter queryFilter = jsonUtil.classpathToType(testFixture, new TypeReference<>() {
        });

        // Make sure the hydrated QFilter looks right
        Assert.assertTrue(queryFilter instanceof LogicalFilter2);
        Assert.assertEquals(QueryParam.AND, queryFilter.queryParam());
        Assert.assertTrue(queryFilter.isLogical());
        Assert.assertEquals(3, queryFilter.filters().size());
        Assert.assertNotNull(queryFilter.filters().get(QueryParam.OR));

        // Make sure one of the top level RealFilters looks right
        QueryFilter productIdFilter = queryFilter.filters().get(QueryParam.PRODUCTID);
        Assert.assertTrue(productIdFilter.isReal());
        Assert.assertEquals(QueryParam.PRODUCTID, productIdFilter.queryParam());
        Assert.assertEquals("Acme-1234", productIdFilter.value());

        // Make sure the nested OR looks right
        QueryFilter orFilter = queryFilter.filters().get(QueryParam.OR);
        Assert.assertTrue(orFilter.isLogical());
        Assert.assertEquals(QueryParam.OR, orFilter.queryParam());
        Assert.assertEquals(2, orFilter.filters().size());

        // Make sure nested AND looks right
        QueryFilter nestedAndFilter = orFilter.filters().get(QueryParam.AND);
        Assert.assertTrue(nestedAndFilter.isLogical());
        Assert.assertEquals(QueryParam.AND, nestedAndFilter.queryParam());
        Assert.assertEquals(2, nestedAndFilter.filters().size());


        // SERIALIZE TO STRING to test serialization logic
        String unitTestString = jsonUtil.toJsonString(queryFilter);

        // LOAD and Diffy the plain vanilla JSON versions of the documents
        Map<String, Object> actual = JsonUtils.jsonToMap(unitTestString);
        Map<String, Object> expected = JsonUtils.classpathToMap(testFixture);

        // Diffy the vanilla versions
        Diffy.Result result = diffy.diff(expected, actual);
        if (!result.isEmpty()) {
            Assert.fail("Failed.\nhere is a diff:\nexpected: " + JsonUtils.toJsonString(result.expected) + "\n  actual: " + JsonUtils.toJsonString(result.actual));
        }
    }

    public static class QueryFilterDeserializer extends JsonDeserializer<QueryFilter> {

        /**
         * Demonstrates how to do recursive polymorphic JSON deserialization in Jackson 2.2.
         * <p>
         * Aka specify a Deserializer and "catch" some input, determine what type of Class it
         * should be parsed too, and then reuse the Jackson infrastructure to recursively do so.
         */
        @Override
        public QueryFilter deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException {

            ObjectCodec objectCodec = jp.getCodec();
            ObjectNode root = jp.readValueAsTree();

            // Check if it is a "RealFilter"
            JsonNode queryParam = root.get("queryParam");
            if (queryParam != null && queryParam.isValueNode()) {

                // pass in our objectCodec so that the subJsonParser knows about our configured Modules and Annotations
                JsonParser subJsonParser = root.traverse(objectCodec);

                return subJsonParser.readValueAs(RealFilter.class);
            }

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

            return new LogicalFilter2(QueryParam.valueOf(key), childrenQueryFilters);
        }
    }

    public static class LogicalFilter2Serializer extends JsonSerializer<LogicalFilter2> {

        @Override
        public void serialize(LogicalFilter2 filter, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeObjectField(filter.queryParam().toString(), filter.filters().values());
            jgen.writeEndObject();
        }
    }
}
