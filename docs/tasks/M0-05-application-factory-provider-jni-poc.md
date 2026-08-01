---
id: M0-05
title: Application、Factory、Provider 与 JNI 兼容性验证
milestone: M0
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-04
  - M0-06
required_skills:
  - implement-runtime-protection
  - validate-protected-apk
security_sensitive: true
---

## Goal

验证 Shell factory 在多 DEX、原始自定义 `Application`、原始自定义 `AppComponentFactory`、启动期 `ContentProvider` 和 JNI 同时存在时仍保持 Android 原始生命周期与类加载语义，并证明在无 `Context` 的 `instantiateClassLoader` 回调中可只用公开 `ApplicationInfo.sourceDir` 完成 signer、ConfigV2 和 payload 的失败关闭门禁。

## Background

M0-04 只证明最小 ClassLoader 接入。M0-05 的首次 API 29 arm64 真机运行证明 `sourceDir` 与早期 signer 可用，但 Framework 回调中的 `ApplicationInfo.metaData` 可以为 `null`。M0-06/ADR 0007 已把启动配置迁移到固定且受认证的 ConfigV2；本任务必须重新验证该合同，不能用 Context、PackageManager 或 hidden API 补读。

## Inputs

- M0-04 通过的 Shell factory PoC。
- M0-06 冻结的 ConfigV2 与 sourceDir 启动合同。
- 仓库源码构建的组合 fixture：两个 DEX、自定义 `Application`、自定义 `AppComponentFactory`、初始化 Provider、Activity、Service 和 JNI 库。
- API 29 与 API 36 的 x86_64 环境，以及 API 29 以上的 arm64 非 root 环境。

## Expected Outputs

- 组合 fixture 与自动化 instrumentation tests。
- Shell factory 对原始 factory 的确定性延迟委托 PoC。
- 生命周期、Provider 顺序、跨 DEX 类解析与 JNI 加载证据。
- API 29/36、ARM/x86 上的启动早期 signer、ConfigV2 认证和 payload 前失败证据。
- 冻结供 M1/M2 使用的启动配置与委托合同。

## In Scope

- 在 `instantiateClassLoader` 中缓存 Framework 传入的 `ApplicationInfo`，完成 signer 与 ConfigV2 门禁后创建 payload loader。
- 仅从同一 `sourceDir` 定位唯一 `assets/ah/runtime/config.bin` 与 `assets/ah/runtime/payload.ahdc`；二者只接受 `STORED`、无 encryption、无 data descriptor 且 CRC/长度一致的规范条目。
- 在任何 payload byte 被解密前，只凭 Framework `ApplicationInfo.sourceDir`/`packageName` 和公开进程 ABI 信息验证当前 APK；对 `Context`、`PackageManager`、`ActivityThread`、`LoadedApk` 或反射的依赖均判定 PoC 失败。
- 严格解析 ConfigV2 的 768-byte 结构、Factory flag/length/UTF-8/zero-fill、版本和 reserved 字段；在固定的非生产测试 signer 摘要验证通过后，把同一已签名 APK 内的 config bytes 标记为 PoC 级 `EARLY_CONFIG_APK_AUTHENTICATED`。
- `EARLY_CONFIG_APK_AUTHENTICATED` 后先创建 provisional payload loader。原始 factory 存在时用该 loader 实例化一次，恰好一次委托其 `instantiateClassLoader`，把非空返回值作为 final loader，再委托 Application、Activity、Service、Receiver 与 Provider 创建；不存在时 provisional loader 直接成为 final loader并保持平台默认组件语义。
- 验证 Provider 早于 `Application.onCreate`、但晚于 loader 创建；验证原始 Application 只创建一次。
- 从 payload DEX 调用 APK 原有 `lib/<abi>/libfixture_jni.so`，覆盖 installer 解压 SO 和从 APK 直接加载 SO 两种模式。

## Out of Scope

- 生产级 CEK envelope、AHDC manifest MAC、完整 config digest 验证、DEX 加密/解密、风险引擎、反调试和四 ABI Runtime 发布；这些分别属于 M1-04、M2-02、M2-03 及后续任务。本任务只验证启动链合同与设备兼容性。
- Flutter、Unity、React Native、热修复框架和已有加固壳。
- 将 ARM-only 原生应用转换为 x86 可运行应用。
- 读取 Manifest metadata、引入配置回退或修改 M1/M2 生产实现。

## Implementation Decisions

- Manifest 保留原始 `android:name`，只把 `android:appComponentFactory` 替换为 `ah.runtime.bootstrap.ShellAppComponentFactory`；不新增 `ah.runtime.*` metadata。
- PoC 构建固定生成 ADR 0006 的 768-byte ConfigV2。原 factory 存在时写入规范化全限定名及 flag/length；缺失时 flag、length 和 512-byte slot 全零。CEK/config-digest 字段只作为格式正确的 PoC bytes，不得宣称已完成生产密码学验证。
- fixture 使用被忽略的一次性非生产证书，并把期望摘要生成为仅供 M0-05 compat bootstrap source set 编译的常量；该值不从 ConfigV2、Manifest 或调用方取得。`apksig` 验证当前 APK且实测 signer 与该期望值一致后，APK Signature Scheme 对同一 APK 内 ConfigV2 bytes 的覆盖构成 PoC 级认证；该固定摘要和签名能力不得进入产品 Runtime 发布物或公共接口。
- 原 Application 不写入 ConfigV2；Shell factory 使用 Framework 传入的 `className` 并在 payload loader 下委托。
- `ApplicationInfo.metaData` 不参与启动，`null` 是必须通过的正向用例；不得通过早期 `Context`/`PackageManager` 补读。
- 原始 factory 的 `instantiateClassLoader` 和五类组件方法都被恰好一次委托。ClassLoader 委托返回 `null` 或任一委托抛出异常时保持稳定错误/cause并失败，不回退到 provisional loader 或 `super`。
- PoC 用 `PocPayloadSession implements AutoCloseable` 拥有可清零 direct buffers 与 provisional loader。进入最终 `READY` 前由局部 `try/finally` 独占；Factory 构造/hook、递归、重入、null 或 final loader 验证失败时恰好一次 close、清零 buffer并清除 provisional/final/factory 引用。close 异常不得覆盖原失败，失败缓存不得保存 throwable、loader、Factory 或 session。
- `InMemoryDexClassLoader` 使用 API 29 三参数数组构造器显式传入 Native 搜索路径。`NativeLibrarySearchPathResolver` 只使用 `ApplicationInfo.nativeLibraryDir`、`sourceDir`、`flags & FLAG_EXTRACT_NATIVE_LIBS`、公开 `Process.is64Bit()` 和对应 ABI 列表；拒绝无匹配 ABI、重复 ABI 目录或非规范 SO 路径。
- 壳 DEX 使用 M0-03 固定、校验来源的 Android `apksig`，要求 APK 验证成功且当前 signer 数严格为 `1`。release/R8 设备测试必须证明类链接、JCA provider 和 verifier 裁剪有效，且无签名执行类或私钥入口。
- Context 可用后只做测试断言：`SigningInfo` 当前证书摘要必须与早期 `apksig` 结果一致；该后置断言不解锁 payload。
- 分支固定为 `spike/m0-05-application-factory-provider-jni-poc`，Issue 标题固定为 `[M0-05] Application, factory, provider, and JNI PoC`，仅允许一个关联 PR。恢复时沿用既有 blocked 分支与 Issue #5，不新建第二 PR。

## Public Interfaces

- 固定资产：`assets/ah/runtime/config.bin`、`assets/ah/runtime/payload.ahdc`。
- Shell factory：`ah.runtime.bootstrap.ShellAppComponentFactory`。
- 稳定事件：`EARLY_SIGNER_VERIFIED`、`EARLY_CONFIG_PARSED`、`EARLY_CONFIG_APK_AUTHENTICATED`、`PROVISIONAL_LOADER_CREATED`、`ORIGINAL_FACTORY_CREATED`、`ORIGINAL_FACTORY_CLASSLOADER_DELEGATED`、`LOADER_CREATED`、`PROVIDER_CREATED`、`APPLICATION_ON_CREATE`、`JNI_LOADED`。`LOADER_CREATED` 专指将返回 Framework 的 final loader；`EARLY_CONFIG_APK_AUTHENTICATED` 只表示 M0-05 的测试 signer/APK 签名覆盖，不等于 M2-03 的完整生产认证。
- `EarlySignerProbe.verify(ApplicationInfo): EarlySignerResult`：只接受 Framework 参数，不接受调用方路径或 `Context`。
- `EarlyConfigProbe.open(ApplicationInfo, EarlySignerResult): EarlyConfigResult`：只定位固定资产；返回值在 authenticated 前不暴露 Factory/策略字段。
- `NativeLibrarySearchPathResolver.resolve(ApplicationInfo): NativeLibrarySearchPath`：只暴露选中 ABI、路径类型和供三参数构造器消费的路径。
- 失败码：factory 加载/构造 `AAH-P002`，ClassLoader 或组件委托异常/非法 null 返回 `AAH-P003`，JNI `AAH-P004`，早期 signer 不可读/无效/非唯一/不一致为 `AAH-P005` 至 `AAH-P008`，配置定位/结构为 `AAH-P009`，配置未被期望测试 signer 的有效 APK 签名覆盖为 `AAH-P010`。

## Security Constraints

- 本 PoC 的 Factory 类名只能来自已通过期望测试 signer/APK 签名覆盖的 ConfigV2，并通过严格 UTF-8、Java 全限定类名、ClassLoader 归属和禁止 Shell 递归校验；生产实现仍必须等待 ADR 0007 的完整认证步骤。
- 不捕获后静默忽略原始 factory 异常，不接受其 ClassLoader 委托的 `null` 返回，不加载网络或可写目录代码。
- `READY` 前失败必须释放 PoC session；只缓存非敏感错误码/消息，不把 Factory cause、APK 路径、loader 或 buffer 保存在静态失败状态。
- 认证完成前不得创建 payload loader、加载业务 JNI 或触发业务探针。
- JNI fixture 只返回固定测试值，不读取设备身份、凭据或外部文件。
- 文件系统扫描必须证明没有 payload DEX 明文落盘。

## Compatibility Requirements

- API 29/36 x86_64 与至少一个 API 29+ arm64 非 root 环境通过。
- 每个平台覆盖 extracted/direct-from-APK 两种 Release/R8 变体。
- 支持 Java/Kotlin、单/多 DEX、自定义 Application/factory 和启动 Provider。
- ARM-only fixture 只在 ARM 环境验证；报告必须明确不能在 x86 环境运行。
- 原 APK 未声明 factory 时保持平台默认组件实例化语义。

## Acceptance Criteria

1. API 29/36 x86_64 与 API 29+ arm64 的两种 Release/R8 变体执行组合 fixture instrumentation 均退出 `0`。
2. 自定义 factory 的事件顺序满足 `EARLY_SIGNER_VERIFIED < EARLY_CONFIG_PARSED < EARLY_CONFIG_APK_AUTHENTICATED < PROVISIONAL_LOADER_CREATED < ORIGINAL_FACTORY_CREATED < ORIGINAL_FACTORY_CLASSLOADER_DELEGATED < LOADER_CREATED < PROVIDER_CREATED < APPLICATION_ON_CREATE`，各关键事件只出现一次；报告不得把 PoC 的 APK 签名覆盖表述为完整生产 config 认证。无原 factory 时不存在两个 `ORIGINAL_FACTORY_*` 事件，provisional 与 final loader identity 相同。
3. 原始 factory 对 `instantiateClassLoader`、Application、Activity、Service、Receiver、Provider 的计数均为 `1`；其 ClassLoader 返回值与 `LOADER_CREATED`/Framework 实际使用的 final loader identity 相同，组件类可由该 final loader 解析。返回 null 或抛错产生 `AAH-P003` 且不回退；无原 factory 时保持平台默认语义。
4. 在 `READY` 前逐项注入 Factory 构造、hook null/异常、递归、重入和 final loader 验证失败，`PocPayloadSession.close()` 计数均为 `1`，direct buffer 清零且静态状态不保留 session/provisional/final/factory；close 自身异常不改变原始错误码。
5. `classes2.dex` 独有类可从 Provider 与 Activity 调用，返回固定断言值。
6. `extractNativeLibs=true/false` 的 `System.loadLibrary("fixture_jni")` 在 x86_64/arm64 均成功；删除选中 ABI、伪造重复 ABI 或非规范 ZIP native 路径时在业务 JNI 前失败。
7. `ApplicationInfo.metaData == null` 与含任意无关 metadata 均通过相同正向矩阵；代码和日志证明未读取七个废弃 `ah.runtime.*` 键。
8. 规范 config/payload entry 可在 loader 前只读定位；重复名称、DEFLATE、data descriptor、CRC/长度不一致、截断 ZIP、未知 major、非零 reserved、尾随字节分别稳定失败且不分配完整 payload。
9. Factory flag/length 不一致、非法 UTF-8、NUL、超长、非零 slot 尾部、非法/递归类名返回 `AAH-P009`；签名后修改 ConfigV2、损坏 APK 签名或改用其他 signer 返回 `AAH-P005` 至 `AAH-P010`，均无 `LOADER_CREATED`。CEK envelope、manifest MAC 和完整 config digest 的生产 tamper matrix 保留给 M1-04/M2-02/M2-03。
10. 后置 `SigningInfo` 摘要与早期 `apksig` 摘要逐字节一致；静态扫描证明启动门禁不引用 `Context`、`PackageManager`、`ActivityThread`、`LoadedApk` 或 hidden API。
11. 安装前后文件扫描没有明文 DEX；API 29/36 各变体 20 次冷启动无超时/残留模拟器，报告包含 p50/p95 和峰值内存。
12. release/R8 bootstrap 无缺类、JCA 或 verifier 行为差异；DEX 扫描不存在签名执行类，报告包含 verifier 引入前后字节增量。
13. 冻结合同被 M1/M2 任务卡引用，独立 `m0_05_security_review` 对冻结设备证据和提交给出 PASS 后才可推送分支或创建 PR。

## Required Tests

- 有/无自定义 factory、Framework `metaData` null/非空的参数化 instrumentation。
- 原 Factory ClassLoader hook 与五类组件委托、provisional/final loader identity、Provider/Application 顺序、多 DEX 跨类调用。
- x86_64/arm64、extracted/direct-from-APK JNI 正负向矩阵。
- ConfigV2 ZIP 结构、结构字段、Factory 编码、签名后 byte tamper 和异 signer matrix。
- factory 不存在、构造失败、ClassLoader 委托 null/抛错、组件委托抛错的错误码与 cause 保留。
- `READY` 前各失败点的 session close 恰好一次、direct buffer 清零、部分引用释放和 cleanup-error precedence 测试。
- API 29/36 各变体 20 次有整体超时与强制清理的冷启动测试及明文落盘扫描。
- 早期 signer 同/异/多当前 signer、损坏签名块、不可读 sourceDir 与后置 `SigningInfo` 交叉验证。
- release/R8 on-device linkage、裁剪、JCA provider、体积、冷启动和峰值内存。

## Required Evidence

- 每个环境的 API、进程 bitness、ABI、fingerprint、root 状态、`FLAG_EXTRACT_NATIVE_LIBS` 和 Native 路径类型。
- 所有命令、退出码、JUnit XML、事件计数、冷启动统计、超时与清理结果。
- fixture APK、各 DEX、各 ABI SO、ConfigV2、AHDC 和报告 SHA-256；记录测试 signer 摘要但不提交证书或私钥。
- 生命周期时序、ClassLoader identity、无明文落盘与 hidden API/废弃 metadata 扫描结果。
- 每台设备的早期 `apksig`/后置 `SigningInfo` 摘要对照和完整失败码矩阵。
- R8 mapping/usage 扫描、bootstrap DEX 增量和 verifier 峰值内存；不得附证书或设备路径原文。
- 冻结提交 SHA、独立复核结论、Issue #5、唯一 PR 和双平台 CI 链接。

## Likely Files

- `fixtures/android/src/compatFixture/`
- `fixtures/android/src/androidTest/`
- `fixtures/android/src/main/cpp/`
- `docs/evidence/M0-05/`

## Dependencies and Blockers

- M0-04 与 M0-06 必须均已验收并合并。
- 任一规定 API/ABI 无法只用 Framework `ApplicationInfo`、固定 `apksig` 和固定 sourceDir 资产完成门禁时，本任务 blocked；不得改用 hidden API 或 Context 回退。
- 缺少 arm64 非 root 环境、双 x86_64 CI 环境、任一生命周期/JNI路径失败或独立复核未通过时不能标记 done。
- 固定 `apksig` 无法在任一规定 Runtime 完成 release/R8 验证或无法裁掉签名执行能力时，本任务 blocked。

## Agent Handoff Requirements

- 固定使用既有分支 `spike/m0-05-application-factory-provider-jni-poc`、Issue #5 和一个 PR。
- 完成状态必须提供命令、退出码、设备环境、产物 SHA-256、事件时序、性能/清理数据和兼容性 gate 结论。
- worker 不修改根 `HandOff.md`，不顺手实现生产 Runtime 或 Host transformer。
- fixture 与平台真实行为再次冲突时提交最小复现和 blocked 交接，由 `/root` 决定是否修订 ADR。
