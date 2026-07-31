---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260731-085400
updated_at: 2026-07-31T08:54:00+08:00
updated_by: /root
state: active
source_branch: fix/m0-02-governance-audit
base_commit: 101c2736eed032dae703272aaf1b3ee4a8f3e82a
working_tree: clean
current_milestone: M0
active_task: M0-02
next_owner: /root
---

# Project HandOff

## Objective

以只读独立 APK 为输入，开发一个离线后处理工具，将业务 DEX 转换为版本化认证加密容器并注入四 ABI Android Runtime，最终只生成新的未签名 APK。当前重新打开 M0-02，只修复独立治理复审发现的文本合同与交接状态阻塞；仓库仍不包含 Host 或 Android Runtime 业务实现。

## Current State

- M0-01 仓库种子位于 `main`，提交为 `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`。
- M0-02 初始包通过 [PR #26](https://github.com/xiaokh31/androidAppHardening/pull/26) 合并到 `142ecc5afc21123e9f05c60f09c4152de5094fae`；`main` 本地严格校验与 Windows/Ubuntu push CI 均通过。
- 合并后的独立语义审计发现七项高置信合同阻塞，因此 Issue #2 已重新打开，限定修复由 [PR #27](https://github.com/xiaokh31/androidAppHardening/pull/27) 跟踪。
- `e0a7860a6fb3fce12fc4ed69389948343b82055a` 修复 fixture 同 signer 顺序、最终 Runtime 等价性依赖、发布审查/打包边界、pre-CLI Skill 模式、完整性能指标、包外 Quickstart 输入和唯一 `SignerPolicyV1` 类型，并加入治理回归断言；该提交双平台 PR CI 均通过。
- 独立复审在 `e0a7860a6fb3fce12fc4ed69389948343b82055a` 又定位到两个语义缺口：M0-03 不应执行不存在的 fixture，以及 HandOff 仍描述提交前状态。`101c2736eed032dae703272aaf1b3ee4a8f3e82a` 已把 fixture 步骤改为按任务条件执行，本交接快照同时清除所有过期状态。
- M0-03 暂停分配，直到当前修复取得最新双平台 CI 与独立复审 `PASS`。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-02 | `/root` | `fix/m0-02-governance-audit` | in_progress | M0-01 | 取得当前冻结提交的双平台 CI 与独立复审 PASS |

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
- `142ecc5afc21123e9f05c60f09c4152de5094fae`：以普通 merge commit 合并 PR #26；随后 `main` 严格 HandOff 与双平台治理 CI 通过。
- `e0a7860a6fb3fce12fc4ed69389948343b82055a`：修复首轮独立审计的七项合同阻塞并新增对应治理回归断言，不实现 APK 加固业务代码。
- `101c2736eed032dae703272aaf1b3ee4a8f3e82a`：把 synthetic fixture 验证限定到提供或明确消费 fixture 的任务，明确 M0-03 记录 `not_applicable`，并为该规则加入自动回归断言。
- 当前交接快照：删除已完成动作和提交前工作树描述，准确记录 PR #27、冻结提交、验证证据与剩余解除条件。

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

### M0-02 audit-remediation immutable manifest

- task_id: `M0-02`
- git_commit: `101c2736eed032dae703272aaf1b3ee4a8f3e82a`
- command: `node tools/governance/hash-project-package.mjs --commit 101c2736eed032dae703272aaf1b3ee4a8f3e82a`
- exit_code: `0`
- environment: `Windows NT 10.0.19045; Git 2.52.0; Node.js 24.12.0`
- timestamp: `2026-07-31T08:54:00+08:00`
- artifact: `immutable commit-tree manifest over 80 files, excluding HandOff.md`
- sha256: `9a9039aa64b89c4e50394fbb83f42874063358868f6f2dd9787fbd0fe1dd5197`
- result: `PASS; manifest covers the complete governance-only package including both independent-audit remediation rounds`

### M0-02 conditional-fixture regression validation

- task_id: `M0-02`
- git_commit: `101c2736eed032dae703272aaf1b3ee4a8f3e82a`
- command: `validate-project-package.mjs --require-governance-only; quick_validate.py .agents/skills/validate-protected-apk; git diff --check`
- exit_code: `0`
- environment: `Windows NT 10.0.19045; Node.js 24.12.0; bundled Python 3.12.13; PyYAML 6.0.2 in ignored local validation directory`
- timestamp: `2026-07-31T08:53:00+08:00`
- artifact: `.agents/skills/validate-protected-apk/SKILL.md; tools/governance/validate-project-package.mjs`
- sha256: `9a9039aa64b89c4e50394fbb83f42874063358868f6f2dd9787fbd0fe1dd5197`
- result: `PASS; M0-03 cannot regress to unconditional fixture execution`

## Blockers and Required Approvals

独立治理复审在 `e0a7860a6fb3fce12fc4ed69389948343b82055a` 返回两个剩余阻塞。代码/Skill 缺口已由 `101c2736eed032dae703272aaf1b3ee4a8f3e82a` 修复，交接状态缺口由当前快照修复；解除条件是当前冻结提交通过 Windows/Ubuntu CI，并由同一独立审计 Agent 返回 `PASS`。

## Ordered Next Actions

1. 推送当前冻结提交并取得 Windows/Ubuntu PR CI `PASS`。
   - Owner: `/root`；前置条件：提交后 strict HandOff、项目治理、六个 Skill、负向测试和 Git 完整性检查全部通过；证据：命令、退出码、job 链接、提交与不可变包哈希。
2. 由同一独立审计 Agent 对当前冻结提交复审并取得 `PASS`。
   - Owner: `/root`；前置条件：双平台 PR CI 通过；证据：冻结提交、审计结论和 PR 评论链接。
3. 把 HandOff 恢复为 `ready`、活动任务恢复为 M0-03，以普通 merge commit 合并 PR #27，并关闭 Issue #2。
   - Owner: `/root`；前置条件：独立复审通过；证据：最终 HandOff、merge commit 和 main CI。

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

1. 确认位于 `fix/m0-02-governance-audit`、包含 `101c2736eed032dae703272aaf1b3ee4a8f3e82a` 且工作树为 clean。
2. 按固定阅读顺序复核 Issue #2、PR #27 和两轮独立审计发现。
3. 运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
4. 运行 `node tools/governance/validate-project-package.mjs --require-governance-only`、`node tools/governance/test-handoff-validator.mjs` 与六个 Skill 的结构校验。
5. 等待双平台 CI，并仅在同一独立审计 Agent 对当前冻结提交返回 `PASS` 后恢复 M0-03 入口；不得绕过审计或开始业务实现。

## Handoff Sign-off

- generated_by: `/root`
- generated_at: `2026-07-31T08:54:00+08:00`
- validation_command: `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`
- validation_result: `PASS after commit; independent review remains pending`
