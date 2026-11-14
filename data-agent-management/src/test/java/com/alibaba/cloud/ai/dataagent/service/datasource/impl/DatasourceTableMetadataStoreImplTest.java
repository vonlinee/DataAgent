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
import com.alibaba.cloud.ai.dataagent.dto.datasource.DatasourceTableColumnQuery;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceTableColumnMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasourceTableMetadataStoreImplTest {

	@Mock
	private DatasourceTableColumnMapper columnMapper;

	@Mock
	private AgentDatasourceTablesMapper tablesMapper;

	@Test
	void saveReplacesColumnsAndUpdatesSelectedTableMetadata() {
		DatasourceTableMetadataStoreImpl store = new DatasourceTableMetadataStoreImpl(columnMapper, tablesMapper);
		ColumnInfoBO column = ColumnInfoBO.builder().name("id").type("int").primary(true).notnull(true).build();
		TableInfoBO table = TableInfoBO.builder()
			.schema("test")
			.name("users")
			.type("TABLE")
			.description("用户表")
			.columns(List.of(column))
			.build();

		store.save(7, List.of(table));

		verify(columnMapper).deleteByDatasource(7);
		verify(columnMapper).insert(7, table, column, 1);
		verify(tablesMapper).updateTableMetadataByDatasourceId(7, "users", "test", "用户表", "TABLE");
	}

	@Test
	void findColumnsReturnsTheInitializationSnapshot() {
		DatasourceTableMetadataStoreImpl store = new DatasourceTableMetadataStoreImpl(columnMapper, tablesMapper);
		List<ColumnInfoBO> columns = List.of(ColumnInfoBO.builder().name("id").build());
		DatasourceTableColumnQuery query = DatasourceTableColumnQuery.builder()
			.datasourceId(7)
			.tableName("users")
			.build();
		when(columnMapper.findByQuery(query)).thenReturn(columns);

		assertEquals(columns, store.findColumns(7, "users", null));
	}

	@Test
	void findTableAndColumnsAggregatesSnapshotsForEachDatasource() {
		DatasourceTableMetadataStoreImpl store = new DatasourceTableMetadataStoreImpl(columnMapper, tablesMapper);
		TableInfoBO users = TableInfoBO.builder().schema("test").name("users").build();
		TableInfoBO orders = TableInfoBO.builder().schema("test").name("orders").build();
		ColumnInfoBO userId = ColumnInfoBO.builder().name("id").build();
		ColumnInfoBO orderId = ColumnInfoBO.builder().name("id").build();
		when(tablesMapper.getTableMetadataByDatasourceId(7)).thenReturn(List.of(users));
		when(tablesMapper.getTableMetadataByDatasourceId(8)).thenReturn(List.of(orders));
		when(columnMapper.findByQuery(DatasourceTableColumnQuery.builder()
			.datasourceId(7).schemaName("test").tableName("users").build())).thenReturn(List.of(userId));
		when(columnMapper.findByQuery(DatasourceTableColumnQuery.builder()
			.datasourceId(8).schemaName("test").tableName("orders").build())).thenReturn(List.of(orderId));

		List<TableInfoBO> result = store.findTableAndColumns(List.of(7, 8, 7));

		assertEquals(List.of(users, orders), result);
		assertEquals(7, result.get(0).getDataSourceId());
		assertEquals(List.of(userId), result.get(0).getColumns());
		assertEquals(8, result.get(1).getDataSourceId());
		assertEquals(List.of(orderId), result.get(1).getColumns());
	}

	@Test
	void findTableAndColumnsReturnsEmptyForNullOrEmptyDatasourceIds() {
		DatasourceTableMetadataStoreImpl store = new DatasourceTableMetadataStoreImpl(columnMapper, tablesMapper);

		assertEquals(List.of(), store.findTableAndColumns((List<Integer>) null));
		assertEquals(List.of(), store.findTableAndColumns(List.of()));
		assertEquals(List.of(), store.findTableAndColumns((Integer) null));
	}

}
