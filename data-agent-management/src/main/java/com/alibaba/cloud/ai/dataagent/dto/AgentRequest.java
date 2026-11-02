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
package com.alibaba.cloud.ai.dataagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 该对象用于绑定 SSE GET 请求中的查询参数
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRequest {

    /**
     * 智能体 ID
     */
    @NotBlank
    private String agentId;

    /**
     * 聊天记忆会话 ID
     */
    private String conversationId;

    /**
     * Graph 运行 ID，仅在人工反馈恢复时复用
     */
    private String threadId;

    /**
     * 用户查询语句
     */
    @NotBlank
    private String query;

    /**
     * 当前请求使用的数据源 ID
     */
    private Integer datasourceId;

    /**
     * 是否提交人工反馈
     */
    private boolean humanFeedback;

    /**
     * 人工反馈内容
     */
    private String humanFeedbackContent;

    /**
     * 是否拒绝了之前生成的计划
     */
    private boolean rejectedPlan;

    /**
     * 是否仅执行 NL2SQL
     */
    private boolean nl2sqlOnly;

    public String getOrNewThreadId() {
        return threadId == null || threadId.isBlank()
                ? UUID.randomUUID().toString()
                : threadId;
    }
}
