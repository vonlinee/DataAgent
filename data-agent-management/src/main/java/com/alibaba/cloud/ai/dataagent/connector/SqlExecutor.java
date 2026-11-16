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
package com.alibaba.cloud.ai.dataagent.connector;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.enums.DatabaseDialectEnum;
import com.alibaba.cloud.ai.dataagent.exception.RuntimeSqlException;
import com.alibaba.cloud.ai.dataagent.util.JdbcUtils;
import jakarta.annotation.Nullable;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.function.ThrowingSupplier;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Responsible for executing SQL and returning structured results.
 */
public class SqlExecutor {

	public static final Integer RESULT_SET_LIMIT = 1000;

	public static final Integer STATEMENT_TIMEOUT = 30;

	/**
	 * Execute SQL query and return structured results (with column information)
	 * @param connection database connection
	 * @param sql SQL statement
	 * @return ResultSetBO structured result
	 * @throws SQLException SQL execution exception
	 */
	public static ResultSetBO executeSqlAndReturnObject(Connection connection, String schema, String sql)
			throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.setMaxRows(RESULT_SET_LIMIT);
			statement.setQueryTimeout(STATEMENT_TIMEOUT);
			setCurrentSchema(statement, schema);
			try (ResultSet rs = statement.executeQuery(sql)) {
				return ResultSetBuilder.buildFrom(rs, schema);
			}
		}
	}

	public static ResultSetBO executeSql(Connection connection, String schema, String sql) {
        ResultSetBO resultSet;
		try {
			resultSet = SqlExecutor.executeSqlAndReturnObject(connection, schema, sql);
		} catch (SQLException e) {
			throw new RuntimeSqlException(e);
		}
		return resultSet;
	}

	static void setCurrentSchema(Statement statement, String schema) throws SQLException {
		if (StringUtils.isEmpty(schema)) {
			return;
		}
		Connection connection = statement.getConnection();
		DatabaseMetaData metaData = connection.getMetaData();
		DatabaseDialectEnum dialectEnum = resolveDialect(metaData);
		if (dialectEnum != null) {
			String changeSchemaSql = dialectEnum.getChangeSchemaSql(schema);
			statement.execute(changeSchemaSql);
		}
	}

	@Nullable
	private static DatabaseDialectEnum resolveDialect(DatabaseMetaData databaseMetaData) throws SQLException {
		String dialect = databaseMetaData.getDatabaseProductName();
		if (isDameng(dialect)) {
			return DatabaseDialectEnum.DAMENG;
		} else if (dialect.equals(DatabaseDialectEnum.MYSQL.code)) {
			return DatabaseDialectEnum.MYSQL;
		} else if (dialect.equals(DatabaseDialectEnum.POSTGRESQL.code)) {
			return DatabaseDialectEnum.POSTGRESQL;
		} else if (dialect.equals(DatabaseDialectEnum.H2.code)) {
			return DatabaseDialectEnum.H2;
		} else if (dialect.equals(DatabaseDialectEnum.ORACLE.code)) {
			return DatabaseDialectEnum.ORACLE;
		}
		return null;
	}

	public static <T> List<T> executeSqlAndExtract(Connection connection, String sql,
												   BiFunction<String[], Integer, T> rowMapper) {
		return mapResult(() -> SqlExecutor.executeSqlAndReturnArr(connection, sql), rowMapper);
	}

	public static <T> List<T> executeSqlAndExtract(Connection connection, String schema, String sql,
												   BiFunction<String[], Integer, T> rowMapper) {
		return mapResult(() -> SqlExecutor.executeSqlAndReturnArr(connection, schema, sql), rowMapper);
	}

	private static <T> List<T> mapResult(ThrowingSupplier<String[][]> resultSupplier,
										 BiFunction<String[], Integer, T> rowMapper) {
		String[][] resultArr;
		try {
			resultArr = resultSupplier.get();
		} catch (Exception e) {
            throw new RuntimeSqlException(e);
        }
		return mapRows(resultArr, rowMapper);
	}

	private static <T> List<T> mapRows(String[][] resultArr, BiFunction<String[], Integer, T> rowMapper) {
		List<T> items = Lists.newArrayList();
		if (resultArr.length <= 1) {
			return Lists.newArrayList();
		}
		// start from 1
		for (int i = 1; i < resultArr.length; i++) {
			if (resultArr[i].length == 0) {
				// column count = 0
				continue;
			}
			T item = rowMapper.apply(resultArr[i], i);
			if (item == null) {
				continue;
			}
			items.add(item);
		}
		return items;
	}

	/**
	 * Execute SQL query and return string two-dimensional array format result
	 * @param connection database connection
	 * @param sql SQL statement
	 * @return two-dimensional array result
	 * @throws SQLException SQL execution exception
	 */
	public static String[][] executeSqlAndReturnArr(Connection connection, String sql) throws SQLException {
		List<String[]> list = executeQuery(connection, sql);
		return list.toArray(new String[0][]);
	}

	public static String[][] executeSqlAndReturnArr(Connection connection, String databaseOrSchema, String sql)
			throws SQLException {
		List<String[]> list = executeQuery(connection, databaseOrSchema, sql);
		return list.toArray(new String[0][]);
	}

	private static List<String[]> executeQuery(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
			return JdbcUtils.extractTableRows(rs);
		}
	}

	private static List<String[]> executeQuery(Connection connection, String databaseOrSchema, String sql)
			throws SQLException {
		if (StringUtils.isEmpty(databaseOrSchema)) {
			return executeQuery(connection, sql);
		}
		String originalDb = connection.getCatalog();
		DatabaseMetaData metaData = connection.getMetaData();
		String dialect = metaData.getDatabaseProductName();

		try (Statement statement = connection.createStatement()) {
			setCurrentSchema(statement, databaseOrSchema);
			ResultSet rs = statement.executeQuery(sql);
			List<String[]> result = JdbcUtils.extractTableRows(rs);
			if (dialect.equals(DatabaseDialectEnum.MYSQL.code)) {
				setCurrentSchema(statement, originalDb);
			}
			return result;
		}
	}

	private static boolean isDameng(String databaseProductName) {
		return "DM DBMS".equalsIgnoreCase(databaseProductName)
				|| DatabaseDialectEnum.DAMENG.code.equalsIgnoreCase(databaseProductName);
	}

}
