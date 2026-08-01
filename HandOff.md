---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260801-131748
updated_at: 2026-08-01T13:17:48+08:00
updated_by: /root
state: blocked
source_branch: spike/m0-05-application-factory-provider-jni-poc
base_commit: 45f29740cd2abfb8054ae9d3a6af2ff2f89f9cf1
working_tree: clean
current_milestone: M0
active_task: M0-05
next_owner: project-coordinator
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
- 已授权真机确认是 API 29、arm64、user/release-keys、`ro.debuggable=0`、`ro.secure=1` 的非 root 环境。
- extracted Release/R8 fixture 在该真机进入真实 `instantiateClassLoader` 后，早期 signer 验证通过，但 Framework `ApplicationInfo.metaData` 为 null，按合同以 `AAH-P009` 在 `LOADER_CREATED` 前失败。
- M0-05 已转为 `blocked`。不得通过 `Context`、`PackageManager`、`ActivityThread`、`LoadedApk`、反射或 hidden API 补读 metadata；必须回到 ADR-0003 做缩减或终止决策。
- 未启动 `m0_05_security_review`，未推送分支，未创建 PR；M1/M2 保持阻塞。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29 已合并，正式设备矩阵与独立复核已通过 |
| M0-05 | `runtime-security-agent` | `spike/m0-05-application-factory-provider-jni-poc` | blocked | M0-04 | `/root` 与产品负责人决定修订 ADR-0003/兼容性合同还是终止 v0.1 当前启动方案；不得继续设备矩阵或绕过 metadata gate |

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
- 在已授权 API 29 arm64 非 root 真机完成环境确认和 extracted Release/R8 基线；安装成功，但真实 Factory 回调收到 null metadata Bundle，触发 `AAH-P009`，instrumentation 进程崩溃且未执行业务 loader/JNI。
- 使用 `aapt2 dump xmltree` 确认 APK 二进制 Manifest 确实包含 Shell Factory 与七个 typed metadata，因此该结果是 Framework 回调可见性失败，不是打包遗漏。
- 遵守任务卡的显式 blocker 条款，停止 direct 变体、冷启动、篡改、内存、x86 CI 和独立复核；证据记录于 `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`。

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

### M0-05 API 29 arm64 callback metadata blocker

- task_id: M0-05
- git_commit: d58a277681443a5e79b770a3e9162ae54006138d
- command: `gradlew.bat --offline --no-daemon --no-configuration-cache <four M0-05 assemble tasks>`; install extracted Release/R8 and instrumentation fixtures; `adb shell am instrument -w`; `aapt2 dump xmltree`
- exit_code: 1
- environment: Windows 10 10.0.19045 x64 host; Android API 29 arm64-v8a physical user/release-keys device; adb shell non-root; `ro.debuggable=0`; `ro.secure=1`
- timestamp: 2026-08-01T13:17:48+08:00
- artifact: `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`
- sha256: c0695656d20926c0aaa6dbc90d9e2591eb6027e74d9db57409b4934e657b0a75
- result: BLOCKED; packaged metadata exists, early signer verification passed, but Framework callback metadata was null and `AAH-P009` occurred before `LOADER_CREATED`

## Blockers and Required Approvals

- Blocker owner: `/root` 项目协调者与产品负责人。
- Required decision: 根据 ADR-0003 和 M0-05 任务卡，选择终止当前 v0.1 启动设计，或先修订 ADR/任务/兼容性声明并定义新的公开、已认证早期配置通道。
- Observed conflict: API 29 arm64 user/release-keys 真机的真实 `instantiateClassLoader` 回调未提供 Manifest metadata Bundle；当前安全合同不能成立。
- Prohibited workaround: 不得改用启动期 `Context`、`PackageManager`、`ActivityThread`、`LoadedApk`、反射、hidden API 或明文磁盘配置。
- Secondary blocker: GitHub x86_64 KVM 必须先推送分支才能运行，而用户批准的顺序要求设备验收与独立复核后才推送。arm64 架构失败已优先阻止继续该流程。
- Independent security review、PR、M1/M2 均不得启动，直至架构决策落地并通过新的任务门禁。

## Ordered Next Actions

1. `/root` 与产品负责人审阅 `docs/evidence/M0-05/arm64-api29-metadata-blocker.md` 和 ADR-0003。
2. 明确选择：终止当前 v0.1 方案，或授权一个独立规划变更来修订 ADR-0003、任务卡、威胁模型和兼容性声明。
3. 若批准修订，先定义不依赖 Context/hidden API、且可在业务 DEX 释放前认证的早期配置通道，再创建独立决策提交；不得在当前实现中试探性绕过。
4. 只有新合同经批准后，才恢复 arm64 与 API 29/36 x86_64 矩阵、冻结 SHA 和独立复核。
5. 在此之前不推送、不创建 PR、不启动 M1/M2。

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/evidence/M0-05/implementation-snapshot.md`
- `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`
- `docs/adr/0003-api29-public-classloader-hook.md`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/EarlySignerProbe.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/StoredDexReader.java`
- `fixtures/android/src/androidTestCompatFixture/java/ah/fixtures/android/CompatibilityPocRunner.java`
- `tools/validation/verify-m0-05-apks.mjs`
- `tools/validation/run-m0-05-device-acceptance.ps1`

## Resume Checklist

- [ ] 确认当前分支为 `spike/m0-05-application-factory-provider-jni-poc`、工作树干净且 blocker base 为 `45f29740cd2abfb8054ae9d3a6af2ff2f89f9cf1`。
- [ ] 无豁免运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
- [ ] 运行项目治理、固定工具链、M0-05 静态验证与 `git diff --check`。
- [ ] 不在文档中记录设备序列号；确认 fixture 包已卸载且不遗留 emulator/watchdog/qemu 进程。
- [ ] 不把早期 signer PASS 描述为兼容性通过；metadata callback gate 已失败。
- [ ] 在 ADR/任务合同决策前不继续 direct、冷启动、篡改、内存或 x86 CI 验收。
- [ ] 不启动独立复核、PR 或 M1/M2。

## Handoff Sign-off

- Coordinator `/root` 已核验设备 API/ABI/非 root 属性、fixture 构建、安装、真实 callback 崩溃、`AAH-P009` 日志、Manifest 七键与清理状态。
- 当前交接明确区分静态 PASS、早期 signer PASS 与整体兼容性 BLOCKED。
- 本次没有启动任何模拟器；fixture 包已从真机卸载，无 emulator/qemu 遗留进程。
- M0-05 停在 ADR-0003 决策点，不允许通过未批准的回退路径继续。
