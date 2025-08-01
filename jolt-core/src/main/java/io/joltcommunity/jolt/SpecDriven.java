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
package io.joltcommunity.jolt;

/**
 * Marker interface for Jolt Transforms that are based off a "spec".
 * <p>
 * Implementations of this interface are expected to have a single arg constructor,
 * which takes an {@link Object} that is the spec for the constructed instance.
 * Chainr leverages this to instantiate these objects correctly.
 * <p>
 * Additionally, all {@link SpecDriven} implementations should mark their constructor
 * with the {@link jakarta.inject.Inject} annotation, so that they can be loaded via
 * Dependency Injection systems.
 * <p>
 * All of the "stock" Jolt {@link SpecDriven} transforms are marked with {@link jakarta.inject.Inject}.
 * <p>
 * Ideally, calls to the transform method are expected to be stateless and multi-thread safe.
 */
public interface SpecDriven {

    String ROOT_KEY = "root";

}
