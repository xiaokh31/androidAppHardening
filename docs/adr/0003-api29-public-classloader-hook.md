# ADR 0003: 使用 API 29 公开 ClassLoader Hook

## Status

Accepted

## Context

业务 DEX 不以普通 APK DEX 条目存在时，Android 必须在创建原 Application、Provider 和其他组件前获得能够解析业务类的 ClassLoader。传统方案常通过反射修改 `LoadedApk`、`BaseDexClassLoader.pathList` 或其他 Framework 私有字段。这些 hidden API 随 Android 版本变化，并受 hidden API enforcement 限制，无法形成稳定兼容性承诺。

Android API 29 的 `AppComponentFactory.instantiateClassLoader(ClassLoader, ApplicationInfo)` 提供公开的组件创建前 ClassLoader 接入点。

## Decision

v0.1 要求输入 `minSdk >= 29`，并以 Shell `AppComponentFactory` 替换 Manifest 中的 factory 声明。

Shell Factory：

1. 在 `instantiateClassLoader` 中只使用 Framework 传入的 `ApplicationInfo`，通过固定版本的 Android `apksig` `ApkVerifier` 以最低平台 29 语义只读验证 `sourceDir` 指向的当前已安装 APK，并取得唯一当前 signer；该回调没有 `Context`，不得伪称能调用 `PackageManager`。
2. 签名结构验证成功后，从同一 `sourceDir` 定位唯一、未压缩且无 data descriptor 的固定 ZIP 条目 `assets/ah/runtime/config.bin` 与 `assets/ah/runtime/payload.ahdc`，对 ConfigV2、容器固定边界和 `SPV1` 预读视图做有界解析，不假设该回调存在 `AssetManager`。
3. 唯一 `RuntimeStartupGuard` 比较实测 signer 与有界预读摘要，再把实测摘要交给 Native Loader；Native 按 ADR 0007 的顺序恢复 CEK、验证覆盖 `SPV1` 的 manifest MAC 和完整 config digest，并将已认证 signer 再次与实测 signer 比较。ConfigV2 的 Factory 和策略字段在此之前不得使用。
4. 通过 Native Loader 将认证通过的 DEX 解密并有界解压到匿名内存。
5. 按原 `classes.dex`、`classes2.dex` 顺序，使用 API 29 的 `InMemoryDexClassLoader(ByteBuffer[], String, ClassLoader)` 构造 provisional payload loader。Native 搜索路径只由 Framework `ApplicationInfo.nativeLibraryDir`/`sourceDir`、公开进程 bitness/ABI 列表与当前 APK 有界 ZIP 清单派生，按“可读 extracted 目录（若有）+ `sourceDir!/lib/<selectedAbi>`”固定顺序传入；不得假设 parent 自动继承业务 SO 路径。
6. 原 Factory 存在时，用 provisional loader 实例化一次，再恰好一次调用其 `instantiateClassLoader(provisionalLoader, applicationInfo)`；委托异常或返回 `null` 均失败关闭，返回值作为 final payload loader。原 Factory 不存在时 provisional loader 直接成为 final loader。
7. 只有 final loader 验证完成且状态转为 `READY` 时，Guard session 所有权才转入进程级状态并与 provisional loader、原 Factory、final loader 一起强引用。此前任一 Factory 构造、递归、重入、ClassLoader 委托 null/异常或 final loader 验证失败，都必须在 `finally` 中恰好一次关闭 session、清除部分引用并缓存非敏感稳定失败；close 异常不得覆盖原始失败或触发回退。
8. 返回 final loader。
9. 对 Application、Activity、Service、Receiver 和 Provider 的创建委托给同一原 Factory；未声明原 Factory 时使用平台默认语义。

Host 只在 Manifest 中把 `android:appComponentFactory` 替换为 Shell Factory，并把规范化原 Factory 写入 ADR 0006 ConfigV2；原 `android:name` 保持不变。Shell 不读取 `ApplicationInfo.metaData`，其为 `null` 也是合法启动条件。原 Application 继续使用 Framework 传给 `instantiateApplication` 的 `className`。启动路径只依赖公开 `ApplicationInfo`/文件 API、固定来源的 `apksig` 库和 ADR 0007 的固定资产，不反射获取 `Context`，不读写 Framework 私有 ClassLoader 字段，也不将明文 DEX 写入磁盘。`PackageManager` 可在 Context 可用后的诊断测试中做一致性复核，但不是启动安全门禁或配置回退路径。

## Consequences

积极结果：

- 接入点具有 Android 公共 API 合同；
- 在 Application 和 Provider 创建前建立业务 ClassLoader；
- 可以通过委托保持自定义 Factory 语义；
- 原 Factory 的 ClassLoader override 与五类组件创建入口均得到恰好一次委托；
- 不依赖厂商 Framework 内部布局；
- 明文 DEX 可直接从内存加载。

代价：

- 最低 API 固定为 29；
- Shell 必须正确覆盖并委托所有组件实例化方法；
- Factory 递归、类加载顺序和多进程启动需要专门 PoC；
- `apksig` 验证增加壳 DEX 体积和启动 I/O，必须纳入大小与冷启动预算；
- `apksig` 官方以设备外使用为主要定位；即使它是纯 Java 库，也必须先由 M0-05 证明 release/R8 后在 API 29/36 ARM/x86 Runtime 可链接、可验证且签名执行类已裁掉，否则本 ADR 的生产路径保持阻塞；
- `InMemoryDexClassLoader` 的内存生命周期仍可被有权限攻击者观察。

## Rejected Alternatives

- 反射替换 `pathList`：依赖 hidden API 和版本内部实现。
- 自定义 Application 中再替换 ClassLoader：Provider 可能已先于 `Application.onCreate` 创建，时机过晚。
- 将解密 DEX 写入 code cache 后使用 `DexClassLoader`：增加明文磁盘暴露和清理风险。
- 降低到 API 26 的内存加载：缺少本决策要求的公开组件前 hook，无法满足完整兼容性合同。
- 不支持自定义 Factory：与 v0.1 明确支持范围冲突。

## Security Impact

业务 DEX 在 signer 和容器认证通过前不加载。公开 API 降低使用不稳定反射造成的绕过和崩溃面。内存加载避免静态明文文件，但不能防止进程内 Hook、调试或内核级读取。

原 Factory 类名在 ConfigV2 完整认证前属于不可信输入；实例化前必须验证 config digest、名称格式、ClassLoader 归属和禁止递归指向 Shell 自身。

## Compatibility Impact

输入和运行设备均要求 API 29 或更高。标准 Java/Kotlin、单/多 DEX、自定义 Application、自定义 Factory 与 Provider 进入强制 fixture。Flutter、Unity、React Native、热修复、插件化、自定义 ClassLoader 框架和已有壳被拒绝。

## Verification

- M0-04 在 API 29 证明 hook 发生在 Application/Provider 业务类解析前。
- M0-05 覆盖默认与自定义 Application、Factory、eager Provider、原 Factory 的 `instantiateClassLoader` 与所有五类组件入口和 JNI。
- M0-05 对 `extractNativeLibs=true/false` 分别证明三参数 `InMemoryDexClassLoader` 能从公开派生的搜索路径加载业务 JNI；无匹配 ABI、重复 ABI 或不规范 SO 路径必须失败关闭。
- M0-05 在 `instantiateClassLoader` 的实际回调中证明 `ApplicationInfo.sourceDir` 可只读访问，固定 `apksig` 能在 API 29/36 的 ARM/x86 环境返回与安装时相同的唯一 signer；异 signer、多个 signer、损坏 APK 必须在 payload 打开前失败。
- M0-05 证明同一回调可从 `sourceDir` 定位唯一 `STORED` ConfigV2 与 payload entry，并完成 PoC 级 `EARLY_CONFIG_APK_AUTHENTICATED < LOADER_CREATED`；重复名称、压缩条目、data descriptor、CRC/长度错误和截断 ZIP 均在 payload 分配前失败。完整生产 ConfigV2 认证仍由 M1-04/M2-02/M2-03 实现和验证。
- M0-05 在 `ApplicationInfo.metaData == null` 时通过相同设备矩阵，且静态扫描证明启动链不引用 `PackageManager`、`ActivityThread`、`LoadedApk` 或 hidden API。
- API 29 至仓库锁定 `compileSdk` 的每个整数 API记录启动事件序列并与未加固 fixture 比较。
- 静态扫描和运行时 strict mode 证明没有 hidden API 使用。
- 文件系统监控证明无明文 DEX 写入。
- 原 Factory 指向 Shell、缺失类或抛出异常时得到稳定失败，而非递归或回退。
- 自定义原 Factory 的时序固定为 `PROVISIONAL_LOADER_CREATED < ORIGINAL_FACTORY_CREATED < ORIGINAL_FACTORY_CLASSLOADER_DELEGATED < LOADER_CREATED`；无原 Factory 时 provisional 与 final loader 相同。
- 在 `READY` 前注入 Factory 构造、hook null/异常、递归或重入失败时，session close 计数恰好为 `1`，Native handle关闭、可清零 buffer 已清理且无 provisional/final/factory 强引用残留。
