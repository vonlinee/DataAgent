/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO.ColumnCommentDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO.TableCommentDTO;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceTableColumnMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DatasourceCommentServiceImplTest {

	private AgentDatasourceTablesMapper tablesMapper;

	private DatasourceTableColumnMapper columnMapper;

	private DatasourceCommentServiceImpl service;

	@BeforeEach
	void setUp() {
		tablesMapper = Mockito.mock(AgentDatasourceTablesMapper.class);
		columnMapper = Mockito.mock(DatasourceTableColumnMapper.class);
		service = new DatasourceCommentServiceImpl(tablesMapper, columnMapper);
	}

	@Test
	void updateCommentsPersistsOnlyDataAgentMetadata() throws Exception {
		ColumnCommentDTO column = new ColumnCommentDTO();
		column.setName("actor_id");
		column.setDescription("字段注释");
		TableCommentDTO table = new TableCommentDTO();
		table.setSchema("sakila");
		table.setName("actor");
		table.setDescription("表注释");
		table.setColumns(List.of(column));
		TableCommentDTO secondTable = new TableCommentDTO();
		secondTable.setName("address");
		secondTable.setDescription("地址表");
		UpdateDatasourceCommentsDTO request = new UpdateDatasourceCommentsDTO();
		request.setTables(List.of(table, secondTable));

		service.updateComments(1, request);

		verify(tablesMapper).updateTableCommentByDatasourceId(1, "actor", "表注释");
		verify(tablesMapper).updateTableCommentByDatasourceId(1, "address", "地址表");
		verify(columnMapper).updateColumnComment(1, "sakila", "actor", "actor_id", "字段注释");
	}

	@Test
	void updateCommentsRejectsMissingRequest() {
		assertThrows(IllegalArgumentException.class, () -> service.updateComments(1, null));
	}

}
