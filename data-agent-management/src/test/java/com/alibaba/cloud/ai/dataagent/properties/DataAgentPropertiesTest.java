/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataAgentPropertiesTest {

	@Test
	void defaultChartTimeoutAllowsStructuredOutputRepair() {
		DataAgentProperties properties = new DataAgentProperties();

		assertEquals(15000L, properties.getEnrichSqlResultTimeout());
	}

	@Test
	void embeddingRecoveryDefaultsToStartupAndScheduledSync() {
		DataAgentProperties properties = new DataAgentProperties();

		assertEquals(300000L, properties.getEmbeddingRecovery().getInitialDelayMs());
		assertEquals(300000L, properties.getEmbeddingRecovery().getFixedDelayMs());
		assertTrue(properties.getEmbeddingRecovery().getBusinessKnowledge().isStartupSyncEnabled());
		assertTrue(properties.getEmbeddingRecovery().getBusinessKnowledge().isScheduledSyncEnabled());
		assertTrue(properties.getEmbeddingRecovery().getAgentKnowledge().isStartupSyncEnabled());
		assertTrue(properties.getEmbeddingRecovery().getAgentKnowledge().isScheduledSyncEnabled());
	}

}
