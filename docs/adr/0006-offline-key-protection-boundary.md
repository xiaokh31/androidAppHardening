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
3. Native share 定义为 `R_native = R XOR R_java`；`R_java` 进入 bootstrap 消费的固定 `config.bin`，同一个 `R_native` 分别写入各适用 ABI Native Runtime 的专用只读 slot，不以完整 `R` 写入单一静态位置。
4. 使用输入唯一当前 signer 的公开证书 SHA-256、输入 package name 的规范摘要、容器 build ID 和固定 domain separation 字符串派生 KEK。`package_name_sha256` 精确定义为输入 Binary AXML 中、经 M1-01 验证且不做大小写或 Unicode 变换的 package name UTF-8 bytes 的 SHA-256：

```text
KEK = HKDF-SHA-256(
  IKM = R,
  salt = build_id,
  info = "AHDC offline KEK v1" || signer_sha256 || package_name_sha256,
  length = 32
)
```

5. CEK 使用 KEK 和 AES-256-GCM 封装，采用独立随机 96-bit nonce；AAD 为下面 `ConfigV2` 的精确 132-byte prefix。
6. Runtime 先通过 Framework 传入的公开 `ApplicationInfo.sourceDir` 与固定 `apksig` 验证安装 signer，并只从同一 `ApplicationInfo.packageName` 计算 package 摘要，再把两项实测绑定交给 Native；Native 短暂重组 `R`、派生 KEK、认证并恢复 CEK，验证覆盖 `SPV1` 的 manifest MAC 后再次比较已认证 signer。
7. CEK 只用于 [ADR-0008](0008-chunk-authenticated-dex-container.md) 的子密钥派生；临时 `R`、KEK、CEK 和明文缓冲在使用后以不会被编译器省略的方式清零。

### ConfigV2 wire layout

`assets/ah/runtime/config.bin` 固定为 768 字节，不允许 padding 或尾随数据。它是 [ADR-0007](0007-source-dir-startup-configuration.md) 的唯一启动配置来源。所有整数为 little-endian：

| Offset | Size | Field | v2 rule |
| ---: | ---: | --- | --- |
| 0 | 4 | magic | ASCII `AHKC` |
| 4 | 2 | major | `2` |
| 6 | 2 | minor | `0` |
| 8 | 2 | flags | bit 0=`HAS_ORIGINAL_FACTORY`；其余位必须为 `0` |
| 10 | 2 | reserved | `0` |
| 12 | 4 | total_size | `768` |
| 16 | 2 | container_major | `2` |
| 18 | 2 | signer_policy_version | `1`，必须等于已认证 `SPV1.schema_version` |
| 20 | 2 | risk_policy_version | `1` |
| 22 | 2 | original_factory_length | 无 Factory 时为 `0`；有 Factory 时为 `1..512` |
| 24 | 16 | build_id | 必须等于 AHDC header |
| 40 | 16 | key_slot_id | 必须等于 AHDC header |
| 56 | 32 | signer_sha256 | 必须等于实测 signer 和已认证 `SPV1` 当前摘要 |
| 88 | 32 | R_java | 每 APK 随机 share |
| 120 | 12 | wrap_nonce | 每 APK 随机 GCM nonce |
| 132 | 32 | wrapped_cek_ciphertext | 32-byte CEK 的 ciphertext |
| 164 | 16 | wrapped_cek_tag | AES-GCM tag |
| 180 | 512 | original_factory_utf8 | 原 Factory 的严格 UTF-8 固定 slot |
| 692 | 76 | reserved_tail | 全部为 `0` |

`HAS_ORIGINAL_FACTORY=0` 时 `original_factory_length` 必须为 `0` 且 512-byte slot 全零；flag 为 `1` 时长度必须为 `1..512`，前 `length` 字节是完整、最短编码的严格 UTF-8，剩余 slot 全零。解码结果不得含 NUL，必须通过 Java 全限定类名语法校验，且不得等于 Shell Factory。Host 使用 M1-01 已按 Manifest package 规则规范化的原 `android:appComponentFactory` 字符串，不做 Unicode NFC、大小写或额外别名变换。原 Application 名称不存入 config；Framework 的 `instantiateApplication` `className` 是唯一来源。

CEK envelope 的 AAD 精确为 `config.bin[0,132)`；该 prefix 包含 header、版本、Factory 长度、build/key slot、signer、`R_java` 和 nonce，但不包含 ciphertext、tag 或 Factory slot。package 绑定由 KEK `info` 中的 `package_name_sha256` 提供。完整 768-byte config 的 SHA-256 必须写入 ADR 0008 `HeaderV2.config_sha256`；Runtime 在恢复 CEK 后验证 manifest MAC，从已认证 header 取得期望 config SHA-256，再常量时间比较实际 config。Factory 名称与风险策略在该比较和所有 binding 交叉验证完成前仍是不可信数据，不得暴露或使用。修改 ConfigV2 任一 byte、signer/package public binding、`R_java`、nonce、ciphertext 或 tag 均必须导致 envelope 或后续 manifest 认证失败。

预发布 `ConfigV1` 在任何生产实现和发布前被 ConfigV2 替代。v0.1 reader 只接受 major `2`；不得兼容或回退 176-byte ConfigV1。

### NativeShareSlotV1

M2-04 为每个 ABI Runtime template 提供一个且仅一个 ELF section `.ah_share_v1`，固定 104 字节、只读映射、不得被 strip。模板 section 使用以下 placeholder：offset `0` 为 ASCII `AHP0`，offset `4` 为 `u16le version=1`，offset `6` 为 ABI ID，其余 96 字节全零。ABI ID 固定为 `1=armeabi-v7a`、`2=arm64-v8a`、`3=x86`、`4=x86_64`。

M1-05 materializer 验证 RuntimeBundle/template SHA-256、ELF machine、section 数量、size、placeholder 和 ABI ID 后，把 section 原地替换为：

| Offset | Size | Field | v1 rule |
| ---: | ---: | --- | --- |
| 0 | 4 | magic | ASCII `AHS1` |
| 4 | 2 | version | `1` |
| 6 | 2 | abi_id | 上述固定映射 |
| 8 | 16 | key_slot_id | 必须等于 ConfigV2 |
| 24 | 16 | build_id | 必须等于 ConfigV2 |
| 40 | 32 | R_native | `R XOR R_java` |
| 72 | 32 | slot_sha256 | 对 section `[0,72)` 的 SHA-256 |

`slot_sha256` 只用于损坏诊断，不是攻击者不可伪造的认证。Runtime 必须同时验证 ELF ABI、slot version/ID、build ID、key slot、config digest、CEK envelope tag 和 AHDC manifest MAC。所有被选择 ABI 的 slot 使用同一 `R_native`；未选择 ABI 的 template 不进入输出。bootstrap DEX 不做每 APK 二进制 patch，只按固定代码读取 `config.bin` 并调用对应 ABI Runtime。

`KeyPackagingPlanV2` 是 Host 内存所有权对象，固定持有完整 `ConfigV2`、`R_native`、build/key slot 和目标 ABI 集；M1-05 消费一次后清零。它不是可持久化文件格式，不得序列化到日志、报告或工作目录。改变 `ConfigV2`、slot layout、ABI ID、AAD 或 materialization 规则必须增加 major version并先修订 ADR。

产品不接受用户 passphrase、私钥、keystore、alias、密码、设备标识符或在线 token。所有恢复材料随输出 APK 本地存在；不引入远程 KMS、license server 或设备注册。

固定 wire layout 之外的代码控制流可做版本化混淆，但不得改变字段字节或被描述为密码学安全。Runtime 必须拒绝 signer、AAD、wrapped CEK tag、config digest、build ID、key slot、ABI ID 或 slot digest 不一致。

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
- 修改 signer digest、Framework package name、build ID、key slot、nonce、ciphertext 或 tag 时恢复失败。
- ConfigV2 768-byte 与四种 NativeShareSlotV1 104-byte golden vectors 在 Host/四 ABI Native 间逐字节一致；任何 flags、Factory length/UTF-8/zero-fill、reserved、size、ABI ID、share、slot digest 或 config digest 篡改均失败。
- 二进制与日志扫描确认不存在完整 CEK/R、测试向量秘密或调试打印。
- 内存 instrumentation 验证正常与异常路径执行清零。
- 不同 signer 重签的设备测试在业务探针运行前失败。
- 安全评审明确确认发布表述只声称提高成本，不声称不可提取。
