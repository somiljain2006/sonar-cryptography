/*
 * Sonar Cryptography Plugin
 * Copyright (C) 2026 PQCA
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ibm.mapper.model.INode;
import com.ibm.output.IOutputFile;
import com.ibm.output.IOutputFileFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ScannerManagerTest {

    private MockedStatic<JavaAggregator> javaMock;
    private MockedStatic<PythonAggregator> pythonMock;
    private MockedStatic<GoAggregator> goMock;
    private MockedStatic<CSharpAggregator> csharpMock;

    private INode javaNode;
    private INode pythonNode;
    private INode goNode;
    private INode csharpNode;

    @BeforeEach
    void setUp() {
        javaNode = mock(INode.class);
        pythonNode = mock(INode.class);
        goNode = mock(INode.class);
        csharpNode = mock(INode.class);

        javaMock = mockStatic(JavaAggregator.class);
        pythonMock = mockStatic(PythonAggregator.class);
        goMock = mockStatic(GoAggregator.class);
        csharpMock = mockStatic(CSharpAggregator.class);

        javaMock.when(JavaAggregator::getTotalNodeCount).thenReturn(0);
        javaMock.when(JavaAggregator::getKindDistribution).thenReturn(Map.of());
        javaMock.when(() -> JavaAggregator.registerConsumer(any())).thenAnswer(inv -> null);
        javaMock.when(JavaAggregator::reset).thenAnswer(inv -> null);

        pythonMock.when(PythonAggregator::getDetectedNodes).thenReturn(List.of());
        pythonMock.when(PythonAggregator::reset).thenAnswer(inv -> null);

        goMock.when(GoAggregator::getDetectedNodes).thenReturn(List.of());
        goMock.when(GoAggregator::reset).thenAnswer(inv -> null);

        csharpMock.when(CSharpAggregator::getDetectedNodes).thenReturn(List.of());
        csharpMock.when(CSharpAggregator::reset).thenAnswer(inv -> null);
    }

    @AfterEach
    void tearDown() {
        javaMock.close();
        pythonMock.close();
        goMock.close();
        csharpMock.close();
    }

    @Test
    void constructor_registersConsumerWithJavaAggregator() {
        new ScannerManager(null);

        javaMock.verify(() -> JavaAggregator.registerConsumer(any()), times(1));
    }

    @Test
    void constructor_registersConsumerThatDelegatesToLiveOutputFile() {
        new ScannerManager(null);

        javaMock.verify(() -> JavaAggregator.registerConsumer(argThat(Objects::nonNull)), times(1));
    }

    @Test
    void getOutputFile_returnsNonNullOutputFile() {
        ScannerManager manager = new ScannerManager(null);
        IOutputFile result = manager.getOutputFile();

        assertThat(result).isNotNull();
    }

    @Test
    void getOutputFile_returnsSameInstanceOnMultipleCalls() {
        ScannerManager manager = new ScannerManager(null);

        IOutputFile first = manager.getOutputFile();
        IOutputFile second = manager.getOutputFile();

        assertThat(first).isSameAs(second);
    }

    @Test
    void getOutputFile_mergesNonJavaNodesExactlyOnce() {
        pythonMock.when(PythonAggregator::getDetectedNodes).thenReturn(List.of(pythonNode));
        goMock.when(GoAggregator::getDetectedNodes).thenReturn(List.of(goNode));
        csharpMock.when(CSharpAggregator::getDetectedNodes).thenReturn(List.of(csharpNode));

        ScannerManager manager = new ScannerManager(null);

        IOutputFile first = manager.getOutputFile();
        IOutputFile second = manager.getOutputFile();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        pythonMock.verify(PythonAggregator::getDetectedNodes, times(1));
        goMock.verify(GoAggregator::getDetectedNodes, times(1));
        csharpMock.verify(CSharpAggregator::getDetectedNodes, times(1));
    }

    @Test
    void getOutputFile_usesCustomFactory_whenProvided() {
        IOutputFileFactory factory = mock(IOutputFileFactory.class);
        new ScannerManager(factory);
        verifyNoInteractions(factory);
    }

    @Test
    void getStatistics_totalNodeCount_sumAllLanguages() {
        //noinspection ResultOfMethodCallIgnored
        javaMock.when(JavaAggregator::getTotalNodeCount).thenReturn(3);
        pythonMock
                .when(PythonAggregator::getDetectedNodes)
                .thenReturn(List.of(pythonNode, pythonNode));
        goMock.when(GoAggregator::getDetectedNodes).thenReturn(List.of(goNode));
        csharpMock.when(CSharpAggregator::getDetectedNodes).thenReturn(List.of(csharpNode));

        when(pythonNode.getKind()).thenAnswer(inv -> pythonNode.getClass());
        when(goNode.getKind()).thenAnswer(inv -> goNode.getClass());
        when(csharpNode.getKind()).thenAnswer(inv -> csharpNode.getClass());

        ScannerManager manager = new ScannerManager(null);
        List<String> output = new ArrayList<>();

        manager.getStatistics().print(output::add);

        assertThat(output).isNotEmpty();
    }

    @Test
    void getStatistics_kindDistribution_includesJavaNodes() {
        Class<? extends INode> kind = javaNode.getClass().asSubclass(INode.class);
        javaMock.when(JavaAggregator::getKindDistribution).thenReturn(Map.of(kind, 5L));

        ScannerManager manager = new ScannerManager(null);
        List<String> output = new ArrayList<>();

        manager.getStatistics().print(output::add);

        assertThat(output).anyMatch(line -> line.contains("5"));
    }

    @Test
    void getStatistics_kindDistribution_includesNonJavaNodes() {
        when(pythonNode.getKind()).thenAnswer(inv -> pythonNode.getClass());
        when(goNode.getKind()).thenAnswer(inv -> goNode.getClass());
        when(csharpNode.getKind()).thenAnswer(inv -> csharpNode.getClass());

        pythonMock.when(PythonAggregator::getDetectedNodes).thenReturn(List.of(pythonNode));
        goMock.when(GoAggregator::getDetectedNodes).thenReturn(List.of(goNode));
        csharpMock.when(CSharpAggregator::getDetectedNodes).thenReturn(List.of(csharpNode));

        ScannerManager manager = new ScannerManager(null);

        assertThatNoException().isThrownBy(() -> manager.getStatistics().print(line -> {}));
    }

    @Test
    void getStatistics_returnsNonNull_whenAllAggregatorsEmpty() {
        ScannerManager manager = new ScannerManager(null);

        assertThat(manager.getStatistics()).isNotNull();
    }

    @Test
    void hasResults_returnsFalse_whenAllAggregatorsEmpty() {
        ScannerManager manager = new ScannerManager(null);

        assertThat(manager.hasResults()).isFalse();
    }

    @Test
    void hasResults_returnsTrue_whenJavaHasNodes() {
        //noinspection ResultOfMethodCallIgnored
        javaMock.when(JavaAggregator::getTotalNodeCount).thenReturn(1);

        ScannerManager manager = new ScannerManager(null);

        assertThat(manager.hasResults()).isTrue();
    }

    @Test
    void hasResults_returnsTrue_whenPythonHasNodes() {
        pythonMock.when(PythonAggregator::getDetectedNodes).thenReturn(List.of(pythonNode));

        ScannerManager manager = new ScannerManager(null);

        assertThat(manager.hasResults()).isTrue();
    }

    @Test
    void hasResults_returnsTrue_whenGoHasNodes() {
        goMock.when(GoAggregator::getDetectedNodes).thenReturn(List.of(goNode));

        ScannerManager manager = new ScannerManager(null);

        assertThat(manager.hasResults()).isTrue();
    }

    @Test
    void hasResults_returnsTrue_whenCSharpHasNodes() {
        csharpMock.when(CSharpAggregator::getDetectedNodes).thenReturn(List.of(csharpNode));

        ScannerManager manager = new ScannerManager(null);

        assertThat(manager.hasResults()).isTrue();
    }

    @Test
    void reset_callsResetOnAllAggregators() {
        ScannerManager manager = new ScannerManager(null);
        manager.reset();

        javaMock.verify(JavaAggregator::reset, times(1));
        pythonMock.verify(PythonAggregator::reset, times(1));
        goMock.verify(GoAggregator::reset, times(1));
        csharpMock.verify(CSharpAggregator::reset, times(1));
    }

    @Test
    void reset_reRegistersConsumerWithJavaAggregator() {
        ScannerManager manager = new ScannerManager(null);
        manager.reset();

        javaMock.verify(() -> JavaAggregator.registerConsumer(any()), times(2));
    }

    @Test
    void reset_allowsNonJavaNodesToBeMergedAgainOnNextGetOutputFile() {
        pythonMock.when(PythonAggregator::getDetectedNodes).thenReturn(List.of(pythonNode));

        ScannerManager manager = new ScannerManager(null);

        IOutputFile firstOutput = manager.getOutputFile();
        manager.reset();
        IOutputFile secondOutput = manager.getOutputFile();

        assertThat(firstOutput).isNotNull();
        assertThat(secondOutput).isNotNull();

        pythonMock.verify(PythonAggregator::getDetectedNodes, times(2));
    }

    @Test
    void reset_outputFileIsNewInstance_notThePreviousOne() {
        ScannerManager manager = new ScannerManager(null);

        IOutputFile before = manager.getOutputFile();
        manager.reset();
        IOutputFile after = manager.getOutputFile();

        assertThat(before).isNotSameAs(after);
    }
}
