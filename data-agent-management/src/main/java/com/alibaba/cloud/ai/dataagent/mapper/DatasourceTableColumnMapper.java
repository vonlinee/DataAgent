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
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** Mapper for the datasource-scoped column metadata snapshot. */
@Mapper
public interface DatasourceTableColumnMapper {

	/** Delete all column snapshots for a datasource before replacing its initialization result. */
	@Delete("DELETE FROM datasource_table_columns WHERE datasource_id = #{datasourceId}")
	int deleteByDatasource(@Param("datasourceId") Integer datasourceId);

	/** Insert one column from the fully enriched Schema initialization result. */
	@Insert("""
			INSERT INTO datasource_table_columns
				(datasource_id, schema_name, table_name, column_name, column_comment, data_type,
				 is_primary, is_notnull, samples, ordinal_position, create_time, update_time)
			VALUES (#{datasourceId}, #{table.schema}, #{table.name}, #{column.name}, #{column.description},
				#{column.type}, #{column.primary}, #{column.notnull}, #{column.samples}, #{ordinalPosition}, NOW(), NOW())
			""")
	int insert(@Param("datasourceId") Integer datasourceId, @Param("table") TableInfoBO table,
			@Param("column") ColumnInfoBO column, @Param("ordinalPosition") Integer ordinalPosition);

	/** Read cached columns in the same order reported by the source database. */
	@Results({
			@Result(property = "name", column = "column_name"),
			@Result(property = "tableName", column = "table_name"),
			@Result(property = "description", column = "column_comment"),
			@Result(property = "type", column = "data_type"),
			@Result(property = "primary", column = "is_primary"),
			@Result(property = "notnull", column = "is_notnull"),
			@Result(property = "samples", column = "samples")
	})
	@Select("""
			SELECT column_name, table_name, column_comment, data_type, is_primary, is_notnull, samples
			FROM datasource_table_columns
			WHERE datasource_id = #{datasourceId} AND table_name = #{tableName}
			  AND schema_name = #{schemaName}
			ORDER BY ordinal_position, id
			""")
	List<ColumnInfoBO> findByDatasourceAndTable(@Param("datasourceId") Integer datasourceId,
			@Param("tableName") String tableName, @Param("schemaName") String schemaName);

}
