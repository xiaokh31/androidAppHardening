# ADR 0006: 离线密钥保护边界

## Status

Accepted

## Context

业务 DEX 使用认证加密后，Android Runtime 必须在没有网络、用户输入、私钥或远程服务的情况下恢复内容加密密钥。任何完全离线且自包含的客户端方案最终都携带恢复密钥所需的信息；拥有静态分析、动态 Hook 或进程控制能力的攻击者最终可能复现恢复过程。

项目仍需要避免明文固定密钥、跨 APK 共用密钥、日志泄露和单点静态提取，同时不得对使用方声称存在不可提取的客户端秘密。

## Decision

采用每 APK 随机密钥、分片、signer 绑定和短生命周期恢复：

1. Host 使用操作系统 CSPRNG 为每次保护生成 256-bit CEK。
2. Host 生成 256-bit 随机 root material `R` 和等长随机 Java share `R_java`。
3. Native share 定义为 `R_native = R XOR R_java`；两个 share 分别进入 bootstrap 与各适用 ABI Native Runtime，不以完整 `R` 写入单一静态位置。
4. 使用输入唯一当前 signer 的公开证书 SHA-256、容器 build ID 和固定 domain separation 字符串派生 KEK：

```text
KEK = HKDF-SHA-256(
  IKM = R,
  salt = build_id,
  info = "AHDC offline KEK v1" || signer_sha256,
  length = 32
)
```

5. CEK 使用 KEK 和 AES-256-GCM 封装，采用独立随机 96-bit nonce；AAD 包含 container major version、build ID、key slot ID 和 signer SHA-256。
6. Runtime 先通过公开 Android API验证安装 signer，再在 Native 边界短暂重组 `R`、派生 KEK、认证并恢复 CEK。
7. CEK 只用于 [ADR-0004](0004-versioned-encrypted-dex-container.md) 的子密钥派生；临时 `R`、KEK、CEK 和明文缓冲在使用后以不会被编译器省略的方式清零。

产品不接受用户 passphrase、私钥、keystore、alias、密码、设备标识符或在线 token。所有恢复材料随输出 APK 本地存在；不引入远程 KMS、license server 或设备注册。

share 的代码级布局可以在不改变上述密码学合同的前提下做版本化混淆，但混淆不能被描述为密码学安全。Runtime 必须拒绝 signer、AAD、wrapped CEK tag 或 key slot 不一致。

## Consequences

积极结果：

- 每次保护使用不同 CEK，单个 APK 泄露不直接暴露其他 APK；
- 完整密钥不以简单常量存放；
- signer 不匹配会阻止正常 CEK 恢复；
- 不需要联网、账户、设备注册或签名秘密；
- 认证封装能发现 wrapped CEK 和绑定元数据篡改。

代价：

- 所有恢复信息最终在客户端，确定性逆向仍然可行；
- Java 与四 ABI Native 构建必须一致管理 share；
- 输出每次随机，业务 APK 不位级可复现；
- signer 更换会导致 Runtime 拒绝，v0.1 不支持迁移。

## Rejected Alternatives

- 明文 CEK 常量：单点静态提取，且容易跨 APK 复用。
- 所有 APK 共用项目主密钥：一个样本泄露影响全部输出。
- 仅以 signer digest 作为密钥：证书是公开信息，不能提供秘密熵。
- 用户 passphrase：扩大交互、凭据和自动化边界。
- keystore 私钥派生：产品不得接触签名秘密，且签名私钥通常不可导出。
- 在线 KMS 或 license server：违反离线产品目标并引入可用性与隐私依赖。
- 仅依赖字符串/Native 混淆：不能替代标准认证加密和随机密钥。

## Security Impact

该方案提高静态提取成本并限制跨 APK 影响，但不建立硬件信任根。攻击者可 patch signer 检查、Hook 恢复函数或读取进程内 CEK/DEX。产品文档必须持续明确这一残余风险。

Host 和 Runtime 日志不得输出 share、R、KEK、CEK、nonce、wrapped CEK 或解密缓冲。崩溃路径也必须清理已分配的敏感内存。

## Compatibility Impact

输出必须由输入的同一当前 signer 在产品外签名。证书轮换到新当前 signer、重新包名签名或测试证书替换都会导致 Runtime 拒绝，除非未来通过新的 signer migration ADR 和容器版本支持。

所有四 ABI 使用相同 key slot 与派生合同；单个输出只包含 [ADR-0005](0005-runtime-abi-policy.md) 允许的 ABI share。

## Verification

- 固定测试向量验证 Java/Host 与四 ABI Native 的 HKDF、AES-GCM 封装和恢复一致。
- 大量构建样本检查 CEK、R、share、nonce、build ID 和 key slot 不重复。
- 修改 signer digest、build ID、key slot、nonce、ciphertext 或 tag 时恢复失败。
- 二进制与日志扫描确认不存在完整 CEK/R、测试向量秘密或调试打印。
- 内存 instrumentation 验证正常与异常路径执行清零。
- 不同 signer 重签的设备测试在业务探针运行前失败。
- 安全评审明确确认发布表述只声称提高成本，不声称不可提取。
