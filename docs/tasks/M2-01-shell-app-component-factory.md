---
id: M2-01
title: "Shell AppComponentFactory 与启动引导"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-05
  - M1-03
  - M1-04
  - M2-03
required_skills:
  - implement-runtime-protection
security_sensitive: true
---

## Goal

实现基于 Android API 29 公共 `AppComponentFactory` 与 `instantiateClassLoader` 接口的壳启动链，在任何应用组件实例化前完成一次性 Runtime 引导，并保持原始 `Application`、`AppComponentFactory`、`ContentProvider` 及其他组件的行为。

## Background

v0.1 仅接收 `minSdk >= 29` 的独立 APK。启动链不得依赖隐藏 API、反射修改系统字段或磁盘明文 DEX；M0-05 的可行性结论和兼容契约是本任务的强制输入。

## Inputs

- M0-05 固化的自定义 `Application`、原始 `AppComponentFactory`、早期 `ContentProvider` 与 JNI 加载顺序。
- M1-03 只替换 Shell Factory 后的 Manifest。
- M1-04 固化的 ConfigV2 与容器定位信息，但本任务不负责解密容器。
- `docs/ARCHITECTURE.md`、`docs/THREAT_MODEL.md` 与 ADR 0003。

## Expected Outputs

- `:runtime:bootstrap` Android Library 模块及其 Java 17 单元测试。
- 壳工厂、引导状态机、原始工厂代理和稳定错误码。
- 面向 M2-02、M2-03、M2-05 的 Runtime 内部接口。
- 覆盖标准应用与自定义启动对象的 instrumentation 测试。

## In Scope

- 覆盖 `instantiateClassLoader`、`instantiateApplication`、`instantiateActivity`、`instantiateService`、`instantiateReceiver` 和 `instantiateProvider`。
- 恢复原始 `Application` 类和原始 `AppComponentFactory` 的实例化语义。
- 并发、重复回调和进程内多组件启动时的幂等引导。
- 主进程及声明组件所在的独立应用进程。

## Out of Scope

- 容器解密与 DEX 加载实现。
- 签名、容器完整性和环境风险算法。
- API 28 及以下兼容。
- AAB、Split APK、跨进程共享解密缓存和任何隐藏 API 方案。

## Implementation Decisions

- 固定 Java package 为 `ah.runtime.bootstrap`，入口类为 `ShellAppComponentFactory`。
- 模块路径固定为 `runtime/bootstrap`，Android Runtime 源码位于 `src/main/java` 并使用 Java 17；不得应用 Kotlin Android plugin。
- `instantiateClassLoader` 只调用一次 `HardeningBootstrap.install(...)`；生产绑定只能调用已完成的 M2-03 `RuntimeStartupGuard.openVerifiedPayload(...)` 并保存返回的 `VerifiedPayloadSession`，不得直接调用 M2-02 的低层 `PayloadRuntime`；状态机固定为 `NEW`、`INSTALLING`、`READY`、`FAILED`。
- Shell 不读取 `ApplicationInfo.metaData`；它为 `null` 或含任意既有应用 metadata 都不得改变启动结果。生产代码不得在无 Context 回调中尝试 `PackageManager`，也不得解析调用方配置。
- 原始 Factory 与策略只从 M2-03 返回的 `VerifiedPayloadSession.startupConfiguration()` 读取。配置必须已完成 ADR 0007 全序列认证；bootstrap 不接触未认证 ConfigV2 bytes。
- 原始工厂为空时使用平台 `AppComponentFactory` 行为；存在时，在 payload `ClassLoader` 可用后实例化并代理所有组件创建方法。原 Application 使用 Framework 传入的 `className`，不从 config 或 package name 重建。
- 检测到递归指向壳工厂、未知已认证配置版本、初始化重入或部分初始化时必须转为 `FAILED`，后续调用返回同一稳定错误，不尝试降级加载原始 DEX。
- 仅使用 Android SDK 公共 API；不得修改 `LoadedApk`、`ActivityThread` 或私有 `ClassLoader` 字段。

## Public Interfaces

- `public final class ShellAppComponentFactory extends AppComponentFactory`
- `final class HardeningBootstrap`，通过静态方法 `static BootstrapResult install(ClassLoader shellLoader, ApplicationInfo applicationInfo)` 提供内部入口。
- `final class BootstrapResult`，通过 `Status.READY`、`Status.FAILURE`、M2-03 的 `ah.runtime.guard.VerifiedPayloadSession` 和稳定错误码表达结果；成功结果必须在进程生命周期内强引用该 session。
- `:runtime:bootstrap` 的生产 compile classpath 只依赖 `:runtime:policy` 的 guard API，不包含 `:runtime:native` 的低层 API；架构测试禁止任何 `ah.runtime.loader` import、反射类名或直接调用。
- 稳定错误码前缀 `AAH-RUNTIME-BOOT-`，错误消息不得包含密钥、DEX 内容或设备敏感路径。

## Security Constraints

- 所有元数据在使用前校验类型、长度、版本和允许字符；异常输入必须 fail closed。
- 引导失败不得回退到未保护 payload、磁盘明文 DEX 或原始未校验类加载器。
- 日志只记录稳定错误码和阶段，不记录容器密钥、证书原文、DEX 字节或完整文件系统路径。
- 本任务提供成本提升与完整性入口，不声称阻止具有进程控制能力的攻击者。

## Compatibility Requirements

- 运行时最低 API 为 29。
- 支持 Java/Kotlin、单 DEX、多 DEX、自定义 `Application`、自定义 `AppComponentFactory`、早期 `ContentProvider` 和 JNI 初始化。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。
- 壳工厂本身必须由基础 APK 的未加密壳 DEX 加载。
- 不改变原始组件类名、进程名、导出属性和初始化顺序。

## Acceptance Criteria

- `./gradlew :runtime:bootstrap:test :runtime:bootstrap:lint` 退出码为 `0`。
- `./gradlew :runtime:bootstrap:connectedCheck` 在 API 29 和项目最高受支持 API 的测试设备上退出码为 `0`。
- 对标准应用、自定义 `Application`、自定义工厂、启动期 `ContentProvider` 和独立进程五类 fixture，组件类均由 payload `ClassLoader` 创建且各进程只安装一次。
- 注入未知 ConfigV2 版本、递归工厂名和初始化重入后，启动均以对应 `AAH-RUNTIME-BOOT-` 错误 fail closed。
- API 29 和最高支持 API 的真实回调在 `ApplicationInfo.metaData == null` 时仍通过；任意无关 metadata 不改变认证结果，静态扫描确认无七个废弃键。
- 静态扫描不存在对隐藏 API、`ActivityThread`、`LoadedApk` 私有字段或磁盘 DEX 输出的调用。

## Required Tests

- 状态机并发、重入、失败缓存和代理选择的 JVM 单元测试。
- 六个 `AppComponentFactory` 实例化入口的 instrumentation 测试。
- 自定义工厂调用顺序、原始 `Application` 恢复、早期 Provider 和多进程回归测试。
- 已认证 Factory 缺失/超长/非法、未知 ConfigV2 版本和递归配置的负向测试，以及 `metaData` null/非空等价测试。

## Required Evidence

- 执行命令、退出码、Gradle 与 Android SDK/设备版本。
- 五类 fixture 的启动日志摘要和测试报告路径。
- 产出 AAR、测试 APK 与报告文件的 SHA-256。
- 公共 API 使用清单及隐藏 API 静态扫描结果。

## Likely Files

- `runtime/bootstrap/build.gradle.kts`
- `runtime/bootstrap/src/main/AndroidManifest.xml`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/HardeningBootstrap.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/BootstrapResult.java`
- `runtime/bootstrap/src/test/`
- `runtime/bootstrap/src/androidTest/`

## Dependencies and Blockers

M0-05 未证明自定义工厂、Provider、JNI 和早期 signer/sourceDir 场景可行，M1-03 未冻结元数据键，或 M2-03 唯一 Guard/session 未验收时不得开始实现。若公共 API 无法满足既定启动顺序，必须提交阻塞交接并回到 ADR 0003 评审，不得自行引入隐藏 API。

## Agent Handoff Requirements

使用分支 `feat/m2-01-shell-app-component-factory`，只处理 Issue `M2-01` 并仅创建一个对应 PR。交接包必须列出变更文件、关键决策、全部命令与退出码、测试设备、失败用例、产物 SHA-256、残余风险和对已冻结 M2-03 Guard/session 的集成证据；不得修改根 `HandOff.md`。
