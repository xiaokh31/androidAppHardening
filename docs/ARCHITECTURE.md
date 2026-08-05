# 系统架构

## 1. 架构原则

1. Host 与 Runtime 通过版本化、可验证的 AHDC 容器和 ConfigV2 通信；Manifest 只接入固定 Shell Factory。
2. 输入 APK 只读，所有变更在独立工作目录和新输出文件上完成。
3. 输出始终未签名，签名身份验证不等于签名操作。
4. Runtime 只使用 API 29 起可用的公开 Android API。
5. 加密必须提供机密性和完整性；本地密钥保护只提高逆向成本。
6. 不支持的输入在写出产物前失败关闭。
7. 解析、转换、加密、打包和验证阶段有明确边界与稳定错误语义。

## 2. 上下文

```text
APK owner
  |
  | signed standalone APK
  v
Host CLI on Windows or Ubuntu
  |
  | unsigned hardened APK + JSON report
  v
Owner-controlled signing pipeline
  |
  | signed hardened APK
  v
Android 29+ device
  |
  v
Bootstrap Runtime -> verified in-memory business DEX -> original app components
```

产品信任拥有者提供的输入用途，但不信任 APK 文件结构。Host 不信任 ZIP、AXML、DEX、签名块或文件名。Runtime 不信任安装包内容、运行环境或来自容器的长度与偏移。

## 3. Host 逻辑组件

### 3.1 CLI Orchestrator

负责参数校验、阶段编排、临时目录生命周期、稳定退出码和 JSON 报告。不持有签名能力，不允许输入与输出路径指向同一文件。

### 3.2 Untrusted APK Inspector

以流式和有界方式读取 ZIP central directory，检查路径穿越、重复条目、大小溢出、压缩炸弹、保留命名空间冲突和必要条目。解析：

- package name、`minSdk`、`targetSdk`；
- Application 与 `AppComponentFactory`；
- DEX 清单；
- 原生库 ABI；
- 框架、热修复和已有壳特征。

检查器只生成不可变分析模型，不修改输入。

### 3.3 Signer Policy

使用固定 Android `apksig` 验证输入，并输出唯一当前 signer 的证书 SHA-256 和可验证轮换历史。安全字段按 [ADR-0008](adr/0008-chunk-authenticated-dex-container.md) 沿用的 `SPV1` wire layout 写入容器并受 manifest MAC 认证；Host 报告 JSON 不是 Runtime 信任输入。v0.1 要求输出由相同当前 signer 在产品外签名；多当前 signer、无签名或无效签名均拒绝。

### 3.4 Binary AXML Transformer

只修改已批准字段：

- 将 `android:appComponentFactory` 指向 Shell Factory。

转换器保留原 `android:name`、所有 `<meta-data>`、未知 chunk、string pool 语义、资源 ID、命名空间和未批准属性。转换后重新解析并与单一属性变更白名单做语义差异比较。原 Factory 由 M1-01 的分析模型传给 ConfigV2 builder，不再写入 Manifest metadata。

### 3.5 DEX Container Builder

按原 DEX 序号稳定排序，为每个 DEX 派生独立 record key 和随机 nonce prefix，把连续 zlib 流按 64 KiB chunk 使用 AES-256-GCM 独立认证加密，将 160-byte HeaderV2、`SPV1`、128-byte records、32-byte chunk table 和 payload 写入 `assets/ah/runtime/payload.ahdc`；同时生成 ADR 0006 的 768-byte ConfigV2 `config.bin`，其 SHA-256 受容器 manifest MAC 绑定。M1-01 对输入 package name 的精确 UTF-8 bytes 计算 SHA-256，并把规范化原 Factory 交给 ConfigV2 builder；builder 重算并交叉核对 package 摘要，该摘要参与 KEK 与每个 chunk 的 GCM AAD，Runtime 只从 Framework `ApplicationInfo.packageName` 重算。容器格式见 [ADR-0008](adr/0008-chunk-authenticated-dex-container.md)。

### 3.6 Runtime Assembler

注入：

- bootstrap DEX；
- 按 [ADR-0005](adr/0005-runtime-abi-policy.md) 选择的 Runtime：无业务 Native 库时注入 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，存在业务 Native 库时只注入输入已提供的受支持 ABI；
- 版本化配置与密钥保护材料。

所有项目条目使用 `ah.runtime` Java package 与 `assets/ah/runtime/` 命名空间。输入已占用保留命名空间时拒绝，不覆盖。

### 3.7 APK Repacker

从输入逐条复制允许保留的原始压缩数据，排除原业务 DEX、失效签名材料和被替换 Manifest，加入新条目，标准化新增条目的时间戳与顺序，执行对齐并写到临时输出。全部验证通过后才原子发布到目标路径。

### 3.8 Output Verifier

验证 ZIP、AXML、资源引用、DEX 清单、容器、ABI、签名缺失状态、对齐和保留条目哈希。禁止仅依赖打包器“无异常”作为成功依据。

## 4. Runtime 逻辑组件

### 4.1 Shell AppComponentFactory

系统在组件创建前实例化 Shell Factory。Shell 在 `instantiateClassLoader` 中：

1. 把 Framework 传入的 `ApplicationInfo` 和 shell loader 交给唯一 `RuntimeStartupGuard`；不读取 `ApplicationInfo.metaData`。
2. Guard 使用 `ApplicationInfo.sourceDir` 与固定 `apksig` 验证当前已安装 APK并取得唯一 signer，再定位固定 ConfigV2/AHDC 资产并做有界预读；该回调没有 `Context`，不调用 `PackageManager`。
3. Guard 调用 Native Loader 按 ADR 0007 认证 signer policy、完整 ConfigV2 和容器；只有认证后才暴露原 Factory 与策略配置。
4. Guard 建立 provisional payload ClassLoader，并返回拥有其内存生命周期和已认证启动配置的 `VerifiedPayloadSession`。
5. 原 Factory 存在时，Shell 用 provisional loader 实例化一次，再恰好一次委托其 `instantiateClassLoader`；非空返回值成为 final loader。无原 Factory 时 provisional loader 直接成为 final loader。
6. `READY` 前 session 由当前引导调用独占；任何 Factory 构造/hook、递归、重入或 final loader 验证失败都在 `finally` 恰好一次 close session 并清除部分引用。成功时才把 session、provisional/final loader 和原 Factory 转移为进程级强引用并返回 final loader；随后把 Application、Activity、Service、Receiver 和 Provider 创建委托给同一 Factory。原 Application 使用 Framework 传入的 `className`。

没有原 Factory 时使用平台默认实例化语义。`:runtime:bootstrap` 只编译依赖 `:runtime:policy` 的 guard API；`:runtime:native` 是 policy 的非传递 implementation dependency，bootstrap 不得导入低层 loader。不得通过隐藏 API 获取 Context 或修改系统 ClassLoader 内部字段。

### 4.2 Native Loader

Native Loader 的顺序固定为：无 payload 分配地检查容器结构边界，恢复每包内容密钥，验证覆盖 HeaderV2、`SPV1`、record table 与 chunk table 的 manifest MAC，再逐 chunk 使用一次性 AES-GCM API 验证 tag。不存在 record-level tag；只有当前 chunk 的 tag 成功后，该 chunk 的已认证压缩明文才允许进入所属 record 的唯一连续 zlib-wrapped DEFLATE 解压器，任何 Provider 在最终 tag 前返回的 plaintext 均不得消费。解压必须恰好命中 record 的原始 DEX 长度和 SHA-256，拒绝 dictionary、尾随/拼接流、checksum 错误和超过单 DEX/总 payload 上限的输出。Native handle 创建前以事务 owner 持有 completed/partial mappings，任何失败都全量清理。全部 DEX 成功后、内部 handle 返回前，CEK/KEK/派生 key、AAD、认证后压缩 chunk、inflater/crypto scratch 全部清零销毁，只有 completed mappings 和同一认证快照及成功 package binding 派生的无秘密 `AuthenticatedPayloadMetadata` 进入 handle；该 metadata 含 32-byte `package_name_sha256`、当前 signer 和有序 lineage 的防御性副本。`PayloadRuntime.openVerified` 用 primitive handle 和 allocation-free `finally` 覆盖 native metadata bytes/对象、buffers、search path、`InMemoryDexClassLoader` 与 `LoadedPayload` 构造；提交前失败恰好 close handle 一次、清除部分引用、保留主错误且不暴露对象。它返回的 `LoadedPayload` 是 M2-02 到 M2-03 的内部模块交接对象，只公开 loader、authenticated metadata 与 close；Guard 必须常量时间复比较 package/current signer 并按顺序逐项复比较 lineage，最终 bootstrap 发布边界才是完整 `VerifiedPayloadSession`。成功后 DEX mappings 保持到 ClassLoader 生命周期结束时清零/unmap。Native 搜索路径按 M0-05 合同从 `ApplicationInfo`、公开进程 ABI 与当前 APK 清单派生，同时覆盖 extracted 和 APK 内直接加载 SO。不得将明文 DEX 写入 code cache、临时目录或外部存储，也不得反射复制 parent loader 的 path list。

多 DEX 的类查找顺序必须与输入的 `classes.dex`、`classes2.dex` 顺序一致。

### 4.3 Signer and Integrity Guard

唯一 `RuntimeStartupGuard` 通过 Framework 提供的 `ApplicationInfo.sourceDir` 只读验证当前安装 APK，使用与 Host 相同的固定 `apksig` 和最低平台 29 语义计算证书 SHA-256。Guard 先与有界但未认证的 `SPV1` 预读值做常量时间预比较，再把实测摘要交给 Native；Native 恢复 CEK、验证覆盖 `SPV1` 的 manifest MAC，并对已认证当前摘要做第二次比较。随后验证：

- 容器 magic、版本、长度和记录边界；
- header 认证信息；
- 每个 canonical chunk 的 GCM tag、record/chunk 序号、声明大小和连续 offset；不存在每 DEX/record tag；
- bootstrap/Native 配置的一致性。

任何强完整性失败在业务类加载前终止。错误对调试构建可分类，对发行构建只暴露稳定、非敏感原因。

Guard 只能从 `LoadedPayload.authenticatedMetadata()` 构造 `VerifiedStartupConfiguration`，不重读 ConfigV2 或使用未认证预读。取得 `LoadedPayload` 后至完整 `VerifiedPayloadSession` 返回前由本地 `committed=false`/`finally` 独占；identity/config/session 构造或 return 前异常/OOM 都恰好 close 一次并清除部分引用。完整 session 才是向 bootstrap 的发布边界；其后 ADR 0007 的 READY 前所有权规则继续生效。

### 4.4 Environment Risk Engine

风险引擎将多个信号规范化为版本化决策，不允许启发式环境信号直接阻止启动。v0.1 输出固定为：

- `allow`：`LOW`，继续正常启动并保留基础内存控制；
- `degrade`：`MEDIUM` 或 `HIGH`，逐级增强内存保护、降低诊断暴露并提高校验频率。

`deny` 不属于 v0.1 环境风险引擎输出。signer、AEAD 和受认证完整性失败由独立 Guard 直接 fail closed，不能通过风险分数降低或覆盖。

### 4.5 Memory Exposure Controls

控制包括按需解密、短生命周期缓冲、显式清零、避免内存重复副本、禁止明文落盘和减少敏感 crash 内容。这些控制不阻止拥有进程调试或内核能力的攻击者读取内存。

## 5. 物理产物布局

规划的源码模块边界为：

```text
host/cli
host/apk-inspector
host/axml
host/container
host/repacker
runtime/bootstrap
runtime/native
runtime/policy
fixtures/android
integration-tests
benchmarks/host
benchmarks/android
tools/validation
distribution
```

规划的输出 APK 关键条目如下；`lib/` 条目是项目可用全集，单个输出按 ADR-0005 取合法子集：

```text
AndroidManifest.xml
classes.dex
assets/ah/runtime/payload.ahdc
assets/ah/runtime/config.bin
lib/armeabi-v7a/libah_runtime.so
lib/arm64-v8a/libah_runtime.so
lib/x86/libah_runtime.so
lib/x86_64/libah_runtime.so
```

`classes.dex` 只包含启动所需项目代码，不包含原业务类。原输入中的非 DEX 内容按重打包规则保留。

## 6. 跨组件合同

### 6.1 Container Contract

容器以 ASCII magic `AHDC` 开始，使用 little-endian 固定宽度整数，当前格式版本为 `2`。逐字节 layout 以 ADR 0008 为唯一来源：160-byte `HeaderV2`、可变长 `SPV1`、`dex_count * 128` record table、`chunk_count * 32` chunk table 和无空洞 Payload。manifest MAC 覆盖 HeaderV2、完整 `SPV1`、record table 与 chunk table；每个 canonical 64 KiB 压缩 chunk 由独立 GCM tag 认证，tag 成功后才进入该 DEX 的连续 zlib inflater。未知 major/version/flags、AHDC v1、非法 signer policy、乱序/重叠 chunk 或尾随数据必须拒绝。

离线恢复材料以 ADR 0006 为唯一来源：`config.bin` 固定 768 bytes，四 ABI template 各有一个 104-byte `.ah_share_v1` slot。M1-05 只 materialize 选中 ABI 的 slot，不 patch bootstrap DEX；Runtime 必须把 config SHA-256、build ID、key slot、signer、Framework package name、ABI ID、CEK envelope 与 AHDC manifest 串成同一失败关闭链。

### 6.2 Startup Configuration Contract

启动只从同一 `ApplicationInfo.sourceDir` 读取两个固定 ZIP 条目：

- `assets/ah/runtime/config.bin`：ADR 0006 的 768-byte ConfigV2；
- `assets/ah/runtime/payload.ahdc`：ADR 0008 的 AHDC v2。

二者必须是唯一规范 `STORED` 条目，且无 encryption/data descriptor、CRC/长度一致。路径和名称是编译期常量，生产接口不接受调用方覆盖。ConfigV2 的 Factory/策略字段只有在 ADR 0007 的完整认证顺序结束后才可使用。

Manifest 的壳入口固定为 `ah.runtime.bootstrap.ShellAppComponentFactory`；原 `android:name` 与既有 metadata 均保留。`ApplicationInfo.metaData` 可为 `null` 且不参与启动。原 Factory 在 Host 侧规范化后进入 ConfigV2；原 Application 由 Framework `className` 提供，Runtime 不根据 package name 猜测。

### 6.3 Error Contract

错误码分为：

```text
INPUT_*
COMPAT_*
SIGNER_*
AXML_*
CONTAINER_*
PACKAGE_*
OUTPUT_*
RUNTIME_*
INTERNAL_*
```

相同失败原因在不同 Host 操作系统返回相同错误码。错误消息可以补充上下文，但自动化不得解析自然语言消息。

### 6.4 Report Contract

JSON 报告使用独立整数 `schema_version`。新增可选字段不改变 major schema；删除字段、改变类型或语义必须增加 major schema。路径只记录用户传入的相对表示或经脱敏后的文件名。

## 7. 启动时序

```text
Android process creation
-> instantiate Shell AppComponentFactory
-> Shell.instantiateClassLoader
-> verify ApplicationInfo.sourceDir with pinned apksig, no Context
-> locate and bounded-parse fixed ConfigV2 and AHDC entries
-> compare exactly one installed signer with pre-read binding
-> recover protected content key
-> verify manifest MAC over HeaderV2, SPV1, record table and chunk table
-> compare full ConfigV2 digest from authenticated header
-> compare authenticated SPV1 signer with measured installed signer
-> verify each canonical chunk with one-shot GCM
-> feed only the authenticated chunk into its record's continuous bounded zlib inflater
-> verify original DEX length and SHA-256
-> create the Native handle and authenticated metadata from the same verified snapshot
-> build LoadedPayload with its provisional InMemoryDexClassLoader chain
-> Guard constructs signer identity and verified startup configuration only from measured identity and LoadedPayload metadata
-> atomically return VerifiedPayloadSession or close LoadedPayload exactly once
-> expose authenticated Factory and policy configuration through that complete session
-> instantiate original AppComponentFactory when declared
-> delegate original Factory instantiateClassLoader exactly once
-> select and return final payload ClassLoader
-> create original Application and providers through delegated semantics
-> run original application
```

业务类不得在 signer 和容器认证完成前执行。

## 8. 失败与回滚

Host 任一阶段失败时：

- 关闭文件句柄；
- 清零密钥与明文缓冲；
- 删除本次临时工作目录；
- 保持输入不变；
- 不发布目标 APK；
- 尽最大可能写出失败 JSON 报告。

Runtime 任一强校验失败时，不尝试回退到磁盘解密、不加载原业务 DEX、不忽略认证错误。Guard 已返回 session 但引导尚未进入 `READY` 时，Factory 构造/hook、递归、重入、null 或 final loader 验证失败必须恰好一次关闭 session，释放 Native handle、清零仍可安全清理的密钥/直接缓冲，清除 provisional/final/factory 强引用，并只缓存非敏感稳定错误；close 异常不得覆盖原失败。启发式环境风险仅按版本化策略处理。

## 9. 架构约束验证

以下检查进入 CI：

- Host 代码不得包含签名凭据参数或调用签名工具的生产路径。
- Runtime 不得引用 Android hidden API。
- 输出不得含原业务 DEX 条目或 DEX 明文临时文件。
- 四 ABI 库名称与导出接口一致。
- 容器 parser 对所有长度、偏移和计数执行溢出安全边界检查。
- Manifest 差异必须属于明确白名单。
