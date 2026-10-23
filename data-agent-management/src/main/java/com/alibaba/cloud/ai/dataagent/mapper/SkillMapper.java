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

import com.alibaba.cloud.ai.dataagent.entity.Skill;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SkillMapper {

	@Select("SELECT id, skill_name, description, root_dir, is_deleted, created_time, updated_time FROM agent_skill WHERE is_deleted = 0 ORDER BY id DESC")
	List<Skill> findAll();

	@Select("SELECT root_dir FROM agent_skill")
	List<String> findAllRootDirs();

	@Select("SELECT id, skill_name, description, root_dir, is_deleted, created_time, updated_time FROM agent_skill WHERE id = #{id} AND is_deleted = 0")
	Skill findById(@Param("id") Long id);

	@Select("SELECT id, skill_name, description, root_dir, is_deleted, created_time, updated_time FROM agent_skill WHERE skill_name = #{name} AND is_deleted = 0")
	Skill findByName(@Param("name") String name);

	@Insert("INSERT INTO agent_skill (skill_name, description, root_dir, is_deleted) VALUES (#{skillName}, #{description}, #{rootDir}, 0)")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Skill skill);

	@Update("UPDATE agent_skill SET skill_name = #{skillName}, description = #{description}, updated_time = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 0")
	int updateMetadata(Skill skill);

	@Delete("DELETE FROM agent_skill WHERE id = #{id}")
	int deleteById(@Param("id") Long id);

	@Update("UPDATE agent_skill SET is_deleted = 1, updated_time = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 0")
	int softDeleteById(@Param("id") Long id);

}
