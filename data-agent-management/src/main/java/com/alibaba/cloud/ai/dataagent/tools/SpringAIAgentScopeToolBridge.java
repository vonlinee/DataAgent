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

import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.beans.BeansException;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapts Spring AI {@link ToolCallback}s to AgentScope {@link AgentTool}s.
 *
 * <p>
 * Spring AI exposes a tool invocation as a JSON string, while AgentScope provides the
 * invocation arguments as a map. This bridge also keeps the Spring AI input schema
 * unchanged and translates invocation failures into an AgentScope error result.
 * </p>
 */
@Component
public final class SpringAIAgentScopeToolBridge implements ApplicationContextAware {

    private static ApplicationContext context = null;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    private SpringAIAgentScopeToolBridge() {
    }

    public static void registerTools(Toolkit toolkit, Class<?>... toolClasses) {
        ArrayList<Class<?>> classes = new ArrayList<>(Arrays.asList(toolClasses));
        List<Class<?>> list = classes.stream()
                .filter(type -> type.isAnnotationPresent(Component.class))
                .toList();
        classes.removeAll(list);

        Set<Object> toolObjects = classes.stream()
                .map(ReflectUtils::newInstance)
                .collect(Collectors.toSet());

        toolObjects.addAll(list
                .stream()
                .map(context::getBean)
                .toList());

        toolObjects.stream()
                .map(SpringAIAgentScopeToolBridge::toAgentTools)
                .flatMap(Arrays::stream)
                .forEach(toolkit::registerAgentTool);
    }

    public static void registerTools(Toolkit toolkit, Object... toolObjects) {
        Arrays.stream(toolObjects)
                .map(SpringAIAgentScopeToolBridge::toAgentTools)
                .flatMap(Arrays::stream)
                .forEach(toolkit::registerAgentTool);
    }

    public static AgentTool[] toAgentTools(Object... toolObjects) {
        return Arrays.stream(toolObjects)
                .map(SpringAIAgentScopeToolBridge::toAgentTools)
                .flatMap(Arrays::stream)
                .toArray(AgentTool[]::new);
    }

    /**
     * Convert a regular object whose methods are annotated with Spring AI {@code @Tool}.
     *
     * <p>Spring AI may expose more than one tool from an object, so this overload returns
     * all converted tools.</p>
     *
     * @param toolObject Spring AI annotated tool object
     * @return converted AgentScope tools
     */
    public static AgentTool[] toAgentTools(Object toolObject) {
        Objects.requireNonNull(toolObject, "toolObject must not be null");
        return toAgentTools(ToolCallbacks.from(toolObject));
    }

    /**
     * Convert a regular object that defines exactly one Spring AI {@code @Tool} method.
     *
     * @param toolObject Spring AI annotated tool object
     * @return the converted AgentScope tool
     * @throws IllegalArgumentException if the object does not define exactly one tool
     */
    public static AgentTool toAgentTool(Object toolObject) {
        AgentTool[] tools = toAgentTools(toolObject);
        if (tools.length != 1) {
            throw new IllegalArgumentException("Expected exactly one Spring AI tool, but found " + tools.length);
        }
        return tools[0];
    }

    /**
     * Convert one Spring AI tool callback to an AgentScope tool.
     *
     * @param callback Spring AI tool callback
     * @return an AgentScope tool backed by the callback
     */
    public static AgentTool toAgentTool(ToolCallback callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        ToolDefinition definition = Objects.requireNonNull(callback.getToolDefinition(),
                "tool definition must not be null");
        Method method = getToolMethod(callback);
        // SpringAI 的 schema 是完整的 JSON schema
        // 而 AgentScope 需要的是参数部分
        // Spring 解析 Input Schema 的方式
        // Map<String, Object> parameters = parseInputSchema(definition.inputSchema());
        ToolSchemaGenerator schemaGenerator = new ToolSchemaGenerator();
        // 参数 Schema
        Map<String, Object> schema =
                schemaGenerator.generateParameterSchema(
                        method, Set.of());
        return new SpringAIAgentToolWrapper(callback, definition, schema);
    }

    private static Method getToolMethod(ToolCallback callback) {
        Method method;
        if (callback instanceof MethodToolCallback methodToolCallback) {
            try {
                Field toolMethodField = MethodToolCallback.class.getDeclaredField("toolMethod");
                toolMethodField.setAccessible(true);
                method = (Method) toolMethodField.get(methodToolCallback);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported tool callback type: " + callback.getClass());
        }
        return method;
    }

    private record SpringAIAgentToolWrapper(ToolCallback callback, ToolDefinition definition,
                                            Map<String, Object> parameters) implements AgentTool {
        @Override
        public String getName() {
            return definition.name();
        }

        @Override
        public String getDescription() {
            return definition.description();
        }

        @Override
        public Map<String, Object> getParameters() {
            return parameters;
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam callParam) {
            Objects.requireNonNull(callParam, "callParam must not be null");
            return Mono.fromCallable(() -> callback.call(serializeInput(callParam, definition)))
                    .map(result -> successResult(callParam, result))
                    .onErrorResume(error -> Mono.just(errorResult(callParam, error)));
        }
    }

    /**
     * Convert all callbacks in an array, preserving their order.
     *
     * @param callbacks Spring AI tool callbacks
     * @return converted AgentScope tools
     */
    public static AgentTool[] toAgentTools(ToolCallback[] callbacks) {
        Objects.requireNonNull(callbacks, "callbacks must not be null");
        AgentTool[] tools = new AgentTool[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            tools[i] = toAgentTool(callbacks[i]);
        }
        return tools;
    }

    /**
     * Convert a list of Spring AI callbacks, preserving their order.
     *
     * @param callbacks Spring AI tool callbacks
     * @return converted AgentScope tools
     */
    public static List<AgentTool> toAgentTools(List<? extends ToolCallback> callbacks) {
        Objects.requireNonNull(callbacks, "callbacks must not be null");
        return callbacks.stream().map(SpringAIAgentScopeToolBridge::toAgentTool).toList();
    }

    private static Map<String, Object> parseInputSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> schema = JsonUtil.getObjectMapper()
                    .readValue(inputSchema, new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return new HashMap<>(schema);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Spring AI tool input schema", e);
        }
    }

    private static String serializeInput(ToolCallParam callParam, ToolDefinition definition) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>(
                callParam.getInput() == null ? Map.of() : callParam.getInput());
        RuntimeContext runtimeContext = callParam.getRuntimeContext();
        if (runtimeContext != null) {
            for (String parameterName : getParameterNames(definition)) {
                Object value = getRuntimeContextValue(runtimeContext, parameterName);
                if (value != null) {
                    input.put(parameterName, value);
                }
            }
        }
        return JsonUtil.getObjectMapper().writeValueAsString(input);
    }

    private static Set<String> getParameterNames(ToolDefinition definition) {
        Map<String, Object> inputSchema = parseInputSchema(definition.inputSchema());
        Object properties = inputSchema.get("properties");
        if (!(properties instanceof Map<?, ?> propertyMap)) {
            return Set.of();
        }
        return propertyMap.keySet()
                .stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Object getRuntimeContextValue(RuntimeContext runtimeContext, String parameterName) {
        Map<String, Object> extra = runtimeContext.getExtra();
        if (extra == null) {
            return null;
        }
        if (extra.containsKey(parameterName)) {
            return extra.get(parameterName);
        }
        return extra.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(parameterName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static ToolResultBlock successResult(ToolCallParam callParam, String result) {
        return withCallIdentity(callParam,
                ToolResultBlock.text(result == null ? "" : result).withState(ToolResultState.SUCCESS));
    }

    private static ToolResultBlock errorResult(ToolCallParam callParam, Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return withCallIdentity(callParam, ToolResultBlock.error(message));
    }

    private static ToolResultBlock withCallIdentity(ToolCallParam callParam, ToolResultBlock result) {
        ToolUseBlock toolUse = callParam.getToolUseBlock();
        if (toolUse == null) {
            return result;
        }
        return result.withIdAndName(toolUse.getId(), toolUse.getName());
    }

}
