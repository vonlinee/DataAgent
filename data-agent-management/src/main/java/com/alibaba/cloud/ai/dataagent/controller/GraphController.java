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
import com.alibaba.cloud.ai.dataagent.agent.DataAgentService;
import com.alibaba.cloud.ai.dataagent.agent.GraphicWorkflowAgentService;
import com.alibaba.cloud.ai.dataagent.agent.ReActDataAgentService;
import com.alibaba.cloud.ai.dataagent.dto.AgentRequest;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.enums.AgentType;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * @author zhangshenghang
 * @author vlsmb
 */
@RestController
@AllArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class GraphController {

    private final AgentService agentService;

    private final ReActDataAgentService reActDataAgentService;

    private final GraphicWorkflowAgentService graphicWorkflowAgentService;

    @GetMapping(value = "/stream/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> streamSearch(
            @Valid @ModelAttribute AgentRequest agentRequest, ServerHttpResponse response) {
        // Set SSE-related HTTP headers
        response.getHeaders().add("Cache-Control", "no-cache");
        response.getHeaders().add("Connection", "keep-alive");
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
        return getDataAgent(agentRequest.getAgentId()).streamSearch(agentRequest);
    }

    private DataAgentService getDataAgent(String agentIdValue) {
        Long agentId = parseAgentId(agentIdValue);
        Agent agent = agentService.findById(agentId);
        if (agent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }

        AgentType agentType = resolveAgentType(agent.getAgentType());
        return switch (agentType) {
            case AGENTIC -> reActDataAgentService;
            case WORKFLOW -> graphicWorkflowAgentService;
        };
    }

    @PostMapping("/stream/stop")
    public ResponseEntity<Void> stopStream(@RequestParam("agentId") String agentId,
                                           @RequestParam("conversationId") String conversationId,
                                           @RequestParam(value = "threadId", required = false) String threadId) {
        getDataAgent(agentId).stopStream(conversationId, threadId);
        return ResponseEntity.noContent().build();
    }

    private Long parseAgentId(String agentId) {
        try {
            return Long.valueOf(agentId);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agentId must be a number", ex);
        }
    }

    private AgentType resolveAgentType(String value) {
        for (AgentType type : AgentType.values()) {
            if (type.getValue().equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported agent type: " + value);
    }

}
