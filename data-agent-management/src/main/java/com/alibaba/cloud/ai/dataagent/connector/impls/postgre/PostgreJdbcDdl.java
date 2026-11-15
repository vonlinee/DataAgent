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
package com.alibaba.cloud.ai.dataagent.connector.impls.postgre;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jiuhe
 * @since 2024/3/15
 */
@Slf4j
@Service
public class PostgreJdbcDdl extends AbstractJdbcDdl {

    @Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		return getDatabaseInfoBOS(connection, "select datname from pg_database;");
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		return getSchemaInfoBOS(connection, "SELECT schema_name FROM information_schema.schemata;");
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String sql = """
                SELECT tb.table_name, d.description\s
                FROM information_schema.tables tb\s
                JOIN pg_class c ON c.relname = tb.table_name\s
                JOIN pg_namespace n ON c.relnamespace = n.oid and tb.table_schema = n.nspname\s
                LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = '0'\s
                WHERE tb.table_schema = '%s'\s
                """;
		if (StringUtils.isNotBlank(tablePattern)) {
			sql += "AND tb.table_name LIKE CONCAT('%%','%s','%%') \n";
		}
		sql += "limit 2000;";
		sql = String.format(sql, schema, tablePattern);

		return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) ->
				TableInfoBO.builder()
						.name(row[0])
						.description(row[1])
						.build());
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		String sql = String.format("""
                SELECT tb.table_name, d.description\s
                FROM information_schema.tables tb\s
                JOIN pg_class c ON c.relname = tb.table_name\s
                JOIN pg_namespace n ON c.relnamespace = n.oid and tb.table_schema = n.nspname\s
                LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = '0'\s
                WHERE tb.table_schema = '%s'\s
                AND tb.table_name IN (%s)\s
                 limit 2000;
                """, schema, SqlUtil.getQuotedTablesExpression(tables));

		return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) ->
				TableInfoBO.builder()
						.name(row[0])
						.description(row[1])
						.build());
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String sql = String.format("""
                SELECT
                    a.attname as column_name,
                    col_description(a.attrelid, a.attnum) as column_description,
                    pg_catalog.format_type(a.atttypid, a.atttypmod) as data_type,
                    CASE
                        WHEN a.attnum = ANY (ind.indkey) THEN true
                        ELSE false
                    END as 主键唯一,
                    a.attnotnull as 非空
                FROM pg_catalog.pg_attribute a
                LEFT JOIN pg_catalog.pg_index ind ON ind.indrelid = a.attrelid AND ind.indisprimary
                LEFT JOIN pg_catalog.pg_class c ON a.attrelid = c.oid
                LEFT JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE
                    c.relname = '%s' AND a.attnum > 0 AND NOT a.attisdropped AND n.nspname = '%s'
                ORDER BY a.attnum;""", table, schema);

		return SqlExecutor.executeSqlAndExtract(connection, null, sql, super::newColumnInfoFromRow);
	}

	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		String sql = String.format("""
                SELECT
                    tc.table_name,
                    kcu.column_name,
                    tc.constraint_name,
                    ccu.table_name AS foreign_table_name,
                    ccu.column_name AS foreign_column_name
                FROM
                    information_schema.table_constraints AS tc
                JOIN
                    information_schema.key_column_usage AS kcu
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage AS ccu
                    ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
                WHERE
                    tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema='public'
                    AND tc.table_name in (%s)
                """, SqlUtil.getQuotedTablesExpression(tables));

		return SqlExecutor.executeSqlAndExtract(connection, null, sql,
				(row, rowNum) -> ForeignKeyInfoBO.builder()
                .table(row[0])
                .column(row[1])
                .referencedTable(row[3])
                .referencedColumn(row[4])
                .build());
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String sql = String.format("""
                SELECT "%s" FROM "%s" LIMIT 99;
                """, column, table);

		try {
			return SqlExecutor.executeSqlAndExtract(connection, schema, sql,
					(row, rowNum) -> {
				if (column.equalsIgnoreCase(row[0])) {
					return null;
				}
				return row[0];
			});
        } catch (Exception e) {
			log.error("sampleColumn error, sql:{}", sql, e);
		}
		return new ArrayList<>();
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String sql = "SELECT * FROM %s LIMIT 20;".formatted(table);
		return SqlExecutor.executeSql(connection, schema, sql);
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.POSTGRESQL;
	}

}
