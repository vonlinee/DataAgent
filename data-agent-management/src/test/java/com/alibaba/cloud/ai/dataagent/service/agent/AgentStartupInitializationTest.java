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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.enums.AgentStatus;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import com.alibaba.cloud.ai.dataagent.service.business.BusinessKnowledgeService;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentStartupInitializationTest {

	@Mock
	private AgentService agentService;

	@Mock
	private AgentVectorStoreService agentVectorStoreService;

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private BusinessKnowledgeService businessKnowledgeService;

	@Mock
	private AgentKnowledgeService agentKnowledgeService;

	@Mock
	private BusinessKnowledgeMapper businessKnowledgeMapper;

	@Mock
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Mock
	private ModelConfigDataService modelConfigDataService;

	@Mock
	private ExecutorService executorService;

	@Mock
	private ApplicationArguments applicationArguments;

	private DataAgentProperties properties;

	private AgentStartupInitialization initialization;

	@BeforeEach
	void setUp() {
		lenient().doAnswer(invocation -> {
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executorService).execute(any(Runnable.class));
		lenient().when(agentService.findByStatus(AgentStatus.PUBLISHED.getStatus())).thenReturn(List.of());
		properties = new DataAgentProperties();
		initialization = new AgentStartupInitialization(agentService, agentVectorStoreService, agentDatasourceService,
				businessKnowledgeService, agentKnowledgeService, businessKnowledgeMapper, agentKnowledgeMapper,
				modelConfigDataService, executorService, properties);
	}

	@Test
	void run_withoutActiveEmbeddingModel_keepsPendingKnowledgeUntouched() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		initialization.run(applicationArguments);

		verify(businessKnowledgeService, never()).syncPendingKnowledge();
		verify(agentKnowledgeMapper, never()).selectPendingAndRecalled();
	}

	@Test
	void run_withActiveEmbeddingModel_checksBothPendingKnowledgeSources() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(ModelConfigDTO.builder().build());
		initialization.run(applicationArguments);

		verify(businessKnowledgeService).syncPendingKnowledge();
		verify(agentKnowledgeService).syncPendingKnowledge();
	}

	@Test
	void run_withBusinessStartupSyncDisabled_onlySyncsAgentKnowledge() {
		properties.getEmbeddingRecovery().getBusinessKnowledge().setStartupSyncEnabled(false);
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING))
			.thenReturn(ModelConfigDTO.builder().build());

		initialization.run(applicationArguments);

		verify(businessKnowledgeService, never()).syncPendingKnowledge();
		verify(agentKnowledgeService).syncPendingKnowledge();
	}

	@Test
	void run_schemaExistsForActiveDatasource_skipsDuplicateInitialization() {
		Agent agent = Agent.builder().id(1L).name(AgentStatus.PUBLISHED.getStatus()).status(AgentStatus.PUBLISHED.getStatus()).build();
		AgentDatasource datasource = new AgentDatasource();
		datasource.setDatasourceId(3);
		datasource.setSelectTables(List.of("orders"));
		when(agentService.findByStatus(AgentStatus.PUBLISHED.getStatus())).thenReturn(List.of(agent));
		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(datasource);
		when(agentVectorStoreService.hasTableDocuments(3, List.of("orders"))).thenReturn(true);
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		initialization.run(applicationArguments);

		verify(agentDatasourceService, never()).initializeSchemaForAgentWithDatasource(any(), any(), any());
	}

	@Test
	void run_onlyBusinessDocumentsExist_initializesMissingSchemaForActiveDatasource() {
		Agent agent = Agent.builder().id(1L).name(AgentStatus.PUBLISHED.getStatus()).status(AgentStatus.PUBLISHED.getStatus()).build();
		AgentDatasource datasource = new AgentDatasource();
		datasource.setDatasourceId(3);
		datasource.setSelectTables(List.of("orders"));
		when(agentService.findByStatus(AgentStatus.PUBLISHED.getStatus())).thenReturn(List.of(agent));
		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(datasource);
		when(agentVectorStoreService.hasTableDocuments(3, List.of("orders"))).thenReturn(false);
		when(agentDatasourceService.initializeSchemaForAgentWithDatasource(1L, 3, List.of("orders"))).thenReturn(true);
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		initialization.run(applicationArguments);

		verify(agentDatasourceService).initializeSchemaForAgentWithDatasource(1L, 3, List.of("orders"));
	}

	@Test
	void scheduledSyncDisabled_skipsBothKnowledgeSources() {
		properties.getEmbeddingRecovery().getBusinessKnowledge().setScheduledSyncEnabled(false);
		properties.getEmbeddingRecovery().getAgentKnowledge().setScheduledSyncEnabled(false);

		initialization.recoverPendingEmbeddings();

		verify(modelConfigDataService, never()).getActiveConfigByType(ModelType.EMBEDDING);
		verify(businessKnowledgeService, never()).syncPendingKnowledge();
		verify(agentKnowledgeService, never()).syncPendingKnowledge();
	}

}
