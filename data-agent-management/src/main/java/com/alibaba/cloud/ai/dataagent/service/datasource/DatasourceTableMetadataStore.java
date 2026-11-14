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
package com.alibaba.cloud.ai.dataagent.service.datasource;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.DatasourceTableColumnQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Persists the database metadata captured during Schema initialization.
 *
 * <p>The metadata is scoped to a datasource rather than an agent because several
 * agents can share the same database connection and selected tables.</p>
 */
public interface DatasourceTableMetadataStore {

	/** Save the latest table and column snapshot for a datasource. */
	void save(Integer datasourceId, List<TableInfoBO> tables);

	/** Remove all metadata captured by earlier initializations for one datasource. */
	void deleteByDatasource(Integer datasourceId);

	/**
	 * 查询表信息(不包含表的列信息)
	 */
	List<TableInfoBO> findTables(Integer datasourceId);

	/**
	 * 查询表信息(不包含表的列信息)
	 */
	Map<String, TableInfoBO> findTablesAsMap(Integer datasourceId);

	/**
	 * 查询单个数据源的所有表和列信息
	 *
	 * @param dataSourceId 数据源ID
	 * @return 表和列信息
	 */
	default List<TableInfoBO> findTableAndColumns(Integer dataSourceId) {
		return dataSourceId == null ? List.of() : findTableAndColumns(List.of(dataSourceId));
	}

	/**
	 * 查询多个数据源的所有表和列信息
	 *
	 * @param dataSourceIds 数据源ID列表
	 * @return 表和列信息
	 */
	List<TableInfoBO> findTableAndColumns(Collection<Integer> dataSourceIds);

	/** Backward-compatible lookup for callers that do not have a schema name. */
	default List<ColumnInfoBO> findColumns(Integer datasourceId, String tableName) {
		return findColumns(DatasourceTableColumnQuery.builder()
			.datasourceId(datasourceId)
			.tableName(tableName)
			.build());
	}

	/**
	 * Query initialized column metadata with optional datasource, schema, table, and column filters.
	 *
	 * @param query optional column metadata filters
	 * @return matching initialized columns
	 */
	List<ColumnInfoBO> findColumns(DatasourceTableColumnQuery query);

	/** Backward-compatible lookup for callers that also provide a schema name. */
	default List<ColumnInfoBO> findColumns(Integer datasourceId, String tableName, String schemaName) {
		return findColumns(DatasourceTableColumnQuery.builder()
			.datasourceId(datasourceId)
			.tableName(tableName)
			.schemaName(schemaName)
			.build());
	}

}
