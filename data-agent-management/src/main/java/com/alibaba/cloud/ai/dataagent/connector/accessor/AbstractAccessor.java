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
package com.alibaba.cloud.ai.dataagent.connector.accessor;

import com.alibaba.cloud.ai.dataagent.connector.ddl.Ddl;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.ddl.DdlFactory;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.List;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */
@Slf4j
@AllArgsConstructor
public abstract class AbstractAccessor implements Accessor {

	private final DdlFactory ddlFactory;

	private final DBConnectionPool dbConnectionPool;

    @SuppressWarnings("unchecked")
	public <T> T accessDb(DbConfigBO dbConfig, String method, DbQueryParameter param) throws Exception {
		try (Connection connection = getConnection(dbConfig)) {
			Ddl ddlExecutor = ddlFactory.getDdlExecutorByDbConfig(dbConfig);
            return switch (method) {
                case "showDatabases" -> (T) ddlExecutor.showDatabases(connection);
                case "showSchemas" -> (T) ddlExecutor.showSchemas(connection);
                case "showTables" -> (T) ddlExecutor.showTables(connection, param.getSchema(), param.getTablePattern());
                case "fetchTables" -> (T) ddlExecutor.fetchTables(connection, param.getSchema(), param.getTables());
                case "showColumns" -> (T) ddlExecutor.showColumns(connection, param.getSchema(), param.getTable());
                case "showForeignKeys" ->
                        (T) ddlExecutor.showForeignKeys(connection, param.getSchema(), param.getTables());
                case "sampleColumn" -> (T) ddlExecutor.sampleColumn(connection, param.getSchema(), param.getTable(),
                        param.getColumn());
                case "scanTable" -> (T) ddlExecutor.scanTable(connection, param.getSchema(), param.getTable());
                case "executeSqlAndReturnObject" ->
                        (T) SqlExecutor.executeSqlAndReturnObject(connection, param.getSchema(), param.getSql());
                default -> throw new UnsupportedOperationException("Unknown method: " + method);
            };
		} catch (Exception e) {
			log.error("Error accessing database with method: {}, reason: {}", method, e.getMessage());
			throw e;
		}
	}

	public List<DatabaseInfoBO> showDatabases(DbConfigBO dbConfig) throws Exception {
		return accessDb(dbConfig, "showDatabases", null);
	}

	public List<SchemaInfoBO> showSchemas(DbConfigBO dbConfig) throws Exception {
		return accessDb(dbConfig, "showSchemas", null);
	}

	public List<TableInfoBO> showTables(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "showTables", param);
	}

	public List<TableInfoBO> fetchTables(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "fetchTables", param);
	}

	public List<ColumnInfoBO> showColumns(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "showColumns", param);
	}

	public List<ForeignKeyInfoBO> showForeignKeys(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "showForeignKeys", param);
	}

	public List<String> sampleColumn(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "sampleColumn", param);
	}

	public ResultSetBO scanTable(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "scanTable", param);
	}

	public ResultSetBO executeSqlAndReturnObject(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "executeSqlAndReturnObject", param);
	}

	public Connection getConnection(DbConfigBO config) {
		return this.dbConnectionPool.getConnection(config);
	}

}
