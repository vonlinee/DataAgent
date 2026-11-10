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

import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO.ColumnCommentDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO.TableCommentDTO;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceTableColumnMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceCommentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists user-maintained comments in the DataAgent metadata snapshots. */
@Service
@RequiredArgsConstructor
public class DatasourceCommentServiceImpl implements DatasourceCommentService {

	private final AgentDatasourceTablesMapper tablesMapper;

	private final DatasourceTableColumnMapper columnMapper;

	@Override
	@Transactional
	public void updateComments(Integer datasourceId, UpdateDatasourceCommentsDTO request) throws Exception {
		if (datasourceId == null || request == null || request.getTables() == null) {
			throw new IllegalArgumentException("数据源和注释变更不能为空");
		}
		if (request.getTables().isEmpty()) {
			return;
		}
		for (TableCommentDTO table : request.getTables()) {
			applyTableComment(datasourceId, table);
		}
	}

	private void applyTableComment(Integer datasourceId, TableCommentDTO table) {
		String tableName = identifier(table.getName(), "表名");
		String tableComment = StringUtils.defaultString(table.getDescription());
		tablesMapper.updateTableCommentByDatasourceId(datasourceId, tableName, tableComment);
		String schema = StringUtils.defaultString(table.getSchema());
		List<ColumnCommentDTO> columns = table.getColumns();
		if (columns == null) {
			return;
		}
		for (ColumnCommentDTO column : columns) {
			String columnName = identifier(column.getName(), "列名");
			String columnComment = StringUtils.defaultString(column.getDescription());
			columnMapper.updateColumnComment(datasourceId, schema, tableName, columnName, columnComment);
		}
	}

	private String identifier(String value, String label) {
		String trimmed = StringUtils.trimToNull(value);
		if (trimmed == null || !trimmed.matches("[A-Za-z0-9_$-]+")) {
			throw new IllegalArgumentException(label + "包含非法字符");
		}
		return trimmed;
	}

}
