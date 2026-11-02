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
package com.alibaba.cloud.ai.dataagent.tools;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAIAgentScopeToolBridgeTest {
    static final String TEST_TOOL_NAME = "Test-Tool";
    private final ToolCallback springTool = TodoWriteTool.create()[0];

    @Test
    void convertsDefinitionAndInvokesSpringTool() {
        AgentTool agentTool = SpringAIAgentScopeToolBridge.toAgentTool(new SpringAITool());

        assertThat(agentTool.getName()).isEqualTo(TEST_TOOL_NAME);
        assertThat(agentTool.getDescription()).isEqualTo("描述信息");
        assertThat(agentTool.getParameters()).containsEntry("type", "object");
        assertThat(agentTool.getParameters()).containsKey("properties");

        Map<String, Object> input = Map.of("p11", "运行测试", "p2", List.of("测试桥接器"));
        ToolUseBlock toolUse = new ToolUseBlock("call-1", TEST_TOOL_NAME, input);
        ToolCallParam callParam = ToolCallParam.builder().toolUseBlock(toolUse).input(input).build();

        ToolResultBlock result = agentTool.callAsync(callParam).block();

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(ToolResultState.SUCCESS);
        assertThat(result.getId()).isEqualTo("call-1");
        assertThat(result.getName()).isEqualTo(TEST_TOOL_NAME);
        assertThat(((TextBlock) result.getOutput().get(0)).getText()).contains("返回值");
    }

    @Test
    void injectsDatasourceIdFromRuntimeContext() {
        AgentTool agentTool = SpringAIAgentScopeToolBridge.toAgentTool(new DatasourceAwareSpringTool());
        RuntimeContext runtimeContext = RuntimeContext.builder().build();
        runtimeContext.put("datasourceId", 42);
        Map<String, Object> input = Map.of();
        ToolUseBlock toolUse = new ToolUseBlock("call-datasource", "listTables", input);
        ToolCallParam callParam = ToolCallParam.builder()
                .toolUseBlock(toolUse)
                .input(input)
                .runtimeContext(runtimeContext)
                .build();

        ToolResultBlock result = agentTool.callAsync(callParam).block();

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(ToolResultState.SUCCESS);
        assertThat(((TextBlock) result.getOutput().get(0)).getText()).contains("42");
    }

    @Test
    void injectsAnyDeclaredParameterFromRuntimeContext() {
        AgentTool agentTool = SpringAIAgentScopeToolBridge.toAgentTool(new ContextAwareSpringTool());
        RuntimeContext runtimeContext = RuntimeContext.builder().build();
        runtimeContext.put("tenantId", "tenant-42");
        Map<String, Object> input = Map.of();
        ToolUseBlock toolUse = new ToolUseBlock("call-context", "contextAware", input);
        ToolCallParam callParam = ToolCallParam.builder()
                .toolUseBlock(toolUse)
                .input(input)
                .runtimeContext(runtimeContext)
                .build();

        ToolResultBlock result = agentTool.callAsync(callParam).block();

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(ToolResultState.SUCCESS);
        assertThat(((TextBlock) result.getOutput().get(0)).getText()).contains("tenant-42");
    }

    @Test
    void convertsAllSpringToolsFromAnAnnotatedObject() {
        AgentTool[] agentTools = SpringAIAgentScopeToolBridge.toAgentTools(new SpringAITool());

        assertThat(agentTools).hasSize(1);
        assertThat(agentTools[0].getName()).isEqualTo(TEST_TOOL_NAME);
    }

    @Test
    void convertsSpringToolFailureToAgentScopeErrorResult() {
        AgentTool agentTool = SpringAIAgentScopeToolBridge.toAgentTool(springTool);
        Map<String, Object> input = Map.of("todos",
                List.of(Map.of("content", "", "status", "pending", "activeForm", "正在执行")));
        ToolUseBlock toolUse = new ToolUseBlock("call-2", "TodoWrite", input);
        ToolCallParam callParam = ToolCallParam.builder().toolUseBlock(toolUse).input(input).build();

        ToolResultBlock result = agentTool.callAsync(callParam).block();

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(ToolResultState.ERROR);
        assertThat(result.getId()).isEqualTo("call-2");
        assertThat(((TextBlock) result.getOutput().get(0)).getText()).contains("content");
    }

    static class AgentScopeTool {

        @Tool(name = TEST_TOOL_NAME, description = "描述信息")
        public String toolMethod(
                @ToolParam(name = "p11", description = "参数1") String p1,
                @ToolParam(name = "p2", description = "参数2", required = false) List<String> p2) {
            return "返回值";
        }
    }

    static class SpringAITool {
        @org.springframework.ai.tool.annotation.Tool(name = TEST_TOOL_NAME, description = "描述信息")
        public String toolMethod(
                @org.springframework.ai.tool.annotation.ToolParam(description = "参数1") String p11,
                @org.springframework.ai.tool.annotation.ToolParam(description = "参数2", required = false) List<String> p2) {
            return "返回值";
        }
    }

    static class DatasourceAwareSpringTool {
        @org.springframework.ai.tool.annotation.Tool(name = "listTables", description = "列出数据源中的表")
        public String listTables(
                @org.springframework.ai.tool.annotation.ToolParam(description = "数据源 ID") String dataSourceId) {
            return "datasource=" + dataSourceId;
        }
    }

    static class ContextAwareSpringTool {
        @org.springframework.ai.tool.annotation.Tool(name = "contextAware", description = "读取运行时上下文")
        public String contextAware(
                @org.springframework.ai.tool.annotation.ToolParam(description = "租户 ID") String tenantId) {
            return "tenant=" + tenantId;
        }
    }

    @Test
    void shouldConvertSpringAIToolToAgentScopeAgentTool() {
        ToolCallback toolCallback = ToolCallbacks.from(new SpringAITool())[0];
        AgentTool agentTool = SpringAIAgentScopeToolBridge.toAgentTool(toolCallback);

        AgentTool agentScopeTool = getAgentScopeTool(new AgentScopeTool());

        Assertions.assertEquals(TEST_TOOL_NAME, agentTool.getName());
        Assertions.assertEquals(TEST_TOOL_NAME, agentScopeTool.getName());

        Assertions.assertEquals(agentScopeTool.getName(), agentTool.getName());
        Assertions.assertEquals(agentScopeTool.getDescription(), agentTool.getDescription());
        Assertions.assertEquals(agentScopeTool.getParameters(), agentTool.getParameters());
        Assertions.assertEquals(agentScopeTool.getOutputSchema(), agentTool.getOutputSchema());
    }

    private static AgentTool getAgentScopeTool(Object toolObject) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(toolObject);
        return toolkit.getTool(TEST_TOOL_NAME);
    }
}
