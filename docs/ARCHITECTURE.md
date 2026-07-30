# 系统架构

## 1. 架构原则

1. Host 与 Runtime 通过版本化、可验证的容器和 Manifest 元数据通信。
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

使用 Android 标准签名验证库验证输入，并输出唯一当前 signer 的证书 SHA-256 和可验证轮换历史。v0.1 要求输出由相同当前 signer 在产品外签名；多当前 signer、无签名或无效签名均拒绝。

### 3.4 Binary AXML Transformer

只修改已批准字段：

- 将 `android:appComponentFactory` 指向 Shell Factory；
- 在项目保留命名空间记录原 Factory、原 Application、容器版本和 Runtime 配置。

转换器保留未知 chunk、string pool 语义、资源 ID、命名空间和未批准属性。转换后重新解析并与变更白名单做语义差异比较。

### 3.5 DEX Container Builder

按原 DEX 序号稳定排序，为每个 DEX 派生独立子密钥并生成唯一 nonce，使用 AES-256-GCM 加密，将 header、记录表和密文写入 `assets/ah/runtime/payload.ahdc`。容器格式见 [ADR-0004](adr/0004-versioned-encrypted-dex-container.md)。

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

1. 读取受限 Manifest 元数据。
2. 验证已安装 signer 与容器结构。
3. 调用 Native Loader 建立业务 DEX ClassLoader。
4. 返回能够解析原应用类的 ClassLoader。
5. 在保护 ClassLoader 可用后实例化原 `AppComponentFactory`，并对 Application、Activity、Service、Receiver 和 Provider 创建进行委托。

没有原 Factory 时使用平台默认实例化语义。不得通过隐藏 API 修改系统 ClassLoader 内部字段。

### 4.2 Native Loader

Native Loader 解析有界容器，恢复每包内容密钥，验证 header 与 signer 绑定，逐 DEX 验证 AES-GCM tag，将明文保留在最短生命周期的直接内存中，并构建 `InMemoryDexClassLoader` 链。不得将明文 DEX 写入 code cache、临时目录或外部存储。

多 DEX 的类查找顺序必须与输入的 `classes.dex`、`classes2.dex` 顺序一致。

### 4.3 Signer and Integrity Guard

通过 Android 公共 PackageManager 签名 API读取当前安装 signer，计算证书 SHA-256，与 Host 嵌入的允许身份进行常量时间比较。随后验证：

- 容器 magic、版本、长度和记录边界；
- header 认证信息；
- 每条 DEX 的 GCM tag、序号和声明大小；
- bootstrap/Native 配置的一致性。

任何强完整性失败在业务类加载前终止。错误对调试构建可分类，对发行构建只暴露稳定、非敏感原因。

### 4.4 Environment Risk Engine

风险引擎将多个信号规范化为版本化决策，不允许单个低置信启发式信号直接阻止启动。策略输出：

- `allow`：继续正常启动；
- `degrade`：减少诊断细节、提高校验频率或关闭可选能力；
- `deny`：仅用于 signer、认证完整性等高置信失败，或经明确批准的高置信组合。

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
tools/validation
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

容器以 ASCII magic `AHDC` 开始，使用 little-endian 固定宽度整数，当前格式版本为 `1`。记录按原 DEX 序号递增。未知 major version 必须拒绝；已知 major version 的未知 flags 也必须拒绝。

### 6.2 Manifest Metadata Contract

项目元数据键使用 `ah.runtime.*` 前缀，至少表达：

- 原始 Application 类名；
- 原始 `AppComponentFactory` 类名或不存在状态；
- 容器路径与 major version；
- signer policy version；
- risk policy version。

类名在 Host 侧解析为完全限定名称，Runtime 不再根据 package name 猜测。

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
-> verify installed signer
-> parse and authenticate container
-> recover protected content key
-> decrypt DEX into bounded memory
-> build InMemoryDexClassLoader chain
-> instantiate original AppComponentFactory when declared
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

Runtime 任一强校验失败时，不尝试回退到磁盘解密、不加载原业务 DEX、不忽略认证错误。启发式环境风险仅按版本化策略处理。

## 9. 架构约束验证

以下检查进入 CI：

- Host 代码不得包含签名凭据参数或调用签名工具的生产路径。
- Runtime 不得引用 Android hidden API。
- 输出不得含原业务 DEX 条目或 DEX 明文临时文件。
- 四 ABI 库名称与导出接口一致。
- 容器 parser 对所有长度、偏移和计数执行溢出安全边界检查。
- Manifest 差异必须属于明确白名单。
