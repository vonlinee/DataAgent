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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.agent.AgentResponse;
import com.alibaba.cloud.ai.dataagent.agent.AgentStreamEvent;
import com.alibaba.cloud.ai.dataagent.agent.GraphicWorkflowAgentService;
import com.alibaba.cloud.ai.dataagent.agent.ReActEventType;
import com.alibaba.cloud.ai.dataagent.agent.ReActDataAgentService;
import com.alibaba.cloud.ai.dataagent.dto.AgentRequest;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.enums.AgentType;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphControllerTest {

    @Mock
    private AgentService agentService;

    @Mock
    private ReActDataAgentService reActDataAgentService;

    @Mock
    private GraphicWorkflowAgentService graphicWorkflowAgentService;

    @Mock
    private ServerHttpResponse serverHttpResponse;

    @Mock
    private HttpHeaders httpHeaders;

    private GraphController graphController;

    @BeforeEach
    void setUp() {
        graphController = new GraphController(agentService, reActDataAgentService, graphicWorkflowAgentService);
    }

    private void stubResponseHeaders() {
        when(serverHttpResponse.getHeaders()).thenReturn(httpHeaders);
    }

    @Test
    void streamSearch_routesAgenticAgentToReActService() {
        stubResponseHeaders();
        AgentRequest request = request("1");
        AgentStreamEvent payload = new AgentStreamEvent("1", "run-1", ReActEventType.THINKING, "step-1", 1,
                "ToolCall", "TEXT", "calling tool", false, false);
        ServerSentEvent<AgentResponse> event = event(payload);
        when(agentService.findById(1L)).thenReturn(agent(AgentType.AGENTIC));
        when(reActDataAgentService.streamSearch(request)).thenReturn(Flux.just(event));

        StepVerifier.create(graphController.streamSearch(request, serverHttpResponse))
            .expectNext(event)
            .verifyComplete();

        verify(reActDataAgentService).streamSearch(request);
        verify(graphicWorkflowAgentService, never()).streamSearch(any(AgentRequest.class));
        verify(httpHeaders).add("Cache-Control", "no-cache");
        verify(httpHeaders).add("Connection", "keep-alive");
        verify(httpHeaders).add("Access-Control-Allow-Origin", "*");
    }

    @Test
    void streamSearch_routesWorkflowAgentToWorkflowService() {
        stubResponseHeaders();
        AgentRequest request = request("2");
        GraphNodeResponse payload = GraphNodeResponse.builder()
            .agentId("2")
            .threadId("run-2")
            .text("workflow output")
            .build();
        ServerSentEvent<AgentResponse> event = event(payload);
        when(agentService.findById(2L)).thenReturn(agent(AgentType.WORKFLOW));
        when(graphicWorkflowAgentService.streamSearch(request)).thenReturn(Flux.just(event));

        StepVerifier.create(graphController.streamSearch(request, serverHttpResponse))
            .expectNext(event)
            .verifyComplete();

        verify(graphicWorkflowAgentService).streamSearch(request);
        verify(reActDataAgentService, never()).streamSearch(any(AgentRequest.class));
    }

    @Test
    void streamSearch_missingAgentReturnsNotFound() {
        stubResponseHeaders();
        AgentRequest request = request("404");
        when(agentService.findById(404L)).thenReturn(null);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> graphController.streamSearch(request, serverHttpResponse));
    }

    @Test
    void stopStream_routesToAgentImplementation() {
        when(agentService.findById(1L)).thenReturn(agent(AgentType.AGENTIC));

        graphController.stopStream("1", "conversation-1", "run-1");

        verify(reActDataAgentService).stopStream("conversation-1", "run-1");
        verify(graphicWorkflowAgentService, never()).stopStream(any(String.class), any(String.class));
    }

    @Test
    void stopStream_routesWorkflowAgentToWorkflowService() {
        when(agentService.findById(2L)).thenReturn(agent(AgentType.WORKFLOW));

        graphController.stopStream("2", "conversation-2", "run-2");

        verify(graphicWorkflowAgentService).stopStream("conversation-2", "run-2");
        verify(reActDataAgentService, never()).stopStream(any(String.class), any(String.class));
    }

    private AgentRequest request(String agentId) {
        return AgentRequest.builder()
            .agentId(agentId)
            .conversationId("conversation-" + agentId)
            .query("test query")
            .build();
    }

    private Agent agent(AgentType type) {
        return Agent.builder().id(1L).agentType(type.getValue()).build();
    }

    private ServerSentEvent<AgentResponse> event(AgentResponse payload) {
        return ServerSentEvent.<AgentResponse>builder().data(payload).build();
    }

}
