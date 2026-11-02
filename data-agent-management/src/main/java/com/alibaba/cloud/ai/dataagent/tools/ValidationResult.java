package com.alibaba.cloud.ai.dataagent.tools;

/**
 * SQL 安全校验结果
 *
 * @param valid   是否通过
 * @param reason  失败原因（可回给 LLM）；成功时为 "OK"
 * @param safeSql 通过时的安全 SQL（已注入/截断 LIMIT）；失败时为 null
 */
public record ValidationResult(boolean valid,
                               String reason,
                               String safeSql) {
}
