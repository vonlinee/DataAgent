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
package com.alibaba.cloud.ai.dataagent.dto.datasource;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Optional filters for querying initialized datasource column metadata. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceTableColumnQuery {

	/** Exact match for one datasource ID. */
	private Integer datasourceId;

	/** Exact match for any datasource ID in this list. */
	private List<Integer> datasourceIds;

	/** Exact match for one schema name. */
	private String schemaName;

	/** Exact match for any schema name in this list. */
	private List<String> schemaNames;

	/** Exact match for one table name. */
	private String tableName;

	/** Exact match for any table name in this list. */
	private List<String> tableNames;

	/** Fuzzy match for one table name fragment. */
	private String tableNameLike;

	/** Fuzzy match for any table name fragment in this list. */
	private List<String> tableNameLikes;

	/** Exact match for any qualified table name fragment in this list. like 'sakila.fiml' */
	private List<String> qualifiedTableNames;

	/** Exact match for one column name. */
	private String columnName;

	/** Exact match for any column name in this list. */
	private List<String> columnNames;

	/** Fuzzy match for one column name fragment. */
	private String columnNameLike;

	/** Fuzzy match for any column name fragment in this list. */
	private List<String> columnNameLikes;

}
