# ADR 0003: 使用 API 29 公开 ClassLoader Hook

## Status

Accepted

## Context

业务 DEX 不以普通 APK DEX 条目存在时，Android 必须在创建原 Application、Provider 和其他组件前获得能够解析业务类的 ClassLoader。传统方案常通过反射修改 `LoadedApk`、`BaseDexClassLoader.pathList` 或其他 Framework 私有字段。这些 hidden API 随 Android 版本变化，并受 hidden API enforcement 限制，无法形成稳定兼容性承诺。

Android API 29 的 `AppComponentFactory.instantiateClassLoader(ClassLoader, ApplicationInfo)` 提供公开的组件创建前 ClassLoader 接入点。

## Decision

v0.1 要求输入 `minSdk >= 29`，并以 Shell `AppComponentFactory` 替换 Manifest 中的 factory 声明。

Shell Factory：

1. 在 `instantiateClassLoader` 中验证安装 signer 和容器。
2. 通过 Native Loader 将认证通过的 DEX 解密到有界内存。
3. 按原 `classes.dex`、`classes2.dex` 顺序构建 `InMemoryDexClassLoader` 链。
4. 返回能够解析原业务类的 ClassLoader。
5. 在该 ClassLoader 可用后实例化输入声明的原 `AppComponentFactory`。
6. 对 Application、Activity、Service、Receiver 和 Provider 的创建委托给原 Factory；未声明原 Factory 时使用平台默认语义。

Host 在 Manifest 项目元数据中保存规范化的原 Application 与原 Factory 类名。Runtime 只使用公开 Android API，不反射读写 Framework 私有 ClassLoader 字段，也不将明文 DEX 写入磁盘。

## Consequences

积极结果：

- 接入点具有 Android 公共 API 合同；
- 在 Application 和 Provider 创建前建立业务 ClassLoader；
- 可以通过委托保持自定义 Factory 语义；
- 不依赖厂商 Framework 内部布局；
- 明文 DEX 可直接从内存加载。

代价：

- 最低 API 固定为 29；
- Shell 必须正确覆盖并委托所有组件实例化方法；
- Factory 递归、类加载顺序和多进程启动需要专门 PoC；
- `InMemoryDexClassLoader` 的内存生命周期仍可被有权限攻击者观察。

## Rejected Alternatives

- 反射替换 `pathList`：依赖 hidden API 和版本内部实现。
- 自定义 Application 中再替换 ClassLoader：Provider 可能已先于 `Application.onCreate` 创建，时机过晚。
- 将解密 DEX 写入 code cache 后使用 `DexClassLoader`：增加明文磁盘暴露和清理风险。
- 降低到 API 26 的内存加载：缺少本决策要求的公开组件前 hook，无法满足完整兼容性合同。
- 不支持自定义 Factory：与 v0.1 明确支持范围冲突。

## Security Impact

业务 DEX 在 signer 和容器认证通过前不加载。公开 API 降低使用不稳定反射造成的绕过和崩溃面。内存加载避免静态明文文件，但不能防止进程内 Hook、调试或内核级读取。

原 Factory 类名属于不可信元数据；实例化前必须验证名称格式、ClassLoader 归属和禁止递归指向 Shell 自身。

## Compatibility Impact

输入和运行设备均要求 API 29 或更高。标准 Java/Kotlin、单/多 DEX、自定义 Application、自定义 Factory 与 Provider 进入强制 fixture。Flutter、Unity、React Native、热修复、插件化、自定义 ClassLoader 框架和已有壳被拒绝。

## Verification

- M0-04 在 API 29 证明 hook 发生在 Application/Provider 业务类解析前。
- M0-05 覆盖默认与自定义 Application、Factory、eager Provider、所有组件类型和 JNI。
- API 29 至仓库锁定 `compileSdk` 的每个整数 API记录启动事件序列并与未加固 fixture 比较。
- 静态扫描和运行时 strict mode 证明没有 hidden API 使用。
- 文件系统监控证明无明文 DEX 写入。
- 原 Factory 指向 Shell、缺失类或抛出异常时得到稳定失败，而非递归或回退。
