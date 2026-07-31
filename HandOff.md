---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260731-082534
updated_at: 2026-07-31T08:25:34+08:00
updated_by: /root
state: ready
source_branch: main
base_commit: 467f491f7356f88aeede451701ac6093768d36fd
working_tree: clean
current_milestone: M0
active_task: M0-03
next_owner: unassigned
---

# Project HandOff

## Objective

以只读独立 APK 为输入，开发一个离线后处理工具，将业务 DEX 转换为版本化认证加密容器并注入四 ABI Android Runtime，最终只生成新的未签名 APK。M0-01 与 M0-02 已完成；下一项是 M0-03 工具链与 CI，仓库仍不包含 Host 或 Android Runtime 业务实现。

## Current State

- M0-01 仓库种子位于 `main`，提交为 `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`。
- M0-02 通过 [PR #26](https://github.com/xiaokh31/androidAppHardening/pull/26) 合并：包含 11 份核心文档、6 份 ADR、25 张任务卡、6 个项目 Skills、严格 HandOff 工具和双平台治理工作流。
- GitHub 已建立 5 个 Milestone、14 个项目 Label 和与任务索引一一对应的 25 个 Issue；任务卡保留技术权威，Issue 仅跟踪负责人、状态、讨论和 PR。
- M0-03 已成为活动任务但尚未分配；M0-04 按已批准顺序在 M0-03 门禁通过后启动，M0-05 仍受 M0-04 门禁约束。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-03 | `unassigned` | `feat/m0-03-toolchain-gradle-ci` | planned | M0-02 | 分配 owner 并建立十四模块工具链骨架 |
| M0-04 | `unassigned` | `spike/m0-04-classloader-poc` | planned | M0-03 | M0-03 门禁满足后验证 API 29/36 公开加载链 |
| M0-05 | `unassigned` | `spike/m0-05-application-factory-provider-jni-poc` | planned | M0-04 | M0-04 通过后验证早期 metadata、签名与 JNI 兼容性 |

## Decisions and Invariants

- [ADR-0001](docs/adr/0001-apk-postprocessing-only.md)：仅处理已签名的独立 APK；输入始终只读。
- [ADR-0002](docs/adr/0002-unsigned-output-only.md)：输出始终是新的未签名 APK；产品不接收或使用私钥、keystore、alias 或密码。
- [ADR-0003](docs/adr/0003-api29-public-classloader-hook.md)：v0.1 要求 `minSdk >= 29`，只采用公开 `AppComponentFactory` ClassLoader 接入。
- [ADR-0004](docs/adr/0004-versioned-encrypted-dex-container.md)：AHDC v1 采用固定字节合同、zlib-wrapped DEFLATE level 9 与 AES-256-GCM。
- [ADR-0005](docs/adr/0005-runtime-abi-policy.md)：Runtime 覆盖 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，但不把 ARM-only 应用转换为 x86 应用。
- [ADR-0006](docs/adr/0006-offline-key-protection-boundary.md)：离线密钥隐藏、内存截取控制、反调试和环境检测只提高攻击成本，不能构成绝对防御。
- “APK 大小优化”只表示测量并控制加固增量，不保证输出小于输入。

## Changes Since Previous Handoff

- `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`：建立 Apache-2.0 公共仓库种子和基础文本规范。
- `1e5264e208ac37f3b8bd5331f59ed5a6d5eef78b`：加入核心策划文档、ADR、25 张任务卡、Agent 角色规则和 GitHub 模板。
- `cb491af037ce1f9597b7792dc66b5f8f383187dc`：加入 6 个项目 Skills、严格 HandOff 校验及 11 个负向测试、项目包校验、不可变提交树哈希工具和双平台治理工作流，并冻结启动早期公开 API、模块边界、无环依赖图与 AHDC v1 二进制协议合同。
- `467f491f7356f88aeede451701ac6093768d36fd`：把 `actions/checkout` 与 `actions/setup-node` 更新为官方 v7 的完整固定提交，工作流 Action 运行时统一为 Node 24。
- GitHub 同步完成：5 个 Milestone、14 个 Label、25 个同编号 Issue、PR #26 的治理标签与双平台 CI 证据均已建立。

## Verification Evidence

### M0-01 immutable seed manifest

- task_id: `M0-01`
- git_commit: `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`
- command: `node tools/governance/hash-project-package.mjs --commit 1fc5fb6380b97ba2a2a54df0409429f4730f6d77`
- exit_code: `0`
- environment: `Windows NT 10.0.19045; Git 2.52.0; Node.js 24.12.0`
- timestamp: `2026-07-31T08:08:37+08:00`
- artifact: `immutable commit-tree manifest over 5 files, excluding HandOff.md`
- sha256: `2db831de0981c715a62df6007d8c2c9219069940d5f951950a6f5b5ca41b977f`
- result: `PASS; the root commit contains only README.md, LICENSE, .gitignore, .gitattributes, and .editorconfig`

### M0-02 immutable governance manifest

- task_id: `M0-02`
- git_commit: `467f491f7356f88aeede451701ac6093768d36fd`
- command: `node tools/governance/hash-project-package.mjs --commit 467f491f7356f88aeede451701ac6093768d36fd`
- exit_code: `0`
- environment: `Windows NT 10.0.19045; Git 2.52.0; Node.js 24.12.0`
- timestamp: `2026-07-31T08:25:06+08:00`
- artifact: `immutable commit-tree manifest over 80 files, excluding HandOff.md`
- sha256: `4fa64772061314da20248dd28e1223bad6c1284bda1e461cd103953ae49c0eb8`
- result: `PASS; manifest covers the complete governance-only package at the evidenced commit`

### M0-02 local governance and Skill validation

- task_id: `M0-02`
- git_commit: `467f491f7356f88aeede451701ac6093768d36fd`
- command: `validate-handoff.mjs HandOff.md --strict; test-handoff-validator.mjs; validate-project-package.mjs --require-governance-only; quick_validate.py for each .agents/skills directory`
- exit_code: `0`
- environment: `Windows NT 10.0.19045; Git 2.52.0; Node.js 24.12.0; bundled Python and skill-creator validator`
- timestamp: `2026-07-31T08:25:34+08:00`
- artifact: `25 task cards; 11 core documents; 6 ADRs; 6 valid Skills; 11 negative HandOff cases`
- sha256: `4fa64772061314da20248dd28e1223bad6c1284bda1e461cd103953ae49c0eb8`
- result: `PASS; task IDs and links are complete, dependencies are acyclic, and the repository remains governance-only`

### M0-02 Ubuntu governance CI

- task_id: `M0-02`
- git_commit: `467f491f7356f88aeede451701ac6093768d36fd`
- command: `GitHub Actions Governance / Governance (ubuntu-24.04)`
- exit_code: `0`
- environment: `GitHub-hosted ubuntu-24.04; Node.js 24.12.0`
- timestamp: `2026-07-31T00:24:33Z`
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30593439005/job/91040491406`
- sha256: `4fa64772061314da20248dd28e1223bad6c1284bda1e461cd103953ae49c0eb8`
- result: `PASS; syntax, governance-only snapshot, pending-main HandOff, negative tests, and Git object database verified`

### M0-02 Windows governance CI

- task_id: `M0-02`
- git_commit: `467f491f7356f88aeede451701ac6093768d36fd`
- command: `GitHub Actions Governance / Governance (windows-2025)`
- exit_code: `0`
- environment: `GitHub-hosted windows-2025; Node.js 24.12.0`
- timestamp: `2026-07-31T00:25:05Z`
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30593439005/job/91040491344`
- sha256: `4fa64772061314da20248dd28e1223bad6c1284bda1e461cd103953ae49c0eb8`
- result: `PASS; syntax, governance-only snapshot, pending-main HandOff, negative tests, and Git object database verified`

## Blockers and Required Approvals

None

## Ordered Next Actions

1. 分配并完成 M0-03 工具链与 CI。
   - Owner: `unassigned`；前置条件：PR #26 合并且 M0-02 验证完成；证据：双平台构建命令、退出码、工具版本和产物 SHA-256。
2. 并行启动 M0-04 ClassLoader PoC。
   - Owner: `unassigned`；前置条件：M0-03 通过；证据：API 29/36 设备测试、失败用例和 PoC 产物 SHA-256。
3. M0-04 通过后启动 M0-05 兼容性 PoC。
   - Owner: `unassigned`；前置条件：M0-04 公开 API 门禁通过；证据：Application、Factory、Provider、multidex 与 JNI 兼容矩阵。
4. M0 门禁通过后冻结容器接口，再并行启动 M1 与 M2。
   - Owner: `/root`；前置条件：M0-03 至 M0-05 验收；证据：冻结合同、关联 ADR 和独立安全复核记录。

## Relevant Files and Artifacts

- `AGENTS.md`
- `HandOff.md`
- `docs/README_FIRST.md`
- `docs/ARCHITECTURE.md`
- `docs/THREAT_MODEL.md`
- `docs/HANDOFF_SPEC.md`
- `docs/tasks/INDEX.md`
- `.agents/skills/coordinate-project-handoff/SKILL.md`
- `.agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs`
- `.github/workflows/governance.yml`
- `tools/governance/validate-project-package.mjs`
- `tools/governance/hash-project-package.mjs`
- `tools/governance/test-handoff-validator.mjs`

## Resume Checklist

1. 确认位于 `main`、HEAD 包含 PR #26 的 merge commit 且工作树为 clean。
2. 按 `AGENTS.md` → `HandOff.md` → `docs/README_FIRST.md` → `docs/tasks/M0-03-toolchain-gradle-ci.md` → 相关架构与 ADR → 项目 Skill 的顺序阅读。
3. 运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
4. 运行 `node tools/governance/validate-project-package.mjs --require-governance-only` 与 `node tools/governance/test-handoff-validator.mjs`。
5. 在 Issue #3 分配唯一 owner 后创建 `feat/m0-03-toolchain-gradle-ci`，不得在该任务中实现 Host 或 Runtime 业务能力。

## Handoff Sign-off

- generated_by: `/root`
- generated_at: `2026-07-31T08:25:34+08:00`
- validation_command: `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-branch`
- validation_result: `PASS on PR target; strict validation required again on main`
