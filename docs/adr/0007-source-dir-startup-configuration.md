# ADR 0007: 使用 sourceDir 中的受认证启动配置

## Status

Accepted

## Context

M0-05 在已授权的 API 29 arm64 非 root 设备上证明：`AppComponentFactory.instantiateClassLoader` 的真实 Framework 回调可读取 `ApplicationInfo.sourceDir`，但 `ApplicationInfo.metaData` 可以为 `null`，即使安装 APK 的 Binary AXML 中存在全部预期 `<meta-data>` 项。把该 Bundle 作为启动安全门禁会把厂商实现差异变成不可恢复的兼容性失败。

该回调没有 `Context`。通过 `PackageManager` 补读、反射 `ActivityThread`/`LoadedApk` 或使用 hidden API 都违反 ADR 0003 的公开 API 边界。启动所需的原始 Factory 名称和策略版本还必须在业务 DEX 加载前完成认证，不能信任调用方参数或未认证的 Manifest 字段。

## Decision

1. 启动配置唯一来源改为当前安装 APK 的固定 ZIP 条目 `assets/ah/runtime/config.bin`。Runtime 只从 Framework 传入的绝对 `ApplicationInfo.sourceDir` 打开该 APK；不接受调用方路径、asset 名或外部配置。
2. 条目必须唯一、名称规范、`STORED`、未加密、无 data descriptor，且 central directory/local header、CRC、压缩长度和原始长度一致。重复、压缩、截断或不一致均在 payload loader 创建前失败。
3. `config.bin` 使用 ADR 0006 的固定 768-byte `ConfigV2`。它携带容器、signer/risk policy 版本、build/key slot、signer binding、密钥恢复材料和可选的原始 `AppComponentFactory` 全限定类名。
4. Runtime 可以在认证前有界解析 ConfigV2 以恢复 CEK，但其所有字段均保持不可信。只有在 CEK envelope、AHDC manifest MAC、完整 config SHA-256、实测 signer、已认证 `SPV1`、build ID 和 key slot 全部交叉验证后，才能暴露或使用原始 Factory 名称和策略版本。
5. Manifest 转换只替换 `android:appComponentFactory` 为 `ah.runtime.bootstrap.ShellAppComponentFactory`，保留原 `android:name` 和其他应用语义；不再新增 `ah.runtime.*` metadata。
6. 原始 Application 类名不重复写入 ConfigV2。Framework 传入 `instantiateApplication(ClassLoader, String)` 的 `className` 保留原 Manifest 语义，并在已验证 payload loader 下交给原 Factory或平台默认实现。
7. `ApplicationInfo.metaData` 可以为 `null`，Runtime 不读取它，也不以它决定启动。Context 可用后的 `PackageManager` 读取只允许作为测试诊断，不能解锁 payload 或改变决定。
8. 完整认证后先创建 provisional payload loader。原 Factory 存在时用它实例化 Factory并恰好一次委托 `instantiateClassLoader`，非空返回值成为 final loader；无原 Factory 时 provisional 即 final。五类组件创建再使用同一 Factory和 final loader。
9. `VerifiedPayloadSession` 在 `READY` 所有权转移前由引导调用栈独占。Factory 构造/hook、递归、重入或 final loader 验证任一失败时必须恰好一次关闭 session、清除部分初始化引用且只缓存稳定非敏感错误；清理失败不得替换原失败或允许回退。
10. M2-02 只从同一个已认证 Native handle 的 ConfigV2/`SPV1` 快照和本次成功认证使用的 Framework package binding 构造不可变 `AuthenticatedPayloadMetadata`，随 `LoadedPayload` 交给 M2-03。它仅含可选原 Factory、container/signer/risk policy version、build/key slot、当前 signer、lineage 与 32-byte `package_name_sha256` 的防御性副本，不含 share、CEK、nonce、wrapped ciphertext/tag 或原始 config bytes。M2-03 必须把该 package digest 与同一 `ApplicationInfo.packageName` 的精确 UTF-8 SHA-256 常量时间比较，并把 metadata lineage 与实测 lineage 按旧到新顺序逐项等值比较；只能从该对象构造 `VerifiedStartupConfiguration`，不得重读 ConfigV2、使用未认证预读或接受调用方覆盖。
11. 真正的 Guard 发布边界是 `RuntimeStartupGuard.openVerifiedPayload` 返回完整 `VerifiedPayloadSession`。Guard 取得 `LoadedPayload` 后用本地唯一 owner、`committed=false` 与 `finally` 覆盖 signer identity、authenticated configuration、session 构造和 return 前窗口；任一异常/OOM 都恰好一次 close `LoadedPayload`、清除部分引用并保留主错误。`VerifiedPayloadSession` 构造器不得注册或泄露 `this`；只有完整结果构造后才无失败地提交并返回。
12. Framework package 与实测 current signer 在 Native 恢复 CEK、认证 manifest/chunks 前已经进入失败关闭的密码学 binding；Guard 对 metadata 的 package/current signer/lineage 复比较是同 handle 跨模块一致性门禁。M2-02 仅可在完整 Native 认证、全部 DEX 摘要成功且 metadata 对象先构造后，创建尚未使用的 provisional `InMemoryDexClassLoader` 并把它封装在内部 `LoadedPayload` 中；在 Guard 复比较成功并原子返回完整 session 前，任何代码不得调用该 loader 查找/解析 payload 类、实例化 Factory、注册引用或向 bootstrap 暴露 loader/metadata。复比较失败按第 11 条恰好一次关闭。

## Authentication Order

固定顺序为：

1. 用固定 `apksig` 从 `sourceDir` 验证安装 APK并取得唯一当前 signer；
2. 有界定位并解析 ConfigV2 与 AHDC 固定头/`SPV1`；
3. 结合当前 ABI share、Framework package name 和实测 signer 验证 CEK envelope；
4. 验证覆盖 HeaderV2、完整 `SPV1`、record table 与 chunk table 的 manifest MAC；
5. 从已认证 HeaderV2 取得 `config_sha256`，常量时间比较完整 768-byte ConfigV2；
6. 交叉比较 ConfigV2、已认证 `SPV1` 与实测 signer，以及 ConfigV2/AHDC 的 build ID、key slot 和版本；
7. 从同一 Native handle 生成无秘密、不可变的 `AuthenticatedPayloadMetadata`；M2-02 随后可以构造尚未使用、未发布的 provisional loader 并封装完整 `LoadedPayload`，但不得进行任何 payload 类查找/解析或泄露引用。
8. M2-03 常量时间比较 metadata 的 `package_name_sha256` 与 Framework package 重算值、当前 signer，并将其 lineage 与实测 lineage 按旧到新顺序逐项等值比较；仅从该 metadata 构造 `VerifiedStartupConfiguration`，并与 signer identity、`LoadedPayload` 原子封装为 `VerifiedPayloadSession`。完整 session 返回前不得使用 provisional loader、实例化原 Factory 或向 bootstrap 发布对象。

任一步失败都必须清理敏感材料、缓存同一稳定失败并禁止降级加载业务代码。

## Consequences

积极结果：

- 启动合同只依赖 API 29 公开回调中已证明可读的 `sourceDir`；
- 厂商是否填充 `ApplicationInfo.metaData` 不再影响兼容性；
- 原始 Factory 与风险策略和密钥恢复链共享同一认证根；
- Manifest 白名单变更从一个属性加七个 metadata 缩减为单一属性替换。

代价：

- Config major 从预发布 V1 提升为 V2，Host、Java 与四 ABI Native 必须共享新的 golden vector；
- Runtime 在启动早期需要定位两个固定 ZIP 条目；
- 原始 Factory 名称占用固定 512-byte UTF-8 slot，超过上限的输入必须由 Host 拒绝。

## Rejected Alternatives

- 继续依赖 `ApplicationInfo.metaData`：已被真实 API 29 设备证伪为稳定合同。
- 在回调中通过 `PackageManager` 补读：需要尚不存在的 `Context`，并扩大启动边界。
- 反射 Framework 私有对象：违反公开 API 与兼容性不变量。
- 把配置放入可写目录、外部存储或系统属性：来源不可绑定当前 APK且增加篡改面。
- 新增第二套独立认证配置：产生版本、身份和错误语义分叉；ConfigV2 已由 AHDC manifest MAC 间接认证。
- 把原 Application 名称复制进配置：Framework 已按 Manifest 传递 `className`，重复来源会产生歧义。

## Security Impact

ConfigV2 在完整认证前仍是不可信输入。其 Factory 字符串必须先通过长度、严格 UTF-8、NUL、Java 全限定类名与 Shell 递归检查，随后还要等完整 config digest 被已认证 AHDC header 绑定后才能消费。该方案不增加对攻击者控制设备的绝对防护声明。

## Compatibility Impact

API 29+ 的支持不再要求 Framework 回调填充 application metadata。M0-05 必须把 `metaData == null` 作为正向设备用例，同时继续证明不使用 Context、PackageManager 或 hidden API。

ConfigV1 在任何产品发布或实现冻结前被本决策替代；v0.1 reader 只接受 Config major `2`，不提供 ConfigV1 兼容回退。

## Verification

- API 29/36 x86_64 和 API 29+ arm64 的 M0-05 真实回调在 `metaData == null` 时完成 `EARLY_CONFIG_APK_AUTHENTICATED < LOADER_CREATED`；该 PoC 事件仅证明固定测试 signer 的 APK 签名覆盖，不替代 M2-03 的完整生产认证。
- 对 config 条目重复、DEFLATE、data descriptor、CRC/长度错误、截断、未知版本、非零 reserved 和尾随字节逐项失败关闭。
- 对 Factory 长度、严格 UTF-8、NUL、类名语法、flag/length 组合和未使用 slot 非零逐项失败关闭。
- 对 ConfigV2 任一字节、CEK envelope、AHDC manifest MAC、`SPV1`、build/key slot、signer 或 package binding 的篡改均不得创建 payload loader。
- 同一 handle/snapshot 测试证明 `AuthenticatedPayloadMetadata` 只来自已认证 ConfigV2/`SPV1` 与本次成功 package binding，`package_name_sha256`/signer/lineage 所有数组防御性复制且无恢复秘密；M2-03 常量时间比较 package/current signer、按顺序逐项比较 lineage，不重读或消费未认证预读字段。
- Guard 在 `LoadedPayload` 返回后对 signer identity、configuration、session 构造及 return 前分别注入异常/OOM，均恰好一次 close、清除部分引用、不发布 `VerifiedPayloadSession`，cleanup error 不覆盖主错误。
- 在 metadata package/current signer/lineage 任一复比较失败时，断言 provisional loader 的 payload class lookup/resolve 计数为零、无 Factory 调用、无 loader/metadata/session 发布，`LoadedPayload` 恰好关闭一次。
- M1-03 semantic diff 只允许 `android:appComponentFactory` 变化，并证明原 `android:name` 与所有既有 metadata 逐字节语义保持。
- 自定义 Factory fixture 证明其 `instantiateClassLoader` 只调用一次、返回 loader 是 Framework/组件使用的 final loader；null/异常不回退。
- 所有 `READY` 前 Factory 失败路径证明 session 只 close 一次、Native handle 与可清零 buffer 已清理、部分 loader/Factory 引用不可达；close 自身失败不覆盖原错误。
- 静态扫描证明启动链不引用 `PackageManager`、`ActivityThread`、`LoadedApk` 或 hidden API。
