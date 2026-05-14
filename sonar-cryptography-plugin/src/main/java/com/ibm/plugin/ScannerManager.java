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
import com.ibm.output.cyclondx.CBOMOutputFile;
import com.ibm.output.statistics.IStatistics;
import com.ibm.output.statistics.ScanStatistics;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ScannerManager {
    private final IOutputFileFactory outputFileFactory;
    private CBOMOutputFile liveOutputFile;
    private boolean nonJavaMerged = false;

    public ScannerManager(@Nullable IOutputFileFactory outputFileFactory) {
        this.outputFileFactory = outputFileFactory;
        this.liveOutputFile = new CBOMOutputFile();
        JavaAggregator.registerConsumer(liveOutputFile::accept);
    }

    @Nonnull
    public IOutputFile getOutputFile() {
        if (!nonJavaMerged) {
            liveOutputFile.add(
                    Stream.of(
                                    PythonAggregator.getDetectedNodes().stream(),
                                    GoAggregator.getDetectedNodes().stream(),
                                    CSharpAggregator.getDetectedNodes().stream())
                            .flatMap(s -> s));
            nonJavaMerged = true;
        }
        return liveOutputFile;
    }

    @Nonnull
    public IStatistics getStatistics() {
        return new ScanStatistics(
                () ->
                        JavaAggregator.getTotalNodeCount()
                                + PythonAggregator.getDetectedNodes().size()
                                + GoAggregator.getDetectedNodes().size()
                                + CSharpAggregator.getDetectedNodes().size(),
                () -> {
                    Map<Class<? extends INode>, Long> combined =
                            new HashMap<>(JavaAggregator.getKindDistribution());

                    Stream.of(
                                    PythonAggregator.getDetectedNodes(),
                                    GoAggregator.getDetectedNodes(),
                                    CSharpAggregator.getDetectedNodes())
                            .forEach(
                                    list -> {
                                        for (INode node : list) {
                                            Class<?> clazz = node.getKind();
                                            if (INode.class.isAssignableFrom(clazz)) {
                                                combined.merge(
                                                        clazz.asSubclass(INode.class),
                                                        1L,
                                                        Long::sum);
                                            }
                                        }
                                    });

                    return combined;
                });
    }

    public boolean hasResults() {
        return JavaAggregator.getTotalNodeCount() > 0
                || !PythonAggregator.getDetectedNodes().isEmpty()
                || !GoAggregator.getDetectedNodes().isEmpty()
                || !CSharpAggregator.getDetectedNodes().isEmpty();
    }

    public void reset() {
        JavaAggregator.reset();
        PythonAggregator.reset();
        GoAggregator.reset();
        CSharpAggregator.reset();
        this.liveOutputFile = new CBOMOutputFile();
        this.nonJavaMerged = false;
        JavaAggregator.registerConsumer(liveOutputFile::accept);
    }
}
