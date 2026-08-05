# M1-07 security review input

## Trigger

M1-04 首个实现候选 `97cb9dc75f68b5ce0ddde2134e09c15ae2e798fb` 的独立只读复核给出 P0=0、P1=3、P2=2。该提交只保留在本地废止分支 `feat/m1-04-encrypted-dex-container`，不属于本治理分支，也不得发布。

决定合同修订的 P1 是：AHDC v1 每个 DEX 只有一个 GCM tag，而 M1-01 允许 512 MiB DEX；固定 SunJCE 可以在 tag 验证前缓存全部 ciphertext。若改用 Provider 的流式 `update` plaintext，又会在最终 tag 成功前向 zlib 暴露未认证数据。因此 v1 无法同时兑现认证后解压和 1 MiB 工作缓冲。

其余复核输入要求新合同/后续实现同时关闭：

- AAD 与认证后压缩缓冲必须在全部退出路径清零；
- 必须具备接近最大 DEX、Provider 行为、内存峰值和无明文落盘测试；
- cleanup failure 不得覆盖首个认证/I/O 失败；
- Host 必须从精确 package name UTF-8 bytes 重算摘要并交叉核对，不能只信任同一模型中的摘要字段。

## Proposed invariant set

- AHDC v2：64 KiB canonical compressed-plaintext chunks，每 chunk 独立 AES-256-GCM tag。
- 一次性 GCM API 返回成功后，才把该 chunk 提交给每 DEX 唯一连续 zlib inflater。
- HeaderV2/RecordV2/ChunkV2 和完整 ConfigV2 进入一条 signer/package/build/key-slot 认证链。
- 单 chunk crypto 输入最大 65,552 bytes，实现总工作缓冲不超过 1 MiB。
- AHDC v1 和 ConfigV1 无回退路径。

## Independent review questions

1. nonce `random_prefix[8] || chunk_ordinal_u32le` 在每 record 独立 key 下是否排除同 key 重用？
2. manifest MAC 加每 chunk AAD/tag 是否完整认证了 table topology、payload、signer、package 和 ConfigV2 binding？
3. 64 KiB chunk 是否足以把固定 JCA/Native API 的认证前缓冲限制在 1 MiB 内，并阻止未认证数据进入 zlib？
4. 最大 count/length/offset、canonical chunk size 和完整消费规则是否排除 overflow、overlap、hole、chunk explosion 与 trailing bytes？
5. 任务依赖是否确保 M1-04/M2/M3 不会实现或接受废止 v1？
