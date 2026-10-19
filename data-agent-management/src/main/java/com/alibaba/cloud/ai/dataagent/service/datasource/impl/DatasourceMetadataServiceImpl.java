/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceMetadataService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry.DatasourceTypeHandlerRegistry;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import java.util.List;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** Adapter that exposes metadata without widening the legacy datasource contract. */
@Service
@AllArgsConstructor
public class DatasourceMetadataServiceImpl implements DatasourceMetadataService {

	private final DatasourceService datasourceService;

	private final AccessorFactory accessorFactory;

	private final DatasourceTypeHandlerRegistry datasourceTypeHandlerRegistry;

	private final DBConnectionPoolFactory dbConnectionPoolFactory;

	@Override
	public List<TableInfoBO> getDatasourceTableDetails(Integer datasourceId) throws Exception {
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}
		DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String schemaName = handler.extractSchemaName(datasource);
		queryParam.setSchema(schemaName);
		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<TableInfoBO> tables = dbAccessor.showTables(dbConfig, queryParam);
		List<TableInfoBO> metadataTables = readTableMetadata(datasource, dbConfig, schemaName);
		Map<String, TableInfoBO> metadataByKey = indexByTableKey(metadataTables);
		Map<String, TableInfoBO> tablesByKey = new HashMap<>();
		for (TableInfoBO table : tables) {
			if (table.getName() == null || table.getName().trim().isEmpty()) {
				continue;
			}
			if (StringUtils.isBlank(table.getSchema())) {
				table.setSchema(schemaName);
			}
			if (StringUtils.isBlank(table.getType())) {
				TableInfoBO metadata = metadataByKey.getOrDefault(tableKey(table.getSchema(), table.getName()),
					metadataByKey.get(tableKey("", table.getName())));
				if (metadata != null) {
					table.setType(metadata.getType());
				}
			}
			tablesByKey.put(tableKey(table.getSchema(), table.getName()), table);
		}
		for (TableInfoBO metadata : metadataTables) {
			String schema = StringUtils.defaultIfBlank(metadata.getSchema(), schemaName);
			String key = tableKey(schema, metadata.getName());
			if (!tablesByKey.containsKey(key) && !tablesByKey.containsKey(tableKey("", metadata.getName()))) {
				metadata.setSchema(schema);
				tablesByKey.put(key, metadata);
			}
		}
		return tablesByKey.values().stream().sorted(java.util.Comparator.comparing(TableInfoBO::getName)).toList();
	}

	private List<TableInfoBO> readTableMetadata(Datasource datasource, DbConfigBO dbConfig, String schemaName) {
		List<TableInfoBO> metadataTables = new ArrayList<>();
		try {
			DBConnectionPool connectionPool = dbConnectionPoolFactory.getPoolByDbType(datasource.getType());
			try (Connection connection = connectionPool.getConnection(dbConfig)) {
				String catalog = connection.getCatalog();
				readTableMetadata(connection, catalog, schemaName, metadataTables);
				if (metadataTables.isEmpty()) {
					readTableMetadata(connection, catalog, null, metadataTables);
				}
				if (metadataTables.isEmpty()) {
					readTableMetadata(connection, null, schemaName, metadataTables);
				}
			}
		}
		catch (Exception ignored) {
			// The connector result remains usable when a driver does not support JDBC metadata.
		}
		return metadataTables;
	}

	private void readTableMetadata(Connection connection, String catalog, String schemaName,
			List<TableInfoBO> metadataTables) throws Exception {
		String[] types = { "TABLE", "VIEW", "MATERIALIZED VIEW", "FOREIGN TABLE", "SYSTEM TABLE" };
		try (ResultSet resultSet = connection.getMetaData().getTables(catalog, schemaName, null, types)) {
			while (resultSet.next()) {
				String tableName = resultSet.getString("TABLE_NAME");
				if (StringUtils.isBlank(tableName)) {
					continue;
				}
				String schema = resultSet.getString("TABLE_SCHEM");
				String type = resultSet.getString("TABLE_TYPE");
				metadataTables.add(TableInfoBO.builder()
					.schema(schema)
					.name(tableName)
					.description(resultSet.getString("REMARKS"))
					.type(normalizeTableType(type))
					.build());
			}
		}
	}

	private Map<String, TableInfoBO> indexByTableKey(List<TableInfoBO> tables) {
		Map<String, TableInfoBO> indexed = new HashMap<>();
		for (TableInfoBO table : tables) {
			indexed.put(tableKey(table.getSchema(), table.getName()), table);
			indexed.putIfAbsent(tableKey("", table.getName()), table);
		}
		return indexed;
	}

	private String normalizeTableType(String type) {
		if (StringUtils.isBlank(type)) {
			return null;
		}
		return type.toUpperCase(Locale.ROOT).contains("VIEW") ? "VIEW" : "TABLE";
	}

	private String tableKey(String schema, String tableName) {
		return String.format("%s.%s", schema == null ? "" : schema.toLowerCase(Locale.ROOT),
			tableName == null ? "" : tableName.toLowerCase(Locale.ROOT));
	}

	@Override
	public List<ColumnInfoBO> getTableColumnDetails(Integer datasourceId, String tableName) throws Exception {
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}
		DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		queryParam.setSchema(handler.extractSchemaName(datasource));
		queryParam.setTable(tableName);
		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		return dbAccessor.showColumns(dbConfig, queryParam).stream()
			.filter(column -> column.getName() != null && !column.getName().trim().isEmpty())
			.sorted(java.util.Comparator.comparing(ColumnInfoBO::getName))
			.toList();
	}

}
