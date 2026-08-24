---
id: V2-M4-01
title: "v0.2 安全与供应链复核"
milestone: V2-M4
status: planned
owner_role: security-review-agent
depends_on:
  - V2-M3-03
  - V2-M3-04
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - validate-protected-apk
  - plan-apk-hardening-change
security_sensitive: true
---

## Goal

由未实现被审查产品与验证代码的独立 reviewer 对 exact v0.2 tuple 完成安全、依赖、许可证、SBOM、秘密和发布 component 审查，并生成唯一 `PASS|BLOCKED` release security gate。

## Background

V2-M3-03 与 V2-M3-04 证明性能和回归，但不能替代独立安全/供应链审核。原 v0.1 M4-01 不可启动，本任务是新 release line 的 fresh 审查，不继承旧 M4 状态或输出。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M3-02 product tuple、implementation/component/toolchain manifests。
- V2-M0-02 frozen `securityReviewV02`、CycloneDX plugin/SBOM generator、`v02-supply-chain-tools.json` 与 transitive verification metadata。
- V2-M3-01 frozen SBOM/component/coverage/CVSS validators 和 workflow candidate。
- V2-M3-03 exact tracked `docs/v0.2/evidence/V2-M3-03/{v02-performance-gate.json,v02-performance-artifact-manifest.json,performance-run-lock.json}` 三件，以及 V2-M3-04 exact tracked `docs/v0.2/evidence/V2-M3-04/{v02-release-validation-gate.json,v02-compatibility-matrix.json,v02-release-validation-artifact-manifest.json,validation-run-lock.json}` 四件；七个文件都必须逐项通过 frozen path/blob/hash/member-closure/official-run validator，缺一项不得开始。
- Gradle locks、verification metadata、THIRD_PARTY_NOTICES、Host/Runtime Release artifacts。
- V2-M0-02 frozen distribution packager、launchers、archive schema 和 archive-internal Quickstart。
- Threat model、ADR、安全 sensitive task reviews 和 residual risks。
- V2-M3-01 frozen zero-byte `tools/security-review-v02/config/osv-scanner-empty-v2.5.1.toml`、raw security artifact-manifest schema/generator/validator 与 security-run-lock schema/validator；本任务只执行，不能创建或修改这些合同 bytes。

## Expected Outputs

- official artifact raw member `reports/security-review-v0.2.0.md`，以及 tracked `independent-review.md` 与 `local-validation.md`。
- canonical tracked `docs/v0.2/evidence/V2-M4-01/bom-v0.2.0.cdx.json`。
- official artifact raw members `reports/v02-sbom-canonicalization.json`、`reports/v02-component-coverage.json`、Native vendor advisory source/reachability、tool-binary source/review、license、vulnerability、secret/signing/plaintext/symbol 与 OSV raw reports；这些细项只由 tracked `v02-security-artifact-manifest.json` 闭包，不另行复制为 tracked output。
- canonical tracked `docs/v0.2/evidence/V2-M4-01/v02-security-artifact-manifest.json`，逐 member 闭合唯一 official raw security artifact。
- canonical tracked `docs/v0.2/evidence/V2-M4-01/v02-rc-component-manifest.json`。
- secret/signing-capability/plaintext/symbol scan reports作为 official artifact raw members，不另建 tracked 副本。
- canonical tracked `docs/v0.2/evidence/V2-M4-01/release-gate-v0.2.0.json`，decision `PASS|BLOCKED`。
- canonical tracked `docs/v0.2/evidence/V2-M4-01/security-run-lock.json`，绑定唯一 official run/head/attempt/artifact 与 artifact manifest、SBOM、component manifest、gate 四个 exact tracked hashes。

## In Scope

- Host untrusted input、path/length/resource limits、atomic failure、report leakage。
- Runtime authentication order、signer/integrity、Native memory/key lifetime、risk policy 和 ABI surface。
- Dependencies、plugins、actions、tool binaries、licenses、sources、hashes 和 known vulnerabilities。
- `SUPPLY_CHAIN_TOOLCHAIN.md` 的 exact tool assets、single offline DB snapshot、component coverage、severity 和 dual-platform equivalence。
- Frozen distribution/launcher/Quickstart/package/evidence validator 的 source、component 和 signing/network surface。
- Repository、Release component staging、logs 和 evidence manifests 的敏感扫描。
- v0.2 security claims、compatibility claims 和 evidence integrity。

## Out of Scope

- 修改生产代码、Gradle plugin/依赖/verification metadata、tool lock、validator、schema、workflow、severity、tests、distribution/launcher/Quickstart、tuple 或 prior evidence。
- 对 Android 平台本身作全面安全审计。
- 使用生产 keystore/私钥或真实客户 APK。
- 创建 final archives、launcher 或 release notes。

## Implementation Decisions

- Reviewer 不得是 V2-M0-02、V2-M3-01 或相关安全 sensitive 产品实现的主要作者。
- 只执行候选前冻结的 `securityReviewV02` 和 `.github/workflows/v02-security-review.yml`；任何工具/validator/schema/workflow/Gradle byte drift 使 tuple 失效，不得在本任务修补。
- SBOM generator 固定 `org.cyclonedx.bom` `3.4.1`、CycloneDX JSON 1.6；schema validator 固定 CycloneDX CLI `0.33.1`；漏洞扫描固定 OSV-Scanner `2.5.1`。asset、commit、size、SHA-256 和 Gradle transitive verification 必须逐项匹配 `SUPPLY_CHAIN_TOOLCHAIN.md`。
- Windows/Ubuntu 各生成 raw SBOM并先做 1.6 schema/component closure；frozen canonicalizer 只能把 `/metadata/timestamp` 改成 implementation freeze UTC并按 RFC 8785 输出 canonical SBOM。两个 raw timestamp 可不同，但 canonical bytes 必须相同；canonicalization report 记录两组 raw/canonical hashes且 semantic diff path 恰好一个。serial/build-system、array/component/dependency 或任何其他 normalization 都 `BLOCKED`。
- 只有一个 Ubuntu acquisition job 可联网生成 OSV DB snapshot并获取全部 Native/tool release/advisory source。toolchain inventory 的每个 component（包括 coverage 为 `osv-package` 的 Gradle、AGP `9.3.0`、Kotlin `2.4.10`、CycloneDX Gradle plugin `3.4.1`、JNA `5.19.1|5.6.0`、Jazzer `0.29.1`、Android command-line tools，以及 verification metadata/resolved graph中的每个传递 Maven `group:name:version`）都必须有独立 record并命中 `SUPPLY_CHAIN_TOOLCHAIN.md` 的唯一 fixed `sourceProfile`、canonical coordinate、artifact machine-lock identity与逐 profile pagination/detail closure；transitive component不得继承root project/profile。generic Maven record必须逐字段复算合同的 repository/POM/SCM/tag/release/advisory无自由选择生成器；以 GitHub API `2022-11-28`、完整分页、全部 detail URL ETag 条件复验和 24 小时 fresh window取得 raw source。任一 component遗漏/重复、endpoint/页/detail/header/schema/version/support-status不可用或冲突立即 `UNKNOWN/BLOCKED`，没有在线、cache、镜像、OSV、NVD 或人工 fallback。
- OSV sorted file manifest/tree hash 作为同一 immutable DB artifact 给 Windows/Ubuntu。scan 必须在 snapshot 完成后 24 小时内、network-deny 下逐字使用 `SUPPLY_CHAIN_TOOLCHAIN.md` 的 absolute-path argv template。启动前只验证已存在的 absolute scanner、canonical config、SBOM和DB path/bytes/identity；report parent必须是新建的 job-owned empty canonical regular directory且无 symlink/junction/reparse component，exact report target必须不存在，禁止预先校验或伪造尚未生成 report 的 size/hash/identity。process exit后才验证 target为同一 canonical regular file、link count `1`、size `1..268435456`、SHA-256与frozen JSON schema，随后原子封存为只读 raw member；missing/旧文件/空/超限/malformed/path或hash drift立即 `BLOCKED`。config 必须是 validation freeze Git blob中的 mode `100644`、`0` bytes、SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`；canonical path、argument token/order 或 byte 任一漂移均失败。launcher 使用合同逐平台 exact empty-environment allowlist，Windows同时隔离 APPDATA/LOCALAPPDATA，保存 argv/environment/file/network trace，并证明父目录、仓库、SBOM 同目录、用户 config及 proxy/config/cache hostile env 都没有影响；禁止第二快照、scan-time download、OSV/deps.dev/registry fallback、call analysis和 inherited ignore。
- 产品 SBOM 与 toolchain inventory 的并集逐项且恰好一次归类为 `osv-package|native-vendor-advisory|first-party-source|tool-binary`。只有 `osv-package` 走 frozen offline scanner；Mbed TLS/TF-PSA-Crypto 使用合同固定的 official vendor advisory/release snapshots和独立 reachability；本仓库源码与工具二进制分别执行固定 closure/static-analysis/provenance/advisory review。未知/不支持 class、generic purl、commit冒充版本、空/缺 coverage、false-zero、数据库/tool/schema/source不可用或跨平台不一致直接 BLOCKED。
- 非 withdrawn 漏洞使用冻结 CVSS v3.1 evaluator复算最高分；Critical/High/UNKNOWN 直接 BLOCKED。Medium 需要精确 ID/component、owner、impact、`/root` 接受和未过期 expiry；Low/None 进入 residual risk。
- `v02-rc-component-manifest.json` 逐项列出同 tuple Host Release artifacts、Runtime 四 ABI、runtime dependencies、toolchain 和 SBOM hash。
- 未解决 Critical/High 直接 BLOCKED。Medium 需要 owner、impact、expiry 和 `/root` 明确接受；Low 进入 residual risk register。
- 未知 license、unlocked/dynamic dependency、来源不明 binary、hash mismatch 或 component omission 直接 BLOCKED。
- Review 不生成 M4-02 final archive，也不把未创建的 archive 当作输入。
- Official run 前必须由 frozen validator逐项消费并复算 `docs/v0.2/evidence/V2-M3-03/{v02-performance-gate.json,v02-performance-artifact-manifest.json,performance-run-lock.json}` 三件与 `docs/v0.2/evidence/V2-M3-04/{v02-release-validation-gate.json,v02-compatibility-matrix.json,v02-release-validation-artifact-manifest.json,validation-run-lock.json}` 四件的 PASS、Git blob、member closure和official identity；只存在 build 输出、缺任一 tracked path、替代路径、copy drift 或 lock hash不符时不得运行 security gate。
- Frozen verifier 禁止自动推断 phase。acquisition/setup 前必须执行 `node tools/governance/verify-v02-post-freeze-head.mjs --head HEAD --policy docs/v0.2/post-freeze-path-policy-v1.json --stage V2-M4-01 --phase pre-run`，要求 HEAD 恰好是已接受 V2-M3-04 stage head且所有 V2-M4-01 output path为零；evidence PR current HEAD执行同命令但 `--phase evidence-pr`，要求 base为该 accepted head、changed paths只属于 V2-M4-01 exact allowlist且全部 required outputs已存在；合并后的 main执行 `--phase post-merge`，要求唯一 merge commit与唯一 official post-merge Governance `runAttempt=1` identity匹配。三种 phase 都证明 V2-M3-02/M3-03/M3-04 evidence bytes、七个 tuple manifest preimage/entry blobs、五组 candidate/live workflow和 validation freeze bytes不变；本任务无权修改 policy、verifier、早期 evidence、manifest 或 workflow。
- Official run 先在 ignored `build/reports/security/v0.2/` 生成报告并上传恰好一个 final evidence artifact `v02-security-raw-v0.2.0-rc.1`；它与第 4 节恰好一个跨平台 OSV DB acquisition artifact 是本 run 仅有的两个 artifact identity。final artifact 只能包含本轮 raw source、raw report、canonical output和脱敏执行证据；不得包含 manifest 本身、run-lock、APK、DEX、证书或秘密。上传完成且 API 返回 numeric artifact ID/size/digest/retention 后，frozen generator 从下载后的 archive bytes和官方 API生成 `v02-security-artifact-manifest.json`；该 manifest 不是 artifact member，避免自引用。
- `v02-security-artifact-manifest.json` 顶层字段顺序固定为 `schemaVersion,taskId,releaseLine,candidateId,productTupleSha256,sourceCommitSha,implementationFreezeSha,validationFreezeSha,repository,workflowPath,workflowSha256,runId,runAttempt,artifactId,artifactName,artifactSizeBytes,artifactDigest,artifactArchiveSha256,osvDatabaseArtifactId,osvDatabaseArtifactName,osvDatabaseArtifactSizeBytes,osvDatabaseArtifactDigest,osvDatabaseTreeSha256,entries`。固定 `schemaVersion=1`、`taskId=V2-M4-01`、`releaseLine=v0.2`、`candidateId=v0.2.0-rc.1`、`runAttempt=1`；OSV DB 五字段必须逐字等于 `reports/v02-osv-db-snapshot.json` 和 official API，不能指向 final evidence artifact。每个 `entries` 元素字段顺序固定为 `path,role,platform,productTupleSha256,sizeBytes,sha256,contentType`，其中 tuple 必须逐项等于顶层 tuple。
- Artifact entry 集合必须与解压后的全部 regular member逐项且恰好一次相等，按 UTF-8 path bytes 严格递增；拒绝 directory entry、symlink、hardlink、duplicate/case-fold/NFC collision、反斜杠、绝对/`.`/`..` path、未列 member 或 manifest 外额外 member。固定单件路径为 `windows/sbom/bom-v0.2.0.raw.cdx.json`、`ubuntu/sbom/bom-v0.2.0.raw.cdx.json`、`windows/sbom/bom-v0.2.0.cdx.json`、`ubuntu/sbom/bom-v0.2.0.cdx.json`、`windows/osv/v02-osv-report.raw.json`、`ubuntu/osv/v02-osv-report.raw.json`、`reports/v02-sbom-canonicalization.json`、`reports/v02-product-component-inventory.json`、`reports/v02-toolchain-component-inventory.json`、`reports/v02-component-closure.json`、`reports/v02-osv-db-snapshot.json`、`reports/v02-osv-evaluation.json`、`reports/v02-component-coverage.json`、`reports/v02-native-advisory-reachability.json`、`reports/v02-tool-binary-review.json`、`reports/licenses.json`、`reports/dependency-verification.json`、`reports/secret-scan.json`、`reports/signing-capability-scan.json`、`reports/plaintext-scan.json`、`reports/symbol-scan.json`、`reports/v02-rc-component-manifest.json`、`reports/release-gate-v0.2.0.json`、`reports/security-review-v0.2.0.md`、`reports/residual-risks.json`、`reports/environment.json`、`reports/commands.json`、`reports/network-trace.json` 与 `reports/cleanup.json`；缺任一项失败。
- 固定单件 path→role 映射不得由实现者选择：两个 `*.raw.cdx.json` 为 `raw-sbom`；两个 platform canonical SBOM 为 `canonical-sbom-platform-copy`；两个 `v02-osv-report.raw.json` 为 `raw-osv-report`；`v02-sbom-canonicalization.json` 为 `sbom-canonicalization`；两个 component inventory 分别为 `product-component-inventory` 与 `toolchain-component-inventory`；`v02-component-closure.json` 为 `component-closure`；`v02-osv-db-snapshot.json` 为 `osv-database-manifest`；`v02-osv-evaluation.json` 为 `osv-evaluation`；`v02-component-coverage.json` 为 `component-coverage`；Native/tool 两份 review 分别为 `native-advisory-review` 与 `tool-binary-review`；其余 `reports/` 文件 role 逐字等于去掉扩展名后的固定 basename。`platform` 对 `windows/` 固定为 `windows-x86_64`，对 `ubuntu/` 固定为 `ubuntu-x86_64`，对 `acquisition/` 固定为 `ubuntu-acquisition`，对 `reports/` 固定为 `aggregate`；固定 `.json` 的 `contentType=application/json`，Markdown 为 `text/markdown; charset=utf-8`。
- 变长 raw source path必须逐字使用 `SUPPLY_CHAIN_TOOLCHAIN.md` 的 SHA-256机器映射：OSV catalog固定 `acquisition/osv/catalog/`，ecosystem固定 `acquisition/osv/ec-<exact-name-sha256>/`，tool固定 `acquisition/tool/tc-<component-preimage-sha256>/<sourceProfile>/<endpointKind>-<endpoint-preimage-sha256>/`，Native使用对应 `nc-`规则，index detail每个 URL独占目录。Maven component preimage逐字为 `sourceProfile NUL maven NUL group NUL name NUL version`，非 Maven/Native使用合同各自封闭 preimage；endpoint preimage逐字为 `endpointKind NUL exactInitialEndpointUrl`，component preimage不得加入额外 release、候选或默认 URL字段。每个目录必须含 `source-manifest.json`，且每个 HTTP response 恰有成对的 `page-<六位十进制序号>.headers.json` 与 `page-<同序号>.body`；页号从 `000001` 连续递增，single/detail目录恰好一页，执行者无命名权。prefix 到 role 固定为 `acquisition/osv/=osv-database-source`、`acquisition/native/=native-advisory-source`、`acquisition/tool/=tool-release-advisory-source`；`source-manifest.json` role 固定为对应 prefix role加 `-manifest`，headers role 加 `-headers`，body role 加 `-body`，body `contentType` 必须逐字等于已冻结 profile允许且 raw headers记录的唯一 MIME。自由 role、`other`、unknown MIME、未定义/重复 initial URL、hash/path映射冲突或未知 prefix 失败。
- 任务 PR 必须把 artifact 内 `windows|ubuntu/sbom/bom-v0.2.0.cdx.json` 已证明相同的 bytes、`reports/v02-rc-component-manifest.json` 和 `reports/release-gate-v0.2.0.json` byte-for-byte复制到三个固定 tracked paths，把生成的 artifact manifest 写入第四个固定 tracked path，并提交 frozen schema验证的 `security-run-lock.json`。run-lock 固定绑定 repository、candidate head、workflow path/hash、run ID、`runAttempt=1`、OSV DB artifact identity/tree hash、final artifact ID/name/size/digest/retention/archive hash及四个 tracked文件各自 path/mode/Git blob/size/SHA-256；run-lock 不包含自身。只存在 build/CI artifact、替代路径、聊天 hash、raw member omission 或任一 tracked copy drift 均不算交付。
- 除 canonical SBOM、component manifest、release gate、security artifact manifest、security run lock，以及本任务 independent-review/local-validation 文档外，不得提交 M4-01 tracked evidence 文件；license/vulnerability/canonicalization/coverage/Native/tool/OSV/scan raw report即使内容有效，也只能作为 final evidence artifact member被 manifest引用。替代 tracked path、重复 copy 或摘要摘录都破坏最小闭包。

## Public Interfaces

- Gradle `./gradlew securityReviewV02`。
- `build/reports/security/v0.2/release-gate-v0.2.0.json`。
- `build/reports/security/v0.2/bom-v0.2.0.cdx.json`。
- `build/reports/security/v0.2/v02-rc-component-manifest.json`。
- `node tools/governance/verify-v02-post-freeze-head.mjs --head HEAD --policy docs/v0.2/post-freeze-path-policy-v1.json --stage V2-M4-01 --phase pre-run|evidence-pr|post-merge`；每次调用必须选择恰好一个枚举值。
- `docs/v0.2/evidence/V2-M4-01/{bom-v0.2.0.cdx.json,v02-rc-component-manifest.json,release-gate-v0.2.0.json,v02-security-artifact-manifest.json,security-run-lock.json}` 是 downstream 唯一输入路径。
- Decision 枚举仅 `PASS|BLOCKED`。
- Canonical `.github/workflows/v02-security-review.yml`；本任务无权编辑。

## Security Constraints

- 审查环境不导入任何生产签名材料。
- 报告不嵌入秘密、完整敏感文件、真实 APK、明文 DEX、device serial 或用户绝对路径。
- test-only signing material 必须与 production dependency/archive exclusion 证明同时检查。
- 任何 authentication bypass、plaintext disk write、uncontrolled Native memory error 或证据 identity mismatch 阻塞发布。

## Compatibility Requirements

- 审查对象与 V2-M3-03/V2-M3-04 为同一 tuple、implementation manifest 和 toolchain。
- 四 ABI Runtime、Windows/Ubuntu Host 和 32-cell compatibility claim 完整。
- 只允许 fresh VERIFIED cells 形成正向声明；UNVERIFIED 保持明确。
- ARM-only 和 x86 non-risk 边界出现在 checklist 和 residual risks。

## Acceptance Criteria

- `securityReviewV02` 退出码 0 且 release gate decision 为 PASS。
- security workflow/runner/schema/tool-lock hashes 与 frozen tuple 一致，official run 唯一、main、无 inputs、`runAttempt=1`。
- SBOM/locks/verification metadata/component manifest 一致；JNA/Jazzer与resolved graph中的每个传递 Maven component都有独立固定 sourceProfile、canonical coordinate、artifact/release/advisory endpoint、size/hash/license，root profile覆盖不能算完成。
- SBOM 是 CycloneDX JSON 1.6；Windows/Ubuntu raw schema/closure 均通过，canonicalizer 只规范 timestamp且两个平台 fixed CLI 对同一 canonical hash验证通过；distributed component 集合与 frozen component baseline完整一致。
- 单一 OSV snapshot 与 Native/tool authoritative snapshots 均未超过 24 小时、完整 endpoint/API-version/pagination/ETag/hash/provenance/coverage/reachability 可复算，所有 `tool-binary` 与 Maven component逐项命中唯一 source profile且没有 fallback；Windows/Ubuntu OSV 使用同一 tree hash、同一 zero-byte config并保持断网，report target启动前不存在且仅在退出后验证/封存。每个 component恰有一个固定 coverage class；未解决 Critical/High/UNKNOWN、未知 license/binary/mapping、unlocked/dynamic dependency 均为零，Medium 接受记录完整。
- Frozen distribution packager、launchers、archive-internal Quickstart 和 release validators 全部进入 reviewed component/source closure；不存在“security PASS 后再新增的发布 byte”。
- `pre-run`、`evidence-pr` 与合并后 `post-merge` 三个显式 phase 的 frozen post-freeze verifier均退出 0；V2-M4-01 是连续 stage，pre-run无本阶段 output，evidence PR outputs完整且 changed paths逐项属于 exact allowlist，post-merge identity唯一，早期 stage evidence、七 manifest与五组 workflow bytes零漂移。
- 两个上游任务的七个 canonical tracked inputs（M3-03 三件、M3-04 四件）均由 frozen validator逐项通过 lock/member closure；四个 canonical tracked outputs（SBOM、component manifest、release gate、security artifact manifest）与 official artifact bytes或其 frozen generator source byte-identical。`v02-security-artifact-manifest.json` 覆盖 artifact 每个 raw/canonical member 的 path/role/platform/tuple/size/hash且无额外或遗漏；security run lock 的 repository/head/workflow/run/attempt/artifact identity、archive hash及四个 tracked output Git blobs可由 official API、archive bytes和 Git blobs复算。
- 产品签名能力、private key、keystore、password、真实客户 APK 和明文客户 DEX 扫描为零。
- V2 performance/validation evidence 的 tuple、commit 和 hashes 可直接复算，无旧 PASS substitution。
- 独立 reviewer 最终报告 `P0=0/P1=0/P2=0` 并明确 residual attacker capabilities。

## Required Tests

- Dependency lock/verification、SBOM schema、license source 和 component manifest consistency。
- 固定 tool/version/tag/commit/asset hash、CycloneDX 1.6、Gradle transitive verification 和禁止 CycloneDX `sign` 负例；JNA/JNA Platform `5.19.1|5.6.0` 与 Jazzer/Jazzer API `0.29.1` 的 coordinate、Maven URL、release/advisory endpoint、artifact hash/size错配都必须失败。
- 单一 DB snapshot、24 小时 freshness、manifest/tree/artifact hash、network deny、exact zero-byte `--config`、`--offline --no-resolve --all-packages`、same-snapshot dual-platform 和禁止 fallback/download-at-scan 负例；必须覆盖 config flag缺失/重复/错 path、LF/注释/空白、symlink/untracked file、父/仓库/SBOM目录/用户 hostile ignore config、environment override 和 call-analysis，以及report parent symlink/junction/reparse、target预存在、启动前错误要求report hash、退出后missing/symlink/hardlink/empty/oversize/malformed/path replacement/hash drift负例。
- 每个 tool/Maven source profile 的 wrong/missing release或advisory endpoint、GitHub API version漂移、缺/重/乱分页、重复 ID、missing/changed ETag、304/200复验漂移、stale/future retrievedAt、schema/version/support-status冲突、cache/mirror/OSV/NVD/manual fallback 与 profile 重复/遗漏负例；必须从resolved graph和verification metadata重建集合，注入transitive component缺失、借用root profile、repository混用、POM/SCM/tag不唯一、generic profile手填字段、artifact URL/size/hash drift、named JNA/Jazzer误入generic，以及component preimage新增未定义 release URL字段或endpoint-kind不绑定URL的raw-path变体。
- Vulnerability coverage/severity gate、unsupported/unknown mapping、UNKNOWN/no-CVSS、Medium acceptance expiry、unknown license/binary 负例。
- Raw two-run timestamp 差异、canonical timestamp/serializer drift、serial/build-system/extra normalization path、component/array semantic drift；native commit-only、generic purl、unsupported ecosystem、vendor source/page缺失、false-zero、reachability缺失与 tool-binary误归类负例。
- Product signing capability、secret/key/APK/DEX/path/symbol/absolute-claim scans。
- Tuple/evidence mismatch、old artifact injection、missing performance/compatibility report 负例。
- Post-freeze 缺/错误/重复 `--stage` 或 `--phase`、phase自动推断、pre-run提前/部分/全部出现 output、evidence-pr missing/partial output、post-merge错误 merge/run、wrong/non-contiguous stage、policy外 path、修改 policy/verifier、早期 evidence drift、七 manifest drift、candidate/live workflow drift和 current HEAD未复验负例。
- Canonical tracked output或 `v02-security-artifact-manifest.json` absent、仅 build output、alternate path、copy byte drift、wrong run/head/attempt/artifact/hash 和 malformed security-run-lock 负例。
- Raw artifact manifest 的 missing/extra/duplicate/乱序 member、错误 path/role/platform/tuple/size/hash/content-type、非法/不连续 acquisition page、unknown prefix/free role、symlink/path traversal/NFC/case collision、把 manifest/run-lock放入 artifact、缺/第二 OSV DB artifact、缺/第二 final evidence artifact、第三 artifact、DB tree/identity漂移、final artifact digest/archive hash漂移，以及四个 tracked binding 少一项负例。
- M3-03 三个与 M3-04 四个 canonical input逐路径 missing、仅 build output、alternate path、copy/blob/hash/member-closure drift、wrong run/head/attempt/artifact 和 malformed locks 负例；尤其删除 `v02-performance-artifact-manifest.json` 或 `v02-release-validation-artifact-manifest.json` 时必须在 acquisition前失败。
- Independent-reviewer identity and authorship separation check。

## Required Evidence

- 全部 scan/tool versions/commits/assets、commands、exit codes、environment、time 和 tuple。
- SBOM、component manifest、raw artifact manifest、locks、licenses、vulnerabilities、security gate hashes。
- DB 与 Native/tool release/advisory snapshot exact URLs、GitHub API version、完整分页、ETag/条件复验、timestamps、file manifest/tree/artifact hashes、分层 coverage/reachability map、raw OSV JSON、raw/canonical SBOM reports 和 dual-platform canonical comparison。
- Official security run/workflow/artifact identity、artifact archive/member closure、security-run-lock bytes/hash、tracked/artifact byte-equality和四个 downstream input hashes。
- Reviewer independence、findings/disposition、Medium acceptance 和 residual risks。
- Test-signing cleanup 与 product signing exclusion proof。

## Likely Files

- `docs/v0.2/security/security-review-v0.2.0.md`
- `docs/v0.2/evidence/V2-M4-01/`

## Dependencies and Blockers

任一 V2-M3 gate 非 PASS、post-freeze stage/changed-path/earlier-evidence/manifest/workflow closure 失败、frozen security/distribution/`THIRD_PARTY_NOTICES.md` byte mismatch、tuple/component mismatch、reviewer 不独立、工具/数据库不可用、snapshot stale/不一致、coverage不完整、存在未解决 Critical/High/UNKNOWN、notice缺口、未知 license/binary/mapping 或 production signing/sensitive material 时保持 blocked。Notice缺口只记录 finding并要求新 candidate，不在本任务修改 frozen notice。不得通过修改 frozen gate/notice/policy/verifier、在线 fallback、降低 severity、删除 component 或省略 evidence 获得通过。

## Agent Handoff Requirements

使用分支 `chore/v2-m4-01-security-review`，只处理 Issue #92 并创建一个对应 PR。交接必须包含 reviewer independence、全部 findings、commands/exits、SBOM/component hashes、decision 和 residual risks；不得修改根 HandOff。
