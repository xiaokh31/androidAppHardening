---
id: M0-05
title: Application、Factory、Provider 与 JNI 兼容性验证
milestone: M0
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-04
required_skills:
  - implement-runtime-protection
  - validate-protected-apk
security_sensitive: true
---

## Goal

验证 Shell factory 在多 DEX、原始自定义 `Application`、原始自定义 `AppComponentFactory`、启动期 `ContentProvider` 和 JNI 同时存在时仍保持 Android 原始生命周期与类加载语义。

## Background

M0-04 只证明最小 ClassLoader 接入。真实 Java/Kotlin APK 常通过 Provider 提前初始化、由自定义 factory 创建组件，或从 Application 加载原生库；这些路径必须在冻结 Host manifest 变换和 Runtime 接口前通过。

## Inputs

- M0-04 通过的 Shell factory PoC。
- 仓库源码构建的组合 fixture：两个 DEX、自定义 `Application`、自定义 `AppComponentFactory`、初始化 Provider、Activity、Service 和 JNI 库。
- API 29 与 API 36 的 x86_64 环境，以及 API 29 以上的 arm64 非 root 环境。

## Expected Outputs

- 组合 fixture 与自动化 instrumentation tests。
- Shell factory 对原始 factory 的确定性延迟委托 PoC。
- 生命周期、Provider 顺序、跨 DEX 类解析与 JNI 加载证据。
- 冻结供 M1-03/M2-01 使用的 `ah.runtime.*` manifest metadata 名称和委托契约。

## In Scope

- 在 `instantiateClassLoader` 完成 payload loader 创建并缓存当前 `ApplicationInfo`。
- 原始 factory 存在时由 payload loader 实例化一次，并委托 Application、Activity、Service、Receiver 与 Provider 创建。
- 原始 factory 不存在时调用 Shell factory 的 `super` 实现。
- 验证 Provider 早于 `Application.onCreate`、但晚于 loader 创建；验证原始 Application 只创建一次。
- 从 payload DEX 类调用 APK 原有 `lib/<abi>/libfixture_jni.so`。

## Out of Scope

- 生产级 DEX 解密、密钥保护、反调试、签名校验或四 ABI Runtime 发布。
- Flutter、Unity、React Native、热修复框架和已有加固壳。
- 将 ARM-only 原生应用转换为 x86 可运行应用。

## Implementation Decisions

- manifest 保留原始 `android:name`，把原 Application 规范化全限定名后写入 metadata `ah.runtime.original_application`，缺失时固定为 `android.app.Application`。
- 原 `android:appComponentFactory` 存在时写入 metadata `ah.runtime.original_app_component_factory`，并设置 `ah.runtime.has_original_app_component_factory=true`；缺失时不写 string 值并将 boolean 设为 `false`。
- manifest 的 `android:appComponentFactory` 固定替换为 `ah.runtime.bootstrap.ShellAppComponentFactory`。
- Shell factory 使用 framework 传入的 payload ClassLoader 加载原始 factory，调用其无参构造器一次；不使用 service loader 或隐藏 API。
- 原始 factory 的五类组件方法都被委托，委托抛出的异常保持类型与 cause，不回退到 `super`。
- `InMemoryDexClassLoader` 的 parent 保持 framework loader，因此 APK 原有 native library search path 可供 `System.loadLibrary("fixture_jni")` 使用。
- 分支名固定为 `spike/m0-05-application-factory-provider-jni-poc`，Issue 标题固定为 `[M0-05] Application, factory, provider, and JNI PoC`，仅允许一个关联 PR。

## Public Interfaces

- metadata：`ah.runtime.original_application`。
- metadata：`ah.runtime.original_app_component_factory`。
- metadata：`ah.runtime.has_original_app_component_factory`。
- Shell factory：`ah.runtime.bootstrap.ShellAppComponentFactory`。
- 稳定 probe 事件追加 `ORIGINAL_FACTORY_CREATED`、`PROVIDER_CREATED`、`APPLICATION_ON_CREATE`、`JNI_LOADED`。
- 失败码：factory 加载 `AAH-P002`，委托异常 `AAH-P003`，JNI 失败 `AAH-P004`。

## Security Constraints

- factory 类名只能来自当前 APK 已验证 manifest metadata，并按 Java 全限定类名语法校验。
- 不捕获后静默忽略原始 factory 异常，不加载网络或可写目录中的代码。
- JNI fixture 只返回固定测试值，不读取设备身份、凭据或外部文件。
- 文件系统扫描仍必须证明没有 payload DEX 明文落盘。

## Compatibility Requirements

- API 29/36 x86_64 与至少一个 API 29+ arm64 环境通过。
- 支持 Java/Kotlin、单/多 DEX、自定义 Application/factory 和启动 Provider。
- ARM-only fixture 只在 ARM 环境验证；报告必须明确其不能在 x86 环境运行。
- 原 APK 未声明 factory 时保持平台默认组件实例化语义。

## Acceptance Criteria

1. API 29/36 x86_64 与 API 29+ arm64 上执行组合 fixture instrumentation 均退出 `0`。
2. 事件顺序满足 `LOADER_CREATED < ORIGINAL_FACTORY_CREATED < PROVIDER_CREATED < APPLICATION_ON_CREATE`，各关键事件只出现一次。
3. 原始 factory 对 Application、Activity、Service、Receiver、Provider 的计数均为 `1`，实际组件类由 payload loader 加载。
4. `classes2.dex` 独有类可从 Provider 与 Activity 调用，返回固定断言值。
5. `System.loadLibrary("fixture_jni")` 成功，JNI 方法在 x86_64 与 arm64 返回相同固定值。
6. 无原始 factory fixture 通过同一生命周期测试；错误 factory 类名产生 `AAH-P002` 且不回退。
7. 安装前后文件扫描没有明文 DEX，静态/动态扫描没有 hidden API 使用。
8. 冻结的 metadata 与委托契约被 M1-03、M2-01 的任务或设计文档引用。

## Required Tests

- 有/无自定义 factory 的参数化 instrumentation test。
- 五类组件委托、Provider/Application 顺序、多 DEX 跨类调用测试。
- x86_64/arm64 JNI 加载和错误 ABI 负向测试。
- factory 不存在、构造失败、委托抛错的错误码与 cause 保留测试。
- API 29/35 各 20 次冷启动稳定性和明文落盘扫描。

## Required Evidence

- 每个测试环境的 API、ABI、fingerprint、root 状态和 native library 列表。
- 所有命令、退出码、JUnit XML、事件计数和冷启动汇总。
- fixture APK、各 DEX、各 ABI SO 和报告的 SHA-256。
- 生命周期时序、ClassLoader identity、无明文落盘与 hidden API 扫描结果。
- 提交 SHA、Issue 与唯一 PR 链接，以及 M0 compatibility gate 结论。

## Likely Files

- `fixtures/android/classloader-poc/src/compatFixture/`
- `fixtures/android/classloader-poc/src/androidTest/`
- `fixtures/android/classloader-poc/src/main/cpp/`
- `docs/evidence/M0-05/`

## Dependencies and Blockers

- M0-04 的公开 API gate 必须为 pass。
- 缺少 arm64 非 root 环境时不能把任务标记为 done。
- 任一组件生命周期被破坏、JNI search path 失效或需要 hidden API 时，M1/M2 保持 blocked。

## Agent Handoff Requirements

- 本任务固定使用分支 `spike/m0-05-application-factory-provider-jni-poc`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、设备环境、产物 SHA-256、事件时序和兼容性 gate 结论。
- worker 不修改根 `HandOff.md`，不顺手实现生产 Runtime 或 Host transformer。
- 若 fixture 与平台真实行为冲突，提交最小复现和 blocked 交接，由 `/root` 决定是否修订 ADR。
