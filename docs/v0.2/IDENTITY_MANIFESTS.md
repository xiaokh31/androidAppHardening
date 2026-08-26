# v0.2 身份 Manifest 合同

## 1. 目的

本文件固定 `v0.2.0-rc.1` tuple 中每个 manifest hash 的唯一 preimage、所有者和完整性规则。机器可读的唯一集合与 path→role 规范是 tracked `docs/v0.2/identity-path-policy-v1.json`；本文件是它的中文解释。V2-M3-02 只能读取固定 tracked 文件并复算，不能临时选择文件、目录、role 或字段，也不能对空文件、ignored build 输出或 CI 临时 artifact 取 hash。

## 2. 唯一 preimage 与所有者

| Tuple 字段 | Tracked preimage | 唯一 owner | 必须覆盖 |
| --- | --- | --- | --- |
| `implementationManifestSha256` | `docs/v0.2/evidence/V2-M0-02/implementation-manifest.json` | V2-M0-02 | Host、Runtime、distribution、launcher、归档内 Quickstart、构建入口和生产锁文件 |
| `toolchainManifestSha256` | `docs/v0.2/evidence/V2-M0-02/toolchain-manifest.json` | V2-M0-02 | Wrapper、JDK/Gradle/AGP/Kotlin/SDK/NDK/CMake/Node、依赖验证、runner/tool binary locks |
| `productContractManifestSha256` | `docs/v0.2/evidence/V2-M0-02/product-contract-manifest.json` | V2-M0-02 | 产品需求、架构、威胁模型、兼容边界、ADR 0001–0015、ADR 0020–0021、公开 schema |
| `fixtureSourceManifestSha256` | `docs/v0.2/evidence/V2-M3-01/fixture-source-manifest.json` | V2-M3-01 | `fixtures/` 下全部 tracked regular files 与 fixture build 配置 |
| `validationManifestSha256` | `docs/v0.2/evidence/V2-M3-01/validation-manifest.json` | V2-M3-01 | benchmark、release validation、security/SBOM、packaging/evidence validator、workflow candidate 与 schema |
| `performanceContractSha256` | `docs/v0.2/evidence/V2-M3-01/performance-contract-manifest.json` | V2-M3-01 | 性能策略、报告 schema、统计/大小复算器和 canonical performance workflow candidate |
| `releaseGateContractSha256` | `docs/v0.2/evidence/V2-M3-01/release-gate-contract-manifest.json` | V2-M3-01 | exact-tuple 回归、安全/SBOM、打包和 release-evidence gate 的 schema、validator 与 workflow candidate |

这七个路径是 tuple 字段的完整映射。重命名、替代路径、CI artifact、URL 内容、目录 hash 或另一个“等价”manifest 都不接受。

## 3. 统一 JSON schema

每个 preimage 的顶层字段及顺序固定为：

```text
schemaVersion
manifestKind
taskId
releaseLine
entries
```

固定值为 `schemaVersion=1`、`releaseLine=v0.2`。`manifestKind` 依表顺序只能是 `implementation`、`toolchain`、`product-contract`、`fixture-source`、`validation`、`performance-contract` 或 `release-gate-contract`；`taskId` 只能是该表的唯一 owner。

每个 `entries` 元素的字段及顺序固定为：

```text
path
mode
sizeBytes
sha256
gitBlobSha1
role
```

- `path` 是仓库相对 POSIX 路径，UTF-8 NFC；不得为空、以 `/` 开头、包含反斜杠、`.`/`..` 段或绝对路径。
- `mode` 只能是 tracked regular file 的 `100644` 或 `100755`。`120000` symlink、`160000` gitlink、未跟踪文件和目录条目直接失败。
- `sizeBytes` 是 Git blob 的非负十进制字节数；两个 hash 均为小写十六进制并从同一 blob 重算。
- `role` 只能来自 `identity-path-policy-v1.json.roleEnum`；具体 path→role 使用该文件中按顺序执行的 `roleRules`，第一个匹配即为唯一结果。零匹配或多义规则均失败，validator source 无权新增枚举、fallback 或 `other`。
- `entries` 按 `Buffer.from(path, "utf8")` 的 UTF-8 无符号字节序严格递增；重复、大小写折叠冲突、NFC 冲突、遗漏和额外路径均失败。

规范 bytes 是 UTF-8、LF、两空格缩进、一个末尾 LF；禁止 BOM、CRLF、时间戳、机器名、用户路径或环境相关字段。tuple 字段的 SHA-256 对整个 tracked 文件 bytes 计算，不对解析后对象、压缩包或生成日志计算。

## 4. 完整路径集合

V2-M0-02 和 V2-M3-01 必须实现共享 manifest validator，使用 `git ls-tree -r` 从对应 freeze commit 计算期望集合，不能从 manifest 自身推导集合。唯一选择算法是 `identity-path-policy-v1.json.manifestPolicies` 的 `exactPaths ∪ recursivePrefixes 下全部 tracked regular files ∪ suffixSelectors 命中的 tracked regular files`；数组顺序、prefix、suffix、canonical path 与 role rule 全部在 V2-M0-01 固定，未来 worker 和 validator 只能消费，不能扩充或解释。

- `implementation` 完整枚举 `host/`、`runtime/`、`distribution/` 下全部 tracked regular files及机器规范中五个根 exact path。它必须包含唯一机器合同 `distribution/src/main/resources/v0.2/schemas/archive-byte-contract-v1.json`，该合同逐字段固定 ZIP/TAR/GZIP bytes 与 Eclipse Temurin `17.0.19+10`；缺少或另一路径的 archive 常量不构成 implementation closure。
- `toolchain` 只按机器规范枚举 `gradle/`、两个 exact path，以及明确列出的模块 prefix 中以 `/build.gradle.kts` 或 `/gradle.lockfile` 结尾的 tracked regular file。工具二进制本身不入库时，其官方 URL、tag/commit、asset name、size 和 SHA-256 必须作为 `tools/validation/v02-supply-chain-tools.json` 的普通 tracked bytes 被本 manifest 覆盖。
- `product-contract` 完整枚举机器规范的 exact docs/ADR 列表和 `docs/specs/` 下全部 tracked regular files；“公开 schema”不得由 worker临时解释。两个 identity policy JSON 本身也在该 manifest 内。
- `fixture-source` 完整枚举 `fixtures/` 下全部 tracked regular files；fixture build 所需的根配置若已在 implementation/toolchain 中列出，仍必须通过交叉引用 hash 验证。
- `validation` 完整枚举机器规范中的五个 recursive prefix 和六个 exact path。五个非执行 workflow candidate 的唯一路径、canary workflow、candidate-neutral tuple/freeze/post-freeze verifier、package/HandOff gate、四类 run/artifact lock、两类 freeze lock schema/validator，以及五类 raw artifact manifest schema与 packaging/release-evidence 两个独立 validator，均由 `canonicalPaths` 固定并必须进入 closure。
- `performance-contract` 是 `identity-path-policy-v1.json` 中逐项列出的十二个 exact path；不存在“性能章节”或未来 validator自行选择文件的规则。
- `release-gate-contract` 是同一机器规范中逐项列出的 task/docs、candidate workflow、schema、validator 与 identity gate exact path；不存在 glob、目录自动加入或同义替代路径。

共享文件可以出现在多个 manifest，但每个集合都必须独立完整。Validator 必须先逐字读取并验证机器规范的 canonical JSON，再执行 selector；不得把 validator source 的另一个列表当作真值。机器规范的 missing/extra/reorder/path/selector/role mutation 必须失败。

2026-08-26 用户授权的 ADR 0021 是候选前唯一追加：仅把该 ADR exact path 纳入 product-contract，并同步规范 hash pin；不开放动态 selector，不改变其他 manifest 所有权或 post-freeze 规则。

### 4.1 固定 canonical path

五个 candidate workflow 唯一位于 `tools/validation/workflows/{v02-performance.yml,v02-release-validation.yml,v02-security-review.yml,v02-release-packaging.yml,v02-release-evidence.yml}`，对应 live path 唯一位于 `.github/workflows/` 下同名文件。四类锁的 schema/validator、freeze acceptance locks、component baseline、五个 raw evidence manifest schema、packaging/release-evidence raw manifest validator、candidate verifier、post-freeze verifier、package validator 与 HandOff validator 的每一个 exact path均由 `identity-path-policy-v1.json.canonicalPaths` 固定。`release-packaging` 唯一使用 `tools/release-evidence-v02/schemas/release-packaging-artifact-manifest-v1.schema.json` 与 `tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs`；`release-evidence` 唯一使用 `tools/release-evidence-v02/schemas/release-evidence-artifact-manifest-v1.schema.json` 与 `tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs`。这四个文件同时进入 `validation` 与 `release-gate-contract` closure；重命名、复制、第二份实现或仅在 build/CI artifact 中存在均失败。

## 5. Self-reference 与 freeze 绑定

- 七个 manifest 文件及其父 evidence 目录不得出现在任何一个 `entries` 数组中；manifest 不能包含自己的 hash、tuple hash、最终 freeze SHA 或运行时证据。
- V2-M0-02 的三个 manifest 必须可从 `implementationFreezeSha` 的 Git blobs 逐字读取；V2-M3-01 的四个 manifest必须可从 `validationFreezeSha` 的 Git blobs逐字读取。两个 SHA 均不是聊天、PR head 或 worker自报值，而是相应 tracked freeze-acceptance lock绑定的合并后 `main` official Governance `runAttempt=1` 的精确 `head_sha`。
- 两个 freeze-acceptance lock 唯一位于 `docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json` 与 `docs/v0.2/evidence/V2-M3-01/validation-freeze-lock.json`。它们绑定 canonical issue/PR、reviewed PR head、`MERGE_COMMIT`、merge commit、post-merge main run ID/attempt/head/conclusion、manifest hashes、changed-path report 与 reviewer结论；V2-M3-01 候选前冻结 schema/validator，V2-M3-02 只能写入并验证这些数据。
- `implementationFreezeSha` 必须是 `validationFreezeSha` 的祖先。两者之间只能改变 V2-M3-01 明确拥有的 validation paths，不能改变任何 V2-M0-02 manifest 所覆盖的 blob。
- V2-M3-02 从两个 freeze commit 读取 exact bytes，验证 schema、集合、排序、mode、blob/hash/size 后才计算 tuple；工作区文件或任务聊天声明不能替代 Git blob。

## 6. Candidate 与 post-freeze HEAD

- 当前 V2-M0-01 版本的 package/HandOff validator 是 pre-candidate fail-closed gate：只接受 development tuple；发现 candidate lock 必须失败。V2-M3-01 在 `validationFreezeSha` 前实现并纳入 manifest 的 candidate-neutral aggregate verifier与两个 candidate-state adapter，才可验证后续 lock。V2-M3-02 不得新增、升级或修复 validator。
- `docs/v0.2/post-freeze-path-policy-v1.json` 固定 V2-M3-02 至 V2-M4-03 的阶段顺序、`pre-run|evidence-pr|post-merge` 三种 phase、required tracked outputs 和每阶段 exact changed-path allowlist。调用必须显式给出 `--stage` 与 `--phase`：pre-run要求当前输出尚不存在且HEAD等于已接受前驱main，evidence-pr要求当前tracked outputs完整且diff只命中本阶段allowlist，post-merge要求HEAD等于唯一merge后的main并绑定official run。V2-M3-04 只有 compatibility matrix、release-validation artifact manifest、gate 与 run lock 四个 canonical output；不存在 `v02-release-validation-summary.json`。V2-M4-01 的 local validation 与 independent review、V2-M4-03 的 archive 外 Quickstart、release notes、evidence index、decision 和 final artifact manifest 都是 required tracked outputs。Aggregate verifier从 `validationFreezeSha` 沿 first-parent `main` 历史验证连续阶段、唯一 merged PR、official post-merge run、七个 manifest blobs与五组 candidate/live bytes；任何阶段/phase不符、缺少 required output、未列路径或先前阶段 bytes漂移失败。
- V2-M3-02 只写两份 freeze lock、product tuple lock、固定报告/HandOff并把五个 candidate逐字复制到 live path。其后任务只写机器规范中本阶段 canonical evidence；M4-03 仅可额外写固定 archive 外 docs/README。允许路径不是允许内容，schema/run/artifact/tuple/hash/role检查仍必须全部通过。

## 7. 必须拒绝的变异

每个 manifest validator 与 V2-M3-01 冻结的 aggregate validator至少拒绝：字段缺失/额外/重排、空 entries、路径遗漏/额外/重复/乱序、反斜杠或非 NFC 路径、错误 mode、symlink/gitlink、错误 size/blob/SHA、自由 role、manifest self-reference、ignored artifact 替代、freeze commit 不含 exact preimage、跨 freeze byte drift、伪造但自洽的 `VERIFIED` lock、错误 PR/merge/run head、post-freeze未列路径、先前阶段 evidence 漂移以及任意 tuple 字段指向错误 preimage。还必须逐项变异五个 `artifactManifestSchemas` path 和两个 `artifactManifestValidators` path；缺失、重命名、alternate/build-only validator、schema/validator byte drift、从 `validation` 或 `release-gate-contract` closure删去任何一个路径，以及 M4-01/M4-03 required output 缺失都必须失败。
