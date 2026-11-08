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
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceTableColumnMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceTableMetadataStore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** JDBC-backed implementation of the datasource metadata snapshot store. */
@Service
@RequiredArgsConstructor
public class DatasourceTableMetadataStoreImpl implements DatasourceTableMetadataStore {

	private final DatasourceTableColumnMapper columnMapper;

	private final AgentDatasourceTablesMapper tablesMapper;

	/** Replace the complete datasource snapshot with the latest initialization result. */
	@Override
	@Transactional
	public void save(Integer datasourceId, List<TableInfoBO> tables) {
		if (datasourceId == null || tables == null) {
			return;
		}
		deleteByDatasource(datasourceId);
		for (TableInfoBO table : tables) {
			if (table == null || StringUtils.isBlank(table.getName())) {
				continue;
			}
			// Keep the unique key deterministic for drivers that do not expose a schema.
			if (table.getSchema() == null) {
				table.setSchema("");
			}
			List<ColumnInfoBO> columns = table.getColumns() == null ? List.of() : table.getColumns();
			for (int index = 0; index < columns.size(); index++) {
				columnMapper.insert(datasourceId, table, columns.get(index), index + 1);
			}
			// Keep the existing selection table useful to the table browser and avoid a second metadata model.
			tablesMapper.updateTableMetadataByDatasourceId(datasourceId, table.getName(), table.getSchema(),
				table.getDescription(), table.getType());
		}
	}

	/** Remove the previous complete snapshot before writing the new initialization result. */
	@Override
	public void deleteByDatasource(Integer datasourceId) {
		if (datasourceId != null) {
			columnMapper.deleteByDatasource(datasourceId);
		}
	}

	/** Return cached table metadata keyed by normalized schema and table name. */
	@Override
	public Map<String, TableInfoBO> findTables(Integer datasourceId) {
		if (datasourceId == null) {
			return Collections.emptyMap();
		}
		return tablesMapper.getTableMetadataByDatasourceId(datasourceId)
			.stream()
			.filter(table -> StringUtils.isNotBlank(table.getName()))
			.collect(Collectors.toMap(table -> tableKey(table.getSchema(), table.getName()), Function.identity(),
				(existing, ignored) -> existing, LinkedHashMap::new));
	}

	/** Read initialized columns using both schema and table name to avoid cross-schema collisions. */
	@Override
	public List<ColumnInfoBO> findColumns(Integer datasourceId, String tableName, String schemaName) {
		if (datasourceId == null || StringUtils.isBlank(tableName)) {
			return List.of();
		}
		return columnMapper.findByDatasourceAndTable(datasourceId, tableName, schemaName);
	}

	private String tableKey(String schemaName, String tableName) {
		return (schemaName == null ? "" : schemaName.trim().toLowerCase(java.util.Locale.ROOT)) + "."
				+ (tableName == null ? "" : tableName.trim().toLowerCase(java.util.Locale.ROOT));
	}

}
