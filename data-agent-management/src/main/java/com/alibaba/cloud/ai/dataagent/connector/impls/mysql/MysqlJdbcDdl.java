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
package com.alibaba.cloud.ai.dataagent.connector.impls.mysql;

import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.util.JdbcUtils;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.*;

@Service
public class MysqlJdbcDdl extends AbstractJdbcDdl {

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		return getDatabaseInfoBOS(connection, "show databases;");
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		return Collections.emptyList();
	}

	private String getShowTablesSql(Connection connection, String schema, String tablePattern) {
		String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT\s
                FROM INFORMATION_SCHEMA.TABLES\s
                WHERE TABLE_SCHEMA = '%s'\s
                """;
		if (StringUtils.isNotBlank(tablePattern)) {
			sql += "AND TABLE_NAME LIKE CONCAT('%%','%s','%%') \n";
		}
		sql += "limit 2000;";
        sql = String.format(sql, JdbcUtils.getCatalog(connection), tablePattern);
        return sql;
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String showTablesSql = getShowTablesSql(connection, schema, tablePattern);
		return SqlExecutor.executeSqlAndExtract(connection, showTablesSql, (row, rowNum) ->
				TableInfoBO.builder()
						.name(row[0])
						.description(row[1])
						.build());
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT\s
                FROM INFORMATION_SCHEMA.TABLES\s
                WHERE TABLE_SCHEMA = '%s'\s
                AND TABLE_NAME in(%s)\s
                limit 200;
                """;
		String tableListStr = SqlUtil.getQuotedTablesExpression(tables);
		sql = String.format(sql, JdbcUtils.getCatalog(connection), tableListStr);

		return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) -> {
			String tableName = row[0];
			String tableDesc = row[1];
			return TableInfoBO.builder().name(tableName).description(tableDesc).build();
		});
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String sql = """
				SELECT column_name, column_comment, data_type, \
				IF(column_key='PRI','true','false') AS '主键唯一',\s
				IF(IS_NULLABLE='NO','true','false') AS '非空'\s
				FROM information_schema.COLUMNS WHERE table_schema='%s' and table_name='%s';
				""";
		sql = String.format(sql, JdbcUtils.getCatalog(connection), table);

		return SqlExecutor.executeSqlAndExtract(connection, "INFORMATION_SCHEMA", sql,
                super::newColumnInfoFromRow);
	}

	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		String sql = """
			SELECT
				 TABLE_NAME AS '表名',
				 COLUMN_NAME AS '列名',
				 CONSTRAINT_NAME AS '约束名',
				 REFERENCED_TABLE_NAME AS '引用表名',
				 REFERENCED_COLUMN_NAME AS '引用列名'
			FROM
				 INFORMATION_SCHEMA.KEY_COLUMN_USAGE
			WHERE
				 CONSTRAINT_SCHEMA = '%s' AND CONSTRAINT_NAME != 'PRIMARY'
				 AND TABLE_NAME in(%s) AND REFERENCED_TABLE_NAME in (%s);
			""";
		String tableListStr = SqlUtil.getQuotedTablesExpression(tables);
		sql = String.format(sql, JdbcUtils.getCatalog(connection), tableListStr, tableListStr);

		return SqlExecutor.executeSqlAndExtract(connection, "INFORMATION_SCHEMA", sql,
                this::newForeignKeyFromRow);
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String sql = "SELECT `%s` FROM `%s` LIMIT 99;";
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
		return BizDataSourceTypeEnum.MYSQL;
	}

}
