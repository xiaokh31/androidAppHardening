---
id: M1-02
title: 输入签名身份验证与输出签名策略
milestone: M1
status: planned
owner_role: host-pipeline-agent
depends_on:
  - M1-01
required_skills:
  - implement-apk-postprocessor
security_sensitive: true
---

## Goal

使用 Android 官方签名验证实现确认输入 APK 的签名有效且只有一个当前 signer，输出供容器、Runtime 和报告使用的规范化证书身份，产品始终不具备签名能力。

## Background

输出 APK 会因内容变化而失去输入签名，并必须在产品外由拥有者重新签名。Runtime 需要把安装后的当前 signer 与 Host 捕获的输入当前 signer 绑定；因此签名验证、轮换历史解释和无私钥边界必须在打包前固定。

## Inputs

- M1-01 通过检查且 SHA-256 稳定的只读 APK。
- 固定并通过依赖校验的 Android `apksig` 库。
- 仓库测试专用证书生成的 v1/v2/v3/v4、轮换、损坏、无签名与多 signer fixtures。

## Expected Outputs

- `SignerPolicyVerifier` 与不可变 `SignerPolicyV1`。
- 唯一当前 signer 的 DER certificate SHA-256、小写十六进制表示和经验证的轮换历史摘要。
- 稳定 `SIGNER_*` 错误码、报告字段和 Runtime config 输入模型。
- 证明生产模块没有签名或私钥入口的架构测试。

## In Scope

- 使用 `ApkVerifier` 按 Android API 29 最低平台语义验证输入。
- 区分当前 signer、历史 signer、多个当前 signer、无签名和结构损坏。
- 将当前 X.509 certificate DER bytes 计算 SHA-256，并规范化为 64 字符小写 hex。
- 形成“输出必须在产品外由同一当前 signer 签名”的 policy version 1。

## Out of Scope

- 生成、导入、选择或调用私钥、keystore、alias、密码、HSM、远程签名服务。
- 对输出 APK 执行 `apksigner` 或任何签名操作。
- 支持换用新 signer 签署输出；v0.1 只接受输入捕获的当前 signer。
- Runtime 侧启动早期 APK signer 校验；属于 M2-03。

## Implementation Decisions

- 使用固定版本 `com.android.tools.build:apksig`，`ApkVerifier.Builder` 的 minimum checked platform version 固定为 `29`。
- `ApkVerifier.Result.isVerified` 必须为 true，且 `getSignerCertificates()` 解析后的当前 signer 数必须严格为 `1`。
- v1/v2/v3 是否足够由 API 29 验证语义决定，不自行实现签名算法；v4 辅助文件不作为独立 APK 输入的一部分。
- 经官方库验证的 signing certificate lineage 以旧到新顺序记录 SHA-256；Runtime allowlist 只包含唯一当前 signer，不接受仅匹配历史 signer。
- `SignerPolicyV1` 固定 `policyVersion=1`、`requiredAfterProtection=true`、`performedByProduct=false`；可序列化安全字段只有 32 字节当前证书摘要和 `1..16` 个旧到新、无重复且以当前摘要结束的 32 字节 lineage 摘要。
- M1-04 必须按 ADR 0008 沿用的 `SPV1` wire layout 序列化上述安全字段；M1-02 不自创 JSON/Java serialization 作为 Runtime 输入。`verifiedSchemes`、`requiredAfterProtection` 与 `performedByProduct` 只进入 Host 报告，不进入 `SPV1`。
- 生产 CLI/API 不得出现名称或别名等价于 `keystore`、`privateKey`、`keyPassword`、`storePassword`、`alias` 或签名执行器的参数。
- 分支名固定为 `feat/m1-02-signer-policy`，Issue 标题固定为 `[M1-02] Signer policy`，仅允许一个关联 PR。

## Public Interfaces

- `SignerPolicyVerifier.verify(Path input, ApkInspection inspection): SignerPolicyV1`。
- `SignerPolicyV1` 字段：`policyVersion`、`currentCertificateSha256`、`lineageCertificateSha256`、`verifiedSchemes`、`requiredAfterProtection`、`performedByProduct`；提供面向 M1-04 的原始 32 字节摘要只读副本，不提供独立 wire encoder。
- 错误码：`SIGNER_UNSIGNED`、`SIGNER_INVALID`、`SIGNER_MULTIPLE_CURRENT`、`SIGNER_LINEAGE_INVALID`、`SIGNER_INPUT_CHANGED`、`SIGNER_INTERNAL`。
- 报告字段：`signing.input_verified=true`、`signing.current_certificate_sha256`、`signing.required=true`、`signing.performed=false`。

## Security Constraints

- 只读取 APK 内公开证书，不接受或访问私钥材料。
- 证书摘要按 DER certificate bytes 计算，不按 subject、serial、文件名或文本编码比较。
- 验证期间输入 SHA-256 必须与 `ApkInspection.inputSha256` 相同；变化时失败。
- 不在异常中输出完整证书、签名块、绝对路径或 apksig 原始诊断中的敏感路径。
- 本任务合并前必须完成独立密码学/签名语义复核。

## Compatibility Requirements

- Windows 与 Ubuntu 对相同 APK 返回相同 policy、scheme 集合和错误码。
- 接受官方库按 min API 29 判断有效的单当前 signer APK及有效轮换历史。
- 无签名、验证失败或多个当前 signer 一律拒绝，不提供宽松选项。
- 后续外部签名必须使用捕获的同一当前证书；使用历史或新证书应由 Runtime 拒绝。

## Acceptance Criteria

1. `./gradlew :host:apk-inspector:signerPolicyTest` 退出码为 `0`。
2. 有效单 signer fixtures 的摘要与独立 `apksigner verify --print-certs` 结果一致。
3. 无签名、篡改、截断 signing block、无效 lineage 和多当前 signer 分别返回规定错误码。
4. 有效轮换 fixture 的 lineage 顺序稳定，current digest 仅等于末端当前证书。
5. 对 APK 字节做并发修改时返回 `SIGNER_INPUT_CHANGED`，且不输出可消费 policy。
6. 源码/API 扫描确认生产模块没有签名选项、私钥类型、keystore 读取或签名工具调用。
7. Windows 与 Ubuntu 的规范化 policy JSON 相同。
8. `SPV1` 合同测试证明未轮换、有效轮换、空列表、超过 16 项、重复摘要和末项不等于当前摘要的处理与 ADR 0008 沿用的布局一致。

## Required Tests

- 各签名 scheme 与有效轮换 lineage 的正向测试。
- unsigned、tampered、malformed block、multi-signer、invalid lineage 负向测试。
- 证书摘要编码、顺序和输入变化测试。
- 禁止签名能力的 API/字节码架构测试。
- Windows/Ubuntu 与官方 `apksigner` 交叉验证。

## Required Evidence

- 所有命令、退出码、OS/JDK、apksig 与 apksigner 版本。
- fixtures、证书 DER、policy JSON 的 SHA-256；测试证书必须明确无生产价值。
- 错误码矩阵、cross-check 摘要和源码能力扫描结果。
- 独立安全 reviewer 结论、提交 SHA、Issue 与唯一 PR 链接。

## Likely Files

- `host/apk-inspector/src/main/kotlin/`
- `host/apk-inspector/src/test/kotlin/`
- `host/apk-inspector/src/test/resources/signing/`
- `docs/evidence/M1-02/`

## Dependencies and Blockers

- M1-01 必须提供稳定输入 SHA-256 和只读模型。
- `apksig` 来源、许可证、固定版本或 verification metadata 不满足治理要求时任务 blocked。
- 签名语义与 ADR-0002 冲突时必须由 `/root` 修订决策，不得加入签名功能。

## Agent Handoff Requirements

- 本任务固定使用分支 `feat/m1-02-signer-policy`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、环境、fixture/policy SHA-256、错误码矩阵及 reviewer 结论。
- worker 不修改根 `HandOff.md`，不实现 Runtime 验签或任何输出签名便利功能。
- 发现需要不同 signer 的业务需求时返回 blocked 交接；不得改变 v0.1 signer policy。
