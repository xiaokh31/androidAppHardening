---
id: M2-03
title: "Runtime 签名者与完整性校验"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M1-02
  - M1-04
  - M2-02
required_skills:
  - implement-runtime-protection
security_sensitive: true
---

## Goal

在 payload 加载前验证当前安装 APK 只有一个当前 signer、其身份等于 M1-02 固化的唯一允许当前 signer，并验证受保护元数据完整性；任何不一致均稳定 fail closed。

## Background

产品始终输出新的未签名 APK，且绝不接收或使用私钥、keystore、alias 或密码。Runtime 只能验证证书公钥摘要：正常发布流程须由使用者在产品外部用与输入 APK 策略一致的授权密钥签名。

## Inputs

- M1-02 生成的 `SignerPolicyV1`、唯一当前证书 SHA-256 和经验证的签名轮换历史。
- M1-04 容器关联数据、ConfigV2 与受保护配置摘要。
- ADR 0003/0007 与 M0-05 冻结的 `ApplicationInfo` 启动输入、早期 signer/config 证据和错误传播合同。
- ADR 0002、ADR 0006 与威胁模型。

## Expected Outputs

- `:runtime:policy` 中的 API 29+ Java 17 签名信息读取与规范化实现。
- `SignerPolicyV1` 校验器、ConfigV2 摘要/绑定校验器和稳定错误码。
- 同 signer、异 signer、有效轮换历史、多个当前 signer 拒绝及篡改场景的集成测试。
- 不含证书原文或敏感材料的审计事件。

## In Scope

- 在无 `Context` 的 `instantiateClassLoader` 路径中，使用 Framework 传入的 `ApplicationInfo.sourceDir` 和固定 Android `apksig` 验证当前 APK 的签名证书和历史。
- 要求平台返回的当前 signer 数严格为 `1`，并按 M1-02 的规则比较唯一当前证书摘要和可见轮换历史。
- 通过唯一 `RuntimeStartupGuard` 串联 M2-02 的有界未认证预读、当前 APK signer 验证、Native CEK/manifest MAC/`SPV1`/完整 ConfigV2 认证和 payload 打开；bootstrap 不得直接触达 M2-02。
- 多进程内一致、幂等且有上限的校验缓存。

## Out of Scope

- APK 签名、重签名、密钥生成、keystore 访问和证书托管。
- 在线许可证、远程证明或服务器密钥服务。
- 自行实现 APK Signature Scheme，或在启动回调中通过 hidden API 获取 `Context`/`PackageManager`。
- 仅依据环境风险信号判定签名无效。
- 接受多个当前 signer，或把任一历史 signer 当作允许的当前 signer。

## Implementation Decisions

- 模块路径固定为 `runtime/policy`，Android Runtime 源码位于 `src/main/java` 并使用 Java 17；不得应用 Kotlin Android plugin。
- 运行时签名验证使用与 M1-02 同一固定版本、同一来源校验的 Android `apksig`，最低检查平台固定为 29；`ApplicationInfo.sourceDir` 只能来自 Framework 参数并以只读方式打开。
- 证书身份固定使用 DER 编码证书的 SHA-256 小写十六进制值；比较前验证为 64 个十六进制字符并使用常量时间比较。
- M1-04 按 ADR 0004 把 M1-02 的 `SignerPolicyV1` 写入受 manifest MAC 认证的 `SPV1` block，并按 ADR 0006 写入 ConfigV2；Runtime 不接受 Manifest、调用参数、`ApplicationInfo.metaData` 或未认证预读对策略/Factory 的覆盖。
- `ApkVerifier.Result` 必须验证成功且当前 signer 数恰好为一个；其当前摘要必须常量时间等于未认证预读的期望摘要，随后作为实测摘要传给 M2-02。Native 认证 manifest MAC 后必须再次确认已认证 `SPV1` 当前摘要相等；历史必须有序、无重复并终止于当前证书，仅匹配历史证书仍拒绝。
- 校验顺序固定为：只读验证当前 APK并取得唯一 signer、从同一 Framework `ApplicationInfo.packageName` 计算精确 UTF-8 SHA-256、有界预读 ConfigV2/AHDC 且不分配 payload、预比较 signer、调用 Native 以 signer/package binding 恢复 CEK、认证 `SPV1`/record table、从已认证 header 常量时间比较完整 ConfigV2、复比较 signer/build/key slot/policy version、逐 record 鉴权/解压、再返回 session。Factory/风险配置在完整 ConfigV2 认证前不得暴露。
- 缓存键包含包名、版本号、APK `lastModified`、唯一当前 signer 摘要、历史摘要和进程启动标识；任一变化都重新校验。
- 产品代码中不得调用 `apksigner`、`jarsigner` 或任何签名 API。

## Public Interfaces

- 唯一生产入口为 `public final class ah.runtime.guard.RuntimeStartupGuard`，通过 `public static VerifiedPayloadSession openVerifiedPayload(ApplicationInfo applicationInfo, ClassLoader shellLoader)` 完成全序列并禁止实例化；ConfigV2/AHDC asset 名均为实现常量，接口不接受覆盖。
- `public final class ah.runtime.guard.VerifiedPayloadSession implements AutoCloseable`，只公开 `ClassLoader provisionalClassLoader()`、只读 `VerifiedSignerIdentity signer()`、只读 `VerifiedStartupConfiguration startupConfiguration()` 和幂等 `close()`，内部拥有 M2-02 `LoadedPayload`。final loader 由 M2-01 委托原 Factory 后决定，不回写 Guard session。
- `public final class VerifiedSignerIdentity`，保存唯一当前证书摘要及复制后的不可变有序 lineage 列表。
- `public final class VerifiedStartupConfiguration` 只在完整认证后构造，公开可选原 Factory 全限定名、container/signer/risk policy version 和 build/key slot 的不可变诊断副本；不暴露 share、nonce、wrapped CEK 或原始 config bytes。
- `public final class IntegrityResult`，通过 `Status.VERIFIED`、`Status.REJECTED` 和稳定错误码表达结果。
- 错误码前缀 `AAH-RUNTIME-INTEGRITY-`；审计日志仅输出错误码和证书摘要前 12 位。

## Security Constraints

- 无签名、多个当前 signer、签名 API 异常、策略缺失、摘要格式错误、当前 signer 不匹配、lineage 异常和 ConfigV2 摘要/绑定不匹配均须 fail closed。
- 不得接受调用方 APK 路径、包名或 Manifest 明文摘要；已安装 APK 路径与包名只取 Framework `ApplicationInfo.sourceDir`/`packageName`，安全策略只取 Native 认证后的容器元数据。
- 不记录完整证书、完整摘要、签名块、设备路径或容器内容。
- 测试可在被忽略的构建输出目录生成一次性非生产证书，并由测试夹具在产品外部签名；证书及私钥不得提交，产品自身永不签名。

## Compatibility Requirements

- 仅使用 API 29 及以上公开的 `ApplicationInfo.sourceDir`、只读文件 API 与固定 `apksig`；Context 可用后的 `SigningInfo` 只作为测试复核，不参与启动门禁。
- 支持唯一当前 signer 和平台可见的有效签名轮换历史；多个当前 signer 始终拒绝。
- 调试 fixture 与发布 fixture 使用相同验证算法，不得为 debug build 绕过校验。
- 自定义 `Application`、多进程和四个 Runtime ABI 的结果必须一致。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。

## Acceptance Criteria

- `./gradlew :runtime:policy:test :runtime:policy:connectedCheck` 退出码为 `0`。
- 输入 fixture 与受保护输出由同一一次性测试证书在外部签名时正常启动；改用另一张一次性证书签名时在 payload 加载前失败。
- 当前 signer 数不是 `1`、当前摘要不匹配、仅历史 signer 匹配，或轮换历史顺序不合法时均以对应错误码失败。
- 篡改策略、package name、容器标识、Factory slot 或 ConfigV2 摘要后，即使 APK 重新签名也不能加载 payload。
- 架构测试证明 `:runtime:bootstrap` 不含 `:runtime:native` compile dependency，不引用 `ah.runtime.loader`，且生产源码中 `PayloadRuntime` 的唯一调用者是 `RuntimeStartupGuard`。
- 仓库扫描确认产品源集不存在私钥、keystore、alias、密码字段和 APK 签名调用；测试密钥目录受 `.gitignore` 约束。

## Required Tests

- 摘要规范化、唯一 signer 常量时间比较、有序 lineage、缓存失效和错误映射单元测试。
- 同 signer、异 signer、多个当前 signer 拒绝、轮换历史和无签名 fixture 的 instrumentation 测试。
- ConfigV2、Factory slot、容器标识、包名绑定和摘要篡改测试。
- 多进程并发校验与缓存一致性测试。

## Required Evidence

- 执行命令、退出码、设备 API、测试证书生成方式和测试完成后的清理证明。
- 各签名场景的预期/实际错误码表。
- 产品源集签名能力与敏感词扫描结果。
- AAR、测试 APK、测试报告和日志摘要的 SHA-256；不附带任何私钥文件。

## Likely Files

- `runtime/policy/build.gradle.kts`
- `runtime/policy/src/main/java/ah/runtime/guard/RuntimeStartupGuard.java`
- `runtime/policy/src/main/java/ah/runtime/guard/VerifiedPayloadSession.java`
- `runtime/policy/src/main/java/ah/runtime/guard/VerifiedSignerIdentity.java`
- `runtime/policy/src/main/java/ah/runtime/guard/IntegrityResult.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/HardeningBootstrap.java`
- `runtime/policy/src/test/`
- `runtime/policy/src/androidTest/`

## Dependencies and Blockers

M1-02 未冻结唯一当前 signer 比较、轮换语义和序列化格式时不得实现。若平台 API 无法表达某种输入签名历史，必须阻塞该输入策略并记录兼容性限制，不得静默放宽为历史 signer 或多个当前 signer。

## Agent Handoff Requirements

使用分支 `feat/m2-03-runtime-integrity`，只处理 Issue `M2-03` 并仅创建一个对应 PR。交接包须列明验证顺序、缓存键、每个负向场景、命令与退出码、设备信息、产物 SHA-256 和独立安全复核结论；确认测试证书为临时非生产材料且未进入提交。
