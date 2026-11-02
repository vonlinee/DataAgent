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

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataAgentTest {

    private final DataAgent.DatasourceSystemPromptMiddleware middleware =
            new DataAgent.DatasourceSystemPromptMiddleware();

    @Test
    void appendsDatasourceIdToSystemPromptFromRuntimeContext() {
        RuntimeContext context = RuntimeContext.builder().build();
        context.put("datasourceId", 42);

        String prompt = middleware.onSystemPrompt(null, context, "基础系统提示").block();

        assertThat(prompt).isEqualTo("基础系统提示\n\n当前的数据源 ID 是: 42");
    }

    @Test
    void leavesSystemPromptUnchangedWithoutDatasourceId() {
        RuntimeContext context = RuntimeContext.builder().build();

        String prompt = middleware.onSystemPrompt(null, context, "基础系统提示").block();

        assertThat(prompt).isEqualTo("基础系统提示");
    }

    @Test
    void resolvesConfiguredSystemPromptAndLoopCount() {
        Agent agent = Agent.builder().prompt("回答必须使用中文").maxLoopCount(25).build();

        assertThat(DataAgent.resolveSystemPrompt(agent)).contains("回答必须使用中文");
        assertThat(DataAgent.resolveMaxLoopCount(agent)).isEqualTo(25);
    }

    @Test
    void resolvesDefaultsForMissingAgenticSettings() {
        Agent agent = Agent.builder().prompt(" ").maxLoopCount(null).build();

        assertThat(DataAgent.resolveSystemPrompt(agent)).contains("严格遵循 ReAct 模式");
        assertThat(DataAgent.resolveMaxLoopCount(agent)).isEqualTo(10);
    }

}
