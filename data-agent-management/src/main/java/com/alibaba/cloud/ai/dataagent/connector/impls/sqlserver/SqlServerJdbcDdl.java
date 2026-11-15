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
package com.alibaba.cloud.ai.dataagent.connector.impls.sqlserver;

import com.alibaba.cloud.ai.dataagent.bo.schema.*;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zihen
 * @since  2025/12/14 17:34
 */
@Slf4j
@Service
public class SqlServerJdbcDdl extends AbstractJdbcDdl {

    @Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		String sql = "SELECT name FROM sys.databases WHERE database_id > 4;";
		return getDatabaseInfoBOS(connection, sql);
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		String sql = "SELECT schema_name FROM INFORMATION_SCHEMA.SCHEMATA WHERE schema_name NOT IN ('sys', 'INFORMATION_SCHEMA', 'guest', 'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin', 'db_backupoperator', 'db_datareader', 'db_datawriter', 'db_denydatareader', 'db_denydatawriter');";
		return getSchemaInfoBOS(connection, sql);
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String sql = """
                SELECT t.TABLE_NAME,\s
                CAST(ep.value AS NVARCHAR(MAX)) AS TABLE_COMMENT\s
                FROM INFORMATION_SCHEMA.TABLES t\s
                LEFT JOIN sys.tables st ON t.TABLE_NAME = st.name\s
                AND SCHEMA_NAME(st.schema_id) = t.TABLE_SCHEMA \
                LEFT JOIN sys.extended_properties ep ON st.object_id = ep.major_id AND ep.minor_id = 0 AND ep.name = 'MS_Description'\s
                WHERE t.TABLE_SCHEMA = '%s' AND t.TABLE_TYPE = 'BASE TABLE'\s
                """;
		if (StringUtils.isNotBlank(tablePattern)) {
			sql += "AND t.TABLE_NAME LIKE '%%' + '%s' + '%%' \n";
		}
		sql += "ORDER BY t.TABLE_NAME \n";
		sql += "OFFSET 0 ROWS FETCH NEXT 2000 ROWS ONLY;";
		sql = String.format(sql, schema, tablePattern);

		return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) ->
				TableInfoBO.builder()
						.name(row[0])
						.description(row[1])
						.build());
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		String sql = """
                SELECT t.TABLE_NAME,\s
                CAST(ep.value AS NVARCHAR(MAX)) AS TABLE_COMMENT\s
                FROM INFORMATION_SCHEMA.TABLES t\s
                LEFT JOIN sys.tables st ON t.TABLE_NAME = st.name\s
                LEFT JOIN sys.extended_properties ep ON st.object_id = ep.major_id AND ep.minor_id = 0 AND ep.name = 'MS_Description'\s
                WHERE t.TABLE_SCHEMA = '%s' AND t.TABLE_TYPE = 'BASE TABLE'\s
                AND t.TABLE_NAME IN (%s)\s
                ORDER BY t.TABLE_NAME;
                """;
		String tableListStr = SqlUtil.getQuotedTablesExpression(tables);
		sql = String.format(sql, schema, tableListStr);

		return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) ->
				TableInfoBO.builder()
						.name(row[0])
						.description(row[1])
						.build());
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String sql =  String.format("""
				SELECT\s
				c.COLUMN_NAME,\s
				CAST(ep.value AS NVARCHAR(MAX)) AS COLUMN_COMMENT,\s
				c.DATA_TYPE,\s
				CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 'true' ELSE 'false' END AS IS_PRIMARY_KEY,\s
				CASE WHEN c.IS_NULLABLE = 'NO' THEN 'true' ELSE 'false' END AS IS_NOT_NULL\s
				FROM INFORMATION_SCHEMA.COLUMNS c\s
				LEFT JOIN sys.columns sc ON OBJECT_ID(c.TABLE_SCHEMA + '.' + c.TABLE_NAME) = sc.object_id AND c.COLUMN_NAME = sc.name\s
				LEFT JOIN sys.extended_properties ep ON sc.object_id = ep.major_id AND sc.column_id = ep.minor_id AND ep.name = 'MS_Description'\s
				LEFT JOIN (\s
				    SELECT ku.TABLE_SCHEMA, ku.TABLE_NAME, ku.COLUMN_NAME\s
				    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc\s
				    JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku ON tc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME\s
				    WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'\s
				) pk ON c.TABLE_SCHEMA = pk.TABLE_SCHEMA AND c.TABLE_NAME = pk.TABLE_NAME AND c.COLUMN_NAME = pk.COLUMN_NAME\s
				WHERE c.TABLE_SCHEMA = '%s' AND c.TABLE_NAME = '%s'\s
				ORDER BY c.ORDINAL_POSITION;
				""", schema, table);
		return SqlExecutor.executeSqlAndExtract(connection, null, sql, super::newColumnInfoFromRow);
	}

	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		String sql = String.format("""
                SELECT\s
                FK.TABLE_NAME AS 'Table',\s
                CU.COLUMN_NAME AS 'Column',\s
                PK.TABLE_NAME AS 'Referenced_Table',\s
                PT.COLUMN_NAME AS 'Referenced_Column'\s
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS C\s
                INNER JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS FK ON C.CONSTRAINT_NAME = FK.CONSTRAINT_NAME\s
                INNER JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS PK ON C.UNIQUE_CONSTRAINT_NAME = PK.CONSTRAINT_NAME\s
                INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE CU ON C.CONSTRAINT_NAME = CU.CONSTRAINT_NAME\s
                INNER JOIN (SELECT i1.TABLE_NAME, i2.COLUMN_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS i1\s
                INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE i2 ON i1.CONSTRAINT_NAME = i2.CONSTRAINT_NAME\s
                WHERE i1.CONSTRAINT_TYPE = 'PRIMARY KEY') PT ON PT.TABLE_NAME = PK.TABLE_NAME\s
                WHERE FK.TABLE_SCHEMA = '%s' AND FK.TABLE_NAME IN (%s);
                """, schema, SqlUtil.getQuotedTablesExpression(tables));

		return SqlExecutor.executeSqlAndExtract(connection, null, sql, (row, rowNum) ->
				ForeignKeyInfoBO.builder()
						.table(row[0])
						.column(row[1])
						.referencedTable(row[2])
						.referencedColumn(row[3])
						.build());
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String sql = super.getSelectSql(BizDataSourceTypeEnum.SQL_SERVER.getTypeName(), table, column, 99);
		try {
			return SqlExecutor.executeSqlAndExtract(connection, null, sql,
					(row, rowNum) -> {
						if (column.equalsIgnoreCase(row[0])) {
							return null;
						}
						return row[0];
					});
		} catch (Exception e) {
			log.error("sampleColumn error, sql:{}", sql, e);
			return new ArrayList<>();
		}
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String sql = String.format(
				super.getSelectSql(BizDataSourceTypeEnum.SQL_SERVER.getTypeName(),
						table, "*", 20), schema, table);
		return SqlExecutor.executeSql(connection, schema, sql);
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.SQL_SERVER;
	}

}
