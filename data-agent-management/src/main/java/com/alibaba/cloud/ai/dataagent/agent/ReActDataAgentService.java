/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.agent;

import com.alibaba.cloud.ai.dataagent.dto.AgentRequest;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReActDataAgentService implements DataAgentService {

    @Autowired
    private AgentService agentService;

    @Autowired
    private ModelConfigDataService modelConfigDataService;

    private final ConcurrentHashMap<String, DataAgent> agentCache = new ConcurrentHashMap<>();

    @Override
    public Flux<ServerSentEvent<AgentResponse>> streamSearch(AgentRequest agentRequest) {
        Agent agent = agentService.findById(Long.valueOf(agentRequest.getAgentId()));
        String name = agent.getName();
        DataAgent dataAgent = agentCache.get(agentRequest.getConversationId());
        if (dataAgent == null) {
            ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
            dataAgent = new DataAgent(config);
            agentCache.put(agentRequest.getConversationId(), dataAgent);
        }

        return dataAgent.streamSearch(agentRequest, agent).map(AgentSseEventAdapter::toAgentResponse);
    }

    @Override
    public void stopStream(String conversationId, String threadId) {
        DataAgent dataAgent = agentCache.get(conversationId);
        if (dataAgent != null) {
            dataAgent.stop(conversationId, threadId);
        }
    }
}
