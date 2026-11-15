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
package com.alibaba.cloud.ai.dataagent.connector.impls.h2;

import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.List;

@Service
public class H2JdbcDdl extends AbstractJdbcDdl {

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		String sql = "SELECT CATALOG_NAME FROM INFORMATION_SCHEMA.INFORMATION_SCHEMA_CATALOG_NAME;";
		return getDatabaseInfoBOS(connection, sql);
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		String sql = "SELECT schema_name FROM information_schema.schemata;";
		return getSchemaInfoBOS(connection, sql);
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String sql = """
                SELECT TABLE_NAME, REMARKS\s
                FROM INFORMATION_SCHEMA.TABLES\s
                WHERE TABLE_SCHEMA = '%s'\s
                """;
		if (StringUtils.isNotBlank(tablePattern)) {
			sql += "AND TABLE_NAME LIKE CONCAT('%%','%s','%%') \n";
		}
		sql += "limit 2000;";
		sql = String.format(sql, schema, tablePattern);
		return getTableInfoBOS(connection, schema, sql);
	}

	protected List<TableInfoBO> getTableInfoBOS(Connection connection, String schema, String sql) {
		return SqlExecutor.executeSqlAndExtract(connection, sql,
				(row, rowNum) -> TableInfoBO.builder()
                        .schema(schema)
                        .name(row[0])
                        .description(row[1])
                        .build());
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		String sql = """
                SELECT TABLE_NAME, REMARKS\s
                FROM INFORMATION_SCHEMA.TABLES\s
                WHERE TABLE_SCHEMA = '%s'\s
                AND TABLE_NAME in(%s)\s
                limit 200;
                """.formatted(schema, SqlUtil.getQuotedTablesExpression(tables));
		return getTableInfoBOS(connection, schema, sql);
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String sql = """
                SELECT column_name, remarks, data_type,\s
                CASE WHEN IS_IDENTITY = 'YES' THEN TRUE ELSE FALSE END AS 主键唯一,\s
                CASE WHEN IS_NULLABLE = 'NO' THEN TRUE ELSE FALSE END AS 非空\s
                FROM information_schema.COLUMNS \
                WHERE table_schema='%s' and table_name='%s';
                """;
		return SqlExecutor.executeSqlAndExtract(connection, "INFORMATION_SCHEMA", sql,
                super::newColumnInfoFromRow);
	}

	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		String tableListStr = SqlUtil.getQuotedTablesExpression(tables);
		String sql = """
                SELECT\s
                    kc.TABLE_NAME AS 表名,
                    kc.COLUMN_NAME AS 列名,
                    kc2.table_name AS 引用表名,
                    kc2.column_name AS 引用列名
                FROM\s
                    information_schema.referential_constraints rc \s
                    join information_schema.key_column_usage kc on rc.constraint_name=kc.constraint_name\s
                    join information_schema.key_column_usage kc2 on rc.unique_constraint_name=kc2.constraint_name\s
                WHERE kc.constraint_schema='%s' and kc.table_name in (%s) and kc2.table_name in (%s);
                """.formatted(schema, tableListStr, tableListStr);
		return SqlExecutor.executeSqlAndExtract(connection, "INFORMATION_SCHEMA", sql,
				(row, rowNum) -> ForeignKeyInfoBO.builder()
						.table(row[0])
						.column(row[1])
						.referencedTable(row[2])
						.referencedColumn(row[3])
						.build());
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String sql = String.format("SELECT `%s` FROM `%s`.`%s` LIMIT 99;", column, schema, table);
		return SqlExecutor.executeSqlAndExtract(connection, null, sql,
				(row, rowNum) -> {
					if (column.equalsIgnoreCase(row[0])) {
						return null;
					}
					return row[0];
				});
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String sql = String.format("SELECT * FROM `%s` LIMIT 20;", table);
		return SqlExecutor.executeSql(connection, schema, sql);
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.H2;
	}

}
