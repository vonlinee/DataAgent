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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.Skill;
import com.alibaba.cloud.ai.dataagent.mapper.SkillMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillControllerTest {

	@TempDir
	Path tempDir;

	private WebTestClient client;
	private SkillMapper skillMapper;

	@BeforeEach
	void setUp() {
		skillMapper = mock(SkillMapper.class);
		Skill skill = new Skill();
		skill.setId(1L);
		skill.setRootDir("skill-test");
		when(skillMapper.findById(1L)).thenReturn(skill);
		client = WebTestClient.bindToController(new SkillController(tempDir.toString(), skillMapper)).build();
	}

	@Test
	void write_emptyRequestBody_createsEmptyFile() throws Exception {
		client.put()
			.uri("/api/skills/1/file?path=abc")
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.exchange()
			.expectStatus().isOk();

		Path file = tempDir.resolve("skill-test/abc");
		assertArrayEquals(new byte[0], Files.readAllBytes(file));
	}

	@Test
	void write_nonEmptyRequestBody_preservesFileContent() throws Exception {
		byte[] content = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);

		client.put()
			.uri("/api/skills/1/file?path=abc")
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.bodyValue(content)
			.exchange()
			.expectStatus().isOk();

		Path file = tempDir.resolve("skill-test/abc");
		assertArrayEquals(content, Files.readAllBytes(file));
	}

	@Test
	void update_savesSkillNameAndDescription() throws Exception {
		when(skillMapper.findByName("updated-skill")).thenReturn(null);
		when(skillMapper.updateMetadata(any(Skill.class))).thenReturn(1);

		client.put()
			.uri("/api/skills/1")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(java.util.Map.of("name", "updated-skill", "description", "Analyze sales data"))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.name").isEqualTo("updated-skill")
			.jsonPath("$.description").isEqualTo("Analyze sales data");

		verify(skillMapper).updateMetadata(any(Skill.class));
		String definition = Files.readString(tempDir.resolve("skill-test/SKILL.md"));
		assertTrue(definition.contains("name: updated-skill"));
		assertTrue(definition.contains("description: \"Analyze sales data\""));
	}

	@Test
	void delete_softDeletesSkillWithoutRemovingItsFiles() throws Exception {
		when(skillMapper.softDeleteById(1L)).thenReturn(1);
		Path skillFile = tempDir.resolve("skill-test/SKILL.md");
		Files.createDirectories(skillFile.getParent());
		Files.writeString(skillFile, "skill content");

		client.delete()
			.uri("/api/skills/1")
			.exchange()
			.expectStatus().isOk();

		verify(skillMapper).softDeleteById(1L);
		assertTrue(Files.exists(skillFile));
	}

}
