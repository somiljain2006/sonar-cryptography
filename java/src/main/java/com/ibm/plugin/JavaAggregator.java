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
package com.ibm.plugin;

import com.ibm.engine.language.ILanguageSupport;
import com.ibm.engine.language.LanguageSupporter;
import com.ibm.mapper.model.INode;
import com.ibm.output.IAggregator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.sonar.plugins.java.api.JavaCheck;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.Tree;

public final class JavaAggregator implements IAggregator {

    private static ILanguageSupport<JavaCheck, Tree, Symbol, JavaFileScannerContext>
            javaLanguageSupport = LanguageSupporter.javaLanguageSupporter();

    private static volatile Consumer<INode> liveConsumer = null;
    private static int totalNodeCount = 0;
    private static final Map<Class<? extends INode>, Long> kindDistribution = new HashMap<>();

    private JavaAggregator() {
        // nothing
    }

    public static synchronized void registerConsumer(Consumer<INode> consumer) {
        if (liveConsumer != null && liveConsumer != consumer) {
            throw new IllegalStateException(
                    "A consumer is already registered. Concurrent multi-module scans are not supported with static aggregation.");
        }
        liveConsumer = consumer;
    }

    public static synchronized void addNodes(List<INode> newNodes) {
        for (INode node : newNodes) {
            recordNodeAndChildren(node);
            if (liveConsumer != null) {
                liveConsumer.accept(node);
            }
        }
        IAggregator.log(newNodes);
    }

    private static void recordNodeAndChildren(INode node) {
        totalNodeCount++;
        kindDistribution.merge(node.getKind(), 1L, Long::sum);

        if (node.hasChildren()) {
            node.getChildren();
            for (INode child : node.getChildren().values()) {
                recordNodeAndChildren(child);
            }
        }
    }

    public static synchronized int getTotalNodeCount() {
        return totalNodeCount;
    }

    public static synchronized Map<Class<? extends INode>, Long> getKindDistribution() {
        return new HashMap<>(kindDistribution);
    }

    @Nonnull
    public static ILanguageSupport<JavaCheck, Tree, Symbol, JavaFileScannerContext>
            getLanguageSupport() {
        return javaLanguageSupport;
    }

    public static synchronized void reset() {
        javaLanguageSupport = LanguageSupporter.javaLanguageSupporter();
        liveConsumer = null;
        totalNodeCount = 0;
        kindDistribution.clear();
    }
}
