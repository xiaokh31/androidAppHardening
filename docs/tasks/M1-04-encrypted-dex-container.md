---
id: M1-04
title: 版本化认证加密 DEX 容器
milestone: M1
status: planned
owner_role: host-pipeline-agent
depends_on:
  - M1-01
  - M1-02
  - M1-07
required_skills:
  - implement-apk-postprocessor
security_sensitive: true
---

## Goal

按 ADR-0008/ADR-0006 实现可由 Host 构建、Runtime 严格解析的 AHDC v2 容器，对每个原始 DEX 形成连续 zlib 流后按 64 KiB chunk 独立认证加密，并绑定 package、当前 signer、record/chunk topology 与 ConfigV2。

## Background

业务 DEX 不再作为普通 APK DEX entry 发布。容器必须同时提供机密性、完整性、顺序与边界认证；离线 Runtime 必然携带恢复材料，因此密钥隐藏只提高逆向成本，不能宣称构成不可提取的信任根。

## Inputs

- M1-01 给出的有序 DEX entry 与 package name。
- M1-02 的 policy version 1 和唯一当前 signer certificate SHA-256。
- OS CSPRNG 及 ADR-0006 规定的每 APK key/share 生成器，不接收用户密钥。
- 只读 APK channel 与 OS CSPRNG。

## Expected Outputs

- `host/container` 模块及 `docs/specs/AHDC_V2.md`。
- `assets/ah/runtime/payload.ahdc` 对应的 AHDC v2 builder、768-byte `ConfigV2` builder、只读 verifier、不可变描述模型和一次性 `KeyPackagingPlanV2`。
- JVM golden vectors、跨语言消费向量和 tamper corpus。
- 明确记录离线密钥边界且不含生产可复用明文密钥的证据。

## In Scope

- 两遍流式压缩/加密，不把 DEX 或压缩明文写入磁盘。
- CEK、每 DEX 子密钥、nonce、`R/R_java/R_native` 与 CEK envelope 的生成。
- little-endian HeaderV2、`SPV1` signer policy block、record/chunk table、认证字段和 chunk ciphertext 的序列化。
- 严格 parser/verifier、版本/flag/长度/offset/顺序检查。
- 成功和失败路径的 key/plaintext buffer 清零与临时密文清理。

## Out of Scope

- Runtime C++ 解密实现；属于 M2-02。
- 将本地恢复材料描述为硬件安全或不可逆。
- 签名、网络 KMS、用户密钥、keystore 或远程授权。
- APK ZIP 注入；属于 M1-05。

## Implementation Decisions

- 文件以 ASCII `AHDC` 开始；major `2`、minor `0`，所有整数为 unsigned little-endian fixed width，flags 固定为 `0`，AHDC v1、未知 major/flags 均拒绝。
- 文件逐字节采用 ADR 0008 的 160-byte `HeaderV2`、128-byte `RecordV2` 和 32-byte `ChunkV2` offset 表；header 写入最终 768-byte `config.bin` 的 SHA-256。实现不得改变宽度、顺序、offset 基准、reserved 零值、名称编码或自行增加 padding。
- signer policy block 紧随 header，并严格按 ADR 0008 沿用的 `SPV1` layout 写入。manifest MAC 覆盖 HeaderV2（MAC 字段置零）、完整 `SPV1`、record table 与 chunk table。
- 每个 DEX 使用一条 zlib-wrapped DEFLATE level `9`、无 dictionary 的连续流；压缩后按规范 65,536-byte chunk 切分，非末块必须满长。第一遍计算原文摘要、压缩大小和 chunk topology，第二遍重新压缩并逐块加密；两遍大小/摘要不一致即 `CONTAINER_INPUT_CHANGED`。
- 每次运行生成随机 32-byte CEK、16-byte build ID、16-byte key slot ID 和每 record 非零 8-byte nonce prefix。record 子密钥为 `HKDF-SHA-256(CEK, buildId, "AHDC record v2" || uint32_le(ordinal))`，chunk nonce 为 `nonce_prefix || uint32_le(chunkOrdinal)`。
- manifest key 为 `HKDF-SHA-256(CEK, buildId, "AHDC manifest v2")`，长度 32 bytes。
- 每 chunk 使用 AES-256-GCM 128-bit tag；verifier 必须以一次性 GCM `doFinal(ciphertext || tag)` 完成认证后才把该块压缩 bytes 交给连续 inflater，禁止消费 Provider 在最终 tag 前返回的 plaintext。
- builder 从精确 package name UTF-8 bytes 重算 `packageNameSha256` 并与 M1-01 模型摘要常量时间比较。每 chunk AAD 严格为 `ASCII("AHDC-GCM-V2") || header[4,8) || build_id || key_slot_id || current_signer_sha256 || package_name_sha256 || RecordV2 || ChunkV2`；parser 完整消费文件并拒绝任何尾随 byte。
- 按 ADR-0006 为每次运行随机生成 32-byte `R` 与 `R_java`，计算 `R_native=R XOR R_java`；`KEK=HKDF-SHA-256(R, buildId, "AHDC offline KEK v1" || signerSha256 || packageNameSha256)`。
- CEK 使用独立随机 12-byte nonce 和 AES-256-GCM 包装；`ConfigV2` 必须逐字节采用 ADR 0006 的 768-byte layout、`container_major=2`，AAD 精确为 config `[0,132)`。构建器从 M1-01 `ApkInspection` 取得规范化原 Factory 与存在标志，严格写入 flag/length/512-byte UTF-8 slot；原 Application 不写入 config。构建器先完成 config 并计算其 SHA-256，再写入 AHDC `HeaderV2.config_sha256`。
- `KeyPackagingPlanV2` 固定持有完整 `ConfigV2`、单个 `R_native`、build/key slot 与目标 ABI 集；只在内存中交给 M1-05。它不自行 patch Runtime template，不可序列化或持久化，任何单一输出位置不得含完整 `R`。
- 分支名固定为 `feat/m1-04-encrypted-dex-container`，Issue 标题固定为 `[M1-04] Encrypted DEX container`，仅允许一个关联 PR。

## Public Interfaces

- `DexContainerBuilder.build(ApkInspection inspection, SignerPolicyV1 signer, Path encryptedTemp): ContainerBuildResult`。
- `DexContainerVerifier.verify(Path container, ExpectedBinding expected): DexContainerDescriptor`。
- `ContainerBuildResult` 包含 `descriptor` 与 `KeyPackagingPlanV2`；后者仅能被 Runtime materializer 消费一次并在使用后销毁。
- `DexContainerDescriptor` 包含版本、package、规范化 `SignerPolicyV1`、DEX 顺序/大小/摘要、container SHA-256，不暴露 key、nonce 之外的恢复材料或明文。
- 错误码：`CONTAINER_FORMAT`、`CONTAINER_VERSION`、`CONTAINER_LIMIT_EXCEEDED`、`CONTAINER_INPUT_CHANGED`、`CONTAINER_CRYPTO`、`CONTAINER_AUTH_FAILED`、`CONTAINER_KEY_MATERIAL`、`CONTAINER_RANDOM_FAILED`。

## Security Constraints

- 只使用 JCA 标准 AES-GCM/HMAC-SHA-256 和经测试的 HKDF 实现；禁止自制 cipher 或可复用 nonce。
- `R`、share、CEK、KEK、子密钥、DEX 与压缩明文不得进入日志、异常、报告或持久临时文件。
- 大小/offset/count 使用 checked arithmetic；DEX count 和大小继承 M1-01 限制，容器总长不得超过 APK v0.1 上限。
- crypto input、认证后压缩 chunk、zlib scratch 与结构解析的总临时 buffer 上限固定为 1 MiB；不得按 record/chunk count/payload 长度分配，也不得物化完整 chunk table。敏感 byte array/direct buffer 在最后一次使用后显式清零，并以测试 hook 验证成功、认证失败、I/O、取消和 cleanup failure 路径；cleanup failure 不得覆盖首个错误。
- Runtime root material 可被逆向恢复，该限制必须写入文档；不得使用“绝对防护”表述。
- 本任务必须经过独立密码学和二进制格式复核。

## Compatibility Requirements

- Windows 与 Ubuntu 在固定 golden-vector RNG 下生成字节相同容器；生产 RNG 下不要求输出字节相同。
- 单/多 DEX 原顺序保持，Runtime 解压结果必须逐字节等于输入 DEX。
- AHDC v2 可由 API 29+ 四 ABI Runtime 的同一规范解析；不得接受 AHDC v1。
- 未知 major、未知 flag、非法 UTF-8、乱序/重复 index 均失败关闭。

## Acceptance Criteria

1. `./gradlew :host:container:test` 退出码为 `0`，NIST AES-GCM、RFC 5869 HKDF 和 zlib vectors 全部通过。
2. 对每个正常 fixture，独立 verifier 解密/解压后的 DEX SHA-256、大小和顺序与 M1-01 模型完全相同。
3. 同一输入连续两次使用生产 RNG 的 container SHA-256 不同，CEK、build ID、key slot ID、`R`、shares 和 nonce 均不同，但 descriptor 语义与恢复的 DEX 相同。
4. 固定 test RNG 的 Windows/Ubuntu golden container SHA-256 相同，Runtime 消费向量字段与 `AHDC_V2.md` 一致。
5. 对 magic、version、flag、count、length、offset、build ID、key slot ID、config digest、`SPV1`、record/chunk table、manifest MAC、package public binding、`ConfigV2`/Factory slot/wrapped CEK/AAD、nonce prefix/ordinal、tag 和 ciphertext 的单 bit 篡改均返回规定错误，且受影响 chunk 在认证前不进入 inflater、不输出任何 DEX。
6. 成功、认证失败、I/O 失败和取消路径均执行敏感 buffer 清零；工作目录没有 DEX 或压缩明文文件。
7. 源码/报告扫描不存在私钥、keystore、用户密钥参数、固定 content key 或安全能力夸大表述。

## Required Tests

- 密码算法标准向量、KDF domain separation 和 nonce uniqueness tests。
- 单/多 DEX round-trip、两遍输入变化，以及 1/65535/65536/65537 bytes 和接近 512 MiB DEX 的流式内存预算测试。
- header/record/chunk/ciphertext、768-byte `ConfigV2` 全字段与 Factory UTF-8/zero-fill tamper matrix。
- truncation、overflow、overlap、unknown version/flag 和 malformed UTF-8 parser tests。
- Windows/Ubuntu deterministic vector 及 Native consumer contract test。
- buffer zeroization、异常清理和无明文落盘测试。

## Required Evidence

- 命令、退出码、OS/JDK/JCA provider/zlib 版本及 peak memory。
- 输入 DEX、golden container、生产随机 container、descriptor 和 tamper corpus 的 SHA-256。
- vector 来源、字段级篡改矩阵、清零 hook 与工作目录扫描结果。
- 独立安全 reviewer 结论、提交 SHA、Issue 与唯一 PR 链接。

## Likely Files

- `host/container/src/main/kotlin/`
- `host/container/src/test/kotlin/`
- `host/container/src/test/resources/vectors/`
- `docs/specs/AHDC_V2.md`
- `docs/evidence/M1-04/`

## Dependencies and Blockers

- M1-01 的 DEX 顺序/限制与 M1-02 signer policy 必须冻结。
- M1-07 必须以独立安全复核通过的 ADR-0008 合并；ADR-0004 已 superseded，不得实现或兼容 AHDC v1。ADR-0006 的 ConfigV2/key boundary 必须保持 Accepted。
- Java 与 Native 无共同可用的标准算法/编码时任务 blocked，不自行替换密码协议。

## Agent Handoff Requirements

- 本任务固定使用分支 `feat/m1-04-encrypted-dex-container`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、平台、vectors/containers SHA-256、peak memory、篡改矩阵和 reviewer 结论。
- worker 不修改根 `HandOff.md`，不实现 Runtime parser、ZIP 注入或签名。
- 任何格式字段或密钥边界变化必须先更新 ADR，不能只在代码中改变。
