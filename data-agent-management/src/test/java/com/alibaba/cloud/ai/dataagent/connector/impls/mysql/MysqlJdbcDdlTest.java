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

import static com.alibaba.cloud.ai.dataagent.support.ExceptionTestSupport.assertRuntimeCause;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutorTestSupport;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MysqlJdbcDdlTest {

	private MysqlJdbcDdl mysqlJdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		mysqlJdbcDdl = new MysqlJdbcDdl();
	}

	@Test
	void getDataSourceType_returnsMysql() {
		assertEquals(BizDataSourceTypeEnum.MYSQL, mysqlJdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_withResults_returnsDatabaseList() {
		String[][] resultArr = { { "Database" }, { "testdb" }, { "production" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<DatabaseInfoBO> databases = mysqlJdbcDdl.showDatabases(connection);

			assertEquals(2, databases.size());
			assertEquals("testdb", databases.get(0).getName());
			assertEquals("production", databases.get(1).getName());
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "Database" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<DatabaseInfoBO> databases = mysqlJdbcDdl.showDatabases(connection);

			assertTrue(databases.isEmpty());
		}
	}

	@Test
	void showDatabases_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(mockedStatic, new SQLException("connection failed"));

			assertRuntimeCause(SQLException.class, "connection failed", () -> mysqlJdbcDdl.showDatabases(connection));
		}
	}

	@Test
	void showSchemas_returnsEmptyList() {
		List<SchemaInfoBO> schemas = mysqlJdbcDdl.showSchemas(connection);
		assertTrue(schemas.isEmpty());
	}

	@Test
	void showTables_withPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "users", "User table" },
				{ "orders", "Order table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", "user");

			assertEquals(2, tables.size());
			assertEquals("users", tables.getFirst().getName());
			assertEquals("User table", tables.getFirst().getDescription());
		}
	}

	@Test
	void showTables_withoutPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "users", "User table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", null);

			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", null);
			assertTrue(tables.isEmpty());
		}
	}

	@Test
	void fetchTables_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "users", "User table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.fetchTables(connection, "testdb", Arrays.asList("users", "orders"));

			assertEquals(1, tables.size());
			assertEquals("users", tables.getFirst().getName());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "column_name", "column_comment", "data_type", "primary", "notnull" },
				{ "id", "Primary key", "bigint", "true", "true" },
				{ "name", "User name", "varchar", "false", "false" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<ColumnInfoBO> columns = mysqlJdbcDdl.showColumns(connection, "testdb", "users");

			assertEquals(2, columns.size());
			assertEquals("id", columns.getFirst().getName());
			assertEquals("Primary key", columns.getFirst().getDescription());
			assertTrue(columns.get(0).isPrimary());
			assertTrue(columns.get(0).isNotnull());
			assertEquals("name", columns.get(1).getName());
			assertFalse(columns.get(1).isPrimary());
		}
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() throws SQLException {
		String[][] resultArr = { { "table", "column", "constraint", "ref_table", "ref_column" },
				{ "orders", "user_id", "fk_user", "users", "id" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<ForeignKeyInfoBO> fks = mysqlJdbcDdl.showForeignKeys(connection, "testdb",
					Arrays.asList("orders", "users"));

			assertEquals(1, fks.size());
			assertEquals("orders", fks.getFirst().getTable());
			assertEquals("user_id", fks.getFirst().getColumn());
			assertEquals("users", fks.getFirst().getReferencedTable());
			assertEquals("id", fks.getFirst().getReferencedColumn());
		}
	}

	@Test
	void sampleColumn_returnsSamples() {
		String[][] resultArr = { { "name" }, { "Alice" }, { "Bob" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<String> samples = mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name");

			assertFalse(samples.isEmpty());
			assertTrue(samples.contains("Alice"));
			assertTrue(samples.contains("Bob"));
		}
	}

	@Test
	void sampleColumn_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "name" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<String> samples = mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name");

			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void sampleColumn_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(mockedStatic, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error",
					() -> mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name"));
		}
	}

	@Test
	void scanTable_returnsResultSet() {
		ResultSetBO expectedResult = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSql(any(Connection.class), nullable(String.class), anyString()))
				.thenReturn(expectedResult);

			ResultSetBO result = mysqlJdbcDdl.scanTable(connection, "testdb", "users");

			assertSame(expectedResult, result);
			mockedStatic.verify(() -> SqlExecutor.executeSql(connection, "testdb", "SELECT * FROM `users` LIMIT 20;"));
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSql(any(Connection.class), anyString(), anyString())).thenThrow(new com.alibaba.cloud.ai.dataagent.exception.RuntimeSqlException(new SQLException("error")));

			assertRuntimeCause(SQLException.class, "error",
					() -> mysqlJdbcDdl.scanTable(connection, "testdb", "users"));
		}
	}

	@Test
	void showDatabases_rowWithEmptyLength_skipsRow() {
		String[][] resultArr = { { "Database" }, {}, { "testdb" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<DatabaseInfoBO> databases = mysqlJdbcDdl.showDatabases(connection);

			assertEquals(1, databases.size());
			assertEquals("testdb", databases.getFirst().getName());
		}
	}

	@Test
	void showTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, {}, { "users", "User table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", null);

			assertEquals(1, tables.size());
			assertEquals("users", tables.getFirst().getName());
		}
	}

	@Test
	void showTables_sqlException_throwsRuntimeException() throws SQLException {
		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(mockedStatic, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error", () -> mysqlJdbcDdl.showTables(connection, "testdb", null));
		}
	}

	@Test
	void fetchTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.fetchTables(connection, "testdb", List.of("users"));

			assertTrue(tables.isEmpty());
		}
	}

	@Test
	void fetchTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, {}, { "users", "User table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.fetchTables(connection, "testdb", List.of("users"));

			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_sqlException_throwsRuntimeException() throws SQLException {
		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(mockedStatic, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error",
					() -> mysqlJdbcDdl.fetchTables(connection, "testdb", List.of("users")));
		}
	}

	@Test
	void showColumns_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "column_name", "column_comment", "data_type", "primary", "notnull" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<ColumnInfoBO> columns = mysqlJdbcDdl.showColumns(connection, "testdb", "users");

			assertTrue(columns.isEmpty());
		}
	}

	@Test
	void showColumns_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "column_name", "column_comment", "data_type", "primary", "notnull" }, {},
				{ "id", "Primary key", "bigint", "true", "true" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<ColumnInfoBO> columns = mysqlJdbcDdl.showColumns(connection, "testdb", "users");

			assertEquals(1, columns.size());
		}
	}

	@Test
	void showColumns_sqlException_throwsRuntimeException() throws SQLException {
		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(mockedStatic, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error", () -> mysqlJdbcDdl.showColumns(connection, "testdb", "users"));
		}
	}

	@Test
	void showForeignKeys_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "table", "column", "constraint", "ref_table", "ref_column" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<ForeignKeyInfoBO> fks = mysqlJdbcDdl.showForeignKeys(connection, "testdb",
					Arrays.asList("orders", "users"));

			assertTrue(fks.isEmpty());
		}
	}

	@Test
	void showForeignKeys_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "table", "column", "constraint", "ref_table", "ref_column" }, {},
				{ "orders", "user_id", "fk_user", "users", "id" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<ForeignKeyInfoBO> fks = mysqlJdbcDdl.showForeignKeys(connection, "testdb",
					Arrays.asList("orders", "users"));

			assertEquals(1, fks.size());
		}
	}

	@Test
	void showForeignKeys_sqlException_throwsRuntimeException() throws SQLException {
		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(mockedStatic, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error",
					() -> mysqlJdbcDdl.showForeignKeys(connection, "testdb", List.of("orders")));
		}
	}

	@Test
	void sampleColumn_rowWithEmptyLength_skipsRow() {
		String[][] resultArr = { { "name" }, {}, { "Alice" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<String> samples = mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name");

			assertEquals(1, samples.size());
			assertTrue(samples.contains("Alice"));
		}
	}

	@Test
	void sampleColumn_valueMatchesColumnName_skipsRow() {
		String[][] resultArr = { { "name" }, { "name" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<String> samples = mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name");

			assertEquals(1, samples.size());
			assertTrue(samples.contains("Alice"));
		}
	}

	@Test
	void showTables_withBlankPattern_usesBaseSqlOnly() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "users", "User table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(mockedStatic, resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", "");

			assertEquals(1, tables.size());
		}
	}

	@Test
	void getSelectSql_delegatesToSqlUtil() {
		String result = mysqlJdbcDdl.getSelectSql("mysql", "users", "id,name", 10);
		assertEquals("SELECT id,name FROM users LIMIT 10", result);
	}

}
