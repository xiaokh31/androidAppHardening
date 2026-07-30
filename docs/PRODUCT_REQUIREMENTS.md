# 产品需求

## 1. 产品定义

`androidAppHardening` 是离线命令行 APK 后处理器，不是签名服务、打包服务或应用商店。它读取一个已签名的独立 APK，验证输入是否属于 v0.1 支持范围，将业务 DEX 转换为认证加密容器，注入启动 Runtime，并写出一个新的未签名 APK 和一份机器可读报告。

## 2. 用户与使用场景

主要用户是拥有 APK 与原签名身份控制权的 Android 发行团队。典型流程为：

```text
signed input APK
-> offline hardening
-> unsigned hardened APK
-> owner-controlled signing outside this product
-> standard signature verification
-> installation and validation
```

用户必须在产品外部使用与输入 APK 相同的当前签名证书对输出签名。产品只读取输入 APK 中的公开证书信息，不接触私钥或任何签名凭据。

## 3. 输入合同

### PR-IN-001 独立 APK

输入必须是单个 ZIP/APK 文件，包含完整 Manifest、resources、DEX 和自身所需文件。AAB、Split APK 集合和设备专用拆分包必须被拒绝。

### PR-IN-002 只读

处理开始前计算输入 SHA-256；处理结束及任何失败退出前再次计算。两次结果必须一致。输入与输出解析为同一路径时必须在写入前拒绝。

### PR-IN-003 Android 版本

解析后的 `minSdkVersion` 必须大于或等于 29。缺失、无法确定或低于 29 时失败关闭。

### PR-IN-004 签名

输入必须具有可由标准 Android 签名验证库验证的有效签名，并且只有一个当前 signer；允许携带可验证的证书轮换历史。处理器记录当前 signer 证书的 SHA-256，但不读取私钥。签名无效、无签名或存在多个当前 signer 时拒绝。

### PR-IN-005 应用类型

v0.1 接受标准 Java/Kotlin 应用的单 DEX 或多 DEX 结构，并允许自定义 `Application` 与 `AppComponentFactory`。检测到 Flutter、Unity、React Native、已知热修复框架、插件化 Runtime 或已有加固壳时拒绝，报告稳定原因码。

### PR-IN-006 原生库

允许 APK 包含自身原生库。处理器必须记录原应用提供的 ABI，并判断其与目标安装环境的限制；不得伪造缺失 ABI，也不得宣称 ARM-only 应用获得 x86 能力。

## 4. 输出合同

### PR-OUT-001 新文件

输出必须写到与输入不同的路径，通过同目录临时文件完成，全部校验成功后再进行原子替换。失败时不得留下被报告为成功的部分产物。

### PR-OUT-002 未签名 APK

输出必须移除输入中因修改而失效的 JAR 签名文件与 APK Signing Block，不调用任何签名命令，不生成自签名证书。报告必须明确 `signing.required=true` 和 `signing.performed=false`。

### PR-OUT-003 内容结构

输出包含：

- 最小 bootstrap DEX；
- 版本化认证加密 DEX 容器；
- 按 [ADR-0005](adr/0005-runtime-abi-policy.md) 选择的 Runtime ABI 库；无原生业务库时包含四 ABI，存在原生业务库时只包含其受支持 ABI 交集；
- 经最小化修改的二进制 Android Manifest；
- 保持原字节的非签名资源、assets 与原生库；
- Runtime 所需的命名空间化元数据。

输出不得包含原始业务 DEX 条目或明文业务 DEX 副本。

### PR-OUT-004 可签名性

输出必须通过 ZIP 结构、Android 资源和 `zipalign` 验证，并可由标准 `apksigner` 在产品外部签名。以与输入相同的当前 signer 签名后，Runtime 签名身份校验应通过。

### PR-OUT-005 JSON 报告

每次运行必须输出稳定 schema 的 JSON 报告，至少包括：

- 工具版本、报告 schema 版本和结果状态；
- 输入与输出路径的规范化非敏感表示；
- 输入、输出和容器 SHA-256；
- package name、`minSdk`、`targetSdk`、DEX 数、ABI；
- 输入 signer 证书 SHA-256 和签名验证结果；
- 检测到的兼容性特征与拒绝原因；
- 每个流水线阶段的状态、耗时和错误码；
- `signing.required`、`signing.performed`；
- 输入大小、输出大小和加固增量。

报告不得包含 DEX 明文、密钥材料、私钥路径、凭据、完整环境变量或用户目录绝对路径。

## 5. 功能需求

### FR-001 不可信输入检查

在提取或转换前校验 ZIP 边界、重复条目、路径穿越、压缩比、条目数量、总解压大小、Manifest/DEX 存在性和保留命名空间冲突。所有限制必须是显式常量并出现在报告中。

### FR-002 签名身份捕获

使用 Android 官方签名验证实现验证输入，提取唯一当前 signer 的证书 SHA-256 及可验证轮换历史。Runtime 的允许身份固定为输入的当前 signer；v0.1 不支持换钥输出。

### FR-003 Manifest 转换

直接处理二进制 AXML，保留未知属性、命名空间、资源 ID 和元素顺序。将 `android:appComponentFactory` 替换为项目 Shell Factory，并将原 Factory、原 Application 和必要启动信息写入项目保留元数据。除已批准字段外不得语义重写 Manifest。

### FR-004 DEX 认证加密

按 [ADR-0004](adr/0004-versioned-encrypted-dex-container.md) 将每个业务 DEX 独立加密。每次运行生成新的每包密钥和 nonce，使用认证加密验证密文与元数据。任何认证失败必须在业务类加载前终止。

### FR-005 公开 ClassLoader 接入

按 [ADR-0003](adr/0003-api29-public-classloader-hook.md) 使用 API 29 公开 `AppComponentFactory.instantiateClassLoader`，不得依赖隐藏 API、反射修改系统 ClassLoader 内部字段或磁盘明文 DEX。

### FR-006 原组件兼容

保护 ClassLoader 生效后恢复原 `Application`、原 `AppComponentFactory` 的组件实例化语义，以及 Provider 和 JNI 的正常初始化顺序。原 Factory 不存在时使用平台默认语义。

### FR-007 Runtime 完整性

在业务类加载前验证容器 header、版本、长度、认证标签、业务 DEX 清单和已安装应用 signer。失败必须返回稳定内部原因并停止业务代码执行；面向生产日志不得泄露密钥或可用于还原 DEX 的内容。

### FR-008 环境风险

收集调试、Hook、模拟和运行时篡改等风险信号，通过版本化策略合并为 `allow`、`degrade` 或 `deny`。默认策略只对高置信完整性或 signer 失败执行 `deny`；启发式环境信号不得单独导致不可解释的兼容性失败。

### FR-009 内存暴露控制

业务 DEX 只在需要时解密到进程内存，使用后尽快清零临时缓冲，避免写入文件系统和 crash dump 日志。该控制只提高导出成本，不声明能阻止具有进程控制权的攻击者。

### FR-010 确定性诊断

相同输入和相同失败条件在 Windows 与 Ubuntu 上必须产生相同的顶层结果、兼容性分类和错误码。因安全需要使用随机加密材料时，不要求输出 APK 字节相同，但结构清单、非随机字段和报告语义必须等价。

## 6. 非功能需求

### NFR-001 安全

- 所有解析器将 APK 视为不可信输入。
- 认证或完整性失败时失败关闭。
- 日志、报告和异常不得包含密钥、DEX 明文或签名凭据。
- 临时目录权限最小化，并在成功和失败路径清理。
- 安全敏感任务必须由独立复核者审查。

### NFR-002 兼容性

支持范围以 [兼容性矩阵](COMPATIBILITY_MATRIX.md) 为准。对不支持输入必须在修改前拒绝，不得生成“可能可用”的输出。

### NFR-003 可移植性

Host CLI 在受支持的 Windows x64 与 Ubuntu x64 环境具有同等功能和错误语义。Runtime 构建并打包 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。

### NFR-004 可复现性

源码、锁定工具链、依赖校验和与构建说明必须足以从干净环境重建发布物。随机加密使业务输出不要求位级可复现；发布二进制本身应遵循可复现构建目标。

### NFR-005 性能与大小

项目必须测量 P50/P95 冷启动增量、峰值内存增量和 APK 大小增量，并在发布证据中提供绝对值与百分比。预算由 M3-05 基于未加固 fixture 基线固化；超出预算必须阻止发布或由 `/root` 记录明确批准。大小目标是控制增量，不保证输出小于输入。

### NFR-006 可观测性

每个阶段有稳定 ID、耗时、状态和错误码。默认日志可供排障但不包含应用内容；详细日志也不得降低秘密与 DEX 内容边界。

## 7. CLI 外部接口

v0.1 提供唯一主命令：

```text
android-app-hardening protect --input input.apk --output output-unsigned.apk --report report.json
```

约束：

- `--input`、`--output`、`--report` 必填。
- 不提供 `--keystore`、`--key`、`--alias`、`--password` 或等价选项。
- 成功退出码为 `0`；输入或兼容性拒绝、处理失败、输出验证失败使用互不重叠的稳定非零退出码类别。
- 人类可读信息写入 standard error，JSON 报告是自动化接口；standard output 不输出二进制内容。

## 8. 验收原则

需求完成必须同时具备自动化测试和证据。证据至少包含命令、退出码、测试环境、输入与输出 SHA-256、关键报告片段和关联任务 ID。没有证据的“可运行”“已兼容”或“已加固”不视为完成。
