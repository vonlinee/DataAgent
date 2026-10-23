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
package com.alibaba.cloud.ai.dataagent.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Skill {

	/** 主键ID */
	private Long id;

	/** Skill名称 */
	private String skillName;
	/** Skill描述 */
	private String description;

	/** Skill文件根目录 */
	private String rootDir;
	/** 删除标记：0-未删除，1-已删除 */
	private Integer isDeleted;
	/** 创建时间 */
	private LocalDateTime createdTime;
	/** 更新时间 */
	private LocalDateTime updatedTime;

}
