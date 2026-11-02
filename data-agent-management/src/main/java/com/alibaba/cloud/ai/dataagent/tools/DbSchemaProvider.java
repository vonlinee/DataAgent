package com.alibaba.cloud.ai.dataagent.tools;

import com.alibaba.cloud.ai.dataagent.agent.SchemaProvider;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceMetadataService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class DbSchemaProvider implements SchemaProvider {
    @Autowired
    DatasourceMetadataService datasourceMetadataService;

    @Override
    public String listTables(Integer dataSourceId) {
        try {
            List<TableInfoBO> tables = datasourceMetadataService.getDatasourceTableDetails(dataSourceId);
            return formatTableContext(tables);
        } catch (Exception e) {
            throw new IllegalStateException("获取数据源 " + dataSourceId + " 的表结构失败", e);
        }
    }

    private String formatTableContext(List<TableInfoBO> tables) {
        if (tables == null || tables.isEmpty()) {
            return "当前数据源没有可用的数据表或视图。";
        }

        StringBuilder context = new StringBuilder("当前数据源的数据表和视图：\n");
        int tableIndex = 0;
        for (TableInfoBO table : tables) {
            if (table == null || StringUtils.isBlank(table.getName())) {
                continue;
            }

            tableIndex++;
            context.append(tableIndex).append(". 表名：`")
                    .append(qualifiedTableName(table)).append("`\n")
                    .append("   类型：").append(valueOrDefault(table.getType(), "TABLE")).append("\n");
            appendIfPresent(context, "描述", table.getDescription());
            appendIfPresent(context, "主键", joinValues(table.getPrimaryKeys()));
            appendIfPresent(context, "外键关系", table.getForeignKey());
        }

        return tableIndex == 0 ? "当前数据源没有可用的数据表或视图。" : context.toString().trim();
    }

    private String qualifiedTableName(TableInfoBO table) {
        if (StringUtils.isBlank(table.getSchema())) {
            return table.getName();
        }
        return table.getSchema() + "." + table.getName();
    }

    private void appendIfPresent(StringBuilder context, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            context.append("   ").append(label).append("：").append(value).append("\n");
        }
    }

    private String joinValues(List<String> values) {
        if (values == null) {
            return "";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("、"));
    }

    private String valueOrDefault(String value, String defaultValue) {
        return StringUtils.defaultIfBlank(value, defaultValue);
    }

    /**
     * 查询指定数据表的列元数据，并将其整理成适合注入 LLM 上下文的文本。
     *
     * @param dataSourceId 数据源 ID
     * @param tableNames 要查询的表名列表
     * @return 包含字段名、类型、非空约束、主键、注释和示例值的表结构描述
     */
    @Override
    public String describeTables(Integer dataSourceId, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return "未提供有效的表名。请传入具体表名，或先调用 listTables 查看可用表。";
        }

        StringBuilder context = new StringBuilder("指定数据表的字段详情：\n");
        int tableIndex = 0;
        try {
            for (String tableName : tableNames) {
                if (StringUtils.isBlank(tableName)) {
                    continue;
                }

                String normalizedTableName = tableName.trim();
                List<ColumnInfoBO> columns = datasourceMetadataService.getTableColumnDetails(dataSourceId,
                        normalizedTableName);
                tableIndex++;
                context.append(tableIndex).append(". 表名：`").append(normalizedTableName).append("`\n");
                appendColumnContext(context, columns);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("获取数据源 " + dataSourceId + " 的表字段详情失败", e);
        }

        return tableIndex == 0 ? "未提供有效的表名。请传入具体表名，或先调用 listTables 查看可用表。"
                : context.toString().trim();
    }

    private void appendColumnContext(StringBuilder context, List<ColumnInfoBO> columns) {
        if (columns == null || columns.isEmpty()) {
            context.append("   字段：无可用字段\n");
            return;
        }

        context.append("   字段：\n");
        int columnIndex = 0;
        for (ColumnInfoBO column : columns) {
            if (column == null || StringUtils.isBlank(column.getName())) {
                continue;
            }

            columnIndex++;
            context.append("   ").append(columnIndex).append(") `").append(column.getName().trim()).append("`\n")
                    .append("      类型：").append(valueOrDefault(column.getType(), "UNKNOWN"))
                    .append("；非空：").append(column.isNotnull() ? "是" : "否")
                    .append("；主键：").append(column.isPrimary() ? "是" : "否").append("\n");
            appendIfPresent(context, "注释", column.getDescription());
            appendIfPresent(context, "示例值", column.getSamples());
        }

        if (columnIndex == 0) {
            context.append("   字段：无可用字段\n");
        }
    }
}
