---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260731-112236
updated_at: 2026-07-31T11:22:36+08:00
updated_by: /root
state: ready
source_branch: main
base_commit: cd182b0e762eda914ca297a5f9fdeada13dad6a9
working_tree: clean
current_milestone: M0
active_task: M0-04
next_owner: unassigned
---

# Project HandOff

## Objective

以只读独立 APK 为输入，开发一个离线后处理工具，将业务 DEX 转换为版本化认证加密容器并注入四 ABI Android Runtime，最终只生成新的未签名 APK。M0-01、M0-02 与 M0-03 的仓库治理和跨平台工具链基线已完成验证；PR #28 合并后的下一入口是 M0-04 ClassLoader PoC，仓库仍不包含 Host 或 Android Runtime 业务实现。

## Current State

- M0-01 仓库种子位于 `main`，提交为 `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`。
- M0-02 初始包通过 [PR #26](https://github.com/xiaokh31/androidAppHardening/pull/26) 合并到 `142ecc5afc21123e9f05c60f09c4152de5094fae`；`main` 本地严格校验与 Windows/Ubuntu push CI 均通过。
- 合并后的独立语义审计发现七项高置信合同阻塞，因此 Issue #2 重新打开，限定修复由 [PR #27](https://github.com/xiaokh31/androidAppHardening/pull/27) 跟踪。
- `e0a7860a6fb3fce12fc4ed69389948343b82055a` 修复 fixture 同 signer 顺序、最终 Runtime 等价性依赖、发布审查/打包边界、pre-CLI Skill 模式、完整性能指标、包外 Quickstart 输入和唯一 `SignerPolicyV1` 类型，并加入治理回归断言；该提交双平台 PR CI 均通过。
- 独立复审在 `e0a7860a6fb3fce12fc4ed69389948343b82055a` 又定位到两个语义缺口；`101c2736eed032dae703272aaf1b3ee4a8f3e82a` 与 `cc09d8c11835391085ee6db57ee92a96ec1bece7` 已分别修复条件化 fixture 验证和提交态 HandOff。
- PR #27 已通过普通 merge commit `44fb8811a3f7639d9e2a57fd8b028107ecf2a2a0` 合并到 `main`。
- GitHub 已建立 5 个里程碑、14 个计划自定义标签和与 25 张任务卡一一对应的 Issues；`main` 分支保护要求两项严格治理检查和 PR 流程。
- M0-03 固定分支 `feat/m0-03-toolchain-gradle-ci` 由唯一草稿 [PR #28](https://github.com/xiaokh31/androidAppHardening/pull/28) 跟踪；实现、跨平台修复、本地证据和 CI 证据均已提交。
- PR #28 在 `cd182b0e762eda914ca297a5f9fdeada13dad6a9` 上的 Windows/Ubuntu Build 与 Governance 四项门禁全部成功；本快照只准备合并后的 `main` 恢复状态，不预称 PR 已合并。
- M0-04 尚未分配，必须在 PR #28 合并并由 `main` 无豁免通过 strict HandOff 后领取。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-03 | `qa-governance-agent` | `feat/m0-03-toolchain-gradle-ci` | done | M0-02 | 合并唯一 PR #28，并在 `main` 复验 strict HandOff |
| M0-04 | `unassigned` | `spike/m0-04-api29-classloader-poc` | planned | M0-03 | PR #28 合并且 `main` 严格校验通过后启动 ClassLoader PoC |
| M0-05 | `unassigned` | `spike/m0-05-application-factory-provider-jni-poc` | planned | M0-04 | M0-04 通过后启动兼容性 PoC |

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
- `cc09d8c11835391085ee6db57ee92a96ec1bece7`：删除已完成动作和提交前工作树描述，准确记录 PR #27、冻结提交及复审解除条件。
- `44fb8811a3f7639d9e2a57fd8b028107ecf2a2a0`：以普通 merge commit 合并 PR #27，完成 M0-02 治理审计修复。
- `305a60898e04d5ab631534705d7b37c2f533021d`：建立锁定工具链、十四模块空骨架、严格依赖验证、四 ABI 空库构建和双平台 CI；删除治理工作流的纯文本快照限制，同时保留永久治理校验。
- `8fec1a57f3f10f9bcf5a83e2163618db22eac735`：记录 M0-03 本地 Windows、离线、依赖篡改与四 ABI 架构证据，并更新活动 HandOff。
- `92d0650353c370be1f5d5b43d5a39279a5398ee4`：改用 Android SDK 内固定 `sdkmanager` 路径，并使提交态 HandOff 声明 post-commit clean 工作树。
- `f755296251f42cd40f3a0f77f1dd31340317c92f`：将 Unix Gradle Wrapper 入口的 Git 模式从 `100644` 修正为 `100755`，脚本内容不变。
- `cd182b0e762eda914ca297a5f9fdeada13dad6a9`：为官方 Google Maven Linux `aapt2` artifact 补充重复下载核验的 SHA-256，保持严格依赖校验。
- 当前 merger-ready 快照：PR #28 的 Windows/Ubuntu Build 与 Governance 四项门禁全部成功；详细 CI 证据位于 `docs/evidence/M0-03/ci-pr-28.md`。

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

### M0-02 remediation Ubuntu governance CI

- task_id: `M0-02`
- git_commit: `cc09d8c11835391085ee6db57ee92a96ec1bece7`
- command: `GitHub Actions Governance / Governance (ubuntu-24.04)`
- exit_code: `0`
- environment: `GitHub-hosted ubuntu-24.04; Node.js 24.12.0`
- timestamp: `2026-07-31T00:55:56Z`
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30594933052/job/91045144692`
- sha256: `9a9039aa64b89c4e50394fbb83f42874063358868f6f2dd9787fbd0fe1dd5197`
- result: `PASS; strict HandOff, governance-only package, negative tests, sensitive scan and Git object database verified`

### M0-02 remediation Windows governance CI

- task_id: `M0-02`
- git_commit: `cc09d8c11835391085ee6db57ee92a96ec1bece7`
- command: `GitHub Actions Governance / Governance (windows-2025)`
- exit_code: `0`
- environment: `GitHub-hosted windows-2025; Node.js 24.12.0`
- timestamp: `2026-07-31T00:56:12Z`
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30594933052/job/91045144698`
- sha256: `9a9039aa64b89c4e50394fbb83f42874063358868f6f2dd9787fbd0fe1dd5197`
- result: `PASS; strict HandOff, governance-only package, negative tests, sensitive scan and Git object database verified`

### M0-02 independent governance audit

- task_id: `M0-02`
- git_commit: `cc09d8c11835391085ee6db57ee92a96ec1bece7`
- command: `independent read-only review; strict HandOff; project package; 11 negative cases; six Skill validators; git diff/fsck`
- exit_code: `0`
- environment: `independent governance audit Agent; read-only frozen commit; Windows workspace plus GitHub-hosted Windows/Ubuntu CI evidence`
- timestamp: `2026-07-31T09:02:10+08:00`
- artifact: `https://github.com/xiaokh31/androidAppHardening/pull/27#issuecomment-5137955553`
- sha256: `9a9039aa64b89c4e50394fbb83f42874063358868f6f2dd9787fbd0fe1dd5197`
- result: `PASS; all nine findings across the independent audit rounds are resolved`

### GitHub governance state verification

- task_id: `M0-02`
- git_commit: `cc09d8c11835391085ee6db57ee92a96ec1bece7`
- command: `GitHub repository, protection, milestone, label, Issue and PR API read-only verification`
- exit_code: `0`
- environment: `GitHub public API plus authenticated GitHub CLI`
- timestamp: `2026-07-31T09:02:20+08:00`
- artifact: `https://github.com/xiaokh31/androidAppHardening`
- sha256: `9a9039aa64b89c4e50394fbb83f42874063358868f6f2dd9787fbd0fe1dd5197`
- result: `PASS; public Apache-2.0 repository, five milestones, fourteen custom labels, twenty-five indexed Issues, merge-only policy and strict protected-main checks match the project package`

### M0-03 local Windows and offline validation

- task_id: `M0-03`
- git_commit: `305a60898e04d5ab631534705d7b37c2f533021d`
- command: `.\gradlew.bat --no-daemon projects; .\gradlew.bat --no-daemon clean check lint verifyGovernance :runtime:native:assemble; repeat full command with --offline; test-dependency-verification.mjs; llvm-readobj --file-headers for four stripped debug libraries`
- exit_code: `0`
- environment: `Windows NT 10.0.19045 x64; Temurin JDK 17.0.19+10; Gradle 9.5.0; Node.js 24.12.0; Android Platform 36; Build Tools 36.1.0; NDK 29.0.14206865; CMake 4.1.2`
- timestamp: `2026-07-31T10:16:15+08:00`
- artifact: `docs/evidence/M0-03/local-windows.md; immutable commit-tree manifest over 130 files excluding HandOff.md`
- sha256: `ab4dadcc7b9a5e2ca120f1a97d11df43b59ba7de3555163ccc5ea3bbdc70b8fa`
- result: `PASS; fourteen required modules, strict dependency verification including tamper failure, permanent governance checks, offline rebuild and four architecture-matched empty libraries verified; fixture validation is not_applicable`

### M0-03 cross-platform Build CI

- task_id: `M0-03`
- git_commit: `cd182b0e762eda914ca297a5f9fdeada13dad6a9`
- command: `GitHub Actions Build / Build (ubuntu-24.04); GitHub Actions Build / Build (windows-2025)`
- exit_code: `0`
- environment: `GitHub-hosted ubuntu-24.04 and windows-2025 x64; Temurin JDK 17.0.19+10; Gradle 9.5.0; Node.js 24.12.0; Android Platform 36; Build Tools 36.1.0; NDK 29.0.14206865; CMake 4.1.2`
- timestamp: `2026-07-31T03:20:58Z`
- artifact: `docs/evidence/M0-03/ci-pr-28.md; Build jobs 91064122142 and 91064122076`
- sha256: `18c63310289a6c03781250430188da0bcb90bab7b106adf6c38992708ad3c4d1`
- result: `PASS; Linux full checks, dependency-verification tamper failure, four ABI ELF inspection and Windows clean checks all succeeded on the unique PR`

### M0-03 cross-platform Governance CI

- task_id: `M0-03`
- git_commit: `cd182b0e762eda914ca297a5f9fdeada13dad6a9`
- command: `GitHub Actions Governance / Governance (ubuntu-24.04); GitHub Actions Governance / Governance (windows-2025)`
- exit_code: `0`
- environment: `GitHub-hosted ubuntu-24.04 and windows-2025 x64; Node.js 24.12.0`
- timestamp: `2026-07-31T03:18:28Z`
- artifact: `docs/evidence/M0-03/ci-pr-28.md; Governance jobs 91064122122 and 91064122127`
- sha256: `18c63310289a6c03781250430188da0bcb90bab7b106adf6c38992708ad3c4d1`
- result: `PASS; permanent project package validation, pending-main HandOff, negative cases, sensitive scan and Git object verification succeeded on both platforms`

## Blockers and Required Approvals

None

## Ordered Next Actions

1. 由 `/root` 或仓库维护者在四项门禁保持成功时合并 PR #28；合并后的 `main` 必须无豁免通过 strict HandOff，证据为合并提交和 push CI 链接。
2. M0-04 由 `unassigned` 在上述合并与严格复验后领取 ClassLoader PoC，证据为同编号 Issue、`spike/m0-04-api29-classloader-poc` 分支和唯一 PR。
3. M0-04 通过后由 `unassigned` 启动 M0-05 兼容性 PoC。
4. M0 门禁通过后冻结容器接口，再并行启动 M1 与 M2。

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
- `.github/workflows/build.yml`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle/verification-metadata.xml`
- `gradle/wrapper/`
- `tools/validation/`
- `tools/governance/validate-project-package.mjs`
- `tools/governance/hash-project-package.mjs`
- `tools/governance/test-handoff-validator.mjs`
- `docs/evidence/M0-03/local-windows.md`
- `docs/evidence/M0-03/ci-pr-28.md`

## Resume Checklist

1. 确认 PR #28 仍以 `main` 为 base、四项 required checks 成功且没有第二个 M0-03 PR。
2. 合并 PR #28 后检出 `main`，确认工作树 clean、合并提交包含 `cd182b0e762eda914ca297a5f9fdeada13dad6a9`。
3. 在 `main` 无 `--allow-pending-branch` 或 `--allow-pending-clean` 运行 strict HandOff 与项目治理校验。
4. 核对 `docs/evidence/M0-03/local-windows.md` 与 `docs/evidence/M0-03/ci-pr-28.md` 的提交、CI 链接和 SHA-256。
5. 关闭 Issue #3 后，按依赖图分配 M0-04，不提前实现 M0-05。

## Handoff Sign-off

- generated_by: `/root`
- generated_at: `2026-07-31T11:22:36+08:00`
- validation_command: `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean --allow-pending-branch`
- validation_result: `PASS for merger-ready M0-03 snapshot targeting main with post-commit clean worktree`
