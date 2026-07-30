---
id: M0-02
title: 项目治理、Skills 与交接协议
milestone: M0
status: planned
owner_role: qa-governance-agent
depends_on:
  - M0-01
required_skills:
  - coordinate-project-handoff
  - audit-third-party-skill
security_sensitive: false
---

## Goal

建立开发 Agent 可直接执行的治理文档、项目 Skills、严格交接协议和自动校验器，使任务范围、责任与证据能够跨 Agent 无歧义传递。

## Background

APK 后处理与 Runtime 保护涉及不可信输入、签名身份和本地代码。仅靠聊天上下文无法形成稳定约束，必须把阅读顺序、角色权限、任务模板、交接状态和第三方 Skill 审计固化在仓库内。

## Inputs

- 已完成的 M0-01 仓库基线。
- `docs/tasks/INDEX.md` 中登记的完整任务集合。
- 用户批准的治理结构、角色分工、HandOff frontmatter 和章节顺序。

## Expected Outputs

- 根治理文件、核心策划文档入口、四份 Agent 角色说明。
- 六个项目 Skill，每个包含完整 `SKILL.md` 与有效 `agents/openai.yaml`。
- `coordinate-project-handoff` 的 schema、worker 模板和 `scripts/validate-handoff.mjs`。
- 严格校验通过的根 `HandOff.md`，且没有 Host/Runtime 业务实现。

## In Scope

- 固化固定阅读顺序、任务边界、角色权限、安全复核和 PR 门禁。
- 实现无第三方运行时依赖的 Node.js HandOff 校验器。
- 规定第三方 Skill 的许可证、固定提交、脚本、网络、凭据和签名行为审计。
- 校验任务索引、Markdown 内部链接、Skill 结构和禁用内容。

## Out of Scope

- 安装 Apktool、JADX 或外部 Handoff Skill。
- 实现 APK 解析、DEX 加密、Runtime、测试 APK 或发布包。
- 修改 GitHub Issue 状态之外的业务开发工作。

## Implementation Decisions

- 分支名固定为 `docs/m0-02-handoff-rules`，Issue 标题固定为 `[M0-02] Governance, skills, and handoff`，仅允许一个关联 PR。
- 根 `HandOff.md` 仅 `/root` 可写；worker 只返回 `assets/worker-handoff-template.md` 定义的交接包。
- `validate-handoff.mjs` 仅使用 Node.js 标准库，严格验证字段集合、枚举、标题顺序、空章节值、SHA 祖先关系和禁用内容。
- 所有项目 Skill 的 frontmatter 只保留可执行的 `name` 与 `description`，正文明确触发条件、输入、步骤、停止条件、证据和安全限制。
- 第三方 Skill 默认拒绝；只有审计报告结论为 approved、许可证兼容且引用固定 commit SHA 时才可引入。
- 文档正文使用中文，命令、代码标识、分支名和提交标题使用英文。

## Public Interfaces

- 阅读入口：`AGENTS.md` → `HandOff.md` → `docs/README_FIRST.md` → 当前任务卡 → 相关设计文档 → 对应 Skill。
- 校验命令：`node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
- worker 交接结构：`task_id`、`status`、`branch`、`commit`、`changes`、`verification`、`artifacts`、`risks`、`blockers`、`next_actions`。
- HandOff schema 固定为 `schema_version: 1`。

## Security Constraints

- 文档、模板和校验输出不得含凭据、密码、私钥、客户 APK 路径或明文 DEX。
- 校验器只读目标文档和 Git 元数据，不执行文档中的命令，不访问网络。
- 安全敏感任务必须明确独立 reviewer，worker 不得自审后直接合并。
- 外部 Skill 未通过审计前不得复制脚本、运行安装命令或授予网络/凭据权限。

## Compatibility Requirements

- 校验器支持 Node.js 22 LTS，在 Windows PowerShell 和 Ubuntu shell 中行为一致。
- 所有仓库内相对链接使用 `/` 分隔符并区分文件名大小写。
- Markdown 与 YAML 必须可按 UTF-8 严格解码，不得出现 replacement character。

## Acceptance Criteria

1. `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` 退出码为 `0`。
2. 删除任一必需 frontmatter 字段、交换两个必需标题、写入禁用词或伪造非祖先 `base_commit` 的隔离测试均以非零退出。
3. 六个 Skill 目录都含 `SKILL.md` 与 `agents/openai.yaml`，且不存在脚手架说明或未完成标记。
4. `AGENTS.md` 明确固定阅读顺序、单任务边界、根 HandOff 所有权和安全复核门禁。
5. `docs/agents/WORKER_AGENT.md` 明确六条 worker 规则并链接交接模板。
6. 内部 Markdown 链接检查结果为零断链，任务索引编号唯一且依赖无环。
7. 仓库扫描确认本任务没有新增 APK、DEX、SO、密钥、证书或 Host/Runtime 业务源码。

## Required Tests

- 使用一份有效 HandOff 和至少六份单一错误 fixture 对校验器做正反测试。
- 在 Windows 与 Ubuntu 分别运行严格校验、Skill 结构校验和 Markdown 链接校验。
- 对所有文本执行 UTF-8、禁用词、绝对用户路径和敏感信息扫描。
- 对任务依赖图执行拓扑排序并断言包含索引中的每个任务一次。

## Required Evidence

- 每项校验的完整命令、退出码、Node.js/OS 版本和结果摘要。
- 有效及无效 fixture 的 SHA-256 与预期结果表。
- Skill 目录清单、任务拓扑顺序和断链数量。
- 变更文件 SHA-256、提交 SHA、Issue 与唯一 PR 链接。

## Likely Files

- `AGENTS.md`
- `HandOff.md`
- `docs/HANDOFF_SPEC.md`
- `docs/agents/`
- `.agents/skills/`

## Dependencies and Blockers

- M0-01 必须提供可提交的仓库基线。
- 若任务索引、HandOff schema 或角色所有权存在冲突，先由 `/root` 形成书面决策。
- 未经审计的第三方 Skill 不是依赖，不得以安装它作为解除阻塞的方法。

## Agent Handoff Requirements

- 本任务固定使用分支 `docs/m0-02-handoff-rules`、同编号 Issue 和一个 PR。
- 完成状态必须附命令、退出码、测试环境、产物 SHA-256、提交 SHA 和负向测试证据。
- worker 不修改根 `HandOff.md`，由 `/root` 根据结构化交接包整合状态。
- 与既有文档冲突时停止扩写范围，记录准确文件与冲突条款并提交 blocked 交接。
