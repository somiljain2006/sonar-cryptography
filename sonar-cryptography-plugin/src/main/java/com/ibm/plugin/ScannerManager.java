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

import com.ibm.mapper.model.INode;
import com.ibm.output.IOutputFile;
import com.ibm.output.IOutputFileFactory;
import com.ibm.output.statistics.IStatistics;
import com.ibm.output.statistics.ScanStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ScannerManager {
    private static final List<String> AGGREGATORS =
            List.of(
                    "com.ibm.plugin.JavaAggregator",
                    "com.ibm.plugin.PythonAggregator",
                    "com.ibm.plugin.GoAggregator",
                    "com.ibm.plugin.CSharpAggregator");

    private final IOutputFileFactory outputFileFactory;
    private IOutputFile liveOutputFile;

    public ScannerManager(@Nullable IOutputFileFactory outputFileFactory) {
        this.outputFileFactory =
                outputFileFactory != null ? outputFileFactory : IOutputFileFactory.DEFAULT;
        initialize();
    }

    private void initialize() {
        Iterable<INode> emptyIterable = List.of();
        this.liveOutputFile = this.outputFileFactory.createOutputFormat(emptyIterable);

        Consumer<INode> streamingConsumer =
                node -> {
                    Iterable<INode> singleNodeIterable = List.of(node);
                    this.liveOutputFile.add(singleNodeIterable);
                };

        for (String aggregator : AGGREGATORS) {
            registerConsumerWithAggregator(aggregator, streamingConsumer);
        }
    }

    /**
     * Retrieves the current output file containing the aggregated scan results.
     *
     * <p><b>Lifecycle / State Semantics:</b>
     *
     * <ul>
     *   <li><b>Live Reference:</b> This method returns a reference to the <i>live</i> {@link
     *       IOutputFile} instance actively being populated by the sensor streams.
     *   <li><b>Safe Early Invocation:</b> Retrieving this object before the scan finishes will
     *       <b>not</b> freeze its state or permanently produce incomplete output. The returned
     *       instance will automatically reflect new nodes as they are detected.
     *   <li><b>Finalization:</b> While the reference can be obtained at any time, consumers <b>must
     *       wait</b> until the entire scanner execution lifecycle is complete before serializing or
     *       exporting the file contents to ensure a complete dataset.
     * </ul>
     *
     * @return The live, continuously updatable output file.
     */
    @Nonnull
    public synchronized IOutputFile getOutputFile() {
        return liveOutputFile;
    }

    @Nonnull
    public IStatistics getStatistics() {
        return new ScanStatistics(
                () -> {
                    long total = 0;
                    for (String aggregator : AGGREGATORS) {
                        total += getTotalNodeCount(aggregator);
                    }
                    return (int) total;
                },
                () -> {
                    Map<Class<? extends INode>, Long> combined = new HashMap<>();
                    for (String aggregator : AGGREGATORS) {
                        getKindDistribution(aggregator)
                                .forEach((k, v) -> combined.merge(k, v, Long::sum));
                    }
                    return combined;
                });
    }

    public boolean hasResults() {
        for (String aggregator : AGGREGATORS) {
            if (getTotalNodeCount(aggregator) > 0) {
                return true;
            }
        }
        return false;
    }

    public synchronized void reset() {
        for (String aggregator : AGGREGATORS) {
            resetAggregator(aggregator);
        }
        initialize();
    }

    private void registerConsumerWithAggregator(String className, Consumer<INode> consumer) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Method method = clazz.getMethod("registerConsumer", Consumer.class);
            method.invoke(null, consumer);
        } catch (ClassNotFoundException e) {
            // Language module not active/loaded at runtime
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long getTotalNodeCount(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Method method = clazz.getMethod("getTotalNodeCount");
            return ((Number) method.invoke(null)).longValue();
        } catch (ClassNotFoundException e) {
            return 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Class<? extends INode>, Long> getKindDistribution(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Method method = clazz.getMethod("getKindDistribution");
            return (Map<Class<? extends INode>, Long>) method.invoke(null);
        } catch (ClassNotFoundException e) {
            return Map.of();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void resetAggregator(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Method method = clazz.getMethod("reset");
            method.invoke(null);
        } catch (ClassNotFoundException e) {
            // Language module not active/loaded at runtime
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
