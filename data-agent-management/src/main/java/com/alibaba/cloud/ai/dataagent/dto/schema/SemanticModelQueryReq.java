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
package com.alibaba.cloud.ai.dataagent.dto.schema;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 语义模型列表查询请求。
 */
@Data
public class SemanticModelQueryReq {

	/** 智能体ID。 */
	private Long agentId;

	/** 兼容旧接口的通用关键词，匹配表名、字段名、业务名称、业务描述和同义词。 */
	private String keyword;

	/** 数据源ID，精确筛选。 */
	private Integer datasourceId;

	/** 表名，精确筛选。 */
	private String tableName;

	/** 字段名，精确筛选。 */
	private String columnName;

	/** 业务名称，模糊筛选。 */
	private String businessName;

	/** 同义词，模糊筛选。 */
	private String synonyms;

	/** 创建时间起点，包含该时刻。 */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime createdTimeStart;

	/** 创建时间终点，包含该时刻。 */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime createdTimeEnd;

	/** 更新时间起点，包含该时刻。 */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime updatedTimeStart;

	/** 更新时间终点，包含该时刻。 */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime updatedTimeEnd;

}
