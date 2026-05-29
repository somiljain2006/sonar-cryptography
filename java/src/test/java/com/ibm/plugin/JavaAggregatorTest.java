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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.ibm.mapper.model.INode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaAggregatorTest {

    @BeforeEach
    void setUp() {
        JavaAggregator.reset();
    }

    @Test
    void reset_clearsNodeCount() {
        INode node = mockNode(INode.class);
        JavaAggregator.addNodes(List.of(node, node));

        JavaAggregator.reset();

        assertThat(JavaAggregator.getTotalNodeCount()).isZero();
    }

    @Test
    void reset_clearsKindDistribution() {
        INode node = mockNode(INode.class);
        JavaAggregator.addNodes(List.of(node));

        JavaAggregator.reset();

        assertThat(JavaAggregator.getKindDistribution()).isEmpty();
    }

    @Test
    void reset_deregistersConsumer() {
        List<INode> received = new ArrayList<>();
        JavaAggregator.registerConsumer(received::add);

        JavaAggregator.reset();

        INode node = mockNode(INode.class);
        JavaAggregator.addNodes(List.of(node));

        assertThat(received).isEmpty();
    }

    @Test
    void reset_isIdempotent() {
        JavaAggregator.reset();
        JavaAggregator.reset();

        assertThat(JavaAggregator.getTotalNodeCount()).isZero();
    }

    @Test
    void addNodes_incrementsCountByListSize() {
        INode a = mockNode(INode.class);
        INode b = mockNode(INode.class);

        JavaAggregator.addNodes(List.of(a, b));

        assertThat(JavaAggregator.getTotalNodeCount()).isEqualTo(2);
    }

    @Test
    void addNodes_accumulatesAcrossMultipleCalls() {
        INode node = mockNode(INode.class);

        JavaAggregator.addNodes(List.of(node));
        JavaAggregator.addNodes(List.of(node, node));

        assertThat(JavaAggregator.getTotalNodeCount()).isEqualTo(3);
    }

    @Test
    void addNodes_emptyList_doesNotChangeCount() {
        JavaAggregator.addNodes(List.of());

        assertThat(JavaAggregator.getTotalNodeCount()).isZero();
    }

    @Test
    void addNodes_tracksKindDistribution_singleKind() {
        INode a = mockNode(AlgorithmStub.class);
        INode b = mockNode(AlgorithmStub.class);

        JavaAggregator.addNodes(List.of(a, b));

        Map<Class<? extends INode>, Long> dist = JavaAggregator.getKindDistribution();
        assertThat(dist).containsEntry(AlgorithmStub.class, 2L);
    }

    @Test
    void addNodes_tracksKindDistribution_multipleKinds() {
        INode algo = mockNode(AlgorithmStub.class);
        INode key = mockNode(KeyStub.class);

        JavaAggregator.addNodes(List.of(algo, key, algo));

        Map<Class<? extends INode>, Long> dist = JavaAggregator.getKindDistribution();
        assertThat(dist).containsEntry(AlgorithmStub.class, 2L).containsEntry(KeyStub.class, 1L);
    }

    @Test
    void getKindDistribution_returnsSnapshot_notLiveReference() {
        INode node = mockNode(AlgorithmStub.class);
        JavaAggregator.addNodes(List.of(node));

        Map<Class<? extends INode>, Long> snapshot = JavaAggregator.getKindDistribution();

        JavaAggregator.addNodes(List.of(node));

        assertThat(snapshot).containsEntry(AlgorithmStub.class, 1L);
    }

    @Test
    void addNodes_forwardsToRegisteredConsumer() {
        List<INode> received = new ArrayList<>();
        JavaAggregator.registerConsumer(received::add);

        INode a = mockNode(INode.class);
        INode b = mockNode(INode.class);
        JavaAggregator.addNodes(List.of(a, b));

        assertThat(received).containsExactly(a, b);
    }

    @Test
    void addNodes_withNoConsumer_doesNotThrow() {
        INode node = mockNode(INode.class);

        JavaAggregator.addNodes(List.of(node));

        assertThat(JavaAggregator.getTotalNodeCount()).isEqualTo(1);
    }

    @Test
    void registerConsumer_throwsOnDuplicateRegistration() {
        List<INode> first = new ArrayList<>();
        List<INode> second = new ArrayList<>();

        JavaAggregator.registerConsumer(first::add);

        assertThatThrownBy(() -> JavaAggregator.registerConsumer(second::add))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
    }

    @Test
    void stressTest_concurrentAddNodesAndReset() throws InterruptedException {
        int threadCount = 50;
        int iterationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        INode node = mockNode(AlgorithmStub.class);
        List<INode> payload = List.of(node, node, node);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < iterationsPerThread; j++) {
                                if (threadId == 0 && j % 100 == 0) {
                                    JavaAggregator.reset();
                                } else {
                                    JavaAggregator.addNodes(payload);

                                    assertThat(JavaAggregator.getKindDistribution()).isNotNull();
                                    assertThat(JavaAggregator.getTotalNodeCount())
                                            .isGreaterThanOrEqualTo(0);
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed)
                .as("Stress test timed out! Possible deadlock in Aggregator.")
                .isTrue();

        assertThat(JavaAggregator.getTotalNodeCount()).isGreaterThanOrEqualTo(0);
    }

    private <T extends INode> T mockNode(Class<T> kind) {
        T mock = mock(kind);
        doReturn(kind).when(mock).getKind();
        return mock;
    }

    interface AlgorithmStub extends INode {}

    interface KeyStub extends INode {}
}
