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

package io.joltcommunity.jolt.modifier.spec;

import io.joltcommunity.jolt.common.Optional;
import io.joltcommunity.jolt.common.SpecStringParser;
import io.joltcommunity.jolt.common.tree.MatchedElement;
import io.joltcommunity.jolt.common.tree.WalkedPath;
import io.joltcommunity.jolt.modifier.OpMode;
import io.joltcommunity.jolt.modifier.ModifierSpecBuilder;
import io.joltcommunity.jolt.modifier.function.Function;
import io.joltcommunity.jolt.modifier.function.FunctionArg;
import io.joltcommunity.jolt.modifier.function.FunctionEvaluator;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecated")
public class ModifierLeafSpec extends ModifierSpec {

    private final List<FunctionEvaluator> functionEvaluatorList;

    @SuppressWarnings("unchecked")
    public ModifierLeafSpec(final String rawJsonKey, Object rhsObj, final OpMode opMode, final Map<String, Function> functionsMap) {
        super(rawJsonKey, opMode);
        functionEvaluatorList = new LinkedList<>();

        FunctionEvaluator functionEvaluator;

        // "key": "expression1"
        if ((rhsObj instanceof String)) {
            functionEvaluator = buildFunctionEvaluator((String) rhsObj, functionsMap);
            functionEvaluatorList.add(functionEvaluator);
        }
        // "key": ["expression1", "expression2", "expression3"]
        else if (rhsObj instanceof List rhsList && !rhsList.isEmpty()) {
            for (Object rhs : rhsList) {
                if (rhs instanceof String) {
                    functionEvaluator = buildFunctionEvaluator(rhs.toString(), functionsMap);
                    functionEvaluatorList.add(functionEvaluator);
                } else {
                    functionEvaluator = FunctionEvaluator.forArgEvaluation(FunctionArg.forLiteral(rhs, false));
                    functionEvaluatorList.add(functionEvaluator);
                }
            }
        }
        // "key": anyObjectOrLiteral --- just set as-is
        else {
            functionEvaluator = FunctionEvaluator.forArgEvaluation(FunctionArg.forLiteral(rhsObj, false));
            functionEvaluatorList.add(functionEvaluator);
        }
    }

    private static FunctionEvaluator buildFunctionEvaluator(final String rhs, final Map<String, Function> functionsMap) {
        final FunctionEvaluator functionEvaluator;
        // "key": "@0" --- evaluate expression then set
        if (!rhs.startsWith(ModifierSpecBuilder.FUNCTION)) {
            return FunctionEvaluator.forArgEvaluation(constructSingleArg(rhs, false));
        } else {
            String functionName;
            // "key": "=abs" --- call function with current value then set output if present
            if (!rhs.contains("(") && !rhs.endsWith(")")) {
                functionName = rhs.substring(ModifierSpecBuilder.FUNCTION.length());
                return FunctionEvaluator.forFunctionEvaluation(functionsMap.get(functionName));
            }
            // "key": "=abs(@(1,&0))" --- evaluate expression then call function with
            //                            expression-output, then set output if present
            else {
                String fnString = rhs.substring(ModifierSpecBuilder.FUNCTION.length());
                List<String> fnArgs = SpecStringParser.parseFunctionArgs(fnString);
                functionName = fnArgs.remove(0);
                functionEvaluator = FunctionEvaluator.forFunctionEvaluation(functionsMap.get(functionName), constructArgs(fnArgs));
            }
        }
        return functionEvaluator;
    }

    private static Optional<Object> getFirstAvailable(List<FunctionEvaluator> functionEvaluatorList, Optional<Object> inputOptional, WalkedPath walkedPath, Map<String, Object> context) {
        Optional<Object> valueOptional = Optional.empty();
        for (FunctionEvaluator functionEvaluator : functionEvaluatorList) {
            try {
                valueOptional = functionEvaluator.evaluate(inputOptional, walkedPath, context);
                if (valueOptional.isPresent()) {
                    return valueOptional;
                }
            } catch (Exception ignored) {
            }
        }
        return valueOptional;
    }

    private static FunctionArg[] constructArgs(List<String> argsList) {
        FunctionArg[] argsArray = new FunctionArg[argsList.size()];
        for (int i = 0; i < argsList.size(); i++) {
            String arg = argsList.get(i);
            argsArray[i] = constructSingleArg(arg, true);
        }
        return argsArray;
    }

    private static FunctionArg constructSingleArg(String arg, boolean forFunction) {
        if (arg.startsWith(ModifierSpecBuilder.CARET)) {
            return FunctionArg.forContext(TRAVERSAL_BUILDER.build(arg.substring(1)));
        } else if (arg.startsWith(ModifierSpecBuilder.AT)) {
            return FunctionArg.forSelf(TRAVERSAL_BUILDER.build(arg));
        } else {
            return FunctionArg.forLiteral(arg, forFunction);
        }
    }

    @Override
    public void applyElement(final String inputKey, final Optional<Object> inputOptional, final MatchedElement thisLevel, final WalkedPath walkedPath, final Map<String, Object> context) {

        Object parent = walkedPath.lastElement().getTreeRef();

        walkedPath.add(inputOptional.get(), thisLevel);

        Optional<Object> valueOptional = getFirstAvailable(functionEvaluatorList, inputOptional, walkedPath, context);

        if (valueOptional.isPresent()) {
            setData(parent, thisLevel, valueOptional.get(), opMode);
        }

        walkedPath.removeLastElement();
    }
}
