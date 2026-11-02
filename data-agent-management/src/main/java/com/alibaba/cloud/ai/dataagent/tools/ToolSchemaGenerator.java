package com.alibaba.cloud.ai.dataagent.tools;

import io.agentscope.core.util.JsonSchemaUtils;
import org.springframework.ai.tool.annotation.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 从 AgentScope 源码进行复制， 解析 SpringAI 的方法定义，适配 AgentScope 的方法定义
 * 参考：io.agentscope.core.tool.ToolSchemaGenerator
 */
class ToolSchemaGenerator {

    /**
     * Generate parameter schema for a method with excluded parameters.
     *
     * <p>
     * This overload allows excluding certain parameters from the generated schema,
     * which is
     * useful for preset parameters that should not be exposed to the agent.
     *
     * @param method        the method to generate schema for
     * @param excludeParams set of parameter names to exclude from the schema (may
     *                      be null or empty)
     * @return JSON Schema map in OpenAI format
     */
    Map<String, Object> generateParameterSchema(Method method, Set<String> excludeParams) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        Map<String, Object> allDefinitions = new HashMap<>();

        Parameter[] parameters = method.getParameters();
        for (Parameter param : parameters) {
            // Only include parameters with @ToolParam annotation
            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            if (toolParam == null) {
                continue;
            }

            ParameterInfo info = extractParameterInfo(param, toolParam);

            // Skip excluded parameters (e.g., preset parameters)
            if (excludeParams != null && excludeParams.contains(info.name)) {
                continue;
            }

            // Hoist $defs from per-parameter schema to the root level so that
            // $ref pointers like "#/$defs/TypeName" resolve against the document root.
            hoistDefs(info.schema, "$defs", allDefinitions);
            hoistDefs(info.schema, "definitions", allDefinitions);

            properties.put(info.name, info.schema);
            if (info.required) {
                required.add(info.name);
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        if (!allDefinitions.isEmpty()) {
            schema.put("$defs", allDefinitions);
        }

        return schema;
    }

    @SuppressWarnings("unchecked")
    private void hoistDefs(
            Map<String, Object> paramSchema, String key, Map<String, Object> target) {
        Object raw = paramSchema.remove(key);
        if (raw instanceof Map<?, ?> defs && !defs.isEmpty()) {
            Map<String, Object> normalizedDefs = (Map<String, Object>) defs;
            for (Map.Entry<String, Object> entry : normalizedDefs.entrySet()) {
                String defKey = entry.getKey();
                Object incomingDef = entry.getValue();
                Object existingDef = target.get(defKey);
                if (existingDef == null) {
                    target.put(defKey, incomingDef);
                    continue;
                }
                if (!Objects.equals(existingDef, incomingDef)) {
                    throw new IllegalStateException(
                            "Conflicting schema definition found for key: " + defKey);
                }
            }
        }
    }

    /**
     * Extract parameter information from a Parameter with @ToolParam annotation.
     *
     * @param param     the parameter to extract info from
     * @param toolParam the @ToolParam annotation
     * @return ParameterInfo containing name, schema, and required flag
     */
    private ParameterInfo extractParameterInfo(Parameter param, ToolParam toolParam) {
        // SpringAI 不支持 定义 name
        // AgentScope 是取注解配置的参数名
        String paramName = param.getName();

        // Generate schema using JsonSchemaUtils with full type support (including generics)
        Map<String, Object> paramSchema =
                JsonSchemaUtils.generateSchemaFromType(param.getParameterizedType());

        if (!toolParam.description().isEmpty()) {
            paramSchema.put("description", toolParam.description());
        }

        return new ParameterInfo(paramName, paramSchema, toolParam.required());
    }

    /**
     * Internal class to hold parameter information.
     */
    private record ParameterInfo(String name, Map<String, Object> schema, boolean required) {
    }
}
