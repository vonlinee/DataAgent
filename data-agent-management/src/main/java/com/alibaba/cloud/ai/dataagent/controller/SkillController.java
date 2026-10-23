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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.cloud.ai.dataagent.entity.Skill;
import com.alibaba.cloud.ai.dataagent.mapper.SkillMapper;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

	private final Path root;
	private final SkillMapper skillMapper;

	public SkillController(
			@Value("${dataagent.skill.store.local.root-path}") String skillStoreLocalRootPath,
			SkillMapper skillMapper) {
		this.root = Paths.get(skillStoreLocalRootPath).toAbsolutePath().normalize();
		this.skillMapper = skillMapper;
	}

	@GetMapping
	public List<Map<String, Object>> list() throws IOException {
		synchronizeExistingSkills();
		List<Map<String, Object>> result = new ArrayList<>();
		for (Skill skill : skillMapper.findAll()) {
			Path dir = root.resolve(skill.getRootDir()).normalize();
			Map<String, Object> item = new HashMap<>();
			item.put("id", skill.getId());
			item.put("name", skill.getSkillName());
			item.put("description", skill.getDescription() != null ? skill.getDescription() : readSkillDescription(dir.resolve("SKILL.md")));
			item.put("modified", Files.exists(dir) ? dir.toFile().lastModified() : 0L);
			result.add(item);
		}
		return result;
	}

	@PostMapping
	public Map<String, Object> create(@RequestBody Map<String, String> body) throws IOException {
		String name = body.get("name") == null ? "" : body.get("name").trim();
		validateName(name);
		String description = body.get("description") == null ? "" : body.get("description").trim();
		validateDescription(description);
		if (skillMapper.findByName(name) != null) throw new IllegalArgumentException("Skill 名称已存在");
		Skill skill = new Skill();
		skill.setSkillName(name);
		skill.setDescription(description);
		skill.setRootDir("skill-" + UUID.randomUUID());
		skillMapper.insert(skill);
		Path dir = skillDir(skill.getRootDir());
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("SKILL.md"), "---\nname: " + name + "\ndescription: " + yamlString(description) + "\n---\n\n", StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			skillMapper.deleteById(skill.getId());
			throw e;
		}
		return Map.of("id", skill.getId(), "name", name, "description", description);
	}

	@PutMapping("/{skillId}")
	public Map<String, Object> update(@PathVariable Long skillId, @RequestBody Map<String, String> body) throws IOException {
		Skill skill = requireSkill(skillId);
		String name = body.get("name") == null ? "" : body.get("name").trim();
		validateName(name);
		String description = body.get("description") == null ? "" : body.get("description").trim();
		validateDescription(description);
		Skill existing = skillMapper.findByName(name);
		if (existing != null && !existing.getId().equals(skillId)) throw new IllegalArgumentException("Skill 名称已存在");
		skill.setSkillName(name);
		skill.setDescription(description);
		updateDefinitionMetadata(skillDir(skill.getRootDir()).resolve("SKILL.md"), name, description);
		skillMapper.updateMetadata(skill);
		return Map.of("id", skillId, "name", name, "description", description);
	}

	@DeleteMapping("/{skillId}")
	public void deleteSkill(@PathVariable Long skillId) {
		requireSkill(skillId);
		if (skillMapper.softDeleteById(skillId) == 0) throw new IllegalArgumentException("Skill 不存在");
	}

	@GetMapping("/{skillId}/tree")
	public List<Map<String, Object>> tree(@PathVariable Long skillId) throws IOException {
		Path dir = skillDir(skillId);
		if (!Files.isDirectory(dir)) return List.of();
		try (var paths = Files.walk(dir)) {
			return paths.filter(p -> !p.equals(dir)).sorted().map(p -> Map.<String, Object>of("path", dir.relativize(p).toString().replace('\\', '/'),
				"directory", Files.isDirectory(p), "size", Files.isDirectory(p) ? 0L : p.toFile().length())).toList();
		}
	}

	@GetMapping("/{skillId}/file")
	public ResponseEntity<ByteArrayResource> read(@PathVariable Long skillId, @RequestParam String path) throws IOException {
		Path file = resolve(skillId, path);
		if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new ByteArrayResource(Files.readAllBytes(file)));
	}

	@PutMapping("/{skillId}/file")
	public void write(@PathVariable Long skillId, @RequestParam String path,
			@RequestBody(required = false) byte[] content) throws IOException {
		Path file = resolve(skillId, path);
		Files.createDirectories(file.getParent());
		Files.write(file, content == null ? new byte[0] : content);
	}

	@PostMapping("/{skillId}/upload")
	public void upload(@PathVariable Long skillId, @RequestParam String path, @RequestParam("file") MultipartFile upload) throws IOException {
		Path file = resolve(skillId, path);
		Files.createDirectories(file.getParent());
		upload.transferTo(file);
	}

	@PostMapping("/{skillId}/directory")
	public void mkdir(@PathVariable Long skillId, @RequestBody Map<String, String> body) throws IOException {
		Files.createDirectories(resolve(skillId, body.get("path")));
	}

	@DeleteMapping("/{skillId}/file")
	public void delete(@PathVariable Long skillId, @RequestParam String path) throws IOException {
		Path target = resolve(skillId, path);
		if (Files.isDirectory(target)) {
			try (var paths = Files.walk(target)) {
				for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
			}
		} else Files.deleteIfExists(target);
	}

	@PostMapping("/{skillId}/move")
	public void move(@PathVariable Long skillId, @RequestBody Map<String, String> body) throws IOException {
		Path dir = skillDir(skillId);
		Path source = resolve(skillId, body.get("source"));
		Path target = resolve(skillId, body.get("target"));
		if (source.equals(dir) || target.equals(dir) || source.equals(target)) {
			throw new IllegalArgumentException("文件位置无效");
		}
		if (target.startsWith(source)) throw new IllegalArgumentException("不能将文件夹移动到自身目录中");
		ensureNoSymbolicLinks(dir, source);
		ensureNoSymbolicLinks(dir, target);
		if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("源文件不存在");
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("目标位置已存在");
		if (!Files.isDirectory(target.getParent(), LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("目标文件夹不存在");
		Files.move(source, target);
	}

	@GetMapping("/{skillId}/download")
	public ResponseEntity<ByteArrayResource> download(@PathVariable Long skillId) throws IOException {
		Skill skill = requireSkill(skillId);
		Path dir = skillDir(skillId);
		if (!Files.isDirectory(dir)) return ResponseEntity.notFound().build();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			try (var paths = Files.walk(dir)) {
				for (Path file : paths.filter(Files::isRegularFile).toList()) {
					zip.putNextEntry(new ZipEntry(skill.getSkillName() + "/" + dir.relativize(file).toString().replace('\\', '/')));
					Files.copy(file, zip);
					zip.closeEntry();
				}
			}
		}
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + skill.getSkillName() + ".zip\"")
			.contentType(MediaType.APPLICATION_OCTET_STREAM).body(new ByteArrayResource(bytes.toByteArray()));
	}

	@PostMapping("/upload")
	public Map<String, Object> importZip(@RequestParam("file") MultipartFile upload) throws IOException {
		if (upload.getSize() > 50L * 1024 * 1024) throw new IllegalArgumentException("压缩包不能超过 50 MB");
		Files.createDirectories(root);
		List<Path> written = new ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(upload.getInputStream())) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				Path target = root.resolve(entry.getName()).normalize();
				if (!target.startsWith(root)) throw new IllegalArgumentException("压缩包包含非法路径");
				if (entry.isDirectory()) Files.createDirectories(target);
				else { Files.createDirectories(target.getParent()); Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); written.add(target); }
			}
		}
		if (written.isEmpty()) return Map.of("id", 0L, "name", "");
		String name = root.relativize(written.get(0)).getName(0).toString();
		synchronizeExistingSkills();
		Skill imported = skillMapper.findByName(name);
		return imported == null ? Map.of("id", 0L, "name", "") : Map.of("id", imported.getId(), "name", imported.getSkillName());
	}

	private Skill requireSkill(Long id) {
		if (id == null || id <= 0) throw new IllegalArgumentException("Skill ID 无效");
		Skill skill = skillMapper.findById(id);
		if (skill == null) throw new IllegalArgumentException("Skill 不存在");
		return skill;
	}

	private Path skillDir(Long id) {
		return skillDir(requireSkill(id).getRootDir());
	}

	private Path skillDir(String storageKey) {
		Path dir = root.resolve(storageKey).normalize();
		if (!dir.startsWith(root) || dir.equals(root)) throw new IllegalArgumentException("Skill 存储路径无效");
		return dir;
	}

	private Path resolve(Long skillId, String relative) {
		Path dir = skillDir(skillId);
		Path path = dir.resolve(relative == null ? "" : relative).normalize();
		if (!path.startsWith(dir)) throw new IllegalArgumentException("文件路径无效");
		return path;
	}

	private void synchronizeExistingSkills() throws IOException {
		Files.createDirectories(root);
		java.util.Set<String> knownStorageKeys = new java.util.HashSet<>(skillMapper.findAllRootDirs());
		try (var paths = Files.list(root)) {
			for (Path dir : paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
				String name = dir.getFileName().toString();
				if (knownStorageKeys.contains(name) || name.length() > 80 || skillMapper.findByName(name) != null) continue;
				Skill skill = new Skill();
				skill.setSkillName(name);
				skill.setDescription(readSkillDescription(dir.resolve("SKILL.md")));
				skill.setRootDir(name);
				try {
					skillMapper.insert(skill);
					knownStorageKeys.add(name);
				}
				catch (org.springframework.dao.DuplicateKeyException ignored) {
					// A concurrent list request already registered this directory.
				}
			}
		}
	}

	private void validateName(String name) {
		if (!name.matches("[\\p{L}\\p{N}._-]{1,80}") || name.equals(".") || name.equals("..")) {
			throw new IllegalArgumentException("Skill 名称仅支持字母、数字、点、下划线和连字符");
		}
	}

	private void validateDescription(String description) {
		if (description.length() > 2000) throw new IllegalArgumentException("Skill 描述不能超过 2000 个字符");
	}

	private String yamlString(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
			.replace("\r", "\\r").replace("\n", "\\n") + "\"";
	}

	private void updateDefinitionMetadata(Path skillFile, String name, String description) throws IOException {
		String content = Files.isRegularFile(skillFile) ? Files.readString(skillFile, StandardCharsets.UTF_8) : "";
		String newline = content.contains("\r\n") ? "\r\n" : "\n";
		Matcher frontMatter = Pattern.compile("\\A---\\r?\\n([\\s\\S]*?)\\r?\\n---(?:\\r?\\n|\\z)").matcher(content);
		String body = content;
		List<String> fields;
		if (frontMatter.find()) {
			fields = new ArrayList<>(Arrays.asList(frontMatter.group(1).split("\\R", -1)));
			body = content.substring(frontMatter.end());
			while (!fields.isEmpty() && fields.get(fields.size() - 1).isEmpty()) fields.remove(fields.size() - 1);
		} else {
			fields = new ArrayList<>();
		}
		setFrontMatterValue(fields, "name", name);
		setFrontMatterValue(fields, "description", yamlString(description));
		String metadata = "---" + newline + String.join(newline, fields) + newline + "---" + newline;
		Files.createDirectories(skillFile.getParent());
		Files.writeString(skillFile, metadata + body, StandardCharsets.UTF_8);
	}

	private void setFrontMatterValue(List<String> fields, String key, String value) {
		Pattern fieldPattern = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*:.*$");
		for (int i = 0; i < fields.size(); i++) {
			if (fieldPattern.matcher(fields.get(i)).matches()) {
				fields.set(i, key + ": " + value);
				return;
			}
		}
		fields.add(key + ": " + value);
	}

	private String readSkillDescription(Path skillFile) throws IOException {
		if (!Files.isRegularFile(skillFile)) return "";
		boolean inFrontMatter = false;
		for (String line : Files.readAllLines(skillFile, StandardCharsets.UTF_8)) {
			if (line.trim().equals("---")) {
				if (inFrontMatter) break;
				inFrontMatter = true;
				continue;
			}
			if (!inFrontMatter || !line.startsWith("description:")) continue;
			String value = line.substring("description:".length()).trim();
			if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
				|| (value.startsWith("'") && value.endsWith("'")))) {
				value = value.substring(1, value.length() - 1);
			}
			return value;
		}
		return "";
	}

	private void ensureNoSymbolicLinks(Path dir, Path path) {
		Path current = dir;
		for (Path part : dir.relativize(path)) {
			current = current.resolve(part);
			if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("不支持通过符号链接访问文件");
		}
	}
}
