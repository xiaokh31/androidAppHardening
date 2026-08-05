# ADR 0004: 版本化认证加密 DEX 容器

## Status

Superseded by [ADR 0008](0008-chunk-authenticated-dex-container.md)

## Context

Host 与 Android Runtime 需要一个稳定、可演进且能安全解析的二进制合同来承载单个或多个业务 DEX。简单拼接无法可靠表达长度、顺序和版本；只加密不认证会允许攻击者修改密文或元数据；使用 ZIP 内多个自定义密文条目会分散版本与完整性边界。

本 ADR 保留 AHDC v1 的历史决策，不再授权产品实现。独立复核确认“每个 DEX 一个 GCM tag”、512 MiB 单 DEX 上限、认证后才解压和 1 MiB 工作缓冲在固定 JCA Provider 上不能同时成立；尚未发布的 v1 由 ADR 0008 的 AHDC v2 取代，reader 不得回退接受 v1。

容器 parser 同时运行在 Host 验证器和 Android Native Runtime 中，所有字段都必须能进行有界、溢出安全的解析。

## Decision

定义 Android Hardening DEX Container，扩展名 `.ahdc`，当前格式为 major `1`、minor `0`，默认 APK 路径为：

```text
assets/ah/runtime/payload.ahdc
```

### 序列化

- magic 为 4 字节 ASCII `AHDC`。
- 所有整数使用 little-endian 固定宽度无符号表示。
- 文件严格按 `HeaderV1 || SignerPolicyV1 || RecordV1[dex_count] || Payload` 拼接，不允许节间 padding、空洞或文件尾随字节。所有 offset 均从相应规范基准计算，不使用 ZIP/APK 绝对 offset。
- `HeaderV1` 固定 128 字节，offset/字段如下：

| Offset | Size | Field | v1 rule |
| ---: | ---: | --- | --- |
| 0 | 4 | magic | ASCII `AHDC` |
| 4 | 2 | major | `1` |
| 6 | 2 | minor | `0` |
| 8 | 2 | header_size | `128` |
| 10 | 2 | flags | `0` |
| 12 | 4 | dex_count | `1..128` |
| 16 | 4 | signer_policy_size | `44 + lineage_count * 32` |
| 20 | 4 | record_table_size | `dex_count * 104` |
| 24 | 8 | payload_size | 所有 ciphertext 与 16-byte tag 的总长度 |
| 32 | 16 | build_id | CSPRNG |
| 48 | 16 | key_slot_id | CSPRNG |
| 64 | 32 | config_sha256 | 完整 `config.bin` 的 SHA-256 |
| 96 | 32 | manifest_mac | HMAC-SHA-256 |

- signer policy block 紧随 header、位于 record table 之前；其 wire layout 固定为：4 字节 ASCII `SPV1`、`u16le schema_version=1`、`u16le flags=0`、`u16le lineage_count`、`u16le reserved=0`、32 字节当前证书 SHA-256 原始摘要、随后 `lineage_count * 32` 字节的证书 SHA-256 原始摘要。`lineage_count` 为 `1..16`，列表按旧到新排列、无重复，最后一项必须等于当前摘要；未轮换 signer 的列表只有当前摘要。
- major `1` 的 flags 必须为 `0`；`flags=0` 隐含本 ADR 规定的固定压缩语义，不表示“未压缩”。未知 major 或未知 flags 失败关闭。
- `RecordV1` 固定 104 字节，按零基 ordinal `0..dex_count-1` 严格递增：

| Offset | Size | Field | v1 rule |
| ---: | ---: | --- | --- |
| 0 | 4 | ordinal | 首条 `0`，连续递增 |
| 4 | 2 | name_length | canonical ASCII name 的字节数 |
| 6 | 2 | reserved | `0` |
| 8 | 8 | original_length | 压缩前原始 DEX 长度 |
| 16 | 8 | ciphertext_length | 压缩后字节长度，不含 tag |
| 24 | 8 | payload_offset | 相对 Payload 起点；首条为 `0` |
| 32 | 12 | nonce | 该 record 的 GCM nonce |
| 44 | 24 | name | ASCII 名称后以零填满；填充必须全零 |
| 68 | 32 | original_sha256 | 压缩前原始 DEX SHA-256 |
| 100 | 4 | reserved2 | `0` |

- canonical name 由 ordinal 唯一决定：ordinal `0` 为 `classes.dex`，ordinal `n>=1` 为 `classes{n+1}.dex`，不允许前导零、别名或非 ASCII。`name_length` 必须等于该规范名长度并且不超过 24。
- “原始长度”和“原始 SHA-256”始终指压缩前的原始 DEX；“ciphertext length”指压缩后字节经 AES-GCM 加密所得 ciphertext 的长度，不包含其后的 16 字节 tag。AES-GCM 不改变输入字节长度，因此该值也等于压缩后字节长度。
- `record_table_size` 必须恰好为 `dex_count * 104`。每条 `payload_offset` 必须等于前面所有 `ciphertext_length + 16` 之和；最后一条结束位置必须恰好等于 `payload_size`。
- payload 保存每条 DEX 的 AES-GCM ciphertext 与紧随其后的 16 字节 tag；ciphertext 解密后的内容是 zlib 数据流，不是原始 DEX。不允许 payload 重叠、空洞或包含尾随未声明字节。
- parser 必须完整消费文件，拒绝截断、额外尾随数据、重复 ordinal、非规范名称和大小不一致。

### 压缩与加密流水线

AHDC v1 对每个原始 DEX 独立执行固定流水线：

```text
original DEX
-> zlib-wrapped DEFLATE level 9 without preset dictionary
-> AES-256-GCM
-> ciphertext || 16-byte tag
```

- 压缩格式必须是带 zlib wrapper 的 DEFLATE，不得使用 raw DEFLATE 或 gzip wrapper。
- 压缩级别固定为 `9`，不得使用 preset dictionary。
- `flags=0` 即表示上述唯一 AHDC v1 压缩语义；v1 不提供按容器或按 record 选择“无压缩”、其他级别、其他 wrapper 或 dictionary 的能力。
- Host 第一遍流式读取原始 DEX，计算原始长度、原始 SHA-256 和压缩后长度；第二遍使用完全相同的 zlib 参数重新压缩，并将压缩字节直接送入 AES-GCM。两个遍次观察到的原始长度、SHA-256 或压缩后长度不一致时必须失败。
- 原始 DEX 与压缩后未加密字节均不得写入磁盘。

### 密码学

每次保护运行由操作系统 CSPRNG 生成新的 256-bit CEK、build ID、key slot ID 和每条 DEX 唯一 96-bit nonce。

通过 HKDF-SHA-256 从 CEK 派生：

```text
K_manifest = HKDF(CEK, build_id, "AHDC manifest v1", 32)
K_dex_i    = HKDF(CEK, build_id, "AHDC dex v1" || ordinal_u32le, 32)
```

manifest MAC 使用 HMAC-SHA-256，按文件字节顺序覆盖 128-byte header、完整 signer policy block 与完整 record table，计算时 header `[96,128)` 的 manifest MAC 字段置零；不覆盖 Payload。每条 DEX 先按固定 zlib 语义压缩，再使用 `K_dex_i` 和 AES-256-GCM 加密压缩后字节。
`package_name_sha256` 精确定义为输入 Binary AXML 中、经 M1-01 验证且不做大小写或 Unicode 变换的 package name UTF-8 bytes 的 SHA-256。每条 DEX 的 AAD 精确为 `ASCII("AHDC-GCM-V1") || header[4,8) || build_id || key_slot_id || current_signer_sha256 || package_name_sha256 || RecordV1`，其中 `RecordV1` 是文件中该 104-byte canonical record。Runtime 只能从 Framework 传入的 `ApplicationInfo.packageName` 计算同一摘要，不接受调用方或未认证元数据覆盖。

Runtime 的验证顺序固定为：

1. 在不分配 payload 大小内存的情况下检查 magic、版本、flags 和全局边界。
2. 恢复 CEK。
3. 验证 manifest MAC，并只在成功后把 `SPV1` signer policy 视为已认证；已安装 signer 必须与已认证当前摘要再次相等。
4. 按 ordinal 逐条验证边界，完成 AES-GCM tag 鉴权并解密得到经过认证的 zlib 数据流；认证完成前不得解压或使用其内容。
5. 使用 zlib-wrapped DEFLATE、无 dictionary 语义解压，并以 record 声明的原始 DEX 长度作为严格输出上限；需要 dictionary、流未完整结束、存在压缩流尾随数据或超出上限时失败。
6. 解压完成后验证所得原始 DEX 的实际长度和 SHA-256 与 record 一致。
7. 只有上述检查全部通过后，才将原始 DEX 交给内存 ClassLoader。

CEK 的离线封装与恢复由 [ADR-0006](0006-offline-key-protection-boundary.md) 定义，不改变 `.ahdc` 的内容加密语义。

## Consequences

积极结果：

- 单/多 DEX 使用同一可版本化合同；
- metadata 与 payload 均受认证；
- 每条 DEX 可独立鉴权、解密、解压并缩短内存生命周期；
- 固定 level 9 压缩可控制业务 DEX 容器的大小增量；
- Host 与 Runtime 可以用相同 corpus 做 parser 一致性和模糊测试；
- major/minor 规则允许受控演进。

代价：

- 相比原 DEX 增加 header、record、nonce、hash、MAC 和 tag；
- Runtime 增加 zlib 解压 CPU、压缩字节缓冲和严格输出上限管理；
- 每次运行随机，因此业务输出 APK 不位级可复现；
- Host 与四 ABI Native parser 必须严格保持规范一致；
- 格式改变必须管理兼容 reader 和版本迁移。

## Rejected Alternatives

- 固定 XOR 或自定义流加密：不提供标准认证安全性。
- AES-CBC 且无 MAC：允许篡改并产生 padding oracle 风险。
- 单个全容器 GCM 消息：需要更大连续缓冲，难以按 DEX 流式处理。
- 每个 DEX 独立自定义 asset 而无 manifest：缺乏统一次序、版本和完整性合同。
- raw DEFLATE、gzip 或 preset dictionary：会增加格式协商与 Runtime 依赖，且与 AHDC v1 的固定 zlib 解码合同不兼容。
- v1 按 record 选择压缩算法或关闭压缩：扩大 parser 状态空间，并使 `flags=0` 无法表达唯一的线上格式语义。
- 确定性 nonce 或由文件名派生 nonce：可能导致同 key 下 nonce 重用。
- Java object serialization 或 protobuf 动态字段作为 Native 边界：解析面更大，规范消费与溢出行为不够直接。

## Security Impact

AES-256-GCM 和 HMAC-SHA-256 提供密文、记录表和 header 的认证。随机 CEK 与 nonce 防止不同运行复用。Runtime 必须先完成 GCM 鉴权再向 zlib 提交数据，并以已认证 record 中的原始长度限制解压输出，降低恶意压缩流造成资源耗尽的风险。原始 DEX SHA-256 用于解压后的一致性验证，不替代 GCM 认证。

本格式不隐藏 DEX 数量和近似大小，也不能阻止拥有 Runtime 控制权的攻击者恢复 CEK 或内存明文。所有错误在发行日志中映射为非敏感稳定分类，不返回 tag、key 或明文内容。

## Compatibility Impact

DEX ordinal 和类查找顺序与输入一致。v0.1 reader 只接受 major `1`；minor 增量只能增加当前 reader 能安全忽略、且不改变既有语义的内容。AHDC v1 的 `flags=0` 固定表示 zlib-wrapped DEFLATE level 9、无 dictionary；改变压缩算法、wrapper、level、dictionary、字段语义或密码学必须增加 major version，或通过经 ADR 接受的新非零 flag 明确协商，不能改变 `flags=0` 的既有含义。

## Verification

- Host 与四 ABI Runtime 使用共享 golden vectors 验证 `original DEX -> zlib-wrapped DEFLATE level 9 without dictionary -> AES-GCM` 的逐字节合同，并确认 zlib header 未设置 dictionary 标志。
- 每个单/多 DEX vector 在鉴权、解密和解压后，其实际长度、SHA-256、字节内容与原始 DEX 完全一致；record 的明文长度/SHA-256 与原始 DEX 对应，ciphertext 长度与压缩后字节长度对应且不包含 tag。
- raw DEFLATE、gzip、要求 dictionary、截断压缩流、带尾随字节的压缩流、解压超出声明原始长度，以及带有效认证但原始长度/SHA-256 错误的测试向量均失败。
- 对每个字段执行截断、溢出、最大值、重复、乱序、未知版本与未知 flags 测试。
- 修改 header、`SPV1` 任一字段、record、nonce、ciphertext 或 tag 时在解压和业务 DEX 加载前失败。
- 同一 CEK 下 nonce 唯一性由构建器断言，跨大量样本执行统计检查。
- parser 接受覆盖引导模糊测试和 Native sanitizer。
- 输出扫描确认不存在原 DEX 明文条目或额外 DEX magic。
