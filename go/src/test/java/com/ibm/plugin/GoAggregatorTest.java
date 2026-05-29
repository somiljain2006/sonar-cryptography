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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ibm.mapper.model.INode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoAggregatorTest {

    @BeforeEach
    void setUp() {
        GoAggregator.reset();
    }

    @Test
    void shouldProvideLanguageSupport() {
        assertThat(GoAggregator.getLanguageSupport()).isNotNull();
    }

    @Test
    void shouldStartWithEmptyCounts() {
        assertThat(GoAggregator.getTotalNodeCount()).isZero();
        assertThat(GoAggregator.getKindDistribution()).isEmpty();
    }

    @Test
    void shouldTrackNodeCountsAndDistributions() {
        INode mockNode = mock(INode.class);
        when(mockNode.getKind()).thenAnswer(invocation -> INode.class);

        GoAggregator.addNodes(List.of(mockNode, mockNode));

        assertThat(GoAggregator.getTotalNodeCount()).isEqualTo(2);
        assertThat(GoAggregator.getKindDistribution()).containsEntry(INode.class, 2L);
    }
}
