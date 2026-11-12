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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelQueryReq;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SemanticModelMapper {

	/**
	 * Query semantic models by the optional list filters.
	 */
	@Results(id = "semanticModelResultMap", value = {
		@Result(property = "updateTime", column = "updated_time")
	})
	@Select("""
			<script>
			SELECT * FROM semantic_model
			<where>
				<if test="query.agentId != null">AND agent_id = #{query.agentId}</if>
				<if test="query.datasourceId != null">AND datasource_id = #{query.datasourceId}</if>
				<if test="query.keyword != null and query.keyword != ''">
					AND (table_name LIKE CONCAT('%', #{query.keyword}, '%')
						OR column_name LIKE CONCAT('%', #{query.keyword}, '%')
						OR business_name LIKE CONCAT('%', #{query.keyword}, '%')
						OR business_description LIKE CONCAT('%', #{query.keyword}, '%')
						OR synonyms LIKE CONCAT('%', #{query.keyword}, '%'))
				</if>
				<if test="query.tableName != null and query.tableName != ''">AND table_name = #{query.tableName}</if>
				<if test="query.columnName != null and query.columnName != ''">AND column_name = #{query.columnName}</if>
				<if test="query.businessName != null and query.businessName != ''">
					AND business_name LIKE CONCAT('%', #{query.businessName}, '%')
				</if>
				<if test="query.synonyms != null and query.synonyms != ''">
					AND synonyms LIKE CONCAT('%', #{query.synonyms}, '%')
				</if>
				<if test="query.createdTimeStart != null">AND created_time &gt;= #{query.createdTimeStart}</if>
				<if test="query.createdTimeEnd != null">AND created_time &lt;= #{query.createdTimeEnd}</if>
				<if test="query.updatedTimeStart != null">AND updated_time &gt;= #{query.updatedTimeStart}</if>
				<if test="query.updatedTimeEnd != null">AND updated_time &lt;= #{query.updatedTimeEnd}</if>
			</where>
			ORDER BY updated_time DESC, created_time DESC
			</script>
			""")
	List<SemanticModel> selectByQuery(@Param("query") SemanticModelQueryReq query);

	@ResultMap("semanticModelResultMap")
	@Select("SELECT * FROM semantic_model ORDER BY created_time DESC")
	List<SemanticModel> selectAll();

	/**
	 * Query semantic model list by agent ID
	 */
	@ResultMap("semanticModelResultMap")
	@Select("""
			SELECT * FROM semantic_model
			WHERE agent_id = #{agentId}
			ORDER BY created_time DESC
			""")
	List<SemanticModel> selectByAgentId(@Param("agentId") Long agentId);

	/**
	 * Query by id
	 */
	@ResultMap("semanticModelResultMap")
	@Select("""
			SELECT * FROM semantic_model
			WHERE id = #{id}
			""")
	SemanticModel selectById(@Param("id") Long id);

	/**
	 * Search semantic models by keyword
	 */
	@ResultMap("semanticModelResultMap")
	@Select("""
			SELECT * FROM semantic_model
			WHERE column_name LIKE CONCAT('%', #{keyword}, '%')
			   OR business_name LIKE CONCAT('%', #{keyword}, '%')
			   OR business_description LIKE CONCAT('%', #{keyword}, '%')
			   OR synonyms LIKE CONCAT('%', #{keyword}, '%')
			ORDER BY created_time DESC
			""")
	List<SemanticModel> searchByKeyword(@Param("keyword") String keyword);

	/**
	 * Batch enable fields
	 */
	@Update("""
			UPDATE semantic_model
			SET status = 1
			WHERE id = #{id}
			""")
	int enableById(@Param("id") Long id);

	/**
	 * Batch disable fields
	 */
	@Update("""
			UPDATE semantic_model
			SET status = 0
			WHERE id = #{id}
			""")
	int disableById(@Param("id") Long id);

	/**
	 * Query semantic models by agent ID and enabled status
	 */
	@ResultMap("semanticModelResultMap")
	@Select("""
			SELECT * FROM semantic_model
			WHERE agent_id = #{agentId}
			  AND status != 0
			ORDER BY created_time DESC
			""")
	List<SemanticModel> selectEnabledByAgentId(@Param("agentId") Long agentId);

	@Insert("""
			INSERT INTO semantic_model
			(agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, created_time, updated_time, status)
			VALUES
			(#{agentId}, #{datasourceId}, #{tableName}, #{columnName}, #{businessName}, #{synonyms}, #{businessDescription}, #{columnComment}, #{dataType}, NOW(), NOW(), #{status})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SemanticModel model);

	@Update("""
			<script>
			UPDATE semantic_model
			<set>
			    <if test="agentId != null">agent_id = #{agentId},</if>
			    <if test="datasourceId != null">datasource_id = #{datasourceId},</if>
			    <if test="tableName != null">table_name = #{tableName},</if>
				<if test="columnName != null">column_name = #{columnName},</if>
				<if test="businessName != null">business_name = #{businessName},</if>
				<if test="synonyms != null">synonyms = #{synonyms},</if>
				<if test="businessDescription != null">business_description = #{businessDescription},</if>
				<if test="columnComment != null">column_comment = #{columnComment},</if>
				<if test="dataType != null">data_type = #{dataType},</if>
				<if test="status != null">status = #{status},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SemanticModel model);

	@Delete("""
			DELETE FROM semantic_model
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	/**
	 * Query semantic models by datasource ID, status and table names
	 */
	@ResultMap("semanticModelResultMap")
	@Select("""
			<script>
			SELECT * FROM semantic_model
			WHERE datasource_id = #{datasourceId}
			  AND status = 1
			  AND table_name IN
			  <foreach item='tableName' index='index' collection='tableNames' open='(' separator=',' close=')'>
			    #{tableName}
			  </foreach>
			ORDER BY created_time DESC
			</script>
			""")
	List<SemanticModel> selectByDatasourceIdAndTableNames(@Param("datasourceId") Integer datasourceId,
			@Param("tableNames") List<String> tableNames);

	/**
	 * Query semantic model based on agentId, tableName, and columnName
	 */
	@ResultMap("semanticModelResultMap")
	@Select("""
			SELECT * FROM semantic_model
			WHERE agent_id = #{agentId}
			  AND table_name = #{tableName}
			  AND column_name = #{columnName}
			LIMIT 1
			""")
	SemanticModel selectByAgentIdAndTableNameAndColumnName(@Param("agentId") Integer agentId,
			@Param("tableName") String tableName, @Param("columnName") String columnName);

}
