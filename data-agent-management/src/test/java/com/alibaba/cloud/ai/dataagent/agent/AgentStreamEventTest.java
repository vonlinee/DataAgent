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

import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentStreamEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesEventTypeForFrontendSsePayload() throws Exception {
        AgentStreamEvent event = new AgentStreamEvent("1", "run-1", ReActEventType.FINAL_ANSWER, "final", 1,
                "FinalAnswer", "MARK_DOWN", "answer", false, true);

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertEquals("FINAL_ANSWER", payload.get("eventType").asText());
    }

    @Test
    void preservesWorkflowEventTypeFormat() throws Exception {
        GraphNodeResponse event = GraphNodeResponse.builder()
            .agentId("1")
            .threadId("run-1")
            .nodeName("PlannerNode")
            .textType(TextType.TEXT)
            .text("workflow output")
            .build();

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertEquals("NODE_OUTPUT", payload.get("eventType").asText());
    }

}
