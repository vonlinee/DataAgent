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
package com.alibaba.cloud.ai.dataagent.connector.impls.hive;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.*;

import static com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil.wrapType;

/**
 * Hive JDBC DDL 执行器实现
 */
@Service
public class HiveJdbcDdl extends AbstractJdbcDdl {

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		return getDatabaseInfoBOS(connection, "SHOW DATABASES");
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		return Collections.emptyList();
	}

	private static String getShowTablesSql(String schema, String tablePattern) {
		StringBuilder sql = new StringBuilder("SHOW TABLES");
		if (StringUtils.isNotBlank(schema)) {
			sql.append(" IN ").append(schema);
		}
		if (StringUtils.isNotBlank(tablePattern)) {
			sql.append(" LIKE '").append(tablePattern).append("'");
		}
		return sql.toString();
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String showTablesSql = getShowTablesSql(schema, tablePattern);
		return SqlExecutor.executeSqlAndExtract(connection, showTablesSql, (row, rowNum) -> {
			if (row.length == 0) {
				return null;
			}
			return TableInfoBO.of(row[0]);
		});
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		for (String tableName : tables) {
			String sql = "DESCRIBE FORMATTED " + (StringUtils.isNotBlank(schema) ? schema + "." : "") + tableName;
			try {
				List<String> comments = SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) ->
						row.length >= 2 && "comment".equalsIgnoreCase(row[0].trim()) ? row[1] : null);
				String tableComment = comments.stream()
						.filter(StringUtils::isNotBlank)
						.findFirst()
						.orElse("");
				tableInfoList.add(TableInfoBO.builder()
						.name(tableName)
						.description(tableComment)
						.build());
            } catch (Throwable throwable) {
				tableInfoList.add(TableInfoBO.builder().name(tableName).build());
			}
		}
		return tableInfoList;
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String fullTableName = StringUtils.isNotBlank(schema) ? schema + "." + table : table;
		String sql = "DESCRIBE " + fullTableName;
		return SqlExecutor.executeSqlAndExtract(connection, sql, (row, rowNum) -> {
			if (row.length < 2) {
				return null;
			}
			String colName = row[0];
			String dataType = row[1];
			String comment = row.length >= 3 ? row[2] : "";
			if (StringUtils.isBlank(colName) || colName.startsWith("#")) {
				return null;
			}
			return ColumnInfoBO.builder()
					.name(colName)
					.description(comment)
					.type(wrapType(dataType))
					.primary(false) // Hive 不支持主键
					.notnull(false) // Hive 不强制非空约束
					.build();
		});
	}

	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		return Collections.emptyList();
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String fullTableName = StringUtils.isNotBlank(schema) ? schema + "." + table : table;
		String sql = String.format("SELECT `%s` FROM %s LIMIT 99", column, fullTableName);
		return SqlExecutor.executeSqlAndExtract(connection, null, sql, (row, rowNum) -> {
			if (column.equalsIgnoreCase(row[0])) {
				return null;
			}
			return row[0];
		});
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String fullTableName = StringUtils.isNotBlank(schema) ? schema + "." + table : table;
		String sql = String.format("SELECT * FROM %s LIMIT 20", fullTableName);
		return SqlExecutor.executeSql(connection, schema, sql);
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.HIVE;
	}

}
