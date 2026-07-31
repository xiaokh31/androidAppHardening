---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260801-003121
updated_at: 2026-08-01T00:31:21+08:00
updated_by: /root
state: ready
source_branch: main
base_commit: 09c654b36a3ec19225926521dca5127ffef7a556
working_tree: clean
current_milestone: M0
active_task: NONE
next_owner: unassigned
---

# Project HandOff

## Objective

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的边界内推进离线 APK 加固工具。M0-04 已用公开 `AppComponentFactory` 与 `InMemoryDexClassLoader` 证明 API 29+ 内存 DEX 接入可行性；下一任务 M0-05 只能在独立领取后验证原始 Factory、Provider、JNI 和兼容性路径。

## Current State

- M0-04 的唯一 [PR #29](https://github.com/xiaokh31/androidAppHardening/pull/29) 已于 `2026-08-01T00:30:34+08:00` 以普通 merge commit `09c654b36a3ec19225926521dca5127ffef7a556` 合并到 `main`。
- PR head `12ce7c11a7b4cf8286a86cd2ce150a33a98cfe3e` 的 Build #9 与 Governance #18 在 Ubuntu 24.04 和 Windows 2025 四项门禁全部通过。
- API 29 rev8 与 API 36 rev2 两套非 root x86_64 验收均通过：instrumentation 1/1、冷启动 20/20、三种真实变异 APK 3/3 failure-closed、零禁止日志/文件/payload 哈希落盘命中。
- 独立安全复核结果为 `PASS`，没有剩余 P0/P1/P2 发现。
- Emulator 37.1.11、API 29 rev8 和 API 36 rev2 官方大体积包只位于项目根下被忽略的 `.toolchains/`；未提交到 Git，验收后没有遗留项目 Emulator 或 watchdog。
- M0-04 仅证明公开 ClassLoader PoC，不表示 M0-05 或生产 signer、加密容器、多 DEX 与完整 Runtime 能力已实现。
- 当前没有活动开发任务；M0-05 尚未领取或启动。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29、正式设备矩阵、独立安全复核及四项 PR CI 均通过 |
| M0-05 | `unassigned` | `spike/m0-05-application-factory-provider-jni-poc` | planned | M0-04 | 从最新 `main` 领取任务，先规划兼容性 PoC 与独立复核所有权 |

## Decisions and Invariants

- 继续遵守 [ADR-0001](docs/adr/0001-apk-postprocessing-only.md) 至 [ADR-0006](docs/adr/0006-offline-key-protection-boundary.md)。
- 输入 APK 始终只读；产品输出始终为新的未签名 APK；生产模块不得读取、传递或使用签名凭据。
- API 29+ 只使用公开 `AppComponentFactory.instantiateClassLoader()` 接入，不使用 hidden API、反射修改 `pathList` 或明文 DEX 落盘回退。
- M0-04 测试签名能力只存在于被忽略的集成测试产物中，不进入产品模块、分发包或版本库。
- APK/ZIP/AXML/DEX/证书和所有长度字段均视为不可信输入；日志和异常不得泄露 payload、用户路径或异常 cause。
- API 镜像与 Emulator 大文件只允许位于项目根 `.toolchains/` 且必须被 Git 忽略。
- 模拟器验收必须限时执行并在 `finally` 中清理；结束后核对 `adb devices` 与 Emulator/watchdog 进程。
- 反 dump、反调试、环境检测、签名校验和离线密钥隐藏只能描述为成本防御，不作绝对安全承诺。

## Changes Since Previous Handoff

- 将 PR #29 从 draft 转为 ready，并在所有门禁保持全绿、head SHA 未变化的前提下以普通 merge commit 合并。
- 将 M0-04 工作流状态从 `review` 更新为 `done`，清除活动任务与 owner。
- 将恢复点从 `spike/m0-04-classloader-poc` 更新为合并后的 `main`。
- 保留 M0-05 为未分配的 `planned` 状态，不提前实现相邻任务。

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

## Blockers and Required Approvals

None

## Ordered Next Actions

1. 提交并推送本次合并后 HandOff 快照，在最新 `main` 上无豁免运行 strict HandOff、项目治理、固定工具链和 diff 校验。
2. 检查 merge commit 触发的 `main` Build/Governance 状态，并在完成后补充证据。
3. 由用户或项目协调者明确授权并分配 M0-05；在授权前保持无活动任务。
4. M0-05 必须从最新 `main` 创建规定分支，只处理其任务卡范围并预先指定独立安全复核者。

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-04-api29-classloader-poc.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/evidence/M0-04/formal-api29-api36.md`
- `docs/TOOLCHAIN_AND_PROVENANCE.md`
- `tools/validation/m0-04-android-packages.json`
- `tools/validation/verify-m0-04-android-packages.mjs`
- `tools/validation/verify-m0-04-apk.mjs`
- `tools/validation/run-m0-04-cold-start.mjs`
- `tools/validation/run-m0-04-tamper-start.mjs`

## Resume Checklist

- [ ] 确认当前分支为 `main`、工作树干净且 HEAD 包含本 HandOff。
- [ ] 运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`，不使用任何 pending 豁免。
- [ ] 运行 `node tools/governance/validate-project-package.mjs`、`node tools/validation/verify-m0-toolchain.mjs` 和 `git diff --check`。
- [ ] 检查 merge commit 的 Build/Governance push CI。
- [ ] 不使用用户的 `20a24423 unauthorized` 物理设备，不遗留项目 Emulator/watchdog。
- [ ] 未获 M0-05 明确授权前，不创建分支或实现相邻任务。

## Handoff Sign-off

- Coordinator `/root` 已核验 PR #29 head、四项 PR CI、普通 merge commit、正式设备证据、独立安全复核及本地 Git 状态。
- 本 HandOff 提交前使用 `--allow-pending-clean` 验证；提交后必须在 `main` 无豁免通过 strict HandOff。
- M0-04 完成不扩大 M0-05 或生产 Runtime 的能力声明。
