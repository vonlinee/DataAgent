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
import com.alibaba.cloud.ai.dataagent.tools.*;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.ProxyConfig;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

class DataAgent {

    static final class DatasourceSystemPromptMiddleware implements MiddlewareBase {

        @Override
        public Mono<String> onSystemPrompt(io.agentscope.core.agent.Agent agent,
                                           RuntimeContext context, String systemPrompt) {
            Object datasourceId = context == null ? null : context.get("datasourceId");
            if (datasourceId == null) {
                return Mono.just(systemPrompt);
            }
            return Mono.just(systemPrompt + "\n\n当前的数据源 ID 是: " + datasourceId);
        }

    }

    private static final int DEFAULT_MAX_LOOP_COUNT = 10;

    private final Map<String, RunState> runs = new ConcurrentHashMap<>();

    private final ModelConfigDTO config;

    private ReActAgent reActAgent;

    private Long configuredAgentId;

    private String configuredPrompt;

    private Integer configuredMaxLoopCount;

    public DataAgent(ModelConfigDTO configDTO) {
        this.config = configDTO;
    }

    private synchronized ReActAgent getAgent(ModelConfigDTO config, Agent agent) {
        int maxLoopCount = resolveMaxLoopCount(agent);
        if (this.reActAgent != null && Objects.equals(this.configuredAgentId, agent.getId())
                && Objects.equals(this.configuredPrompt, agent.getPrompt())
                && Objects.equals(this.configuredMaxLoopCount, maxLoopCount)) {
            return this.reActAgent;
        }
        if (config == null) {
            throw new IllegalStateException("未找到激活的 CHAT 模型配置，请先在模型配置页面完成配置。");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new IllegalStateException("激活的 CHAT 模型配置缺少 API Key，请检查数据库中的模型配置。");
        }
        // Keep one AgentScope instance per conversation so its internal ReAct state can be
        // reused by subsequent turns. ReActAgent performs the Reasoning -> Action -> Observation
        // loop inside streamEvents, bounded by maxIters configured below.
        this.reActAgent = createAgent(config, agent);
        this.configuredAgentId = agent.getId();
        this.configuredPrompt = agent.getPrompt();
        this.configuredMaxLoopCount = maxLoopCount;
        return this.reActAgent;
    }

    private ReActAgent createAgent(ModelConfigDTO config, Agent agent) {
        Toolkit toolkit = new Toolkit();
        SpringAIAgentScopeToolBridge.registerTools(toolkit,
                TodoWriteTool.class,
                ListTablesTool.class,
                DescribeTablesTool.class,
                ExecuteSqlTool.class,
                TimeTool.class,
                ValidateSqlTool.class
        );
        // 基础注册：不限制访问路径
        // toolkit.registerTool(new ReadFileTool());
        ClasspathSkillRepository repo;
        try {
            repo = new ClasspathSkillRepository("skills");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<AgentSkill> skills = repo.getAllSkills();
        SkillBox skillBox = new SkillBox(toolkit);
        for (AgentSkill skill : skills) {
            skillBox.registration().skill(skill).apply();
        }


        ExecutionConfig modelExecConfig = ExecutionConfig.builder()
                .timeout(Duration.ofMinutes(2))
                .maxAttempts(3)
                .initialBackoff(Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .backoffMultiplier(2.0)
                .retryOn(error -> error.getMessage() != null
                                  && (error.getMessage().contains("rate limit")
                                      || error.getMessage().contains("timeout")))
                .build();
        ExecutionConfig toolExecConfig = ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(30))
                .maxAttempts(1)
                .build();

        GenerateOptions.Builder generateOptions = GenerateOptions.builder().thinkingBudget(4096);
        if (config.getTemperature() != null) {
            generateOptions.temperature(config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            generateOptions.maxTokens(config.getMaxTokens());
        }

        DashScopeChatModel.Builder modelBuilder = DashScopeChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(resolveDashScopeBaseUrl(config.getBaseUrl()))
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(generateOptions.build());
        if (Boolean.TRUE.equals(config.getProxyEnabled())) {
            if (!StringUtils.hasText(config.getProxyHost()) || config.getProxyPort() == null) {
                throw new IllegalStateException("模型代理已开启，但代理地址或端口未配置。");
            }
            modelBuilder.proxy(ProxyConfig.http(config.getProxyHost(), config.getProxyPort(),
                    config.getProxyUsername(), config.getProxyPassword()));
        }

        return ReActAgent.builder()
                .name(agent.getName())
                .sysPrompt(resolveSystemPrompt(agent))
                .model(modelBuilder.build())
                .toolkit(toolkit)
                .skillRepository(repo)
                .middleware(new DatasourceSystemPromptMiddleware())
                .maxIters(resolveMaxLoopCount(agent))
                .modelExecutionConfig(modelExecConfig)
                .toolExecutionConfig(toolExecConfig)
                .build();
    }

    static String resolveSystemPrompt(Agent agent) {
        return agent.getPrompt().strip();
    }

    static int resolveMaxLoopCount(Agent agent) {
        return agent == null || agent.getMaxLoopCount() == null
                ? DEFAULT_MAX_LOOP_COUNT
                : agent.getMaxLoopCount();
    }

    private static String resolveDashScopeBaseUrl(String configuredBaseUrl) {
        if (!StringUtils.hasText(configuredBaseUrl)) {
            return configuredBaseUrl;
        }
        String baseUrl = configuredBaseUrl.strip().replaceAll("/+$", "");
        int compatibleModeIndex = baseUrl.indexOf("/compatible-mode");
        if (compatibleModeIndex > 0) {
            // 管理端默认保存的是 OpenAI 兼容地址；AgentScope 使用 DashScope 原生地址。
            return baseUrl.substring(0, compatibleModeIndex);
        }
        return baseUrl;
    }

    public Flux<ServerSentEvent<AgentStreamEvent>> streamSearch(
            AgentRequest agentRequest, Agent agent) {
        String agentId = agentRequest.getAgentId();
        String conversationId = agentRequest.getConversationId();
        String query = agentRequest.getQuery();

        String runId = agentRequest.getOrNewThreadId();
        RunState state = new RunState(agentId, conversationId, runId);
        ReActAgent currentAgent;
        try {
            currentAgent = getAgent(config, agent);
        } catch (RuntimeException ex) {
            return Flux.just(errorEvent(state, ex.getMessage() == null ? "Agentic 服务不可用。" : ex.getMessage()));
        }

        RunState previous = runs.putIfAbsent(runId, state);
        if (previous != null) {
            return Flux.just(errorEvent(state, "已有相同线程正在运行，请稍后重试。"));
        }
        Msg userMessage = buildUserMsg(query);

        RuntimeContext context = RuntimeContext.builder()
                .userId(agentId)
                .sessionId(conversationId)
                .build();
        if (agentRequest.getDatasourceId() != null) {
            context.put("datasourceId", agentRequest.getDatasourceId());
        }
        state.setContext(context);
        state.agent = currentAgent;

        state.subscription = currentAgent.streamEvents(userMessage, context)
                .subscribe(event -> handleEvent(state, event),
                        error -> finishWithError(state, error),
                        () -> finishSuccessfully(state));

        return state.sink.asFlux().doOnCancel(() -> cancelRun(state));
    }

    static Msg buildUserMsg(String msg) {
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(msg).build())
                .build();
    }

    public void stop(String conversationId,
                     String threadId) {
        RunState state = threadId == null ? null : runs.get(threadId);
        if (state == null && conversationId != null) {
            for (Map.Entry<String, RunState> entry : runs.entrySet()) {
                if (conversationId.equals(entry.getValue().conversationId)) {
                    state = entry.getValue();
                    break;
                }
            }
        }
        if (state != null) {
            cancelRun(state);
        }
    }

    private void cancelRun(RunState state) {
        if (!runs.remove(state.threadId, state)) {
            return;
        }
        try {
            if (state.agent != null) {
                state.agent.interrupt(state.context);
            }
        } catch (RuntimeException ignored) {
            // The stream is still disposed below when an interrupt races with completion.
        }
        if (state.subscription != null) {
            state.subscription.dispose();
        }
        state.sink.tryEmitComplete();
    }

    private void handleEvent(RunState state, AgentEvent event) {
        AgentEventType type = event.getType();
        if (type == AgentEventType.THINKING_BLOCK_DELTA) {
            ThinkingBlockDeltaEvent thinking = (ThinkingBlockDeltaEvent) event;
            emitNode(state, ReActEventType.THINKING, "Thinking", valueOrDefault(thinking.getBlockId(), "thinking"),
                    thinking.getDelta());
        } else if (type == AgentEventType.TOOL_CALL_START) {
            ToolCallStartEvent tool = (ToolCallStartEvent) event;
            String stepId = toolStepId(tool.getToolCallId(), "tool-call");
            emitNode(state, ReActEventType.TOOL_CALL, "ToolCall", stepId,
                    "正在调用工具：" + valueOrDefault(tool.getToolCallName(), "未知工具"));
        } else if (type == AgentEventType.TOOL_CALL_DELTA) {
            ToolCallDeltaEvent tool = (ToolCallDeltaEvent) event;
            emitNode(state, ReActEventType.TOOL_CALL, "ToolCall",
                    toolStepId(tool.getToolCallId(), "tool-call"), tool.getDelta());
        } else if (type == AgentEventType.TOOL_RESULT_START) {
            ToolResultStartEvent tool = (ToolResultStartEvent) event;
            emitNode(state, ReActEventType.TOOL_RESULT, "ToolResult",
                    toolStepId(tool.getToolCallId(), "tool-result"),
                    "工具返回结果");
        } else if (type == AgentEventType.TOOL_RESULT_TEXT_DELTA) {
            ToolResultTextDeltaEvent tool = (ToolResultTextDeltaEvent) event;
            emitNode(state, ReActEventType.TOOL_RESULT, "ToolResult",
                    toolStepId(tool.getToolCallId(), "tool-result"), tool.getDelta());
        } else if (type == AgentEventType.TEXT_BLOCK_DELTA) {
            TextBlockDeltaEvent text = (TextBlockDeltaEvent) event;
            if (text.getDelta() != null) {
                state.answer.append(text.getDelta());
            }
        } else if (type == AgentEventType.AGENT_RESULT) {
            AgentResultEvent result = (AgentResultEvent) event;
            String answer = result.getResult() == null ? null : result.getResult().getTextContent();
            if (answer != null && !answer.isBlank()) {
                state.answer.setLength(0);
                state.answer.append(answer);
            }
        } else if (type == AgentEventType.EXCEED_MAX_ITERS) {
            finishWithError(state, new IllegalStateException("ReAct 循环超过最大迭代次数"));
        }
    }

    private void emitNode(RunState state, ReActEventType eventType, String nodeName, String stepId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AgentStreamEvent payload = new AgentStreamEvent(state.agentId, state.threadId,
                eventType, stepId, 1, nodeName, "TEXT", text, false, false);
        state.sink.tryEmitNext(ServerSentEvent.<AgentStreamEvent>builder().data(payload).build());
    }

    private void finishSuccessfully(RunState state) {
        if (!runs.remove(state.threadId, state)) {
            return;
        }
        String answer = state.answer.toString().trim();
        if (!answer.isEmpty()) {
            AgentStreamEvent payload = new AgentStreamEvent(state.agentId, state.threadId,
                    ReActEventType.FINAL_ANSWER, "final", 1, "FinalAnswer", "MARK_DOWN", answer, false, true);
            state.sink.tryEmitNext(ServerSentEvent.<AgentStreamEvent>builder().data(payload).build());
        }
        state.sink.tryEmitNext(ServerSentEvent.<AgentStreamEvent>builder().event("complete").build());
        state.sink.tryEmitComplete();
    }

    private void finishWithError(RunState state, Throwable error) {
        if (!runs.remove(state.threadId, state)) {
            return;
        }
        String message = error.getMessage() == null ? "Agent 执行失败" : error.getMessage();
        state.sink.tryEmitNext(ServerSentEvent.<AgentStreamEvent>builder().event("error")
                .data(new AgentStreamEvent(state.agentId, state.threadId, ReActEventType.ERROR, "error", 1,
                        "Agent", "TEXT", message, true, true))
                .build());
        state.sink.tryEmitComplete();
    }

    private ServerSentEvent<AgentStreamEvent> errorEvent(RunState state, String message) {
        return ServerSentEvent.<AgentStreamEvent>builder().event("error")
                .data(new AgentStreamEvent(state.agentId, state.threadId, ReActEventType.ERROR, "error", 1,
                        "Agent", "TEXT", message, true, true))
                .build();
    }

    private static String toolStepId(String id, String fallback) {
        return fallback + ":" + valueOrDefault(id, fallback);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }


}
