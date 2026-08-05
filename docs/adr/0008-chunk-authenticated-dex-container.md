# ADR 0008: 分块认证的 DEX 容器

## Status

Accepted

## Context

ADR 0004 的 AHDC v1 为每个 DEX 生成一条 AES-256-GCM 消息。任务合同同时要求支持 M1-01 允许的 512 MiB 单 DEX、在认证成功前不向 zlib 提交数据，并把工作缓冲限制在 1 MiB。固定 JDK 17 的 SunJCE 在解密时可以把整条 ciphertext 保留到 `doFinal` 才释放已认证 plaintext；其他 Provider 即使从 `update` 返回 plaintext，也不能在最终 tag 成功前安全消费。因而 v1 的三个约束无法由相同、可移植的 JCA 合同同时满足。

该冲突在 M1-04 首个冻结实现的独立安全复核中被列为 P1。格式尚未发布，因此应修订共享 Host/Runtime wire contract，而不是依赖 Provider 特有的未认证流式行为、提高内存上限或缩小已承诺的输入范围。

## Decision

AHDC 当前且唯一允许实现的格式提升为 major `2`、minor `0`。AHDC v2 在压缩后按规范 64 KiB plaintext chunk 分割，每个 chunk 是独立的 AES-256-GCM 消息；Runtime 只有在该 chunk 的 tag 验证成功后，才把其中的已认证压缩字节提交给同一个连续 zlib inflater。

默认 APK 路径保持 `assets/ah/runtime/payload.ahdc`。

### 文件序列化

- magic 为 4 字节 ASCII `AHDC`，所有整数为 little-endian 固定宽度无符号值。
- 文件严格为 `HeaderV2 || SPV1 || RecordV2[dex_count] || ChunkV2[chunk_count] || Payload`；节间无 padding、空洞或尾随字节。
- `SPV1` wire layout 逐字节沿用 ADR 0004：`SPV1` magic、schema `1`、flags `0`、`1..16` lineage count、零 reserved、当前 signer 摘要和按旧到新排列且末项等于当前摘要的 lineage。
- v0.1 限制 `dex_count=1..64`；`chunk_count=1..65536`，并同时受容器总长不超过 `2,147,483,647` bytes 的 checked arithmetic 限制。

`HeaderV2` 固定 160 bytes：

| Offset | Size | Field | v2 rule |
| ---: | ---: | --- | --- |
| 0 | 4 | magic | ASCII `AHDC` |
| 4 | 2 | major | `2` |
| 6 | 2 | minor | `0` |
| 8 | 2 | header_size | `160` |
| 10 | 2 | flags | `0` |
| 12 | 4 | dex_count | `1..64` |
| 16 | 4 | signer_policy_size | `44 + lineage_count * 32` |
| 20 | 4 | record_table_size | `dex_count * 128` |
| 24 | 4 | chunk_count | `1..65536` |
| 28 | 4 | chunk_table_size | `chunk_count * 32` |
| 32 | 8 | payload_size | 所有 `plaintext_length + 16` 的 checked sum |
| 40 | 16 | build_id | OS CSPRNG |
| 56 | 16 | key_slot_id | OS CSPRNG |
| 72 | 32 | config_sha256 | 完整 768-byte ConfigV2 SHA-256 |
| 104 | 32 | manifest_mac | HMAC-SHA-256 |
| 136 | 4 | chunk_plaintext_max | `65536` |
| 140 | 20 | reserved | 全零 |

`RecordV2` 固定 128 bytes，按 ordinal `0..dex_count-1` 连续递增：

| Offset | Size | Field | v2 rule |
| ---: | ---: | --- | --- |
| 0 | 4 | ordinal | 连续零基 ordinal |
| 4 | 2 | name_length | canonical ASCII 名称长度 |
| 6 | 2 | flags | `0` |
| 8 | 8 | original_length | `1..536870912`，压缩前 DEX 长度 |
| 16 | 8 | compressed_length | `1..容器剩余上限`，完整连续 zlib 流长度 |
| 24 | 4 | chunk_count | `ceil(compressed_length / 65536)` |
| 28 | 4 | first_chunk_index | 在全局 chunk table 中的首项 |
| 32 | 8 | payload_offset | 该 record 首个 payload chunk 的相对 offset |
| 40 | 8 | nonce_prefix | OS CSPRNG，非零 |
| 48 | 24 | name | canonical ASCII 名称，余下全零 |
| 72 | 32 | original_sha256 | 压缩前 DEX SHA-256 |
| 104 | 24 | reserved | 全零 |

canonical name 与 ADR 0004 相同：ordinal `0` 为 `classes.dex`，其后为 `classes{ordinal+1}.dex`，不允许前导零、别名或非 ASCII。

`ChunkV2` 固定 32 bytes，按 record ordinal、record 内 chunk ordinal 的字典序连续排列：

| Offset | Size | Field | v2 rule |
| ---: | ---: | --- | --- |
| 0 | 4 | record_ordinal | 所属 `RecordV2.ordinal` |
| 4 | 4 | chunk_ordinal | record 内从 `0` 连续递增 |
| 8 | 8 | compressed_offset | 相对该 DEX zlib 流起点 |
| 16 | 8 | payload_offset | 相对全局 Payload 起点 |
| 24 | 4 | plaintext_length | `1..65536` |
| 28 | 4 | reserved | `0` |

每个非末尾 chunk 的 `plaintext_length` 必须恰为 `65536`，末尾 chunk 为 `1..65536`。`compressed_offset` 和 `payload_offset` 必须分别等于此前相应长度的累计值；record 的 chunk range 必须连续、非空、不重叠且恰好覆盖其 `compressed_length`。每个 payload chunk 固定为 `ciphertext[plaintext_length] || tag[16]`；全部 chunk 恰好覆盖 `payload_size`。

### 压缩与构建

每个原始 DEX 仍使用一条连续的 zlib-wrapped DEFLATE level `9` 流，不使用 dictionary；chunk 边界只切分压缩后的字节，不重置 zlib 状态。

Host 使用两遍流程。第一遍计算原始长度、原始 SHA-256、压缩长度和规范 chunk table；第二遍以完全相同的 zlib 参数重新压缩，填满 64 KiB chunk 后逐块认证加密。两遍观察到的长度、摘要或压缩长度不同必须以 `CONTAINER_INPUT_CHANGED` 失败。原始 DEX 和压缩明文不得落盘。

### 密钥、nonce、AAD 与 manifest

每次运行由 OS CSPRNG 生成新的 CEK、build ID、key slot ID 和每条 record 的 8-byte 非零 nonce prefix。派生合同为：

```text
K_manifest = HKDF-SHA-256(CEK, build_id, "AHDC manifest v2", 32)
K_record_i = HKDF-SHA-256(CEK, build_id, "AHDC record v2" || ordinal_u32le, 32)
```

record 内每个 chunk 的 96-bit GCM nonce精确为 `nonce_prefix[8] || chunk_ordinal_u32le`。每个 record 使用独立派生 key，因此 nonce 唯一性在 `(K_record_i, nonce)` 边界内成立；同一 record 不允许 ordinal 重复或超过 `u32`。

每个 chunk 的 AAD 精确为：

```text
ASCII("AHDC-GCM-V2")
|| header[4,8)
|| build_id
|| key_slot_id
|| current_signer_sha256
|| package_name_sha256
|| RecordV2
|| ChunkV2
```

`package_name_sha256` 是经 M1-01 验证的精确 package name UTF-8 bytes 的 SHA-256。Host builder 必须从 package name 字符串重新计算并与 inspection 中摘要常量时间比较；Runtime 只从 Framework `ApplicationInfo.packageName` 重算，不接受调用方摘要。

manifest MAC 按文件顺序覆盖 HeaderV2（`[104,136)` 置零）、完整 `SPV1`、完整 record table 和完整 chunk table。Payload 由各 chunk 的 GCM tag 认证。任何结构字段在分配 payload 大小内存或解压前都必须通过 checked arithmetic 和 manifest 验证。

ADR 0006 的 ConfigV2 保持 768 bytes，offset `16` 的 `container_major` 固定改为 `2`；完整 config digest 由 HeaderV2 绑定。离线 KEK 的既有 domain `"AHDC offline KEK v1"` 不随容器分块改变，因为其版本描述的是独立的离线 key boundary；ConfigV1 和 AHDC v1 均不得回退接受。

### Runtime 认证顺序与内存上限

1. 有界检查固定 asset、HeaderV2、SPV1、record/chunk table 尺寸和全部算术关系。
2. 恢复 CEK，验证 manifest MAC，再交叉验证 signer、package、config digest、build ID、key slot 和版本。
3. 对每个 chunk 只读取至多 `65536 + 16` bytes，使用标准 AES-GCM 一次性 `doFinal(ciphertext || tag)` 或等价 Native API；失败时不向 zlib 提交任何该 chunk 数据。
4. tag 成功后，把该 chunk 的已认证压缩 bytes 提交给该 record 唯一的连续 zlib inflater。
5. 流结束必须恰好命中 record 的原始长度和 SHA-256，且无 dictionary 请求、拼接流、尾随压缩数据或未消费 chunk。
6. Native 在 `nativeOpenVerifiedPayload` 返回内部 handle 前以单一事务 owner 持有全部已完成 DEX 映射、当前 partial DEX 映射、inflater 和 crypto 临时状态；全部 DEX 成功后只把 completed mappings 转给该内部 handle。对产品调用方的唯一发布边界是 Java `PayloadRuntime.openVerified(...)` 成功返回完整 `LoadedPayload`，不是 Native `long` 返回。

在 Native handle 创建前，首个/中间/末尾 chunk 的认证、I/O、取消、OOM、zlib、长度或摘要失败都必须以不依赖新内存分配的路径清零/unmap 全部 completed/partial mappings 并销毁临时状态，不返回 handle。Native `long` 返回后，Java facade 立即用 primitive local、`committed=false` 和 `finally` 保护该 handle，不依赖 guard 对象分配；`nativeDexBuffers` 数组/元素创建、Native search path、`InMemoryDexClassLoader`、`LoadedPayload` 构造或 return 前任一步失败，都必须在 `finally` 恰好一次调用 allocation-free `nativeClosePayload`，清零/unmap completed mappings，清除未发布的 buffers/loader 引用，不向调用方暴露 `LoadedPayload`/`ByteBuffer`。清理继续 best-effort；错误 suppressed/聚合且不替换首个失败。只有完整 `LoadedPayload` 构造成功后才设置 `committed=true` 并返回，由其接管 handle、mappings 和 loader 生命周期。

crypto 输入、认证后压缩 chunk、zlib scratch 和结构解析的实现总临时缓冲必须不超过 1 MiB；不得按 `compressed_length`、`chunk_count` 或 `payload_size` 分配连续缓冲，也不得把完整 chunk table 物化为对象/数组。Host 按 canonical 公式流式生成表；Runtime 第一遍流式验证 manifest/table，成功后从同一已验证容器按序重读 chunk entry。CEK、KEK、派生 key、AAD 临时数组和认证后压缩缓冲在正常、认证失败、I/O 失败与取消路径均须显式清零。清理失败作为 suppressed failure 保留，不得替换首个安全失败。

## Consequences

积极结果：认证后解压不再依赖 JCA Provider 是否流式释放未认证 plaintext；512 MiB 单 DEX 与 1 MiB 工作缓冲可以同时验证；record/chunk topology、signer、package 和 ConfigV2 共享一条认证链；仍保留每 DEX 连续 zlib 流和原始 DEX 顺序。

代价：每个 64 KiB 压缩块增加 16-byte tag 和 32-byte table entry；Host 与 Runtime 需要维护 chunk ordinal、nonce 和连续 inflater 状态；v1 与 v2 不兼容；格式仍不隐藏 DEX 数量和近似大小，也不阻止受控进程提取内存明文。

## Rejected Alternatives

- 依赖 `Cipher.update` 流式返回 plaintext：最终 tag 失败前的数据未认证，且 Provider 行为不一致。
- 为每个 DEX 缓冲完整 ciphertext/plaintext：违反 1 MiB 上限，并放大恶意长度的内存风险。
- 把单 DEX 上限降到 1 MiB：无授权地缩小 M1-01 已冻结的输入范围。
- 每 chunk 独立 zlib 流：增加压缩开销和 parser 状态，且改变既有的每 DEX 压缩语义。
- 仅提高 AHDC minor：chunk table、record 和 nonce 语义不兼容，必须增加 major。
- 兼容读取 AHDC v1：v1 没有满足冻结安全与内存合同，不应形成降级路径。

## Security Impact

每个已认证 chunk 最多向 inflater 暴露 64 KiB 压缩数据；tag、record 或 chunk table 篡改在该 chunk 解压前失败。chunk 认证不能阻止攻击者在完全控制 Runtime 后截获成功解密的明文 DEX，所有产品表述仍只能声称提高提取成本。

## Compatibility Impact

v0.1 reader 只接受 AHDC major `2`、minor `0`、flags `0` 和 ConfigV2 `container_major=2`。尚未发布的 AHDC v1、ConfigV1 和任何 Provider 特有的流式解密行为不构成兼容合同。

## Verification

- Host/JVM 与四 ABI Native 共享 HeaderV2、RecordV2、ChunkV2、HKDF、nonce、AAD、manifest 和 payload golden vectors。
- 固定 RNG 的 Windows/Ubuntu 输出逐字节一致；每个 chunk 单独用标准 AES-GCM 一次性 API 验证。
- 覆盖 1 byte、65535、65536、65537、多个 chunk 和接近 512 MiB DEX 的流式内存测试，peak 工作缓冲不超过 1 MiB。
- 对 header、SPV1、record、chunk table、nonce prefix、chunk ordinal、ciphertext、tag、config digest、signer 和 package 的单 bit 篡改均在向 inflater 提交受影响 chunk 前失败。
- 覆盖截断、尾随、乱序、重复、空洞、重叠、算术溢出、chunk explosion、错误 zlib wrapper/dictionary/checksum/尾随和声明摘要不符。
- 成功提交测试证明 CEK/KEK/派生 key、AAD、认证后压缩 chunk、inflater scratch 等临时敏感状态立即清零；completed DEX 映射不在提交时清零或 unmap，而是原子转交已发布 handle，保持到 payload ClassLoader 生命周期结束，再由 handle close 安全清零并 unmap。
- 发布前失败测试同时覆盖 Native handle 创建前的首个/中间/末尾 chunk 认证、I/O、取消、OOM、zlib、长度/摘要，以及 handle 返回后的 `nativeDexBuffers` 数组/元素、search path、ClassLoader、`LoadedPayload` 构造/return 前注入点；全部路径不发布 `LoadedPayload`/`ByteBuffer`，适用时 allocation-free Native close 恰好一次，completed/partial mappings 清零/unmap、部分 Java 引用清除，cleanup error suppressed 且不覆盖首个错误。
