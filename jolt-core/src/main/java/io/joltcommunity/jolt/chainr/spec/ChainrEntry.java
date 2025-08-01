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
package io.joltcommunity.jolt.chainr.spec;

import io.joltcommunity.jolt.exception.SpecException;
import io.joltcommunity.jolt.removr.Removr;
import io.joltcommunity.jolt.utils.StringTools;
import io.joltcommunity.jolt.*;

import java.util.Map;

import static java.util.Map.entry;

/**
 * Helper class that encapsulates the information one of the individual transform entries in
 * the Chainr spec's list.
 */
public class ChainrEntry {

    /**
     * Map transform "operation" names to the classes that handle them
     */
    public static final Map<String, String> STOCK_TRANSFORMS;
    public static final String OPERATION_KEY = "operation";
    public static final String SPEC_KEY = "spec";

    /**
     * getName() returns fqdn$path compared to humanReadablePath from getCanonicalPath()
     * to make internal classes available/loadable at runtime it is imperative that we use fqdn
     */
    static {
        STOCK_TRANSFORMS = Map.ofEntries(
                entry("shift", Shiftr.class.getName()),
                entry("default", Defaultr.class.getName()),
                entry("modify-overwrite-beta", Modifier.Overwritr.class.getName()),
                entry("modify-overwrite", Modifier.Overwritr.class.getName()),
                entry("modify-default-beta", Modifier.Defaultr.class.getName()),
                entry("modify-default", Modifier.Defaultr.class.getName()),
                entry("modify-define-beta", Modifier.Definr.class.getName()),
                entry("modify-define", Modifier.Definr.class.getName()),
                entry("remove", Removr.class.getName()),
                entry("sort", Sortr.class.getName()),
                entry("cardinality", CardinalityTransform.class.getName())
        );
    }

    private final int index;
    private final Object spec;
    private final String operationClassName;

    private final Class<? extends JoltTransform> joltTransformClass;
    private final boolean isSpecDriven;

    /**
     * Process an element from the Chainr Spec into a ChainrEntry class.
     * This method tries to validate the syntax of the Chainr spec, whereas
     * the ChainrInstantiator deals with loading the Transform classes.
     *
     * @param chainrEntryObj the unknown Object from the Chainr list
     * @param index          the index of the chainrEntryObj, used in reporting errors
     */
    public ChainrEntry(int index, Object chainrEntryObj, ClassLoader classLoader) {

        if (!(chainrEntryObj instanceof Map)) {
            throw new SpecException("JOLT ChainrEntry expects a JSON map - Malformed spec" + getErrorMessageIndexSuffix());
        }

        @SuppressWarnings("unchecked") // We know it is a Map due to the check above
        Map<String, Object> chainrEntryMap = (Map<String, Object>) chainrEntryObj;

        this.index = index;

        String opString = extractOperationString(chainrEntryMap);

        if (opString == null) {
            throw new SpecException("JOLT Chainr 'operation' must implement Transform or ContextualTransform" + getErrorMessageIndexSuffix());
        }

        operationClassName = STOCK_TRANSFORMS.getOrDefault(opString, opString);

        joltTransformClass = loadJoltTransformClass(classLoader);

        spec = chainrEntryMap.get(ChainrEntry.SPEC_KEY);

        isSpecDriven = SpecDriven.class.isAssignableFrom(joltTransformClass);
        if (isSpecDriven && !chainrEntryMap.containsKey(SPEC_KEY)) {
            throw new SpecException("JOLT Chainr - Transform className:" + joltTransformClass.getName() + " requires a spec" + getErrorMessageIndexSuffix());
        }
    }

    private String extractOperationString(Map<String, Object> chainrEntryMap) {

        Object operationNameObj = chainrEntryMap.get(ChainrEntry.OPERATION_KEY);
        if (operationNameObj == null) {
            return null;
        } else if (operationNameObj instanceof String) {
            if (StringTools.isBlank((String) operationNameObj)) {
                throw new SpecException("JOLT Chainr '" + ChainrEntry.OPERATION_KEY + "' should not be blank" + getErrorMessageIndexSuffix());
            }
            return (String) operationNameObj;
        } else {
            throw new SpecException("JOLT Chainr needs a '" + ChainrEntry.OPERATION_KEY + "' of type String" + getErrorMessageIndexSuffix());
        }
    }

    private Class<? extends JoltTransform> loadJoltTransformClass(ClassLoader classLoader) {

        try {
            Class<?> opClass = classLoader.loadClass(operationClassName);

            if (Chainr.class.isAssignableFrom(opClass)) {
                throw new SpecException("Attempt to nest Chainr inside itself" + getErrorMessageIndexSuffix());
            }

            if (!JoltTransform.class.isAssignableFrom(opClass)) {
                throw new SpecException("JOLT Chainr class:" + operationClassName + " does not implement the JoltTransform interface" + getErrorMessageIndexSuffix());
            }

            @SuppressWarnings("unchecked") // We know it is some type of Transform due to the check above
            Class<? extends JoltTransform> transformClass = (Class<? extends JoltTransform>) opClass;

            return transformClass;

        } catch (ClassNotFoundException e) {
            throw new SpecException("JOLT Chainr could not find transform class:" + operationClassName + getErrorMessageIndexSuffix(), e);
        }
    }


    /**
     * Generate an error message suffix what lists the index of the ChainrEntry in the overall ChainrSpec.
     */
    public String getErrorMessageIndexSuffix() {
        return " at index:" + index + ".";
    }

    /**
     * @return Spec for the transform, can be null
     */
    public Object getSpec() {
        return spec;
    }

    /**
     * @return Class instance specified by this ChainrEntry
     */
    public Class<? extends JoltTransform> getJoltTransformClass() {
        return joltTransformClass;
    }

    /**
     * @return true if the Jolt Transform specified by this ChainrEntry implements the SpecTransform interface
     */
    public boolean isSpecDriven() {
        return isSpecDriven;
    }
}
