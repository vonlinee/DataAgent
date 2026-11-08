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
		when(columnMapper.findByDatasourceAndTable(7, "users", null)).thenReturn(columns);

		assertEquals(columns, store.findColumns(7, "users", null));
	}

}
