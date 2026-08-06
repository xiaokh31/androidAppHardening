---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260805-130636
updated_at: 2026-08-07T07:28:12+08:00
updated_by: /root
state: active
source_branch: chore/m2-07-native-crypto-backend
base_commit: 1bce1f61a3edcebdf94a511c495006a38edb6cb4
working_tree: clean
current_milestone: M2
active_task: M2-07
next_owner: /root
---

# Project HandOff

## Objective

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的边界内执行 M2-07：固定并验证 M2-02 唯一可用的 Native 密码后端、供应链和最小 AES-256-GCM/HKDF-SHA-256 facade；不实现 AHDC parser、DEX loader、TLS/X.509 或相邻 M2 功能。

## Current State

- 用户已授权独立 M2-07 ADR/任务合同与供应链修订，并要求合并后恢复 M2-02。Issue #41 和分支 `chore/m2-07-native-crypto-backend` 已从 `main@1bce1f6` 建立；M2-02 本地分支 `feat/m2-02-native-decrypt-loader@40e3900` 保持暂停且未发布。候选已从受 2026-07 官方安全公告影响的 Mbed TLS 4.1.0 提升为 4.1.1 LTS；官方完整归档已下载到忽略的仓库根 `.toolchains/native-crypto/` 并命中 `7099934` bytes/SHA-256 `3359a349...5c98c`，未向 C 盘下载大体积程序。首个 CI 候选 `dcd27f1e141222d7af81f4289034dc1c1a5c5310` 已通过本地 296-task 根回归和四 ABI ELF/符号检查，固定分支已推送并创建关联关闭 Issue #41 的唯一草稿 PR [#42](https://github.com/xiaokh31/androidAppHardening/pull/42)。GitHub 延迟补发的旧 HEAD `f2ebbbef7784ea9aea3100ad2242160bb5da2454` 已证明 Ubuntu Host NIST/RFC 向量、依赖负例、完整回归和四 ABI 全部 PASS；Governance 仅因 HandOff dirty 声明失败，Windows Build 仅因 7-Zip 拒绝未启用 PQC examples 的 147 个符号链接失败，API 36 KVM 仅因未准备 fail-closed 密码源失败。当前候选已声明 clean、把 Windows 解包切到固定 CMake 4.1.2，并在 KVM 构建前校验同一锁定归档；等待 replacement CI。PR 保持 draft，M2-02 不恢复。
- 用户已明确启动 M1-06，并预先授权任务所需的推送、唯一草稿 PR、ready 与 expected-head 普通合并。Issue [#11](https://github.com/xiaokh31/androidAppHardening/issues/11) 为 OPEN、无 assignee；远程不存在固定分支或关联 PR。分支 `feat/m1-06-cli-and-json-report` 从已完成 M1-05 且 final main 双平台 CI 全绿的 `main@55ef3c57e631cde65d3e04d58aa75d26a7e75ba8` 创建。
- M1-01 至 M1-05 依赖均已完成。M1-06 选择 `full-flow` 验证模式；实现计划归档于 `docs/evidence/M1-06/implementation-plan.md`。生产入口仅从后续 distribution 提供的固定 classpath RuntimeBundle 读取资源，不把 synthetic Runtime 打入产品；本任务集成测试使用 M1-05 已授权的合成 RuntimeBundle 合同 fixture。当前只修改 `host/cli`、REPORT_V1、M1-06 证据/CI 与根交接，不启动 M2、设备或模拟器。
- M1-06 Host 实现与 Windows full-flow 已通过：唯一 `protect` CLI、REPORT_V1 及 Draft 2020-12 可执行 schema validator、七阶段状态机、M1-01 一基至 AHDC v2 零基 ordinal 边界适配、输入只读、未签名输出、报告原子 no-replace 发布/回滚、线程中断与 JVM shutdown 清理、路径/能力扫描均已闭环。Windows 离线 clean 根 `check verifyGovernance` 共 273 项任务退出 `0`；规范化成功报告、错误矩阵、清理矩阵和路径矩阵已固定到 Ubuntu/Windows CI，当前等待冻结提交、只读复核、唯一草稿 PR 和远端双平台门禁。
- 首个冻结提交 `7d9072e` 的协调者只读复核发现两个关停边界缺口并立即废止：报告临时文件/hard-link 已发布窗口未被 shutdown hook 精确拥有，以及首 stage 前未知异常会错误归到 `publish`。当前修复精确追踪本次 report temp/target、失败时只删除 owned target、失败清理不成功则保留退出 hook 重试，并按实际 active stage 映射未知异常；新增 pre-stage、report-temp 和 report-target shutdown 回归。修复后 targeted full-flow 与最终 273-task clean 根回归再次退出 `0`，等待新冻结点完整复核。
- 新冻结提交 `e882691c1dbc4958c111c7e33580c3921eff2fc8` 的完整协调者只读复核为 PASS：P0/P1/P2 均为 `0`。生产 JAR 无 synthetic Runtime/DEX/SO/key/cert，生产源码无签名执行、网络或环境采集能力，Git 无 build 产物，首个冻结点两项发现和全部任务验收已关闭；结论归档于 `docs/evidence/M1-06/read-only-review.md`。当前允许推送固定分支并创建关闭 Issue #11 的唯一草稿 PR。
- 固定分支已推送，关联关闭 Issue #11 的唯一草稿 PR 为 [#40](https://github.com/xiaokh31/androidAppHardening/pull/40)。初始 HEAD `5a3981ced2c1f889ece284684b9167c34bae5f99` 的 Build run `31115781825` 与 Governance run `31115781121` 在 Ubuntu 24.04/Windows 2025 四项全部 PASS；两个 Build job 的 M1-06 full-flow 与固定 normalized/error/cleanup/path 哈希均命中。当前只允许提交 merger-ready 证据并等待该 exact HEAD 的替换 CI，不启动 M2。
- merger-ready HEAD `702995748cfd643feb9d75ef0abee9cbced1cb4c` 的 Build run `31116416406` 与 Governance run `31116415535` 在 Ubuntu/Windows 四项全部 PASS；两个 Build job 再次通过 M1-06 full-flow 和四份固定哈希。PR #40 已按预授权转为 ready，并以 expected-head 保护的普通 merge commit `d0eb39264f1382469712a4f3c28a7d42ab19d1dd` 合并到 `main`，Issue #11 已关闭。本地 main 已快进同步；当前只允许 post-merge README/HandOff、无豁免 strict/Governance 和最终 main 双平台门禁，不启动 M2。
- 用户已明确启动 M1-05，并预先授权任务所需的推送、唯一草稿 PR、ready 与 expected-head 普通合并。Issue [#10](https://github.com/xiaokh31/androidAppHardening/issues/10) 为 OPEN、无 assignee，远程无固定分支或关联 PR；分支 `feat/m1-05-apk-repacker-and-alignment` 从已验证 `main@d32abe1d68d41910d72c90c3f9fc3d2831972756` 创建。
- M1-02/M1-03/M1-04 依赖均已合并并完成 post-merge 门禁。M1-05 使用 `pre-cli` 内部 assembler/repacker harness，不启动本机模拟器或真机；生产 Runtime binaries 在 M3 集成前可由任务卡明确允许的合成 RuntimeBundle 合同 fixture 代替。
- 实现与验收边界归档于 `docs/evidence/M1-05/implementation-plan.md`。ADR 0005/0006/0007/0008 已固定 ABI、NativeShareSlotV1、sourceDir 资产和 AHDC v2 合同，无需新 ADR；独立复核者固定为 `m1_05_security_review`，仅在 clean 冻结提交后启动。
- `host/repacker` 已完成 raw ZIP 白名单重建、精确签名材料删除、四 ABI Runtime materialization、4 KiB/16 KiB 对齐、AHDC v2 重新认证、独立候选重读和 Windows/Linux native atomic no-replace 发布。Windows clean 268-task 根回归、Governance、固定 Android 工具、28 项失败矩阵与六项敏感清理矩阵均 PASS；证据归档于 `docs/evidence/M1-05/local-windows.md` 与 `security-scan.md`。
- 首轮独立只读 `m1_05_security_review` 对冻结提交 `bb748f68ec3cfac255124c6bdfd0bbb242bed1c1` 判定 FAIL：`P0=0`、`P1=4`、`P2=1`。plan cleanup/发布顺序、敏感数组事务所有权、文件身份 TOCTOU、缺失的定向 verifier mutation 矩阵和异常 entry 名脱敏均须修复；旧冻结点已废止，完整结论归档于 `docs/evidence/M1-05/security-review-1.md`。
- 修复提交 `c1c1f3006bc57754ba7637653d9c5b1bb1838e93` 已关闭首轮发现；其后冻结 `55b951269201f37aada6945b13c0716531616b92` 的第二轮独立复核仍为 FAIL：`P0=0`、`P1=3`、`P2=1`，结论归档于 `docs/evidence/M1-05/security-review-2.md`，该冻结点已废止。
- 第二轮修复提交 `f99c7d05f2a70aa9b076a2d1baadfce5a931f036` 已关闭所有既有代码发现。第三轮独立复核对 clean 冻结 `1febc2da91d62ba3163cdab022955c51be88759a` 判定 FAIL：`P0=0`、`P1=1`、`P2=0`；唯一 P1 是直接分发的 JNA 5.6.0 缺少维护状态与已知漏洞核对，结论归档于 `docs/evidence/M1-05/security-review-3.md`，该冻结点已废止。
- 依赖修复提交 `af2d850f54eb6555d8880449d99750491ee7f0eb` 选择官方最新 tag `5.19.1`/commit `1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d`，GitHub 官方 Maven Advisory API 对 `jna`/`jna-platform` 均返回零公告；第三轮引用的 `CVE-2021-44549` 经 GitHub/NVD 核实属于 Apache Sling Mail 而非 JNA。catalog、受影响锁、verification metadata、provenance、notice 与点时安全审查已同步；5.19.1 Windows 生产 JNA 模块门禁和 268-task clean 根回归均 PASS，仍须协调冻结和第四轮独立复核。
- 第四轮独立只读复核对 clean 冻结 `5b8163f7c1db15951e4eaf55399cc8e54f4224af` 给出 PASS：`P0=0`、`P1=0`、`P2=0`。JNA 5.19.1 分发/锁/SHA/公告证据、旧 5.6 build-only 边界和前三轮全部发现均关闭；结论归档于 `docs/evidence/M1-05/security-review-4.md`。当前允许归档、推送固定分支并创建关联关闭 Issue #10 的唯一草稿 PR。
- 固定分支已推送，唯一草稿 PR [#39](https://github.com/xiaokh31/androidAppHardening/pull/39) 正确关联关闭 Issue #10；初始 PR HEAD `b3758f8d7beb3f9ce10dd8c6042e52e47137e981` 的 Build run `31069545834` 与 Governance run `31069545814` 在 Ubuntu 24.04/Windows 2025 四项全绿。Ubuntu Build 已实际覆盖 Linux native no-replace、M1-05 字节一致性及四 ABI 门禁；当前只允许提交本证据并等待 exact merger-ready HEAD CI。
- merger-ready HEAD `a239a7ca1c99ed4bf7206f86174f8ca9fa6a17ae` 的 Build run `31069868900` 与 Governance run `31069868904` 四项全绿；PR #39 已按预授权转为 ready，并以 expected-head 保护的普通 merge commit `78b44f2d3c94514d8aeb3f851b60318e06eb7391` 合并到 `main`，Issue #10 已关闭。本地 main 已同步；合并提交首次 Governance run `31069977032` 仅因 HandOff 仍声明任务分支而失败，当前 post-merge 协调候选修复该状态并等待最终 main 门禁。
- post-merge 协调提交 `5756f9c5dd4f4139396bf17db1a74c6e5331c555` 已在本地 main 无豁免通过 Governance、strict HandOff 与 diff check；Build run `31070137444` 和 Governance run `31070137438` 的 Ubuntu 24.04/Windows 2025 四项全部 PASS，M1-05 字节一致性、Linux native no-replace、依赖验证负例与四 ABI 再次通过。README/HandOff 已同步完成状态，M1-05 结束；M1-06/M2 未启动。
- PR [#38](https://github.com/xiaokh31/androidAppHardening/pull/38) 已按用户授权转为 ready，并以 expected-head 保护的普通 merge commit `f908861cbb61e79e7c3127fd5216d4a6f8c6e3e1` 合并到 `main`；唯一 tracking Issue [#9](https://github.com/xiaokh31/androidAppHardening/issues/9) 已关闭。
- 旧本地同名分支停在失败复核提交 `ca3d14147b88991c45d539e90b1f42dc95116860`，已无损重命名为 `spike/m1-04-rejected-ahdc-v1`。新任务分支从最新 main 创建，不 merge/cherry-pick/复用废止 AHDC v1 实现。
- M1-04 采用 `pre-cli` 验证模式，只实现 `host:container` 的 AHDC v2 builder/verifier、768-byte ConfigV2、不可变 descriptor、一次性 `KeyPackagingPlanV2`、规范向量与失败关闭测试；本轮不启动设备或模拟器。
- 实现计划归档于 `docs/evidence/M1-04/implementation-plan.md`。固定合同来自 ADR 0008/0006；任何 wire、密钥边界或公开接口变化必须先停下并修订 ADR，不得在代码中漂移。
- M1-04 当前冻结实现为 `58352c6de732887cf497de2775bc0fa3021f5332`：包含两遍连续 zlib、64 KiB 分块 AES-256-GCM、HeaderV2/RecordV2/ChunkV2、SPV1 manifest MAC、768-byte ConfigV2、一次性 `KeyPackagingPlanV2`、只读 verifier、严格拓扑/尾随拒绝，以及 OOM/callback/构造/比较失败下的事务清理；未实现 Runtime、APK 注入、签名或 CLI。
- Windows `:host:container:check` 退出 `0`；13 组自测覆盖 RFC 5869、NIST AES-256-GCM、zlib、完整 512 MiB 流式输入、1/65535/65536/65537 边界、单/多 DEX、生产随机差异、篡改矩阵、ConfigV2、两遍输入变化、I/O/原子移动/随机/OOM/callback/取消和一次性消费。固定容器 SHA-256 为 `3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d`，跟踪峰值 live buffer 为 `262431` bytes。
- Ubuntu/Windows Build workflow 已增加同一固定容器哈希门禁。仓库级本地 `check` 在配置阶段因既有 `fixtures:android` 未声明固定 NDK 29、且仓库 SDK 不含 AGP 默认 NDK 28.2 而停止；未下载未固定工具或混入相邻 fixture 修复。模块门禁与 Governance 均通过。
- 第五轮独立只读复核对冻结实现 `58352c6de732887cf497de2775bc0fa3021f5332` 给出 PASS：P0 `0`、P1 `0`、P2 `0`；前四轮发现全部关闭，Node 消费者和模块 13 项门禁独立通过，复核结论归档于 `docs/evidence/M1-04/security-review-5.md`。
- merger-ready HEAD `65ae18e62f80fe856a3f23c1663d51193c9d2061` 的 KVM run `31060409306`、Build run `31060409389` 与 Governance run `31060409341` 六项全部 PASS。post-merge `main@9f074db7222fc76442aa8fa7d44ea29091d7bdfa` 的 Build run `31061052744` 与 Governance run `31061052957` 四项全部 PASS，本地 strict HandOff 无豁免通过；README 已把 M1-04 标记为“已完成”。
- 用户已明确授权启动独立 ADR/任务合同修订。唯一 tracking Issue 为 [#36](https://github.com/xiaokh31/androidAppHardening/issues/36)，固定分支为 `docs/m1-07-chunk-authenticated-container-contract`，base 为 clean `main@225ec169661e2a366736be36b1249fb79faf3dcc`；未授权推送或创建 PR。
- M1-04 首个实现候选 `97cb9dc75f68b5ce0ddde2134e09c15ae2e798fb` 的独立复核为 FAIL（P0 `0`、P1 `3`、P2 `2`）；该提交仅保留在本地废止分支，不属于 M1-07，也不得发布。
- 决定性 P1 是每 DEX 单 GCM tag 在固定 SunJCE 下可能缓存至多 512 MiB ciphertext；使用其他 Provider 的 `update` plaintext 又会在 tag 成功前解压，无法同时兑现认证顺序与 1 MiB 缓冲。
- 当前治理 diff 新增 ADR 0008、M1-07 任务卡与复核输入，定义 AHDC v2 HeaderV2/RecordV2/ChunkV2、64 KiB canonical chunk、每 chunk 一次性 GCM、连续每 DEX zlib 流和 v1 无回退；M1-04 新增对 M1-07 的硬依赖。
- 首轮独立只读复核对冻结提交 `e13927a22f8b008ab6bc419b26b53044a847ef4a` 给出 FAIL：P0 `0`、P1 `1`、P2 `1`。P1 是架构/M2-02 残留 record/DEX-level tag 措辞及启动序列漏写 chunk table；P2 是依赖无环证明把边方向写反。旧冻结点立即失效，结论归档于 `docs/evidence/M1-07/security-review-1.md`。
- 当前修正候选统一规定不存在 record-level tag、manifest 覆盖 HeaderV2/`SPV1`/record/chunk table、每 chunk 一次性 GCM 成功后才进入所属 record 的连续 inflater，并纠正无环证明；必须重新校验、冻结并取得新的独立全零复核。
- 第二轮独立只读全量复核对 `3380659355981738998d32a3b0f1dabb70a2067d` 给出 FAIL：P0 `0`、P1 `2`、P2 `0`。一项是 M2-02 Goal 仍残留“已认证 record”措辞；另一项是 handle 发布前的 completed/partial DEX 映射没有事务 owner 和失败清理验收。旧冻结点立即失效，结论归档于 `docs/evidence/M1-07/security-review-2.md`。
- 当前第二轮修正候选把 per-chunk/no-whole-record 语义写入 M2-02 Goal，并在 ADR/架构/M2-02/威胁/测试中固定未发布事务所有权、全映射清零/unmap、原子提交、无 handle/`ByteBuffer` 暴露和主错误优先；必须再次冻结并全量复核。
- 第三轮独立只读全量复核对 `e35543804905df0045d22c1d6a06e903384afd93` 给出 FAIL：P0 `0`、P1 `1`、P2 `0`。ADR 验证条款误把成功路径的已提交 DEX 映射也要求清零/unmap，与 handle 原子接管和 ClassLoader 生命周期冲突。旧冻结点立即失效，结论归档于 `docs/evidence/M1-07/security-review-3.md`。
- 当前第三轮修正候选把成功提交与发布前失败分开：成功只清临时敏感状态、映射转交 handle 并在生命周期结束清理；发布前失败才全量清零/unmap completed/partial 映射且不暴露 handle。
- 第四轮独立只读全量复核对 `dd0c4c0811557be09ce2ac2b11afde5d7794b337` 给出 FAIL：P0 `0`、P1 `2`、P2 `1`。P1 要求成功返回前即清理所有临时秘密并补正向 hook，以及让 M3-02 实际证明失败事务映射清理；P2 指出 review-2/3 手工时间顺序不可信。旧冻结点失效，结论归档于 `docs/evidence/M1-07/security-review-4.md`。
- 当前第四轮修正候选把成功提交清理同步到架构/M2-02/M1-07/测试，把 M3-02 catalog 扩展为 handle/ByteBuffer/mapping/primary/suppressed 断言，并以可核验的归档提交时间替换无法恢复的 review-2 完成时间声明。
- 第五轮独立只读全量复核对 `340b6ae83f05d89fb20d2d2d7d32ad1b55d65404` 给出 FAIL：P0 `0`、P1 `1`、P2 `0`。Native long 已返回但公开 `LoadedPayload` 尚未构造完成的跨 JNI 窗口缺少 owner/finally/注入验收。旧冻结点失效，结论归档于 `docs/evidence/M1-07/security-review-5.md`。
- 第五轮修正候选曾把 M2-02 内部交接边界提升为 `PayloadRuntime.openVerified` 返回完整对象，用 primitive handle + allocation-free finally 覆盖 buffers/search path/ClassLoader/LoadedPayload 构造窗口，并扩展 M3-02 内部 handle、交接对象、close-count 和部分引用清理字段；第六轮已进一步确认最终 bootstrap 发布边界必须是 Guard 返回完整 session。
- 第六轮独立只读全量复核对 `bb2e744fce0f64c7f0effd59c99f5bb2882b834c` 给出 FAIL：P0 `0`、P1 `2`、P2 `0`。M2-02 缺少已认证 ConfigV2/`SPV1` 元数据交给 M2-03 的可实现接口，且 LoadedPayload 到最终 VerifiedPayloadSession return 仍有 Guard 所有权窗口。旧冻结点失效，结论归档于 `docs/evidence/M1-07/security-review-6.md`。
- 当前第六轮修正候选新增同 handle、不可变、无秘密 `AuthenticatedPayloadMetadata`，禁止 M2-03 使用未认证预读；并把 committed/finally exactly-once close 延伸到 Guard 返回完整 session，M3-02 增加 session 发布和 Guard 部分引用清理字段。
- 第七轮独立只读全量复核对 `3bea66ad1aa89a6cbc97ba093b71235561481d38` 给出 FAIL：P0 `0`、P1 `1`、P2 `2`。权威架构时序仍在同 handle metadata 前暴露配置/创建 loader，共享测试策略、M2-02 和 M1-07 验收摘要未完整同步 metadata 注入与双窗口。旧冻结点失效，结论归档于 `docs/evidence/M1-07/security-review-7.md`。
- 当前第七轮修正候选统一为完整 DEX 验证后创建 handle/metadata/LoadedPayload，再由 Guard 原子构造 session 后暴露配置；共享验收明确 metadata bytes/object 注入和两段 exactly-once owner 窗口。
- 第八轮独立只读全量复核对 `01f76f6c7dfa3a0fad999016c54351329bc56e29` 给出 FAIL：P0 `0`、P1 `1`、P2 `1`。Native package 密码学绑定成立，但固定 authenticated metadata 缺少 package digest，导致下游复比较不可机械实现；必需复核输入也未同步后续所有权门禁。旧冻结点失效，结论归档于 `docs/evidence/M1-07/security-review-8.md`。
- 当前第八轮修正候选把成功 binding 的 32-byte `package_name_sha256` 加入同 handle metadata，固定 package/current signer 常量时间复比较和有序 lineage 等值比较，并扩展必需独立复核清单覆盖事务、临时秘密、双窗口与 M3 证据。
- 第九轮独立只读全量复核对 `d5d5d292600953eb21c4422dee8038288bb19d6a` 给出 FAIL：P0 `0`、P1 `1`、P2 `1`。ADR 要求 metadata 复比较先于 loader 构造，但固定接口只能经已含 loader 的 LoadedPayload 交付 metadata；其余跨模块 getter 也未冻结精确签名。旧冻结点失效，结论归档于 `docs/evidence/M1-07/security-review-9.md`。
- 当前第九轮修正候选明确 Native binding 是密码学门禁，metadata 先于内部 provisional loader 构造；Guard 复比较和完整 session return 前 class/resource lookup、Factory 与 bootstrap 发布均为零，并冻结全部十个 metadata getter 的类型、范围、长度、nullability 和深复制语义。
- 第十轮独立只读全量复核对 `358a71a9478a0ccb76f71538002184a6a4ea4dc4` 给出 FAIL：P0 `0`、P1 `0`、P2 `1`。Guard 失配矩阵对 build/key/version/Factory 未逐字段冻结真实比较源，Factory 无第二可信来源。旧冻结点失效，结论归档于 `docs/evidence/M1-07/security-review-10.md`。
- 当前第十轮修正候选固定来源表：package/signer/lineage 对 Framework/apksig，build/key 预读仅检测快照变化，versions 对 `2.0/1/1`，Factory 仅消费 Native 认证值；Native tamper 与 M2-02 parser 测试分别承担 Factory/config 和内部编码错误。
- 第十一次独立只读全量复核对 `9dec7603a860c33ab6bb91f37221e2e81d6011bf` 给出 PASS：P0 `0`、P1 `0`、P2 `0`。wire/算术/密码链、事务与成功清理、两段所有权、十个 getter、真实比较来源、零 lookup/Factory/bootstrap 发布、M3 证据和依赖图全部闭环，结论归档于 `docs/evidence/M1-07/security-review-11.md`。
- 用户已授权继续执行 M1-07；根 README 已同步真实进度，固定分支已推送并创建关联 Issue #36 的唯一草稿 PR #37。M1-04 在 M1-07 合并前仍保持 blocked。
- PR #37 的最终草稿 HEAD `2c13ecc8521f269e6f02fdace77f7f14f546c9cc` 已通过 Ubuntu/Windows Build 与 Governance 四项 CI；PR 保持 OPEN、draft、CLEAN、MERGEABLE，并正确关联关闭 Issue #36。
- merger-ready HEAD `d0e0ee61171eb9472bce306619a426da86292f5c` 的 Build run `31025197065` 与 Governance run `31025197119` 在 Ubuntu/Windows 四项全部 PASS。PR #37 已于 `2026-08-06T00:28:42+08:00` 转为 ready，并以 expected-head 保护的普通 merge commit `9ec90fca6b2b293a56a98f3d0c60190b5c0e7a20` 合并到 `main`；Issue #36 已关闭。
- 本地 `main@9ec90fca6b2b293a56a98f3d0c60190b5c0e7a20` 已无豁免通过 strict HandOff、Governance 与 diff check；根 README 已同步 M1-07 完成和 M1-04 为下一任务。M1-07 完成，当前未启动 M1-04/M2/M3。
- 用户新增持续规则：每个任务只有在 PR 合并及合并后门禁完成后才算完成，并必须在收尾协调提交中同步根 `README.md` 的公开进度表；`HandOff.md` 继续作为详细证据源。
- M0-04 的 PR #29 已合并，正式 API 29/36 x86_64 设备矩阵和独立安全复核通过。
- M0-06 的 PR #31 已合并为 `main@f1362188be5083a6d557522f0f5be1905935f6eb`；合并后的 Governance/Build 在 Ubuntu 与 Windows 通过，`main` 已无豁免通过 strict HandOff。
- M0-06/ADR 0007 已解除旧的 `ApplicationInfo.metaData == null` 阻塞，启动配置唯一来源改为 `ApplicationInfo.sourceDir` 中的固定 ConfigV2 与 AHDC 条目。
- M1-02 的 PR #34 已以 merge commit `d590b94f08047352d2b1f56c1c08aba4cbf079ec` 合并；post-merge `main@077e4be14865c777dbbf3c1a5a3d9609b3620868` 已通过 Ubuntu/Windows Build、Governance、M1-02 字节门禁和无豁免 strict HandOff。
- 用户已明确启动 M1-03；唯一 tracking Issue 为 [#8](https://github.com/xiaokh31/androidAppHardening/issues/8)，固定分支为 `feat/m1-03-binary-axml-transformer`，当前唯一关联 PR 为草稿 [#35](https://github.com/xiaokh31/androidAppHardening/pull/35)。
- M1-03 已完成有界 binary AXML reader/writer、固定请求/结果模型、单属性 semantic diff、自有 UTF-8/UTF-16/unknown-chunk/resource-map fixtures、18 个稳定错误负例、seed `0x4d313033` 的 5,000 样本 fuzz 与固定 `aapt2` 独立解析；Windows 四份规范报告 hashes 已冻结在 workflow 中。
- M1-03 实现、Host/static 证据与受阻设备尝试已提交为 `352a6d15a7a7b6443123638ef8e5f4fc1aebc527`；该实现提交之后的协调提交只把 HandOff 恢复为 clean 冻结点，不改变产品实现。
- Windows `:host:axml:test` 与 237-task 根 `check verifyGovernance` 均退出 `0`；双变体 Release/R8 测试 APK 的签名、双 DEX、JNI、ABI、R8、原 Factory 配置、metadata 与无明文 payload 静态门禁均 PASS。未下载新工具到 C 盘，也未启动本机模拟器。
- API 29 arm64 非 root 真机被确认是 64-bit `user/release-keys` 且 shell UID 2000。历史首个 extracted 安装曾因 MIUI `INSTALL_FAILED_USER_RESTRICTED` 停止；用户明确允许安装后，同一有界 runner 在 57.5 秒内完成 transformed extracted/direct 双变体 instrumentation、生命周期、跨 DEX、JNI、signer、metadata、各 20 次冷启动、内存、零明文 DEX 与清理，结果 PASS。
- 首轮独立只读 `m1_03_security_review` 对冻结 HEAD `9fee22df524f0465f5a9fc310bec153b6d37696b` 给出 FAIL：P0 `0`、P1 `3`、P2 `1`。发现既有属性扩展尾部被清零、style/namespace 超线性工作、tiny unknown chunk 堆放大及高位 unsigned/显式保留证据缺口；旧冻结点立即失效，结论归档于 `docs/evidence/M1-03/security-review-1.md`。
- 当前修正候选保留既有 Factory 属性的非零扩展尾部，将 chunk/string/attribute/namespace 预算前置，使用常数时间 namespace 活动计数、style 全局线性工作预算与重复 offset 缓存，并把 unknown chunk 保留证明合并为带语义锚点的单一摘要；新增高位 resource/typed value 和三项放大负例。
- 四项发现的修复、首轮 FAIL 归档和新规范 hashes 已提交为 `adbf435c5bc393f90d4358988f5ba6f9cdcd507f`；237-task 根回归、双变体 Release/R8 重建与静态 APK 验证均 PASS。后续协调提交只恢复 clean 冻结点，不改变产品实现。
- 第二轮独立只读 `m1_03_security_review_2` 对冻结 HEAD `99877a49c9950a64941858fa3a01d51dbf8c988e` 给出 FAIL：P0 `0`、P1 `1`、P2 `0`。首轮四项均确认关闭，但完整祖先路径字符串在长元素名和深嵌套下产生二次内存放大；旧冻结点立即失效，结论归档于 `docs/evidence/M1-03/security-review-2.md`。
- 当前修复提交 `f15088d3b811d7be3827e8abaa0286da28f42f6a` 移除持久化完整路径，改用有界 manifest/application 角色标识，并增加 32,767 字符元素名叠加深度上限的稳定失败回归。6 正例、18 负例、5,000 fuzz、237-task 根回归、Governance、四份规范报告 hashes 与双变体静态 APK 验证均 PASS；必须重新冻结并取得新的独立全零复核。
- 第三轮独立只读复核已对 clean 冻结 HEAD `1425f911eb48796a8e4ade9aa3c5fcec09cb1f7b` 给出 PASS：P0 `0`、P1 `0`、P2 `0`。复核者实际运行长名称深嵌套负例并确认稳定有界失败，首轮四项与第二轮路径问题全部关闭；结论归档于 `docs/evidence/M1-03/security-review-3.md`。
- 用户已授权发布固定分支、创建 Issue #8 的唯一草稿 PR 并运行 CI。草稿 PR [#35](https://github.com/xiaokh31/androidAppHardening/pull/35) 的初始 HEAD `c6ed194c2fea9672d6cdd38cf181560e8d76e87f` 已完成 API 29/36 x86_64 KVM、Ubuntu/Windows Build 与 Governance 六项 PASS；两平台四份 M1-03 规范报告 hashes 均命中冻结值。
- 最终证据 HEAD `16ffba62df8f25d4397d771c5bdfa77f8dba78ad` 的 replacement KVM run `30790389457`、Build run `30790389453` 与 Governance run `30790389460` 六项全部 PASS；PR #35 为唯一关联 PR，状态 OPEN、draft、CLEAN、MERGEABLE，且将关闭 Issue #8。
- 用户已明确授权将 PR #35 转为 ready 并合并。本协调提交只准备 post-merge `main` 恢复点，不改变产品实现；merger-ready HEAD 必须再次通过相同六项 CI，再以 expected-head 保护执行普通 merge commit。
- merger-ready HEAD `07c519c73b2a48f8636eed557da463f699299f20` 的 KVM run `30832968146`、Build run `30832968383` 与 Governance run `30832968130` 六项全部 PASS。PR #35 已于 `2026-08-04T00:46:56+08:00` 转为 ready，并以 expected-head 保护的普通 merge commit `197eb45535b117e28ad1ef904993d2b54068056b` 合并到 `main`；Issue #8 已关闭。
- 本地 `main@197eb45535b117e28ad1ef904993d2b54068056b` 已无豁免通过 strict HandOff、Governance 与 diff check；M1-03 完成，M1-04/M2 仍未启动。
- 用户已要求完成 M0-05 剩余部分；固定 Issue 为 #5，固定分支为 `spike/m0-05-application-factory-provider-jni-poc`。
- 旧实现提交 `d58a277681443a5e79b770a3e9162ae54006138d` 已具备 early signer、原 Factory 五类组件委托、双 DEX、JNI 和两种 Native 路径的初始 PoC，但仍依赖已废弃 metadata，必须按 ConfigV2/sourceDir 合同修订。
- 旧 arm64 真机证据仅证明 early signer 可用并复现 metadata 缺失，不构成当前合同的设备验收。
- 最新 `main` 已通过 merge commit `71d3f9519b5e304346814f33b58b5bf97adeb440` 合入既有 M0-05 分支，合并后 strict HandOff 无豁免通过。
- ConfigV2/sourceDir、原 Factory ClassLoader hook、READY 前 session 清理、无 Factory、双 DEX、JNI 和负向 APK 矩阵已完成本地实现；Gradle check、Release/R8、静态 APK、签名和治理校验通过。
- API 29 arm64 非 root 真机已完成 extracted/direct Release/R8、instrumentation、生命周期、跨 DEX、JNI、signer/config/metadata、17 个负向用例、各 20 次冷启动、内存和无明文 DEX 的正式验收，结果 PASS。
- M0-05 可执行实现已冻结为 `0d8e6f8c13ac871c840fe134d83d1bfc0b69d3a9`；后续仅允许为 KVM 失败修复重新冻结，或在不改变实现的前提下补充证据与 HandOff。
- 验证/workflow 已冻结为 `f63a7192eb6e1055a7647d27850ece262c59210a`；GitHub Actions run `30706455270` 的 API 29 job `91386314437` 与 API 36 job `91386314472` 均为 `success`。
- API 29/36 x86_64 Linux/KVM 已完成 extracted/direct Release/R8、instrumentation、生命周期、跨 DEX、JNI、signer/config/metadata、17 个独立启动负例、各 20 次冷启动、内存、无明文 DEX 和强制清理的正式验收，结果 PASS。
- 首轮独立只读 `m0_05_security_review` 在 `859fe25d15cc7e8670ac621d25d2e0101cf93c9a` 上结论为 FAIL：P0 `0`、P1 `3`、P2 `3`；原三套设备证据因此被复核否决，不再作为最终验收。
- 六项发现的修复候选已提交为 `789d37e9fa321b54ee19bf4af1382e589f2942d4`：五类组件委托失败归一、双变体 17 例矩阵、重复 ABI 条目、目标恢复计时、Linux 包精确固定，以及 JUnit/SO/R8/验证器内存证据。
- 本地静态门禁、双变体 Release/R8 和静态 APK 验证已 PASS；GitHub Actions run `30708544925` 的 API 29/36 x86_64 repaired KVM 双 job 已 PASS。
- M0-05 的 API 29 arm64 非 root 真机与 API 29/36 x86_64 Linux/KVM 双变体矩阵、第五次独立只读复核和 PR 最终 HEAD 六项 CI 全部 PASS；冻结证据为 `350d08ee5f3c83bf60dcbd4564866ffb5f819844`，复核结果为 P0 `0`、P1 `0`、P2 `0`。
- 用户已明确授权把 PR #32 转为 ready 并合并；PR #32 已于 `2026-08-02T12:34:55+08:00` 以 merge commit `1fe9ea9ca7ac989e2e071ccb00ae2a0c0010c463` 合并到 `main`，Issue #5 已关闭。
- 合并后的首次 Governance run `30732622423` 只因 HandOff 仍声明旧 source branch 而失败；coordinator-only 提交 `d682c85125e11084cf023b5f523d715e28c74e75` 已完成状态协调，随后 Governance run `30732725929` 与 Build run `30732725931` 在 Ubuntu/Windows 全部 PASS。
- 用户已明确领取 M1-01；固定 Issue 为 #6，固定分支为 `feat/m1-01-untrusted-apk-inspector`，从已通过合并后 CI 与 strict HandOff 的 `main@e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030` 启动。
- M1-01 启动时 `/root` 仅负责该任务；该历史范围已由 PR #33 合并关闭，当前唯一活动任务为 M1-02。
- 首次独立复核尝试被平台中断，未形成有效 PASS/FAIL；其终止前指出 DEX MUTF-8 声明长度可被直接用于 `StringBuilder` 容量。旧冻结目标 `4f55222fd00408f7f67b3b58a93733e9a77c23e2` 因此作废，不得作为完成证据。
- 修正候选 `d3dbfaa8ce4317d8b394f22478ddbb185fd480cb` 改为固定 128 字符 marker 前缀的流式 descriptor 校验，增加 DEX 表顺序唯一性、拒绝非规范 `.dex` 路径，并收紧 AXML resource map、namespace 与 string-pool 验证；35 个负例、10,000 样本 fuzz 和全仓库 check 均 PASS。
- 第二次完整独立只读复核对冻结提交 `02e6334e916581f3d49c89ec512f6e9a9ec4a245` 给出 FAIL：P0 `0`、P1 `4`、P2 `3`。旧冻结提交立即失效；结论归档于 `docs/evidence/M1-01/security-review-2.md`。
- 修正实现 `e267e3c7eab7d3b7d5d8c90947c79f0c77ee1208` 已关闭全部七项发现：AXML 固定 resource ID/namespace/raw-typed 语义、DEX 重复偏移 CPU 上界与显式版本、ELF/path ABI 一致性、版本化规则表，以及缺失的结构化回归。54 个命名错误 fixture、10,000 样本和 231-task 根 check 均 PASS；分支仍未发布。
- 第三次完整独立只读复核对冻结提交 `0bbbeb6da8573ab770b0ca4ec1f6227e444244a1` 给出 FAIL：P0 `0`、P1 `4`、P2 `0`。发现 hash/parse 分离句柄、DEX 装箱集合内存放大、20-byte 截断 ELF 正例和缺失 DEX map/data 闭环；旧冻结提交立即失效，结论归档于 `docs/evidence/M1-01/security-review-3.md`。
- 修正实现 `e97d67f9fbfc5b4c23751a85822dc6c96af4c6c5` 已使用同句柄 64 KiB 分块快照绑定全部 parser 读取、以文件大小受限 BitSet 取代大装箱集合、验证完整 ELF32/ELF64 header，并为 DEX 固定表/data/map-list 建立闭环。58 个命名错误 fixture、10,000 样本和 231-task 根 check 均 PASS；分支仍未发布。
- 第四次完整独立只读复核对冻结提交 `19ea544ddec32fcaac63dfee81f25546084d8bae` 给出 PASS：P0 `0`、P1 `0`、P2 `0`。复核者独立重跑根 check、Governance、strict HandOff 和 diff check，均退出 `0`；结论归档于 `docs/evidence/M1-01/security-review-4.md`。分支仍未发布且尚无 PR。
- 用户已明确授权推送固定分支、创建关联 Issue #6 的唯一草稿 PR，并运行 Ubuntu/Windows 字节一致性 CI。发布前补充的 Build 门禁要求两个平台生成的规范模型与 58-fixture 错误矩阵分别严格命中同一冻结 SHA-256；不改检查器实现。
- 固定分支已发布到 `83faffb17b41efc7cd9c81cff6759ddf2208a135`，关联 Issue #6 的唯一草稿 PR 为 [#33](https://github.com/xiaokh31/androidAppHardening/pull/33)。Build run `30736757259` 与 Governance run `30736757261` 的 Ubuntu/Windows 四个 job 全部 PASS；两个 Build job 的显式字节门禁均命中同一规范模型和错误矩阵 SHA-256。
- 证据提交 `de4d69a6c178de97da1e7700948d8d1db5a4ff79` 的最终 PR Build run `30736945439` 与 Governance run `30736945448` 再次在 Ubuntu/Windows 四个 job 全部 PASS；两个最终 Build job 的字节一致性步骤均 PASS。
- 用户已明确授权将 PR #33 转为 ready 并合并。当前 merger-ready 协调只允许更新 HandOff/证据；该协调提交通过最终 CI 后，必须以预期 HEAD 保护执行普通 merge commit，并在 `main` 无豁免运行 strict HandOff。
- merger-ready HEAD `7a54ba7874fb47aaf749715be2ba5897ef5a6b2e` 的 Build run `30737456598` 与 Governance run `30737456604` 已在 Ubuntu/Windows 全部 PASS；两项字节一致性步骤也均命中冻结哈希。
- PR #33 已于 `2026-08-02T15:19:49+08:00` 转为 ready，并以普通 merge commit `74c5f6252ea9b89154c285764d5f9601a0347358` 合并到 `main`；Issue #6 已自动关闭。本地 `main` 已无豁免通过 strict HandOff、Governance 与 diff check，M1-01 完成，M1-02/M1-03/M2 未启动。
- M1-01 的 post-merge `main@aebbc441da34d2fba78648415c1d80ea844d774d` 已在 Ubuntu/Windows Build 与 Governance 全部 PASS；两平台字节一致性步骤和 `main` strict HandOff 均通过。
- 用户已明确启动 M1-02。固定 Issue 为 #7，固定分支为 `feat/m1-02-signer-policy`，base 为 `aebbc441da34d2fba78648415c1d80ea844d774d`；远端不存在同名分支或既有 M1-02 PR。
- `apksig 9.3.0` 已由 version catalog 和 dependency verification 固定；ADR 0002 与 ADR 0004 已覆盖 signer/未签名输出和 `SPV1` 模型合同，无需新增 ADR。独立复核者预定为 `m1_02_security_review`，只在实现与证据提交冻结后启动。
- 首次独立 `m1_02_security_review` 对证据 HEAD `21bfd6db333767c9182c1310e6cd838a8fae49a1` 给出 FAIL：P0 `0`、P1 `1`、P2 `3`。发现无界 Signing Block materialization、公开 cause 路径泄露、magic-only unsigned 误分类和官方 cross-check/manifest 缺口；旧冻结目标立即失效并归档于 `docs/evidence/M1-02/security-review-1.md`。
- 修复候选 `5016cd39426b2d50d1fbedfafee2f24c567e0546` 关闭四项发现：32 MiB block/连续读取上限、公开异常无 cause、完整 block envelope 分类、六个正向官方交叉验证、每个负例官方状态和完整 artifact manifest。
- 第二次独立复核对 `8718975255cfbdab4fc2ce29eae67c18f21b62ed` 给出 FAIL：P0 `0`、P1 `0`、P2 `1`。首轮四项均确认关闭，但高位无符号 size 被 `Long` 解码为负数后误归 unsigned；结论归档于 `docs/evidence/M1-02/security-review-2.md`。
- 最终修复候选 `61908507c741865c50aac07763d42c890bf25d4b` 将负 size 明确归为 malformed，增加 `Long.MIN_VALUE`/`-1L` 回归，并把 `input_changed` 官方状态绑定变更后 artifact。
- 最终 Windows clean signer matrix 与 256-task 根 `clean check verifyGovernance` 已 PASS；规范 policy 和错误矩阵 SHA-256 分别为 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2` 与 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`。Ubuntu 字节一致性仍须在第三次独立复核 PASS、用户授权发布后由 GitHub CI 验证。
- 第三次独立只读复核对冻结证据 HEAD `902c20977d787ea9646078bbbe4c3c46bf0041cc` 给出 PASS：P0 `0`、P1 `0`、P2 `0`。两轮历史发现全部关闭；边界探针、十三行错误矩阵、二十六项 manifest、官方 signer 摘要、异常脱敏和无签名能力边界均通过。结论归档于 `docs/evidence/M1-02/security-review-3.md`。
- 用户已明确授权推送 `feat/m1-02-signer-policy`、创建关联 Issue #7 的唯一草稿 PR，并运行 Ubuntu/Windows CI；该授权不包含 ready 或 merge。
- 固定分支已推送，关联 Issue #7 的唯一草稿 PR 为 [#34](https://github.com/xiaokh31/androidAppHardening/pull/34)。首轮 Governance run `30752847768` 的 Ubuntu/Windows job 均 PASS；Build run `30752847752` 的双平台 job 在测试前因 `host:cli` 缺失 apksig 9.3.0 传递依赖锁而 FAIL。
- 用户已明确授权修复该 CI 锁问题。自动生成的最小 diff 只为 `host/cli/gradle.lockfile` 增加 apksig 9.3.0 的 `runtimeClasspath,testRuntimeClasspath` 条目；本地 Windows 256-task 根回归随后 PASS，产品代码、版本和冻结报告哈希均未改变。
- 锁修复 HEAD `b72ef88003c2dea993afbd7d96d502535833e450` 的替换 Build run `30753702741` 与 Governance run `30753702728` 已在 Ubuntu/Windows 四项全绿；两平台 M1-02 字节门禁均命中冻结 policy/error hashes。PR #34 保持 draft，ready/merge 尚未授权。
- 最终证据 HEAD `2ed5f4f7973c9ff87a3e3cbbf6e4e5325a259418` 的 Build run `30753889812` 与 Governance run `30753889774` 已在 Ubuntu/Windows 四项全绿；两平台 M1-02 字节门禁再次命中冻结 hashes。
- 用户已明确授权将 PR #34 转为 ready 并合并。当前 merger-ready 协调只允许更新 HandOff；该协调 HEAD 通过最终 CI 后，必须以 expected-head 保护执行普通 merge commit，并在 `main` 无豁免运行 strict HandOff。
- merger-ready HEAD `43fd2dd0671b90430b5f4b06f1728c563eb4c07c` 的 Build run `30782245138` 与 Governance run `30782245141` 已在 Ubuntu/Windows 全部 PASS；两项 M1-02 字节一致性步骤再次命中冻结哈希。
- PR #34 已于 `2026-08-03T11:40:12+08:00` 转为 ready，并以 expected-head 保护的普通 merge commit `d590b94f08047352d2b1f56c1c08aba4cbf079ec` 合并到 `main`；Issue #7 已关闭。本地 `main` 已无豁免通过 strict HandOff、Governance 与 diff check，M1-02 完成，M1-03/M1-04/M2-03 未启动。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29、正式设备矩阵和独立复核已通过 |
| M0-06 | `runtime-security-agent` | `docs/m0-06-early-startup-config-contract` | done | M0-04 | PR #31、合并后 strict HandOff 和双平台 CI 已通过 |
| M0-05 | `runtime-security-agent` | `spike/m0-05-application-factory-provider-jni-poc` | done | M0-04, M0-06 | PR #32、三环境矩阵、独立安全复核和最终 PR CI 已通过 |
| M1-01 | `/root` | `feat/m1-01-untrusted-apk-inspector` | done | M0-05 | PR #33、Issue #6、独立复核、双平台字节一致性 CI 与 main strict HandOff 均已关闭 |
| M1-02 | `/root` | `feat/m1-02-signer-policy` | done | M1-01 | PR #34、Issue #7、独立复核、双平台字节一致性 CI 与 main strict HandOff 均已关闭 |
| M1-03 | `/root` | `feat/m1-03-binary-axml-transformer` | done | M1-01, M0-05 | PR #35、Issue #8、独立复核、三套设备/CI 矩阵和 main strict HandOff 均已关闭 |
| M1-07 | `/root` | `docs/m1-07-chunk-authenticated-container-contract` | done | M1-02 | PR #37、Issue #36、独立复核、双平台 CI、README 与 main strict HandOff 均已关闭 |
| M1-04 | `/root` | `feat/m1-04-encrypted-dex-container` | done | M1-01, M1-02, M1-07 | PR #38、Issue #9、独立复核、merger-ready 六项 CI、post-merge 双平台 CI、README 与 main strict HandOff 均已关闭 |
| M1-05 | `/root` | `feat/m1-05-apk-repacker-and-alignment` | done | M1-02, M1-03, M1-04 | PR #39、Issue #10、独立复核、merger-ready CI、post-merge 双平台 CI、README 与 main strict HandOff 均已关闭 |
| M1-06 | `/root` | `feat/m1-06-cli-and-json-report` | done | M1-01, M1-02, M1-03, M1-04, M1-05 | PR #40、Issue #11、冻结复核、merger-ready 双平台 CI 与 expected-head 合并已关闭；等待 post-merge main 最终门禁，不启动 M2 |
| M2-07 | `/root` | `chore/m2-07-native-crypto-backend` | in_progress | M0-03, M1-04 | PR #42 replacement 双平台标准向量与四 ABI CI 通过后冻结，启动独立只读安全复核 |
| M2-02 | `/root` | `feat/m2-02-native-decrypt-loader` | blocked | M0-04, M1-04, M2-07 | `/root` 在 M2-07 合并且 final main 门禁通过后恢复 `40e3900` |

## Decisions and Invariants

- 继续遵守 ADR 0001 至 ADR 0003、ADR 0005 至 ADR 0008；ADR 0004 已被 ADR 0008 supersede。ADR 0007 固定 sourceDir 配置通道，ADR 0006 保持 768-byte ConfigV2 且 `container_major=2`。
- AHDC v2 是 v0.1 唯一容器 major：64 KiB canonical compressed-plaintext chunk，每 chunk 独立 AES-256-GCM tag；tag 成功后才进入每 DEX 唯一连续 zlib inflater，AHDC v1 不得回退接受。
- 输入 APK 只读；产品输出必须为新的未签名 APK；生产模块不得读取、传递或使用签名凭据。
- M0-05 使用 `pre-cli` 验证模式，只处理仓库生成的合成 fixture 和被忽略的一次性测试签名产物。
- API 29+ 只使用公开 `AppComponentFactory.instantiateClassLoader()`、Framework `ApplicationInfo` 和只读文件 API；不使用 Context、PackageManager、Framework 私有对象、反射或 hidden API 回退。
- 启动固定读取 `assets/ah/runtime/config.bin` 与 `assets/ah/runtime/payload.ahdc`；ConfigV2 在 PoC 级 APK signer 覆盖成立前不得暴露原 Factory。
- Manifest 只替换 `android:appComponentFactory`，不新增或读取废弃 `ah.runtime.*` metadata；原 Application 使用 Framework `className`。
- x86_64 验收只在有整体超时和强制清理的 GitHub Linux/KVM 环境运行；本机不启动模拟器。arm64 验收只使用已授权非 root 真机。
- 每个平台覆盖 extracted/direct 两种 Release/R8 变体，并验证 instrumentation、生命周期顺序、跨 DEX、JNI、早期 signer、ConfigV2、篡改失败、20 次冷启动、内存和无明文 DEX 落盘。
- 冻结设备证据和提交后，由独立 `m0_05_security_review` 只读复核；P0/P1/P2 全部关闭前不完成任务。
- x86/x86_64 结果不得冒充 ARM-only 应用兼容性；离线 Runtime 只提高提取成本，不作绝对防护声明。
- M1-01 只使用仓库生成的合成 APK/AXML/DEX fixture；输入只读、不得解压到磁盘、不得执行输入代码、不得引入未经审计的第三方 parser。独立只读复核者预指定为 `m1_01_security_review`，仅在实现与证据提交冻结后启动。
- M1-02 只使用固定 `apksig 9.3.0` 读取公开证书信息，最低检查平台固定为 API 29；要求唯一当前 signer，DER SHA-256 使用 32 字节原始摘要和 64 字符小写 hex，轮换 lineage 为旧到新、`1..16`、无重复且以当前摘要结束。
- 产品仍不得接收或调用私钥、keystore、alias、密码、HSM、远程签名服务或任何签名执行器；M1-02 不序列化 `SPV1`，只提供 M1-04 可消费的防御性摘要副本与模型约束。
- M1-03 生产路径只处理 binary AXML；string pool 仅追加字符串并保持旧 index，未知 chunk 原 bytes/顺序保留，resource map 仅允许补入 compileSdk 36 固定 `android:appComponentFactory` ID `0x0101057a`。
- M1-03 唯一语义白名单是 application 上的 `android:appComponentFactory=ah.runtime.bootstrap.ShellAppComponentFactory`；不得新增、删除或改写 `<meta-data>`、`android:name` 或任何其他元素/属性。
- 根 `README.md` 必须维护公开任务进度表；任务仅在合并后门禁完成时标记“已完成”，每个任务的收尾协调提交必须同步该表，不能以 README 替代 `HandOff.md` 的证据。

## Changes Since Previous Handoff

- 用户启动 M1-06 并预授权完整发布/合并流程；协调者核验 Issue #11、依赖、无分支/PR 冲突与 `main@55ef3c5` final 双平台 CI 后创建固定分支。
- 新增 M1-06 full-flow 实施计划，固定唯一 protect 命令、schema discriminator、报告自哈希边界、RuntimeBundle distribution/test fixture 分离、原子 report 回滚语义与验收矩阵；不改变上游算法或启动 M2。
- 实现 `host/cli` 应用入口、严格 parser/path policy、七阶段 orchestration、REPORT_V1 writer/schema、classpath RuntimeBundle fail-closed loader、输出/report 回滚和稳定 exit/error 映射。CLI 只在编排边界把 M1-01 一基 DEX ordinal 转成 AHDC v2 零基视图，不修改上游 parser 或容器 wire format。
- Windows full-flow 使用官方固定 aapt2/apksigner、仓库合成双 DEX/四 ABI/Application/Factory fixture 和 test-only RuntimeBundle 通过；官方 apksigner 确认输出未签名。SIGNER/AXML/CONTAINER/PACKAGE/VERIFY/PUBLISH/INTERNAL、线程取消、JVM shutdown、short-write/disk-full 等价注入、report race 与 cleanup/input immutability 全部失败关闭；所有成功/失败报告实际执行 checked-in Draft 2020-12 Schema 校验。
- 首个冻结点 `7d9072e` 的 shutdown report publication owning 与 pre-stage error mapping 发现已修复；新增三个定向回归并重跑 273-task clean 根门禁，冻结哈希更新为 normalized `71052641...c213`、error `9de958b0...785e`、cleanup `03f9f1b8...598`、path `ea48b25b...e4a5`。
- 发布固定分支并创建唯一草稿 PR #40，正确关联关闭 Issue #11；初始 HEAD `5a3981c` 的 Build/Governance run `31115781825`/`31115781121` 在 Ubuntu/Windows 四项全绿，两平台 M1-06 full-flow 与四份字节一致性门禁全部 PASS。
- merger-ready `7029957` 的 Build/Governance run `31116416406`/`31116415535` 四项全绿；PR #40 已以 expected-head 普通 merge commit `d0eb392` 合并且 Issue #11 关闭。本地 main 已同步，README 将 M1-06 标记完成，当前执行 post-merge strict 与最终 main CI。
- 用户明确启动 M1-05 并授予完成任务所需的推送/PR/ready/merge权限；协调者核验 Issue #10、无同名远程分支/PR、依赖完成和 `main@d32abe1` 双平台 Build/Governance 全绿后创建固定分支。
- 新增 M1-05 实现计划，把九项验收映射为 raw ZIP 保留、签名材料精确删除、ABI policy、ELF share materialization、4 KiB/16 KiB 对齐、独立 verifier、别名/故障注入、外部 Android 工具和双平台字节门禁；未改变 ADR 或相邻公共接口。
- 关闭首轮 M1-05 复核候选的 P1=4/P2=1：plan cleanup 先于发布、敏感 owner/OOM 清理、同句柄输入及文件身份复验、完整定向 mutation/TOCTOU 矩阵、异常名称脱敏与官方 unsigned reason 均已有可执行证据；旧冻结 `bb748f6` 保持废止。
- 冻结提交 `58352c6de732887cf497de2775bc0fa3021f5332` 完成 AHDC v2 builder/verifier、ConfigV2/密钥包装、规范、自测、证据和 Ubuntu/Windows 固定容器哈希门禁；不包含 Runtime、APK 注入、签名或 CLI。
- Windows 模块 `check`、Node 独立消费者、Governance、diff check 与安全扫描均通过；固定容器哈希为 `3764b908e...fa60d`，完整篡改矩阵、512 MiB、边界和事务清理负例全部失败关闭，运行结束 Java 为 0。
- 五轮独立只读复核中前四轮 FAIL 均已归档；第五轮在冻结提交上 PASS，P0/P1/P2 全零。merger-ready HEAD 六项 CI 全部通过，PR #38 已以 expected-head 普通 merge commit 合并且 Issue #9 已关闭；post-merge `main@9f074db` 的 Ubuntu/Windows Build、Governance、M1-04 字节一致性与无豁免 strict HandOff 全部通过，README/HandOff 已同步完成状态。
- 用户启动 M1-04；核验 Issue #9 OPEN、远程无关联 PR/分支、main 与 origin/main 一致且 post-M1-07 双平台 CI 全绿。
- 旧 AHDC v1 失败分支无损归档为 `spike/m1-04-rejected-ahdc-v1`，从 `main@ebbe928` 新建固定 AHDC v2 工作分支；新增 `docs/evidence/M1-04/implementation-plan.md`，未复用废止实现。
- 用户启动 M1-07，创建 Issue #36 和独立治理分支；该分支不包含废止的 M1-04 产品实现。
- ADR 0008 固定 160-byte HeaderV2、128-byte RecordV2、32-byte ChunkV2、record key/nonce/AAD/manifest coverage、1 MiB 工作缓冲和 cleanup error precedence；ADR 0004 标记 superseded。
- M1-04 依赖增加 M1-07；产品需求、架构、威胁模型、测试策略、路线图和下游任务正同步到 AHDC v2。
- 首轮独立复核废止 `e13927a`；P1/P2 修复只更正权威 Runtime chunk 语义和依赖证明，不改变 ADR 0008 wire layout。
- 第二轮独立复核废止 `3380659`；两项 P1 修复增加 Goal 的无 whole-record 约束和 handle 发布前事务清理合同，wire layout、KDF、nonce、AAD 与 manifest bytes 不变。
- 第三轮独立复核废止 `e355438`；唯一 P1 只纠正成功/失败清理验收的互斥语义，不改变 wire 或事务所有权模型。
- 第四轮独立复核废止 `dd0c4c0`；两项 P1 补齐成功提交临时秘密清理和 M3-02 事务清理证据，P2 纠正复核时间证据语义，wire 不变。
- 第五轮独立复核废止 `340b6ae`；唯一 P1 补齐 Native handle 到公开 `LoadedPayload` 之间的跨 JNI 所有权窗口，wire 不变。
- 第六轮独立复核废止 `bb2e744`；两项 P1 补齐 authenticated metadata 跨模块接口和 LoadedPayload 到 VerifiedPayloadSession 的 Guard 所有权窗口，wire 不变。
- 第七轮独立复核废止 `3bea66a`；一项 P1 与两项 P2 只统一权威时序、metadata 注入和双窗口验收，wire 不变。
- 第八轮独立复核废止 `01f76f6`；一项 P1 与一项 P2 补齐 package/lineage 可实现复比较和完整复核输入，wire 不变。
- 第九轮独立复核废止 `d5d5d29`；一项 P1 与一项 P2 统一 loader 构造/使用边界并冻结完整 metadata API，wire 不变。
- 第十轮独立复核废止 `358a71a`；唯一 P2 把 Guard 每个比较映射到真实来源并移除虚假 Factory 双源断言，wire 不变。
- 第十一次独立复核通过 `9dec760`，P0/P1/P2 全零；该 SHA 成为 M1-07 当前 merger-review 候选，发布仍需用户单独授权。
- 用户已授权发布 M1-07，并要求今后每个任务完成时同步根 README；本次 README 从停留在 M0 的旧描述更新为 M0 完成、M1-01/02/03 已完成、M1-07 待合并及 M1-04 为下一开发任务。
- 发布提交 `b094119a33e2fe4b69e23f03a0c7ae05080f3834` 已推送到固定分支，并创建关联 Issue #36 的唯一草稿 PR #37；GitHub App 写入因 integration 权限返回 403 后，使用已验证登录的 `gh` CLI 回退完成创建，未产生重复 PR。
- 证据 HEAD `ceeae8a4a0828b97ad45196d3727fca460c59f91` 的 Build run `31021991586` 与 Governance run `31021992020` 在 Ubuntu/Windows 四项全部 PASS；PR #37 保持 draft，未获 ready/merge 授权。
- 最终草稿 HEAD `2c13ecc8521f269e6f02fdace77f7f14f546c9cc` 的 Build run `31022701793` 与 Governance run `31022701584` 在 Ubuntu/Windows 四项全部 PASS；用户已授权 ready/merge，当前只新增 merger-ready HandOff 协调并把合并后恢复分支设为 `main`。
- merger-ready HEAD `d0e0ee61171eb9472bce306619a426da86292f5c` 的四项 CI 全绿后，PR #37 已以 expected-head 普通 merge commit `9ec90fca6b2b293a56a98f3d0c60190b5c0e7a20` 合并，Issue #36 关闭；README 已按持续规则把 M1-07 标记为完成。
- merger-ready HEAD `07c519c73b2a48f8636eed557da463f699299f20` 的 API 29/36 KVM、Ubuntu/Windows Build 与 Governance 六项全部 PASS；两个 Build job 再次命中四份规范报告冻结 hashes。
- PR #35 已以 expected-head 保护的普通 merge commit `197eb45535b117e28ad1ef904993d2b54068056b` 合并，Issue #8 已关闭；本地 `main` 已无豁免通过 strict HandOff、Governance 与 diff check，M1-03 标记 done。
- 最终证据 HEAD `16ffba62df8f25d4397d771c5bdfa77f8dba78ad` 的 API 29/36 KVM、Ubuntu/Windows Build 与 Governance 六项 replacement CI 全部 PASS；两平台四份 M1-03 规范报告 hashes 再次命中冻结值。
- 用户已明确授权将 PR #35 转为 ready 并合并；当前只新增 merger-ready HandOff 协调，不改产品实现，并要求协调 HEAD 再次通过六项 CI 后以 expected-head 保护合并。
- 用户明确启动 M1-03；协调者核验 Issue #8 为唯一 tracking Issue，远端无同名分支且不存在 M1-03 PR，并从已验证 `main@077e4be14865c777dbbf3c1a5a3d9609b3620868` 创建固定分支 `feat/m1-03-binary-axml-transformer`。
- ADR 0003、ADR 0007 与系统架构均已固定单属性 Manifest 变换、原 Application/metadata 保留和 sourceDir 配置合同；本任务无需新增 ADR，M1-04/M2 保持未启动。
- 用户明确要求开始 M1-01；协调者从 `main@e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030` 创建唯一固定分支 `feat/m1-01-untrusted-apk-inspector`，领取 Issue #6，并保留 M1-02/M1-03/M2 未启动。
- 提交 `bb2a6a93b840dd0416118119b4fe4434e395be02` 新增不可变公开模型、固定限制与错误码、有界 ZIP/AXML/DEX/ABI 检查、版本化兼容 marker 表、无依赖合成 fixture 和 10,000 样本 deterministic fuzz；未新增第三方依赖。
- Windows 正式 `:host:apk-inspector:test` 与根 `check` 均退出 `0`；根 check 共 231 actionable tasks。规范模型与 32-fixture 错误矩阵 SHA-256 分别为 `a689e24f5a0e5dd81fcfe4175cacb3566477a4a659ed3da5dd3c6a84014264d3` 与 `545aa5987cc82fc98a0f7f20dcc5492ba84d40d91431a3350da6122854f39618`。
- 首次独立 reviewer 在形成最终 handoff 前被平台中断；该次不计作独立复核结论。其已报告的攻击者长度分配缺口按失败门禁处理，旧冻结目标立即失效。
- `d3dbfaa8ce4317d8b394f22478ddbb185fd480cb` 关闭该资源缺口并补充 oversized DEX string、oversized AXML resource map 和 non-canonical DEX path 回归；修正后的规范模型 SHA-256 保持 `a689e24f5a0e5dd81fcfe4175cacb3566477a4a659ed3da5dd3c6a84014264d3`，35-fixture 矩阵 SHA-256 为 `184fcde7ae41234bfe4a0a3f61b76bdd32afb45882d05449573e372f69613d2e`。
- 第二次完整独立只读复核否决 `02e6334e916581f3d49c89ec512f6e9a9ec4a245`，发现 AXML 语义闭环、DEX CPU 放大、ELF ABI 分类和强制回归四项 P1，以及 DEX 036、规则表版本、HandOff 陈旧三项 P2。
- `e267e3c7eab7d3b7d5d8c90947c79f0c77ee1208` 关闭上述代码与测试发现。正式 Windows 模块测试和根 check 退出 `0`；新规范模型 SHA-256 为 `fc224233c5a7a61b13075431684f0478c83f784444e712492315b4631c9efcc8`，54-fixture 错误矩阵 SHA-256 为 `b6df7c5d4ba216f78a3b52d3bac043d64900fed5ab4ed3b3a10f554a975c0d1f`，峰值已用内存为 `316352352` bytes。
- 第三次完整独立只读复核否决 `0bbbeb6da8573ab770b0ca4ec1f6227e444244a1`，四项 P1 分别为输入 hash/model 句柄脱钩、DEX offset 装箱集合内存放大、截断 ELF 正例和 DEX map/data 非标准正例；P2 为 `0`。
- `e97d67f9fbfc5b4c23751a85822dc6c96af4c6c5` 关闭上述四项。正式 Windows 模块测试和根 check 退出 `0`；新规范模型 SHA-256 为 `c15561ee6d6e879ad9db058be2762282538a77d4204279d6b5d6d57b1f1d52bf`，58-fixture 错误矩阵 SHA-256 为 `b396616ff369fa2d4db56c92f6908253339867d71554f96debee4d7ed06a02fc`，峰值已用内存为 `108715272` bytes。
- 第四次完整独立只读复核在冻结提交 `19ea544ddec32fcaac63dfee81f25546084d8bae` 上给出 PASS，P0/P1/P2 全为零；独立根 check、Governance、strict HandOff 和 diff check 均通过。该结论关闭本地实现与独立复核门禁，但不替代发布授权或双平台 PR CI。
- 用户已授予 M1-01 发布权限；`.github/workflows/build.yml` 在原 Ubuntu/Windows 根检查后分别校验 `canonical-model.json` 与 `error-matrix.json` 的冻结 SHA-256，使两平台只有在产物逐字节等于同一规范值时才能通过。
- 分支发布 HEAD `83faffb17b41efc7cd9c81cff6759ddf2208a135` 已创建唯一草稿 PR #33。Build #23 的 Ubuntu/Windows job `91466820734`/`91466820755` 和 Governance #32 的 Ubuntu/Windows job `91466820833`/`91466820830` 全部 PASS；Build 日志在两个平台都记录相同的两份冻结报告哈希。
- 证据提交 `de4d69a6c178de97da1e7700948d8d1db5a4ff79` 的 Build #24 Ubuntu/Windows job `91467352371`/`91467352342` 与 Governance #33 Ubuntu/Windows job `91467352594`/`91467352631` 全部 PASS；最终两个 Build 日志再次包含相同规范模型和 58-fixture 错误矩阵哈希。
- 用户已授权 PR #33 ready/merge；merger-ready HandOff 将 resume branch 设为 `main`，合并方式保持仓库普通 merge commit 策略，不使用 squash/rebase/force。
- merger-ready 协调提交 `7a54ba7874fb47aaf749715be2ba5897ef5a6b2e` 的 Build #25 与 Governance #34 在 Ubuntu/Windows 全部通过，且两份规范报告逐字节命中冻结 SHA-256。
- PR #33 已转为 ready 并以普通 merge commit `74c5f6252ea9b89154c285764d5f9601a0347358` 合并，Issue #6 已关闭；本地 `main` 随后无豁免通过 strict HandOff、Governance 和 diff check。
- 用户明确启动 M1-02；协调者核验 Issue #7 为唯一 tracking Issue，远端无同名分支或 M1-02 PR，并从已验证 `main@aebbc441da34d2fba78648415c1d80ea844d774d` 创建固定分支 `feat/m1-02-signer-policy`。
- `docs/evidence/M1-02/implementation-plan.md` 固定输入、输出、公开接口、稳定错误语义、`SPV1` 模型边界、跨平台报告和独立复核顺序；不扩大到 M1-04/M2-03。
- `146aac3795a1f92adefbab376939129e55975c65` 新增 `SignerPolicyVerifier`、不可变 `SignerPolicyV1`、官方 lineage 解析、同句柄输入变更检测、v1/v2/v3/v4/rotation/multi-signer 合成矩阵、官方 `apksigner` digest 交叉验证和生产能力扫描。
- Windows clean M1-02 矩阵与根 `clean check verifyGovernance` 均退出 `0`；根回归共 256 actionable tasks，M1-01 10,000 样本保持 PASS。本轮未启动模拟器或真机。
- `.github/workflows/build.yml` 已增加 Ubuntu/Windows 规范 policy 与错误矩阵固定哈希门禁；分支尚未发布，故 Ubuntu 等价性未声明。
- 首次独立复核否决 `21bfd6db333767c9182c1310e6cd838a8fae49a1`，结论为 P0 `0`、P1 `1`、P2 `3`；完整 finding、攻击路径和独立命令已归档于 `docs/evidence/M1-02/security-review-1.md`。
- `5016cd39426b2d50d1fbedfafee2f24c567e0546` 对 Signing Block envelope 和 materialization 设置 32 MiB 上限，断开公开异常 cause，修正 magic-only unsigned，并补齐六个正向/十一行错误矩阵的官方验证和全部 artifact 哈希。
- 修复候选的 clean signer task 在 102 秒内 PASS；根 `clean check verifyGovernance` 在 2 分 43 秒内 PASS，共 256 actionable tasks。新 error matrix SHA-256 为 `dce3c1a17647a96e93da291033e28c169ad0f5daee5d7544c6555392d66fc7eb`，official cross-check 为 `c63d706f08763819e30c1e682fff87448a999a3ce53a27c7253e35ef9f82e2ba`，artifact manifest 为 `fddc19d2a1ed3068c8ac5cdf8bc44299df0279a927a62da0af33be7cc1a0eab8`。
- 第二次独立复核确认首轮 P1/P2 全部关闭，但以 P2 否决高位 size 语义；`61908507c741865c50aac07763d42c890bf25d4b` 修复并新增两个高位负例。最终 clean signer 与 256-task 根回归再次 PASS；error matrix SHA-256 更新为 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`，artifact manifest 为 `d74287aec49cfd3cb18af55c6119b3ea90689d2f03bc15df8e5e8d04f43eb201`。
- 第三次独立只读复核冻结 `902c20977d787ea9646078bbbe4c3c46bf0041cc`，专项 clean signer 103.9 秒、根 256-task check 163.6 秒均退出 `0`；P0/P1/P2 全为零。该复核未修改 tracked 文件、未联网、未启动设备或模拟器，完整结论已归档。
- PR #34 首轮 Build run `30752847752` 在 Ubuntu/Windows 同因依赖锁缺口失败：`:host:cli:testRuntimeClasspath` 解析到固定 `apksig:9.3.0`，但 downstream lock state 未收录。用户授权后通过 Gradle `--write-locks` 仅增加该版本的两个 runtime 配置；本地 256-task 根回归退出 `0`，规范 policy/error hashes 不变。
- 锁修复提交 `b72ef88003c2dea993afbd7d96d502535833e450` 的 Build run `30753702741` 与 Governance run `30753702728` 在 Ubuntu/Windows 全部 PASS；两个 Build job 的 M1-02 显式字节门禁均命中 `b945ede1...` policy 与 `c33d3420...` error matrix。
- 最终证据提交 `2ed5f4f7973c9ff87a3e3cbbf6e4e5325a259418` 的 Build run `30753889812` 与 Governance run `30753889774` 在 Ubuntu/Windows 全部 PASS；PR #34 为 CLEAN/MERGEABLE。用户已授权 ready/merge，merger-ready HandOff 将恢复分支设为 `main`，合并方式保持普通 merge commit，不使用 squash、rebase、force 或分支删除。
- merger-ready 协调提交 `43fd2dd0671b90430b5f4b06f1728c563eb4c07c` 的 Build run `30782245138` 与 Governance run `30782245141` 在 Ubuntu/Windows 全部通过；PR #34 随后以普通 merge commit `d590b94f08047352d2b1f56c1c08aba4cbf079ec` 合并，Issue #7 关闭，本地 `main` 无豁免通过 strict HandOff、Governance 和 diff check。

- PR #31 已合并，旧 metadata blocker 的架构依赖已解除，M0-05 从 `blocked` 恢复为 `in_progress`。
- 既有 M0-05 分支保留四个本地历史提交和 Issue #5，不创建第二分支或第二任务。
- 已把 `main@f1362188be5083a6d557522f0f5be1905935f6eb` 合入固定分支，解决 HandOff 冲突并无豁免通过 strict 验证。
- 已实现严格 768-byte ConfigV2、固定 sourceDir 条目、测试 signer 双重绑定、原 Factory 确定性 ClassLoader 委托和 READY 前失败清理。
- 已新增不会启动/关闭模拟器的跨平台设备 runner、签名后 Config/ZIP/payload 负向矩阵和固定 API 29 r8/API 36 r2/Emulator 37.1.11 Linux/KVM workflow。
- Google 官方 Linux Emulator 归档只下载到项目 D 盘 ignored `build/`，SHA-256 固定为 `95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266`；未向 C 盘下载大体积工具。
- MIUI streamed install 的拒绝已通过标准 `adb install --no-streaming` 方式消除；正式 API 29 arm64 非 root 真机矩阵在 64.2 秒内 PASS，runner 完成 cleanup，未启动本机模拟器。
- 用户已授予一次验证性推送权限；冻结分支可推送用于 KVM workflow，但独立复核 PASS 前不创建 PR。
- GitHub Linux/KVM workflow 的 API 29 冷启动检查已改为在 2 秒有界窗口内核验目标进程与 resumed Activity，避免 Android 10 `am start -W` 偶发先报告 Launcher 的假阴性，真实未恢复仍失败并保留 logcat。
- 独立启动负例检查已按当前 FATAL PID 隔离日志，避免 Android 10 logcat 中前一 instrumentation 进程的 marker 污染；当前失败 PID 必须包含预期错误码且不得包含 `LOADER_CREATED`。
- `f63a7192eb6e1055a7647d27850ece262c59210a` 上的 run `30706455270` 双 job PASS；正式报告、命令日志、启动负例报告和静态报告哈希已归档到 `docs/evidence/M0-05/formal-compatibility.md`。
- 首轮独立只读复核否决上述证据作为最终验收，发现 3 个 P1 和 3 个 P2；完整记录见 `docs/evidence/M0-05/security-review-1.md`。
- `789d37e9fa321b54ee19bf4af1382e589f2942d4` 已关闭六项代码、工作流与证据缺口并通过本地 Gradle/check/governance 和双变体静态验证；三套设备环境正在重跑，尚未重新声明 M0-05 PASS。
- run `30708544925` 在 `587e7f2c7ab9ba44296891fb3d2668e4bd54998c` 上完成 repaired API 29/36 x86_64 KVM：双变体各自 17/17 负例、各 20 次冷启动、JUnit、R8/SO/验证器内存证据和 cleanup 全部 PASS；原始证据位于 ignored `build/m0-05/github-run-30708544925/`。
- repaired API 29 arm64 真机在 `2026-08-02T10:31:01+08:00` 完成：双变体 instrumentation 和独立 17/17 负例、各 20 次冷启动、JUnit、组件委托 16 例、native 3 例、无明文 DEX 和 cleanup 全部 PASS；报告 SHA-256 为 `e2b154a79f22b900956f4eccdd9c8a450a69a6be340244c031ccf6103aaa94dd`。

- Second independent read-only review of frozen SHA `39a30ed1bb5ab80bb13c2ac71968c1599bbb6db4` returned FAIL with P0 `0`, P1 `0`, P2 `3`; no PR was created and the evidence-only commit remains unpushed.
- Commit `189a04c5286187ae61575d3a9ec574d62501eacc` adds an authenticated, signed duplicate-ABI-alias startup negative, renames the M0-04 baseline DEX comparison so it is not attributed solely to the verifier, and raises each extracted/direct matrix to 18 cases.
- Review-3 KVM run `30729952586` passed on API 29/36 x86_64, and the API 29 arm64 physical matrix passed at the same implementation lineage. Each environment and variant passed 18/18 startup negatives, including an installed signed duplicate-ABI alias returning `AAH-P004` with no loader creation.
- Corrected device evidence is frozen at `350d08ee5f3c83bf60dcbd4564866ffb5f819844`; the pushed remote branch remains at KVM validation commit `e54d3d2a06b11375cb08f09ebaedb51d6623920f`, so the frozen evidence commit itself is intentionally local pending review.
- Third independent read-only review returned FAIL with P0 `0`, P1 `0`, P2 `1`: its sole finding was stale evidence-freeze and next-action wording in `docs/evidence/M0-05/formal-compatibility.md` and this HandOff; it confirmed all technical and device-evidence closures and required no device rerun.
- Fourth independent read-only review of coordinating HEAD `3ddae22709775d5badb97671c4c2ee3f16d45a5e` returned FAIL with P0 `0`, P1 `0`, P2 `1`: its sole finding was that this HandOff still listed the already-completed documentation commit as a future action; it reconfirmed all technical and device-evidence closures and required no device rerun.
- Fifth independent read-only review of coordinating commit `05c4b0641cbab7819da59189bb363039f4276fe8` returned PASS with P0 `0`, P1 `0`, P2 `0`; it confirmed the fourth-review documentation P2 closed, the three-environment evidence unchanged, live KVM run successful, remote at `e54d3d2a06b11375cb08f09ebaedb51d6623920f`, and no existing PR.
- Complete branch published at `6f9a072a60072d6db83c7a0da8659bb7cd772666`; draft PR [#32](https://github.com/xiaokh31/androidAppHardening/pull/32) is the sole Issue #5 PR. PR runs `30732016374` (API 29/36 KVM), `30732016378` (Ubuntu/Windows Build), and `30732016377` (Ubuntu/Windows Governance) all passed.
- Final PR head `fbcb2d1a89bc46126d987605fed0c44913c7e320` passed KVM run `30732302393`, Build run `30732302394`, and Governance run `30732302403` before merge.
- User authorized ready/merge; PR #32 was merged as `1fe9ea9ca7ac989e2e071ccb00ae2a0c0010c463` and Issue #5 closed.
- Post-merge Governance run `30732622423` reproduced one HandOff-only source-branch mismatch on Ubuntu and Windows; coordinator commit `d682c85125e11084cf023b5f523d715e28c74e75` changed the resume point to `main` and marked M0-05 done. The later `main@e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030` is the verified M1-01 base.
- On `d682c85125e11084cf023b5f523d715e28c74e75`, Governance run `30732725929` and Build run `30732725931` passed on Ubuntu 24.04 and Windows 2025, including strict HandOff on `main` with no exemption.
- 该 M1-01 post-merge 动作已完成；当前恢复点为下述 M1-02 冻结实现与本地验收。

## Verification Evidence

### M1-04 clean restart baseline

- task_id: M1-04
- git_commit: ebbe92830cd5f3a4f3c7a51f058d8d5f6f74912a
- command: verify clean main/origin main; inspect old local branch ancestry and rejected review; verify Issue #9 and absence of remote head PR; archive old branch; create fixed branch from main; Governance and strict HandOff baseline
- exit_code: 0
- environment: Windows 10.0.19045 x64; Node v24.12.0; Git 2.52.0; no device or emulator
- timestamp: 2026-08-06T00:47:22+08:00
- artifact: Issue `https://github.com/xiaokh31/androidAppHardening/issues/9`; `docs/evidence/M1-04/implementation-plan.md`; rejected local branch `spike/m1-04-rejected-ahdc-v1`
- sha256: not_applicable
- result: PASS; M1-01/M1-02/M1-07 dependencies are merged, main is clean, the fixed branch starts exactly at current main, and no v1 implementation commit is in the new branch ancestry

### M1-07 task start and contract blocker

- task_id: M1-07
- git_commit: 225ec169661e2a366736be36b1249fb79faf3dcc
- command: verify clean main/base/remote/branch; create Issue #36; switch to `docs/m1-07-chunk-authenticated-container-contract`; inspect M1-01 limits, ADR 0004/0006/0007 and M1-04 review blocker
- exit_code: 0
- environment: Windows 10.0.19045; PowerShell; GitHub Issue #36; no emulator/device/download
- timestamp: 2026-08-05T13:06:36+08:00
- artifact: `docs/adr/0008-chunk-authenticated-dex-container.md`; `docs/tasks/M1-07-chunk-authenticated-container-contract.md`; `docs/evidence/M1-07/security-review-input.md`
- sha256: not_applicable
- result: IN_PROGRESS; independent governance task is isolated from the rejected M1-04 implementation, and the AHDC v2 contract is being reconciled before validation and review

### M1-07 independent security review 1

- task_id: M1-07
- git_commit: e13927a22f8b008ab6bc419b26b53044a847ef4a
- command: independent offline read-only review; governance validator; strict HandOff; structure/boundary calculations; v1/record-tag and UTF-8 scans; final clean status
- exit_code: 1
- environment: Windows 10.0.19045 x64; PowerShell 5.1; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T13:17:49+08:00
- artifact: `docs/evidence/M1-07/security-review-1.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=1, P2=1; authoritative Runtime text retained whole-record tag semantics and the dependency proof reversed one edge; frozen SHA invalidated pending remediation and full re-review

### M1-07 independent security review 2

- task_id: M1-07
- git_commit: 3380659355981738998d32a3b0f1dabb70a2067d
- command: second independent offline read-only full review; governance/strict/diff/UTF-8 checks; structure, maximum-container, ownership and failure-path analysis
- exit_code: 1
- environment: Windows 10.0.19045 x64; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T13:29:52+08:00
- artifact: `docs/evidence/M1-07/security-review-2.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=2, P2=0; M2-02 Goal retained whole-record wording and unpublished completed/partial DEX mappings lacked transactional cleanup ownership and failure-injection acceptance; timestamp is the verifiable remediation archive commit time, because the original review-completion clock was not preserved

### M1-07 independent security review 3

- task_id: M1-07
- git_commit: e35543804905df0045d22c1d6a06e903384afd93
- command: third independent offline read-only full review; governance/strict/diff/UTF-8 checks; structure, compress-bound, ownership and success/failure lifecycle analysis
- exit_code: 1
- environment: Windows 10.0.19045 x64; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T13:36:03+08:00
- artifact: `docs/evidence/M1-07/security-review-3.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=1, P2=0; ADR verification incorrectly required successful committed DEX mappings to be zeroized/unmapped before their handle/ClassLoader lifecycle ended

### M1-07 independent security review 4

- task_id: M1-07
- git_commit: dd0c4c0811557be09ce2ac2b11afde5d7794b337
- command: fourth independent offline read-only full review; governance/strict/node/diff/UTF-8/whole-record checks; structure, compress-bound, success lifecycle, M3 cleanup evidence and timeline analysis
- exit_code: 1
- environment: Windows 10.0.19045 x64; PowerShell 5.1; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T13:38:26+08:00
- artifact: `docs/evidence/M1-07/security-review-4.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=2, P2=1; success temporary-secret cleanup was not enforceable downstream, M3-02 lacked transaction-cleanup assertions, and review-2/3 timestamp evidence was inconsistent; timestamp is the target archival commit time because the reviewer completion clock was not preserved

### M1-07 independent security review 5

- task_id: M1-07
- git_commit: 340b6ae83f05d89fb20d2d2d7d32ad1b55d65404
- command: fifth independent offline read-only full review; governance/strict/node/diff/UTF-8/whole-record checks; wire, provider, lifecycle, M3 schema and cross-JNI publication-window analysis
- exit_code: 1
- environment: Windows 10.0.19045 x64; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T14:00:51+08:00
- artifact: `docs/evidence/M1-07/security-review-5.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=1, P2=0; Native handle return to public LoadedPayload return lacked an allocation-safe owner, exact-close failure cleanup and injection acceptance

### M1-07 independent security review 6

- task_id: M1-07
- git_commit: bb2e744fce0f64c7f0effd59c99f5bb2882b834c
- command: sixth independent offline read-only full review; governance/strict/node/diff/UTF-8 checks; wire, metadata handoff and end-to-end startup ownership analysis
- exit_code: 1
- environment: Windows 10.0.19045 x64; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T14:05:48+08:00
- artifact: `docs/evidence/M1-07/security-review-6.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=2, P2=0; authenticated ConfigV2/SPV1 metadata lacked a same-handle handoff to M2-03 and Guard ownership stopped before VerifiedPayloadSession publication; timestamp is the verifiable target-commit archive time because the reviewer completion clock was not preserved

### M1-07 independent security review 7

- task_id: M1-07
- git_commit: 3bea66ad1aa89a6cbc97ba093b71235561481d38
- command: seventh independent offline read-only full review; governance/strict/node/diff/UTF-8 checks; exact wire/boundary arithmetic, dependency traversal, authoritative sequence and dual publication-window analysis
- exit_code: 1
- environment: Windows 10.0.19045 x64; PowerShell 5.1.19041.7548; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T14:35:32+08:00
- artifact: `docs/evidence/M1-07/security-review-7.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=1, P2=2; architecture sequence preceded authenticated metadata with configuration exposure/loader creation, and shared acceptance summaries omitted metadata injection and the final Guard publication window

### M1-07 independent security review 8

- task_id: M1-07
- git_commit: 01f76f6c7dfa3a0fad999016c54351329bc56e29
- command: eighth independent offline read-only full review; governance/strict/node/diff/UTF-8 checks; exact wire/zlib arithmetic, dependency traversal, same-handle package metadata and required-review-input analysis
- exit_code: 1
- environment: Windows 10.0.19045 x64; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T14:44:38+08:00
- artifact: `docs/evidence/M1-07/security-review-8.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=1, P2=1; authenticated metadata omitted the package digest required for a mechanical Guard recheck, and mandatory review inputs omitted later transaction/ownership gates

### M1-07 independent security review 9

- task_id: M1-07
- git_commit: d5d5d292600953eb21c4422dee8038288bb19d6a
- command: ninth independent offline read-only full review; governance/strict/node/diff/UTF-8 checks; exact layout/zlib arithmetic, dependency traversal, metadata/loader order and public-accessor analysis
- exit_code: 1
- environment: Windows 10.0.19045; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T14:56:05+08:00
- artifact: `docs/evidence/M1-07/security-review-9.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=1, P2=1; ADR demanded Guard rechecks before loader construction although metadata was exposed only through a LoadedPayload with a loader, and non-binding metadata getter signatures remained underspecified

### M1-07 independent security review 10

- task_id: M1-07
- git_commit: 358a71a9478a0ccb76f71538002184a6a4ea4dc4
- command: tenth independent offline read-only full review; governance/strict/node/diff/UTF-8 checks; exact arithmetic, dependency traversal, metadata getters, loader-use boundary and Guard comparison-source analysis
- exit_code: 1
- environment: Windows 10.0.19045; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T15:04:49+08:00
- artifact: `docs/evidence/M1-07/security-review-10.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=0, P2=1; Guard mismatch tests did not map build/key/version/Factory to executable trusted sources, and Factory has no independent comparison source

### M1-07 independent security review 11

- task_id: M1-07
- git_commit: 9dec7603a860c33ab6bb91f37221e2e81d6011bf
- command: eleventh independent offline read-only full review; governance/strict/node/diff/UTF-8 checks; exact layout/zlib arithmetic, dependency traversal, wire/crypto, transaction, dual ownership, metadata getters, comparison sources and M3 evidence analysis
- exit_code: 0
- environment: Windows 10.0.19045; Node v24.12.0; Git 2.52.0; no network/device/emulator
- timestamp: 2026-08-05T15:12:59+08:00
- artifact: `docs/evidence/M1-07/security-review-11.md`
- sha256: not_applicable
- result: PASS; P0=0, P1=0, P2=0; timestamp is the coordinator receipt time because reviewer completion time was not preserved

### M1-07 draft PR publication

- task_id: M1-07
- git_commit: b094119a33e2fe4b69e23f03a0c7ae05080f3834
- command: validate Governance and strict HandOff; explicit README/HandOff commit; push fixed branch; verify no existing head PR; create the sole Issue #36 draft PR #37
- exit_code: 0
- environment: Windows 10.0.19045 x64; Node v24.12.0; Git 2.52.0; GitHub CLI 2.96.0; no device or emulator
- timestamp: 2026-08-05T23:44:34+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/37`; Issue `https://github.com/xiaokh31/androidAppHardening/issues/36`; branch `docs/m1-07-chunk-authenticated-container-contract`
- sha256: not_applicable
- result: PASS; the branch was published exactly once, PR #37 is draft and uniquely targets main, and the root README now carries the public milestone/task progress rule

### M1-07 draft PR CI evidence

- task_id: M1-07
- git_commit: ceeae8a4a0828b97ad45196d3727fca460c59f91
- command: `gh pr checks 37`; live `gh pr view 37` query for head/base, draft, mergeability, closing Issue and check rollup
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; local coordinator Windows 10.0.19045; no device or emulator
- timestamp: 2026-08-05T23:49:44+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/37`; Build run `31021991586`, jobs `92360817375`/`92360817245`; Governance run `31021992020`, jobs `92360758847`/`92360758705`; closing Issue #36
- sha256: not_applicable
- result: PASS; Ubuntu/Windows Build completed in 1m23s/1m54s and Governance in 13s/39s; PR #37 is OPEN, draft, CLEAN and MERGEABLE at the exact evidence HEAD

### M1-07 final draft HEAD and merge authorization

- task_id: M1-07
- git_commit: 2c13ecc8521f269e6f02fdace77f7f14f546c9cc
- command: fetch current main/head; `gh pr checks 37`; live `gh pr view 37` query for exact head/base, draft, mergeability, closing Issue and check rollup
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; local coordinator Windows 10.0.19045; no device or emulator
- timestamp: 2026-08-06T00:23:15+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/37`; Build run `31022701793`, jobs `92363215927`/`92363216067`; Governance run `31022701584`, jobs `92363215056`/`92363215049`; closing Issue #36
- sha256: not_applicable
- result: PASS; Ubuntu/Windows Build completed in 1m24s/1m37s and Governance in 16s/40s; PR #37 is OPEN, draft, CLEAN and MERGEABLE at exact HEAD, and the user authorized ready/merge

### M1-07 merger-ready CI and merge

- task_id: M1-07
- git_commit: 9ec90fca6b2b293a56a98f3d0c60190b5c0e7a20
- command: `gh pr checks 37`; `gh pr ready 37`; `gh pr merge 37 --merge --match-head-commit d0e0ee61171eb9472bce306619a426da86292f5c`; fast-forward `main`; sync README; Governance, strict HandOff without exemption and `git diff --check`
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; local coordinator Windows 10.0.19045; no device or emulator
- timestamp: 2026-08-06T00:29:36+08:00
- artifact: merged PR `https://github.com/xiaokh31/androidAppHardening/pull/37`; closed Issue `https://github.com/xiaokh31/androidAppHardening/issues/36`; Build run `31025197065`, jobs `92371721867`/`92371721903`; Governance run `31025197119`, jobs `92371722332`/`92371722171`
- sha256: not_applicable
- result: PASS; merger-ready Ubuntu/Windows Build completed in 1m18s/1m49s and Governance in 12s/41s; expected-head ordinary merge produced `9ec90fca6b2b293a56a98f3d0c60190b5c0e7a20`, Issue #36 closed, README was synchronized, and post-merge main passed strict HandOff without exemption

### M1-03 merger-ready CI and merge

- task_id: M1-03
- git_commit: 197eb45535b117e28ad1ef904993d2b54068056b
- command: `gh pr checks 35`; `gh pr ready 35`; `gh pr merge 35 --merge --match-head-commit 07c519c73b2a48f8636eed557da463f699299f20`; fast-forward `main`; strict HandOff, Governance and `git diff --check`
- exit_code: 0
- environment: GitHub Actions API 29/36 x86_64 Linux/KVM; ubuntu-24.04; windows-2025; local coordinator Windows 10.0.19045; no local emulator
- timestamp: 2026-08-04T00:47:36+08:00
- artifact: merged PR #35; closed Issue #8; KVM run `30832968146`; Build run `30832968383`; Governance run `30832968130`
- sha256: not_applicable
- result: PASS; merger-ready API 29 job `91751154201` in 7m57s, API 36 job `91751154144` in 8m59s, Ubuntu/Windows Build in 1m20s/2m38s and Governance in 15s/35s all succeeded; expected-head merge produced `197eb45535b117e28ad1ef904993d2b54068056b`; post-merge main passed strict HandOff without exemption

### M1-03 final replacement PR validation

- task_id: M1-03
- git_commit: 16ffba62df8f25d4397d771c5bdfa77f8dba78ad
- command: `gh pr view 35 --json ...`; inspect KVM run `30790389457`, Build run `30790389453` and Governance run `30790389460`; confirm Issue #8 closing reference and sole head PR
- exit_code: 0
- environment: GitHub Actions API 29/36 x86_64 Linux/KVM; ubuntu-24.04; windows-2025; local coordinator Windows 10.0.19045; no local emulator
- timestamp: 2026-08-03T14:37:58+08:00
- artifact: PR #35 final replacement checks and comment `https://github.com/xiaokh31/androidAppHardening/pull/35#issuecomment-5163132253`
- sha256: not_applicable
- result: PASS; API 29 job `91612470254`, API 36 job `91612470271`, Ubuntu/Windows Build jobs `91612470232`/`91612470258` and Governance jobs `91612470128`/`91612470156` all succeeded; both Build jobs matched all four canonical M1-03 hashes; PR was OPEN, draft, CLEAN and MERGEABLE

### M1-03 second independent review and path-state remediation

- task_id: M1-03
- git_commit: f15088d3b811d7be3827e8abaa0286da28f42f6a
- command: independent offline read-only review of `99877a49c9950a64941858fa3a01d51dbf8c988e`; project-local offline `:host:axml:test`; project-local offline `check verifyGovernance`; `node tools/validation/verify-m0-05-apks.mjs` against both transformed Release/R8 APKs; Governance, Node syntax, report-hash, diff and UTF-8 scans
- exit_code: 0
- environment: Windows 10 amd64; Temurin `17.0.19+10`; Gradle `9.5.0`; Kotlin plugin `2.4.10`; Node `24.12.0`; aapt2 `2.20-14042983`; offline; no emulator or device rerun
- timestamp: 2026-08-03T13:48:56+08:00
- artifact: `docs/evidence/M1-03/security-review-2.md`; `docs/evidence/M1-03/formal-host-validation.md`; transform SHA-256 `35bd420aa0fe05e1a5efee197bdea8d3699f5de743bf54974f13833e24ef5635`; error SHA-256 `9a60c0c9fe710798d7f458822c1f2d6ffb9a22527a43c176c6c3869fb6dcf49c`; fuzz SHA-256 `d1dbf919a489a067506ab40b629916ea66a5b8a3e3ced42e710ae8dc57f8dced`; aapt2 SHA-256 `916e2d79af152c6090fc7ba0c4b9b24f054f0eb094ff2968192b922d0d593672`
- sha256: not_applicable
- result: PASS_WINDOWS_THIRD_REVIEW_CANDIDATE; the second review's P1 is removed by bounded structural flags and an explicit long-name/deep-nesting regression, but this new commit still requires an independent P0/P1/P2-zero review; physical API 29, published KVM and Ubuntu equivalence remain pending

### M1-03 third independent parser/security review

- task_id: M1-03
- git_commit: 1425f911eb48796a8e4ade9aa3c5fcec09cb1f7b
- command: independent project-local offline `:host:axml:test`; independent project-local offline `check verifyGovernance`; strict HandOff; two Node syntax checks; production ZIP/signing capability scan; base-to-HEAD diff check
- exit_code: 0
- environment: Windows 10 amd64; Temurin `17.0.19+10`; Gradle `9.5.0`; Node `24.12.0`; aapt2 `2.20-14042983`; offline; no network, device or emulator
- timestamp: 2026-08-03T13:54:07+08:00
- artifact: `docs/evidence/M1-03/security-review-3.md`; transform SHA-256 `35bd420aa0fe05e1a5efee197bdea8d3699f5de743bf54974f13833e24ef5635`; error SHA-256 `9a60c0c9fe710798d7f458822c1f2d6ffb9a22527a43c176c6c3869fb6dcf49c`; fuzz SHA-256 `d1dbf919a489a067506ab40b629916ea66a5b8a3e3ced42e710ae8dc57f8dced`; aapt2 SHA-256 `916e2d79af152c6090fc7ba0c4b9b24f054f0eb094ff2968192b922d0d593672`
- sha256: not_applicable
- result: PASS; P0 `0`, P1 `0`, P2 `0`; the full-path memory issue and all first-review findings are closed, so the local implementation/review gate is complete; physical API 29 and publication-dependent KVM/dual-platform CI remain pending

### M1-03 API 29 arm64 physical-device acceptance

- task_id: M1-03
- git_commit: df924ed9a10c29680c033d9200a7f5581fda0d1f
- command: `node tools/validation/run-m0-05-device-acceptance.mjs --platform arm64-api29-physical-m1-03 --cold-starts 20 --command-timeout-ms 60000` against the transformed extracted/direct Release/R8 APKs; independent post-run `pm path` and `pidof` cleanup check
- exit_code: 0
- environment: Android API 29 arm64-v8a; 64-bit `user/release-keys`; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000 non-root; Windows 10 host; no emulator
- timestamp: 2026-08-03T14:08:52+08:00
- artifact: ignored `build/m1-03/device-api29-arm64-review3/`; transformed extracted APK SHA-256 `9c31c54ad001613130b2150937f95d94a75f5bc2cf12bc2af87e8206ac381c18`; transformed direct APK SHA-256 `ece3317e12b420e7afb66ee16f08c42518772818ee6f16b97a52a3f2bb524f64`; report SHA-256 `4f563a49c76ff27bef8033401b47591d8acd45e43c70f2045adc6ff3b57de042`; JUnit SHA-256 `af4bd59eaebe4448c9512129aaf3710d1169718891e59deffdf8d5f900da9f61`; commands SHA-256 `ac2521cbaa67d8865788f1125bec07477eeff3138874487dbf21f5c4e9c47ec6`
- sha256: 4f563a49c76ff27bef8033401b47591d8acd45e43c70f2045adc6ff3b57de042
- result: PASS_DEVICE_EVIDENCE; both variants passed instrumentation, lifecycle, cross-DEX, JNI, signer, metadata independence, 20 cold starts, memory and zero plaintext DEX; extracted P50/P95 `269/294 ms`, peak PSS `50,382 KB`; direct P50/P95 `332/370 ms`, peak PSS `51,943 KB`; runner and independent cleanup checks passed

### M1-03 initial draft PR validation

- task_id: M1-03
- git_commit: c6ed194c2fea9672d6cdd38cf181560e8d76e87f
- command: push fixed branch; create sole Issue #8 draft PR #35; `gh pr checks 35 --watch --interval 30`; inspect runs `30789605156`, `30789605218`, `30789605187`; download KVM artifacts to ignored project-local evidence and verify report hashes
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04, Windows 2025 and Ubuntu Linux/KVM; API 29 r8/API 36 r2 x86_64; Emulator 37.1.11; local Windows coordinator; no local emulator
- timestamp: 2026-08-03T14:24:35+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/35`; KVM run `30789605156`; Build run `30789605218`; Governance run `30789605187`; ignored `build/m1-03/github-run-30789605156/`; API 29 report SHA-256 `0ebd9d4cf89caaec3cfd056de34d93e2f810d6b793c0b6d0a7d6385265b05700`; API 36 report SHA-256 `d00b794661191fac624ffa0be44e9841966a55ba489e61b193d58c07bb36b7b3`
- sha256: not_applicable
- result: PASS; API 29 job `91610180311` in 7m58s, API 36 job `91610180365` in 8m54s, Ubuntu/Windows Build in 3m11s/4m02s and Governance in 14s/41s all succeeded; both Build jobs matched all four canonical M1-03 hashes; PR remained draft, OPEN, CLEAN and MERGEABLE

### M1-02 start baseline

- task_id: M1-02
- git_commit: aebbc441da34d2fba78648415c1d80ea844d774d
- command: `git fetch origin main`; compare local and remote `main`; Governance; strict HandOff without exemption; inspect Issue #7, existing PRs and remote branch; inspect pinned `apksig` catalog and verification metadata
- exit_code: 0
- environment: Windows 10 x64; Git 2.52.0; Node 24.12.0; no APK fixture, device, emulator or new download
- timestamp: 2026-08-02T15:32:13+08:00
- artifact: Issue `https://github.com/xiaokh31/androidAppHardening/issues/7`; `docs/evidence/M1-02/implementation-plan.md`; pinned `com.android.tools.build:apksig:9.3.0` JAR SHA-256 `562cd0a88890960d2ece48e116c61f12872222f1dcc306890799382bc019b201`
- sha256: not_applicable
- result: PASS; M1-01 dependency and post-merge main gates are closed, Issue #7 is open with no PR, the fixed branch was absent before creation, signer/container decisions are already accepted, and M1-02 may proceed without adjacent work

### M1-02 frozen implementation and Windows validation

- task_id: M1-02
- git_commit: 146aac3795a1f92adefbab376939129e55975c65
- command: project-local offline Gradle `:host:apk-inspector:clean :host:apk-inspector:signerPolicyTest`; project-local offline Gradle `clean check verifyGovernance`; governance validator; strict HandOff; diff and strict UTF-8 scans
- exit_code: 0
- environment: Windows 10 x64 10.0.19045; Temurin 17.0.19+10; Gradle 9.5.0; Kotlin plugin 2.4.10; apksig 9.3.0; Build Tools 36.1.0; no device or emulator
- timestamp: 2026-08-02T16:08:29+08:00
- artifact: `docs/evidence/M1-02/formal-host-validation.md`; ignored `host/apk-inspector/build/reports/m1-02/`; canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`; error matrix SHA-256 `ecd2193e7ec38418715cc7ee57023d0aa9ba9923d4001fa8d6d1da71cbea3762`; artifact manifest SHA-256 `187c200809051300e028bfc5270f43fc264c1e62baa414890fa501893d0b4488`; capability scan SHA-256 `97c89653b10a7e7b2fd97b53e7ae2ccc53994d623de2fc7c56852d982adbfcfa`
- sha256: not_applicable
- result: PASS_WINDOWS_REVIEW_CANDIDATE; official signer digest, valid schemes and rotation, unsigned/tampered/malformed/multi-signer/invalid-lineage/input-change failures, SPV1 model constraints, production capability scan, full root regression and governance passed; independent review and published Ubuntu/Windows equivalence remain pending

### M1-02 first independent security review

- task_id: M1-02
- git_commit: 21bfd6db333767c9182c1310e6cd838a8fae49a1
- command: independent offline read-only code and apksig bytecode review; clean signer matrix; root `clean check verifyGovernance`; six-fixture `apksigner` verification; exception and magic-only probes; Governance; strict HandOff; diff and UTF-8 scans
- exit_code: 1
- environment: Windows 10 x64 10.0.19045; Temurin 17.0.19+10; Gradle 9.5.0; apksig 9.3.0; Build Tools 36.1.0; no network, device or emulator
- timestamp: 2026-08-02T16:29:00+08:00
- artifact: `docs/evidence/M1-02/security-review-1.md`
- sha256: not_applicable
- result: FAIL; P0 `0`, P1 `1`, P2 `3`; unbounded Signing Block materialization, raw cause path disclosure, magic-only unsigned misclassification and incomplete official/artifact evidence invalidate the target

### M1-02 review-1 remediation candidate

- task_id: M1-02
- git_commit: 5016cd39426b2d50d1fbedfafee2f24c567e0546
- command: project-local offline Gradle `:host:apk-inspector:clean :host:apk-inspector:signerPolicyTest`; project-local offline Gradle `clean check verifyGovernance`; diff and strict UTF-8 scans
- exit_code: 0
- environment: Windows 10 x64 10.0.19045; Temurin 17.0.19+10; Gradle 9.5.0; Kotlin plugin 2.4.10; apksig 9.3.0; Build Tools 36.1.0; no network, device or emulator
- timestamp: 2026-08-02T16:40:07+08:00
- artifact: `docs/evidence/M1-02/formal-host-validation.md`; ignored `host/apk-inspector/build/reports/m1-02/`; canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`; error matrix SHA-256 `dce3c1a17647a96e93da291033e28c169ad0f5daee5d7544c6555392d66fc7eb`; official cross-check SHA-256 `c63d706f08763819e30c1e682fff87448a999a3ce53a27c7253e35ef9f82e2ba`; artifact manifest SHA-256 `fddc19d2a1ed3068c8ac5cdf8bc44299df0279a927a62da0af33be7cc1a0eab8`
- sha256: not_applicable
- result: PASS_WINDOWS_SECOND_REVIEW_CANDIDATE; all four review-1 findings have implementation and deterministic regression closure, 256-task root regression passes, and the exact target now requires a new independent review

### M1-02 second independent security review

- task_id: M1-02
- git_commit: 8718975255cfbdab4fc2ce29eae67c18f21b62ed
- command: independent offline read-only clean signer and root validation; review-1 closure verification; 43 Signing Block boundary/mutation probes; Governance; strict HandOff; diff and UTF-8 scans
- exit_code: 1
- environment: Windows 10 x64 10.0.19045; Temurin 17.0.19+10; Gradle 9.5.0; apksig 9.3.0; Build Tools 36.1.0; no network, device or emulator
- timestamp: 2026-08-02T16:58:00+08:00
- artifact: `docs/evidence/M1-02/security-review-2.md`
- sha256: not_applicable
- result: FAIL; P0 `0`, P1 `0`, P2 `1`; review-1 findings are closed, but high-bit unsigned size values decoded as negative `Long` and were misclassified as unsigned instead of malformed

### M1-02 review-2 remediation candidate

- task_id: M1-02
- git_commit: 61908507c741865c50aac07763d42c890bf25d4b
- command: project-local offline Gradle `:host:apk-inspector:clean :host:apk-inspector:signerPolicyTest`; project-local offline Gradle `clean check verifyGovernance`; diff and strict UTF-8 scans
- exit_code: 0
- environment: Windows 10 x64 10.0.19045; Temurin 17.0.19+10; Gradle 9.5.0; Kotlin plugin 2.4.10; apksig 9.3.0; Build Tools 36.1.0; no network, device or emulator
- timestamp: 2026-08-02T17:04:11+08:00
- artifact: `docs/evidence/M1-02/formal-host-validation.md`; ignored `host/apk-inspector/build/reports/m1-02/`; canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`; error matrix SHA-256 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`; official cross-check SHA-256 `c63d706f08763819e30c1e682fff87448a999a3ce53a27c7253e35ef9f82e2ba`; artifact manifest SHA-256 `d74287aec49cfd3cb18af55c6119b3ea90689d2f03bc15df8e5e8d04f43eb201`
- sha256: not_applicable
- result: PASS_WINDOWS_THIRD_REVIEW_CANDIDATE; negative decoded sizes are malformed, high-bit fixtures and actual-artifact official status are frozen, and 256-task root regression passes; a third independent review remains mandatory

### M1-02 third independent security review

- task_id: M1-02
- git_commit: 902c20977d787ea9646078bbbe4c3c46bf0041cc
- command: independent project-local offline Gradle `:host:apk-inspector:clean :host:apk-inspector:signerPolicyTest`; independent project-local offline Gradle `clean check verifyGovernance`; `-Xmx256m` ignored Signing Block boundary probes; official `apksigner` cross-check; Governance, strict HandOff, diff and UTF-8 scans
- exit_code: 0
- environment: Windows 10 amd64; Temurin 17.0.19+10; Gradle 9.5.0; Kotlin plugin 2.4.10; apksig 9.3.0; apksigner 0.9; Node 24.12.0; offline; no device or emulator
- timestamp: 2026-08-02T17:17:49+08:00
- artifact: `docs/evidence/M1-02/security-review-3.md`; canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`; error matrix SHA-256 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`; official cross-check SHA-256 `c63d706f08763819e30c1e682fff87448a999a3ce53a27c7253e35ef9f82e2ba`; artifact manifest SHA-256 `d74287aec49cfd3cb18af55c6119b3ea90689d2f03bc15df8e5e8d04f43eb201`; capability scan SHA-256 `97c89653b10a7e7b2fd97b53e7ae2ccc53994d623de2fc7c56852d982adbfcfa`
- sha256: not_applicable
- result: PASS; P0 `0`, P1 `0`, P2 `0`; both historical review rounds are closed, the frozen implementation and local independent-review gate are complete, and publication/Ubuntu-Windows CI remain pending explicit user authorization

### M1-02 PR #34 dependency-lock remediation

- task_id: M1-02
- git_commit: b72ef88003c2dea993afbd7d96d502535833e450
- command: GitHub Actions initial Build run `30752847752` and Governance run `30752847768`; project-local offline Gradle `--write-locks :host:cli:dependencies`; project-local offline Gradle `clean check verifyGovernance`; replacement Build run `30753702741` and Governance run `30753702728`
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025 for initial CI; local Windows 10 amd64, Temurin 17.0.19+10, Gradle 9.5.0, Android SDK 36.1.0; no device or emulator
- timestamp: 2026-08-02T23:12:25+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/34`; `host/cli/gradle.lockfile` SHA-256 `26d344690f11ad00b114bc559337c78b493cce94938ea7ec4f38e20f272de57c`; canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`; error matrix SHA-256 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`
- sha256: 26d344690f11ad00b114bc559337c78b493cce94938ea7ec4f38e20f272de57c
- result: PASS; the initial Build failure was limited to missing downstream lock state; the approved one-line generated lock fix passed the 256-task Windows root regression, replacement Ubuntu/Windows Build and Governance, dependency-verification tamper test, four-ABI gate, and both M1-02 byte-equivalence steps

### M1-02 final PR HEAD and merge authorization

- task_id: M1-02
- git_commit: 2ed5f4f7973c9ff87a3e3cbbf6e4e5325a259418
- command: GitHub Actions Build run `30753889812` and Governance run `30753889774`; live PR #34 head/base, draft, mergeability and check query; live Issue #7 query
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; local Windows 10 amd64 coordinator; no device or emulator
- timestamp: 2026-08-03T11:35:34+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/34`; Issue `https://github.com/xiaokh31/androidAppHardening/issues/7`; Build `https://github.com/xiaokh31/androidAppHardening/actions/runs/30753889812`; Governance `https://github.com/xiaokh31/androidAppHardening/actions/runs/30753889774`; canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`; error matrix SHA-256 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`
- sha256: not_applicable
- result: PASS; final evidence HEAD passed Ubuntu/Windows Build and Governance, both M1-02 byte-equivalence steps passed, PR #34 is CLEAN/MERGEABLE, and the user authorized ready/merge

### M1-02 merger-ready HEAD and merge

- task_id: M1-02
- git_commit: d590b94f08047352d2b1f56c1c08aba4cbf079ec
- command: GitHub Actions Build run `30782245138` and Governance run `30782245141`; mark PR #34 ready; expected-head ordinary merge commit; fast-forward local `main`; `node tools/governance/validate-project-package.mjs`; strict HandOff without exemption; `git diff --check`; live PR and Issue state query
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; Build jobs `91589067454`/`91589067452`; Governance jobs `91589067571`/`91589067573`; Windows 10 amd64 local coordinator; no device or emulator
- timestamp: 2026-08-03T11:40:56+08:00
- artifact: merged PR `https://github.com/xiaokh31/androidAppHardening/pull/34`; closed Issue `https://github.com/xiaokh31/androidAppHardening/issues/7`; Build `https://github.com/xiaokh31/androidAppHardening/actions/runs/30782245138`; Governance `https://github.com/xiaokh31/androidAppHardening/actions/runs/30782245141`; canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`; error matrix SHA-256 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`
- sha256: not_applicable
- result: PASS; merger-ready HEAD passed all four jobs and both M1-02 byte-equivalence steps; PR #34 was merged by expected-head ordinary merge commit, Issue #7 closed, and local `main` passed strict HandOff without exemption

### M1-01 third-review remediation candidate

- task_id: M1-01
- git_commit: e97d67f9fbfc5b4c23751a85822dc6c96af4c6c5
- command: repository-local Gradle 9.5.0 with Temurin 17.0.19, offline `:host:apk-inspector:test` and root `check`; governance validation; clean strict HandOff; `git diff --check`
- exit_code: 0
- environment: Windows 10 x64 10.0.19045; Kotlin JVM plugin 2.4.10; no download, device or local emulator
- timestamp: 2026-08-02T14:23:23+08:00
- artifact: `docs/evidence/M1-01/formal-host-validation.md`; ignored `host/apk-inspector/build/reports/m1-01/`
- sha256: not_applicable
- result: PASS_WINDOWS_THIRD_REMEDIATION_CANDIDATE; canonical model SHA-256 `c15561ee6d6e879ad9db058be2762282538a77d4204279d6b5d6d57b1f1d52bf`; 58-fixture matrix SHA-256 `b396616ff369fa2d4db56c92f6908253339867d71554f96debee4d7ed06a02fc`; same-handle block snapshot binding, restored-byte `INPUT_CHANGED`, BitSet-bounded DEX offsets, fixed-table/data/map closure, complete ELF headers, AXML semantics, every public error code, 10,000 seeded samples, root check and zero extraction passed; a new completed independent review plus Ubuntu equivalence, publication and PR CI remain pending

### M1-01 fourth independent review

- task_id: M1-01
- git_commit: 19ea544ddec32fcaac63dfee81f25546084d8bae
- command: independent read-only implementation and evidence review; repository-local offline root `check`; governance validation; strict HandOff validation; `git diff --check`
- exit_code: 0
- environment: Windows 10 x64 10.0.19045; Temurin 17.0.19+10; Gradle 9.5.0; Kotlin JVM plugin 2.4.10; independent `m1_01_reliability_review_4`; no network, device or local emulator
- timestamp: 2026-08-02T14:33:17+08:00
- artifact: `docs/evidence/M1-01/security-review-4.md`; `docs/evidence/M1-01/formal-host-validation.md`
- sha256: not_applicable
- result: PASS; P0 `0`, P1 `0`, P2 `0`; canonical model SHA-256 `c15561ee6d6e879ad9db058be2762282538a77d4204279d6b5d6d57b1f1d52bf`; 58-fixture error-matrix SHA-256 `b396616ff369fa2d4db56c92f6908253339867d71554f96debee4d7ed06a02fc`; same-handle input snapshot, bounded DEX memory, complete ELF headers, DEX fixed-table/data/map closure and earlier AXML/DEX findings are closed; branch remains unpublished pending explicit publication authority and dual-platform PR CI

### M1-01 draft PR first CI

- task_id: M1-01
- git_commit: 83faffb17b41efc7cd9c81cff6759ddf2208a135
- command: GitHub Actions Build run `30736757259` and Governance run `30736757261`; inspect both Build job logs for the frozen canonical model and error-matrix SHA-256 values
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; Build jobs `91466820734`/`91466820755`; Governance jobs `91466820833`/`91466820830`; no device or local emulator
- timestamp: 2026-08-02T14:58:28+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/33`; Build `https://github.com/xiaokh31/androidAppHardening/actions/runs/30736757259`; Governance `https://github.com/xiaokh31/androidAppHardening/actions/runs/30736757261`
- sha256: not_applicable
- result: PASS; Ubuntu and Windows Build plus Governance all succeeded; both explicit byte-equivalence steps passed with canonical model SHA-256 `c15561ee6d6e879ad9db058be2762282538a77d4204279d6b5d6d57b1f1d52bf` and 58-fixture error-matrix SHA-256 `b396616ff369fa2d4db56c92f6908253339867d71554f96debee4d7ed06a02fc`; PR remains draft

### M1-01 evidence HEAD final CI

- task_id: M1-01
- git_commit: de4d69a6c178de97da1e7700948d8d1db5a4ff79
- command: GitHub Actions Build run `30736945439` and Governance run `30736945448`; inspect both final Build job logs for frozen report SHA-256 values
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; Build jobs `91467352371`/`91467352342`; Governance jobs `91467352594`/`91467352631`; no device or local emulator
- timestamp: 2026-08-02T15:14:13+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/33`; Build `https://github.com/xiaokh31/androidAppHardening/actions/runs/30736945439`; Governance `https://github.com/xiaokh31/androidAppHardening/actions/runs/30736945448`
- sha256: not_applicable
- result: PASS; final evidence HEAD passed Ubuntu/Windows Build and Governance; both byte-equivalence steps passed with canonical model SHA-256 `c15561ee6d6e879ad9db058be2762282538a77d4204279d6b5d6d57b1f1d52bf` and 58-fixture error-matrix SHA-256 `b396616ff369fa2d4db56c92f6908253339867d71554f96debee4d7ed06a02fc`; user authorized ready/merge

### M1-01 merger-ready HEAD and merge

- task_id: M1-01
- git_commit: 74c5f6252ea9b89154c285764d5f9601a0347358
- command: GitHub Actions Build run `30737456598` and Governance run `30737456604`; mark PR #33 ready; ordinary merge commit; `git pull --ff-only origin main`; `node tools/governance/validate-project-package.mjs`; strict HandOff without exemption; `git diff --check`; live PR and Issue state query
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; Build jobs `91468754251`/`91468754228`; Governance jobs `91468754252`/`91468754234`; Windows 10 x64 local coordinator; no device or local emulator
- timestamp: 2026-08-02T15:20:47+08:00
- artifact: merged PR `https://github.com/xiaokh31/androidAppHardening/pull/33`; closed Issue `https://github.com/xiaokh31/androidAppHardening/issues/6`; Build `https://github.com/xiaokh31/androidAppHardening/actions/runs/30737456598`; Governance `https://github.com/xiaokh31/androidAppHardening/actions/runs/30737456604`
- sha256: not_applicable
- result: PASS; merger-ready HEAD `7a54ba7874fb47aaf749715be2ba5897ef5a6b2e` passed all four jobs and both byte-equivalence steps; PR #33 was merged by ordinary merge commit `74c5f6252ea9b89154c285764d5f9601a0347358`, Issue #6 closed, and local `main` passed strict HandOff without exemption

### M0-05 second independent review and remediation candidate

- task_id: M0-05
- git_commit: 189a04c5286187ae61575d3a9ec574d62501eacc
- command: second independent read-only review of `39a30ed1bb5ab80bb13c2ac71968c1599bbb6db4`; local Gradle/check/governance gate; official `apksigner` duplicate-ABI-alias signing and verification probe
- exit_code: 0
- environment: Windows 10 x64 local validation; no local emulator; independent `m0_05_security_review_2`
- timestamp: 2026-08-02T11:02:23+08:00
- artifact: ignored `build/m0-05/native-duplicate-signing-probe/`; second-review result recorded in this handoff
- sha256: not_applicable
- result: REVIEW_FAIL_REMEDIATING; P0 `0`, P1 `0`, P2 `3`; exact duplicate ZIP names cannot pass official `apksig` authentication, so the bounded repair uses a signed case-folded duplicate ABI alias that passes signer/config authentication and must fail with `AAH-P004` before `LOADER_CREATED` or `JNI_LOADED`

### M0-05 review-3 frozen device matrix

- task_id: M0-05
- git_commit: 350d08ee5f3c83bf60dcbd4564866ffb5f819844
- command: GitHub Actions run `30729952586`; local `run-m0-05-device-acceptance.mjs`; separate extracted/direct `run-m0-05-startup-negative.mjs`; post-run package, remote-directory and stay-awake cleanup checks
- exit_code: 0
- environment: GitHub Linux/KVM API 29 r8 and API 36 r2 x86_64 with Emulator 37.1.11; physical Android API 29 arm64-v8a user/release-keys, adb shell uid 2000 non-root; no local emulator
- timestamp: 2026-08-02T11:50:38+08:00
- artifact: `docs/evidence/M0-05/formal-compatibility.md`; `https://github.com/xiaokh31/androidAppHardening/actions/runs/30729952586`; ignored `build/m0-05/review3-device-arm64-api29/` and `build/m0-05/github-run-30729952586/`
- sha256: not_applicable
- result: PASS_DEVICE_EVIDENCE; all six device variants passed lifecycle, cross-DEX, JNI, signer/config/metadata, authenticated duplicate-ABI rejection, independent 18/18 startup negatives, 20 cold starts, memory, zero plaintext DEX and cleanup; report SHA-256 values are arm64 `a44c64bbb0f9d8c17c0e1fab4b11e5ec0a31b060fda81ff99a330954ab9a312b`, API 29 KVM `57b0b6b53eafbc9f2ce1f2496201918d25cb7ac0989e40c908463cf8c592ce6f`, and API 36 KVM `9e7de9b2bc33fd27cc632d64f8b84a4301fa5a9e9e1bf1dec0c82d8e063721b8`; verifier peak memory was `51,900 / 71,348 / 73,516 KB`; the cross-variant M0-04 baseline delta is correctly scoped as `1,668` bytes and is not attributed solely to the verifier; third review completed with its sole P2 limited to stale documentation state

### M0-05 third independent review

- task_id: M0-05
- git_commit: 1ed514a20524d1014624a932d82843230e7b81d1
- command: independent read-only review of local frozen evidence SHA `350d08ee5f3c83bf60dcbd4564866ffb5f819844`, remote KVM validation SHA `e54d3d2a06b11375cb08f09ebaedb51d6623920f`, three review-3 device environments, repository contracts, and publication state
- exit_code: 0
- environment: independent `m0_05_security_review_3`; no mutation, download, device rerun, or local emulator
- timestamp: 2026-08-02T12:01:42+08:00
- artifact: reviewer structured handoff recorded in the coordinating task and reconciled into `docs/evidence/M0-05/formal-compatibility.md` plus this HandOff
- sha256: not_applicable
- result: REVIEW_FAIL_DOCUMENTATION_ONLY; P0 `0`, P1 `0`, P2 `1`; all technical and device-evidence closures were confirmed, and the sole finding was stale evidence-freeze and ordered-next-action wording; no device rerun is required

### M0-05 fourth independent review

- task_id: M0-05
- git_commit: 3ddae22709775d5badb97671c4c2ee3f16d45a5e
- command: independent read-only review of the documentation reconciliation, frozen evidence SHA `350d08ee5f3c83bf60dcbd4564866ffb5f819844`, remote KVM SHA `e54d3d2a06b11375cb08f09ebaedb51d6623920f`, three review-3 device environments, strict HandOff, governance, GitHub run and PR state
- exit_code: 0
- environment: Windows 10 10.0.19045; Git 2.52.0; Node 24.12.0; GitHub CLI 2.96.0; independent `m0_05_security_review_4`; no mutation, download, device rerun, or local emulator
- timestamp: 2026-08-02T12:05:34+08:00
- artifact: reviewer structured handoff recorded in the coordinating task and reconciled into `docs/evidence/M0-05/formal-compatibility.md` plus this HandOff
- sha256: not_applicable
- result: REVIEW_FAIL_DOCUMENTATION_ONLY; P0 `0`, P1 `0`, P2 `1`; technical and three-environment evidence remained closed, and the sole finding was an already-completed documentation commit still listed as a future action; no device rerun is required

### M0-05 fifth independent review

- task_id: M0-05
- git_commit: 05c4b0641cbab7819da59189bb363039f4276fe8
- command: independent read-only review of current coordination state, frozen evidence SHA `350d08ee5f3c83bf60dcbd4564866ffb5f819844`, remote KVM SHA `e54d3d2a06b11375cb08f09ebaedb51d6623920f`, three review-3 device environments, strict HandOff, governance, GitHub run and PR state
- exit_code: 0
- environment: Windows 10 x64; Git 2.52.0; Node 24.12.0; GitHub CLI 2.96.0; independent `m0_05_security_review_5`; no mutation, download, device rerun, or local emulator
- timestamp: 2026-08-02T12:10:36+08:00
- artifact: reviewer structured handoff recorded in the coordinating task; frozen report SHA-256 values arm64 `a44c64bbb0f9d8c17c0e1fab4b11e5ec0a31b060fda81ff99a330954ab9a312b`, API 29 KVM `57b0b6b53eafbc9f2ce1f2496201918d25cb7ac0989e40c908463cf8c592ce6f`, API 36 KVM `9e7de9b2bc33fd27cc632d64f8b84a4301fa5a9e9e1bf1dec0c82d8e063721b8`
- sha256: not_applicable
- result: PASS; P0 `0`, P1 `0`, P2 `0`; the independent-review gate is closed and publication is authorized by the established workflow

### M0-05 draft PR first CI

- task_id: M0-05
- git_commit: 6f9a072a60072d6db83c7a0da8659bb7cd772666
- command: `gh pr checks 32 --watch --interval 30`; live `gh pr view 32 --json ...`
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04, Windows 2025, and Ubuntu Linux/KVM; local monitor used GitHub CLI 2.96.0; no local emulator
- timestamp: 2026-08-02T12:22:01+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/32`; KVM run `30732016374`, Build run `30732016378`, Governance run `30732016377`
- sha256: not_applicable
- result: PASS; API 29 KVM job `91453957462` in 6m51s, API 36 KVM job `91453957410` in 7m52s, Ubuntu/Windows Build and Ubuntu/Windows Governance all succeeded; PR remained draft and merge state was CLEAN

### M0-05 merge

- task_id: M0-05
- git_commit: 1fe9ea9ca7ac989e2e071ccb00ae2a0c0010c463
- command: `gh pr ready 32`; `gh pr merge 32 --merge`; `gh pr view 32 --json ...`; `git pull --ff-only origin main`; post-merge strict HandOff and Governance log inspection
- exit_code: 0
- environment: GitHub; Windows 10 x64 local coordinator; Git 2.52.0; Node 24.12.0; GitHub CLI 2.96.0; no local emulator
- timestamp: 2026-08-02T12:35:53+08:00
- artifact: merged PR `https://github.com/xiaokh31/androidAppHardening/pull/32`; closed Issue `https://github.com/xiaokh31/androidAppHardening/issues/5`; merge commit `1fe9ea9ca7ac989e2e071ccb00ae2a0c0010c463`; initial post-merge Governance run `30732622423`
- sha256: not_applicable
- result: MERGED; PR final head passed all six checks before merge; initial main Governance failure was limited to the stale HandOff source branch and is reconciled by this coordinator-only update

### M0-05 post-merge main validation

- task_id: M0-05
- git_commit: d682c85125e11084cf023b5f523d715e28c74e75
- command: `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`; `node tools/governance/validate-project-package.mjs`; `git diff --check`; GitHub Actions Build and Governance push workflows
- exit_code: 0
- environment: Windows 10 x64 local coordinator; GitHub Actions Ubuntu 24.04 and Windows 2025; Node 24.12.0 locally; no local emulator
- timestamp: 2026-08-02T12:42:09+08:00
- artifact: Governance run `https://github.com/xiaokh31/androidAppHardening/actions/runs/30732725929`; Build run `https://github.com/xiaokh31/androidAppHardening/actions/runs/30732725931`
- sha256: not_applicable
- result: PASS; strict HandOff on `main` passed without exemption on both platforms; Governance jobs `91455809943` and `91455809963`, Build jobs `91455816635` and `91455816728` all succeeded

### M0-04 completed dependency

- task_id: M0-04
- git_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
- command: `gradlew.bat --offline --no-daemon :fixtures:android:connectedClassloaderPocDebugAndroidTest`; `node tools/validation/run-m0-04-cold-start.mjs`; `node tools/validation/run-m0-04-tamper-start.mjs`; independent read-only review
- exit_code: 0
- environment: Windows 10 x64; Emulator 37.1.11; API 29 revision 8 and API 36 revision 2 x86_64 non-root AVDs; independent `m0_04_security_review`
- timestamp: 2026-07-31T15:06:44+08:00
- artifact: `docs/evidence/M0-04/formal-api29-api36.md`
- sha256: 57ed7fda2539a8053ea7e361b1db51950dc0096305ae2c514780cc9ec6edef0b
- result: PASS; both devices passed instrumentation, cold starts, tamper matrices and independent review

### M0-06 merged dependency

- task_id: M0-06
- git_commit: f1362188be5083a6d557522f0f5be1905935f6eb
- command: `gh pr merge 31 --merge`; `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`; GitHub Actions Governance and Build
- exit_code: 0
- environment: Windows 10 x64 local strict validation; GitHub Actions Ubuntu 24.04 and Windows 2025
- timestamp: 2026-08-01T14:46:00+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/pull/31`
- sha256: not_applicable
- result: PASS; normal merge commit, post-merge no-exemption HandOff and four main CI jobs succeeded

### M0-05 previous implementation snapshot

- task_id: M0-05
- git_commit: d58a277681443a5e79b770a3e9162ae54006138d
- command: `gradlew.bat --offline --no-daemon --no-configuration-cache` for four M0-05 assemble tasks; bootstrap and fixture check/lint; static APK, apksigner and zipalign verification
- exit_code: 0
- environment: Windows 10 x64; Temurin 17.0.19+10; Gradle 9.5.0; Build Tools 36.1.0; NDK 29.0.14206865; CMake 4.1.2; Node.js 24.12.0
- timestamp: 2026-08-01T01:19:15+08:00
- artifact: `docs/evidence/M0-05/implementation-snapshot.md`
- sha256: b5341f7e6dbe553139baad9d6e13a510119155a7266fb5ee68202ed0ced8a987
- result: PASS for the superseded static implementation only; current ConfigV2/device acceptance remains pending

### M0-05 previous arm64 blocker reproduction

- task_id: M0-05
- git_commit: 3d716ddc4be513a07be0b5cf2d986529d9e0dc06
- command: build and install extracted Release/R8 fixture; `adb shell am instrument -w`; `aapt2 dump xmltree`
- exit_code: 1
- environment: Windows 10 x64 host; Android API 29 arm64-v8a physical user/release-keys device; adb shell non-root
- timestamp: 2026-08-01T13:17:48+08:00
- artifact: `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`
- sha256: c0695656d20926c0aaa6dbc90d9e2591eb6027e74d9db57409b4934e657b0a75
- result: HISTORICAL BLOCKER; early signer passed and metadata was null; M0-06 replaced that contract, so this is regression context rather than current acceptance

### M0-05 ConfigV2 implementation and local gate

- task_id: M0-05
- git_commit: 71d3f9519b5e304346814f33b58b5bf97adeb440
- command: `gradle --no-daemon :runtime:bootstrap:check :fixtures:android:check :tools:validation:check verifyGovernance`; two-pass signer build for extracted/direct Release/R8 and AndroidTest; `node tools/validation/verify-m0-05-apks.mjs ...`; `apksigner verify`; `zipalign -c -P 16 4`
- exit_code: 0
- environment: Windows 10 x64; project-local Temurin 17.0.19+10 and Gradle 9.5.0; Android build-tools/NDK/CMake from the pinned existing SDK; Node.js 24; no local emulator
- timestamp: 2026-08-01T15:47:01+08:00
- artifact: ignored `build/m0-05/`; committed evidence pending device matrix
- sha256: not_applicable
- result: PASS for local compile/check/governance, ConfigV2 20-case parser test, Release/R8 structure, signer cross-binding, APK signature, alignment, R8 removal and signed malformed-APK generation; ignored artifact SHA-256 values are extracted `315f3b84f7fb32ffd5aa6c384b07dad9934594d37e39f532cf177daf7a02c499`, direct `152eec34ebc05753a7c9c94cc0cf8ddb65d57d1c820266d268524c30dc86c471`, ConfigV2 `a9a58af1463d7d9adf59674e775ce38a3cf2c691adbf052cfa61d8219659636e`; device PASS remains pending

### M0-05 API 29 arm64 formal acceptance

- task_id: M0-05
- git_commit: 0d8e6f8c13ac871c840fe134d83d1bfc0b69d3a9
- command: `node tools/validation/run-m0-05-device-acceptance.mjs --serial <redacted> --platform arm64-api29-physical --cold-starts 20 --negative-signed-dir <ignored> --negative-unsigned-dir <ignored> ...`
- exit_code: 0
- environment: Android API 29; arm64-v8a; user/release-keys; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000; serial omitted
- timestamp: 2026-08-01T22:44:17+08:00
- artifact: ignored `build/m0-05/device-arm64-api29-physical/report.json`; committed summary `docs/evidence/M0-05/formal-compatibility.md`
- sha256: 833ae034e7c99389a398bce2acdd24b17bb300f98374292c7da5988c9496731f
- result: PASS; extracted/direct instrumentation, lifecycle/factory, cross-DEX, JNI, signer/config/metadata, 17 external startup negatives, no-factory semantics, 20 cold starts each, memory collection, zero plaintext DEX and cleanup all passed; redacted command log SHA-256 is `2c0ab50114aefc8ebe16f9eab6c5f81c530a22ae547ded5db41796d06d08166d`

### M0-05 API 29/36 x86_64 Linux/KVM formal acceptance

- task_id: M0-05
- git_commit: f63a7192eb6e1055a7647d27850ece262c59210a
- command: GitHub Actions workflow `.github/workflows/m0-05-linux-kvm.yml`; project-local pinned API 29 r8/API 36 r2 x86_64 images and Emulator 37.1.11; `run-m0-05-device-acceptance.mjs`; `run-m0-05-startup-negative.mjs`
- exit_code: 0
- environment: GitHub Linux/KVM; API 29 x86_64 and API 36 x86_64; 64-bit; adb shell non-root; local emulator use none
- timestamp: 2026-08-01T23:48:49+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30706455270`; committed summary `docs/evidence/M0-05/formal-compatibility.md`; raw artifacts downloaded under ignored `build/m0-05/github-run-30706455270/`
- sha256: not_applicable
- result: PASS; both jobs succeeded, both variants passed instrumentation/lifecycle/cross-DEX/JNI/signer/config/metadata, each variant completed 20 cold starts and memory collection, independent startup negatives were 17/17, plaintext DEX count was zero, no-factory semantics and cleanup passed; API 29 report SHA-256 is `ceb1a572b149260bbb7c7b3fac808f73bf3f6ffb96dc2448262c41e6dd6f4519`, API 36 report SHA-256 is `ce5ffc1815a671b21a8e11fe978cd84eb821a2edfa17813fe7fd1f01e3b65a6f`

### M0-05 repaired API 29/36 x86_64 Linux/KVM acceptance

- task_id: M0-05
- git_commit: 587e7f2c7ab9ba44296891fb3d2668e4bd54998c
- command: GitHub Actions run `30708544925`; pinned API 29 r8/API 36 r2 x86_64 images and Emulator 37.1.11; repaired device and separate extracted/direct startup-negative runners
- exit_code: 0
- environment: GitHub Linux/KVM; API 29 and API 36 x86_64; adb shell non-root; exact `libpulse0=1:16.1+dfsg1-2ubuntu10.1`; local emulator use none
- timestamp: 2026-08-02T00:45:00+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/actions/runs/30708544925`; ignored `build/m0-05/github-run-30708544925/`; committed summary `docs/evidence/M0-05/formal-compatibility.md`
- sha256: not_applicable
- result: PASS; each environment and variant passed instrumentation, lifecycle, cross-DEX, JNI, signer/config/metadata, independent 17/17 startup negatives, 20 cold starts, JUnit, memory, zero plaintext DEX and cleanup; R8 mapping/usage, per-ABI SO and verifier memory evidence archived; API 29 report SHA-256 `a2333cc0539330331a1db287aa4c4279209ee0b01aa07c78cac6633e90428c50`, API 36 report SHA-256 `da70a5f80d10e8d295b8e1795803adb64803ce0244fb8f967764f43578481983`

### M0-05 repaired API 29 arm64 physical acceptance

- task_id: M0-05
- git_commit: 789d37e9fa321b54ee19bf4af1382e589f2942d4
- command: repaired `run-m0-05-device-acceptance.mjs` with four negative directories; separate extracted/direct `run-m0-05-startup-negative.mjs`; post-run `svc power stayon false` and package/remote-directory cleanup check
- exit_code: 0
- environment: Android API 29 arm64-v8a; 64-bit user/release-keys; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000 non-root; no local emulator
- timestamp: 2026-08-02T10:31:01+08:00
- artifact: ignored `build/m0-05/device-arm64-api29-repaired-20260802/`; committed summary `docs/evidence/M0-05/formal-compatibility.md`
- sha256: e2b154a79f22b900956f4eccdd9c8a450a69a6be340244c031ccf6103aaa94dd
- result: PASS; extracted/direct each passed instrumentation, independent 17/17 startup negatives, component delegate 16-case and native 3-case failures, lifecycle, cross-DEX, JNI, signer/config/metadata, 20 cold starts, memory and zero plaintext DEX; no-factory semantics and cleanup passed; command log SHA-256 `15d700aae1be8f2f9b82839cf1469c0e93dc21f58d47818b613a6cac4d5aa830`, JUnit SHA-256 `04a12c0e60857dac8a41468b79780b036d37df3ed2c2047ed06dc92239edd15d`

### M1-04 frozen implementation and independent review PASS

- task_id: M1-04
- git_commit: 58352c6de732887cf497de2775bc0fa3021f5332
- command: repository-local `:host:container:check --offline --no-daemon --console=plain --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process`; `node tools/validation/verify-ahdc-v2-vector.mjs`; Governance; diff/security/UTF-8 scans; independent read-only full lifecycle review
- exit_code: 0
- environment: Windows 10.0.19045 x64; Eclipse Temurin JDK 17.0.19+10; Gradle 9.5.0; Node v24.12.0; SunJCE; no device or emulator; final repository Java processes 0
- timestamp: 2026-08-06T07:05:18+08:00
- artifact: `docs/evidence/M1-04/local-windows.md`; `docs/evidence/M1-04/security-scan.md`; `docs/evidence/M1-04/security-review-1.md` through `security-review-5.md`; ignored `host/container/build/reports/m1-04/`
- sha256: 3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d
- result: PASS; 13/13 module cases and independent Node consumer passed; independent review P0=0/P1=0/P2=0; input remained read-only, output publication atomic, sensitive cleanup transactional, no plaintext DEX persisted, and no wire/hash drift occurred

### M1-04 draft PR initial CI

- task_id: M1-04
- git_commit: ab24d32a45912cac4e0b938d4720f49bf2d79fb0
- command: push `feat/m1-04-encrypted-dex-container`; create unique draft PR #38 with `Closes #9`; GitHub Actions Build and Governance matrices on Ubuntu 24.04 and Windows 2025
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; no local device or emulator
- timestamp: 2026-08-06T08:23:06+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/pull/38`; Build run `31059253829`; Governance run `31059253798`
- sha256: 3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d
- result: PASS; Ubuntu/Windows Build and Governance all passed, including the fixed M1-04 AHDC v2 byte-identity gate; PR remains OPEN/draft and no ready/merge authority has been granted

### M1-04 final draft HEAD and merge authorization

- task_id: M1-04
- git_commit: 4af2e4413f9277355b3a1ecd4a2c3a3e40401843
- command: fetch current main/head; `gh pr checks 38`; live PR/Issue uniqueness, exact head/base, draft, mergeability, closing Issue and check-rollup queries
- exit_code: 0
- environment: GitHub Actions API 29/36 x86_64 Linux/KVM, Ubuntu 24.04 and Windows 2025; local coordinator Windows 10.0.19045; no local device or emulator
- timestamp: 2026-08-06T08:38:09+08:00
- artifact: draft PR `https://github.com/xiaokh31/androidAppHardening/pull/38`; Issue `https://github.com/xiaokh31/androidAppHardening/issues/9`; KVM run `31059674092`, jobs `92484710984`/`92484711009`; Build run `31059674083`, jobs `92484651903`/`92484651965`; Governance run `31059674088`, jobs `92484651991`/`92484651949`
- sha256: 3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d
- result: PASS; all six checks succeeded at the exact final draft HEAD, both M1-04 byte-identity steps passed, PR #38 is the sole OPEN/draft/CLEAN/MERGEABLE PR for the fixed head and closes Issue #9, and the user authorized ready/merge

### M1-04 merger-ready CI and merge

- task_id: M1-04
- git_commit: f908861cbb61e79e7c3127fd5216d4a6f8c6e3e1
- command: `gh pr checks 38`; `gh pr ready 38`; `gh pr merge 38 --merge --match-head-commit 65ae18e62f80fe856a3f23c1663d51193c9d2061`; verify PR/Issue state; fast-forward local `main`
- exit_code: 0
- environment: GitHub Actions API 29/36 x86_64 Linux/KVM, Ubuntu 24.04 and Windows 2025; local coordinator Windows 10.0.19045; no local device or emulator
- timestamp: 2026-08-06T08:50:33+08:00
- artifact: merged PR `https://github.com/xiaokh31/androidAppHardening/pull/38`; closed Issue `https://github.com/xiaokh31/androidAppHardening/issues/9`; KVM run `31060409306`, jobs `92486945060`/`92486945058`; Build run `31060409389`, jobs `92486945705`/`92486945742`; Governance run `31060409341`, jobs `92486945333`/`92486945261`
- sha256: 3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d
- result: PASS; merger-ready six-check matrix and both M1-04 byte-identity gates succeeded, expected-head ordinary merge produced `f908861cbb61e79e7c3127fd5216d4a6f8c6e3e1`, Issue #9 closed, and local main fast-forwarded cleanly; post-merge main gates remain pending

### M1-04 post-merge main validation

- task_id: M1-04
- git_commit: 9f074db7222fc76442aa8fa7d44ea29091d7bdfa
- command: update post-merge README/HandOff candidate; strict HandOff without branch exemption; Governance and diff check; push `main`; GitHub Actions Build and Governance matrices
- exit_code: 0
- environment: GitHub Actions Ubuntu 24.04 and Windows 2025; local coordinator Windows 10.0.19045; no local device or emulator
- timestamp: 2026-08-06T08:57:25+08:00
- artifact: Build run `31061052744`, Ubuntu job `92488933441`, Windows job `92488933387`; Governance run `31061052957`, Ubuntu job `92488916292`, Windows job `92488916178`
- sha256: 3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d
- result: PASS; Ubuntu/Windows Build and Governance succeeded, both M1-04 byte-identical AHDC v2 gates passed, and local main passed strict HandOff with no branch exemption; M1-04 is complete

### M1-05 start baseline

- task_id: M1-05
- git_commit: d32abe1d68d41910d72c90c3f9fc3d2831972756
- command: verify clean `main` and `origin/main`; inspect M1-05 task/dependencies and accepted ADRs; query Issue #10, fixed remote branch, existing PRs, and latest main Build/Governance; create fixed task branch
- exit_code: 0
- environment: Windows 10.0.19045 x64; Git 2.52.0; Node 24.12.0; GitHub CLI 2.96.0; no device, emulator, or download
- timestamp: 2026-08-06T09:17:40+08:00
- artifact: Issue `https://github.com/xiaokh31/androidAppHardening/issues/10`; `docs/evidence/M1-05/implementation-plan.md`; Build run `31061447875`; Governance run `31061447770`
- sha256: not_applicable
- result: IN_PROGRESS; M1-02/M1-03/M1-04 and main gates are closed, Issue #10 is the sole OPEN tracker with no branch/PR collision, and M1-05 is isolated to its fixed Host branch and pre-cli scope

### M1-05 local implementation validation

- task_id: M1-05
- git_commit: 8f07e686c414d86b19740e71cf8d51e4e4e49fc3
- command: clean `:host:repacker:test`; pinned `aapt2 dump xmltree`, `zipalign -c -P 16 -v 4`, and unsigned `apksigner verify`; repository `check verifyGovernance`; deterministic report hash gates; diff and security scan
- exit_code: 0
- environment: Windows 10.0.19045 x64; Eclipse Temurin 17.0.19; Gradle 9.5.0; Build Tools 36.1.0; AAPT2 2.20-14042983; apksigner 0.9; no device, emulator, or download
- timestamp: 2026-08-06T09:46:44+08:00
- artifact: `docs/evidence/M1-05/local-windows.md`; `docs/evidence/M1-05/security-scan.md`; ignored `host/repacker/build/reports/m1-05/output-unsigned.apk`; five deterministic reports
- sha256: 573ddd2ad869284427cfbe0c93af2fd226debc462a082863dec40a54b6c1dcb6
- result: PASS; four ABI policies, raw compressed preservation, fixed entry and Runtime bindings, no plaintext business DEX, unsigned state, alignment, input immutability, and eleven fault/alias/cleanup outcomes passed; independent review remains pending

### M1-05 independent security review 1

- task_id: M1-05
- git_commit: bb748f68ec3cfac255124c6bdfd0bbb242bed1c1
- command: independent full diff review plus repository-local offline `:host:repacker:test`; no network/device/emulator/write operation
- exit_code: 1
- environment: independent read-only reviewer; repository-local Eclipse Temurin 17.0.19 and Gradle 9.5.0
- timestamp: 2026-08-06T10:04:00+08:00
- artifact: `docs/evidence/M1-05/security-review-1.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=4, P2=1; frozen commit invalidated and publication blocked pending transactional cleanup, identity-bound I/O, complete mutation evidence, and exception sanitization

### M1-05 review-1 remediation validation

- task_id: M1-05
- git_commit: c1c1f3006bc57754ba7637653d9c5b1bb1838e93
- command: repository-local offline `gradle clean check verifyGovernance`; pinned `aapt2 dump xmltree`, `zipalign -c -P 16 -v 4`, exact unsigned `apksigner verify`; 23-case failure/TOCTOU/mutation matrix; four-case success/OOM cleanup matrix; diff and security scan
- exit_code: 0
- environment: Windows 10.0.19045 x64; Eclipse Temurin 17.0.19; Gradle 9.5.0; Build Tools 36.1.0; AAPT2 2.20-14042983; apksigner 0.9; no device, emulator, or download
- timestamp: 2026-08-06T10:25:17+08:00
- artifact: `docs/evidence/M1-05/local-windows.md`; `docs/evidence/M1-05/security-scan.md`; `docs/evidence/M1-05/security-review-1.md`; six deterministic reports
- sha256: f7228836595666da63d21c2a230e16eeacce7a2b4e15834ad5cbbd0f37945b1e
- result: PASS; remediation commit c1c1f3006bc57754ba7637653d9c5b1bb1838e93 records the reviewed diff, 268 tasks passed in 1m47s, every targeted candidate mutation failed closed, every repack attempt consumed the one-shot plan, and observed sensitive owners were zeroed on success plus copy/materialization/verifier OOM paths; independent review 2 remains mandatory

### M1-05 independent security review 2

- task_id: M1-05
- git_commit: 55b951269201f37aada6945b13c0716531616b92
- command: independent full diff and publication-boundary review plus repository-local offline `:host:repacker:test`; no network/device/emulator/write operation
- exit_code: 1
- environment: independent read-only reviewer; Windows 10.0.19045 x64; repository-local Eclipse Temurin 17.0.19 and Gradle 9.5.0
- timestamp: 2026-08-06T10:29:00+08:00
- artifact: `docs/evidence/M1-05/security-review-2.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=3, P2=1; frozen commit invalidated pending transactional coverage of plan/verifier copies, publication as the final fallible step, fail-closed candidate identity plus native no-clobber publication, and complete gap/identity/output-race evidence; timestamp is the verifiable frozen commit time because the reviewer completion clock was not preserved

### M1-05 review-2 remediation validation

- task_id: M1-05
- git_commit: f99c7d05f2a70aa9b076a2d1baadfce5a931f036
- command: repository-local offline `gradle clean check verifyGovernance`; pinned `aapt2 dump xmltree`, `zipalign -c -P 16 -v 4`, exact unsigned `apksigner verify`; 28-case failure/TOCTOU/mutation matrix; six-case success/OOM cleanup matrix; diff and security scan
- exit_code: 0
- environment: Windows 10.0.19045 x64; Eclipse Temurin 17.0.19; Gradle 9.5.0; Build Tools 36.1.0; AAPT2 2.20-14042983; apksigner 0.9; no device, emulator, network, or download
- timestamp: 2026-08-06T11:03:35+08:00
- artifact: `docs/evidence/M1-05/local-windows.md`; `docs/evidence/M1-05/security-scan.md`; `docs/evidence/M1-05/security-review-2.md`; six deterministic reports
- sha256: ce8634eb84bd870e13b5146ba2e4a1477649cec85dc0d0abfab4e7afab471eb2
- result: PASS; remediation commit f99c7d05f2a70aa9b076a2d1baadfce5a931f036 records the exact reviewed diff; 268 tasks passed in 1m42s, native Windows no-replace publication and real parent/output races executed, all 28 failures retained fail-closed semantics, and all six sensitive-owner cleanup probes passed; independent review 3 remains mandatory

### M1-05 independent security review 3

- task_id: M1-05
- git_commit: 1febc2da91d62ba3163cdab022955c51be88759a
- command: independent full diff/dependency/publication review; repository-local offline `:host:repacker:test`; Governance, strict HandOff, diff, UTF-8, sensitive-data, and deterministic report-hash checks
- exit_code: 1
- environment: Windows 10.0.19045 amd64; Eclipse Temurin 17.0.19; Gradle 9.5.0; Node 24.12.0; no network/device/emulator/write operation
- timestamp: 2026-08-06T11:13:11+08:00
- artifact: `docs/evidence/M1-05/security-review-3.md`
- sha256: not_applicable
- result: FAIL; P0=0, P1=1, P2=0; all review-1/2 code findings were confirmed closed, but direct Host distribution of JNA 5.6.0 lacked the mandatory maintained-version and known-vulnerability evidence

### M1-05 review-3 dependency remediation

- task_id: M1-05
- git_commit: af2d850f54eb6555d8880449d99750491ee7f0eb
- command: GitHub official tag and global-advisory API queries; NVD cross-check; upgrade JNA/JNA Platform to 5.19.1; Gradle-generated lock and SHA-256 verification metadata; repository-local offline `:host:repacker:test`
- exit_code: 0
- environment: Windows 10.0.19045 amd64; Eclipse Temurin 17.0.19; Gradle 9.5.0; project-local D-drive `GRADLE_USER_HOME`; no C-drive download, device, or emulator
- timestamp: 2026-08-06T11:22:43+08:00
- artifact: `docs/evidence/M1-05/dependency-security-review.md`; JNA tag `5.19.1` commit `1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d`; JAR/POM hashes in `gradle/verification-metadata.xml`
- sha256: eabc8c5bdc159f0e3e158236f278ef76bfbc79505bc2fbce0b972a82105e2fb8
- result: PASS; remediation commit af2d850f54eb6555d8880449d99750491ee7f0eb records the exact dependency and evidence diff; official JNA 5.19.1 release resolved from Maven Central, both exact Maven package advisory queries returned zero records, the cited CVE was independently shown unrelated to JNA, the Windows production JNA module test passed in 38s, and the 268-task clean root validation passed in 1m46s with unchanged deterministic report hashes; fourth independent review remains mandatory

### M1-05 independent security review 4

- task_id: M1-05
- git_commit: 5b8163f7c1db15951e4eaf55399cc8e54f4224af
- command: independent full code, publication, dependency, provenance, lock and verification-metadata review; offline `:host:repacker:test`; Host runtime `dependencyInsight`; Governance, strict HandOff, diff, UTF-8, sensitive-data and six deterministic hash checks
- exit_code: 0
- environment: Windows 10.0.19045 amd64; Eclipse Temurin 17.0.19+10; Gradle 9.5.0; Node 24.12.0; no network/device/emulator/write/Git mutation
- timestamp: 2026-08-06T11:28:36+08:00
- artifact: `docs/evidence/M1-05/security-review-4.md`; `docs/evidence/M1-05/dependency-security-review.md`
- sha256: not_applicable
- result: PASS; P0=0, P1=0, P2=0; JNA 5.19.1 runtime locks and four artifact hashes, old 5.6 build-only boundary, native publication, sensitive cleanup, identity/gap/race coverage, unsigned output and no plaintext business DEX all independently closed

### M1-05 initial PR dual-platform CI

- task_id: M1-05
- git_commit: b3758f8d7beb3f9ce10dd8c6042e52e47137e981
- command: GitHub Actions Build run 31069545834 and Governance run 31069545814 on pull_request for PR #39
- exit_code: 0
- environment: ubuntu-24.04 and windows-2025; Eclipse Temurin 17; Node 24; pinned Android command-line tools and packages
- timestamp: 2026-08-06T11:55:29+08:00
- artifact: https://github.com/xiaokh31/androidAppHardening/actions/runs/31069545834 ; https://github.com/xiaokh31/androidAppHardening/actions/runs/31069545814
- sha256: not_applicable
- result: PASS; Build ubuntu 3m22s, Build windows 4m46s, Governance ubuntu 14s, Governance windows 44s; Linux native no-replace path, four Native ABIs, and deterministic reports passed: entry `1a4caf8b01af9326d3ff3e8c9581d4c4ce40e0f7c5aefa1f8ee63ca0b018e201`, error `cc624a344cb82df3074b46ed39c8776c2bdb2e962e22fe3c46a667adf16da21d`, cleanup `474cd013d51c3b8faec5d25863d31288b4de4af3a095e14c78acb659df4e52b5`, alignment `a9b153f5ad01cbc7df8aa993416fb5d819e05ee5029a89bc5edf38b3d80e4a5b`, ABI `add443496d258e389917d7fabaf1ea7d59b120d7d57b088969bb89976da3f5b8`, external `9723e87adedf97b176ea186baf0309159981e0154fedd25f46841d53f0bde29b`

### M1-05 merger-ready CI and merge

- task_id: M1-05
- git_commit: a239a7ca1c99ed4bf7206f86174f8ca9fa6a17ae
- command: GitHub Actions Build run 31069868900 and Governance run 31069868904; `gh pr ready 39`; expected-head ordinary merge with `--match-head-commit a239a7ca1c99ed4bf7206f86174f8ca9fa6a17ae`; fast-forward local main
- exit_code: 0
- environment: ubuntu-24.04 and windows-2025; GitHub Actions and authenticated GitHub CLI 2.96.0
- timestamp: 2026-08-06T12:01:13+08:00
- artifact: https://github.com/xiaokh31/androidAppHardening/pull/39 ; merge commit `78b44f2d3c94514d8aeb3f851b60318e06eb7391`; Issue #10 closed
- sha256: not_applicable
- result: PASS; exact merger-ready HEAD passed four checks, PR #39 was merged without squash/rebase under expected-head protection, and main post-merge coordination is now in progress

### M1-05 post-merge main validation

- task_id: M1-05
- git_commit: 5756f9c5dd4f4139396bf17db1a74c6e5331c555
- command: local `validate-project-package`; strict `validate-handoff`; diff check; GitHub Actions Build run 31070137444 and Governance run 31070137438 on main push
- exit_code: 0
- environment: local Windows 10.0.19045 amd64 with Node 24.12.0; GitHub ubuntu-24.04 and windows-2025 with pinned JDK/Android/Node toolchain
- timestamp: 2026-08-06T12:08:12+08:00
- artifact: https://github.com/xiaokh31/androidAppHardening/actions/runs/31070137444 ; https://github.com/xiaokh31/androidAppHardening/actions/runs/31070137438 ; `README.md`; `HandOff.md`
- sha256: not_applicable
- result: PASS; Ubuntu/Windows Build and Governance, M1-05 byte-identical reports, Linux native no-replace path, dependency verification failure probe, four Native ABIs, and local no-exemption strict HandOff all passed; M1-05 is complete

### M1-06 Windows full-flow frozen candidate

- task_id: M1-06
- git_commit: e882691c1dbc4958c111c7e33580c3921eff2fc8
- command: repository-local offline `clean check verifyGovernance`; official pinned aapt2 link, apksigner test-fixture sign and unsigned-output verify; strict HandOff and diff/sensitive/UTF-8 checks
- exit_code: 0
- environment: Windows 10.0.19045 x64; Eclipse Temurin 17.0.19+10; Gradle 9.5.0; Android Build Tools 36.1.0; no download/device/emulator
- timestamp: 2026-08-06T23:10:40+08:00
- artifact: `docs/evidence/M1-06/local-windows.md`; REPORT_V1 schema; normalized/error/cleanup/path matrices in ignored `host/cli/build/reports/m1-06/`
- sha256: not_applicable
- result: PASS; final post-review-fix Windows clean root run completed 273 actionable tasks in 2m; actual two-DEX/four-ABI/custom Application/Factory Host pipeline completed, input stayed unchanged, output was verifier-approved and unsigned, REPORT_V1 passed the checked-in Draft 2020-12 validator, and all injected failures including shutdown cleanup removed outputs/workspaces without exposing paths, stacks, DEX or signing capability

### M1-06 frozen read-only review

- task_id: M1-06
- git_commit: e882691c1dbc4958c111c7e33580c3921eff2fc8
- command: frozen diff/security/capability/classpath/artifact review; final offline clean root check; Governance; strict HandOff; diff check
- exit_code: 0
- environment: Windows 10.0.19045 x64; Eclipse Temurin 17.0.19+10; Gradle 9.5.0; Android Build Tools 36.1.0; no download/device/emulator
- timestamp: 2026-08-06T23:22:00+08:00
- artifact: `docs/evidence/M1-06/read-only-review.md`
- sha256: not_applicable
- result: PASS; P0=0, P1=0, P2=0; rejected freeze 7d9072e findings are closed, production JAR/capability boundaries are clean, final 273-task root gate and all frozen report hashes passed; Ubuntu byte identity remains mandatory PR CI

### M1-06 initial pull request CI

- task_id: M1-06
- git_commit: 5a3981ced2c1f889ece284684b9167c34bae5f99
- command: GitHub Actions Build run 31115781825 and Governance run 31115781121 on draft PR #40
- exit_code: 0
- environment: GitHub ubuntu-24.04 and windows-2025; pinned JDK 17.0.19+10, Android 36/36.1.0, Node 24.12.0, NDK 29.0.14206865, CMake 4.1.2
- timestamp: 2026-08-06T23:31:29+08:00
- artifact: https://github.com/xiaokh31/androidAppHardening/actions/runs/31115781825 ; https://github.com/xiaokh31/androidAppHardening/actions/runs/31115781121 ; https://github.com/xiaokh31/androidAppHardening/pull/40
- sha256: not_applicable
- result: PASS; Ubuntu/Windows Build and Governance all passed; both Build jobs passed M1-06 unit/full-flow and matched normalized 71052641...c213, error 9de958b0...785e, cleanup 03f9f1b8...598, and path ea48b25b...e4a5

### M1-06 merger-ready CI and merge

- task_id: M1-06
- git_commit: 702995748cfd643feb9d75ef0abee9cbced1cb4c
- command: GitHub Actions Build run 31116416406 and Governance run 31116415535; ready transition; expected-head protected normal merge
- exit_code: 0
- environment: GitHub ubuntu-24.04 and windows-2025 with pinned repository toolchain
- timestamp: 2026-08-06T23:39:22+08:00
- artifact: https://github.com/xiaokh31/androidAppHardening/actions/runs/31116416406 ; https://github.com/xiaokh31/androidAppHardening/actions/runs/31116415535 ; https://github.com/xiaokh31/androidAppHardening/pull/40 ; https://github.com/xiaokh31/androidAppHardening/issues/11
- sha256: not_applicable
- result: PASS; exact-head Ubuntu/Windows Build and Governance all passed, both Build jobs matched all four M1-06 hashes, PR #40 merged as d0eb39264f1382469712a4f3c28a7d42ab19d1dd, and Issue #11 closed

## Blockers and Required Approvals

None

## Ordered Next Actions

1. Recheck PR #42 for actual Build and Governance runs without changing its draft state or bypassing checks.
2. When Ubuntu/Windows Host vectors, dependency lock checks, and four-ABI gates pass, archive remote evidence and freeze the exact candidate SHA.
3. Launch an independent read-only M2-07 security review against that exact SHA; remediate and re-freeze on any P0/P1/P2 finding.
4. After a zero-finding review and replacement exact-HEAD CI, mark PR #42 ready, merge with expected-head protection, run final `main` gates, update README/HandOff, then resume `feat/m2-02-native-decrypt-loader`.

## Relevant Files and Artifacts

- `HandOff.md`
- `README.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/tasks/M1-01-untrusted-apk-inspector.md`
- `docs/tasks/M1-02-signer-policy.md`
- `docs/tasks/M1-03-binary-axml-transformer.md`
- `docs/tasks/M1-04-encrypted-dex-container.md`
- `docs/tasks/M1-05-apk-repacker-and-alignment.md`
- `docs/tasks/M1-06-cli-and-json-report.md`
- `docs/evidence/M1-06/implementation-plan.md`
- `docs/evidence/M1-06/read-only-review.md`
- `docs/specs/REPORT_V1.md`
- `host/cli/`
- `docs/evidence/M1-05/implementation-plan.md`
- `host/container/`
- `host/repacker/`
- `docs/specs/AHDC_V2.md`
- `docs/evidence/M1-04/`
- `host/apk-inspector/`
- `host/axml/`
- `docs/adr/0003-api29-public-classloader-hook.md`
- `docs/adr/0006-offline-key-protection-boundary.md`
- `docs/adr/0007-source-dir-startup-configuration.md`
- `docs/adr/0008-chunk-authenticated-dex-container.md`
- `docs/tasks/M1-07-chunk-authenticated-container-contract.md`
- `docs/evidence/M1-07/security-review-input.md`
- `docs/evidence/M0-05/implementation-snapshot.md`
- `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`
- `docs/evidence/M0-05/formal-compatibility.md`
- `docs/evidence/M0-05/security-review-1.md`
- `docs/evidence/M1-01/security-review-2.md`
- `docs/evidence/M1-01/security-review-3.md`
- `docs/evidence/M1-01/security-review-4.md`
- `docs/evidence/M1-02/implementation-plan.md`
- `docs/evidence/M1-02/formal-host-validation.md`
- `docs/evidence/M1-02/security-review-1.md`
- `docs/evidence/M1-02/security-review-2.md`
- `docs/evidence/M1-02/security-review-3.md`
- `docs/evidence/M1-03/implementation-plan.md`
- `docs/evidence/M1-03/formal-host-validation.md`
- `docs/evidence/M1-03/security-review-1.md`
- `docs/evidence/M1-03/security-review-2.md`
- `docs/evidence/M1-03/security-review-3.md`
- ignored `build/m1-03/device-api29-arm64-review3/`
- ignored `build/m1-03/github-run-30789605156/`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `fixtures/android/src/androidTestCompatFixture/java/ah/fixtures/android/CompatibilityPocRunner.java`
- `tools/validation/verify-m0-05-apks.mjs`
- `tools/validation/create-m0-05-test-apks.mjs`
- `tools/validation/run-m0-05-device-acceptance.mjs`
- `tools/validation/create-m1-03-device-apk.mjs`
- `tools/validation/run-m0-05-startup-negative.mjs`
- `tools/validation/m0-05-linux-kvm-packages.json`
- `.github/workflows/m0-05-linux-kvm.yml`

## Resume Checklist

- [x] 用户明确启动 M1-06 并预授权推送、唯一 PR、ready 与合并；Issue #11、依赖、无远程冲突和 final main CI 已核验。
- [x] 从 `main@55ef3c5` 创建固定分支，选择 full-flow 模式并归档 CLI/report 实施计划。
- [x] 完成 Host CLI/REPORT_V1、Windows full-flow、失败/清理/敏感能力扫描和双平台规范报告哈希候选。
- [x] 完成 Windows 273-task clean 根回归、Governance、strict HandOff、diff/敏感/UTF-8 检查。
- [x] 冻结 `e882691` 并完成完整只读复核；P0/P1/P2 全零。
- [x] 固定分支已推送，唯一草稿 PR #40 正确关联 Issue #11；初始 Ubuntu/Windows Build/Governance 与固定哈希全绿。
- [x] merger-ready `7029957` 的 exact-head Ubuntu/Windows CI 全绿；PR #40 已普通合并，Issue #11 已关闭。
- [ ] 完成 post-merge main README/HandOff、strict 与最终双平台门禁。
- [x] 用户明确启动 M1-05 并预先授权推送、唯一 PR、ready 与合并；Issue #10、依赖、无远程分支/PR冲突和最新 main 门禁已核验。
- [x] 从 `main@d32abe1` 创建固定分支，归档 `pre-cli` 实现/验收计划并预定独立 reviewer `m1_05_security_review`。
- [x] 实现 repacker/materializer/verifier/native no-replace publisher，完成固定 Android 工具、28 项失败/TOCTOU/mutation、六项清理矩阵和 268-task clean 根回归。
- [x] 首轮 `bb748f6`、第二轮 `55b9512` 与第三轮 `1febc2d` 的 FAIL 均已归档；第四轮对 `5b8163f` 给出 P0/P1/P2 全零，全部代码和依赖发现关闭。
- [x] 固定分支已推送，唯一草稿 PR #39 正确关联关闭 Issue #10；初始 HEAD 的 Ubuntu/Windows Build 与 Governance 四项全绿。
- [x] merger-ready exact HEAD 四项 CI 全绿；PR #39 已 expected-head 普通合并，Issue #10 已关闭。
- [x] post-merge `main@5756f9c` 的 strict、Ubuntu/Windows Build/Governance、M1-05 字节门禁与 README/HandOff 完成状态均已关闭。
- [x] 用户明确启动 M1-04；Issue #9、固定分支、clean main base 与 M1-07 合并门禁已核验。
- [x] 旧 AHDC v1 失败分支已无损归档，新分支不包含其实现提交；`pre-cli` 实现计划已归档。
- [x] 从零实现 AHDC v2 builder/verifier、ConfigV2、descriptor 与一次性 KeyPackagingPlanV2，并完成所有本地验收。
- [x] 冻结 clean 提交 `58352c6` 并取得独立只读复核 P0/P1/P2 全零。
- [x] 用户授权发布；固定分支已推送，关联 Issue #9 的唯一草稿 PR #38 已创建，初始 HEAD 的 Ubuntu/Windows Build 与 Governance 四项全绿。
- [x] 最终草稿 HEAD `4af2e44` 的 API 29/36 KVM、Ubuntu/Windows Build/Governance 六项全绿；PR 为唯一 OPEN/draft/CLEAN/MERGEABLE，用户已授权 ready/merge。
- [x] merger-ready HEAD `65ae18e` 的六项 CI 全绿；PR #38 已以 expected-head 普通 merge commit `f908861` 合并，Issue #9 已关闭。
- [x] post-merge `main@9f074db` 的 Ubuntu/Windows Build、Governance、M1-04 字节一致性和无豁免 strict HandOff 全部 PASS；README/HandOff 已标记 M1-04 完成。
- [x] 用户明确启动 M1-07；Issue #36、固定治理分支和 clean main base 已核验。
- [x] 完成 AHDC v2 全仓库合同同步、字段复算、Governance 与 strict HandOff。
- [x] 冻结治理提交并取得独立只读复核 P0/P1/P2 全零结论。
- [x] 首轮独立复核 FAIL 已归档，`e13927a` 废止；P1 whole-record 措辞和 P2 依赖证明方向已形成修正候选。
- [x] 第二轮独立复核 FAIL 已归档，`3380659` 废止；M2-02 Goal 和未发布 DEX 事务清理两项 P1 已形成修正候选。
- [x] 第三轮独立复核 FAIL 已归档，`e355438` 废止；成功提交与发布前失败的映射清理验收已拆分为互斥路径。
- [x] 第四轮独立复核 FAIL 已归档，`dd0c4c0` 废止；成功提交临时秘密清理、M3-02 事务清理证据和复核时间语义已形成修正候选。
- [x] 第五轮独立复核 FAIL 已归档，`340b6ae` 废止；跨 JNI 公开对象构造窗口的 primitive/finally 所有权和注入矩阵已形成修正候选。
- [x] 第六轮独立复核 FAIL 已归档，`bb2e744` 废止；authenticated metadata 接口与 Guard 最终 session 发布窗口已形成修正候选。
- [x] 第七轮独立复核 FAIL 已归档，`3bea66a` 废止；权威时序、metadata 注入和双窗口验收已形成修正候选。
- [x] 第八轮独立复核 FAIL 已归档，`01f76f6` 废止；package/lineage 复比较接口与完整独立复核输入已形成修正候选。
- [x] 第九轮独立复核 FAIL 已归档，`d5d5d29` 废止；loader 构造/使用边界和完整 metadata getter 合同已形成修正候选。
- [x] 第十轮独立复核 FAIL 已归档，`358a71a` 废止；Guard 比较来源表与真实 Native/parser 验收职责已形成修正候选。
- [x] 第十一次独立复核 PASS 已归档，`9dec760` 的 P0/P1/P2 全零；M1-07 本地合同修订 ready。
- [x] 用户已授权发布 M1-07，并要求每个任务完成时同步根 README 进度。
- [x] 提交 README/HandOff 同步、推送固定分支并创建 Issue #36 的唯一草稿 PR #37；M1-04 在 M1-07 合并前保持 blocked。
- [x] PR #37 证据 HEAD `ceeae8a` 的 Ubuntu/Windows Build 与 Governance 四项 CI 全绿，PR 为 OPEN/draft/CLEAN/MERGEABLE。
- [x] 用户已明确授权将 PR #37 转为 ready 并以普通 merge commit 合并。
- [x] merger-ready HEAD `d0e0ee6` 的四项 CI 全绿，PR #37 已以 expected-head 普通 merge commit `9ec90fc` 合并，Issue #36 已关闭。
- [x] 本地 main 已无豁免通过 Governance、strict HandOff 与 diff check，根 README 已把 M1-07 标记完成；当前无活动任务。
- [x] M1-01 从固定 base `e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030` 与唯一分支 `feat/m1-01-untrusted-apk-inspector` 启动，Issue 固定为 #6；当前恢复点为 `main`。
- [x] M0-04 与 M0-06 已合并并完成各自门禁。
- [x] 完成最新 main 合并并无豁免运行 strict HandOff。
- [x] 建立 M0-05 十三项验收条件到实现、静态测试、设备 runner 与 GitHub KVM workflow 的映射。
- [x] 完成 ConfigV2/sourceDir、Factory/session、JNI、签名后篡改、R8 和落盘扫描的本地实现与静态门禁。
- [x] 解除 MIUI USB 安装限制并完成 arm64 20 次冷启动、内存和负向设备验收。
- [x] 首轮独立复核 FAIL 已归档，六项 P1/P2 修复候选已通过本地门禁。
- [x] 在修复候选上重跑 API 29 arm64 与 API 29/36 Linux/KVM 双变体完整矩阵。
- [x] 纠正设备证据已冻结为 `350d08ee5f3c83bf60dcbd4564866ffb5f819844`；前三次独立复核及其发现均已归档。
- [x] 第四次独立复核 FAIL 已归档；其唯一 P2 是已完成的文档提交仍被列为未来动作，技术与设备证据闭环再次确认。
- [x] 第五次独立 reviewer 已对协调提交 `05c4b0641cbab7819da59189bb363039f4276fe8`、冻结设备证据和远端 KVM SHA 给出 PASS，P0/P1/P2 全为零。
- [x] 完整分支已发布，唯一草稿 PR #32 已创建，发布 HEAD `6f9a072` 的首轮六项 PR CI 全部 PASS。
- [x] merger-ready HandOff 所在最终 PR HEAD `fbcb2d1` 的六项 CI 全部 PASS。
- [x] PR #32 已转 ready 并以 merge commit `1fe9ea9` 合并，Issue #5 已关闭。
- [x] post-merge HandOff 提交 `d682c85` 已在 `main` 的 Ubuntu/Windows Build 与 Governance 全部 PASS，并无豁免通过 strict HandOff。
- [x] M0-05 完成后由用户明确启动 M1-01；M1-02/M1-03/M2 保持未启动。
- [x] 冻结公开模型、限制、稳定错误码和恶意输入测试合同。
- [x] 完成有界 ZIP/AXML/DEX/ELF 检查器、确定性 Windows 验证和修正证据归档；Ubuntu 等价性留给发布后的双平台 PR CI。
- [x] 独立只读 `m1_01_reliability_review_4` 对冻结提交 `19ea544ddec32fcaac63dfee81f25546084d8bae` 给出 P0/P1/P2 全零 PASS。
- [x] 固定分支已发布，Issue #6 的唯一草稿 PR #33 已创建；首轮 Ubuntu/Windows Build、Governance 和两份报告字节一致性门禁全部 PASS。
- [x] 证据提交 `de4d69a` 的最终 Ubuntu/Windows Build、Governance 和两份报告字节一致性门禁全部 PASS；用户已授权 ready/merge。
- [x] merger-ready HEAD `7a54ba7` 的 Ubuntu/Windows Build、Governance 和两份报告字节一致性门禁全部 PASS。
- [x] PR #33 已转 ready，并以普通 merge commit `74c5f62` 合并；Issue #6 已关闭，本地 `main` 已无豁免通过 strict HandOff。
- [x] M1-01 post-merge `main@aebbc44` 的 Ubuntu/Windows Build、Governance、字节一致性和 strict HandOff 全部 PASS。
- [x] 用户明确启动 M1-02；Issue #7、固定分支、base、既有 PR/远端分支缺失状态和 `apksig 9.3.0` 来源锁均已核验。
- [x] ADR 0002/0004 与实现计划已固定 signer policy、无签名能力和 `SPV1` 模型边界；独立复核者预定为 `m1_02_security_review`。
- [x] 完成 M1-02 实现、完整负向矩阵、官方工具交叉验证、Windows 根回归和正式证据候选。
- [x] 首次独立 `m1_02_security_review` FAIL 已归档，P1/P2 四项修复候选 `5016cd3` 已通过 clean signer 与 256-task 根回归。
- [x] 第二次独立复核 FAIL 已归档；唯一高位 size P2 修复候选 `6190850` 已通过 clean signer 与 256-task 根回归。
- [x] 冻结高位边界修复后的正式证据提交并完成第三次独立复核；`902c209` 复核为 P0/P1/P2 全零 PASS。
- [x] 获得用户对固定分支、唯一 Issue #7 草稿 PR 和 Ubuntu/Windows CI 的明确发布授权。
- [x] 推送固定分支并创建关联 Issue #7 的唯一草稿 PR #34；首轮 Governance 双平台 PASS，Build 双平台因 downstream lock 缺口 FAIL。
- [x] 获得用户锁修复授权并完成一行自动生成的 `host:cli` 锁变更与 Windows 256-task 根回归。
- [x] 推送锁修复并完成 PR #34 的替换 Ubuntu/Windows Build、Governance 和 M1-02 字节一致性 CI；四项全绿。
- [x] 推送最终证据提交 `2ed5f4f` 并确认最终 PR HEAD 的 Ubuntu/Windows Build、Governance 与 M1-02 字节门禁全部 PASS。
- [x] 用户已明确授权将 PR #34 转为 ready 并以普通 merge commit 合并。
- [x] merger-ready HEAD `43fd2dd` 的 Ubuntu/Windows Build、Governance 与 M1-02 字节门禁全部 PASS。
- [x] PR #34 已转 ready 并以 expected-head 普通 merge commit `d590b94` 合并；Issue #7 已关闭，本地 `main` 已无豁免通过 strict HandOff。
- [x] 用户明确启动 M1-03；Issue #8、固定分支、base、既有 PR/远端分支缺失状态和 ADR 0003/0007 单属性合同均已核验。
- [x] 完成有界 reader/writer、单属性 semantic diff、18 个稳定错误负例、5,000 样本 fuzz、固定 aapt2 解析、双变体 Release/R8 构造与 Windows 静态验证。
- [x] 首轮独立复核 FAIL 已归档，旧冻结 HEAD `9fee22d` 已废止；P1 `3` / P2 `1` 的修正候选与 6 正例/17 负例已通过模块自测。
- [x] 第二轮独立复核确认首轮四项关闭但以新 P1 废止 `99877a4`；`f15088d` 已移除完整路径持有并通过 6 正例/18 负例、5,000 fuzz、237-task 根回归和静态 APK 验证。
- [x] 第三轮独立只读复核已对 `1425f911` 给出 P0/P1/P2 全零 PASS；本地实现与独立复核门禁关闭。
- [x] 用户允许 MIUI USB 安装后，API 29 arm64 transformed extracted/direct 双变体设备矩阵在 57.5 秒内 PASS，20 次冷启动、内存、零明文 DEX 和最终清理均通过。
- [x] 固定分支已发布并创建 Issue #8 的唯一草稿 PR #35；初始 HEAD `c6ed194` 的 API 29/36 KVM、Ubuntu/Windows Build/Governance 和四报告字节一致性全部 PASS。
- [x] 推送证据提交并完成 replacement PR HEAD `16ffba6` 的六项 CI；两平台四份规范报告 hashes 命中冻结值。
- [x] 用户已明确授权将 PR #35 转为 ready 并以普通 merge commit 合并。
- [x] merger-ready HEAD `07c519c` 的六项 CI 全部 PASS；API 29/36 KVM 完成强制清理。
- [x] PR #35 已转 ready 并以 expected-head 普通 merge commit `197eb45` 合并；Issue #8 已关闭，本地 `main` 已无豁免通过 strict HandOff。
- [x] 对当前 clean 冻结提交完成新的独立 parser/security 复核；P0/P1/P2 全零。

## Handoff Sign-off

- `/root` 已核验 M1-06 冻结 `e882691`、merger-ready `7029957` 的本地/双平台门禁和全零复核；PR #40 已 expected-head 普通合并且 Issue #11 关闭。当前只允许 main 的 README/HandOff、strict/Governance 和最终双平台门禁，synthetic RuntimeBundle 仅限测试，不启动 M2、设备或模拟器。
- `/root` 已核验 M1-05 的唯一 Issue #10、固定分支、依赖和 main 双平台基线；当前只允许 `host/repacker` 与合成 `pre-cli` 验收，不启动 M1-06/M2、设备或本机模拟器。用户已预授权本任务后续发布与合并，但技术门禁和独立复核不得跳过。
- `/root` 已核验 M1-05 本地实现、AHDC v2 重新认证、四 ABI/故障矩阵、固定 Android 工具和 245-task 根回归全部 PASS；当前仅允许冻结并启动独立只读复核，复核全零前不得发布完成或启动 M1-06/M2。
- `/root` 已核验首轮 M1-05 独立复核为 FAIL 并废止 `bb748f6`；当前只允许关闭 P1=4/P2=1、重跑门禁和重新冻结，不得推送、创建 PR 或启动 M1-06/M2。
- `/root` 已核验首轮 M1-05 P1=4/P2=1 的修复 diff、固定 Android 工具、23 项失败/变异/身份矩阵、四项敏感清理矩阵和 268-task clean 回归全部 PASS；当前只允许提交新冻结点并执行第二轮独立只读复核，复核全零前不得推送、创建 PR 或启动 M1-06/M2。
- `/root` 已核验第二轮 M1-05 复核为 FAIL 并废止 `55b9512`；P1=3/P2=1 已由敏感 owner、单缓冲 Runtime materialization、发布前全部校验/close、native no-replace、fail-closed file identity 和完整 gap/race 矩阵关闭。28 项失败、六项清理与 268-task clean 根回归 PASS；当前只允许冻结并执行第三轮独立只读复核，复核全零前不得发布或启动 M1-06/M2。
- `/root` 已核验第三轮 M1-05 复核为 FAIL 并废止 `1febc2d`；其唯一 P1 限于 JNA 5.6.0 依赖审查证据。官方最新 5.19.1 tag/commit、Maven Central artifact SHA-256、GitHub 双包零公告查询与错误 CVE 归属核对已归档，Windows 生产 JNA 模块测试 PASS；当前只允许完成 clean 根回归、冻结和第四轮独立复核，复核全零前不得发布或启动 M1-06/M2。
- `/root` 已核验第四轮 M1-05 独立复核对 `5b8163f` 给出 P0/P1/P2 全零 PASS，前三轮代码与依赖发现全部关闭；当前允许推送固定分支、创建唯一草稿 PR 和运行双平台 CI，仍不得启动 M1-06/M2。
- `/root` 已核验唯一草稿 PR #39 正确关联关闭 Issue #10；初始 HEAD `b3758f8` 的 Ubuntu/Windows Build 与 Governance 四项全部 PASS，Ubuntu 已执行 Linux native no-replace、M1-05 六份字节哈希和四 ABI 门禁。当前只允许提交 CI 证据并等待 exact merger-ready HEAD 全绿，仍不得启动 M1-06/M2。
- `/root` 已核验 merger-ready HEAD `a239a7c` 的四项 CI 全绿；PR #39 以 expected-head 普通 merge commit `78b44f2` 合并且 Issue #10 关闭。本地 main 已同步，首次 Governance 失败仅由合并态 HandOff 分支字段过期导致；当前只允许完成 post-merge README/HandOff、strict 与双平台 CI，仍不得启动 M1-06/M2。
- `/root` 已核验 post-merge `main@5756f9c5dd4f4139396bf17db1a74c6e5331c555` 的 Ubuntu/Windows Build、Governance、M1-05 字节一致性、Linux native no-replace、依赖负例、四 ABI 与无豁免 strict HandOff 全部 PASS；README/HandOff 已同步，M1-05 标记 done，当前无后续任务已启动。
- `/root` 已核验 M1-04 从 `main@ebbe928` clean 重启、Issue #9 OPEN、远程无同 head PR；废止 v1 分支仅保留为 rejected 归档。当前只实现 AHDC v2 Host 范围，不启动 Runtime、ZIP/CLI、设备或相邻任务。
- `/root` 已核验唯一草稿 PR #38 正确关联关闭 Issue #9；最终草稿 HEAD `4af2e44` 的 API 29/36 KVM、Ubuntu/Windows Build/Governance 六项全部 PASS，PR 为 CLEAN/MERGEABLE。用户已授权 ready/merge，本协调提交只准备 expected-head 合并与 post-merge `main` 恢复点。
- `/root` 已核验 merger-ready HEAD `65ae18e` 的六项检查全部 PASS；PR #38 以 expected-head 普通 merge commit `f908861cbb61e79e7c3127fd5216d4a6f8c6e3e1` 合并，Issue #9 关闭，本地 main 已同步。M1-04 仍等待 post-merge main 双平台 CI，完成前不启动 M1-05/M2。
- `/root` 已核验 post-merge `main@9f074db7222fc76442aa8fa7d44ea29091d7bdfa` 的 Ubuntu/Windows Build、Governance、M1-04 字节一致性和无豁免 strict HandOff 全部 PASS；README/HandOff 已同步，M1-04 标记 done，当前无活动任务且未启动 M1-05/M2。
- `/root` 已核验 M1-04 单 tag 合同无法在固定 Provider、512 MiB DEX 和 1 MiB 缓冲下满足认证后解压；M1-07 只修订治理合同，不包含废止实现、不启动模拟器或设备，也未获授权推送/创建 PR。
- `/root` 已核验首轮 M1-07 独立复核为 FAIL 并废止 `e13927a`；当前只允许关闭该轮 P1/P2、重新冻结并进行完整独立复核。
- `/root` 已核验第二轮 M1-07 独立复核为 FAIL 并废止 `3380659`；当前只允许关闭两项 P1、重新冻结并进行完整独立复核。
- `/root` 已核验第三轮 M1-07 独立复核为 FAIL 并废止 `e355438`；当前只允许关闭成功路径清理 P1、重新冻结并进行完整独立复核。
- `/root` 已核验第四轮 M1-07 独立复核为 FAIL 并废止 `dd0c4c0`；当前只允许关闭两项 P1/P2、重新冻结并进行完整独立复核。
- `/root` 已核验第五轮 M1-07 独立复核为 FAIL 并废止 `340b6ae`；当前只允许关闭跨 JNI 发布窗口 P1、重新冻结并进行完整独立复核。
- `/root` 已核验第六轮 M1-07 独立复核为 FAIL 并废止 `bb2e744`；当前只允许关闭 metadata/session 两项 P1、重新冻结并进行完整独立复核。
- `/root` 已核验第七轮 M1-07 独立复核为 FAIL 并废止 `3bea66a`；当前只允许关闭一项时序 P1 与两项验收 P2、重新冻结并进行完整独立复核。
- `/root` 已核验第八轮 M1-07 独立复核为 FAIL 并废止 `01f76f6`；当前只允许关闭 package metadata P1 与复核输入 P2、重新冻结并进行完整独立复核。
- `/root` 已核验第九轮 M1-07 独立复核为 FAIL 并废止 `d5d5d29`；当前只允许关闭 loader 时序 P1 与 metadata API P2、重新冻结并进行完整独立复核。
- `/root` 已核验第十轮 M1-07 独立复核为 FAIL 并废止 `358a71a`；当前只允许关闭 Guard 比较来源 P2、重新冻结并进行完整独立复核。
- `/root` 已核验 merger-ready HEAD `d0e0ee61171eb9472bce306619a426da86292f5c` 的四项 CI 全部 PASS；PR #37 使用 expected-head 普通 merge commit `9ec90fca6b2b293a56a98f3d0c60190b5c0e7a20` 合并，Issue #36 关闭，并在本地 `main` 无豁免通过 Governance、strict HandOff 与 diff check。README 已同步，M1-07 标记 done，当前无活动任务。
- Coordinator `/root` 已核验首轮独立复核 FAIL、六项修复 diff、本地 Gradle/check/governance、双变体 Release/R8 和静态 APK 验证结果。
- 当前快照声明三套 review-3 设备环境验收 PASS、第五次独立复核 P0/P1/P2 全为零、最终 PR HEAD 六项 CI 全部 PASS，PR #32 已合并，且 post-merge `main` Build/Governance 全绿并无豁免通过 strict HandOff；M0-05 标记 done。旧证据仅保留为历史回归基线。
- `/root` 已核验真机为 API 29 arm64 64-bit、user/release-keys、非 root 环境，设备 runner cleanup PASS；本轮未启动任何本机模拟器。
- GitHub KVM workflow 的既有超时与强制清理合同保持不变；M1-01 是纯 Host 任务，本轮不启动本机模拟器或真机，也不启动 M1-02/M1-03/M2。
- `/root` 已核验 PR #33 的首轮、证据 HEAD 与 merger-ready HEAD 的 Build/Governance 四个 job 和两个字节一致性步骤均 PASS；独立复核 P0/P1/P2 全为零。
- `/root` 已核验 PR #33 使用普通 merge commit `74c5f6252ea9b89154c285764d5f9601a0347358` 合并、Issue #6 关闭，并在本地 `main` 无豁免通过 strict HandOff；M1-01 标记 done，当前活动任务已转为 M1-02。
- `/root` 已领取 M1-02 并核验其唯一 Issue、分支、依赖、固定官方 `apksig` 与既有 ADR；当前活动范围仅为 Host signer policy，M1-03/M1-04/M2-03 未启动。
- `/root` 已核验首个证据 HEAD 与第二个修复证据 HEAD 的独立复核均 FAIL 并废止；第三次独立只读复核已对冻结证据 `902c20977d787ea9646078bbbe4c3c46bf0041cc` 给出 P0/P1/P2 全零 PASS。clean signer、256-task 根回归、Governance、官方六 fixture/十三行错误矩阵、二十六项 artifact manifest、block 资源上界/高位边界、异常脱敏、SPV1 和无签名能力扫描均已闭环；用户已授权发布固定分支、创建唯一草稿 PR 和运行双平台 CI，但未授权 ready 或 merge。
- `/root` 已核验 PR #34 首轮双平台 Build 的共同根因为 `host:cli` 传递依赖锁缺口；用户授权的修复仅增加现有 apksig 9.3.0 的 runtime/testRuntime 锁条目。本地 256-task 根回归和冻结报告 hashes 均 PASS；下一步只推送该最小修复并等待替换 CI，仍未授权 ready 或 merge。
- `/root` 已核验锁修复 HEAD `b72ef88003c2dea993afbd7d96d502535833e450` 的替换 Build/Governance 四项 CI 全部 PASS，Ubuntu/Windows M1-02 显式字节门禁均命中冻结 hashes；当前仅归档最终证据并等待单独 ready/merge 授权。
- `/root` 已核验最终证据 HEAD `2ed5f4f7973c9ff87a3e3cbbf6e4e5325a259418` 的 Build/Governance 四项 CI、两项 M1-02 字节门禁、CLEAN/MERGEABLE 状态与用户 ready/merge 授权；本协调提交只准备 post-merge `main` 恢复点，产品实现未改变。
- `/root` 已核验 merger-ready HEAD `43fd2dd0671b90430b5f4b06f1728c563eb4c07c` 的四项 CI 与两项字节门禁全部 PASS；PR #34 使用 expected-head 普通 merge commit `d590b94f08047352d2b1f56c1c08aba4cbf079ec` 合并，Issue #7 关闭，并在本地 `main` 无豁免通过 strict HandOff。M1-02 标记 done，当前无活动任务。
- `/root` 已核验 M1-03 Windows Host/静态候选、规范报告 hashes、双变体 Release/R8 测试包和 237-task 根回归；首次 API 29 安装拒绝仅构成历史零残留证据，已由后续正式 PASS 矩阵取代；本机模拟器始终未启动。
- `/root` 已核验首轮 M1-03 独立复核为 FAIL 并废止旧冻结点；该轮只允许关闭四项发现、重跑门禁与重新冻结，其发布限制已由后续用户授权取代。
- `/root` 已核验第二轮 M1-03 独立复核为 P0 `0`、P1 `1`、P2 `0` FAIL 并废止 `99877a4`；修复 `f15088d` 仅保留有界结构角色状态，Windows Host/root/static 门禁通过；该时点的复核与发布待办已由后续全零复核和用户授权关闭。
- `/root` 已核验第三轮 M1-03 独立复核对 `1425f911eb48796a8e4ade9aa3c5fcec09cb1f7b` 给出 P0/P1/P2 全零 PASS；本地实现与复核门禁关闭。该时点的 API 29 安装阻塞已由下述正式矩阵关闭；远端 KVM/双平台 CI 仍需单独发布授权，当前不得声明 M1-03 完成。
- `/root` 已核验用户允许安装后的 API 29 arm64 正式矩阵为 PASS：双变体 instrumentation、生命周期、跨 DEX、JNI、signer、metadata、各 20 次冷启动、内存、零明文 DEX、runner 与独立清理均闭环；本轮未启动模拟器。该时点的发布后 KVM/双平台 CI 与推送授权待办已由 PR #35 初始六项 PASS 关闭。
- `/root` 已核验授权发布后的 PR #35 初始 HEAD `c6ed194c2fea9672d6cdd38cf181560e8d76e87f` 六项 CI 全部 PASS；API 29/36 KVM artifacts、四份 startup-negative 报告、20 次冷启动、no-Factory、内存、零明文 DEX、cleanup 及 Ubuntu/Windows 四份规范 hashes 均闭环。
- `/root` 已核验最终证据 HEAD `16ffba62df8f25d4397d771c5bdfa77f8dba78ad` 的 replacement 六项 CI、两平台四份规范报告字节门禁、唯一 PR/Issue #8 关联、CLEAN/MERGEABLE 状态与用户 ready/merge 授权；本协调提交只准备 post-merge `main` 恢复点，产品实现未改变。
- `/root` 已核验 merger-ready HEAD `07c519c73b2a48f8636eed557da463f699299f20` 的六项 CI 全部 PASS；PR #35 使用 expected-head 普通 merge commit `197eb45535b117e28ad1ef904993d2b54068056b` 合并，Issue #8 关闭，并在本地 `main` 无豁免通过 strict HandOff。M1-03 标记 done，当前无活动任务。
