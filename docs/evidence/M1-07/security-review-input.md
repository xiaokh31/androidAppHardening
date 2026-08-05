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
6. Native handle 创建前任一失败是否事务清理 completed/partial mappings，且成功提交是否在返回 handle 前清零全部临时秘密而保留 mappings 到生命周期 close？
7. Native handle 到同快照 `AuthenticatedPayloadMetadata`/内部 `LoadedPayload` 的构造窗口，是否由 primitive owner 与 allocation-free `finally` 覆盖 metadata/buffers/loader/return 的异常和 OOM？
8. metadata 是否机械携带并复比较已认证 package/current signer/有序 lineage，且不含恢复秘密、不可跨 handle/session 替换？
9. Guard 从 `LoadedPayload` 到最终 `VerifiedPayloadSession` return 的 identity/config/session 构造窗口，是否 exactly-once close、清除部分引用并保留主错误？
10. M3 catalog 是否区分 Native handle、内部 LoadedPayload/ByteBuffer 与最终 session 发布，覆盖两段窗口的 close-count、mapping、部分引用和 primary/suppressed 断言？
11. metadata 的全部跨模块 Java getter 是否固定名称、类型、长度/nullability/深复制语义；内部 provisional loader 在 Guard 复比较完成前是否保持零 class/resource lookup、零 Factory 调用和零 bootstrap 发布？
