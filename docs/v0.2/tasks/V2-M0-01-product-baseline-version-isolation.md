---
id: V2-M0-01
title: "v0.2 产品基线与版本隔离"
milestone: V2-M0
status: planned
owner_role: /root
depends_on: []
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

建立与 v0.1 终态完全隔离的 v0.2 产品基线、开发 tuple、九任务 DAG 和治理门禁，使后续 Agent 可以开始 fresh release work，而不恢复或改写旧 M3-05/M4。

## Background

ADR 0019 固定 `STOP_CURRENT_V0_1_RELEASE_LINE`。旧 M3-05、M3-10、M3-14 和 M4 没有恢复路径；PR #63 与 PR #83 已关闭且未合并。用户已明确授权制定新版本化 baseline、tuple、ADR 和任务图。当前任务是治理和版本隔离任务，不实现产品或 benchmark。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523` 及 Git tree `1f8a4c387b4715443df31457fa498c7463b1f23f`。
- ADR 0019 与 M3-15 terminal evidence。
- 根 AGENTS、HandOff schema、治理验证器和既有任务格式。
- 用户授权的 task IDs、Issue #86–#94 和不变产品范围。

## Expected Outputs

- ADR 0020。
- `docs/v0.2/` 核心文档、development tuple、任务索引和九张任务卡。
- v0.2 专用治理验证器及旧终态 preservation 负例设计。
- 供 `/root` 把根 HandOff 从 terminal idle 转为 V2-M0-01 active 的可核验状态。

## In Scope

- v0.2 基线身份、版本隔离、证据复用规则、产品边界和候选失败规则。
- V2 task frontmatter、依赖、owner、接口、验收、测试和交接合同。
- development tuple 的 source trees、contracts、predecessor identity、canonicalization 和 hash 验证。
- 对旧 terminal commit 进行历史字节验证，同时允许 current HandOff 表达新版本状态的 successor-aware 设计。

## Out of Scope

- 修改 Host、Runtime、fixture、benchmark 或 distribution 实现。
- 启动 Android、KVM、emulator、设备、fuzz 或性能测量。
- 修改 ADR 0019 的决定、恢复旧 task/PR/Issue 或执行旧 workflow。
- 计算 release-candidate tuple 或作出 v0.2 发布声明。

## Implementation Decisions

- 新任务和核心文档位于 `docs/v0.2/`，不把 V2 task 混入锁定的 v0.1 索引。
- V2 task `depends_on` 只能引用 V2 ID；旧 main 只通过 `baseline_inputs` 引用。
- development tuple 采用用户固定字段。移除 `tuple_sha256` 后，对对象键递归字典排序、数组保序，以无空白 UTF-8 JSON 计算 SHA-256。
- development tuple 固定 `releasable=false` 和 `evidence_policy=fresh_release_evidence_required`；不能授权 device、performance 或 release PASS。
- 旧 terminal 文件的权威历史字节由 fixed baseline commit 证明；current 文档仍需语义检查 no-retry/no-M4，而不能通过删除旧文本规避。
- V2-M0-02 与 V2-M3-01 在本任务完成后可并行，文件所有权必须互斥。

## Public Interfaces

- `docs/v0.2/development-product-tuple.json`。
- `docs/v0.2/tasks/INDEX.md`。
- 治理命令 `node tools/governance/validate-v0-2-package.mjs`。
- 根 HandOff schema 2 的 `current_milestone` 允许值固定为 `V2-M0|V2-M1|V2-M2|V2-M3|V2-M4`，但只由 `/root` 修改。

## Security Constraints

- 任何旧 tuple、run、artifact、PR 或 task 状态不得变成 v0.2 PASS。
- 不删除或重写 v0.1 terminal Git history。
- 文档不得包含客户 APK、明文 DEX、签名秘密、设备序列号或用户绝对路径。
- 安全能力只表述为成本防御。

## Compatibility Requirements

- 维持 standalone APK、unsigned output、`minSdk >= 29` 和四 ABI Runtime。
- 不新增格式、框架或 API/ABI 兼容承诺。
- 历史 verified cells 只能作为 fresh V2 test planning input。

## Acceptance Criteria

- ADR、十份 v0.2 核心/策略文档、tuple、索引和九张任务卡均存在且内部链接有效。
- 九个 ID 和 Issue 唯一，依赖图无环，第一卡 `depends_on: []`，其余依赖只含 V2 ID。
- development tuple 的八个 source tree ID 可从 baseline commit 复算，规范 hash 等于文件记录值。
- 验证器拒绝旧 predecessor tuple 被标为 releasable、旧任务依赖、PR #63/#83 PASS、retry/replacement/M4 resume 和产品范围扩大。
- base-to-HEAD diff 仅含本任务授权治理路径，不含产品、benchmark 或 distribution 实现。
- 项目治理、严格 HandOff、UTF-8、link、敏感扫描、`git diff --check` 和 `git fsck --full` 通过。
- 独立只读复核返回 `P0=0/P1=0/P2=0`。

## Required Tests

- development tuple canonicalization/hash 正向、每个字段与 tree ID 的变异、数组换序、额外字段和错误 hash 负例；对象 key 换序必须规范化为同一 hash。
- V2 task ID、Issue、frontmatter、依赖闭环和正文标题完整性测试。
- old terminal commit ancestry、历史 hash、current no-retry/no-M4 文本测试。
- forbidden phrase/semantic mutation：retry、replacement、renewal、platform substitution、resume M3-05、start old M4、reuse as PASS。
- 治理-only changed-path 测试和敏感/绝对路径扫描。

## Required Evidence

- baseline commit/tree、每个 source tree ID 和 tuple hash 复算输出。
- 所有验证命令、退出码、OS、Node/JDK 版本、时间戳和 frozen commit。
- 任务数量、依赖图、内部链接和 changed-path manifest。
- 独立审查报告及全部发现处置。

## Likely Files

- `docs/adr/0020-v0-2-product-baseline-and-version-isolation.md`
- `docs/v0.2/`
- `tools/governance/validate-v0-2-package.mjs`
- `.github/workflows/governance.yml`
- `HandOff.md`，仅 `/root` 的协调提交

## Dependencies and Blockers

如果 baseline commit/tree 不匹配、旧 terminal state 不可复算、任务图需要引用旧 task、治理必须弱化 ADR 0019 或独立审查存在发现，本任务保持 blocked。不得通过修改旧 run/PR 状态或删除终态文本获得通过。

## Agent Handoff Requirements

使用分支 `docs/v2-m0-01-versioned-baseline`，只处理 Issue #86 并创建一个对应 PR。交接必须列出全部文件、tuple/hash 复算、DAG、命令、退出码、changed paths、独立审查和残余风险。根 HandOff 只由 `/root` 更新。
