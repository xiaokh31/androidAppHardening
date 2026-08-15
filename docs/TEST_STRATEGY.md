# 测试策略

## 1. 目标

测试体系证明以下结论，而不是只证明代码能够编译：

- 输入 APK 在成功和失败路径都未被修改。
- 支持范围内的应用经处理、外部签名后可启动并保持关键组件语义。
- 不支持输入在写出结果前以稳定原因拒绝。
- signer 或容器被篡改时，业务代码不会执行。
- Host 在 Windows 与 Ubuntu 具有等价结果和诊断。
- Runtime 覆盖四 ABI，但不虚构原应用缺失的 ABI。
- 大小、冷启动和内存增量已被测量并受发布预算控制。

## 2. 测试层级

### 2.1 静态治理测试

每个 PR 执行：

- Markdown 内部链接检查；
- 任务 ID 唯一性、依赖存在性和无环检查；
- 任务卡 frontmatter 与固定章节检查；
- Skill 目录和 `agents/openai.yaml` 结构检查；
- `HandOff.md` schema、章节顺序和 Git 祖先关系检查；
- 禁止占位文本、替换字符、用户绝对路径和敏感材料扫描；
- 依赖锁定、许可证和来源登记检查。

### 2.2 单元测试

覆盖纯函数与边界模型：

- ZIP 路径、大小、压缩比和重复条目；
- AXML chunk、string pool、resource map 和白名单 diff；
- signer 模型和证书 digest；
- ConfigV2 固定 offset、flag/length、严格 UTF-8、zero-fill、reserved 和版本；
- container 序列化、解析、密钥派生、nonce 和 tag；
- ABI 集合兼容性；
- CLI 参数、错误码和报告 schema；
- Runtime 风险策略与完整性决策。

### 2.3 属性与模糊测试

目标 parser 为 ZIP metadata、二进制 AXML、ConfigV2、AHDC container 和 JSON report reader。必须具备：

- AHDC v2 HeaderV2/RecordV2/ChunkV2 的 checked arithmetic、canonical 64 KiB chunk、完整消费和 v1 拒绝测试；
- 每 chunk 一次性 GCM tag 验证成功后才进入连续 zlib inflater 的顺序断言；
- 1/65535/65536/65537 bytes 与接近 512 MiB DEX 的不超过 1 MiB 工作缓冲测试；
- Native handle 创建前在首个/中间/末尾 chunk 注入认证、I/O、取消、OOM、zlib/摘要和 cleanup failure，证明 completed/partial DEX 映射清零并 unmap、无 handle 返回且主错误不被覆盖；
- 正向 `openVerified` 返回后、`close` 前断言全部 key/AAD/compressed/inflater/crypto 临时状态已清零且不可达，只有 completed DEX mappings 由 handle 保持可加载；close 后才清零/unmap 映射；
- Native handle 返回后在 `nativeAuthenticatedMetadata` bytes 获取/解析/对象构造、`nativeDexBuffers` 数组/元素、search path、ClassLoader、`LoadedPayload` 构造/return 前注入异常/OOM，断言内部交接对象/`ByteBuffer` 未发布、Native close 恰好一次、mappings/部分引用清理和 primary/suppressed error；
- 同 handle `AuthenticatedPayloadMetadata` 来源、跨 handle 替换、不可伪造、防御性复制和无恢复秘密测试；package/current signer 对实测值、lineage 对实测有序列表、build/key 对同次未认证预读快照、versions 对 `2.0/1/1` 的可执行比较及失败均断言 provisional loader lookup/Factory 为零；原 Factory/config 篡改在 Native 认证阶段失败，getter 编码错误在 M2-02 golden parser 覆盖，不伪造 Factory 第二来源；Guard 在 recheck/identity/config/session/return 前注入异常/OOM，断言无 loader/metadata/`VerifiedPayloadSession` 发布、close 恰好一次且部分引用清除；

- 任意输入不崩溃、不越界、不无限循环；
- 解析成功后重新序列化保持规范语义；
- 任一截断点均失败关闭；
- 长度、偏移与计数运算无溢出；
- 已知有效 corpus 在持续模糊运行后仍可解析。

崩溃样本必须最小化并进入回归语料库。

M3-02 将上述要求固定为 `:tools:validation:regressionFuzz`、`:tools:validation:tamperTest`、`:tools:validation:prFuzz` 与 `:tools:validation:nightlyFuzz` 四个公开入口。APK inspector 和 Binary AXML 使用锁定 Jazzer，AHDC Native parser 使用锁定 Clang libFuzzer + ASan/UBSan；PR/nightly 时长分别为每 target 10/60 分钟。每个执行先在忽略工作区连续运行固定 corpus/regression 两次，再执行有界 fuzz，并保存执行次数、时长、corpus SHA-256、资源上限和 crash/sanitizer/timeout/OOM 计数。API 29/36 KVM 复用生产 Runtime 的 tokenized startup 与 Guard failure-injection 验收，但必须由 M3-02 catalog 映射每项发布和清理断言，不能以旧任务成功标记代替本任务结果。

### 2.4 Host 集成测试

以合成 APK fixture 驱动完整流水线，验证：

- 输入前后 SHA-256 相同；
- 输出路径独立且失败不发布；
- 原业务 DEX 不在输出中；
- Manifest 只发生白名单变化；
- 非签名保留条目哈希一致；
- 输出未签名、对齐且能被标准工具签名；
- JSON 报告字段、错误码和哈希准确；
- 同一失败在 Windows 与 Ubuntu 分类一致。

### 2.5 Android 设备测试

输出先在独立测试步骤中使用 fixture 专用公开测试身份签名，再安装到目标设备或模拟器。验证：

- Shell Factory 在业务组件前接管 ClassLoader；
- 单/多 DEX 类均可解析；
- 默认及自定义 `Application`；
- 无 Factory 及自定义 `AppComponentFactory`；
- 自定义 Factory 的 `instantiateClassLoader` 恰好调用一次，返回 loader 成为组件使用的 final loader；
- API 29 配置 relaunch 即使重建 Shell Factory wrapper，也只能以相同 final loader 附着进程已有 `READY` 结果；Guard open、原 Factory 构造与 ClassLoader hook 仍各为一次；
- Factory 构造/hook/null/重入失败在 `READY` 前恰好一次关闭 payload session，清理 Native handle/direct buffer/部分引用，cleanup 异常不覆盖主错误；
- eager/lazy Provider；
- Java/Kotlin JNI 调用；
- 进程重启、冷启动、后台恢复和组件直接启动；
- signer 或 container 失败时业务探针未执行。

测试签名步骤属于 QA fixture 流程，不进入产品 CLI，也不接收任何生产签名资料。

M2-05 额外执行 ADR-0010 的纯评分矩阵和真实当前进程采集：覆盖全部分值边界、映射家族去重/封顶、模拟组合封顶、不可用/畸形/超长输入、x86/x86_64 单项零贡献、报告脱敏，以及 1,000 次单次不超过 50 ms 的预算。环境结果只允许 `ALLOW` 或 `DEGRADE`，不得替代或降低 signer、AEAD 和认证完整性失败。

### 2.6 发布验收

在干净 Windows x64 和 Ubuntu x64 环境构建发布物，验证压缩包内容、SBOM、第三方声明、SHA-256、帮助文本和 smoke test。发布二进制不得包含测试证书私钥、fixture 明文产物或构建机绝对路径。

## 3. Fixture 设计

所有 fixture 由仓库内源码生成，不提交真实第三方或客户 APK。

| Fixture | DEX | Application | Factory | Provider | JNI | Native ABI |
| --- | --- | --- | --- | --- | --- | --- |
| `basic-java` | single | default | default | none | no | none |
| `basic-kotlin` | single | custom | default | none | no | none |
| `multidex-kotlin` | multi | custom | default | lazy | no | none |
| `factory-components` | multi | custom | custom | eager | no | none |
| `jni-all-abi` | single | custom | custom | eager | yes | all four |
| `jni-arm-only` | single | custom | default | none | yes | ARM only |
| `unsupported-framework-markers` | single | framework marker | varied | varied | varied | varied |
| `reserved-namespace-collision` | single | project namespace | default | none | no | none |

测试探针使用可观察但非敏感的事件序列，记录 ClassLoader、Application、Provider、Factory 和 JNI 初始化顺序。

## 4. API 与 ABI 矩阵

矩阵清单必须枚举 `29` 到 M0-03 锁定 `compileSdk` 之间的每一个整数 API 与四个 Runtime ABI。最低边界始终包含 `29`；不得删除中间版本，也不得用只测首尾推导中间版本。

```text
29..locked compileSdk
```

当项目提高 `compileSdk` 或 Android 发布新的稳定 API 时，新增目标通过兼容性变更任务进入清单，不自动扩大已发布版本的承诺。

强制 Runtime ABI：

```text
armeabi-v7a
arm64-v8a
x86
x86_64
```

每个格子只能是 ADR 0012 定义的 `VERIFIED`、`FAILED` 或 `UNVERIFIED`：

- `VERIFIED` 必须有 Android 真实回报的 API/进程 ABI、完整 fixture/负向结果和证据哈希；runner 标签、构建成功和其他格子不能替代；
- `FAILED` 保留执行证据并阻止任务/发布；最多重试一次，首轮失败仍归档；
- `UNVERIFIED` 必须有稳定原因，不能带伪造设备事实，不能在 JSON、Markdown 或发布文档中表示为支持；
- 缺格、重复格、未知状态或 JSON/Markdown 语义不一致均失败关闭。

当前强制执行一次的可获得基线为：

- API 29：`armeabi-v7a`、`arm64-v8a` 物理设备进程；
- API 29 与 API 36：`x86_64` 固定 Linux/KVM 进程；
- 每个强制格运行单/多 DEX、适用的 JNI、异 signer 和认证 tag 篡改；x86_64 额外运行自定义 Factory；
- ARM-only fixture 在已验证 x86/x86_64 格子上得到明确原应用 ABI 不兼容结论，不得标记为 Runtime ABI 成功。

其他 API/ABI 组合保留在清单中并标为 `UNVERIFIED`，直到获得固定来源的真实环境并执行同一合同。若继承既有设备证据，必须证明生产 Runtime、fixture、验收脚本和被测 artifact 未变化，并记录祖先提交、diff 边界与哈希；不能为了形式重复完全相同的长测。

完整产品分类见 [兼容性矩阵](COMPATIBILITY_MATRIX.md)。

## 5. 安全负面矩阵

对每个有效输出分别修改：

- 安装 signer；
- container magic、major version、flags；
- header 长度、记录数、偏移和大小；
- DEX 序号与名称；
- nonce、ciphertext、GCM tag；
- ConfigV2 中原 Factory、policy version、build/key slot、signer binding、wrapped CEK 和 reserved 字段；
- Manifest 的 `android:appComponentFactory` 以外任一语义变化，以及原 `android:name`/既有 metadata 变化；
- bootstrap 与 Native Runtime 版本组合；
- ABI 库缺失或替换。

预期结果是稳定拒绝，且业务探针事件数为零。测试同时检查日志、报告、临时目录和 crash artifact 不含 `dex\n035`/`dex\n039` magic、密钥字节或用户路径。

## 6. Host 跨平台等价性

Windows 与 Ubuntu 使用同一 Git commit、锁定 JDK、Gradle wrapper、Android build tools 和 fixture。比较：

- 顶层退出码；
- 结果状态和稳定错误码；
- 输入分析模型；
- Manifest 语义 diff；
- 容器 record/chunk 清单与非随机字段；
- 输出 ZIP 条目名、压缩方式、权限和排序；
- 报告字段类型与非环境字段；
- 输入不变性。

每次保护使用随机密钥和 record nonce prefix，因此密文、输出 APK SHA-256 和相应大小细节可不同；等价性比较必须排除明确列出的随机字段，不能通过忽略整个容器来通过。固定测试 RNG 下，Windows/Ubuntu 的 HeaderV2、record/chunk table、AAD、tag 和 payload 必须逐字节一致。

## 7. 性能与大小

M3-05 对每个基准 fixture 至少执行 30 次冷启动，分别报告未加固与加固的 P50、P95：

- process start 到 `Application.onCreate`；
- process start 到首个测试 Activity 可交互；
- 峰值 proportional set size；
- Native heap 峰值；
- 输入 APK、输出未签名 APK 和外部签名后 APK 大小；
- bootstrap、实际注入 Runtime ABI、四 ABI 全集基准和 container metadata 的分项增量。

测试设备在同一对比组中保持型号、系统镜像、电源模式和后台负载配置一致。异常值规则和统计脚本版本必须写入报告。预算由 M3-05 形成基线后作为发布配置入库，任何调整需要评审记录。

## 8. 测试证据格式

每项任务的证据至少记录：

```text
task_id
git_commit
command
exit_code
started_at
duration
os
cpu_arch
jdk
android_api
android_abi
tool_versions
artifact_paths
artifact_sha256
result_summary
```

不适用字段写 `not_applicable` 并说明原因，不能省略。产物路径使用仓库相对路径或 CI artifact 名，不使用构建机绝对路径。

## 9. 发布门禁

以下任一情况阻止发布：

- 支持矩阵存在未分类失败或 flaky 强制用例；
- 输入 SHA-256 改变；
- 输出带有有效签名或产品路径调用了签名工具；
- signer/container 篡改后业务探针执行；
- 明文 DEX 写入磁盘或出现在发布包/日志；
- Windows 与 Ubuntu 产生不同错误语义；
- ABI 或 API 声明与实测不一致；
- 性能、内存或大小超出已固化预算且无 `/root` 明确批准；
- SBOM、许可证、来源或校验和不完整；
- 安全敏感 PR 缺少独立复核。
