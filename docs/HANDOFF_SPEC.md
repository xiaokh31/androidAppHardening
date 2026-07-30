# 交接规范

## 1. 目的与所有权

根 `HandOff.md` 是项目恢复工作的唯一入口，描述已合并事实、当前状态、阻塞和有序下一步。仅 `/root` 可以更新根交接文件。Worker 不直接编辑它，而是在任务结束时提交本规范定义的结构化交接包，由 `/root` 在合并前整合。

交接不是状态口号。任何 `done` 结论必须带有可复现命令、退出码、环境和产物 SHA-256。

## 2. 根 HandOff frontmatter

根文件以 UTF-8 YAML frontmatter 开始，字段与顺序固定：

```yaml
---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260731-120000
updated_at: 2026-07-31T12:00:00+08:00
updated_by: /root
state: ready
source_branch: main
base_commit: 0123456789abcdef0123456789abcdef01234567
working_tree: clean
current_milestone: M0
active_task: M0-03
next_owner: unassigned
---
```

示例值用于说明格式；实际文件必须使用生成时的真实时间、分支和 commit。

字段规则：

| 字段 | 规则 |
| --- | --- |
| `schema_version` | 整数 `1` |
| `project` | 固定为 `androidAppHardening` |
| `handoff_id` | `HO-YYYYMMDD-HHMMSS`，使用 `updated_at` 对应的本地时间 |
| `updated_at` | 带时区的 ISO-8601 |
| `updated_by` | 固定为 `/root` |
| `state` | `active`、`ready` 或 `blocked` |
| `source_branch` | 生成交接时的真实分支 |
| `base_commit` | 40 位小写 Git SHA，尚无提交时才允许 `UNBORN` |
| `working_tree` | `clean` 或 `dirty`，必须与生成时状态一致 |
| `current_milestone` | `M0`、`M1`、`M2`、`M3` 或 `M4` |
| `active_task` | 合法任务 ID 或 `NONE` |
| `next_owner` | 已定义角色、Agent 名或 `unassigned` |

`base_commit` 必须是当前 `HEAD` 的祖先。`source_branch` 与 `working_tree` 必须通过 Git 读取，不得凭记忆填写。

## 3. 根 HandOff 正文章节

标题与顺序固定：

```text
# Project HandOff
## Objective
## Current State
## Active Workstreams
## Decisions and Invariants
## Changes Since Previous Handoff
## Verification Evidence
## Blockers and Required Approvals
## Ordered Next Actions
## Relevant Files and Artifacts
## Resume Checklist
## Handoff Sign-off
```

任何章节无内容时写 `None`。不得省略章节、改变标题或使用模糊占位语。

### Objective

用一段话说明当前项目要达成的可验证目标，不复述营销表述。

### Current State

列出当前里程碑、活动任务、最近已合并任务和工作树状态。只写已验证事实。

### Active Workstreams

使用表格记录任务 ID、负责人、分支、状态、依赖和下一检查点。只列活动或已明确分配的工作。

### Decisions and Invariants

引用相关 ADR，并重述影响下一位 Agent 的不可变约束，包括输入只读、输出未签名、`minSdk >= 29` 和安全边界。

### Changes Since Previous Handoff

逐项记录 commit/PR、变更文件和行为影响。不使用“若干优化”等不可核验描述。

### Verification Evidence

每条证据包含：

```text
task_id
git_commit
command
exit_code
environment
artifact
sha256
result
```

没有产物的文档校验将 `artifact` 和 `sha256` 写为 `not_applicable` 并说明校验对象。

### Blockers and Required Approvals

阻塞项必须说明影响任务、已验证事实、需要谁做什么决策，以及解除阻塞的可观察条件。

### Ordered Next Actions

使用有序列表，每项包括任务 ID、负责人或 `unassigned`、前置条件和预期证据。顺序代表执行优先级。

### Relevant Files and Artifacts

只使用仓库相对路径或 CI artifact 名。不得记录用户目录绝对路径、客户 APK 路径或秘密存储位置。

### Resume Checklist

下一位 `/root` 可逐项执行的检查，至少包括 Git 状态、HandOff 校验、任务依赖、PR/Issue 状态和证据完整性。

### Handoff Sign-off

记录生成者、生成时间、验证命令和结果。只有 `/root` 可以签署根交接。

## 4. 状态语义

- `active`：存在已分配且正在进行的任务；`active_task` 必须为该任务。
- `ready`：没有未解决阻塞，下一项工作可以领取；`active_task` 可指向首个待执行任务。
- `blocked`：存在阻止关键路径继续的明确条件；阻塞章节必须非空。

任务状态只允许任务卡定义的枚举。根 HandOff 不创建独立、冲突的状态体系。

## 5. 更新触发条件

发生以下任一事件时，`/root` 必须更新根 HandOff：

- 任务分配、转移或完成；
- 阻塞新增、变化或解除；
- 安全或架构决策改变；
- 验证门禁完成或失败；
- 里程碑变化；
- 并行 PR 按顺序 rebase/merge 后；
- 当前会话结束且仍有后续工作。

并行 PR 必须逐个更新到最新 `main`、验证、合并。最后一个合并完成后，再基于真实 `main` 生成最终 HandOff。

## 6. 初始 ready 状态

项目文本包合并后，根 HandOff 的业务状态必须为：

```yaml
state: ready
current_milestone: M0
active_task: M0-03
next_owner: unassigned
```

`Ordered Next Actions` 固定为：

1. 分配并完成 M0-03 工具链与 CI。
2. 并行启动 M0-04 ClassLoader PoC。
3. M0-04 通过后启动 M0-05 兼容性 PoC。
4. M0 门禁通过后冻结容器接口，再并行启动 M1 与 M2。

## 7. Worker 交接包

Worker 返回 Markdown，章节固定为：

```text
# Worker Handoff
## Task
## Outcome
## Scope
## Files Changed
## Public Interfaces
## Verification Evidence
## Security and Compatibility
## Remaining Risks
## Blockers
## Recommended Next Action
```

要求：

- `Task` 包含任务 ID、分支和 commit。
- `Outcome` 只能使用 `done` 或 `blocked`，并给出一句可验证摘要。
- `Scope` 对照任务卡列出完成与未完成事项。
- `Files Changed` 使用仓库相对路径并说明每个文件用途。
- `Public Interfaces` 记录新增或变更的 schema、CLI、错误码、容器字段和 Runtime 接口；无变化写 `None`。
- `Verification Evidence` 遵循根证据字段。
- `Security and Compatibility` 明确输入只读、签名边界、API/ABI 影响和独立复核状态。
- `Remaining Risks` 记录已知但不阻塞本任务的风险；无风险写 `None`。
- `Blockers` 仅记录可解除的明确条件；无阻塞写 `None`。
- `Recommended Next Action` 只建议任务依赖图中的下一步，不扩大范围。

## 8. 严格校验规则

`coordinate-project-handoff/scripts/validate-handoff.mjs` 必须检查：

- frontmatter 字段、顺序、类型和枚举；
- 标题唯一、完整且顺序正确；
- UTF-8 解码成功且不含 Unicode replacement character；
- 不含模糊占位语；
- 不含 Windows 用户目录、Unix home 目录等绝对用户路径；
- 不含私钥、token、密码值、客户 APK 路径或 DEX 明文；
- `base_commit` 格式及其为 `HEAD` 祖先；
- 分支和工作树状态与 Git 一致；
- `active_task` 存在于任务索引；
- `done` 证据具备命令、退出码、环境和 SHA-256；
- 相对路径存在，或被明确标记为预期后续产物。

校验命令：

```text
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md
```

退出码 `0` 表示通过；任一结构、安全或 Git 一致性错误返回非零。

## 9. 安全规则

根 HandOff 和 Worker 交接包禁止包含：

- 私钥、keystore 内容、alias、密码值、token 或凭据；
- 真实客户 APK 名称与路径；
- 明文业务 DEX、密钥片段、nonce 或可复原秘密的调试转储；
- 构建机用户目录绝对路径；
- 未脱敏环境变量。

需要引用敏感产物时，只记录受控 artifact ID、SHA-256 和访问责任角色，不记录访问凭据。
