package com.alibaba.cloud.ai.dataagent.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL 执行计划预检查。
 * <p>
 * 只做保守拦截：小表全表扫描、无索引但数据量小、合理聚合查询都放行；
 * 只拦截明显高风险的大表明细扫描或 JOIN 结果扩张。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplainPrecheckService {

    private final DataSource dataSource;

    private static final int MAX_PLAN_ROWS = 64;

    @Value("${data-agent.explain-precheck.timeout-seconds:5}")
    private int timeoutSeconds;

    @Value("${data-agent.explain-precheck.max-estimated-rows:100000}")
    private long maxEstimatedRows;

    public PrecheckResult check(String finalSql) {
        if (finalSql == null || finalSql.isBlank()) {
            return PrecheckResult.allow();
        }

        long t0 = System.currentTimeMillis();
        try {
            List<ExplainRow> rows = explain(finalSql);
            PrecheckResult result = analyze(finalSql, rows);
            log.debug("EXPLAIN 预检查 {} | planRows={} cost={}ms | reason={}",
                    result.allowed() ? "PASS" : "BLOCK", rows.size(),
                    System.currentTimeMillis() - t0, result.reason());
            return result;
        } catch (SQLException e) {
            if (isTimeout(e)) {
                String reason = "EXPLAIN 预检查超时，说明数据库生成执行计划也较慢，当前 SQL 可能过重";
                log.warn("{}，已拦截 | sql={}", reason, truncate(finalSql, 300));
                return PrecheckResult.block(reason);
            }
            String reason = "EXPLAIN 预检查失败：" + truncate(e.getMessage(), 300);
            log.warn("{}，按 fail-open 放行", reason);
            return PrecheckResult.allow();
        } catch (Exception e) {
            String reason = "EXPLAIN 预检查失败：" + truncate(e.getMessage(), 300);
            log.warn("{}，按 fail-open 放行", reason);
            return PrecheckResult.allow();
        }
    }

    private List<ExplainRow> explain(String finalSql) throws SQLException {
        List<ExplainRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            trySetReadOnly(conn);
            try (Statement stmt = conn.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setQueryTimeout(timeoutSeconds);
                stmt.setMaxRows(MAX_PLAN_ROWS);
                try (ResultSet rs = stmt.executeQuery("EXPLAIN " + finalSql)) {
                    while (rs.next()) {
                        rows.add(readExplainRow(rs));
                    }
                }
            }
        }
        return rows;
    }

    private ExplainRow readExplainRow(ResultSet rs) throws SQLException {
        return new ExplainRow(
                getString(rs, "select_type"),
                getString(rs, "table"),
                getString(rs, "type"),
                getString(rs, "key"),
                getLong(rs, "rows"),
                getString(rs, "Extra")
        );
    }

    private PrecheckResult analyze(String sql, List<ExplainRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return PrecheckResult.allow();
        }

        if (!looksLikeDetailQuery(sql)) {
            return PrecheckResult.allow();
        }

        List<ExplainRow> tableRows = rows.stream()
                .filter(row -> row.table() != null && !row.table().isBlank())
                .toList();
        if (tableRows.isEmpty()) {
            return PrecheckResult.allow();
        }

        for (ExplainRow row : tableRows) {
            if (isRiskyAccess(row.accessType()) && row.rows() >= maxEstimatedRows) {
                return PrecheckResult.block("疑似大表明细扫描，表 " + tableName(row)
                        + " 使用 " + safe(row.accessType())
                        + " 访问，预计扫描 " + row.rows() + " 行");
            }
        }

        if (tableRows.size() >= 2) {
            long joinThreshold = joinExpansionThreshold();
            long estimatedJoinRows = estimatedJoinRows(tableRows, joinThreshold);
            if (estimatedJoinRows >= joinThreshold && hasJoinRiskSignal(tableRows)) {
                return PrecheckResult.block("疑似 JOIN 结果扩张，预计组合行数超过 "
                        + joinThreshold + "，且存在无索引访问或 Join Buffer 风险");
            }
        }

        return PrecheckResult.allow();
    }

    private boolean looksLikeDetailQuery(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return !(normalized.contains(" group by ")
                || normalized.contains(" having ")
                || normalized.contains("count(")
                || normalized.contains("sum(")
                || normalized.contains("avg(")
                || normalized.contains("min(")
                || normalized.contains("max("));
    }

    private boolean hasJoinRiskSignal(List<ExplainRow> rows) {
        int missingKeyCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            ExplainRow row = rows.get(i);
            String extra = safe(row.extra()).toLowerCase(Locale.ROOT);
            if (extra.contains("using join buffer")) {
                return true;
            }
            if (i > 0 && isRiskyAccess(row.accessType())) {
                return true;
            }
            if (isBlankKey(row.key())) {
                missingKeyCount++;
            }
        }
        return missingKeyCount >= 2;
    }

    private long estimatedJoinRows(List<ExplainRow> rows, long threshold) {
        long product = 1;
        for (ExplainRow row : rows) {
            long value = Math.max(1, row.rows());
            if (product > threshold / value) {
                return threshold;
            }
            product *= value;
        }
        return product;
    }

    private long joinExpansionThreshold() {
        return Math.max(maxEstimatedRows * 10, maxEstimatedRows);
    }

    private boolean isRiskyAccess(String accessType) {
        return "ALL".equalsIgnoreCase(accessType) || "index".equalsIgnoreCase(accessType);
    }

    private boolean isTimeout(SQLException e) {
        String state = e.getSQLState();
        return "HY000".equals(state) && e.getErrorCode() == 3024;
    }

    private boolean isBlankKey(String key) {
        return key == null || key.isBlank() || "NULL".equalsIgnoreCase(key);
    }

    private String tableName(ExplainRow row) {
        return row.table() == null || row.table().isBlank() ? "<unknown>" : row.table();
    }

    private void trySetReadOnly(Connection conn) {
        try {
            conn.setReadOnly(true);
        } catch (SQLException e) {
            log.debug("setReadOnly 失败，忽略：{}", e.getMessage());
        }
    }

    private String getString(ResultSet rs, String label) throws SQLException {
        if (!hasColumn(rs, label)) {
            return null;
        }
        return rs.getString(label);
    }

    private long getLong(ResultSet rs, String label) throws SQLException {
        if (!hasColumn(rs, label)) {
            return 0;
        }
        Object value = rs.getObject(label);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean hasColumn(ResultSet rs, String label) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (label.equalsIgnoreCase(md.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record ExplainRow(String selectType, String table, String accessType,
                              String key, long rows, String extra) {
    }

    public record PrecheckResult(boolean allowed, String reason) {
        public static PrecheckResult allow() {
            return new PrecheckResult(true, "OK");
        }

        public static PrecheckResult block(String reason) {
            return new PrecheckResult(false, reason);
        }
    }
}
