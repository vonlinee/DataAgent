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
class H2JdbcDdlTest {

	private H2JdbcDdl h2JdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		h2JdbcDdl = new H2JdbcDdl();
	}

	@Test
	void getDataSourceType_returnsH2() {
		assertEquals(BizDataSourceTypeEnum.H2, h2JdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_withResults_returnsDatabaseList() {
		String[][] resultArr = { { "CATALOG_NAME" }, { "TESTDB" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<DatabaseInfoBO> databases = h2JdbcDdl.showDatabases(connection);
			assertEquals(1, databases.size());
			assertEquals("TESTDB", databases.getFirst().getName());
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "CATALOG_NAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			assertTrue(h2JdbcDdl.showDatabases(connection).isEmpty());
		}
	}

	@Test
	void showSchemas_returnsSchemaList() {
		String[][] resultArr = { { "schema_name" }, { "PUBLIC" }, { "INFORMATION_SCHEMA" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<SchemaInfoBO> schemas = h2JdbcDdl.showSchemas(connection);
			assertEquals(2, schemas.size());
			assertEquals("PUBLIC", schemas.getFirst().getName());
		}
	}

	@Test
	void showSchemas_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "schema_name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			assertTrue(h2JdbcDdl.showSchemas(connection).isEmpty());
		}
	}

	@Test
	void showTables_withPattern_returnsTableList() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, { "USERS", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.showTables(connection, "PUBLIC", "USER");
			assertEquals(1, tables.size());
			assertEquals("USERS", tables.getFirst().getName());
			assertEquals("PUBLIC", tables.getFirst().getSchema());
		}
	}

	@Test
	void showTables_withoutPattern_returnsTableList() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, { "ORDERS", "" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.showTables(connection, "PUBLIC", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_returnsTableList() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, { "USERS", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.fetchTables(connection, "PUBLIC", List.of("USERS"));
			assertEquals(1, tables.size());
			assertEquals("PUBLIC", tables.getFirst().getSchema());
		}
	}

	@Test
	void showColumns_returnsColumnList() {
		String[][] resultArr = { { "column_name", "remarks", "data_type", "primary", "notnull" },
				{ "ID", "Primary key", "BIGINT", "true", "true" }, { "NAME", "Name", "VARCHAR", "false", "false" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<ColumnInfoBO> columns = h2JdbcDdl.showColumns(connection, "PUBLIC", "USERS");
			assertEquals(2, columns.size());
			assertEquals("ID", columns.getFirst().getName());
			assertTrue(columns.getFirst().isPrimary());
		}
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() {
		String[][] resultArr = { { "table", "column", "ref_table", "ref_column" },
				{ "ORDERS", "USER_ID", "USERS", "ID" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<ForeignKeyInfoBO> fks = h2JdbcDdl.showForeignKeys(connection, "PUBLIC",
					Arrays.asList("ORDERS", "USERS"));
			assertEquals(1, fks.size());
			assertEquals("ORDERS", fks.getFirst().getTable());
			assertEquals("USER_ID", fks.getFirst().getColumn());
		}
	}

	@Test
	void sampleColumn_returnsDedupedSamples() {
		String[][] resultArr = { { "NAME" }, { "Alice" }, { "Bob" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<String> samples = h2JdbcDdl.sampleColumn(connection, "PUBLIC", "USERS", "NAME");
			assertTrue(samples.contains("Alice"));
			assertTrue(samples.contains("Bob"));
		}
	}

	@Test
	void sampleColumn_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(ms, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error",
					() -> h2JdbcDdl.sampleColumn(connection, "PUBLIC", "USERS", "NAME"));
		}
	}

	@Test
	void scanTable_returnsResultSet() {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSql(any(Connection.class), nullable(String.class), anyString()))
				.thenReturn(expected);

			ResultSetBO result = h2JdbcDdl.scanTable(connection, "PUBLIC", "USERS");
			assertSame(expected, result);
			ms.verify(() -> SqlExecutor.executeSql(connection, "PUBLIC", "SELECT * FROM `USERS` LIMIT 20;"));
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSql(any(Connection.class), anyString(), anyString())).thenThrow(new com.alibaba.cloud.ai.dataagent.exception.RuntimeSqlException(new SQLException("error")));

			assertRuntimeCause(SQLException.class, "error", () -> h2JdbcDdl.scanTable(connection, "PUBLIC", "USERS"));
		}
	}

	@Test
	void showDatabases_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(ms, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error", () -> h2JdbcDdl.showDatabases(connection));
		}
	}

	@Test
	void showDatabases_emptyRowInResults_skipsRow() {
		String[][] resultArr = { { "CATALOG_NAME" }, {}, { "TESTDB" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<DatabaseInfoBO> databases = h2JdbcDdl.showDatabases(connection);
			assertEquals(1, databases.size());
		}
	}

	@Test
	void showSchemas_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(ms, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error", () -> h2JdbcDdl.showSchemas(connection));
		}
	}

	@Test
	void showSchemas_emptyRowInResults_skipsRow() {
		String[][] resultArr = { { "schema_name" }, {}, { "PUBLIC" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<SchemaInfoBO> schemas = h2JdbcDdl.showSchemas(connection);
			assertEquals(1, schemas.size());
		}
	}

	@Test
	void showTables_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.showTables(connection, "PUBLIC", null);
			assertTrue(tables.isEmpty());
		}
	}

	@Test
	void showTables_emptyRowInResults_skipsRow() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, {}, { "USERS", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.showTables(connection, "PUBLIC", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(ms, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error", () -> h2JdbcDdl.showTables(connection, "PUBLIC", null));
		}
	}

	@Test
	void fetchTables_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.fetchTables(connection, "PUBLIC", List.of("USERS"));
			assertTrue(tables.isEmpty());
		}
	}

	@Test
	void fetchTables_emptyRowInResults_skipsRow() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, {}, { "USERS", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.fetchTables(connection, "PUBLIC", List.of("USERS"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(ms, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error",
					() -> h2JdbcDdl.fetchTables(connection, "PUBLIC", List.of("USERS")));
		}
	}

	@Test
	void showColumns_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "column_name", "remarks", "data_type", "primary", "notnull" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<ColumnInfoBO> columns = h2JdbcDdl.showColumns(connection, "PUBLIC", "USERS");
			assertTrue(columns.isEmpty());
		}
	}

	@Test
	void showColumns_emptyRowInResults_skipsRow() {
		String[][] resultArr = { { "column_name", "remarks", "data_type", "primary", "notnull" }, {},
				{ "ID", "Primary key", "BIGINT", "true", "true" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<ColumnInfoBO> columns = h2JdbcDdl.showColumns(connection, "PUBLIC", "USERS");
			assertEquals(1, columns.size());
		}
	}

	@Test
	void showColumns_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(ms, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error", () -> h2JdbcDdl.showColumns(connection, "PUBLIC", "USERS"));
		}
	}

	@Test
	void showForeignKeys_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "table", "column", "ref_table", "ref_column" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<ForeignKeyInfoBO> fks = h2JdbcDdl.showForeignKeys(connection, "PUBLIC",
					Arrays.asList("ORDERS", "USERS"));
			assertTrue(fks.isEmpty());
		}
	}

	@Test
	void showForeignKeys_emptyRowInResults_skipsRow() {
		String[][] resultArr = { { "table", "column", "ref_table", "ref_column" }, {},
				{ "ORDERS", "USER_ID", "USERS", "ID" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<ForeignKeyInfoBO> fks = h2JdbcDdl.showForeignKeys(connection, "PUBLIC",
					Arrays.asList("ORDERS", "USERS"));
			assertEquals(1, fks.size());
		}
	}

	@Test
	void showForeignKeys_sqlException_throwsRuntimeException() {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubSqlException(ms, new SQLException("error"));

			assertRuntimeCause(SQLException.class, "error",
					() -> h2JdbcDdl.showForeignKeys(connection, "PUBLIC", List.of("ORDERS")));
		}
	}

	@Test
	void sampleColumn_emptyResult_returnsEmptyList() {
		String[][] resultArr = { { "NAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<String> samples = h2JdbcDdl.sampleColumn(connection, "PUBLIC", "USERS", "NAME");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void sampleColumn_valueMatchesColumnName_skipsRow() {
		String[][] resultArr = { { "NAME" }, { "NAME" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<String> samples = h2JdbcDdl.sampleColumn(connection, "PUBLIC", "USERS", "NAME");
			assertEquals(1, samples.size());
			assertTrue(samples.contains("Alice"));
		}
	}

	@Test
	void sampleColumn_emptyRowInResults_skipsRow() {
		String[][] resultArr = { { "NAME" }, {}, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<String> samples = h2JdbcDdl.sampleColumn(connection, "PUBLIC", "USERS", "NAME");
			assertEquals(1, samples.size());
		}
	}

	@Test
	void showTables_withBlankPattern_usesBaseSqlOnly() {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, { "USERS", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			SqlExecutorTestSupport.stubRows(ms, resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.showTables(connection, "PUBLIC", "");
			assertEquals(1, tables.size());
		}
	}

}
