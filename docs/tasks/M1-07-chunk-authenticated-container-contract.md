---
id: M1-07
title: 分块认证 DEX 容器合同修订
milestone: M1
status: planned
owner_role: host-pipeline-agent
depends_on:
  - M1-02
required_skills:
  - plan-apk-hardening-change
security_sensitive: true
---

## Goal

以 ADR 0008 取代尚未发布的 AHDC v1 合同，冻结可在 1 MiB 工作缓冲内支持 512 MiB 单 DEX、且保证每块认证成功后才进入 zlib 的 AHDC v2 wire contract；本任务只修改治理、架构、任务和验证合同，不实现 Host 或 Runtime 代码。

## Background

M1-04 首个冻结实现的独立复核发现：单条 DEX 使用一个 GCM tag 时，固定 SunJCE 可以在 `doFinal` 前保留全部 ciphertext；消费其他 Provider 从 `update` 返回的数据又会在最终 tag 成功前解压。现有 v1 无法同时满足认证顺序、512 MiB 上限和 1 MiB 缓冲，必须先形成独立 ADR 任务，M1-04 才能重启。

## Inputs

- M1-01 的 64 DEX、512 MiB 单 DEX和 2,147,483,647-byte APK 上限。
- M1-02 的 `SignerPolicyV1`/`SPV1`。
- ADR 0004、0006、0007 与 M1-04 首轮独立复核结论。
- JCA AES-GCM `doFinal` 的认证边界和四 ABI Native 消费约束。

## Expected Outputs

- Accepted ADR 0008，明确 HeaderV2、RecordV2、ChunkV2、KDF、nonce、AAD、manifest 和认证顺序。
- ADR 0004 标记为 superseded，ADR 0006/0007 与共享架构改指 AHDC v2。
- M1-04/M2-02/M3-02 等下游任务的可执行验收合同和依赖关系。
- `docs/evidence/M1-07/security-review-input.md` 与独立只读安全复核结论。

## In Scope

- 固定 64 KiB 压缩明文 chunk、每 chunk 独立 AES-256-GCM tag 和连续每 DEX zlib 流。
- 固定 wire offsets、checked limits、domain separation、nonce/AAD、manifest coverage 和无回退规则。
- 同步产品需求、架构、威胁模型、测试策略、路线图、任务索引及根 HandOff。
- 对合同本身进行独立密码学、格式、内存与依赖顺序复核。

## Out of Scope

- Kotlin、Java、JNI、C++、Gradle、fixture、CI 或设备实现。
- 修改或摘取 `feat/m1-04-encrypted-dex-container` 的废止实现。
- AHDC v1 兼容 reader、迁移工具或任何业务发布。
- APK 重打包、签名、Runtime loader 或 M1/M2 相邻实现。

## Implementation Decisions

- 分支固定为 `docs/m1-07-chunk-authenticated-container-contract`，Issue 固定为 `#36 [M1-07] Chunk-authenticated DEX container contract`，仅允许一个关联 PR。
- AHDC v2 严格采用 ADR 0008；64 KiB chunk 是压缩后明文边界，不重置每 DEX 唯一 zlib 流。
- 每个 chunk 必须通过一次性标准 GCM API 完成 tag 验证后才向 inflater 提交；禁止依赖 Provider 从 `update` 返回未认证 plaintext。
- ConfigV2 保持 768 bytes，但 `container_major=2`；AHDC v1/ConfigV1 无兼容回退。
- M1-04 的 `depends_on` 增加 M1-07；在本任务合并且合同独立复核通过前，M1-04 保持 blocked，M2/M3 不得消费废止 v1。
- M2-02 在 Native handle 创建前必须以事务 owner 持有 completed/partial DEX 映射与临时状态；任一失败无分配地清零/unmap 全部未发布映射、不返回 handle，cleanup failure 不覆盖主错误，全部 DEX 成功后才把 completed mappings 转给内部 handle。
- `PayloadRuntime.openVerified` 返回完整 `LoadedPayload` 是 M2-02 到 M2-03 的内部模块交接边界。Native handle 返回后的 authenticated metadata bytes/对象、buffer array/element、search path、ClassLoader、LoadedPayload 构造/return 前窗口由 primitive handle + allocation-free `finally` 覆盖，失败恰好 close 一次、清映射/部分引用且不公开对象。
- M2-02 必须随 `LoadedPayload` 交付同 handle 已认证、无秘密、不可伪造的 `AuthenticatedPayloadMetadata`；M2-03 只从该对象构造安全配置。最终 bootstrap 发布边界是 Guard 返回完整 `VerifiedPayloadSession`，LoadedPayload 到 session return 的 identity/config/session 构造窗口同样 exactly-once close。
- 本任务冻结提交后由独立只读 reviewer 检查 P0/P1/P2；任何 P0/P1 或未处置 P2 均不得推送或创建 PR。

## Public Interfaces

无产品代码接口。规范接口仅为 ADR 0008 的 `HeaderV2`、`RecordV2`、`ChunkV2`、ConfigV2 `container_major=2` 和下游任务验收条款。

## Security Constraints

- 不把降低 DEX 上限、提高缓冲上限或 Provider 特例当作合同修复。
- AAD、manifest、nonce 和 table coverage 必须无歧义；所有 count/length/offset 采用 checked arithmetic。
- 不声称离线客户端密钥不可提取；分块认证只保证正常验证链不消费未认证压缩 bytes。
- 不提交密钥、真实 APK、明文客户 DEX 或复核中生成的敏感样本。

## Compatibility Requirements

- AHDC v2 为 v0.1 唯一容器 major；Windows/Ubuntu Host 和 API 29+ 四 ABI Runtime 必须消费同一逐字节合同。
- 保持原 DEX ordinal、canonical name、每 DEX 连续 zlib level 9/no-dictionary 语义和 ConfigV2 768-byte 外形。
- M1-01、M1-02、M1-03 已合并接口不因本治理任务改变。

## Acceptance Criteria

1. ADR 0008 对每个字段给出唯一 offset/size/rule，且 table/payload 算术、nonce 唯一性、AAD 和 MAC coverage 可机械实现。
2. 合同明确覆盖 512 MiB 单 DEX、64 DEX、2 GiB APK 上限和不超过 1 MiB 的实现工作缓冲，不要求按 record 长度分配。
3. M1-04、M2-02、M2-03、M3-02 及架构/威胁/测试文档统一使用 AHDC v2，并明确每 chunk 认证后才解压、v1 无回退、Native handle 创建前事务清理，以及 Native handle 到内部 `LoadedPayload` 交接、再到 Guard 返回最终 bootstrap `VerifiedPayloadSession` 的两个 exactly-once close 与无暴露窗口。
4. 任务索引和路线图无依赖环：M1-07 依赖 M1-02，M1-04 依赖 M1-01、M1-02、M1-07。
5. `node tools/governance/validate-project-package.mjs` 与 strict HandOff 校验退出 `0`。
6. 独立只读复核对冻结提交给出 P0=0、P1=0、P2=0，且结论、提交 SHA 和检查范围归档。

## Required Tests

- 文档链接、任务 ID/Issue/branch/依赖图和 UTF-8 治理校验。
- 手工/脚本复算 HeaderV2 160 bytes、RecordV2 128 bytes、ChunkV2 32 bytes 和 ConfigV2 768 bytes。
- 边界推演：1/65535/65536/65537 bytes、最大 DEX、最大 APK、chunk/count/offset 溢出与尾随数据。
- Native handle 创建前的首个/中间/末尾 chunk 认证、I/O、取消、OOM、zlib/摘要和 cleanup failure 推演，证明未发布 DEX 映射全部清零/unmap、不返回 handle 且主错误保留。
- 成功提交推演：handle 返回后、close 前所有 key/AAD/compressed/inflater/crypto 临时状态已清零且不可达，只有 completed DEX mappings 转交 handle 并保持可用；生命周期 close 才清零/unmap 映射。
- 跨 JNI 内部交接窗口推演：Native handle 返回后在 authenticated metadata bytes/对象、buffers array/element、search path、ClassLoader、LoadedPayload 构造/return 前注入异常/OOM，证明内部交接对象未返回、Native close 恰好一次、mappings/部分引用清理且主错误保留。
- 同 handle authenticated metadata 的来源/不可伪造/防御性复制/无秘密推演，以及 Guard 取得 LoadedPayload 后 identity/config/session/return 前异常/OOM 的无 session 发布、close-count=1、映射/部分引用清理和主错误优先推演。
- 独立 reviewer 检查 JCA Provider 语义、认证顺序、domain separation、nonce 重用和 cleanup/error precedence。

## Required Evidence

- 分支/base/Issue、命令、退出码、OS、Node/Git 版本和时间戳。
- 文档 diff、字段尺寸复算、依赖图、治理与 strict HandOff 输出。
- `docs/evidence/M1-07/security-review-input.md`、冻结提交 SHA 和独立复核报告。

## Likely Files

- `docs/adr/0004-versioned-encrypted-dex-container.md`
- `docs/adr/0008-chunk-authenticated-dex-container.md`
- `docs/tasks/M1-07-chunk-authenticated-container-contract.md`
- `docs/tasks/M1-04-encrypted-dex-container.md`
- `docs/ARCHITECTURE.md`
- `docs/THREAT_MODEL.md`
- `docs/TEST_STRATEGY.md`
- `docs/ROADMAP.md`
- `docs/PROJECT_PLAN.md`
- `docs/tasks/INDEX.md`
- `docs/evidence/M1-07/`
- `HandOff.md`

## Dependencies and Blockers

M1-02 必须已合并。若独立 reviewer 证明该 chunk 合同仍无法在固定 Java/Native API、1 MiB 上限或既有输入限制内实现，本任务 blocked 并再次修订 ADR；不得恢复废止 v1 或自行放宽边界。

## Agent Handoff Requirements

只处理 Issue #36 和 `docs/m1-07-chunk-authenticated-container-contract`，不修改产品代码。交接必须包含字段复算、依赖关系、治理/strict 输出、冻结 SHA 和独立复核结论；用户另行授权前不得推送或创建 PR。
