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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
class AgentDatasourceTablesMapperIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AgentDatasourceTablesMapper mapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS agent_datasource_tables");
		jdbcTemplate.execute("DROP TABLE IF EXISTS agent_datasource");
		jdbcTemplate.execute("""
				CREATE TABLE agent_datasource (
				  id INT PRIMARY KEY,
				  agent_id BIGINT NOT NULL,
				  datasource_id INT NOT NULL,
				  is_active INT NOT NULL
				)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE agent_datasource_tables (
				  agent_datasource_id INT NOT NULL,
				  schema_name VARCHAR(255) NOT NULL DEFAULT '',
				  table_name VARCHAR(255) NOT NULL,
				  table_type VARCHAR(50),
				  table_comment VARCHAR(1000),
				  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
				  UNIQUE (agent_datasource_id, table_name)
				)
				""");
	}

	@Test
	void selectedTablesAreUnionedAcrossAgentsSharingDatasource() {
		jdbcTemplate.update("INSERT INTO agent_datasource VALUES (1, 10, 3, 1), (2, 11, 3, 1), (3, 12, 4, 1)");
		jdbcTemplate.update("""
				INSERT INTO agent_datasource_tables (agent_datasource_id, table_name) VALUES
				  (1, 'orders'), (1, 'users'),
				  (2, 'orders'), (2, 'products'),
				  (3, 'unrelated_table')
				""");

		assertThat(mapper.getSelectedTablesByDatasourceId(3)).containsExactly("orders", "products", "users");
	}

	@Test
	void tableMetadataCanBeUpdatedAndReadForDatasource() {
		jdbcTemplate.update("INSERT INTO agent_datasource VALUES (1, 10, 3, 1)");
		jdbcTemplate.update("""
				INSERT INTO agent_datasource_tables
				(agent_datasource_id, schema_name, table_name, table_type, table_comment)
				VALUES (1, 'public', 'orders', NULL, NULL)
				""");

		mapper.updateTableMetadataByDatasourceId(3, "orders", "public", "订单表", "TABLE");

		assertThat(mapper.getTableMetadataByDatasourceId(3)).singleElement()
			.satisfies(table -> {
				assertThat(table.getName()).isEqualTo("orders");
				assertThat(table.getSchema()).isEqualTo("public");
				assertThat(table.getType()).isEqualTo("TABLE");
				assertThat(table.getDescription()).isEqualTo("订单表");
			});
	}

}
