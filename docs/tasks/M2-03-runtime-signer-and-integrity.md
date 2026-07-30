---
id: M2-03
title: "Runtime 签名者与完整性校验"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M1-02
  - M2-01
required_skills:
  - implement-runtime-protection
security_sensitive: true
---

## Goal

在 payload 加载前验证当前安装 APK 的签名者身份、M1-02 固化的允许签名者集合及受保护元数据完整性，任何不一致均稳定 fail closed。

## Background

产品始终输出新的未签名 APK，且绝不接收或使用私钥、keystore、alias 或密码。Runtime 只能验证证书公钥摘要：正常发布流程须由使用者在产品外部用与输入 APK 策略一致的授权密钥签名。

## Inputs

- M1-02 生成的 `SignerPolicyV1`、证书 SHA-256 集合和签名轮换规则。
- M1-04 容器关联数据与受保护元数据摘要。
- M2-01 启动链及错误传播接口。
- ADR 0002、ADR 0006 与威胁模型。

## Expected Outputs

- `:runtime:policy` 中的 API 29+ Java 17 签名信息读取与规范化实现。
- `SignerPolicyV1` 校验器、元数据摘要校验器和稳定错误码。
- 同签名、异签名、签名轮换、多签名者及篡改场景的集成测试。
- 不含证书原文或敏感材料的审计事件。

## In Scope

- 使用 `PackageManager` 与 `SigningInfo` 获取当前 APK 的签名证书和历史。
- 按 M1-02 的规则比较当前签名者集合、轮换历史和允许摘要。
- 在调用 M2-02 前验证受认证元数据、容器标识和 APK 身份绑定。
- 多进程内一致、幂等且有上限的校验缓存。

## Out of Scope

- APK 签名、重签名、密钥生成、keystore 访问和证书托管。
- 在线许可证、远程证明或服务器密钥服务。
- 替代 Android 平台签名验证器。
- 仅依据环境风险信号判定签名无效。

## Implementation Decisions

- 模块路径固定为 `runtime/policy`，Android Runtime 源码位于 `src/main/java` 并使用 Java 17；不得应用 Kotlin Android plugin。
- 证书身份固定使用 DER 编码证书的 SHA-256 小写十六进制值；比较前验证为 64 个十六进制字符并使用常量时间比较。
- `SignerPolicyV1` 由 M1-02 写入受认证容器元数据，包含模式、当前签名者集合和允许的签名轮换历史；Runtime 不接受 Manifest 中未认证的覆盖值。
- 多签名者模式要求当前集合与策略集合完全相等；轮换模式要求当前签名者或平台返回的有效历史链满足 M1-02 的有序规则。
- 校验顺序固定为读取安装包身份、解析策略、验证当前签名者、验证元数据摘要、再允许 M2-02 打开 payload。
- 缓存键包含包名、版本号、APK `lastModified`、签名者集合摘要和进程启动标识；任一变化都重新校验。
- 产品代码中不得调用 `apksigner`、`jarsigner` 或任何签名 API。

## Public Interfaces

- `public final class RuntimeIntegrityVerifier`，通过静态方法 `public static IntegrityResult verify(Context context, AuthenticatedMetadata metadata)` 执行校验并禁止实例化。
- `public final class VerifiedSignerSet`，构造时复制并保存不可变的 `Set<String>` 证书摘要。
- `public final class IntegrityResult`，通过 `Status.VERIFIED`、`Status.REJECTED` 和稳定错误码表达结果。
- 错误码前缀 `AAH-RUNTIME-INTEGRITY-`；审计日志仅输出错误码和证书摘要前 12 位。

## Security Constraints

- 无签名、签名 API 异常、策略缺失、摘要格式错误、集合不匹配和元数据摘要不匹配均须 fail closed。
- 不得信任调用方传入的包名、APK 路径或 Manifest 明文摘要，实际值必须来自当前进程 `Context` 和受认证元数据。
- 不记录完整证书、完整摘要、签名块、设备路径或容器内容。
- 测试可在被忽略的构建输出目录生成一次性非生产证书，并由测试夹具在产品外部签名；证书及私钥不得提交，产品自身永不签名。

## Compatibility Requirements

- 仅使用 API 29 及以上公开的 `SigningInfo`/`PackageManager` 行为。
- 支持单签名者、多签名者和平台可见的签名轮换历史。
- 调试 fixture 与发布 fixture 使用相同验证算法，不得为 debug build 绕过校验。
- 自定义 `Application`、多进程和四个 Runtime ABI 的结果必须一致。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。

## Acceptance Criteria

- `./gradlew :runtime:policy:test :runtime:policy:connectedCheck` 退出码为 `0`。
- 输入 fixture 与受保护输出由同一一次性测试证书在外部签名时正常启动；改用另一张一次性证书签名时在 payload 加载前失败。
- 多签名者缺少、增加或替换任一证书，以及轮换历史顺序不合法时均以对应错误码失败。
- 篡改策略、容器标识或元数据摘要后，即使 APK 重新签名也不能加载 payload。
- 仓库扫描确认产品源集不存在私钥、keystore、alias、密码字段和 APK 签名调用；测试密钥目录受 `.gitignore` 约束。

## Required Tests

- 摘要规范化、常量时间集合比较、缓存失效和错误映射单元测试。
- 同签名、异签名、多签名、轮换历史和无签名 fixture 的 instrumentation 测试。
- 元数据、容器标识、包名绑定和摘要篡改测试。
- 多进程并发校验与缓存一致性测试。

## Required Evidence

- 执行命令、退出码、设备 API、测试证书生成方式和测试完成后的清理证明。
- 各签名场景的预期/实际错误码表。
- 产品源集签名能力与敏感词扫描结果。
- AAR、测试 APK、测试报告和日志摘要的 SHA-256；不附带任何私钥文件。

## Likely Files

- `runtime/policy/build.gradle.kts`
- `runtime/policy/src/main/java/ah/runtime/RuntimeIntegrityVerifier.java`
- `runtime/policy/src/main/java/ah/runtime/VerifiedSignerSet.java`
- `runtime/policy/src/main/java/ah/runtime/IntegrityResult.java`
- `runtime/policy/src/main/java/ah/runtime/SignerPolicy.java`
- `runtime/policy/src/test/`
- `runtime/policy/src/androidTest/`

## Dependencies and Blockers

M1-02 未冻结集合比较、轮换语义和序列化格式时不得实现。若平台 API 无法表达某种输入签名历史，必须阻塞该输入策略并记录兼容性限制，不得静默放宽为任意当前签名者。

## Agent Handoff Requirements

使用分支 `feat/m2-03-runtime-integrity`，只处理 Issue `M2-03` 并仅创建一个对应 PR。交接包须列明验证顺序、缓存键、每个负向场景、命令与退出码、设备信息、产物 SHA-256 和独立安全复核结论；确认测试证书为临时非生产材料且未进入提交。
