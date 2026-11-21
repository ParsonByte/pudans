# 参与贡献 Pudans

感谢你愿意为 Pudans 贡献代码。本文说明协作流程；更详细、具有约束力的规范位于 [AGENTS.md](AGENTS.md) 与 [`.agents/rules`](.agents/rules)。

## 开始之前

- 先阅读 [AGENTS.md](AGENTS.md)，再阅读 [`.agents`](.agents) 下与任务相关的规则与技能。
- 非简单改动请先开 Issue 或讨论，确认方案与范围后再动手。
- 创建类、组件、方法或配置项前，先搜索仓库中是否已有实现，优先复用或扩展，遵循 `reuse-and-cleanup` 规则。
- 禁止在仓库任何位置添加 mock、fake、demo、种子或桩数据，必须基于真实 API 与真实配置开发。

## 分支

- `main` 是唯一的长期分支与发布分支。
- 从最新的 `origin/main` 创建短生命周期分支：`<type>/<name>`，例如 `feat/ai-route-groups` 或 `fix/chat-stream`。
- 禁止直接向 `main` 提交代码。
- 一个分支只负责一个任务；提交 Pull Request 前先 rebase 到 `origin/main`。
- 禁止直接推送 `main`，禁止改写共享历史。推送自己的功能分支后发起 Pull Request。

## 提交

- 使用单行 Conventional Commits 格式：`<type>: <subject>`。
- subject 为小写英文、祈使语气，不超过 72 个字符，结尾不加句号。
- 一个提交只做一件事。
- 提交信息只保留标题行：不要添加正文、footer 或 `Co-authored-by`。

示例：

```text
feat: add ai route group editor
fix: persist ai message metadata and decode jsonb
docs: add readme contributing and license
```

## 代码规范

- 遵循 [`.editorconfig`](.editorconfig)，不要顺手格式化无关代码。
- 注释只允许使用单行中文 `//` 注释；禁止 Javadoc 块、注释掉的代码或英文注释。
- 后端只使用 WebFlux、R2DBC 与现有 Jackson 配置；禁止引入 JDBC、JPA、Spring MVC、Flyway 或第二套 HTTP / JSON 库。
- 后端依赖版本写入 `gradle/libs.versions.toml`。Pull Request 中说明新增依赖的用途与重叠情况。
- 禁止提交凭据、令牌、私钥或本地环境文件。

## 测试与检查

推送前请运行相关检查：

```bash
./gradlew :appkernel:test
./gradlew :api-docs:validateOpenApiDocs
```

`make verify` 会执行后端检查与 `git diff --check`。

行为变更必须同时新增或更新测试。后端测试位于 `appkernel/src/test/java`，使用 JUnit 5、Mockito 与 Reactor Test。禁止为了让改动通过而禁用测试。

## Pull Request

- Pull Request 保持小而聚焦，一个 PR 解决一个问题。
- 标题与描述使用英文。
- 说明改了什么、影响哪些模块、如何验证，以及是否存在迁移或人工步骤。
- 新增实现时，按 `.agents/rules/reuse-and-cleanup.md` 提供复用搜索证据。
- Pull Request 只有在评审通过且所有评审意见处理后才能合并；使用 rebase and merge 保持 `main` 线性。

## 许可证

提交贡献即表示你同意该贡献按仓库的 [GNU 通用公共许可证第 3 版](LICENSE) 授权，并确认你有权以该许可证提交它。
