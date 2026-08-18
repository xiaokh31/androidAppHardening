# 路线图

## 路线原则

- 先验证 Android 启动链路，再建设完整 Host 与 Runtime。
- 先冻结跨组件合同，再允许 Host 和 Runtime 并行。
- 每个里程碑以可执行门禁结束，不以功能数量结束。
- 安全、兼容性和供应链证据与代码同批交付。
- 不通过临时支持扩大 v0.1 范围。

## M0 Foundation and PoC

### 目标

建立仓库、治理、工具链和高风险 Android 启动可行性证据。

### 任务

| ID | 交付 | 依赖（与任务卡一致） |
| --- | --- | --- |
| M0-01 | 仓库初始化与许可证 | None |
| M0-02 | 治理文档、Skills、HandOff | M0-01 |
| M0-03 | Gradle、Android、Native、CI 工具链 | M0-02 |
| M0-04 | API 29 ClassLoader PoC | M0-03 |
| M0-06 | sourceDir 启动配置与认证合同 | M0-04 |
| M0-05 | Application/Factory/Provider/JNI PoC | M0-04, M0-06 |

### 退出门禁

- Windows 与 Ubuntu 从干净环境构建成功。
- Gradle、SDK、NDK、CMake 和 Actions 均固定版本与来源。
- API 29 公开 `instantiateClassLoader` 路径可以加载内存 DEX。
- 默认/自定义 Application、Factory、Provider、JNI 的时序有设备证据。
- 不使用 hidden API 或磁盘明文 DEX。
- 容器、ConfigV2、单一 Manifest 属性变换、Runtime ABI 和错误合同冻结。

### 决策点

若 M0-04、M0-06 或 M0-05 无法在公开 API 下满足支持范围，停止 M1/M2，实现团队向 `/root` 提交证据，由架构决策决定缩小兼容性或终止 v0.1 路线；不得私自切换 hidden API、Context 或 PackageManager 回退。

## M1 Host Processor

### 目标

交付安全、离线、跨平台、输入只读的 APK 后处理流水线。

### 任务

| ID | 交付 | 依赖（与任务卡一致） |
| --- | --- | --- |
| M1-01 | 不可信 APK 检查器 | M0-05 |
| M1-02 | signer 验证与身份策略 | M1-01 |
| M1-03 | 二进制 AXML 转换器 | M1-01, M0-05 |
| M1-07 | 分块认证 DEX 容器合同修订 | M1-02 |
| M1-04 | 版本化认证加密容器 | M1-01, M1-02, M1-07 |
| M1-05 | APK 重打包、签名材料移除与对齐 | M1-02, M1-03, M1-04 |
| M1-06 | CLI、JSON 报告与稳定错误码 | M1-01, M1-02, M1-03, M1-04, M1-05 |

### 退出门禁

- 每个支持 fixture 生成新的未签名 APK。
- 输入在成功、失败和中断路径的 SHA-256 不变。
- 非支持输入在任何输出发布前拒绝。
- Manifest diff 只含白名单字段。
- 原业务 DEX 和失效签名材料不在输出。
- 输出能被标准外部工具签名并验证。
- Windows 与 Ubuntu 报告与错误语义等价。

## M2 Android Runtime

### 目标

交付在 API 29+ 上从认证内存容器启动原应用的四 ABI Runtime。

### 任务

| ID | 交付 | 依赖（与任务卡一致） |
| --- | --- | --- |
| M2-07 | Native 密码后端与供应链固定 | M0-03, M1-04 |
| M2-01 | Shell AppComponentFactory | M0-05, M1-03, M1-04, M2-03 |
| M2-02 | Native 解密与 InMemoryDexClassLoader | M0-04, M1-04, M2-07 |
| M2-08 | Native parser topology bounds hardening | M2-02 |
| M2-09 | Shell Factory configuration-relaunch lifecycle | M2-01 |
| M2-03 | 运行时 signer 和完整性校验 | M1-02, M1-04, M2-02 |
| M2-04 | 四 ABI 构建与一致接口 | M0-03, M1-01, M2-01, M2-02, M2-03 |
| M2-05 | 环境风险引擎 | M2-01, M2-03, M2-04 |
| M2-06 | 内存导出成本控制 | M2-02, M2-04, M2-05 |

### 退出门禁

- API 29 ARM32/ARM64 与 API 29/36 x86_64 的既定可获得基线通过强制 ABI 用例；其他设备组合由 M3-06/M3-04 的证据状态限定，不从构建能力外推。
- 多 DEX 顺序、原 Application、原 Factory、Provider 与 JNI 语义保持。
- signer 或任一认证字段被修改时，业务探针不执行。
- 不产生磁盘明文 DEX。
- 四 ABI Runtime 均构建、加载并具有一致导出接口。
- ARM-only 应用在 x86-only 环境被明确分类为原应用 ABI 不兼容。

## M3 Validation

### 目标

以可公开、可复现的 fixture 和矩阵证明产品声明。

### 任务

| ID | 交付 | 依赖（与任务卡一致） |
| --- | --- | --- |
| M3-01 | Android fixture 集 | M1-06, M2-04 |
| M3-02 | 篡改、parser 模糊和残留扫描 | M1-03, M1-04, M1-06, M2-02, M2-03, M2-06, M2-08, M3-01 |
| M3-03 | Windows/Ubuntu 等价性 | M0-03, M1-05, M1-06, M2-06, M3-01 |
| M3-06 | API/ABI 验证声明合同 | M0-03, M2-04, M3-01, M3-02 |
| M3-04 | API/ABI/组件启动矩阵 | M0-03, M2-04, M2-09, M3-01, M3-02, M3-06 |
| M3-07 | 测试专用 HIGH benchmark 合同 | M2-05, M2-06, M3-01 |
| M3-08 | 启动性能与测量稳定性合同 | M3-01, M3-07 |
| M3-09 | 端到端启动性能归因边界合同 | M3-08 |
| M3-05 | 大小、冷启动、内存基准与预算 | M1-06, M2-04, M2-06, M3-01, M3-07, M3-08, M3-09 |

### 退出门禁

- 完整 API/ABI 清单中的强制可获得基线全部 `VERIFIED`，无 `FAILED`；不可获得组合逐格标为 `UNVERIFIED` 且不形成兼容承诺。
- 模糊测试无未处理 crash、hang 或 sanitizer 告警。
- 全部安全负面测试在业务代码前失败。
- 跨平台差异均被解释并纳入允许随机字段。
- 性能与大小预算固化并由 CI/发布校验使用。

## M4 Release

### 目标

发布可核验、无签名秘密、跨平台的 v0.1。

### 任务

| ID | 交付 | 依赖（与任务卡一致） |
| --- | --- | --- |
| M4-01 | 安全、许可证与供应链复核 | M3-02, M3-03, M3-04, M3-05 |
| M4-02 | Windows/Ubuntu 发布包 | M1-06, M2-04, M3-03, M4-01 |
| M4-03 | 发布证据、兼容性与用户文档 | M3-03, M3-04, M3-05, M4-01, M4-02 |

### 退出门禁

- 独立安全复核关闭所有 release-blocking 发现。
- SBOM、第三方声明、来源和依赖校验完整。
- 两个平台发布包通过干净环境 smoke test。
- 每个产物有 SHA-256，且与 release manifest 一致。
- 用户文档明确未签名输出、同 signer 外部签名要求和不支持范围。
- 发布包不含私钥、测试签名秘密、客户 APK 或明文业务 DEX。

## 跨里程碑变更控制

以下变化必须先新增或修订 ADR：

- 接受非 APK 输入或修改输入文件；
- 引入产品内签名；
- 降低 `minSdk`；
- 改用 hidden API 或磁盘 DEX；
- 改变容器 major version 或密码学；
- 改变四 ABI 策略；
- 引入在线密钥服务；
- 将框架或已加固应用加入支持范围。

路线图状态以任务卡和 GitHub Issue 为执行记录，以根 `HandOff.md` 为当前恢复入口。Issue 不复制任务卡技术要求。
