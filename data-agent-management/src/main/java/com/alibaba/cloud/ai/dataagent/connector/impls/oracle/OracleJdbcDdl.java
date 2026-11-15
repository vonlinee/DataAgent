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
package com.alibaba.cloud.ai.dataagent.connector.impls.oracle;

import com.alibaba.cloud.ai.dataagent.bo.schema.*;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.exception.RuntimeSqlException;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OracleJdbcDdl extends AbstractJdbcDdl {

	/**
	 * 获取当前用户的 schema，如果参数 schema 为空则使用连接的 schema
	 */
	private String getSchema(Connection connection, String schema) {
		if (StringUtils.isNotBlank(schema)) {
			return schema.toUpperCase();
		}
		try {
			String connSchema = connection.getSchema();
			if (StringUtils.isNotBlank(connSchema)) {
				return connSchema.toUpperCase();
			}
			// 如果都为空，使用当前用户
			return connection.getMetaData().getUserName().toUpperCase();
        } catch (SQLException e) {
			throw new RuntimeSqlException(e);
		}
	}

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		// Oracle 中 schema 等同于用户
		String sql = "SELECT USERNAME FROM ALL_USERS ORDER BY USERNAME";
		return getDatabaseInfoBOS(connection, sql);
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		String sql = "SELECT USERNAME FROM ALL_USERS ORDER BY USERNAME";
		return getSchemaInfoBOS(connection, sql);
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String ownerSchema = getSchema(connection, schema);
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT t.TABLE_NAME, c.COMMENTS FROM ALL_TABLES t ");
		sql.append("LEFT JOIN ALL_TAB_COMMENTS c ON t.TABLE_NAME = c.TABLE_NAME AND t.OWNER = c.OWNER ");
		sql.append("WHERE t.OWNER = '").append(ownerSchema).append("' ");

		if (StringUtils.isNotBlank(tablePattern)) {
			sql.append("AND t.TABLE_NAME LIKE '%").append(tablePattern.toUpperCase()).append("%' ");
		}
		sql.append("AND ROWNUM <= 2000 ");
		sql.append("ORDER BY t.TABLE_NAME");

		return SqlExecutor.executeSqlAndExtract(connection, ownerSchema, sql.toString(),
				(row, rowNum) -> {
			String tableName = row[0];
			String tableDesc = row.length > 1 ? row[1] : null;
			return TableInfoBO.builder().name(tableName).description(tableDesc).build();
		});
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		if (tables == null || tables.isEmpty()) {
			return tableInfoList;
		}
		String ownerSchema = getSchema(connection, schema);
		String tableListStr = tables.stream()
				.map(x -> "'" + x.toUpperCase() + "'")
				.collect(Collectors.joining(", "));
		String sql = String.format("SELECT t.TABLE_NAME, c.COMMENTS FROM ALL_TABLES t "
						+ "LEFT JOIN ALL_TAB_COMMENTS c ON t.TABLE_NAME = c.TABLE_NAME AND t.OWNER = c.OWNER "
						+ "WHERE t.OWNER = '%s' AND t.TABLE_NAME IN (%s) " + "AND ROWNUM <= 200 " + "ORDER BY t.TABLE_NAME",
				ownerSchema, tableListStr);

		return SqlExecutor.executeSqlAndExtract(connection, sql,
				(row, rowNum) -> {
					String tableName = row[0];
					String tableDesc = row.length > 1 ? row[1] : null;
					return TableInfoBO.builder().name(tableName).description(tableDesc).build();
				});
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String ownerSchema = getSchema(connection, schema);
		String upperTable = table.toUpperCase();

		String sql = String.format("SELECT " + "    c.COLUMN_NAME, " + "    cc.COMMENTS, " + "    c.DATA_TYPE, "
						+ "    CASE WHEN EXISTS ( " + "        SELECT 1 FROM ALL_CONSTRAINTS uc "
						+ "        JOIN ALL_CONS_COLUMNS ucc ON uc.CONSTRAINT_NAME = ucc.CONSTRAINT_NAME AND uc.OWNER = ucc.OWNER "
						+ "        WHERE uc.CONSTRAINT_TYPE = 'P' " + "            AND uc.OWNER = '%s' "
						+ "            AND uc.TABLE_NAME = c.TABLE_NAME "
						+ "            AND ucc.COLUMN_NAME = c.COLUMN_NAME "
						+ "    ) THEN 'true' ELSE 'false' END AS IS_PRIMARY, "
						+ "    CASE WHEN c.NULLABLE = 'N' THEN 'true' ELSE 'false' END AS IS_NOT_NULL "
						+ "FROM ALL_TAB_COLUMNS c " + "LEFT JOIN ALL_COL_COMMENTS cc ON c.TABLE_NAME = cc.TABLE_NAME "
						+ "    AND c.OWNER = cc.OWNER AND c.COLUMN_NAME = cc.COLUMN_NAME "
						+ "WHERE c.OWNER = '%s' AND c.TABLE_NAME = '%s' " + "ORDER BY c.COLUMN_ID", ownerSchema,
				ownerSchema, upperTable);

		return SqlExecutor.executeSqlAndExtract(connection, null, sql, super::newColumnInfoFromRow);
	}

	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		List<ForeignKeyInfoBO> foreignKeyInfoList = Lists.newArrayList();
		if (tables == null || tables.isEmpty()) {
			return foreignKeyInfoList;
		}
		String ownerSchema = getSchema(connection, schema);
		String tableListStr = SqlUtil.getQuotedTablesExpression(tables);
		String sql = String.format("""
						SELECT
						  ucc1.TABLE_NAME,
						  ucc1.COLUMN_NAME,
						  uc.CONSTRAINT_NAME,
						  ucc2.TABLE_NAME AS REFERENCED_TABLE_NAME,
						  ucc2.COLUMN_NAME AS REFERENCED_COLUMN_NAME
						FROM
						  ALL_CONSTRAINTS uc
						  JOIN ALL_CONS_COLUMNS ucc1 ON uc.CONSTRAINT_NAME = ucc1.CONSTRAINT_NAME
						  AND uc.OWNER = ucc1.
						  OWNER JOIN ALL_CONSTRAINTS uc_ref ON uc.R_CONSTRAINT_NAME = uc_ref.CONSTRAINT_NAME
						  AND uc.R_OWNER = uc_ref.
						  OWNER JOIN ALL_CONS_COLUMNS ucc2 ON uc_ref.CONSTRAINT_NAME = ucc2.CONSTRAINT_NAME
						  AND uc_ref.OWNER = ucc2.OWNER
						  AND ucc1.POSITION = ucc2.POSITION
						WHERE
						  uc.CONSTRAINT_TYPE = 'R'
						  AND uc.OWNER = '%s'
						  AND ucc1.TABLE_NAME IN (%s)
						  AND ucc2.TABLE_NAME IN (%s)
						ORDER BY ucc1.TABLE_NAME, uc.CONSTRAINT_NAME, ucc1.POSITION
						""", ownerSchema, tableListStr, tableListStr);
		return SqlExecutor.executeSqlAndExtract(connection, null, sql, super::newForeignKeyFromRow);
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String sql = String.format("SELECT %s FROM %s WHERE ROWNUM <= 99", column.toUpperCase(), table.toUpperCase());
		return SqlExecutor.executeSqlAndExtract(connection, null, sql,
				(row, rowNum) -> {
					if (row[0] == null) {
						return null;
					}
					return row[0];
				});
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String sql = String.format("SELECT * FROM %s WHERE ROWNUM <= 20", table.toUpperCase());
		return SqlExecutor.executeSql(connection, schema, sql);
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.ORACLE;
	}

}
