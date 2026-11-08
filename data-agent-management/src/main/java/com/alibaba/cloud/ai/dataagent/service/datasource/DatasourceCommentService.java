/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package com.alibaba.cloud.ai.dataagent.service.datasource;

import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO;

/** Applies table and column comments to the connected business database. */
public interface DatasourceCommentService {

	void updateComments(Integer datasourceId, UpdateDatasourceCommentsDTO request) throws Exception;

}
