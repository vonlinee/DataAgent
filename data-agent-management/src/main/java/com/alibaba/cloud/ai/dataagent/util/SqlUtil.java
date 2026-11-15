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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL 工具类
 *
 * @author Yang Yufeng
 * @version 1.0
 */
@Slf4j
public final class SqlUtil {

	private static final String UNRESOLVED_PLACEHOLDER_REASON = "Generated SQL contains an unresolved '?' parameter "
			+ "placeholder. Replace every placeholder with a concrete value from the current instruction or previous "
			+ "step results.";

	private SqlUtil() {}

	/**
	 * 构建SELECT SQL语句
	 * @param typeName 数据源类型
	 * @param tableName 表名
	 * @param columnNames 列名
	 * @param limit 查询数量限制
	 * @return SELECT SQL语句
	 */
	public static String buildSelectSql(String typeName, String tableName, String columnNames, int limit) {
		if (tableName == null || tableName.isEmpty()) {
			throw new IllegalArgumentException("Table name cannot be empty");
		}
		if (columnNames == null || columnNames.isEmpty()) {
			columnNames = "*";
		}

		if (BizDataSourceTypeEnum.isSqlServerDialect(typeName)) {
			// SQL Server 使用 TOP
			return String.format("SELECT TOP %d %s FROM %s", limit, columnNames, tableName);
		}
		else if (BizDataSourceTypeEnum.isOracleDialect(typeName)) {
			// Oracle 使用 FETCH FIRST (Oracle 12c+)
			return String.format("SELECT %s FROM %s FETCH FIRST %d ROWS ONLY", columnNames, tableName, limit);
		}
		else {
			// MySQL, PostgreSQL, H2, SQLite 通用 LIMIT
			return String.format("SELECT %s FROM %s LIMIT %d", columnNames, tableName, limit);
		}
	}

	/**
	 * Validate structural constraints that must hold before generated SQL reaches the
	 * database.
	 * @param sql generated SQL
	 * @param dialect database dialect
	 * @return a regeneration reason when a deterministic constraint fails
	 */
	public static Optional<String> findGeneratedSqlValidationError(String sql, String dialect) {
		try {
			List<SQLStatement> statements = SQLUtils.parseStatements(sql, resolveDbType(dialect));
			if (statements.size() != 1) {
				return Optional.of("Generated SQL contains " + statements.size()
						+ " executable statements. Return exactly one SQL statement for the current plan step.");
			}
			if (containsUnresolvedPlaceholder(statements.get(0))) {
				return Optional.of(UNRESOLVED_PLACEHOLDER_REASON);
			}
		}
		catch (RuntimeException ex) {
			// Syntax and dialect errors continue through the existing semantic/execution
			// retry path. This guard only rejects the two deterministic conditions above.
			log.debug("Unable to parse generated SQL for structural validation; using existing validation flow", ex);
		}
		return Optional.empty();
	}

	private static boolean containsUnresolvedPlaceholder(SQLStatement statement) {
		boolean[] found = { false };
		statement.accept(new SQLASTVisitorAdapter() {
			@Override
			public boolean visit(SQLVariantRefExpr variant) {
				if ("?".equals(variant.getName())) {
					found[0] = true;
				}
				return !found[0];
			}
		});
		return found[0];
	}

	private static DbType resolveDbType(String dialect) {
		String normalizedDialect = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
		if ("dameng".equals(normalizedDialect)) {
			return DbType.dm;
		}
		DbType dbType = DbType.of(normalizedDialect);
		return dbType == null ? DbType.other : dbType;
	}

	public static String quoteIdentifier(String identifier) {
		return '"' + identifier.replace("\"", "\"\"") + '"';
	}

	/**
	 * Groups table names by schema.
	 * e.g. ["sakila.sales_by_store", "actor"] -> {"sakila": ["sales_by_store"], "": ["actor"]}
	 *
	 * @param tableNames    names like "schema.table" or "table"
	 * @param defaultSchema key used for names without a schema
	 * @return schema -> list of table names without schema, preserving input order
	 */
	public static Map<String, List<String>> groupBySchema(String[] tableNames, String defaultSchema) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		if (tableNames == null) return result;

		for (String raw : tableNames) {
			if (raw == null || raw.trim().isEmpty()) continue;

			String name = raw.trim();
			int dot = name.lastIndexOf('.');
			String schema = dot < 0 ? defaultSchema : name.substring(0, dot).trim();
			String table = dot < 0 ? name : name.substring(dot + 1).trim();

			if (schema == null || schema.isEmpty()) schema = defaultSchema;
			if (table.isEmpty()) continue;

			result.computeIfAbsent(schema, k -> new ArrayList<>()).add(table);
		}
		return result;
	}

	public static Map<String, List<String>> groupBySchema(String[] tableNames) {
		return groupBySchema(tableNames, "");
	}

	/**
	 * Extracts table names without schema from inputs like "schema.table" or "table".
	 * e.g. ["sakila.sales_by_store", "actor"] -> ["sales_by_store", "actor"]
	 *
	 * @param tableNames names like "schema.table" or "table"
	 * @return list of table names without schema, preserving input order
	 */
	public static List<String> extractTableNames(List<String> tableNames) {
		List<String> result = new ArrayList<>();
		if (tableNames == null) return result;
		for (String raw : tableNames) {
			if (raw == null || raw.trim().isEmpty()) continue;
			String name = raw.trim();
			int dot = name.lastIndexOf('.');
			String table = dot < 0 ? name : name.substring(dot + 1).trim();
			if (!table.isEmpty()) result.add(table);
		}
		return result;
	}

	/**
	 * Extracts table names without schema from inputs like "schema.table" or "table".
	 * e.g. ["sakila.sales_by_store", "actor"] -> ["actor"]
	 *
	 * @param tableNames names like "schema.table" or "table"
	 * @return list of table names without schema, preserving input order
	 */
	public static List<String> extractNoSchemedTableNames(List<String> tableNames) {
		List<String> result = new ArrayList<>();
		if (tableNames == null) return result;
		for (String raw : tableNames) {
			if (raw == null || raw.trim().isEmpty()) continue;
			String name = raw.trim();
			int dot = name.lastIndexOf('.');
			if (dot < 0) {
				result.add(name);
			}
		}
		return result;
	}

	public static String quoteColumnName(String columnName) {
		String[] columnPart = columnName.split("\\.");
		if (columnPart.length == 1) {
			return "`" + columnPart[0] + "`";
		}
		return "`" + columnPart[0] + "`.`" + columnPart[1] + "`";
	}

    public static String getQuotedTablesExpression(List<String> tables) {
        return tables.stream().map(x -> "'" + x + "'").collect(Collectors.joining(", "));
    }
}
