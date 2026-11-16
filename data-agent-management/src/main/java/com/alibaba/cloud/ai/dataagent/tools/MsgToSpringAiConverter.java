/*
 * Copyright 2026-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.tools;

import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.Source;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.ToolResultBlock;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.content.Media;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AgentScope-Java 2.0 Msg -> Spring AI Message 转换工具
 */
final class MsgToSpringAiConverter {

    private MsgToSpringAiConverter() {}

    public static Message convert(Msg msg) {
        if (msg == null) {
            throw new IllegalArgumentException("Msg must not be null");
        }
        MsgRole role = msg.getRole();
        if (role == null) {
            role = MsgRole.USER;
        }
        return switch (role) {
            case SYSTEM -> toSystemMessage(msg);
            case USER -> toUserMessage(msg);
            case ASSISTANT -> toAssistantMessage(msg);
            case TOOL -> toToolResponseMessage(msg);
            // default -> throw new IllegalArgumentException("Unsupported MsgRole: " + role);
        };
    }

    // ---------- SYSTEM ----------
    private static SystemMessage toSystemMessage(Msg msg) {
        String text = extractText(msg);
        return new SystemMessage(text);
    }

    // ---------- USER ----------
    private static UserMessage toUserMessage(Msg msg) {
        String text = extractText(msg);
        List<Media> mediaList = msg.getContent().stream()
                .filter(DataBlock.class::isInstance)
                .map(DataBlock.class::cast)
                .map(MsgToSpringAiConverter::toMedia)
                .collect(Collectors.toList());

        UserMessage.Builder builder = UserMessage.builder();
        if (!text.isEmpty()) builder.text(text);
        if (!mediaList.isEmpty()) builder.media(mediaList);
        return builder.build();
    }

    // ---------- ASSISTANT ----------
    private static AssistantMessage toAssistantMessage(Msg msg) {
        String text = extractText(msg);
        List<AssistantMessage.ToolCall> toolCalls = msg.getContent().stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .map(toolUse -> new AssistantMessage.ToolCall(
                        toolUse.getId(),
                        "function",
                        toolUse.getName(),
                        toJsonArguments(toolUse.getInput())))
                .collect(Collectors.toList());

        AssistantMessage.Builder builder = AssistantMessage.builder();
        if (!text.isEmpty()) builder.content(text);
        if (!toolCalls.isEmpty()) builder.toolCalls(toolCalls);
        return builder.build();
    }

    // ---------- TOOL ----------
    private static ToolResponseMessage toToolResponseMessage(Msg msg) {
        List<ToolResponseMessage.ToolResponse> responses = msg.getContent().stream()
                .filter(ToolResultBlock.class::isInstance)
                .map(ToolResultBlock.class::cast)
                .map(result -> new ToolResponseMessage.ToolResponse(
                        result.getId(),
                        result.getName(),
                        result.getOutput().stream()
                                .filter(TextBlock.class::isInstance)
                                .map(TextBlock.class::cast)
                                .map(TextBlock::getText)
                                .collect(Collectors.joining("\n"))))
                .collect(Collectors.toList());

        return ToolResponseMessage.builder().responses(responses).build();
    }

    // ---------- 辅助 ----------
    private static String extractText(Msg msg) {
        return msg.getContent().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .collect(Collectors.joining("\n"));
    }

    private static Media toMedia(DataBlock block) {
        Source source = block.getSource();
        if (!(source instanceof Base64Source base64Source)) {
            throw new IllegalArgumentException("Unsupported AgentScope media source: " + source);
        }
        MimeType mimeType = MimeTypeUtils.parseMimeType(base64Source.getMediaType());
        byte[] bytes = Base64.getDecoder().decode(base64Source.getData());
        return new Media(mimeType, new ByteArrayResource(bytes));
    }

    private static String toJsonArguments(Object input) {
        if (input == null) {
            return "{}";
        }
        if (input instanceof String s) {
            return s;
        }
        try {
            return JsonUtil.toJsonString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize tool arguments", e);
        }
    }
}
