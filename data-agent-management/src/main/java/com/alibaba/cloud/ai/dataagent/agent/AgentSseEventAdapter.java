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

import org.springframework.http.codec.ServerSentEvent;

/**
 * Converts typed implementation events to the common AgentResponse SSE contract.
 */
public final class AgentSseEventAdapter {

    private AgentSseEventAdapter() {
    }

    /**
     * Preserves SSE metadata while widening the payload to the common response type.
     *
     * @param event typed implementation event
     * @return event with an AgentResponse payload
     */
    public static ServerSentEvent<AgentResponse> toAgentResponse(
            ServerSentEvent<? extends AgentResponse> event) {
        return ServerSentEvent.<AgentResponse>builder()
                .id(event.id())
                .event(event.event())
                .retry(event.retry())
                .comment(event.comment())
                .data(event.data())
                .build();
    }

}
