---
id: V2-M0-02
title: "v0.2 版本化候选基线"
milestone: V2-M0
status: planned
owner_role: host-pipeline-agent
depends_on:
  - V2-M0-01
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - plan-apk-hardening-change
  - implement-apk-postprocessor
  - validate-protected-apk
security_sensitive: true
---

## Goal

把继承的产品实现标识为版本 `0.2.0`，在候选前实现并冻结 distribution packager、双平台 launcher、archive schema 与 archive-internal Quickstart，建立可复算的 production-path manifests 和唯一 tracked component baseline；M0-02 以固定 merge-commit 与合并后官方 Governance run 形成可供后续精确验证的 freeze 事实，不在 PR 内自报或预填 `implementationFreezeSha`，且不改变任何保护逻辑。

## Background

基线根 Gradle 和 CLI 当前分别声明 `0.1.0-dev`，distribution 尚无可发布实现。v0.2 需要单一版本来源、可构建 Host/Runtime component 清单和在安全审查前已经存在的全部发布字节生成逻辑。该 freeze 只说明候选实现字节，不能替代性能、安全或发布证据。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M0-01 已接受的 development tuple、ADR 0020 和版本隔离规则。
- baseline Host、Runtime、Gradle toolchain、lockfiles 和 verification metadata。
- 当前 `REPORT_V1`、AHDC v2、ConfigV2、SPV1 和四 ABI 合同。

## Expected Outputs

- 产品版本唯一来源 `0.2.0`，CLI `--version` 与 REPORT tool version 同步。
- 唯一 tracked component baseline `docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json`，固定 schema `distribution/src/main/resources/v0.2/schemas/component-baseline-v1.schema.json` 与 validator `distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt`；`build/v0.2/candidate-component-manifest.json` 仅可作为逐字重建的 ignored 临时副本，不能作为 freeze、tuple 或发布输入。
- tracked `docs/v0.2/evidence/V2-M0-02/implementation-manifest.json`、`toolchain-manifest.json`、`product-contract-manifest.json` 与 SHA-256。
- Windows/Ubuntu Host Release、Runtime 四 ABI Release component hashes。
- `distribution/` 下冻结的 packager、Windows/Ubuntu launcher、release/archive/provenance schema 和 `distribution/docs/QUICKSTART.md`。
- CycloneDX plugin/SBOM generator、`securityReviewV02` Gradle 入口、冻结的 SBOM canonicalizer、`tools/validation/v02-supply-chain-tools.json`、`v02-toolchain-component-inventory.json` schema 与全部 plugin transitive verification metadata。
- 非 release canary 的双平台两次构建、metadata/entry equality、launcher/JRE/offline smoke 与敏感扫描。
- 固定 `MERGE_COMMIT` 合并方式、M0-02 PR head、合并 commit 与该 commit 上 `main` 官方 Governance 初次运行身份；后续 V2-M3-02 只能把这些外部可复算事实写入 `docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json`，不得选择其他 commit 或运行。

## In Scope

- 根项目/CLI 版本元数据和只读 version propagation。
- 生产 Host/Runtime/distribution/source/build/lock path 的规范 manifest。
- 固定 archive layout/order/time/mode/owner/group/compression 逐字段字节合同的 packager、两个 launcher、archive-internal Quickstart 和 manifest/provenance schema；运行前置固定 Eclipse Temurin `17.0.19+10`，不接受任意“JRE 17”。
- `SUPPLY_CHAIN_TOOLCHAIN.md` 固定版本的 CycloneDX plugin/SBOM generation/Gradle entry/tool lock，以及只允许规范化 `metadata.timestamp` 的 canonicalizer；本任务不下载 OSV DB 或形成 security PASS。
- 只消费包外 signed fixture 的 canary package/smoke；canary artifact 进入 ignored build 目录且不形成发布证据。
- 现有 public interfaces、error codes、container/config/report version 与 ABI surface 的冻结检查。
- clean Build/check/lint、dependency verification、Release/R8 和 symbol surface 验证。
- component baseline 的 tracked canonical bytes、schema、validator 和与三个 implementation-side manifest 的闭包验证。

## Out of Scope

- 修改 APK 解析、加密、repack、Runtime、风险、内存或兼容逻辑。
- 实现 benchmark、设备验证、执行 SBOM/security scan 或 archive 外 release notes/evidence 文档。
- 生成、上传或命名为 final v0.2.0 的 release archive。
- 使用旧 M3-05/诊断 artifact，生成 candidate product tuple 或运行 canonical performance。
- 在 M0-02 PR 内创建、预填或提交 `implementation-freeze-lock.json`，或把 PR head/content commit 当作 accepted `implementationFreezeSha`；该锁只能由 V2-M3-02 在 schema/verifier 已由 V2-M3-01 预冻结后写入数据。
- 升级工具链或依赖版本。

## Implementation Decisions

- 2026-08-26 用户授权 ADR 0021 的限定整改：允许同一 PR 新增 `docs/adr/0021-v0-2-maven-published-artifact-source-profiles.md`、修订 `SUPPLY_CHAIN_TOOLCHAIN.md`，把该 ADR exact path追加到 identity policy并同步 `IDENTITY_MANIFESTS.md`、validator中的 policy hash pin。本卡供应链 source allowlist追加 `tools/supply-chain-v02/locked-maven-published-artifact-v2.mjs` 及其同目录 self-test，根 Gradle追加 actual resolved graph导出和两个 supply-chain self-test接线；`host/axml/gradle.lockfile` 只补现有 `apksig:9.3.0` 的 `runtimeClasspath` 缺失归属。版本、原 verification hash、其他 path ownership、post-freeze policy不变；该例外不是通用治理/tool path放宽。
- Maven lock完成只证明 ADR 0021 定义的完整 published-artifact acquisition identity，不代表源码、vendor attribution、维护或 security PASS；UNRESOLVED/UNVERIFIED/PENDING 必须逐项保留，后续 gate不得当 PASS消费。实现 freeze后任何 profile/endpoint/lock补录需要新授权和新的 implementation/validation freezes。
- Canonicalizer保留既有四参数 CLI；`V02_CYCLONEDX_CLI` 显式提供已预置工具路径，逐次核验固定平台 size/SHA-256且只能调用 `validate`。未提供、错误工具、raw/canonical schema失败均不发布 canonical/report，绝不写入伪造的 schema exit 0。
- 产品版本只有一个生产来源；CLI 和 REPORT 不保留第二个手写常量。
- 三个 tracked manifest 严格遵守 `docs/v0.2/IDENTITY_MANIFESTS.md`：固定顶层/entry 字段顺序、两空格 LF canonical JSON、POSIX/NFC 路径、UTF-8 字节排序、`100644|100755`、完整 Git blob/size/SHA-256 和固定 role。symlink、gitlink、self-reference、ignored artifact、空/漏/重/额外/乱序 entry 均失败。
- `implementation` 从 freeze commit 完整枚举 `host/`、`runtime/`、`distribution/` 和规定根构建文件；`toolchain` 与 `product-contract` 使用文档固定的完整集合。三个 preimage 的 tracked 路径不可替换。
- Runtime manifest 分别记录四 ABI `libah_runtime.so`，不得用一个聚合 hash 代替逐 ABI hash。
- component baseline 的唯一权威 preimage 固定为 `docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json`，schema 固定为 `distribution/src/main/resources/v0.2/schemas/component-baseline-v1.schema.json`，validator 固定为 `distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt`。baseline 逐项记录 Host release JAR、四 ABI `libah_runtime.so`、两个 launcher、Quickstart、LICENSE、THIRD_PARTY_NOTICES、canonical SBOM 注入槽和 release/provenance schema 的 logical path、source Git blob、mode、size、SHA-256、variant/ABI 与 archive role；字段顺序、entry UTF-8 byte order、canonical JSON 和完整 expected set 由固定 validator 从三个 tracked manifest 与 exact Git blobs 重建，不能从 baseline 自身、目录扫描或 ignored build 输出推导。
- `v02-component-baseline.json` 必须记录并精确匹配 `implementationManifestSha256`、`toolchainManifestSha256` 与 `productContractManifestSha256`；其自身 SHA-256、schema SHA-256、validator Git blob/SHA-256 和上述三个 manifest SHA-256 由 freeze acceptance lock 联合绑定。schema 与 validator 必须作为 implementation manifest 的 production bytes 被覆盖；evidence 目录中的 baseline 本身不进入七个 tuple manifest 的 `entries`，避免 evidence self-reference。ignored `build/v0.2/candidate-component-manifest.json` 只能是 validator 从 tracked baseline 重建后 byte-identical 的诊断副本，缺失不影响身份，存在但不相同必须失败。
- archive root layout 固定为 `bin/`、`lib/`、`runtime/`、`docs/QUICKSTART.md`、`LICENSE`、`THIRD_PARTY_NOTICES.md`、`bom.cdx.json`、`release-manifest.json`；SBOM 在 canary 中使用 schema-valid synthetic placeholder，V2-M4-02 才注入 V2-M4-01 exact reviewed SBOM bytes。
- Windows launcher 固定 `bin/android-app-hardening.cmd`，Ubuntu launcher 固定 executable `bin/android-app-hardening`。二者只接受预安装 Eclipse Temurin `17.0.19+10`并原样转发 args/stdout/stderr/exit；vendor、runtime/version/build不精确相等即稳定失败，不得下载 JRE 或调用签名工具。
- ZIP machine contract 的摘要常量保持为 ZIP32（ZIP64 拒绝）、version-made-by `0x0314`、version-needed `20`、general-purpose flags `0x0800`、regular-file method `8`、Deflater level `9`、data descriptor absent、extra/comment/archive-comment empty、UTC DOS timestamp 向下取偶数秒；下面的逐字段规则是这些摘要常量的唯一完整解释。
- `archive-byte-contract-v1.json` 对 Windows ZIP 逐字段固定为单磁盘 ZIP32，禁止 ZIP64、split 与自解压前缀。entry 按规范 NFC POSIX path 的无符号 UTF-8 bytes 严格递增；目录必须是以 `/` 结尾的显式 entry、空 payload、`method=0 (STORED)`、CRC32/压缩前/后 size 均为 `0`，普通文件必须为 `method=8 (DEFLATED)`。所有 local/central header 的 `version-needed=0x0014`、flags 恰为 `0x0800`（仅 UTF-8）、DOS UTC date/time 为 freeze time 向下取偶数秒、file-name bytes/CRC32/uncompressed size/compressed size 必须逐项相等；encryption、data descriptor、local/central extra field 与 entry comment 均不存在。central header 的 `version-made-by=0x0314`、disk-start=0、internal-attributes=`0x0000`；external-attributes 对目录恰为 `(0040755 << 16) | 0x10`，launcher regular file 恰为 `(0100755 << 16)`，其他 regular file 恰为 `(0100644 << 16)`。普通文件 payload 使用 Eclipse Temurin `17.0.19+10` `java.util.zip.Deflater` 的 `level=9`、`strategy=DEFAULT_STRATEGY`、`nowrap=true`，完整输入一次写入后 finish/drain，输出为 RFC 1951 raw DEFLATE，禁止 zlib/gzip wrapper；CRC32 与 size 在写 header 前从原 bytes 预计算。central directory 的相对 local-header offset、总 size/offset 与 entry count 必须复算且在 uint32/uint16 范围内；EOCD 的 disk number、central-directory disk 均为 0，两个 entry count 相等，archive comment length=0，EOCD 后无 trailing byte。freeze time 超 DOS 范围、任一 size/offset/count 需要 ZIP64、路径为空/重复/大小写或 NFC 冲突、无法 UTF-8/NFC 表示、symlink/hardlink 或非 regular/directory 均失败。
- TAR machine contract 的摘要常量保持为 uid/gid `0`、uname/gname empty、无 PAX/GNU/sparse/hardlink/symlink；下面固定每个 POSIX ustar field、padding 与 EOF byte。
- 同一合同对 Ubuntu TAR 逐 512-byte block 固定 POSIX `ustar`：header `magic` 为六 bytes `ustar\0`、`version` 为两 bytes `00`；`mode`/`uid`/`gid` 分别以 7 个 ASCII octal digits 加 NUL 编码，`size`/`mtime` 以 11 个 ASCII octal digits 加 NUL 编码，禁止 base-256；uid/gid 为 0，directory/launcher mode 为 `0755`、其他 regular file 为 `0644`，mtime 为 implementation freeze UTC epoch integer seconds。checksum 计算时八 byte checksum field 先全部视为空格，以 512 header 的 unsigned byte sum 计算，再恰好写六个 ASCII octal digits、NUL、space；regular file `typeflag='0'`，directory `typeflag='5'` 且 size=0，linkname 全 NUL，uname/gname 全 NUL，devmajor/devminor 各为七个 ASCII `0` 加 NUL，所有未使用 header bytes 为 NUL。path 能放入 100-byte `name` 时 `prefix` 全 NUL；否则只在最后一个可行 `/` 分割，name 不超过 100 UTF-8 bytes、prefix 不超过 155 UTF-8 bytes且均保持 NFC，若不存在唯一可行分割即失败；目录 path 保留结尾 `/`。entry 仍按完整 path UTF-8 bytes 排序；每个 regular payload 后补最少 NUL 到 512 对齐，目录无 payload；archive 恰以两个全零 512-byte EOF blocks 结束且之后无 byte。PAX、GNU longname/longlink、sparse、global header、concatenated TAR、symlink 与 hardlink 全部拒绝。
- GZIP machine contract 的摘要常量保持为 `MTIME=freeze epoch` 与无 extra/name/comment/header-CRC；下面的单 member header、payload 与 trailer规则固定全部 bytes。
- 外层 GZIP 恰有一个 member：10-byte header 固定 `ID1=0x1f`、`ID2=0x8b`、`CM=8`、`FLG=0`、四 byte little-endian `MTIME=implementation freeze epoch`、`XFL=2`、`OS=3`；freeze epoch 必须在 uint32 范围，因此不得出现 extra/name/comment/header-CRC。payload 是前述完整 TAR bytes，使用同一 Eclipse Temurin `17.0.19+10` `Deflater(level=9, nowrap=true)` 与 `DEFAULT_STRATEGY`、一次完整输入和 finish/drain 的 RFC 1951 raw DEFLATE。八 byte trailer 恰为未压缩 TAR bytes 的 CRC32 与 `ISIZE = tarSize mod 2^32`，两者均四 byte little-endian；trailer 后不得有 trailing byte、第二 member 或 padding。
- packager 只接受机器可读 exact component manifest，不扫描目录“自动加入”文件；未列出、重复、hash/mode 不符或 Quickstart/launcher byte drift 立即失败。
- `securityReviewV02` 固定生成 `bom-v0.2.0.raw.cdx.json`，先用 CycloneDX CLI 1.6 schema 和 component-closure validator 检查，再由 `tools/supply-chain-v02/canonicalize-cyclonedx-v02.mjs` 把唯一允许变化的 `/metadata/timestamp` 规范为 `implementationFreezeSha` 的 UTC committer time并按 RFC 8785 输出 `bom-v0.2.0.cdx.json`。raw/canonical hash、timestamp 和 semantic diff 路径都进入 canonicalization report；任何其他语义变化失败。
- 产品 SBOM 与 `v02-toolchain-component-inventory.json` 分开：前者只列随发行版分发的 Host/Runtime/Native/launcher components，后者列 Gradle plugins 和 CI/review tools。两者并集才能进入 V2-M3-01 的分层 vulnerability coverage；工具不得伪装成产品 component。
- 新 distribution source 的唯一 allowlist 为：`distribution/build.gradle.kts`、`distribution/src/main/kotlin/ah/distribution/V02ReleasePackager.kt`、`distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt`、`distribution/src/main/resources/v0.2/windows/android-app-hardening.cmd`、`distribution/src/main/resources/v0.2/ubuntu/android-app-hardening`、`distribution/src/main/resources/v0.2/schemas/component-baseline-v1.schema.json`、`distribution/src/main/resources/v0.2/schemas/component-manifest-v1.schema.json`、`distribution/src/main/resources/v0.2/schemas/release-manifest-v1.schema.json`、`distribution/src/main/resources/v0.2/schemas/provenance-v1.schema.json`、`distribution/src/main/resources/v0.2/schemas/archive-byte-contract-v1.json`、`distribution/docs/QUICKSTART.md`、`distribution/src/test/kotlin/ah/distribution/V02ReleasePackagerTest.kt`、`distribution/src/test/kotlin/ah/distribution/V02ComponentBaselineValidatorTest.kt`、`distribution/src/test/kotlin/ah/distribution/V02LauncherContractTest.kt` 和 `distribution/src/test/kotlin/ah/distribution/V02ArchiveReproducibilityTest.kt`。供应链新增 source 的唯一 allowlist 为 `tools/supply-chain-v02/canonicalize-cyclonedx-v02.mjs`、`tools/supply-chain-v02/component-inventory-v1.schema.json`、`tools/supply-chain-v02/sbom-canonicalization-v1.schema.json` 及其同目录 self-test。除这些路径、三个 tracked manifest、tracked component baseline、批准的版本传播、`tools/validation/v02-supply-chain-tools.json` 及 CycloneDX plugin/verification 配置外，新增或修改任一 distribution/production/tool path 均失败。
- M0-02 PR 必须以 GitHub `MERGE_COMMIT` 合并到 `main`，禁止 squash、rebase、fast-forward 或本地直推。accepted merge commit 必须恰有两个 parents：first parent 等于该 PR 锁定的 base SHA，second parent 等于经独立复核和 exact-head CI 的 PR head SHA；GitHub PR API 的 `merge_commit_sha`、合并后 `refs/heads/main` 的 push event `after` 与后述 Governance run `head_sha` 必须全部是同一 40-character SHA。该唯一 SHA 才是 `implementationFreezeSha`；PR head、PR base、任一 content commit、后继 main、rerun head、tag 或聊天记录一律不接受。
- accepted Governance 身份固定为仓库 `xiaokh31/androidAppHardening` 中 `.github/workflows/governance.yml` 因上述 merge commit 对 `main` 的 `push` 触发的唯一初次 official run：`event=push`、`head_branch=main`、`head_sha=implementationFreezeSha`、`run_attempt=1`、overall conclusion `success`，且 Ubuntu 与 Windows Governance jobs 均在同一 run/attempt 成功。零个或多于一个匹配 run、仅 PR run、`workflow_dispatch`、Actions rerun (`run_attempt>1`)、不同 workflow path/repository/head 或任一 job 非 success 都保持 `BLOCKED`；不得择优挑选另一个 run。
- freeze lock 的唯一 tracked path 固定为 `docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json`；ancestry 与 changed-path 原始报告的唯一 tracked path 固定为 `docs/v0.2/evidence/V2-M0-02/implementation-freeze-ancestry-report.json` 和 `docs/v0.2/evidence/V2-M0-02/implementation-freeze-changed-path-report.json`。锁按固定字段顺序绑定 repository/default branch、selection method `MERGE_COMMIT_MAIN_PUSH_GOVERNANCE_V1`、PR number/base SHA/head SHA、merge method、merge commit/两个 parent、`implementationFreezeSha`、workflow path/run ID/run attempt/run head SHA/event/conclusion、Ubuntu/Windows job IDs/conclusions、三个 manifest path/size/SHA-256、component baseline path/size/SHA-256、component schema/validator Git blob/size/SHA-256，以及两个报告的 path/size/SHA-256；字段缺失/额外/重排或任一 identity/hash 漂移均失败。
- ancestry report 必须从 Git object 与 GitHub PR API 双源证明 baseline、PR base、PR head、merge parents、merge commit、official main ref 与 `implementationFreezeSha` 的精确关系，并证明 `implementationFreezeSha` 是后续 `validationFreezeSha` 的祖先；changed-path report 必须从 PR base 到 merge commit 枚举按 UTF-8 byte order排序的完整 Git status/path/blob/mode/role，逐项命中本卡 exact allowlist，并证明三个 manifest 与 component baseline/schema/validator 可从 merge commit 的 exact blobs 重建。忽略目录、工作区、artifact、手写路径摘要或仅 hash 一个目录不能替代报告。
- M0-02 只产生 PR、merge 与 official Governance 的可核验外部事实，不提交上述 lock/ancestry/changed-path 数据文件。V2-M3-01 必须在候选前冻结唯一 schema `tools/validation/schemas/v02-implementation-freeze-lock-v1.schema.json` 与 verifier `tools/validation/verify-v02-implementation-freeze-lock.mjs`，包括 Git/GitHub 重新取证、唯一 run 选择、manifest/component closure 和 mutation tests；V2-M3-02 只能用该已冻结 verifier把事实写成上述三个 canonical tracked JSON，并消费验证结果，不能新增字段、修改 schema/verifier、改选 commit/run 或自报 `VERIFIED`。
- Host/Runtime protection logic 相对 development baseline 只允许版本传播与 manifest generator接线；新 distribution 文件只允许上一条 exact allowlist和固定 role。任何未授权 Host/Runtime protection byte 或 distribution path/role变化必须阻塞并返回 `/root`。
- 输出仍为 unsigned APK，版本任务不得新增签名依赖或执行入口。

## Public Interfaces

- `android-app-hardening --version` 输出 `android-app-hardening 0.2.0`。
- REPORT_V1 `tool.version` 输出 `0.2.0`，schema version 保持 1。
- Gradle 入口 `./gradlew v02CandidateManifest`。
- Gradle `:distribution:packageWindowsV02`、`:distribution:packageUbuntuV02`、`:distribution:verifyReleaseV02`；本任务只以 non-release canary mode 执行。
- `node tools/supply-chain-v02/canonicalize-cyclonedx-v02.mjs --raw <path> --implementation-freeze <sha> --output <path> --report <path>`。
- tracked `docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json`、schema `distribution/src/main/resources/v0.2/schemas/component-baseline-v1.schema.json` 与 validator `distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt`；`build/v0.2/candidate-component-manifest.json` 只是可删除的 byte-identical 临时重建物。
- `docs/v0.2/evidence/V2-M0-02/{implementation-manifest,toolchain-manifest,product-contract-manifest}.json`。
- 后续只读接口 `docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json`；本任务不创建它，V2-M3-02 只能按 V2-M3-01 预冻结 schema/verifier写入 exact merge/run facts。

## Security Constraints

- 不接收或使用 keystore、private key、alias 或 password。
- manifest 不记录绝对路径、环境凭据、明文 DEX 或完整 signer digest。
- dependency lock 与 verification metadata 不得弱化。
- 产品能力和安全边界必须与 baseline 等价；版本变化不能绕过旧安全负例。

## Compatibility Requirements

- `minSdk=29`、target/compile/toolchain 保持仓库固定值。
- Host 仍为 Windows/Ubuntu x86_64 开发和 release target。
- Runtime 四 ABI 和 exported symbol surface 不变。
- AHDC v2、ConfigV2、SPV1 和 REPORT_V1 保持字节合同兼容。

## Acceptance Criteria

- 根版本、CLI `--version` 和 REPORT tool version 均为 `0.2.0` 且由单一来源生成。
- Windows/Ubuntu clean `check`, `lint`, Build 和 Governance 退出码为 0。
- 四 ABI Release/R8 产物存在，逐项 SHA-256、size、symbol surface 和 build variant 完整记录。
- 三个 tracked manifest 可从 clean checkout 与 exact freeze Git blobs 重建且 canonical bytes/SHA-256 一致；任一集合、路径、mode 或 byte 变异失败。
- tracked component baseline 能从三个 manifest 和 exact merge-commit Git blobs完整重建并 byte-identical；baseline 中三个 manifest binding、每个 component hash/mode/role/variant/ABI 与固定 schema/validator 全部一致，ignored build 副本缺失时验证仍成功，存在但漂移时失败。
- distribution canary 在 Windows/Ubuntu 各连续构建两次且 archive hashes/entry metadata一致；ZIP 的 directory STORED 空流、file raw-DEFLATE、local/central/EOCD、creator/needed、flags、CRC/size/offset、internal/external attrs与无 trailing bytes，TAR 的 POSIX ustar header/octal/checksum/typeflag/name-prefix/link-dev/padding/two EOF blocks，GZIP 的单 member header/raw-DEFLATE/little-endian CRC32/ISIZE 与无 trailing bytes均和 `archive-byte-contract-v1.json` 逐 byte相等；launcher 对 Temurin `17.0.19+10`、args/Unicode/exit、offline protect、input read-only、unsigned output和 cleanup 正常。
- packager、launcher、archive-internal Quickstart、layout/schema bytes 全部进入 implementation manifest，V2-M4-01 后不得再新增。
- 两个平台 raw SBOM timestamp 可以不同，但 raw schema/component closure 必须相同；规范化后 SBOM bytes 完全一致，report 恰好只记录 `/metadata/timestamp` 语义变化。canonicalizer、schema、Gradle entry 和 tool inventory 均进入 implementation/toolchain manifest，候选后不得修改。
- base-to-HEAD diff 只包含本卡批准的版本传播、三个 manifest、exact distribution allowlist 和 fixed supply-chain plugin/tool-lock/verification配置；Host/Runtime protection bytes 除版本传播与 manifest 接线外与 baseline相同。
- 产品签名能力、动态依赖、绝对路径和敏感材料扫描为零。
- 独立只读复核返回 `P0=0/P1=0/P2=0`。
- PR 以 `MERGE_COMMIT` 合并后，merge commit 的两个 parent、PR base/head、GitHub `merge_commit_sha`、main push SHA 和唯一 `.github/workflows/governance.yml` attempt 1 run head 完全一致；同一 run 的 Windows/Ubuntu jobs均为 success，且该 merge commit 是唯一 accepted `implementationFreezeSha`。任何 PR head替代、squash/rebase、rerun、第二个匹配 run 或 post-merge changed path 越界使任务保持 blocked。
- M0-02 合并 diff 不含 `implementation-freeze-lock.json` 或两个 freeze report；交接明确记录供 V2-M3-01 固定 verifier、供 V2-M3-02 后写 canonical data 的 PR/merge/run事实，且不把未落锁的聊天声明当 tuple 输入。

## Required Tests

- version single-source、CLI/help/report 正向与旧 `0.1.0-dev` 残留扫描。
- manifest schema/field order、空/缺/额外/重复/乱序 path、反斜杠/非 NFC、错误 mode、symlink/gitlink/self-reference、错误 blob/hash/size/ABI、wrong preimage path 和额外 component 负例。
- Host full-flow CLI 回归，输入只读、未签名输出和稳定错误码。
- 四 ABI build/symbol/Release/R8 和 dependency verification。
- Packager allowlist、两次 archive equality、metadata、launcher JRE missing/wrong vendor/version/build、Unicode/args/exit、offline smoke、SBOM placeholder replacement boundary 和 forbidden-entry 负例。
- component baseline 的 missing/extra/duplicate/reordered component、错误 logical/archive path、role/variant/ABI、source blob/mode/size/hash、三个 manifest binding、schema/validator path/hash 及 ignored build 副本替代负例；删除 ignored 副本的正例必须通过。
- ZIP mutation 至少逐项覆盖 directory 非 STORED/非空/非零 CRC-size、file 非 method 8、zlib/gzip wrapper、level/strategy/nowrap/input-drain漂移、flags/version-made/version-needed、local-central 不一致、CRC/size/offset、internal attr、三类 external attr、extra/data-descriptor/comment/archive-comment、DOS rounding、entry order、ZIP64/split/prefix、EOCD disk/count/offset/size 与 trailing byte；双平台独立 parser 必须拒绝每个漂移。
- TAR mutation 至少逐项覆盖 magic/version、octal width/NUL、base-256、unsigned checksum 与 checksum field编码、regular/directory typeflag和size、uid/gid/uname/gname/mode/mtime、name-prefix唯一分割、linkname/dev fields、非零 reserved byte、entry order、payload padding、PAX/GNU/sparse/link、EOF block少于或多于两个及 trailing byte；GZIP mutation至少覆盖 member count、ID/CM/FLG/MTIME/XFL/OS、extra/name/comment/header-CRC、raw-vs-wrapped DEFLATE、level/strategy/nowrap、CRC32/ISIZE endian/value与 trailer 后 byte。
- SBOM 两次/双平台不同 timestamp 正例，以及 duplicate key、serial/build-system、额外 normalization path、component/dependency/array-order drift、错误 implementation-freeze time、raw/canonical schema failure 和 canonical hash mismatch 负例。
- Exact distribution allowlist 的 missing/extra/path/role mutation，以及任一未授权 Host/Runtime protection byte mutation。
- changed-product-path allowlist 与无签名能力扫描。
- 以本地 synthetic Git DAG 和 GitHub API fixture 覆盖正确 two-parent MERGE_COMMIT/main push/attempt-1 run正例，以及 squash、rebase、PR-head 当 freeze、parents 颠倒/缺失/额外、main/run head漂移、workflow/repository/event/branch漂移、attempt>1、零/多 matching run、任一平台 job失败、manifest/component/report hash漂移、祖先关系失败与 changed-path 越界负例；这些测试由 V2-M3-01 的冻结 verifier实现，V2-M3-02 只能执行。

## Required Evidence

- M0-02 exact PR number/base/head、merge method、merge commit/two parents、main push identity、三个 tracked preimage bytes/hash、tracked component baseline/schema/validator bytes/hash、complete-set report 和 production changed-path report。
- Windows/Ubuntu 命令、退出码、toolchain、时间，以及同一 official post-merge Governance run 的 workflow path、run ID、attempt、head SHA、event、branch、conclusion、两个 job ID/conclusion 和不可变 URL。
- CLI/version/report 输出、Host/Runtime component hashes 和四 ABI symbol reports。
- Raw/canonical SBOM hashes、canonicalization reports、toolchain inventory hash、独立审查、发现处置和残余风险。

## Likely Files

- `build.gradle.kts`
- `host/cli/`
- `distribution/`
- `tools/validation/`
- `build/v0.2/`，ignored generated evidence
- `docs/v0.2/evidence/V2-M0-02/`

## Dependencies and Blockers

V2-M0-01 未完成、工具链/依赖漂移、发现非版本保护逻辑变化、任一 manifest/component baseline 不闭合、distribution canary 不可重现、任何四 ABI build 失败、public contract 改变、不能使用 `MERGE_COMMIT`、merge identity不唯一或合并后 official attempt-1 Governance 任一平台失败时保持 blocked。不得 rerun 来替换失败的 accepted run，不得把功能修复混入此任务，也不得把 launcher/Quickstart/packager 延迟到 V2-M4。

## Agent Handoff Requirements

使用分支 `chore/v2-m0-02-candidate-baseline`，只处理 Issue #87 并创建一个对应 PR。交接必须包含版本来源、三个 manifest、唯一 tracked component baseline/schema/validator、distribution/launcher/Quickstart/schema、canary reproducibility/offline smoke、四 ABI、命令/退出码、哈希、changed paths、独立审查，以及 PR base/head、`MERGE_COMMIT`、merge parents/main push和唯一 attempt-1 Governance run facts；不得修改根 HandOff，不得在本 PR 创建 freeze lock，也不得把 PR head 自称 `implementationFreezeSha`。本任务必须先于 V2-M3-01 合并并完成 post-merge PASS；协调者只把 exact merge commit 标记为待 V2-M3-01 verifier与 V2-M3-02 tracked lock共同接受的 freeze fact，并据此发布可供 V2-M3-01 刷新的 `main`。
