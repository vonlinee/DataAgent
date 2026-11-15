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
package com.alibaba.cloud.ai.dataagent.connector.ddl;

import com.alibaba.cloud.ai.dataagent.bo.schema.*;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;

import java.sql.Connection;
import java.util.List;

public interface Ddl {

	BizDataSourceTypeEnum getDataSourceType();

	List<DatabaseInfoBO> showDatabases(Connection connection);

	List<SchemaInfoBO> showSchemas(Connection connection);

	List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern);

	List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables);

	List<ColumnInfoBO> showColumns(Connection connection, String schema, String table);

	List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables);

	List<String> sampleColumn(Connection connection, String schema, String table, String column);

	ResultSetBO scanTable(Connection connection, String schema, String table);

	default boolean supportedDataSourceType(String type) {
		return getDataSourceType().getTypeName().equals(type);
	}

	default boolean supportedDataSourceType(BizDataSourceTypeEnum type) {
		return getDataSourceType().equals(type);
	}

	default String getDdlType() {
		return getDataSourceType().getProtocol() + "@" + getDataSourceType().getDialect();
	}

}
