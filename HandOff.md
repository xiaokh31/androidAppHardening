---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260801-234944
updated_at: 2026-08-01T23:49:44+08:00
updated_by: /root
state: active
source_branch: spike/m0-05-application-factory-provider-jni-poc
base_commit: f63a7192eb6e1055a7647d27850ece262c59210a
working_tree: clean
current_milestone: M0
active_task: M0-05
next_owner: m0_05_security_review
---

# Project HandOff

## Objective

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的边界内完成 M0-05 兼容性 PoC。验证 API 29/36 x86_64 与 API 29+ arm64 非 root 环境中的 ConfigV2/sourceDir 启动门禁、原始 `AppComponentFactory`、Provider、跨 DEX、JNI、失败清理、冷启动、内存和无明文 DEX 落盘，不扩展到 M1/M2 生产实现。

## Current State

- M0-04 的 PR #29 已合并，正式 API 29/36 x86_64 设备矩阵和独立安全复核通过。
- M0-06 的 PR #31 已合并为 `main@f1362188be5083a6d557522f0f5be1905935f6eb`；合并后的 Governance/Build 在 Ubuntu 与 Windows 通过，`main` 已无豁免通过 strict HandOff。
- M0-06/ADR 0007 已解除旧的 `ApplicationInfo.metaData == null` 阻塞，启动配置唯一来源改为 `ApplicationInfo.sourceDir` 中的固定 ConfigV2 与 AHDC 条目。
- 用户已要求完成 M0-05 剩余部分；固定 Issue 为 #5，固定分支为 `spike/m0-05-application-factory-provider-jni-poc`。
- 旧实现提交 `d58a277681443a5e79b770a3e9162ae54006138d` 已具备 early signer、原 Factory 五类组件委托、双 DEX、JNI 和两种 Native 路径的初始 PoC，但仍依赖已废弃 metadata，必须按 ConfigV2/sourceDir 合同修订。
- 旧 arm64 真机证据仅证明 early signer 可用并复现 metadata 缺失，不构成当前合同的设备验收。
- 最新 `main` 已通过 merge commit `71d3f9519b5e304346814f33b58b5bf97adeb440` 合入既有 M0-05 分支，合并后 strict HandOff 无豁免通过。
- ConfigV2/sourceDir、原 Factory ClassLoader hook、READY 前 session 清理、无 Factory、双 DEX、JNI 和负向 APK 矩阵已完成本地实现；Gradle check、Release/R8、静态 APK、签名和治理校验通过。
- API 29 arm64 非 root 真机已完成 extracted/direct Release/R8、instrumentation、生命周期、跨 DEX、JNI、signer/config/metadata、17 个负向用例、各 20 次冷启动、内存和无明文 DEX 的正式验收，结果 PASS。
- M0-05 可执行实现已冻结为 `0d8e6f8c13ac871c840fe134d83d1bfc0b69d3a9`；后续仅允许为 KVM 失败修复重新冻结，或在不改变实现的前提下补充证据与 HandOff。
- 验证/workflow 已冻结为 `f63a7192eb6e1055a7647d27850ece262c59210a`；GitHub Actions run `30706455270` 的 API 29 job `91386314437` 与 API 36 job `91386314472` 均为 `success`。
- API 29/36 x86_64 Linux/KVM 已完成 extracted/direct Release/R8、instrumentation、生命周期、跨 DEX、JNI、signer/config/metadata、17 个独立启动负例、各 20 次冷启动、内存、无明文 DEX 和强制清理的正式验收，结果 PASS。
- 三套设备环境证据已冻结并归档；当前门禁只剩独立 `m0_05_security_review`。独立复核 PASS 前仍禁止创建 PR。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29、正式设备矩阵和独立复核已通过 |
| M0-06 | `runtime-security-agent` | `docs/m0-06-early-startup-config-contract` | done | M0-04 | PR #31、合并后 strict HandOff 和双平台 CI 已通过 |
| M0-05 | `m0_05_security_review` | `spike/m0-05-application-factory-provider-jni-poc` | in_progress | M0-04, M0-06 | 对冻结实现、验证 SHA 与三套设备证据执行独立只读复核 |

## Decisions and Invariants

- 继续遵守 ADR 0001 至 ADR 0007；ADR 0007 固定 sourceDir 配置通道，ADR 0006 固定 768-byte ConfigV2 wire layout。
- 输入 APK 只读；产品输出必须为新的未签名 APK；生产模块不得读取、传递或使用签名凭据。
- M0-05 使用 `pre-cli` 验证模式，只处理仓库生成的合成 fixture 和被忽略的一次性测试签名产物。
- API 29+ 只使用公开 `AppComponentFactory.instantiateClassLoader()`、Framework `ApplicationInfo` 和只读文件 API；不使用 Context、PackageManager、Framework 私有对象、反射或 hidden API 回退。
- 启动固定读取 `assets/ah/runtime/config.bin` 与 `assets/ah/runtime/payload.ahdc`；ConfigV2 在 PoC 级 APK signer 覆盖成立前不得暴露原 Factory。
- Manifest 只替换 `android:appComponentFactory`，不新增或读取废弃 `ah.runtime.*` metadata；原 Application 使用 Framework `className`。
- x86_64 验收只在有整体超时和强制清理的 GitHub Linux/KVM 环境运行；本机不启动模拟器。arm64 验收只使用已授权非 root 真机。
- 每个平台覆盖 extracted/direct 两种 Release/R8 变体，并验证 instrumentation、生命周期顺序、跨 DEX、JNI、早期 signer、ConfigV2、篡改失败、20 次冷启动、内存和无明文 DEX 落盘。
- 冻结设备证据和提交后，由独立 `m0_05_security_review` 只读复核；P0/P1/P2 全部关闭前不完成任务。
- x86/x86_64 结果不得冒充 ARM-only 应用兼容性；离线 Runtime 只提高提取成本，不作绝对防护声明。

## Changes Since Previous Handoff

- PR #31 已合并，旧 metadata blocker 的架构依赖已解除，M0-05 从 `blocked` 恢复为 `in_progress`。
- 既有 M0-05 分支保留四个本地历史提交和 Issue #5，不创建第二分支或第二任务。
- 已把 `main@f1362188be5083a6d557522f0f5be1905935f6eb` 合入固定分支，解决 HandOff 冲突并无豁免通过 strict 验证。
- 已实现严格 768-byte ConfigV2、固定 sourceDir 条目、测试 signer 双重绑定、原 Factory 确定性 ClassLoader 委托和 READY 前失败清理。
- 已新增不会启动/关闭模拟器的跨平台设备 runner、签名后 Config/ZIP/payload 负向矩阵和固定 API 29 r8/API 36 r2/Emulator 37.1.11 Linux/KVM workflow。
- Google 官方 Linux Emulator 归档只下载到项目 D 盘 ignored `build/`，SHA-256 固定为 `95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266`；未向 C 盘下载大体积工具。
- MIUI streamed install 的拒绝已通过标准 `adb install --no-streaming` 方式消除；正式 API 29 arm64 非 root 真机矩阵在 64.2 秒内 PASS，runner 完成 cleanup，未启动本机模拟器。
- 用户已授予一次验证性推送权限；冻结分支可推送用于 KVM workflow，但独立复核 PASS 前不创建 PR。
- GitHub Linux/KVM workflow 的 API 29 冷启动检查已改为在 2 秒有界窗口内核验目标进程与 resumed Activity，避免 Android 10 `am start -W` 偶发先报告 Launcher 的假阴性，真实未恢复仍失败并保留 logcat。
- 独立启动负例检查已按当前 FATAL PID 隔离日志，避免 Android 10 logcat 中前一 instrumentation 进程的 marker 污染；当前失败 PID 必须包含预期错误码且不得包含 `LOADER_CREATED`。
- `f63a7192eb6e1055a7647d27850ece262c59210a` 上的 run `30706455270` 双 job PASS；正式报告、命令日志、启动负例报告和静态报告哈希已归档到 `docs/evidence/M0-05/formal-compatibility.md`。

## Verification Evidence

### M0-04 completed dependency

- task_id: M0-04
- git_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
- command: `gradlew.bat --offline --no-daemon :fixtures:android:connectedClassloaderPocDebugAndroidTest`; `node tools/validation/run-m0-04-cold-start.mjs`; `node tools/validation/run-m0-04-tamper-start.mjs`; independent read-only review
- exit_code: 0
- environment: Windows 10 x64; Emulator 37.1.11; API 29 revision 8 and API 36 revision 2 x86_64 non-root AVDs; independent `m0_04_security_review`
- timestamp: 2026-07-31T15:06:44+08:00
- artifact: `docs/evidence/M0-04/formal-api29-api36.md`
- sha256: 57ed7fda2539a8053ea7e361b1db51950dc0096305ae2c514780cc9ec6edef0b
- result: PASS; both devices passed instrumentation, cold starts, tamper matrices and independent review

### M0-06 merged dependency

- task_id: M0-06
- git_commit: f1362188be5083a6d557522f0f5be1905935f6eb
- command: `gh pr merge 31 --merge`; `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`; GitHub Actions Governance and Build
- exit_code: 0
- environment: Windows 10 x64 local strict validation; GitHub Actions Ubuntu 24.04 and Windows 2025
- timestamp: 2026-08-01T14:46:00+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/pull/31`
- sha256: not_applicable
- result: PASS; normal merge commit, post-merge no-exemption HandOff and four main CI jobs succeeded

### M0-05 previous implementation snapshot

- task_id: M0-05
- git_commit: d58a277681443a5e79b770a3e9162ae54006138d
- command: `gradlew.bat --offline --no-daemon --no-configuration-cache` for four M0-05 assemble tasks; bootstrap and fixture check/lint; static APK, apksigner and zipalign verification
- exit_code: 0
- environment: Windows 10 x64; Temurin 17.0.19+10; Gradle 9.5.0; Build Tools 36.1.0; NDK 29.0.14206865; CMake 4.1.2; Node.js 24.12.0
- timestamp: 2026-08-01T01:19:15+08:00
- artifact: `docs/evidence/M0-05/implementation-snapshot.md`
- sha256: b5341f7e6dbe553139baad9d6e13a510119155a7266fb5ee68202ed0ced8a987
- result: PASS for the superseded static implementation only; current ConfigV2/device acceptance remains pending

### M0-05 previous arm64 blocker reproduction

- task_id: M0-05
- git_commit: 3d716ddc4be513a07be0b5cf2d986529d9e0dc06
- command: build and install extracted Release/R8 fixture; `adb shell am instrument -w`; `aapt2 dump xmltree`
- exit_code: 1
- environment: Windows 10 x64 host; Android API 29 arm64-v8a physical user/release-keys device; adb shell non-root
- timestamp: 2026-08-01T13:17:48+08:00
- artifact: `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`
- sha256: c0695656d20926c0aaa6dbc90d9e2591eb6027e74d9db57409b4934e657b0a75
- result: HISTORICAL BLOCKER; early signer passed and metadata was null; M0-06 replaced that contract, so this is regression context rather than current acceptance

### M0-05 ConfigV2 implementation and local gate

- task_id: M0-05
- git_commit: 71d3f9519b5e304346814f33b58b5bf97adeb440
- command: `gradle --no-daemon :runtime:bootstrap:check :fixtures:android:check :tools:validation:check verifyGovernance`; two-pass signer build for extracted/direct Release/R8 and AndroidTest; `node tools/validation/verify-m0-05-apks.mjs ...`; `apksigner verify`; `zipalign -c -P 16 4`
- exit_code: 0
- environment: Windows 10 x64; project-local Temurin 17.0.19+10 and Gradle 9.5.0; Android build-tools/NDK/CMake from the pinned existing SDK; Node.js 24; no local emulator
- timestamp: 2026-08-01T15:47:01+08:00
- artifact: ignored `build/m0-05/`; committed evidence pending device matrix
- sha256: not_applicable
- result: PASS for local compile/check/governance, ConfigV2 20-case parser test, Release/R8 structure, signer cross-binding, APK signature, alignment, R8 removal and signed malformed-APK generation; ignored artifact SHA-256 values are extracted `315f3b84f7fb32ffd5aa6c384b07dad9934594d37e39f532cf177daf7a02c499`, direct `152eec34ebc05753a7c9c94cc0cf8ddb65d57d1c820266d268524c30dc86c471`, ConfigV2 `a9a58af1463d7d9adf59674e775ce38a3cf2c691adbf052cfa61d8219659636e`; device PASS remains pending

### M0-05 API 29 arm64 formal acceptance

- task_id: M0-05
- git_commit: 0d8e6f8c13ac871c840fe134d83d1bfc0b69d3a9
- command: `node tools/validation/run-m0-05-device-acceptance.mjs --serial <redacted> --platform arm64-api29-physical --cold-starts 20 --negative-signed-dir <ignored> --negative-unsigned-dir <ignored> ...`
- exit_code: 0
- environment: Android API 29; arm64-v8a; user/release-keys; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000; serial omitted
- timestamp: 2026-08-01T22:44:17+08:00
- artifact: ignored `build/m0-05/device-arm64-api29-physical/report.json`; committed summary `docs/evidence/M0-05/formal-compatibility.md`
- sha256: 833ae034e7c99389a398bce2acdd24b17bb300f98374292c7da5988c9496731f
- result: PASS; extracted/direct instrumentation, lifecycle/factory, cross-DEX, JNI, signer/config/metadata, 17 external startup negatives, no-factory semantics, 20 cold starts each, memory collection, zero plaintext DEX and cleanup all passed; redacted command log SHA-256 is `2c0ab50114aefc8ebe16f9eab6c5f81c530a22ae547ded5db41796d06d08166d`

### M0-05 API 29/36 x86_64 Linux/KVM formal acceptance

- task_id: M0-05
- git_commit: f63a7192eb6e1055a7647d27850ece262c59210a
- command: GitHub Actions workflow `.github/workflows/m0-05-linux-kvm.yml`; project-local pinned API 29 r8/API 36 r2 x86_64 images and Emulator 37.1.11; `run-m0-05-device-acceptance.mjs`; `run-m0-05-startup-negative.mjs`
- exit_code: 0
- environment: GitHub Linux/KVM; API 29 x86_64 and API 36 x86_64; 64-bit; adb shell non-root; local emulator use none
- timestamp: 2026-08-01T23:48:49+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30706455270`; committed summary `docs/evidence/M0-05/formal-compatibility.md`; raw artifacts downloaded under ignored `build/m0-05/github-run-30706455270/`
- sha256: not_applicable
- result: PASS; both jobs succeeded, both variants passed instrumentation/lifecycle/cross-DEX/JNI/signer/config/metadata, each variant completed 20 cold starts and memory collection, independent startup negatives were 17/17, plaintext DEX count was zero, no-factory semantics and cleanup passed; API 29 report SHA-256 is `ceb1a572b149260bbb7c7b3fac808f73bf3f6ffb96dc2448262c41e6dd6f4519`, API 36 report SHA-256 is `ce5ffc1815a671b21a8e11fe978cd84eb821a2edfa17813fe7fd1f01e3b65a6f`

## Blockers and Required Approvals

- 无当前外部批准阻塞。验证性分支推送和三套设备证据归档已完成；仍须遵守“独立复核 PASS 前不创建 PR”。
- 技术门禁仅剩独立只读 `m0_05_security_review`；P0/P1/P2 必须全部为零。

## Ordered Next Actions

1. `/root` commits and pushes the reconciled evidence without changing frozen implementation or validation SHA.
2. `/root` assigns independent `m0_05_security_review` read-only review of the frozen implementation, validation/workflow SHA and all three device environments; all P0/P1/P2 findings must close.
3. Only after review PASS, create the sole Issue #5 PR and run required PR CI; request explicit merge authorization before merging.
4. Keep M1/M2 blocked until M0-05 is merged and strict HandOff passes on `main`.

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/adr/0003-api29-public-classloader-hook.md`
- `docs/adr/0006-offline-key-protection-boundary.md`
- `docs/adr/0007-source-dir-startup-configuration.md`
- `docs/evidence/M0-05/implementation-snapshot.md`
- `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`
- `docs/evidence/M0-05/formal-compatibility.md`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `fixtures/android/src/androidTestCompatFixture/java/ah/fixtures/android/CompatibilityPocRunner.java`
- `tools/validation/verify-m0-05-apks.mjs`
- `tools/validation/create-m0-05-test-apks.mjs`
- `tools/validation/run-m0-05-device-acceptance.mjs`
- `tools/validation/run-m0-05-startup-negative.mjs`
- `tools/validation/m0-05-linux-kvm-packages.json`
- `.github/workflows/m0-05-linux-kvm.yml`

## Resume Checklist

- [x] 当前分支为 `spike/m0-05-application-factory-provider-jni-poc`，Issue 固定为 #5。
- [x] M0-04 与 M0-06 已合并并完成各自门禁。
- [x] 完成最新 main 合并并无豁免运行 strict HandOff。
- [x] 建立 M0-05 十三项验收条件到实现、静态测试、设备 runner 与 GitHub KVM workflow 的映射。
- [x] 完成 ConfigV2/sourceDir、Factory/session、JNI、签名后篡改、R8 和落盘扫描的本地实现与静态门禁。
- [x] 解除 MIUI USB 安装限制并完成 arm64 20 次冷启动、内存和负向设备验收。
- [x] 已获得验证性推送授权；API 29/36 Linux/KVM 两套 Release/R8 设备验收与强制清理均已通过。
- [ ] 冻结 SHA 并由独立 reviewer 对同一提交与设备证据复核，P0/P1/P2 全为零。
- [ ] 复核通过后再完成分支发布、唯一 PR、CI 与 merger-ready HandOff。
- [ ] M0-05 完成前不启动 M1/M2。

## Handoff Sign-off

- Coordinator `/root` 已核验当前 Git 分支、M0-04/M0-06 合并状态、本地静态门禁、API 29 arm64 正式设备证据和同一验证 SHA 上的 API 29/36 x86_64 KVM 正式设备证据。
- 当前快照声明三套要求的设备环境验收 PASS，但在独立复核前不声明 M0-05 完成，也不把 PoC signer 覆盖描述为生产 ConfigV2 完整认证。
- `/root` 已核验真机为 API 29 arm64 64-bit、user/release-keys、非 root 环境；本轮未启动任何本机模拟器。
- GitHub KVM workflow 具有 35 分钟 job 上限、180 秒 boot 上限、900 秒 device-runner 上限和 EXIT/INT/TERM 强制清理；run `30706455270` 双 job 成功，独立复核 PASS 前不创建 PR。
