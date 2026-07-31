---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260801-004618
updated_at: 2026-08-01T00:46:18+08:00
updated_by: /root
state: active
source_branch: spike/m0-05-application-factory-provider-jni-poc
base_commit: 43e10c38569dfdd64bc41d688d23d23e005906fb
working_tree: clean
current_milestone: M0
active_task: M0-05
next_owner: runtime-security-agent
---

# Project HandOff

## Objective

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的边界内推进离线 APK 加固工具。M0-05 已从通过 strict HandOff 和双平台 CI 的最新 `main` 领取，仅验证原始 Factory、Provider、JNI、早期 signer/typed metadata 与兼容性路径。

## Current State

- M0-04 的唯一 [PR #29](https://github.com/xiaokh31/androidAppHardening/pull/29) 已于 `2026-08-01T00:30:34+08:00` 以普通 merge commit `09c654b36a3ec19225926521dca5127ffef7a556` 合并到 `main`。
- PR head `12ce7c11a7b4cf8286a86cd2ce150a33a98cfe3e` 的 Build #9 与 Governance #18 在 Ubuntu 24.04 和 Windows 2025 四项门禁全部通过。
- 合并后的 `main@fdf43361ff42680bb69daa24783ef528dba1411c` 已无任何 pending 豁免通过 strict HandOff、项目治理、固定工具链、官方包哈希和 diff 校验。
- API 29 rev8 与 API 36 rev2 两套非 root x86_64 验收均通过：instrumentation 1/1、冷启动 20/20、三种真实变异 APK 3/3 failure-closed、零禁止日志/文件/payload 哈希落盘命中。
- 独立安全复核结果为 `PASS`，没有剩余 P0/P1/P2 发现。
- Emulator 37.1.11、API 29 rev8 和 API 36 rev2 官方大体积包只位于项目根下被忽略的 `.toolchains/`；未提交到 Git，验收后没有遗留项目 Emulator 或 watchdog。
- M0-04 仅证明公开 ClassLoader PoC，不表示 M0-05 或生产 signer、加密容器、多 DEX 与完整 Runtime 能力已实现。
- 用户已于 `2026-08-01` 明确授权启动 M0-05；固定分支已从 `main@43e10c38569dfdd64bc41d688d23d23e005906fb` 创建，Issue #5 保持 open，远端不存在同分支 PR。
- 当前本地只有 API 29 rev8/API 36 rev2 x86_64 官方镜像；唯一可见物理设备 `20a24423` 为 `unauthorized`，不得用于验收。缺少 API 29+ arm64 非 root 环境只阻塞完成状态，不阻塞静态实现和 x86_64 验证。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29、正式设备矩阵、独立安全复核及四项 PR CI 均通过 |
| M0-05 | `runtime-security-agent` | `spike/m0-05-application-factory-provider-jni-poc` | in_progress | M0-04 | 实现组合 fixture、早期 signer/metadata gate、Factory 委托与 JNI；冻结后交 `m0_05_security_review` 独立复核 |

## Decisions and Invariants

- 继续遵守 [ADR-0001](docs/adr/0001-apk-postprocessing-only.md) 至 [ADR-0006](docs/adr/0006-offline-key-protection-boundary.md)。
- 输入 APK 始终只读；产品输出始终为新的未签名 APK；生产模块不得读取、传递或使用签名凭据。
- API 29+ 只使用公开 `AppComponentFactory.instantiateClassLoader()` 接入，不使用 hidden API、反射修改 `pathList` 或明文 DEX 落盘回退。
- M0-04 测试签名能力只存在于被忽略的集成测试产物中，不进入产品模块、分发包或版本库。
- APK/ZIP/AXML/DEX/证书和所有长度字段均视为不可信输入；日志和异常不得泄露 payload、用户路径或异常 cause。
- API 镜像与 Emulator 大文件只允许位于项目根 `.toolchains/` 且必须被 Git 忽略。
- 模拟器验收必须限时执行并在 `finally` 中清理；结束后核对 `adb devices` 与 Emulator/watchdog 进程。
- M0-05 使用 `pre-cli` 验证模式；独立只读安全复核者预先指定为 `m0_05_security_review`，实现冻结前不得用其声明替代实际 Git、测试和设备证据。
- 反 dump、反调试、环境检测、签名校验和离线密钥隐藏只能描述为成本防御，不作绝对安全承诺。

## Changes Since Previous Handoff

- 将 PR #29 从 draft 转为 ready，并在所有门禁保持全绿、head SHA 未变化的前提下以普通 merge commit 合并。
- 将 M0-04 工作流状态从 `review` 更新为 `done`，清除活动任务与 owner。
- 将恢复点从 `spike/m0-04-classloader-poc` 更新为合并后的 `main`。
- 在合并后的 `main` 提交 HandOff 快照并完成无豁免 strict 验证。
- 保留 M0-05 为未分配的 `planned` 状态，不提前实现相邻任务。
- 用户明确授权启动 M0-05，从最新 `main` 创建固定任务分支并将 Issue #5/HandOff 状态切换为进行中。
- 完成 arm64 可用性预检：未发现项目内 arm64 system image，用户物理设备未授权；继续静态实现与 x86_64 路径，禁止等待或占用该设备。

## Verification Evidence

### M0-04 formal API 29/36 acceptance

- task_id: M0-04
- git_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
- command: `.\gradlew.bat --offline --no-daemon :fixtures:android:connectedClassloaderPocDebugAndroidTest`; `node tools/validation/run-m0-04-cold-start.mjs`; `node tools/validation/run-m0-04-tamper-start.mjs`
- exit_code: 0
- environment: Windows 10 10.0.19045 x64; Temurin 17.0.19+10; Gradle 9.5.0; Node.js 24.12.0; Emulator 37.1.11; API 29 rev8 and API 36 rev2 x86_64 non-root AVDs
- timestamp: 2026-07-31T15:06:44+08:00
- artifact: `docs/evidence/M0-04/formal-api29-api36.md`; ignored raw evidence under `build/m0-04/evidence/`
- sha256: `57ed7fda2539a8053ea7e361b1db51950dc0096305ae2c514780cc9ec6edef0b`
- result: PASS; both devices passed 1/1 instrumentation, 20/20 cold starts, complete snapshots, zero forbidden log/file/hash hits, and 3/3 real failure-close variants

### M0-04 pinned Android packages

- task_id: M0-04
- git_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
- command: `node tools/validation/verify-m0-04-android-packages.mjs`
- exit_code: 0
- environment: Windows 10 10.0.19045 x64; Node.js 24.12.0; project-local `.toolchains/android-m0-04`
- timestamp: 2026-07-31T15:06:44+08:00
- artifact: `tools/validation/m0-04-android-packages.json`
- sha256: `cbc44d8325f44f3bef1f1529c0bbf77d42c8fd13e494aba4e10e27ba6813b6c2`
- result: PASS; all three official archive SHA-1 and project SHA-256 values match the fixed manifest

### M0-04 independent security review

- task_id: M0-04
- git_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
- command: `independent read-only review of implementation diff, validators, raw API 29/36 evidence, and scope boundaries`
- exit_code: 0
- environment: independent `m0_04_security_review` Agent; reviewed commit and ignored evidence hashes independently
- timestamp: 2026-07-31T15:06:44+08:00
- artifact: `docs/evidence/M0-04/formal-api29-api36.md`
- sha256: `57ed7fda2539a8053ea7e361b1db51950dc0096305ae2c514780cc9ec6edef0b`
- result: PASS; no remaining P0/P1/P2 findings, with later production protections explicitly outside M0-04 scope

### M0-04 PR CI and merge

- task_id: M0-04
- git_commit: 09c654b36a3ec19225926521dca5127ffef7a556
- command: `GitHub Actions Build run 30612038332; Governance run 30612038433; merge PR #29 with expected head 12ce7c11a7b4cf8286a86cd2ce150a33a98cfe3e`
- exit_code: 0
- environment: GitHub-hosted ubuntu-24.04 and windows-2025; protected main; normal merge commit
- timestamp: 2026-08-01T00:30:34+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/pull/29`
- sha256: not_applicable
- result: PASS; four required PR checks succeeded and GitHub reports PR #29 merged into main

### M0-04 merged-main strict validation

- task_id: M0-04
- git_commit: fdf43361ff42680bb69daa24783ef528dba1411c
- command: `validate-handoff.mjs HandOff.md --strict; validate-project-package.mjs; verify-m0-toolchain.mjs; verify-m0-04-android-packages.mjs; git diff --check HEAD^ HEAD`
- exit_code: 0
- environment: Windows 10 10.0.19045 x64; Git 2.52.0; Node.js 24.12.0; main branch with clean working tree
- timestamp: 2026-08-01T00:32:28+08:00
- artifact: `HandOff.md`
- sha256: `b085cb274afdbaa24f7b545d7676d459a24d9fbb6a0d7cd992d09edc6a262118`
- result: PASS; strict HandOff ran on merged main without pending-branch or pending-clean exemptions, and all companion validations succeeded

## Blockers and Required Approvals

- M0-05 完成态需要至少一个 API 29+ arm64 非 root 环境；当前尚未提供。该条件不会阻塞本地实现、构建、静态测试或 API 29/36 x86_64 有界验收。

## Ordered Next Actions

1. 在 M0-05 任务卡范围内实现组合 fixture、七个 typed metadata、早期 apksig gate、原始 Factory 委托、多 DEX 与 JNI 两种加载路径。
2. 先运行 JVM/静态/构建验证，再以有界 watchdog 分别执行 API 29/36 x86_64；每次均在 `finally` 清理并复核无 Emulator/watchdog 遗留。
3. 冻结实现提交后，由 `m0_05_security_review` 对固定 SHA 做独立只读安全复核；arm64 环境可用后完成最后一套设备验收。
4. 只有全部强制矩阵、独立复核、strict HandOff 和双平台 CI 通过后，才可把 Issue/PR 标记完成。

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-04-api29-classloader-poc.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/tasks/M1-03-binary-axml-transformer.md`
- `docs/tasks/M2-01-shell-app-component-factory.md`
- `docs/evidence/M0-04/formal-api29-api36.md`
- `docs/TOOLCHAIN_AND_PROVENANCE.md`
- `tools/validation/m0-04-android-packages.json`
- `tools/validation/verify-m0-04-android-packages.mjs`
- `tools/validation/verify-m0-04-apk.mjs`
- `tools/validation/run-m0-04-cold-start.mjs`
- `tools/validation/run-m0-04-tamper-start.mjs`

## Resume Checklist

- [ ] 确认当前分支为 `spike/m0-05-application-factory-provider-jni-poc`、工作树干净且基于 `main@43e10c38569dfdd64bc41d688d23d23e005906fb`。
- [ ] 运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`；开发中仅允许 schema 明确支持的 pending 豁免，冻结提交必须无豁免复验。
- [ ] 运行 `node tools/governance/validate-project-package.mjs`、`node tools/validation/verify-m0-toolchain.mjs` 和 `git diff --check`。
- [ ] 检查 M0-05 实现没有进入相邻 M1/M2 生产范围，且未引入 hidden API、磁盘明文 DEX 或签名执行能力。
- [ ] 不使用用户的 `20a24423 unauthorized` 物理设备，不遗留项目 Emulator/watchdog。
- [ ] 缺少 arm64 环境时保持任务 `in_progress` 或提交准确 `blocked` 交接，不以 x86_64 结果冒充完整验收。

## Handoff Sign-off

- Coordinator `/root` 已核验 PR #29 head、四项 PR CI、普通 merge commit、正式设备证据、独立安全复核及本地 Git 状态。
- 合并后 `main@fdf43361ff42680bb69daa24783ef528dba1411c` 已无豁免通过 strict HandOff；最终证据提交后必须再次复验。
- M0-04 完成不扩大 M0-05 或生产 Runtime 的能力声明。
- Coordinator `/root` 已核验 M0-05 前置条件、固定 Issue/分支、远端无重复 PR，并预先指定独立安全复核者。
