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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import lombok.Data;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime state for one active streaming Agentic execution.
 *
 * <p>This object tracks the current run's transport and cancellation handles. It is not the
 * persisted conversation history or the multi-turn memory.</p>
 */
@Data
final class RunState {

    /** Agent identifier associated with this run. */
    public final String agentId;

    /** Logical conversation identifier shared by multiple runs. */
    public final String conversationId;

    /** Identifier for this specific execution and its SSE stream. */
    public final String threadId;

    /** SSE sink used to publish Agent events to the client. */
    public final Sinks.Many<ServerSentEvent<AgentStreamEvent>> sink =
            Sinks.many().unicast().onBackpressureBuffer();

    /** Full answer assembled from streamed text deltas. */
    public final StringBuilder answer = new StringBuilder();

    /**
     * Correlates the provider's tool-call ID and reply ID to one stable trace key. Providers
     * are not required to populate both IDs on every tool event, so keeping this mapping in the
     * run state prevents a result from becoming a separate trace message.
     */
    private final Map<String, String> toolCorrelationIds = new ConcurrentHashMap<>();

    /** AgentScope context used for the current execution and interruption. */
    public RuntimeContext context;

    /** Agent instance executing this run. */
    public ReActAgent agent;

    /** Subscription for the Agent event stream, disposed when the run is cancelled. */
    public Disposable subscription;

    public RunState(String agentId, String conversationId, String threadId) {
        this.agentId = agentId;
        this.conversationId = conversationId;
        this.threadId = threadId;
    }

    String resolveToolCorrelationId(String toolCallId, String replyId, String fallback) {
        String correlationId = lookupToolCorrelationId(toolCallId);
        if (correlationId == null) {
            correlationId = lookupToolCorrelationId(replyId);
        }
        if (correlationId == null) {
            correlationId = firstText(toolCallId, replyId, fallback);
        }
        rememberToolCorrelationId(toolCallId, correlationId);
        rememberToolCorrelationId(replyId, correlationId);
        return correlationId;
    }

    private String lookupToolCorrelationId(String id) {
        return id == null || id.isBlank() ? null : toolCorrelationIds.get(id);
    }

    private void rememberToolCorrelationId(String id, String correlationId) {
        if (id != null && !id.isBlank()) {
            toolCorrelationIds.putIfAbsent(id, correlationId);
        }
    }

    private static String firstText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}
