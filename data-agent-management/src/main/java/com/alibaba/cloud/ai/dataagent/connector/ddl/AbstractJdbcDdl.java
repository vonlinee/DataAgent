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
package com.alibaba.cloud.ai.dataagent.connector.ddl;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.BooleanUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public abstract class AbstractJdbcDdl implements Ddl {

    @NonNull
    public List<DatabaseInfoBO> getDatabaseInfoBOS(Connection connection, String sql) {
        List<DatabaseInfoBO> databaseInfoList = Lists.newArrayList();
        try {
            String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);
            if (resultArr.length <= 1) {
                return Lists.newArrayList();
            }

            for (int i = 1; i < resultArr.length; i++) {
                if (resultArr[i].length == 0) {
                    continue;
                }
                String database = resultArr[i][0];
                databaseInfoList.add(DatabaseInfoBO.builder().name(database).build());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return databaseInfoList;
    }

    @NonNull
    public List<SchemaInfoBO> getSchemaInfoBOS(Connection connection, String sql) {
        return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) ->
                SchemaInfoBO.builder()
                        .name(row[0])
                        .build());
    }

    public String getSelectSql(String typeName, String tableName, String columnNames, int limit) {
        return SqlUtil.buildSelectSql(typeName, tableName, columnNames, limit);
    }

    @Nullable
    protected ColumnInfoBO newColumnInfoFromRow(String[] row, int rowNum) {
        if (row.length < 5) {
            return null;
        }
        return ColumnInfoBO.builder()
                .name(row[0])
                .description(row[1])
                .type(ColumnTypeUtil.wrapType(row[2]))
                .primary(BooleanUtils.toBoolean(row[3]))
                .notnull(BooleanUtils.toBoolean(row[4]))
                .build();
    }

    @Nullable
    protected ForeignKeyInfoBO newForeignKeyFromRow(String[] row, int rowNum) {
        if (row.length < 5) {
            return null;
        }
        return ForeignKeyInfoBO.builder()
                .table(row[0])
                .column(row[1])
                .referencedTable(row[3])
                .referencedColumn(row[4])
                .build();
    }
}
