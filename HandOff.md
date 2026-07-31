---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260801-012038
updated_at: 2026-08-01T01:20:38+08:00
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

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的边界内完成 M0-05 兼容性 PoC。当前只验证原始 `AppComponentFactory`、Provider、JNI、早期 signer/typed metadata 和多 DEX 内存加载路径，不扩展到 M1/M2 生产实现。

## Current State

- M0-04 的唯一 PR #29 已合并；合并后 `main` 已无豁免通过 strict HandOff。
- 用户已授权启动 M0-05；固定 Issue 为 #5，固定分支为 `spike/m0-05-application-factory-provider-jni-poc`。
- M0-05 起始提交为 `ea229e384bdcad549ffcc184fbd7f49969fb7154`，实现提交为 `d58a277681443a5e79b770a3e9162ae54006138d`。
- 静态实现、Release/R8 构建、lint/check、APK 结构验证、`apksigner` 和 `zipalign` 已通过。
- API 29 rev8 与 API 36 rev2 x86_64 模拟器均未在有界时间内完成启动，因此没有执行安装或 instrumentation；每次失败均自动清理，当前没有 emulator/qemu 遗留进程。
- API 29+ arm64 非 root 环境仍未提供。
- M0-05 仍为 `in_progress`，未完成设备矩阵、冻结提交和独立安全复核，不得标记 accepted/done。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29 已合并，正式设备矩阵与独立复核已通过 |
| M0-05 | `runtime-security-agent` | `spike/m0-05-application-factory-provider-jni-poc` | in_progress | M0-04 | 在可可靠启动的 API 29/36 x86_64 和 API 29+ arm64 非 root 环境完成矩阵，再冻结并交 `m0_05_security_review` |

## Decisions and Invariants

- 继续遵守 `docs/adr/0001` 至 `docs/adr/0006`。
- 输入 APK 只读；产品输出必须为新的未签名 APK；生产模块不得读取、传递或使用签名凭据。
- API 29+ 只使用公开 `AppComponentFactory.instantiateClassLoader()` 接入，不使用 hidden API、反射修改 `pathList` 或明文 DEX 落盘回退。
- fixture 的一次性 debug 签名仅用于被忽略的集成测试产物，不进入产品模块或版本库。
- APK、ZIP、AXML、DEX、证书和长度字段均视为不可信输入；日志和异常不得泄露 payload、用户路径或异常 cause。
- 官方镜像与 Emulator 大文件只允许位于项目根的、被忽略的 `.toolchains/`。
- 模拟器验收必须限时执行并在 `finally` 清理；结束后复核 `adb devices` 和 emulator/qemu 进程。
- M0-05 使用 `pre-cli` 验证模式；独立只读安全复核者为 `m0_05_security_review`，只能在设备验证后针对冻结 SHA 复核。
- x86_64 结果不能冒充 arm64 验收；M0-05 完成前 M1/M2 仍保持阻塞。

## Changes Since Previous Handoff

- 在提交 `d58a277681443a5e79b770a3e9162ae54006138d` 完成早期 apksig、七项 typed metadata、STORED AHDC 双 DEX、原始 Factory 五类组件委托、无 Factory 回退、JNI 与两种 native library 路径的兼容性 PoC。
- 新增 `compatExtracted` 与 `compatDirect` Release/R8 fixture，以及 Application、eager Provider、Activity、Service、Receiver、第二 DEX API 和固定 JNI marker 的 instrumentation 覆盖。
- 新增静态 APK/R8 验证器和有界设备验收脚本；设备脚本包含隐藏启动、逐命令 timeout、`finally` 卸载/关闭和 PID 差集清理。
- 构建、lint/check、静态验证、签名验证和对齐验证通过，证据写入 `docs/evidence/M0-05/implementation-snapshot.md`。
- API 29 冷启动 75 秒、API 29 snapshot 45 秒、API 36 snapshot 45 秒均超时；遵照用户要求停止继续重试，所有尝试已自动清理。

## Verification Evidence

### M0-04 formal device acceptance and independent review

- task_id: M0-04
- git_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
- command: `gradlew.bat --offline --no-daemon :fixtures:android:connectedClassloaderPocDebugAndroidTest`; `node tools/validation/run-m0-04-cold-start.mjs`; `node tools/validation/run-m0-04-tamper-start.mjs`; independent read-only review
- exit_code: 0
- environment: Windows 10 10.0.19045 x64; Emulator 37.1.11; API 29 rev8 and API 36 rev2 x86_64 non-root AVDs; independent `m0_04_security_review` Agent
- timestamp: 2026-07-31T15:06:44+08:00
- artifact: `docs/evidence/M0-04/formal-api29-api36.md`
- sha256: 57ed7fda2539a8053ea7e361b1db51950dc0096305ae2c514780cc9ec6edef0b
- result: PASS; both devices passed instrumentation, cold-start and tamper matrices, and the independent review reported no remaining P0/P1/P2 finding

### M0-05 static implementation and R8 verification

- task_id: M0-05
- git_commit: d58a277681443a5e79b770a3e9162ae54006138d
- command: `gradlew.bat --offline --no-daemon --no-configuration-cache :fixtures:android:assembleCompatExtractedDebugAndroidTest :fixtures:android:assembleCompatDirectDebugAndroidTest :fixtures:android:assembleCompatExtractedRelease :fixtures:android:assembleCompatDirectRelease`; `gradlew.bat --offline --no-daemon --no-configuration-cache :runtime:bootstrap:check :runtime:bootstrap:lint :fixtures:android:check :fixtures:android:lint`; `node tools/validation/verify-m0-05-apks.mjs ...`; `apksigner verify --verbose`; `zipalign -c -P 16 4`
- exit_code: 0
- environment: Windows 10 10.0.19045 x64; Temurin/OpenJDK 17.0.19+10; Gradle 9.5.0; Android build-tools 36.1.0; NDK 29.0.14206865; CMake 4.1.2; Node.js 24.12.0
- timestamp: 2026-08-01T01:19:15+08:00
- artifact: `docs/evidence/M0-05/implementation-snapshot.md`
- sha256: b5341f7e6dbe553139baad9d6e13a510119155a7266fb5ee68202ed0ced8a987
- result: PASS for build/static scope; this is not device acceptance

### M0-05 bounded x86_64 device attempts

- task_id: M0-05
- git_commit: d58a277681443a5e79b770a3e9162ae54006138d
- command: `tools/validation/run-m0-05-device-acceptance.ps1` against API 29 rev8 and API 36 rev2 x86_64 project-local AVDs
- exit_code: 1
- environment: Emulator 37.1.11; API 29 rev8 x86_64 and API 36 rev2 x86_64; cold/snapshot bounded runs
- timestamp: 2026-08-01T01:19:15+08:00
- artifact: `docs/evidence/M0-05/implementation-snapshot.md`; ignored logs under `build/m0-05/`
- sha256: not_applicable
- result: NOT_ACCEPTED; both emulators timed out before `sys.boot_completed=1`, no install/instrumentation ran, and cleanup PASS left no emulator/qemu process

## Blockers and Required Approvals

- API 29 rev8 与 API 36 rev2 x86_64 项目 AVD 无法在用户允许的有界窗口内完成启动。需要可靠的已启动设备、外部设备实验室或 CI 设备环境；不得用无限等待解决。
- 仍需要至少一个 API 29+ arm64 非 root 环境。
- 设备矩阵通过并冻结 SHA 后，仍需由 `m0_05_security_review` 完成独立只读安全复核。
- 上述事项不需要扩大 M0-05 代码范围，但会阻止 accepted/done、PR 合并和 M1/M2 启动。

## Ordered Next Actions

1. 在可靠启动的 API 29 rev8 x86_64 环境运行 extracted/direct 两个 Release/R8 变体的完整矩阵。
2. 在可靠启动的 API 36 rev2 x86_64 环境运行相同矩阵。
3. 在 API 29+ arm64 非 root 环境运行相同矩阵，记录 signer、metadata、生命周期、JNI、20 次冷启动、峰值内存与篡改结果。
4. 设备验收通过后冻结提交，再启动预先指定的 `m0_05_security_review` 独立复核。
5. 仅在矩阵、复核、strict HandOff 和双平台 CI 全部通过后，才可标记完成或合并唯一 PR。

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/evidence/M0-05/implementation-snapshot.md`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/EarlySignerProbe.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/StoredDexReader.java`
- `fixtures/android/src/androidTest/java/ah/fixtures/android/M005CompatibilityInstrumentedTest.java`
- `tools/validation/verify-m0-05-apks.mjs`
- `tools/validation/run-m0-05-device-acceptance.ps1`

## Resume Checklist

- [ ] 确认当前分支为 `spike/m0-05-application-factory-provider-jni-poc`、工作树干净且基于 `main@43e10c38569dfdd64bc41d688d23d23e005906fb`。
- [ ] 无豁免运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
- [ ] 运行项目治理、固定工具链、M0-05 静态验证与 `git diff --check`。
- [ ] 不使用 `20a24423 unauthorized` 物理设备，不遗留 emulator/watchdog/qemu 进程。
- [ ] 不把构建和静态通过描述为设备验收；arm64 缺失时保持 `in_progress`。
- [ ] 设备证据冻结前不启动独立复核，验收和复核完成前不进入 M1/M2。

## Handoff Sign-off

- Coordinator `/root` 已核验当前分支、实现提交、静态命令结果、APK 哈希和有界设备失败结果。
- 当前交接明确区分静态 PASS 与设备 NOT_ACCEPTED，不把模拟器启动失败包装成兼容性通过。
- 本次没有再次启动模拟器；最后一次清理后只剩预先存在的 `20a24423 unauthorized`，无 emulator/qemu 遗留进程。
- M0-05 保持进行中，等待可靠的 x86_64 环境、arm64 非 root 环境和冻结后的独立复核。
