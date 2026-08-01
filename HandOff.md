---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260801-145821
updated_at: 2026-08-01T14:58:21+08:00
updated_by: /root
state: active
source_branch: spike/m0-05-application-factory-provider-jni-poc
base_commit: 3d716ddc4be513a07be0b5cf2d986529d9e0dc06
working_tree: clean
current_milestone: M0
active_task: M0-05
next_owner: runtime-security-agent
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
- 当前正在把最新 `main` 合入既有 M0-05 分支；完成实现与全矩阵验证前不启动 M1/M2。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29、正式设备矩阵和独立复核已通过 |
| M0-06 | `runtime-security-agent` | `docs/m0-06-early-startup-config-contract` | done | M0-04 | PR #31、合并后 strict HandOff 和双平台 CI 已通过 |
| M0-05 | `runtime-security-agent` | `spike/m0-05-application-factory-provider-jni-poc` | in_progress | M0-04, M0-06 | 合并最新 main，补齐 ConfigV2/sourceDir 实现和 13 项验收矩阵 |

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
- 正在合并 `main@f1362188be5083a6d557522f0f5be1905935f6eb`，以取得 ADR 0007、ConfigV2、任务卡和治理校验器的冻结合同。
- 本轮尚未启动模拟器、访问真机、下载工具、推送分支或创建 PR。

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

## Blockers and Required Approvals

None

## Ordered Next Actions

1. `runtime-security-agent` resolves the M0-06 merge and maps every M0-05 acceptance criterion to executable tests.
2. `runtime-security-agent` replaces deprecated metadata startup with strict ConfigV2/sourceDir parsing, authenticated Factory release and READY-before-ownership cleanup semantics.
3. `runtime-security-agent` runs local static, Gradle, R8, tamper and cleanup tests without starting a local emulator.
4. `runtime-security-agent` runs API 29/36 x86_64 on GitHub Linux/KVM and API 29+ arm64 on the authorized non-root device, each with extracted/direct Release/R8 variants and bounded cleanup.
5. `/root` freezes the exact implementation/evidence SHA and assigns independent `m0_05_security_review` read-only review; all findings must close before publication completion.
6. After review PASS, push the fixed branch, create or update the sole Issue #5 PR, run required CI, and keep M1/M2 blocked until M0-05 is merged and strict HandOff passes on main.

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/adr/0003-api29-public-classloader-hook.md`
- `docs/adr/0006-offline-key-protection-boundary.md`
- `docs/adr/0007-source-dir-startup-configuration.md`
- `docs/evidence/M0-05/implementation-snapshot.md`
- `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `fixtures/android/src/androidTestCompatFixture/java/ah/fixtures/android/CompatibilityPocRunner.java`
- `tools/validation/verify-m0-05-apks.mjs`
- `tools/validation/run-m0-05-device-acceptance.ps1`

## Resume Checklist

- [x] 当前分支为 `spike/m0-05-application-factory-provider-jni-poc`，Issue 固定为 #5。
- [x] M0-04 与 M0-06 已合并并完成各自门禁。
- [ ] 完成最新 main 合并并无豁免运行 strict HandOff。
- [ ] 建立 M0-05 十三项验收条件到测试与证据的映射。
- [ ] 完成 ConfigV2/sourceDir、Factory/session、JNI、篡改、冷启动、内存和落盘扫描验证。
- [ ] 冻结 SHA 并由独立 reviewer 对同一提交与设备证据复核，P0/P1/P2 全为零。
- [ ] 复核通过后再完成分支发布、唯一 PR、CI 与 merger-ready HandOff。
- [ ] M0-05 完成前不启动 M1/M2。

## Handoff Sign-off

- Coordinator `/root` 已核验当前 Git 分支、M0-04/M0-06 合并状态、旧 M0-05 提交和 arm64 blocker 证据。
- 当前快照只声明 M0-05 已恢复为 active，不把旧静态 PASS 或 early signer PASS 描述为新合同兼容性通过。
- 本轮尚未启动模拟器或真机命令；后续所有设备执行必须有整体超时、强制清理和无明文 DEX 扫描。
