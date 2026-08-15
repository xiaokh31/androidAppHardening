# 兼容性矩阵

## 1. 解释

本矩阵是 v0.1 的产品合同：

- `Supported`：必须通过自动化和设备矩阵。
- `Rejected`：Host 在修改或发布输出前以稳定原因码拒绝。
- `Conditional`：满足明确前提时支持，报告必须给出判定。
- `Not promised`：可能在个别环境运行，但不构成产品承诺，也不得在成功报告中标记为已验证支持。

设备级 API/ABI 声明另使用 ADR 0012 的证据状态：`VERIFIED` 表示精确组合已在真实进程通过强制验收，`FAILED` 表示已执行但失败，`UNVERIFIED` 表示缺少适用环境且不作兼容承诺。Runtime 构建能力、输入 ABI 可接受性与设备验证状态必须分别判断。

## 2. 输入与打包格式

| 能力 | 状态 | 判定 |
| --- | --- | --- |
| 单个独立 APK | Supported | 一个文件包含完整安装内容 |
| AAB | Rejected | 输入类型错误 |
| Split APK / APK set | Rejected | 检测拆分元数据或多个输入 |
| 动态特性模块 | Rejected | v0.1 不处理模块交付 |
| 已签名输入 APK | Supported | 标准库验证成功，唯一当前 signer |
| 未签名或签名无效 APK | Rejected | signer policy 失败 |
| 多个当前 signer | Rejected | v0.1 身份策略不允许歧义 |
| signer 轮换历史 | Conditional | 当前 signer 唯一且 lineage 可验证；输出仍须使用同一当前 signer |
| 输出未签名 APK | Supported | 产品唯一输出签名状态 |
| 产品内签名 | Rejected | CLI 不提供签名凭据接口 |

## 3. Android API

| 条件 | 状态 | 判定 |
| --- | --- | --- |
| 输入 `minSdk >= 29` | Supported | 继续其他检查 |
| 输入 `minSdk < 29` | Rejected | `COMPAT_MIN_SDK` |
| 设备 API 29 至仓库锁定 `compileSdk` | Conditional | 仅精确 API/进程 ABI 格子在 M3-04 为 `VERIFIED` 时形成发布兼容声明；其余为 `UNVERIFIED`/Not promised |
| 设备 API 高于当前发布证据的最高 API | Not promised | 完成新增矩阵前不扩大承诺 |
| 设备 API 28 及以下 | Rejected | Runtime 依赖 API 29 公开 ClassLoader hook |

当前 M3-04 的强制可获得验证基线为 API 29 `armeabi-v7a`/`arm64-v8a` 真机进程，以及 API 29/36 `x86_64` 固定 KVM 进程。API 30-35 与其他不可获得组合仍保留在完整清单中并明确标为 `UNVERIFIED`；端点测试不能外推中间 API。

## 4. 语言与 DEX

| 能力 | 状态 | 判定 |
| --- | --- | --- |
| 标准 Java 应用 | Supported | 合成 fixture 验证 |
| 标准 Kotlin 应用 | Supported | 合成 fixture 验证 |
| 单 DEX | Supported | `classes.dex` |
| 多 DEX | Supported | `classes.dex`、`classes2.dex` 依序处理 |
| 自定义 `Application` | Supported | 保存类名并在保护 ClassLoader 下创建 |
| 自定义 `AppComponentFactory` | Supported | Shell 用 provisional loader 实例化，并委托其 ClassLoader hook 与五类组件入口 |
| 自定义 Provider | Supported | eager/lazy fixture 验证启动顺序 |
| `ApplicationInfo.metaData` 为空 | Supported | 启动配置来自 sourceDir 中已认证 ConfigV2，不读取该 Bundle |
| 动态下载并执行代码 | Rejected | 不在离线完整性合同内 |
| 非标准 DEX 命名或重复 DEX 序号 | Rejected | DEX 清单不规范 |

## 5. Runtime ABI

| Runtime ABI | 状态 | 说明 |
| --- | --- | --- |
| `armeabi-v7a` | Supported | 注入对应 `libah_runtime.so` |
| `arm64-v8a` | Supported | 注入对应 `libah_runtime.so` |
| `x86` | Supported | 注入对应 `libah_runtime.so` |
| `x86_64` | Supported | 注入对应 `libah_runtime.so` |
| 其他 ABI | Rejected | v0.1 Runtime 不提供 |

四 ABI 的 `Supported` 只描述项目 Runtime 的构建、注入和固定接口能力，不自动表示每个 API/ABI 设备组合已验证。实际运行声明还必须命中 M3-04 的精确 `VERIFIED` 格子。原 APK 包含 Native 库时，还必须满足原应用 ABI：

| 原应用 Native 情况 | 目标设备 | 状态 |
| --- | --- | --- |
| 无 Native 库 | 任一 Runtime 构建 ABI | Conditional：精确 API/ABI 格子必须为 `VERIFIED` |
| 同时提供四 ABI | 对应四 ABI 设备 | Conditional：精确 API/ABI 格子必须为 `VERIFIED` |
| 仅 ARM ABI | ARM 设备 | Conditional：设备 ABI 与原库匹配，且精确 API/ABI 格子为 `VERIFIED` |
| 仅 ARM ABI | x86-only 设备 | Rejected：不得用 Runtime x86 库伪装兼容 |
| 仅 64-bit 原库 | 32-bit-only 设备 | Rejected |
| ABI 目录含未知或冲突库 | 任意 | Rejected 或在任务定义的安全白名单内明确分类 |

## 6. 框架与加载机制

| 类型 | 状态 | 原因 |
| --- | --- | --- |
| Android 标准 Java/Kotlin | Supported | v0.1 目标 |
| Flutter | Rejected | 特殊 Runtime 与 assets/Native 启动模型 |
| Unity | Rejected | 特殊 Native/asset 加载模型 |
| React Native | Rejected | JavaScript bundle 与 Runtime 不在保护合同 |
| 热修复框架 | Rejected | 修改 ClassLoader/组件启动语义 |
| 插件化框架 | Rejected | 动态代码与组件发现超出合同 |
| 已有加固壳 | Rejected | 多壳启动与密钥语义不可验证 |
| 自定义 ClassLoader 框架 | Rejected | 与唯一公开接入路径冲突 |

框架检测使用多个结构特征并在 JSON 报告中列出命中项。不能可靠分类时失败关闭，不生成推测可用的输出。

## 7. Android 组件

| 组件/行为 | 状态 | 验证 |
| --- | --- | --- |
| 默认 Application | Supported | basic fixture |
| 自定义 Application | Supported | `onCreate` 时序 |
| 默认 AppComponentFactory | Supported | 平台默认语义 |
| 自定义 AppComponentFactory | Supported | 所有组件实例化委托 |
| Activity | Supported | 冷启动与直接启动 |
| Service | Supported | 显式启动与进程重启 |
| BroadcastReceiver | Supported | manifest receiver |
| ContentProvider | Supported | eager/lazy 初始化次序 |
| 多进程组件 | Conditional | 每个声明进程都能初始化 Runtime；M3 矩阵通过后才标记 |
| isolated process | Not promised | 权限与文件可见性需要独立产品决策 |

## 8. Host 平台

| 平台 | 状态 | 要求 |
| --- | --- | --- |
| Windows x64 | Supported | 锁定 JDK 17 与项目发布包 |
| Ubuntu x64 | Supported | 锁定 JDK 17 与项目发布包 |
| macOS | Not promised | v0.1 不发布、不做等价性门禁 |
| Windows ARM64 | Not promised | 未提供 Host 原生发布验证 |
| Ubuntu ARM64 | Not promised | 未提供 Host 原生发布验证 |

Windows 与 Ubuntu 的随机密文可以不同，但输入分类、稳定错误码、Manifest 语义、容器非随机结构和报告 schema 必须等价。

## 9. 安全能力

| 能力 | 状态 | 准确表述 |
| --- | --- | --- |
| 分发包业务 DEX 静态加密 | Supported | 使用认证加密容器 |
| 容器离线篡改检测 | Supported | 在业务类加载前验证 |
| signer 身份绑定 | Supported | 要求输出以输入同一当前 signer 签名 |
| 明文 DEX 不落盘 | Supported | 使用内存 ClassLoader |
| 反调试 | Conditional | 风险信号与策略只提高成本 |
| 环境检测 | Conditional | 低置信信号不得单独拒绝 |
| 防内存截取 | Not promised | 缩短和清理暴露，不保证阻止 |
| 不可逆密钥保护 | Not promised | 离线 Runtime 必须可恢复内容密钥 |
| 绝对防破解 | Not promised | 不属于产品声明 |

## 10. 大小与性能

| 主张 | 状态 |
| --- | --- |
| 记录输入、输出与分项大小 | Supported |
| 控制加固大小增量 | Supported，预算由 M3-05 固化 |
| 输出小于输入 | Not promised |
| 记录 P50/P95 冷启动增量 | Supported |
| 记录峰值内存增量 | Supported |
| 所有设备零性能影响 | Not promised |

## 11. 判定顺序

Host 必须按以下顺序分类，避免先写出后发现不兼容：

1. 文件类型和 ZIP 安全。
2. 签名有效性与 signer 数。
3. Manifest、`minSdk` 和拆分特征。
4. 框架、热修复、插件化和已有壳特征。
5. DEX 结构与保留命名空间。
6. 原生库 ABI。
7. AXML 可变换性。
8. 通过后才允许创建最终输出临时文件。

任何 `Rejected` 结果都必须映射稳定错误码并进入 JSON 报告。
