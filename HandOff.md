---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260731-122012
updated_at: 2026-07-31T12:20:12+08:00
updated_by: /root
state: blocked
source_branch: spike/m0-04-classloader-poc
base_commit: 1fc855c71bbd16141b419b928ffdad4f998ad3d6
working_tree: clean
current_milestone: M0
active_task: M0-04
next_owner: runtime-security-agent
---

# Project HandOff

## Objective

以只读独立 APK 为输入，开发一个离线后处理工具，将业务 DEX 转换为版本化认证加密容器并注入四 ABI Android Runtime，最终只生成新的未签名 APK。M0-01、M0-02 与 M0-03 的仓库治理和跨平台工具链基线已完成验证；当前只执行 M0-04 ClassLoader PoC，用 API 29+ 公开入口验证内存 DEX 加载可行性，不提前实现 M0-05 或可发布 Runtime。

## Current State

- M0-01 仓库种子位于 `main`，提交为 `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`。
- M0-02 初始包通过 [PR #26](https://github.com/xiaokh31/androidAppHardening/pull/26) 合并到 `142ecc5afc21123e9f05c60f09c4152de5094fae`；`main` 本地严格校验与 Windows/Ubuntu push CI 均通过。
- 合并后的独立语义审计发现七项高置信合同阻塞，因此 Issue #2 重新打开，限定修复由 [PR #27](https://github.com/xiaokh31/androidAppHardening/pull/27) 跟踪。
- `e0a7860a6fb3fce12fc4ed69389948343b82055a` 修复 fixture 同 signer 顺序、最终 Runtime 等价性依赖、发布审查/打包边界、pre-CLI Skill 模式、完整性能指标、包外 Quickstart 输入和唯一 `SignerPolicyV1` 类型，并加入治理回归断言；该提交双平台 PR CI 均通过。
- 独立复审在 `e0a7860a6fb3fce12fc4ed69389948343b82055a` 又定位到两个语义缺口；`101c2736eed032dae703272aaf1b3ee4a8f3e82a` 与 `cc09d8c11835391085ee6db57ee92a96ec1bece7` 已分别修复条件化 fixture 验证和提交态 HandOff。
- PR #27 已通过普通 merge commit `44fb8811a3f7639d9e2a57fd8b028107ecf2a2a0` 合并到 `main`。
- GitHub 已建立 5 个里程碑、14 个计划自定义标签和与 25 张任务卡一一对应的 Issues；`main` 分支保护要求两项严格治理检查和 PR 流程。
- M0-03 固定分支 `feat/m0-03-toolchain-gradle-ci` 的唯一 [PR #28](https://github.com/xiaokh31/androidAppHardening/pull/28) 已通过普通 merge commit `978d357a8f0203ee90ebcfff6ede64c09bf6135e` 合并；Issue #3 已关闭。
- 合并后的 `main` 已无 `--allow-pending-branch` 或 `--allow-pending-clean` 通过 strict HandOff、项目治理、工具链和 diff 校验；push 触发的 Windows/Ubuntu Build 与 Governance 也全部成功。
- M0-04 已由 `runtime-security-agent` 在规定分支 `spike/m0-04-classloader-poc` 启动，唯一跟踪项为 [Issue #4](https://github.com/xiaokh31/androidAppHardening/issues/4)。GitHub App 对添加 assignee 返回 `403 Resource not accessible by integration`，因此 Issue 页面 assignee 暂为空，但根 HandOff 已记录实际所有权。
- `1fc855c71bbd16141b419b928ffdad4f998ad3d6` 已实现隔离 `classloaderPoc` flavor、公开 `AppComponentFactory`/`InMemoryDexClassLoader` PoC、固定 STORED payload DEX、稳定 `AAH-P001` 失败路径、instrumentation 与静态/冷启动验证器；未实现 M0-05 范围。
- 固定 JDK 的全仓构建和 API 34 x86_64 非 root AVD 烟测已通过，包含 1/1 instrumentation、3 个负向 payload、20/20 冷启动、零禁止日志和零明文落盘命中；API 34 仅为非验收证据。
- 本地 Android SDK 不含任务卡要求的 API 29 与 API 36 x86_64 system image，仓库也未固定可下载的 system-image 包版本、校验值和来源。依照工具链来源规则未下载任意最新镜像，因此 M0-04 正式 gate 保持 blocked，M0-05 不得启动。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-03 | `qa-governance-agent` | `feat/m0-03-toolchain-gradle-ci` | done | M0-02 | PR #28、合并后 strict HandOff 与 push CI 证据完整 |
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | blocked | M0-03 | 固定并提供 API 29/36 x86_64 image 后补跑两套正式设备门禁 |
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
- `978d357a8f0203ee90ebcfff6ede64c09bf6135e`：以普通 merge commit 合并 PR #28；合并后的 `main` 无豁免通过 strict HandOff、本地治理和工具链校验，push Build 与 Governance 全部成功。
- 从上述已验证 `main` 创建 `spike/m0-04-classloader-poc`，按 Issue #4 和任务卡启动 M0-04；未启动 M0-05。
- `1fc855c71bbd16141b419b928ffdad4f998ad3d6`：实现 M0-04 的公开 ClassLoader PoC、隔离 fixture flavor、构建期单 DEX 生成、失败关闭、instrumentation、静态 APK 合同与冷启动验证脚本；把 M0-03 空源码边界保留为显式历史校验模式，默认永久校验继续检查固定工具链和十四模块图。

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

### M0-03 merged-main strict validation

- task_id: `M0-03`
- git_commit: `978d357a8f0203ee90ebcfff6ede64c09bf6135e`
- command: `validate-handoff.mjs HandOff.md --strict; validate-project-package.mjs; verify-m0-toolchain.mjs; git diff --check; git merge-base --is-ancestor cd182b0e762eda914ca297a5f9fdeada13dad6a9 HEAD`
- exit_code: `0`
- environment: `Windows NT 10.0.19045 x64; Git 2.52.0; Node.js 24.12.0; main at merge commit with clean working tree`
- timestamp: `2026-07-31T11:28:00+08:00`
- artifact: `https://github.com/xiaokh31/androidAppHardening/commit/978d357a8f0203ee90ebcfff6ede64c09bf6135e`
- sha256: `dadf51d8438cde27ecf9a3d951eb4d7efee7c42a8d80a3185cb9b70b401300d3`
- result: `PASS; strict HandOff ran on main without pending exemptions and the merge contains the final M0-03 head`

### M0-03 merged-main push CI

- task_id: `M0-03`
- git_commit: `978d357a8f0203ee90ebcfff6ede64c09bf6135e`
- command: `GitHub Actions Build run 30601930828; Governance run 30601930798`
- exit_code: `0`
- environment: `GitHub-hosted ubuntu-24.04 and windows-2025 x64`
- timestamp: `2026-07-31T03:39:00Z`
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30601930828`
- sha256: `dadf51d8438cde27ecf9a3d951eb4d7efee7c42a8d80a3185cb9b70b401300d3`
- result: `PASS; merged-main Build and Governance jobs succeeded on Ubuntu and Windows`

### M0-04 local Windows build and API 34 smoke

- task_id: `M0-04`
- git_commit: `1fc855c71bbd16141b419b928ffdad4f998ad3d6`
- command: `gradlew.bat --no-daemon check lint verifyGovernance :runtime:native:assemble :fixtures:android:assembleClassloaderPocDebugAndroidTest; connectedClassloaderPocDebugAndroidTest; verify-m0-04-apk.mjs; run-m0-04-cold-start.mjs --iterations 20`
- exit_code: `0`
- environment: `Windows 10 10.0 amd64; Temurin JDK 17.0.19+10; Gradle 9.5.0; Node.js 24.12.0; API 34 x86_64 AVD; shell uid 2000`
- timestamp: `2026-07-31T12:18:09+08:00`
- artifact: `fixtures/android/build/outputs/apk/classloaderPoc/debug/android-classloaderPoc-debug.apk; detailed report docs/evidence/M0-04/local-windows-api34-smoke.md`
- sha256: `4c0dc83351511de4728baa27c36858f5cd98f2faa7d0fb389fd3857d278bba7c`
- result: `PASS smoke only; APK hash is recorded above, payload DEX 77fdfdb6e35a0f09747c09c28b245a289cdc5126af0c7e2a719581548318cda1 and test APK 069ce8488eb842361821488085d376da58517f13f683a827f17bd6431e5a846d; 1/1 instrumentation, required event order, InMemoryDexClassLoader identity, three AAH-P001 negative paths, 20/20 cold starts, zero forbidden logs and files`

### M0-04 formal API matrix gate

- task_id: `M0-04`
- git_commit: `1fc855c71bbd16141b419b928ffdad4f998ad3d6`
- command: `inventory installed SDK system images; compare with task-required API 29 and API 36 x86_64 emulators`
- exit_code: `1`
- environment: `local Android SDK contains API 30, 33, 34 and 35 images; required API 29 and API 36 images absent`
- timestamp: `2026-07-31T12:18:09+08:00`
- artifact: `docs/evidence/M0-04/local-windows-api34-smoke.md`
- sha256: `not_applicable`
- result: `BLOCKED; no acceptance APK was executed on either required API because the repository does not pin an allowed API 29/36 system-image revision, checksum and source, so no unpinned download was performed`

## Blockers and Required Approvals

- Impact: M0-04 cannot receive a formal `PASS`, and dependent M0-05, M1 and M2 work must not start.
- Verified fact: the local SDK has no API 29 or API 36 x86_64 system image; the available API 34 smoke cannot substitute for either required acceptance row.
- Required owner/action: the project coordinator must pin approved `system-images;android-29;...;x86_64` and `system-images;android-36;...;x86_64` package revisions, SHA-256 values and official source, then provision both images.
- Observable unblock condition: on each unrooted image, `connectedClassloaderPocDebugAndroidTest` exits `0`, the public loader and three negative paths pass, and 20/20 cold starts show zero forbidden logs and zero forbidden files with complete environment and artifact hashes.

## Ordered Next Actions

1. 项目协调者固定并提供 API 29 与 API 36 x86_64 system-image 包版本、SHA-256 和官方来源，不以未锁定下载绕过来源治理。
2. 在两个未 root AVD 上分别运行固定 instrumentation 入口和 20 次冷启动，记录 API、ABI、fingerprint、非 root 状态、XML、日志、文件系统差异与 SHA-256。
3. 两个正式 API 行全部通过后，将 M0-04 gate 改为 `PASS`；任一公开 API 核心条件失败则保留 blocked 最小复现，不改用 hidden API 或明文落盘。
4. 只有 M0-04 唯一 PR 合并后才分配 M0-05；此前不实现 Provider、JNI、多 DEX 或原始自定义 factory。

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
- `docs/evidence/M0-04/local-windows-api34-smoke.md`
- `docs/tasks/M0-04-api29-classloader-poc.md`
- `docs/adr/0003-api29-public-classloader-hook.md`
- `fixtures/android/build.gradle.kts`
- `fixtures/android/src/classloaderPoc/`
- `fixtures/android/src/classloaderPocPayload/`
- `fixtures/android/src/androidTestClassloaderPoc/`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/`
- `tools/validation/verify-m0-04-apk.mjs`
- `tools/validation/run-m0-04-cold-start.mjs`

## Resume Checklist

1. 确认分支为 `spike/m0-04-classloader-poc`、base commit 是当前 HEAD 的祖先，工作树只包含 M0-04 文件。
2. 阅读 `docs/evidence/M0-04/local-windows-api34-smoke.md`；API 34 是非验收烟测，不得改写为正式 gate 通过。
3. 在下载前核对协调者固定的 API 29/36 system-image 完整包名、版本、SHA-256 和官方来源；缺少任一项继续 blocked。
4. 在两个要求的 x86_64 AVD 上复用现有静态、instrumentation 和冷启动入口，补充独立测试 XML、环境、文件系统差异、日志扫描和 SHA-256。
5. 只有全部 M0-04 gate 通过时才将 HandOff 标为 ready；公开 API 路径失败则提交 blocked 证据，不以 hidden API 或明文落盘绕过。

## Handoff Sign-off

- generated_by: `/root`
- generated_at: `2026-07-31T12:20:12+08:00`
- validation_command: `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`
- validation_result: `PASS after commit; blocked state and required API 29/36 acceptance condition validated without exemptions`
