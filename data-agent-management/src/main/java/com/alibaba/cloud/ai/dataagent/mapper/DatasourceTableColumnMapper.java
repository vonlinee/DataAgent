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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.DatasourceTableColumnQuery;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for the datasource-scoped column metadata snapshot.
 */
@Mapper
public interface DatasourceTableColumnMapper {

    /**
     * Delete all column snapshots for a datasource before replacing its initialization
     * result.
     */
    int deleteByDatasource(@Param("datasourceId") Integer datasourceId);

    /**
     * Insert one column from the fully enriched Schema initialization result.
     */
    int insert(@Param("datasourceId") Integer datasourceId, @Param("table") TableInfoBO table,
               @Param("column") ColumnInfoBO column, @Param("ordinalPosition") Integer ordinalPosition);

    /**
     * Read initialized columns using the optional query filters.
     */
    List<ColumnInfoBO> findByQuery(DatasourceTableColumnQuery query);

    /**
     * Update one user-maintained column comment in the datasource snapshot.
     */
    int updateColumnComment(@Param("datasourceId") Integer datasourceId, @Param("schemaName") String schemaName,
                            @Param("tableName") String tableName, @Param("columnName") String columnName,
                            @Param("columnComment") String columnComment);

}
