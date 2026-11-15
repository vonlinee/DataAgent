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
package com.alibaba.cloud.ai.dataagent.connector.impls.dameng;

import com.alibaba.cloud.ai.dataagent.bo.schema.*;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;

@Service
public class DamengJdbcDdl extends AbstractJdbcDdl {

    @Override
    public List<DatabaseInfoBO> showDatabases(Connection connection) {
        // 达梦通常以实例+用户作为schema，数据库枚举意义不大，这里返回空集
        return Collections.emptyList();
    }

    @Override
    public List<SchemaInfoBO> showSchemas(Connection connection) {
        String sql = "SELECT USERNAME FROM SYS.ALL_USERS";
        return getSchemaInfoBOS(connection, sql);
    }

    @Override
    public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
        String sql = "SELECT TABLE_NAME FROM USER_TABLES";
        if (StringUtils.isNotBlank(tablePattern)) {
            sql = "SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE '%' || '" + tablePattern + "' || '%'";
        }

        return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) -> {
            String tableName = row[0];
            return TableInfoBO.builder().name(tableName).description("").build();
        });
    }

    @Override
    public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return Lists.newArrayList();
        }
        String sql = "SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME in( %s )"
                .formatted(SqlUtil.getQuotedTablesExpression(tables));
        return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) -> {
            String tableName = row[0];
            return TableInfoBO.builder().name(tableName).description("").build();
        });
    }

    @Override
    public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE FROM USER_TAB_COLUMNS WHERE TABLE_NAME='"
                + table + "'";
        return SqlExecutor.executeSqlAndExtract(connection, null, sql, (row, rowNum) ->
                ColumnInfoBO.builder()
                        .name(row[0])
                        .description("")
                        .type(ColumnTypeUtil.wrapType(row[1]))
                        .primary(false)
                        .notnull(BooleanUtils.toBoolean(Boolean.toString("N".equalsIgnoreCase(row[3]))))
                        .build());
    }

    @Override
    public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return Lists.newArrayList();
        }
        String tableListStr = SqlUtil.getQuotedTablesExpression(tables);
        String sql = "SELECT uc.TABLE_NAME, ucc.COLUMN_NAME, uc.CONSTRAINT_NAME, uc.R_OWNER, uc.R_CONSTRAINT_NAME "
                + "FROM USER_CONSTRAINTS uc JOIN USER_CONS_COLUMNS ucc ON uc.CONSTRAINT_NAME = ucc.CONSTRAINT_NAME "
                + "WHERE uc.CONSTRAINT_TYPE='R' AND uc.TABLE_NAME IN (" + tableListStr + ")";

        return SqlExecutor.executeSqlAndExtract(connection, null, sql, (row, rowNum) ->
                ForeignKeyInfoBO.builder()
                        .table(row[0])
                        .column(row[1])
                        .referencedTable("")
                        .referencedColumn("")
                        .build());
    }

    @Override
    public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
        String sql = "SELECT " + column + " FROM " + table + " FETCH FIRST 99 ROWS ONLY";
        return SqlExecutor.executeSqlAndExtract(connection, null, sql, (row, rowNum) -> {
            if (column.equalsIgnoreCase(row[0])) {
                return null;
            }
            return row[0];
        });
    }

    @Override
    public ResultSetBO scanTable(Connection connection, String schema, String table) {
        String sql = "SELECT * FROM " + table + " FETCH FIRST 20 ROWS ONLY";
        return SqlExecutor.executeSql(connection, schema, sql);
    }

    @Override
    public BizDataSourceTypeEnum getDataSourceType() {
        return BizDataSourceTypeEnum.DAMENG;
    }

}
