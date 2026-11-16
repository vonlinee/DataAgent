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

import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessKnowledgeMapper {

	/**
	 * Query business knowledge list by agent ID
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_deleted = 0
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> selectByAgentId(@Param("agentId") Long agentId);

	/**
	 * Query all business knowledge list
	 */
	@Select("SELECT * FROM business_knowledge WHERE is_deleted = 0 ORDER BY created_time DESC")
	List<BusinessKnowledge> selectAll();

	/**
	 * Search in a specific agent scope by keyword
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_deleted = 0
			  AND (business_term LIKE CONCAT('%', #{keyword}, '%')
			    OR description LIKE CONCAT('%', #{keyword}, '%')
			    OR synonyms LIKE CONCAT('%', #{keyword}, '%'))
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> searchInAgent(@Param("agentId") Long agentId, @Param("keyword") String keyword);

	int insert(BusinessKnowledge knowledge);

	int batchInsert(@Param("list") List<BusinessKnowledge> list);

	int updateById(BusinessKnowledge knowledge);

	@Delete("""
			DELETE FROM business_knowledge
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	@Select("""
			SELECT * FROM business_knowledge
			WHERE id = #{id} AND is_deleted = 0
			""")
	BusinessKnowledge selectById(Long id);

	@Select("""
			SELECT id FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_recall = 1 AND is_deleted = 0
			""")
	List<Long> selectRecalledKnowledgeIds(@Param("agentId") Long agentId);

	@Update("""
			UPDATE business_knowledge
			SET embedding_status = 'PENDING', error_msg = 'Recovered stale PROCESSING job', updated_time = NOW()
			WHERE embedding_status = 'PROCESSING'
			  AND is_deleted = 0
			  AND updated_time < #{beforeTime}
			""")
	int resetStaleProcessing(@Param("beforeTime") LocalDateTime beforeTime);

	@Update("""
			UPDATE business_knowledge
			SET is_deleted = #{isDeleted}, updated_time = NOW()
			WHERE id = #{id}
			""")
	int logicalDelete(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

}
