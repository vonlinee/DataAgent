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
import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.enums.GraphEventType;
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;

@Slf4j
@Component
@AllArgsConstructor
public class GraphicWorkflowAgentService implements DataAgentService {

    private final GraphService graphService;

    @Override
    public Flux<ServerSentEvent<AgentResponse>> streamSearch(AgentRequest agentRequest) {
        Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();

        GraphRequest request = GraphRequest.builder()
                .agentId(agentRequest.getAgentId())
                .conversationId(agentRequest.getConversationId())
                .threadId(agentRequest.getThreadId())
                .query(agentRequest.getQuery())
                .humanFeedback(agentRequest.isHumanFeedback())
                .humanFeedbackContent(agentRequest.getHumanFeedbackContent())
                .rejectedPlan(agentRequest.isRejectedPlan())
                .nl2sqlOnly(agentRequest.isNl2sqlOnly())
                .build();
        graphService.graphStreamProcess(sink, request);

        return sink.asFlux().filter(sse -> {
                    // 1. 如果 event 是 "complete" 或 "error"，直接放行（不管 text 是否为空）
                    if (STREAM_EVENT_COMPLETE.equals(sse.event()) || STREAM_EVENT_ERROR.equals(sse.event())) {
                        return true;
                    }
                    // Protocol events carry state transitions in eventType and do not require
                    // display text.
                    if (sse.data() != null && sse.data().getEventType() != null
                        && !GraphEventType.NODE_OUTPUT.equals(sse.data().getEventType())) {
                        return true;
                    }
                    // 判断字符串是否为空
                    return sse.data() != null && sse.data().getText() != null && !sse.data().getText().isEmpty();
                })
                .map(AgentSseEventAdapter::toAgentResponse)
                .doOnSubscribe(subscription -> log.info("Client subscribed to stream, threadId: {}", request.getThreadId()))
                .doOnCancel(() -> {
                    log.info("Client disconnected from stream, threadId: {}", request.getThreadId());
                    if (request.getThreadId() != null) {
                        graphService.stopStreamProcessing(request.getThreadId());
                    }
                })
                .doOnError(e -> {
                    log.error("Error occurred during streaming, threadId: {}: ", request.getThreadId(), e);
                    if (request.getThreadId() != null) {
                        graphService.stopStreamProcessing(request.getThreadId());
                    }
                })
                .doOnComplete(() -> log.info("Stream completed successfully, threadId: {}", request.getThreadId()));
    }

    @Override
    public void stopStream(String conversationId,
                           String threadId) {
        if (StringUtils.hasText(threadId)) {
            graphService.stopStreamProcessing(threadId);
        } else {
            graphService.stopStreamProcessingByConversationId(conversationId);
        }
    }
}
