# 项目计划

## 1. 项目目标

项目交付一个可在 Windows 和 Ubuntu 上离线运行的 APK 后处理器，以及随输出 APK 运行的 Android 启动保护层。处理器读取已签名的独立 APK，将业务 DEX 封装为带版本的认证加密容器，注入最小启动 Runtime，更新二进制 Android Manifest，最终生成一个新的未签名 APK，交由使用方在仓库外使用原签名身份签名。

v0.1 的成功标准不是“不可破解”，而是在不破坏已声明兼容性的前提下，提高静态提取、直接内存导出、调试和篡改的成本，并提供可审计、可复现、失败即停止的处理流程。

## 2. 范围

### 2.1 交付范围

- Host：APK 检查、签名身份读取、二进制 AXML 转换、DEX 容器生成、APK 重打包、对齐、CLI 与 JSON 报告。
- Runtime：公开 API 29 ClassLoader 接入、原始 `Application`/`AppComponentFactory` 兼容、JNI 加载、签名与完整性校验、四 ABI 构建、风险信号与内存导出成本控制。
- 验证：可公开提交的合成 fixture、篡改测试、模糊测试、Windows/Ubuntu 等价性、API/ABI 矩阵、大小与性能基准。
- 发布：依赖和许可证审计、跨平台压缩包、SBOM、校验和、发布证据与使用文档。

### 2.2 排除范围

- AAB、Split APK 和动态特性模块。
- Flutter、Unity、React Native 等自带特殊 Runtime 的框架。
- 热修复框架、插件化框架和已存在加固壳。
- 低于 API 29 的设备或 `minSdk < 29` 的输入。
- 代替使用方签名、托管签名凭据或调用远程签名服务。
- 将 ARM-only 原生应用转换为 x86 可执行应用。
- 承诺绝对防止 DEX 恢复、调试、Hook 或内存截取。

## 3. 工作分解

### M0 Foundation and PoC

目标是固定仓库治理、构建工具链和两项高风险兼容性结论。

- M0-01：仓库初始化。
- M0-02：治理、Skills 与交接规范。
- M0-03：Gradle、Android、Native 和 CI 工具链。
- M0-04：验证 API 29 公开 `AppComponentFactory.instantiateClassLoader` 路径。
- M0-06：把启动配置迁移到 `sourceDir` 中受认证的固定 ConfigV2，并修订 Host/Runtime 合同。
- M0-05：验证自定义 `Application`、自定义 Factory、Provider 和 JNI 的启动顺序。

退出门禁：Windows 与 Ubuntu 构建可复现；API 29 至目标 API 的 PoC 通过；失败场景和不支持组合被固化为机器可读诊断；容器与 Runtime 接口可以冻结。

### M1 Host Processor

目标是完成仅对副本写入的 APK 后处理流水线。

- M1-01：不可信 APK 检查器。
- M1-02：签名身份策略。
- M1-03：二进制 AXML 转换器。
- M1-04：版本化加密 DEX 容器。
- M1-07：在 M1-04 实现前冻结 AHDC v2 64 KiB 分块认证合同，取代不可同时满足认证顺序与内存上限的未发布 v1。
- M1-05：重打包与对齐。
- M1-06：CLI 与 JSON 报告。

退出门禁：对支持矩阵内 fixture 能产生结构有效、未签名、可由外部工具签名的输出；所有拒绝路径具有稳定错误码；同一锁定工具链下的结构和报告可复现。

### M2 Android Runtime

目标是完成注入 APK 的最小启动与防护 Runtime。

- M2-01：Shell `AppComponentFactory`。
- M2-09：Shell Factory 配置 relaunch 生命周期维护。
- M2-02：Native 解密与内存 ClassLoader。
- M2-03：运行时签名和完整性校验。
- M2-04：四 ABI Runtime。
- M2-05：环境风险引擎。
- M2-06：内存导出成本控制。

退出门禁：支持的 API/ABI 组合可冷启动并运行 fixture；原始组件创建语义保持；篡改和签名身份不匹配在业务代码运行前失败；所有防护均有确定性降级或拒绝策略。

### M3 Validation

目标是用自动化证据验证兼容性、安全负面行为和资源增量。

- M3-01：Android fixtures。
- M3-02：篡改与模糊测试。
- M3-03：Windows/Ubuntu 等价性。
- M3-06：API/ABI 验证声明合同。
- M3-04：API/ABI 矩阵。
- M3-05：大小、启动和内存基准。

退出门禁：完整矩阵逐格区分 `VERIFIED`、`FAILED` 与 `UNVERIFIED`，强制可获得基线和负面测试通过，基准报告完整；不存在未解释的平台差异，所有未验证组合不形成兼容承诺，已知限制与产品文档一致。

### M4 Release

目标是形成可核验、可分发、无签名秘密的 v0.1 发布物。

- M4-01：安全与供应链复核。
- M4-02：跨平台发布打包。
- M4-03：发布证据和文档。

退出门禁：发布压缩包、SHA-256、SBOM、第三方声明、测试报告和兼容性声明齐全；从干净环境能够复现构建。

## 4. 依赖与并行策略

关键路径为：

```text
M0-03 -> M0-04 -> M0-06 -> M0-05 -> interface freeze
interface freeze -> M1-07 -> M1-04 -> M2-02 -> M2-09 -> M3-06 -> M3-04 -> M4
```

M0-03 完成后，M0-04 可与治理校验并行。M0-04 通过后先完成 M0-06，再恢复 M0-05。M1-04 首轮复核发现容器认证/内存合同冲突后，必须先合并 M1-07 的 AHDC v2 分块认证合同；只有该合同独立复核通过，M1 Host 与 M2 Runtime 才可按同一 HeaderV2/RecordV2/ChunkV2、ConfigV2、单一 Manifest 属性变换和错误语义继续。M3 fixture 可提前建设，但矩阵结论必须基于已合并的 M1 和 M2。

任务依赖的唯一规范来源是 [任务索引](tasks/INDEX.md) 及各任务卡的 `depends_on` 字段。任何新增依赖必须通过任务卡和路线图变更审查，不得只存在于 Issue 评论中。

## 5. 角色与责任

| 角色 | 责任 |
| --- | --- |
| `/root` | 项目统筹、任务分配、架构决策、合并顺序和根 `HandOff.md` |
| `host-pipeline-agent` | M1 Host 后处理器 |
| `runtime-security-agent` | M0 Runtime PoC 与 M2 Runtime |
| `qa-governance-agent` | M0 治理、M3 验证与 M4 发布 |
| 独立安全复核者 | 复核 `security_sensitive: true` 的任务，不得与实现者为同一工作流 |

## 6. 项目控制

- 除 M0-01 空远程种子提交和 M0-02 预先批准的 `docs/m0-project-package` 引导流程外，一个任务卡对应一个含任务 ID 的分支、Issue 和 Pull Request；后续任务无例外。
- 架构决策变化必须先修订 ADR，再修改实现。
- 安全敏感 PR 必须获得独立复核结论。
- 任务完成以可执行证据为准，不以文字声明为准。
- 根交接文件仅由 `/root` 更新；Worker 只提交结构化交接包。
- 未完成依赖、范围冲突或安全边界冲突均视为阻塞，不得以临时兼容代码绕过。

## 7. 质量指标

v0.1 发布至少满足：

- 已验证矩阵内的所有强制启动用例通过，所有不可获得组合显式标为 `UNVERIFIED` 且不被宣传为支持。
- 输入 APK 的 SHA-256 在处理前后相同。
- 输出 APK 不含有效签名块，且可被标准外部签名工具签名和验证。
- Android API 与 ABI 矩阵中无未分类失败。
- Windows 与 Ubuntu 对同一 fixture 产生等价结构、相同逻辑报告和相同失败语义。
- 加固大小、冷启动时间和峰值内存增量均有基线、预算和可追溯报告。
- 每个发布产物都有 SHA-256、SBOM、来源记录和测试证据。

具体测量方法和门禁见 [测试策略](TEST_STRATEGY.md)。

## 8. 主要风险

| 风险 | 控制 |
| --- | --- |
| ClassLoader 接入时机或早期配置可见性破坏组件创建 | M0-04/M0-06/M0-05 先行，使用 API 29 公开 `sourceDir` 合同 |
| 自定义 Factory 或 Provider 启动顺序不兼容 | 保存原始声明并在保护 ClassLoader 生效后委托；纳入 fixture |
| Native ABI 缺失导致安装后崩溃 | 四 ABI Runtime 与原 APK 原生库 ABI 预检 |
| 本地密钥可被逆向恢复 | 认证加密、每包随机密钥、分片与签名绑定，并明确残余风险 |
| 重打包破坏资源或签名结构 | 二进制级转换、结构校验、签名块移除和外部签名回归 |
| 不可信 APK 触发解析器漏洞 | 边界检查、资源上限、失败关闭、模糊测试和隔离临时目录 |
| 供应链不可追溯 | 锁定版本、校验和、依赖验证、SBOM 与第三方声明 |
