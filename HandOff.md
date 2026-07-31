---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260731-150847
updated_at: 2026-07-31T15:08:47+08:00
updated_by: /root
state: ready
source_branch: spike/m0-04-classloader-poc
base_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
working_tree: clean
current_milestone: M0
active_task: M0-04
next_owner: /root
---

# Project HandOff

## Objective

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的既定边界内，完成 M0-04 ClassLoader PoC：使用公开 `AppComponentFactory` 与 `InMemoryDexClassLoader` 验证内存 DEX 加载，并在固定的 API 29/36 x86_64 官方 system image 上完成正向、篡改、兼容和失败路径验收。

## Current State

- M0-03 已由 PR #28 合并，`main` 已无豁免通过 strict HandOff，M0-04 的前置条件成立。
- M0-04 实现提交为 `e9f89734aa3d4148ec6ebe9a6b970a9276128d00`，对应分支 `spike/m0-04-classloader-poc` 和草稿 PR #29。
- Emulator 37.1.11、API 29 rev8 和 API 36 rev2 官方包已固定并校验；下载、SDK、AVD、Android 用户状态和新增 Gradle 缓存均位于项目根目录下被忽略的 `.toolchains/`。
- API 29/36 两套非 root x86_64 设备验收均通过：instrumentation 1/1、冷启动 20/20、四项文件快照完整、零禁止日志、零明文 DEX、三种真实变异 APK 3/3 failure-closed。
- 独立安全复核结果为 `PASS`，没有剩余 P0/P1/P2 发现。
- 验收后没有遗留项目 Emulator 或 watchdog；`adb devices` 只显示用户原有的 `20a24423 unauthorized` 设备。
- M0-04 仅证明公开 ClassLoader 接入可行性，不提前声称 M0-05 或后续生产 Runtime 能力已实现。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | review | M0-03 | 推送证据与 HandOff，等待 PR #29 的 Build/Governance 全绿并完成评审 |
| M0-05 | `unassigned` | `spike/m0-05-application-factory-provider-jni-poc` | planned | M0-04 | 仅在 PR #29 合并且 `main` 无豁免通过 strict HandOff 后启动 |

## Decisions and Invariants

- 继续遵守 [ADR-0001](docs/adr/0001-apk-postprocessing-only.md) 至 [ADR-0006](docs/adr/0006-offline-key-protection-boundary.md)。
- 输入 APK 始终只读；产品输出始终为新的未签名 APK；生产模块不得读取或传递签名凭据。
- M0-04 的测试签名能力仅存在于被忽略的集成测试产物中，不进入生产模块、分发包或版本库。
- APK/ZIP/AXML/DEX/证书和所有长度字段均视为不可信输入；payload 中央目录有明确大小和条目数上限，并拒绝重复 payload。
- 错误和日志只暴露稳定错误码与安全事件类型，不泄露 `sourceDir`、异常 cause 或 payload 内容。
- API 29/36 镜像和 Emulator 的大体积文件只能位于项目根 `.toolchains/`，不得提交到 Git。
- 模拟器验收必须限时执行并在 `finally` 中关闭；结束后必须核对 `adb devices` 和本地 Emulator/watchdog 进程。
- 反 dump、反调试、环境检测、签名校验和离线密钥隐藏只能描述为成本防御，不作绝对安全承诺。

## Changes Since Previous Handoff

- 固定 Emulator 37.1.11 build 15917651、API 29 rev8 和 API 36 rev2，并加入机器可读校验清单与官方来源说明。
- 将 `.toolchains/` 纳入 `.gitignore`，避免下载包、解压 SDK、AVD 和测试缓存进入版本库。
- 完整扫描 ZIP 中央目录，限制中央目录和条目数量，拒绝重复 payload，统一安全错误边界。
- 增加受限 probe 事件、异常路径脱敏、四项文件快照、payload 哈希落盘扫描和验证器自测。
- 增加缺失、损坏、空 payload 三种真实变异 APK 的冷启动 failure-close 验证。
- 在固定 API 29/36 x86_64 system image 上完成两套正式验收，并完成独立安全复核。
- 新增正式证据文档 [docs/evidence/M0-04/formal-api29-api36.md](docs/evidence/M0-04/formal-api29-api36.md)。

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
- result: PASS; no remaining P0/P1/P2 findings, with later production protections explicitly retained outside M0-04 scope

## Blockers and Required Approvals

None

## Ordered Next Actions

1. 提交并推送本 HandOff 和正式证据到 `spike/m0-04-classloader-poc`。
2. 检查草稿 PR #29 的 Windows/Ubuntu Build 和 Governance checks，若失败只修复 M0-04 范围内的原因。
3. checks 全绿后完成 PR #29 的正常评审；是否从草稿转为 ready 及合并由项目协调者按授权执行。
4. PR #29 合并后，在最新 `main` 上无 `--allow-pending-branch` 或 `--allow-pending-clean` 运行 strict HandOff、治理、工具链和 diff 校验。
5. 只有第 4 步通过后，才可领取 M0-05；不得提前实现相邻任务。

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-04-api29-classloader-poc.md`
- `docs/evidence/M0-04/formal-api29-api36.md`
- `docs/TOOLCHAIN_AND_PROVENANCE.md`
- `tools/validation/m0-04-android-packages.json`
- `tools/validation/verify-m0-04-android-packages.mjs`
- `tools/validation/verify-m0-04-apk.mjs`
- `tools/validation/run-m0-04-cold-start.mjs`
- `tools/validation/run-m0-04-tamper-start.mjs`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/`
- `fixtures/android/`

## Resume Checklist

- [ ] 确认当前分支为 `spike/m0-04-classloader-poc`，工作树干净且 HEAD 包含本 HandOff。
- [ ] 运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
- [ ] 运行 `node tools/governance/validate-project-package.mjs`、固定工具链验证和 `git diff --check`。
- [ ] 检查 PR #29 的 Build/Governance 状态和独立评审结论。
- [ ] 不使用用户的 `20a24423 unauthorized` 物理设备，不遗留项目 Emulator/watchdog。
- [ ] 在 PR #29 合并和最新 `main` strict 验证之前，不启动 M0-05。

## Handoff Sign-off

- Coordinator `/root` 已核验实际 Git 状态、固定包哈希、两套设备原始证据、独立安全复核和本地进程清理状态。
- 本 HandOff 提交前使用 `--allow-pending-clean` 验证；提交后必须无豁免通过 strict HandOff。
- PR CI 是本地离线 lint 依赖缺失后的最终全量 Build/Governance 验证来源；该限制已在正式证据中披露。
