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

验证 Shell factory 在多 DEX、原始自定义 `Application`、原始自定义 `AppComponentFactory`、启动期 `ContentProvider` 和 JNI 同时存在时仍保持 Android 原始生命周期与类加载语义，并证明在无 `Context` 的 `instantiateClassLoader` 回调中可通过公开 `ApplicationInfo.sourceDir` 与固定 `apksig` 完成当前安装 APK 的 signer 门禁。

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
- API 29/36、ARM/x86 上的启动早期 `ApplicationInfo.sourceDir` 可读性、`apksig` 唯一当前 signer 验证和 payload 前失败证据。
- 冻结供 M1-03/M2-01 使用的 `ah.runtime.*` manifest metadata 名称和委托契约。

## In Scope

- 在 `instantiateClassLoader` 完成 payload loader 创建并缓存当前 `ApplicationInfo`。
- 在同一真实回调中验证 `ApplicationInfo.metaData` 非空且保留键保持 Binary AXML 的 string/boolean/int 类型；PoC 不把“PackageManager 安装后可读”替代为“该早期回调可读”。
- 在任何 payload byte 被打开前，仅凭 Framework 传入的 `ApplicationInfo.sourceDir` 只读验证当前 APK；对 `Context`、`PackageManager`、`ActivityThread`、`LoadedApk` 或反射的依赖均判定 PoC 失败。
- 从同一 `sourceDir` 定位唯一 `assets/ah/runtime/payload.ahdc`，只接受 `STORED`、无 encryption、无 data descriptor 且 CRC/长度一致的条目；PoC 不从不存在的早期 `AssetManager` 读取。
- 原始 factory 存在时由 payload loader 实例化一次，并委托 Application、Activity、Service、Receiver 与 Provider 创建。
- 原始 factory 不存在时调用 Shell factory 的 `super` 实现。
- 验证 Provider 早于 `Application.onCreate`、但晚于 loader 创建；验证原始 Application 只创建一次。
- 从 payload DEX 类调用 APK 原有 `lib/<abi>/libfixture_jni.so`，覆盖 installer 解压 SO 和从 APK 直接加载 SO 两种模式。

## Out of Scope

- 生产级 DEX 解密、密钥保护、反调试、完整 signer policy 或四 ABI Runtime 发布；本任务只验证启动早期 signer 获取与调用时序的可行性。
- Flutter、Unity、React Native、热修复框架和已有加固壳。
- 将 ARM-only 原生应用转换为 x86 可运行应用。

## Implementation Decisions

- manifest 保留原始 `android:name`，把原 Application 规范化全限定名后写入 metadata `ah.runtime.original_application`，缺失时固定为 `android.app.Application`。
- 原 `android:appComponentFactory` 存在时写入 metadata `ah.runtime.original_app_component_factory`，并设置 `ah.runtime.has_original_app_component_factory=true`；缺失时不写 string 值并将 boolean 设为 `false`。
- 其余键和值固定为 string `ah.runtime.container_asset=assets/ah/runtime/payload.ahdc`，以及 binary integer `ah.runtime.container_major=1`、`ah.runtime.signer_policy_version=1`、`ah.runtime.risk_policy_version=1`。
- manifest 的 `android:appComponentFactory` 固定替换为 `ah.runtime.bootstrap.ShellAppComponentFactory`。
- Shell factory 使用 framework 传入的 payload ClassLoader 加载原始 factory，调用其无参构造器一次；不使用 service loader 或隐藏 API。
- Shell factory 只从 Framework 回调传入并缓存的 `ApplicationInfo.metaData` 读取七个保留键；缺失、类型错误、重复语义或 API/厂商返回空 Bundle 时以稳定 PoC 错误失败，不通过早期 `Context`/`PackageManager` 补读。
- 原始 factory 的五类组件方法都被委托，委托抛出的异常保持类型与 cause，不回退到 `super`。
- `InMemoryDexClassLoader` 必须使用 API 29 的三参数数组构造器显式传入 Native 搜索路径，不能假设 parent 会继承该路径。`NativeLibrarySearchPathResolver` 只使用 `ApplicationInfo.nativeLibraryDir`、`sourceDir`、`flags & FLAG_EXTRACT_NATIVE_LIBS`、公开 `Process.is64Bit()` 及对应 `Build.SUPPORTED_32_BIT_ABIS`/`SUPPORTED_64_BIT_ABIS`；它通过有界 ZIP 清单选出当前进程首个实际存在的 ABI，并按固定顺序生成“可读 `nativeLibraryDir`（若有）+ `sourceDir!/lib/<selectedAbi>`”，以 `File.pathSeparator` 拼接，拒绝无匹配 ABI、重复 ABI 目录或非规范 SO 路径。
- PoC 在壳 DEX 中使用 M0-03 固定、校验来源的 Android `apksig`，以 `new ApkVerifier.Builder(new File(applicationInfo.sourceDir)).setMinCheckedPlatformVersion(29).build().verify()` 验证当前 APK；要求验证成功且当前 signer 数严格为 `1`，并记录 DER certificate SHA-256。不得调用只存在于 `apksig` 内部 package 的 API。
- `apksig` 官方定位为 Host 侧纯 Java 库，不能仅凭“可编译”视为 Android Runtime 兼容。PoC 必须用 release/R8 后的真实 bootstrap DEX 在规定设备验证类链接、JCA provider、内存和冷启动；R8 输出只允许保留 `ApkVerifier` 所需路径，静态扫描不得出现 `ApkSigner`、`ApkSignerEngine`、私钥入口或 `com.android.apksig.internal.*` 的直接项目引用。
- 启动回调不得调用需要 `Context` 的 `PackageManager`。Context 可用后只做测试断言：`SigningInfo` 观察到的当前证书摘要必须与早期 `apksig` 结果一致；该后置断言不解锁 payload。
- 分支名固定为 `spike/m0-05-application-factory-provider-jni-poc`，Issue 标题固定为 `[M0-05] Application, factory, provider, and JNI PoC`，仅允许一个关联 PR。

## Public Interfaces

- metadata：`ah.runtime.original_application`。
- metadata：`ah.runtime.original_app_component_factory`。
- metadata：`ah.runtime.has_original_app_component_factory`。
- metadata：`ah.runtime.container_asset`。
- metadata：`ah.runtime.container_major`。
- metadata：`ah.runtime.signer_policy_version`。
- metadata：`ah.runtime.risk_policy_version`。
- Shell factory：`ah.runtime.bootstrap.ShellAppComponentFactory`。
- 稳定 probe 事件追加 `ORIGINAL_FACTORY_CREATED`、`PROVIDER_CREATED`、`APPLICATION_ON_CREATE`、`JNI_LOADED`。
- 启动早期 PoC 接口：`EarlySignerProbe.verify(ApplicationInfo): EarlySignerResult`；它只接受 Framework 参数，不接受调用方路径或 `Context`。
- Native 搜索路径 PoC 接口：`NativeLibrarySearchPathResolver.resolve(ApplicationInfo): NativeLibrarySearchPath`；结果只暴露选中 ABI、是否使用 extracted/APK 路径和供三参数构造器消费的路径，不接受调用方路径。
- 稳定 probe 事件追加 `EARLY_SIGNER_VERIFIED` 与 `EARLY_METADATA_VERIFIED`；失败码：factory 加载 `AAH-P002`，委托异常 `AAH-P003`，JNI 失败 `AAH-P004`，早期 signer 不可读/无效/非唯一/不一致分别为 `AAH-P005` 至 `AAH-P008`，早期 metadata 缺失/类型不符为 `AAH-P009`。

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
5. `android:extractNativeLibs=true` 与 `false` 两类 fixture 的 `System.loadLibrary("fixture_jni")` 均成功，JNI 方法在 x86_64 与 arm64 返回相同固定值；删除选中 ABI、伪造额外 ABI 或传入不规范 ZIP native 路径时在业务 JNI 调用前稳定失败。
6. 无原始 factory fixture 通过同一生命周期测试；错误 factory 类名产生 `AAH-P002` 且不回退。
7. 安装前后文件扫描没有明文 DEX，静态/动态扫描没有 hidden API 使用。
8. 冻结的 metadata 与委托契约被 M1-03、M2-01 的任务或设计文档引用。
9. `EARLY_SIGNER_VERIFIED < LOADER_CREATED` 在全部规定设备成立；更换测试 signer、构造多个当前 signer、截断签名块或使 `sourceDir` 不可读时，均在 `LOADER_CREATED` 前以对应错误失败。
10. 后置 `SigningInfo` 摘要与早期 `apksig` 摘要逐字节一致；静态扫描证明启动门禁不引用 `Context`、`PackageManager`、`ActivityThread`、`LoadedApk` 或 hidden API。
11. 规范 payload entry 可在 `LOADER_CREATED` 前从 `sourceDir` 只读定位；重复名称、DEFLATE、data descriptor、CRC/长度不一致和截断 ZIP 分别以稳定错误失败且不分配完整 payload。
12. `EARLY_METADATA_VERIFIED < LOADER_CREATED` 在全部规定环境成立，七个键的值与类型逐项匹配 M1-03 合同；删除键、改错类型或使回调收到空 `metaData` 时以 `AAH-P009` 失败且不调用 `PackageManager`。
13. release/R8 bootstrap 在全部规定设备无缺类、JCA 或 verifier 行为差异；DEX 扫描不存在签名执行类，报告给出引入 verifier 前后的 bootstrap DEX 字节增量、冷启动 p50/p95 和峰值内存。

## Required Tests

- 有/无自定义 factory 的参数化 instrumentation test。
- 五类组件委托、Provider/Application 顺序、多 DEX 跨类调用测试。
- x86_64/arm64、extracted/direct-from-APK JNI 加载和错误/重复 ABI 负向测试。
- factory 不存在、构造失败、委托抛错的错误码与 cause 保留测试。
- API 29/36 各 20 次冷启动稳定性和明文落盘扫描。
- 早期 signer 的同 signer、异 signer、多当前 signer、损坏签名块、不可读路径与后置 `SigningInfo` 交叉验证测试。
- 真实 `instantiateClassLoader` 回调的七键 typed metadata 正向测试，以及缺失、错型、空 Bundle 负向测试。
- release/R8 bootstrap 的 on-device linkage、签名能力裁剪、JCA provider、体积、冷启动和峰值内存测试。

## Required Evidence

- 每个测试环境的 API、进程 bitness、选中 ABI、fingerprint、root 状态、`FLAG_EXTRACT_NATIVE_LIBS` 和脱敏 native library 路径类型。
- 所有命令、退出码、JUnit XML、事件计数和冷启动汇总。
- fixture APK、各 DEX、各 ABI SO 和报告的 SHA-256。
- 生命周期时序、ClassLoader identity、无明文落盘与 hidden API 扫描结果。
- 每个设备的早期 `apksig`/后置 `SigningInfo` 摘要对照、失败码矩阵与额外启动耗时。
- R8 mapping/usage 扫描摘要、bootstrap DEX 增量和 verifier 峰值内存；不得附带证书或设备路径原文。
- 提交 SHA、Issue 与唯一 PR 链接，以及 M0 compatibility gate 结论。

## Likely Files

- `fixtures/android/src/compatFixture/`
- `fixtures/android/src/androidTest/`
- `fixtures/android/src/main/cpp/`
- `docs/evidence/M0-05/`

## Dependencies and Blockers

- M0-04 的公开 API gate 必须为 pass。
- 任一规定 API/ABI 上无法在回调中只用 `ApplicationInfo.sourceDir` 和固定 `apksig` 得到唯一当前 signer，或无法从同一 Framework `ApplicationInfo.metaData` 取得七个 typed 保留键时，本任务必须 blocked，并回到 ADR 0003 缩减或终止方案；不得改用 hidden API 获取 Context。
- 缺少 arm64 非 root 环境时不能把任务标记为 done。
- 任一组件生命周期被破坏、JNI search path 失效或需要 hidden API 时，M1/M2 保持 blocked。
- 固定 `apksig` 无法在任一规定 Runtime 环境完成 release/R8 后验证，或无法裁掉签名执行能力时，本任务 blocked；不得悄悄改用未审计的自制 APK signature parser。

## Agent Handoff Requirements

- 本任务固定使用分支 `spike/m0-05-application-factory-provider-jni-poc`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、设备环境、产物 SHA-256、事件时序和兼容性 gate 结论。
- worker 不修改根 `HandOff.md`，不顺手实现生产 Runtime 或 Host transformer。
- 若 fixture 与平台真实行为冲突，提交最小复现和 blocked 交接，由 `/root` 决定是否修订 ADR。
