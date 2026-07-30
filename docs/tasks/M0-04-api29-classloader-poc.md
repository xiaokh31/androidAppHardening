---
id: M0-04
title: API 29 公共 ClassLoader 接入可行性验证
milestone: M0
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-03
required_skills:
  - implement-runtime-protection
  - validate-protected-apk
security_sensitive: true
---

## Goal

证明在 Android API 29 及以上仅使用公开 API，`AppComponentFactory.instantiateClassLoader` 能返回承载内存 DEX 的 ClassLoader，并在组件实例化前加载原始应用类。

## Background

Runtime 必须避免把解密后的 DEX 写回文件系统。Android 公开的 `InMemoryDexClassLoader` 与 `AppComponentFactory` 是 v0.1 唯一允许的接入点；若该路径在 API 29 不成立，M1/M2 不得进入业务实现。

## Inputs

- M0-03 的 Android/Native 工程骨架。
- 仓库源码生成的单 DEX fixture，包含一个自定义 `Application` 和一个 `Activity`。
- API 29 与 API 36 的 x86_64 emulator。

## Expected Outputs

- 隔离的 `:fixtures:android:classloader-poc` 与 instrumentation test source set。
- `ah.runtime.bootstrap.ShellAppComponentFactory` PoC。
- 展示调用顺序、ClassLoader 身份、内存 DEX 加载和禁止落盘结果的可复现实验报告。
- 明确的 pass/fail M0 gate，不产生可发布 Runtime。

## In Scope

- 在 `instantiateClassLoader(ClassLoader, ApplicationInfo)` 中读取仓库自有 asset、创建只读 direct `ByteBuffer` 并返回 `InMemoryDexClassLoader`。
- 让 fixture 的 `Application` 与 `Activity` 由返回的 loader 解析。
- 记录 framework 回调顺序与组件类的实际 ClassLoader。
- 检查进程私有目录和外部存储没有新增 `.dex`、`.jar`、`.odex` 或明文 payload。

## Out of Scope

- 加密、密钥包装、签名校验、反调试和内存清理强化。
- 多 DEX、自定义原始 `AppComponentFactory`、Provider 或 JNI；这些属于 M0-05。
- hidden API、反射访问 framework 内部字段或修改 `BaseDexClassLoader.pathList`。

## Implementation Decisions

- manifest 中只把 `android:appComponentFactory` 指向 `ah.runtime.bootstrap.ShellAppComponentFactory`，保留原始 `android:name`。
- PoC 使用 `dalvik.system.InMemoryDexClassLoader(ByteBuffer, parent)`；parent 固定为 framework 传入的 ClassLoader。
- DEX asset 仅用于可行性验证，构建时由 fixture 源码生成；运行时以 `AssetManager.open` 读入 direct buffer，不调用 `DexClassLoader`。
- 回调事件写入进程内 ring buffer，由 instrumentation 读取；禁止依赖 logcat 作为唯一断言。
- 失败时抛出带稳定错误码 `AAH-P001` 的 `IllegalStateException`，不得静默回退到原 ClassLoader。
- 分支名固定为 `spike/m0-04-classloader-poc`，Issue 标题固定为 `[M0-04] API 29 ClassLoader PoC`，仅允许一个关联 PR。

## Public Interfaces

- Android manifest 入口：`ah.runtime.bootstrap.ShellAppComponentFactory`。
- PoC 只读诊断接口：`ClassLoaderProbe.snapshot(): List<ProbeEvent>`。
- 稳定事件名：`FACTORY_ENTER`、`LOADER_CREATED`、`APPLICATION_CREATED`、`ACTIVITY_CREATED`。
- 失败码：`AAH-P001`。

## Security Constraints

- 只加载打包在当前测试 APK 中、由构建系统生成的 DEX。
- 不把 ByteBuffer、DEX 内容、用户目录或设备标识写入日志。
- 禁止使用 greylist/blacklist API、`setAccessible(true)`、JVMTI、root 或调试器注入。
- 测试结束扫描 payload 落盘，发现明文 DEX 即判定 gate 失败。

## Compatibility Requirements

- 必须在未 root 的 API 29 和 API 36 x86_64 emulator 上通过。
- 生产源集不得引用高于 API 29 且未做版本保护的 API。
- fixture 的 `minSdk` 固定为 29，启用普通 Android 组件生命周期。

## Acceptance Criteria

1. `./gradlew :fixtures:android:classloader-poc:connectedDebugAndroidTest` 在 API 29 与 API 36 均退出 `0`。
2. 事件序列严格满足 `FACTORY_ENTER < LOADER_CREATED < APPLICATION_CREATED < ACTIVITY_CREATED`。
3. `Application` 与 `Activity` 的 ClassLoader 均为返回的 `InMemoryDexClassLoader`，且能调用只存在于 payload DEX 的方法。
4. 对安装前后应用私有目录快照求差，不出现 `.dex`、`.jar`、`.odex` 或与 payload SHA-256 相同的文件。
5. 静态扫描和运行日志中不存在 hidden API、反射 `pathList`、`DexClassLoader` 或 hiddenapi denial。
6. 删除 payload asset 后启动失败并产生 `AAH-P001`，不回退加载。
7. PoC 报告将结果标为 pass 后，M0-05 才可开始。

## Required Tests

- API 29/35 冷启动与 Activity 实例化 instrumentation test。
- payload 缺失、损坏 DEX 和空 buffer 的负向测试。
- 回调顺序、ClassLoader identity 和 payload-only 方法测试。
- 安装前后文件系统差异与 logcat hidden API 扫描。
- 连续 20 次 force-stop/cold-start 稳定性测试。

## Required Evidence

- 每台 emulator 的 API、ABI、system image fingerprint、非 root 状态。
- Gradle/adb 命令、退出码、20 次运行汇总和测试 XML。
- fixture APK、payload DEX、测试报告的 SHA-256。
- 文件系统差异清单与 hidden API 扫描结果。
- 提交 SHA、Issue 与唯一 PR 链接，以及明确的 gate 结论。

## Likely Files

- `fixtures/android/classloader-poc/`
- `fixtures/android/classloader-poc/src/main/AndroidManifest.xml`
- `fixtures/android/classloader-poc/src/androidTest/`
- `docs/evidence/M0-04/`

## Dependencies and Blockers

- M0-03 必须能构建并运行 Android instrumentation。
- API 29 public API 路径任一核心验收失败即阻塞 M0-05、M1 和 M2。
- 失败不得通过 hidden API 或明文落盘绕过；应提交 fail 证据供 `/root` 重新决策。

## Agent Handoff Requirements

- 本任务固定使用分支 `spike/m0-04-classloader-poc`、同编号 Issue 和一个 PR。
- 完成状态必须包含命令、退出码、设备环境、产物 SHA-256、事件序列和 gate 结论。
- worker 不修改根 `HandOff.md`，不把 PoC 代码提升为生产实现。
- 若公开 API 路径失败，返回 blocked 交接并附最小复现，不扩大到规避平台限制。
