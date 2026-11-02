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
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Unified entry point for model-agent implementations.
 */
public interface DataAgentService {

    /**
     * Starts an agent run and exposes its events through the common response contract.
     *
     * @param agentRequest request parameters
     * @return stream of agent responses
     */
    Flux<ServerSentEvent<AgentResponse>> streamSearch(AgentRequest agentRequest);

    /**
     * Stops a running agent execution.
     *
     * @param conversationId conversation identifier
     * @param threadId optional run identifier
     */
    void stopStream(String conversationId, String threadId);
}
