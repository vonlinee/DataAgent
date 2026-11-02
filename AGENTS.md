# DataAgent 开发协作说明

本文件适用于仓库根目录及其所有子目录。开始修改代码前，请根据改动范围阅读对应文档，并遵循其中的约定。

# 文档指引

以下文档是当前仓库的项目指南：

- [快速开始](docs/QUICK_START.md)：环境准备、数据库初始化和本地启动。
- [架构设计](docs/ARCHITECTURE.md)：模块边界、工作流和核心组件关系。
- [开发者指南](docs/DEVELOPER_GUIDE.md)：开发环境、配置、编码规范和扩展方式。
- [高级功能](docs/ADVANCED_FEATURES.md)：API Key、MCP、检索策略和 Python 沙盒等功能。
- [知识配置最佳实践](docs/KNOWLEDGE_USAGE.md)：业务知识和智能体知识的配置与使用。
- [贡献说明](CONTRIBUTING-zh.md)：分支、提交、格式检查和 Pull Request 流程。

# 开发规则

## Git 提交规范

提交信息使用语义化提交（Conventional Commits）格式：

```text
<type>(<scope>): <subject>
```

- `type` 使用项目认可的变更类型：`feat`（新功能）、`fix`（修复）、`docs`（文档）、`refactor`（重构）、`perf`（性能）、`test`（测试）、`build`（构建/依赖）、`ci`（持续集成）、`chore`（维护）或 `revert`（回滚）。
- `scope` 使用受影响的模块或领域，例如 `backend`、`frontend`、`db`、`workflow` 或 `docs`；变更跨多个领域时使用最主要的范围或省略。
- `subject` 使用简洁的祈使句描述变更，首字母小写，不以句号结尾；不要只写 `update`、`修改` 等无信息内容。
- 一个提交只表达一个可独立审查和回滚的逻辑变更，避免把无关格式化、生成文件或环境配置混入提交。
- 破坏性变更在类型后添加 `!`，并在提交正文或脚注中说明迁移方式，例如 `feat(api)!: change datasource response`。
- 提交前检查标题格式，并遵循仓库的 [PR 标题检查配置](.github/workflows/lint-pr-title.yml)。

示例：

```text
feat(frontend): add datasource connection test panel
fix(backend): prevent duplicate agent datasource binding
docs(db): document incremental schema script convention
```

## Java 格式化与 Checkstyle

在仓库根目录执行。Windows 用 `.\mvnw.cmd`，Unix 用 `./mvnw`。

```powershell
# 格式化
.\mvnw.cmd spring-javaformat:apply
# 检查格式
.\mvnw.cmd spring-javaformat:validate
# 清理导入、补许可证头
.\mvnw.cmd spotless:apply
# Checkstyle
.\mvnw.cmd checkstyle:check
# 仅检查后端模块
.\mvnw.cmd -pl data-agent-management checkstyle:check
# 生成 target/checkstyle-report.xml
.\mvnw.cmd -Dcheckstyle.output.file=checkstyle-report.xml checkstyle:check
```

Checkstyle 配置为 `CI/src/checkstyle/checkstyle.xml`，绑定 Maven `validate` 阶段，覆盖测试源码。`validate`、`test`、`package`、`verify` 会自动执行。

需要 `make` 和 `mvnd` 时可用：

```bash
make format-fix # 格式化
make format-check # 检查格式
make spotless-apply # 清理导入、补许可证头
make checkstyle-check # Checkstyle
```

提交前按上述顺序执行。

## 数据库脚本更新规则

更新数据库 Schema 时，必须为已有环境提供增量脚本，并同时维护 MySQL 和 H2 两种数据库脚本：

- MySQL 脚本放在 `scripts/sql/mysql/YYYYMMDD/`，H2 脚本放在 `scripts/sql/h2/YYYYMMDD/`；目录使用八位日期命名，例如 `scripts/sql/mysql/20261025/xxx.sql`。
- 同一次 Schema 变更必须同时提交两种数据库脚本。两者应保持相同的业务语义，按 MySQL/H2 方言分别实现，不能用一份脚本假定两种数据库语法完全兼容。
- 文件名使用能表达变更内容的 `snake_case` 名称，例如 `add_agent_avatar_index.sql`；同一日期下不得重复命名。历史增量脚本一经合并不得修改，后续变更新建脚本。
- 所有新增或修改的数据库表和字段都必须添加清晰、准确的注释，说明业务含义、取值范围、单位、默认值或状态值；MySQL 使用表/列 `COMMENT`，H2 使用对应的 `COMMENT ON TABLE`/`COMMENT ON COLUMN`（或等价语法）。
- 脚本应尽量幂等，或在文件头明确执行前提、执行顺序和不可重复执行的原因；涉及数据回填时说明批次、锁影响和失败恢复方式。
- 变更必须同步检查实体、Mapper、DTO、接口和测试夹具。若项目的全量初始化 Schema 仍需支持全新部署，也要同步更新对应的基础 Schema 文件，但不能以修改基础文件替代增量脚本。
- 提交前分别在 MySQL 和 H2 上验证脚本的语法、约束、索引、默认值和回滚/恢复路径，并在 PR 说明中列出执行顺序和兼容性影响。_


# 修改要求

- 先理解相关模块和现有实现，再进行最小范围修改；不要覆盖与当前任务无关的工作区变更。
- 新增或修改后端代码时，保持 Java 17、Spring Boot 和现有 Spring AI Alibaba 模式的一致性。
- 新增或修改前端代码时，遵循现有 Nuxt、TypeScript、ESLint 和 Prettier 配置。
- 为行为变化补充或更新测试；提交前至少运行与改动模块相关的测试和格式检查。
- 修改配置、接口或用户可见行为时，同步更新对应文档，并保持中英文文档一致（如适用）。
