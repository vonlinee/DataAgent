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
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceTableMetadataStore;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry.DatasourceTypeHandlerRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasourceMetadataServiceImplTest {

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private DatasourceTypeHandlerRegistry datasourceTypeHandlerRegistry;

	@Mock
	private DBConnectionPoolFactory dbConnectionPoolFactory;

	@Mock
	private DatasourceTableMetadataStore metadataStore;

	@Test
	void initializedTableDetailsReturnsCachedTables() throws Exception {
		DatasourceMetadataServiceImpl service = new DatasourceMetadataServiceImpl(datasourceService, accessorFactory,
			datasourceTypeHandlerRegistry, dbConnectionPoolFactory, metadataStore);
		TableInfoBO cached = TableInfoBO.builder().schema("public").name("orders").type("TABLE").build();
		when(metadataStore.findTables(1)).thenReturn(Map.of("public.orders", cached));

		List<TableInfoBO> result = service.getInitializedDatasourceTableDetails(1);

		assertEquals(List.of(cached), result);
	}

	@Test
	void initializedTableDetailsReadsOnlyTheMetadataSnapshot() throws Exception {
		DatasourceMetadataServiceImpl service = new DatasourceMetadataServiceImpl(datasourceService, accessorFactory,
			datasourceTypeHandlerRegistry, dbConnectionPoolFactory, metadataStore);
		TableInfoBO cached = TableInfoBO.builder().schema("public").name("orders")
			.description("订单表").type("TABLE").build();
		when(metadataStore.findTables(1)).thenReturn(Map.of("public.orders", cached));

		List<TableInfoBO> result = service.getInitializedDatasourceTableDetails(1);

		assertEquals(List.of(cached), result);
		verify(metadataStore).findTables(1);
		verifyNoInteractions(datasourceService, accessorFactory, datasourceTypeHandlerRegistry, dbConnectionPoolFactory);
	}

	@Test
	void initializedColumnsNormalizeMissingSchemaToDefaultSchema() {
		DatasourceMetadataServiceImpl service = new DatasourceMetadataServiceImpl(datasourceService, accessorFactory,
			datasourceTypeHandlerRegistry, dbConnectionPoolFactory, metadataStore);
		List<ColumnInfoBO> columns = List.of(ColumnInfoBO.builder().name("id").build());
		when(metadataStore.findColumns(1, "orders", "")).thenReturn(columns);

		assertEquals(columns, service.getInitializedTableColumnDetails(1, "orders", null));
		verify(metadataStore).findColumns(1, "orders", "");
	}

}
