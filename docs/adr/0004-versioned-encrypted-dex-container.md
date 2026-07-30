# ADR 0004: 版本化认证加密 DEX 容器

## Status

Accepted

## Context

Host 与 Android Runtime 需要一个稳定、可演进且能安全解析的二进制合同来承载单个或多个业务 DEX。简单拼接无法可靠表达长度、顺序和版本；只加密不认证会允许攻击者修改密文或元数据；使用 ZIP 内多个自定义密文条目会分散版本与完整性边界。

容器 parser 同时运行在 Host 验证器和 Android Native Runtime 中，所有字段都必须能进行有界、溢出安全的解析。

## Decision

定义 Android Hardening DEX Container，扩展名 `.ahdc`，当前格式为 major `1`、minor `0`，默认 APK 路径为：

```text
assets/ah/runtime/payload.ahdc
```

### 序列化

- magic 为 4 字节 ASCII `AHDC`。
- 所有整数使用 little-endian 固定宽度无符号表示。
- header 包含 major、minor、header size、flags、DEX count、record table size、payload size、16 字节随机 build ID、16 字节 key slot ID 和 32 字节 manifest MAC。
- major `1` 的 flags 必须为 `0`；`flags=0` 隐含本 ADR 规定的固定压缩语义，不表示“未压缩”。未知 major 或未知 flags 失败关闭。
- record 按原 DEX ordinal 严格递增，名称只允许规范形式 `classes.dex`、`classes2.dex` 等。
- 每条 record 包含 ordinal、名称、明文长度、密文长度、payload offset、12 字节 nonce 和明文 SHA-256。其中“明文长度”和“明文 SHA-256”始终指压缩前的原始 DEX；“密文长度”指压缩后字节经 AES-GCM 加密所得 ciphertext 的长度，不包含其后的 16 字节 tag。AES-GCM 不改变输入字节长度，因此该值也等于压缩后字节长度。
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

manifest MAC 使用 HMAC-SHA-256，覆盖固定 header 与完整 record table，计算时 manifest MAC 字段置零。每条 DEX 先按固定 zlib 语义压缩，再使用 `K_dex_i` 和 AES-256-GCM 加密压缩后字节；AAD 为 magic、major、minor、build ID、key slot ID 与该条规范 record。

Runtime 的验证顺序固定为：

1. 在不分配 payload 大小内存的情况下检查 magic、版本、flags 和全局边界。
2. 恢复 CEK。
3. 验证 manifest MAC。
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
- 修改 header、record、nonce、ciphertext 或 tag 时在解压和业务 DEX 加载前失败。
- 同一 CEK 下 nonce 唯一性由构建器断言，跨大量样本执行统计检查。
- parser 接受覆盖引导模糊测试和 Native sanitizer。
- 输出扫描确认不存在原 DEX 明文条目或额外 DEX magic。
