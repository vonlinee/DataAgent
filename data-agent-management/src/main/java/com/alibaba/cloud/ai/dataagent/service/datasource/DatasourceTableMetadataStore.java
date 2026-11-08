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

	/** Return cached table metadata indexed by normalized schema and table name. */
	Map<String, TableInfoBO> findTables(Integer datasourceId);

	/** Backward-compatible lookup for callers that do not have a schema name. */
	default List<ColumnInfoBO> findColumns(Integer datasourceId, String tableName) {
		return findColumns(datasourceId, tableName, null);
	}

	List<ColumnInfoBO> findColumns(Integer datasourceId, String tableName, String schemaName);

}
