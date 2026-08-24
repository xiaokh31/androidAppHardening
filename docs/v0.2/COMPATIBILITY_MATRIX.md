# v0.2 兼容性矩阵

## 1. 声明规则

`Supported` 表示产品设计/构建范围；`VERIFIED` 表示 exact v0.2 candidate 在真实环境通过 fresh 验收；`UNVERIFIED` 表示没有当前 release evidence，不能形成正向兼容承诺。历史 v0.1 VERIFIED 不会自动升级为 v0.2 VERIFIED。

## 2. 输入与打包格式

| 输入 | 状态 | 说明 |
| --- | --- | --- |
| 单个 standalone APK | Supported | 输入必须已签名且获授权 |
| 未签名 standalone APK | Rejected | 缺少 signer identity |
| AAB/APKS/Split/dynamic feature | Unsupported | v0.2 不扩展格式 |
| Flutter/Unity/React Native | Unsupported | 需要独立架构与测试 |
| 热修复/插件/已有保护壳 | Unsupported | ClassLoader/启动链冲突风险 |

## 3. Android API

| API | 设计范围 | v0.2 release claim |
| --- | --- | --- |
| 29 | Supported baseline | 仅 fresh `VERIFIED` ABI 格子 |
| 30–35 | Conditional | 默认 `UNVERIFIED`，不从端点外推 |
| 36 | Supported endpoint | 仅 fresh `VERIFIED` ABI 格子 |
| < 29 | Rejected | 不静默提高输入 minSdk |

## 4. Runtime ABI

| ABI | Build capability | Mandatory fresh device cell |
| --- | --- | --- |
| `armeabi-v7a` | Supported | API 29 physical process |
| `arm64-v8a` | Supported | API 29 physical process |
| `x86` | Supported build | 无授权设备则保持 `UNVERIFIED` |
| `x86_64` | Supported | API 29/36 KVM process |

四 ABI 只描述 Runtime build。若输入 APK 含 Native 库，实际设备 ABI 仍必须与客户库匹配。ARM-only 输入不能在 x86-only 设备运行。x86/x86_64 本身不是风险信号。

## 5. 语言、DEX 与组件

| 能力 | 状态 | Fresh coverage |
| --- | --- | --- |
| Java | Supported | `java-single-dex` |
| Kotlin | Supported | Kotlin single/multidex fixture |
| 单 DEX | Supported | full-flow + device |
| 多 DEX | Supported | full-flow + device |
| 自定义 `Application` | Supported | component event sequence |
| 自定义受支持 `AppComponentFactory` | Supported | relaunch/lifecycle fixture |
| Provider、Service、Receiver、多进程 | Conditional | exact fixture event contract |
| JNI 四 ABI | Conditional | process ABI 与输入库匹配 |

## 6. Host

| Platform | Planned v0.2 package |
| --- | --- |
| Windows x86_64 | Supported after V2-M4-02 PASS |
| Ubuntu x86_64 | Supported after V2-M4-02 PASS |
| macOS/其他 Host | Unsupported in v0.2 |

发布包依赖预安装的 Eclipse Temurin `17.0.19+10`，并在离线 smoke 环境中验证；其他 vendor/version/build稳定拒绝，不会静默联网下载 JDK。

## 7. 安全能力声明

| 能力 | 可发布表述 |
| --- | --- |
| DEX 认证加密 | 增加静态提取和篡改成本 |
| signer/完整性 | 在受支持启动链中 fail closed 检测不一致 |
| 内存保护 | 缩短明文生命周期并增加 dump 成本 |
| 环境检测 | 提供有界风险信号和策略，不保证检测所有环境 |
| x86 支持 | Runtime build/验证能力，不转换客户 Native ABI |
| 大小优化 | 控制增量，不保证输出小于输入 |

## 8. V2-M3-04 最终矩阵

最终机器可读矩阵必须包含 API 29–36 与四 ABI 的 32 个唯一格子。只有下列四格全部 fresh `VERIFIED`、其余格子均有稳定 `UNVERIFIED` 原因且不存在 `FAILED` 时，V2-M3-04 才可 PASS：

- API 29 `armeabi-v7a`
- API 29 `arm64-v8a`
- API 29 `x86_64`
- API 36 `x86_64`
