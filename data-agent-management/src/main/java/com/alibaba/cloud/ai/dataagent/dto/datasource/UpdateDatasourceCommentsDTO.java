/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package com.alibaba.cloud.ai.dataagent.dto.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** Batch table and column comment changes submitted by the table browser. */
@Data
public class UpdateDatasourceCommentsDTO {

	@NotNull(message = "tables cannot be null")
	@Valid
	private List<TableCommentDTO> tables;

	@Data
	public static class TableCommentDTO {

		private String schema;

		@NotNull(message = "table name cannot be null")
		private String name;

		private String description;

		@Valid
		private List<ColumnCommentDTO> columns;

	}

	@Data
	public static class ColumnCommentDTO {

		@NotNull(message = "column name cannot be null")
		private String name;

		private String description;

	}

}
