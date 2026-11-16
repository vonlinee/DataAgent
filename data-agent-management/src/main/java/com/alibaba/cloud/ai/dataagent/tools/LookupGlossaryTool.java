/*
 * Copyright 2026-2026 the original author or authors.
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

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.service.business.BusinessKnowledgeService;
import com.alibaba.cloud.ai.dataagent.util.SpringAIUtils;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 业务术语口径查询工具，编程式注册。不走 @Tool 注解（description 必须编译时常量），
 * 改用 FunctionToolCallback 构建，把 YAML 术语列表拼进 description，让 LLM 直接看到所有术语名。
 * 用精确匹配而非向量检索：口径必须一术语一标准答案，模糊检索会出错。
 */
@Component
public class LookupGlossaryTool {

    @Resource
    BusinessKnowledgeService businessKnowledgeService;

    /**
     * 工具描述模板，%s 为术语列表注入位置。
     */
    private static final String TOOL_DESCRIPTION = """
            查询业务术语的标准口径（含定义 + 可复用 SQL 片段 + 同义词）。
            何时使用本工具：
            - 用户问题涉及"活跃客户""VIP""热门影片""高消费客户""租金"等业务指标术语时
            - 用户问题涉及"近 N 个月""最近""本月""上月""今年"等相对时间时
            """;

    private static final String TOOL_DESCRIPTION_TEMPLATE =
            TOOL_DESCRIPTION + """
                    <可用术语列表>
                    %s
                    </可用术语列表>
                    传入上述术语名或其同义词即可命中。未命中会返回全部术语供你重试。
                    """;

    @Tool(description = TOOL_DESCRIPTION_TEMPLATE)
    public String lookupGlossary(
            @ToolParam(description = """
                    要查的术语名或同义词，必传，如 "活跃客户"、"近3个月"、"最近"
                    """)
            List<String> terms, ToolContext toolContext) {
        if (terms == null || terms.isEmpty()) {
            return "入参为空，请必须传入具体术语名（如 \"活跃客户\"），参考工具说明中的可用术语列表，再次尝试调用本工具。";
        }
        Integer agentId = SpringAIUtils.getInteger(toolContext, Constant.AGENT_ID);
        if (agentId == null) {
            return "未找到 agentId，请检查工具调用上下文，再次尝试调用本工具。";
        }
        List<BusinessKnowledgeVO> knowledge = businessKnowledgeService.getKnowledge(Long.valueOf(agentId));
        return formatTermsList(knowledge);
    }

    public static String getToolDescription(List<BusinessKnowledgeVO> businessKnowledgeVOS) {
        String termsBlock = formatTermsList(businessKnowledgeVOS);
        return TOOL_DESCRIPTION_TEMPLATE.formatted(termsBlock);
    }

    private static String format(BusinessKnowledgeVO g) {
        StringBuilder sb = new StringBuilder();
        sb.append("【术语】").append(g.getBusinessTerm()).append("\n\n");
        if (g.getDescription() != null && !g.getDescription().isBlank()) {
            sb.append("【标准口径】").append(g.getDescription()).append("\n\n");
        }
        if (g.getSqlFragment() != null && !g.getSqlFragment().isBlank()) {
            sb.append("【SQL 片段（可直接复用）】\n").append(g.getSqlFragment()).append("\n\n");
        }
        if (!g.getSynonyms().isEmpty()) {
            sb.append("【同义词】").append(String.join("、", g.getSynonyms())).append('\n');
        }
        return sb.toString().trim();
    }

    private static String formatMiss(List<BusinessKnowledgeVO> allTerms, String query) {
        if (allTerms.isEmpty()) {
            return "未找到术语「" + query + "」的标准口径。当前 glossary 为空——"
                    + "可能是 schema 字典未正确加载，请检查 schema.yml 的 glossary section。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("未找到术语「").append(query).append("」的标准口径。\n\n");
        sb.append("当前已登记的术语（用准确名或同义词重试）：\n");
        for (BusinessKnowledgeVO g : allTerms) {
            sb.append("- ").append(g.getBusinessTerm());
            if (!g.getSynonyms().isEmpty()) {
                sb.append("（同义词：").append(String.join("/", g.getSynonyms())).append("）");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 把术语列表渲染为紧凑格式，每个术语一行附带同义词。
     */
    private static String formatTermsList(List<BusinessKnowledgeVO> terms) {
        StringBuilder sb = new StringBuilder();
        for (BusinessKnowledgeVO g : terms) {
            sb.append("- ").append(g.getBusinessTerm());
            if (!g.getSynonyms().isEmpty()) {
                sb.append("（同义词：").append(String.join("、", g.getSynonyms())).append("）");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
