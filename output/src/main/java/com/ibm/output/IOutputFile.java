/*
 * Sonar Cryptography Plugin
 * Copyright (C) 2024 PQCA
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.output;

import com.ibm.mapper.model.INode;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents the output target for aggregated scan results. *
 *
 * <p><b>State Semantics & Recursion Invariant:</b> The {@link INode} tree structure passed to this
 * interface <b>must be acyclic (no circular references)</b>. Downstream serialization components
 * and statistical counters traverse these nodes recursively; introducing a cycle in the node graph
 * will result in infinite loops or {@code StackOverflowError}s.
 */
public interface IOutputFile extends Consumer<INode> {

    /**
     * Adds a collection or sequence of nodes to the output structure.
     *
     * @param nodes an {@link Iterable} of {@link INode} objects
     */
    void add(Iterable<INode> nodes);

    /**
     * Ergonomic helper to add a single node directly. Naturally aligns with functional streaming
     * patterns.
     *
     * @param node a single {@link INode} to append
     */
    @Override
    default void accept(INode node) {
        add(List.of(node));
    }

    /**
     * Backward-compatibility shim for legacy callers compiled against the List signature.
     *
     * @deprecated Use {@link #add(Iterable)} instead.
     */
    @Deprecated(forRemoval = true)
    default void add(List<INode> nodes) {
        add((Iterable<INode>) nodes);
    }

    void saveTo(File file);
}
