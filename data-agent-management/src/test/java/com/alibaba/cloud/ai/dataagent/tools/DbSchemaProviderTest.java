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
package com.alibaba.cloud.ai.dataagent.tools;

import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.DatasourceTableColumnQuery;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceTableMetadataStore;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbSchemaProviderTest {

    @Mock
    private DatasourceTableMetadataStore datasourceTableMetadataStore;

    @Mock
    private SemanticModelService semanticModelService;

    @InjectMocks
    private DbSchemaProvider dbSchemaProvider;

    @Test
    void listTablesFormatsTableMetadataForLlmContext() throws Exception {
        TableInfoBO table = TableInfoBO.builder()
                .schema("public")
                .name("orders")
                .type("TABLE")
                .description("订单信息")
                .primaryKeys(List.of("id"))
                .foreignKey("orders.user_id=users.id")
                .build();
        when(datasourceTableMetadataStore.findTables(7)).thenReturn(List.of(table));

        String result = dbSchemaProvider.listTables(7);

        assertThat(result)
                .contains("当前数据源的数据表和视图")
                .contains("表名：`public.orders`")
                .contains("类型：TABLE")
                .contains("描述：订单信息")
                .contains("主键：id")
                .contains("外键关系：orders.user_id=users.id");
        verify(datasourceTableMetadataStore).findTables(7);
    }

    @Test
    void listTablesReturnsEmptyContextWhenNoTableIsAvailable() throws Exception {
        when(datasourceTableMetadataStore.findTables(7)).thenReturn(List.of());

        assertThat(dbSchemaProvider.listTables(7)).isEqualTo("当前数据源没有可用的数据表或视图。");
    }

    @Test
    void listTablesWrapsMetadataFailure() throws Exception {
        when(datasourceTableMetadataStore.findTables(7))
                .thenThrow(new RuntimeException("connection failed"));

        assertThatThrownBy(() -> dbSchemaProvider.listTables(7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("获取数据源 7 的表结构失败")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void describeTablesFormatsColumnMetadataForLlmContext() throws Exception {
        ColumnInfoBO id = ColumnInfoBO.builder()
                .schemaName("public")
                .tableName("orders")
                .name("id")
                .type("BIGINT")
                .primary(true)
                .notnull(true)
                .description("订单 ID")
                .samples("[1,2]")
                .build();
        ColumnInfoBO status = ColumnInfoBO.builder()
                .schemaName("public")
                .tableName("orders")
                .name("status")
                .type("VARCHAR")
                .build();
        when(datasourceTableMetadataStore.findColumns(org.mockito.ArgumentMatchers.any(DatasourceTableColumnQuery.class)))
                .thenReturn(List.of(id, status));
        when(semanticModelService.query(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        String result = dbSchemaProvider.describeTables(7, List.of("public.orders"));

        assertThat(result)
                .contains("指定数据表的字段详情")
                .contains("表名: `public`.`orders`")
                .contains("`id`")
                .contains("类型：BIGINT")
                .contains("非空：是")
                .contains("主键：是")
                .contains("注释：订单 ID")
                .contains("示例值：[1,2]")
                .contains("`status`");
        verify(datasourceTableMetadataStore).findColumns(org.mockito.ArgumentMatchers.any(DatasourceTableColumnQuery.class));
    }

    @Test
    void describeTablesReportsMissingColumnsAndInvalidNames() throws Exception {
        when(datasourceTableMetadataStore.findColumns(org.mockito.ArgumentMatchers.any(DatasourceTableColumnQuery.class)))
                .thenReturn(List.of());
        when(semanticModelService.query(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        assertThat(dbSchemaProvider.describeTables(7, List.of("", "  ", "public.orders")))
                .contains("指定数据表的字段详情")
                .contains("`public`.`orders`")
                .contains("无可用字段");
        verify(datasourceTableMetadataStore).findColumns(org.mockito.ArgumentMatchers.any(DatasourceTableColumnQuery.class));
    }

    @Test
    void describeTablesWrapsMetadataFailure() throws Exception {
        when(datasourceTableMetadataStore.findColumns(org.mockito.ArgumentMatchers.any(DatasourceTableColumnQuery.class)))
                .thenThrow(new RuntimeException("connection failed"));
        assertThatThrownBy(() -> dbSchemaProvider.describeTables(7, List.of("public.orders")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("获取数据源 7 的表字段详情失败")
                .hasCauseInstanceOf(RuntimeException.class);
    }

}
