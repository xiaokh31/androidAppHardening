---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260802-153213
updated_at: 2026-08-02T22:44:56+08:00
updated_by: /root
state: active
source_branch: feat/m1-02-signer-policy
base_commit: aebbc441da34d2fba78648415c1d80ea844d774d
working_tree: clean
current_milestone: M1
active_task: M1-02
next_owner: /root
---

# Project HandOff

## Objective

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的边界内执行 M1-02 输入签名身份策略。当前只实现 Host 侧固定 `apksig` 验证、唯一当前 signer、轮换历史与不可变 `SignerPolicyV1`，不实现签名能力、M1-04 wire encoder、M2-03 Runtime 校验或任何相邻任务。

## Current State

- M0-04 的 PR #29 已合并，正式 API 29/36 x86_64 设备矩阵和独立安全复核通过。
- M0-06 的 PR #31 已合并为 `main@f1362188be5083a6d557522f0f5be1905935f6eb`；合并后的 Governance/Build 在 Ubuntu 与 Windows 通过，`main` 已无豁免通过 strict HandOff。
- M0-06/ADR 0007 已解除旧的 `ApplicationInfo.metaData == null` 阻塞，启动配置唯一来源改为 `ApplicationInfo.sourceDir` 中的固定 ConfigV2 与 AHDC 条目。
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

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29、正式设备矩阵和独立复核已通过 |
| M0-06 | `runtime-security-agent` | `docs/m0-06-early-startup-config-contract` | done | M0-04 | PR #31、合并后 strict HandOff 和双平台 CI 已通过 |
| M0-05 | `runtime-security-agent` | `spike/m0-05-application-factory-provider-jni-poc` | done | M0-04, M0-06 | PR #32、三环境矩阵、独立安全复核和最终 PR CI 已通过 |
| M1-01 | `/root` | `feat/m1-01-untrusted-apk-inspector` | done | M0-05 | PR #33、Issue #6、独立复核、双平台字节一致性 CI 与 main strict HandOff 均已关闭 |
| M1-02 | `/root` | `feat/m1-02-signer-policy` | in_progress | M1-01 | 发布授权已获得；推送唯一草稿 PR 并运行双平台 CI |

## Decisions and Invariants

- 继续遵守 ADR 0001 至 ADR 0007；ADR 0007 固定 sourceDir 配置通道，ADR 0006 固定 768-byte ConfigV2 wire layout。
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

## Changes Since Previous Handoff

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

## Blockers and Required Approvals

None. The third independent M1-02 review passed with P0/P1/P2 all zero, and the user authorized the branch push, sole Issue #7 draft PR and Ubuntu/Windows CI. Ready/merge remains outside the current authorization.

## Ordered Next Actions

1. Push `feat/m1-02-signer-policy` and create the sole Issue #7 draft PR against `main`.
2. Require Ubuntu/Windows Build, Governance and M1-02 byte-equivalence gates.
3. Archive the final PR CI evidence and request separate ready/merge authorization; do not merge implicitly.
4. Do not start M1-03, M1-04, M2-03 or any adjacent task implicitly.

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/tasks/M1-01-untrusted-apk-inspector.md`
- `docs/tasks/M1-02-signer-policy.md`
- `host/apk-inspector/`
- `docs/adr/0003-api29-public-classloader-hook.md`
- `docs/adr/0006-offline-key-protection-boundary.md`
- `docs/adr/0007-source-dir-startup-configuration.md`
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
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `fixtures/android/src/androidTestCompatFixture/java/ah/fixtures/android/CompatibilityPocRunner.java`
- `tools/validation/verify-m0-05-apks.mjs`
- `tools/validation/create-m0-05-test-apks.mjs`
- `tools/validation/run-m0-05-device-acceptance.mjs`
- `tools/validation/run-m0-05-startup-negative.mjs`
- `tools/validation/m0-05-linux-kvm-packages.json`
- `.github/workflows/m0-05-linux-kvm.yml`

## Resume Checklist

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
- [ ] 推送并完成唯一 Issue #7 PR 的 Ubuntu/Windows 字节一致性与治理 CI。

## Handoff Sign-off

- Coordinator `/root` 已核验首轮独立复核 FAIL、六项修复 diff、本地 Gradle/check/governance、双变体 Release/R8 和静态 APK 验证结果。
- 当前快照声明三套 review-3 设备环境验收 PASS、第五次独立复核 P0/P1/P2 全为零、最终 PR HEAD 六项 CI 全部 PASS，PR #32 已合并，且 post-merge `main` Build/Governance 全绿并无豁免通过 strict HandOff；M0-05 标记 done。旧证据仅保留为历史回归基线。
- `/root` 已核验真机为 API 29 arm64 64-bit、user/release-keys、非 root 环境，设备 runner cleanup PASS；本轮未启动任何本机模拟器。
- GitHub KVM workflow 的既有超时与强制清理合同保持不变；M1-01 是纯 Host 任务，本轮不启动本机模拟器或真机，也不启动 M1-02/M1-03/M2。
- `/root` 已核验 PR #33 的首轮、证据 HEAD 与 merger-ready HEAD 的 Build/Governance 四个 job 和两个字节一致性步骤均 PASS；独立复核 P0/P1/P2 全为零。
- `/root` 已核验 PR #33 使用普通 merge commit `74c5f6252ea9b89154c285764d5f9601a0347358` 合并、Issue #6 关闭，并在本地 `main` 无豁免通过 strict HandOff；M1-01 标记 done，当前活动任务已转为 M1-02。
- `/root` 已领取 M1-02 并核验其唯一 Issue、分支、依赖、固定官方 `apksig` 与既有 ADR；当前活动范围仅为 Host signer policy，M1-03/M1-04/M2-03 未启动。
- `/root` 已核验首个证据 HEAD 与第二个修复证据 HEAD 的独立复核均 FAIL 并废止；第三次独立只读复核已对冻结证据 `902c20977d787ea9646078bbbe4c3c46bf0041cc` 给出 P0/P1/P2 全零 PASS。clean signer、256-task 根回归、Governance、官方六 fixture/十三行错误矩阵、二十六项 artifact manifest、block 资源上界/高位边界、异常脱敏、SPV1 和无签名能力扫描均已闭环；用户已授权发布固定分支、创建唯一草稿 PR 和运行双平台 CI，但未授权 ready 或 merge。
