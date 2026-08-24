---
id: V2-M4-02
title: "v0.2 可重现发布打包"
milestone: V2-M4
status: planned
owner_role: host-pipeline-agent
depends_on:
  - V2-M4-01
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - implement-apk-postprocessor
  - validate-protected-apk
  - plan-apk-hardening-change
security_sensitive: true
---

## Goal

从 V2-M4-01 已审查的 exact component 集合生成可重现的 Windows x86_64 与 Ubuntu x86_64 v0.2.0 发布包、校验和和 provenance，并完成断网 smoke test。

## Background

Distribution packager、launcher、archive schema 和 archive-internal Quickstart 已由 V2-M0-02 冻结并由 V2-M4-01 审查。旧 M4-02 不可启动；本任务只执行 exact reviewed bytes 形成 fresh archives，不实现或修改发行逻辑。产品仍不签名 APK，发布包也不包含 fixture 或测试证书。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M4-01 PASS tuple，以及 fixed tracked `docs/v0.2/evidence/V2-M4-01/{bom-v0.2.0.cdx.json,v02-rc-component-manifest.json,v02-security-artifact-manifest.json,release-gate-v0.2.0.json,security-run-lock.json}` 五个输入；缺少任一路径都不得开始打包。
- V2-M0-02 Host/Runtime Release components。
- V2-M0-02 frozen packager、Windows/Ubuntu launchers、archive schema、`distribution/docs/QUICKSTART.md` 和 canary evidence。
- V2-M3-04 exact-tuple full-flow compatibility evidence。
- Apache-2.0 LICENSE、THIRD_PARTY_NOTICES 和 Eclipse Temurin `17.0.19+10` prerequisite。

## Expected Outputs

- `androidAppHardening-0.2.0-windows-x86_64.zip`。
- `androidAppHardening-0.2.0-ubuntu-x86_64.tar.gz`。
- `SHA256SUMS`、byte-identical SBOM、release manifests 和 provenance。
- Windows/Ubuntu two-build reproducibility 和 offline smoke reports。
- Canonical tracked `docs/v0.2/evidence/V2-M4-02/v02-release-packaging-gate.json` 与 `v02-release-artifact-manifest.json`，完整枚举 official artifact中的两份 archive、SHA256SUMS、SBOM、manifest、provenance与raw smoke/report member。
- tracked canonical `docs/v0.2/evidence/V2-M4-02/release-artifact-lock.json`，固定唯一 official CI artifact identity 与 archive member hashes。
- `v02-release-artifact-manifest.json` 只接受候选前冻结的 `tools/release-evidence-v02/schemas/release-packaging-artifact-manifest-v1.schema.json` 与 `tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs`；本任务不生成或修改 schema/validator。

## In Scope

- 执行 frozen packager，把 reviewed launchers、Host JAR/dependencies、四 ABI Runtime、licenses、archive-internal Quickstart、SBOM 和 manifests 组装为 final archives。
- 复核 frozen `archive-byte-contract-v1.json` 的 ZIP/TAR/GZIP 逐字段 bytes、entry order、timestamps、permissions、owner/group、compression、filenames、Temurin `17.0.19+10`、Unicode relative paths 和 argument/exit forwarding。
- `--help`、`--version`、`protect`、input read-only、unsigned output 和 JSON schema smoke。

## Out of Scope

- APK signing、keystore/alias/password、certificate management 或 app-store publish。
- Bundling JRE、online tool download、macOS 或非-x86_64 Host package。
- 添加/替换 V2-M4-01 未审核 component。
- 修改或新增 `distribution/`、launcher、Quickstart、packager、schema、validator 或 workflow bytes。
- 在 archive 中包含 fixture APK、test certificate、private key、source map 或 debug symbol。

## Implementation Decisions

- 包名固定为上述两个文件；root layout 固定 `bin/`、`lib/`、`runtime/`、`docs/QUICKSTART.md`、`LICENSE`、`THIRD_PARTY_NOTICES.md`、`bom.cdx.json`、`release-manifest.json`。
- Windows/Ubuntu launcher、archive-internal Quickstart、layout/schema 和 packager bytes 必须逐项等于 V2-M4-01 reviewed component/source closure；本任务只执行，不生成新模板或代码。
- 在打包前用 frozen validator逐字读取五个 V2-M4-01 tracked 输入，复算 security-run-lock 的 official repository/head/run ID/`runAttempt=1`，并从 accepted V2-M4-01 merge commit 的 Git objects分别复算 `bom-v0.2.0.cdx.json`、`v02-rc-component-manifest.json`、`v02-security-artifact-manifest.json` 与 `release-gate-v0.2.0.json` 四个 `path,mode,blob,sizeBytes,sha256` 绑定。security-run-lock 是第五个输入而不是其自身 blob binding；四个被绑定文件任一 missing、alternate path、mode/blob/size/hash/bytes drift或 lock遗漏都在任何 archive生成前失败。
- Archive 时间取 implementation freeze commit time并转 UTC；ZIP 使用 ZIP32、UTF-8 flags、DEFLATE level 9、无 descriptor/extra/comment、UTC DOS偶数秒与固定 mode；TAR 使用 POSIX `ustar`、uid/gid 0、空 uname/gname、固定 mode/mtime且无 PAX/GNU/link；GZIP 使用 `CM=8,FLG=0,MTIME=freeze,XFL=2,OS=3`、无可选字段与 level 9。所有常量逐字读取 frozen `archive-byte-contract-v1.json`，本任务无权选择或修改。
- Runtime 目录逐 ABI包含 V2-M4-01 审核的 exact `libah_runtime.so`。
- Smoke harness 在 archive 外从 frozen source 构建 fixture，生成一次性证书和 signed input，挂载 `work/input/signed-fixture.apk`，结束后清理。
- SBOM 必须与 security PASS 字节相同；任何新增/替换 component 使 security gate 失效。
- 只有 `/root` 可在 V2-M4-01 PASS 后对 `main` 执行一次无 input 的 frozen `v02-release-packaging.yml`；要求 `runAttempt=1`、固定 concurrency、无 caller/external artifact。失败不得修改或 rerun 同 tuple。
- Final archives 只保存在同 repository 该唯一 official run 的一个 immutable artifact。任务 PR 必须提交 canonical `release-artifact-lock.json`，字段顺序/schema由 V2-M3-01 冻结，记录 repository、head SHA、run ID/attempt、artifact numeric ID/name/size/GitHub digest、created/expires UTC、retention和两个 archive的 name/size/SHA-256；ID/hash 由 `/root` 通过 official API核验，不能由 worker或 workflow input提供。
- `v02-release-artifact-manifest.json` 按 `identity-path-policy-v1.json.canonicalPaths.artifactManifestSchemas.release-packaging` 与 `.artifactManifestValidators.release-packaging` 固定的 schema/validator、UTF-8 path顺序，列出 official artifact每个 member的 path、role、size、SHA-256、tuple、source run/artifact；release-artifact-lock绑定该 manifest与packaging gate的 Git blob/size/hash并从 official artifact复算，missing/extra/alternate member失败。
- 打包前运行 frozen post-freeze verifier `--stage V2-M4-02 --phase pre-run`并要求本任务outputs缺失；evidence PR运行`--phase evidence-pr`，合并后main运行`--phase post-merge`。V2-M4-02 changed paths只能是机器 policy本阶段 exact allowlist，M3/M4-01 evidence、七 manifest和五组 workflow bytes不得改变。

## Public Interfaces

- Gradle `:distribution:packageWindowsV02`。
- Gradle `:distribution:packageUbuntuV02`。
- Gradle `:distribution:verifyReleaseV02`。
- Launchers `bin/android-app-hardening.cmd` 与 `bin/android-app-hardening`。
- `release-manifest.json` v0.2 schema、`SHA256SUMS`、`bom.cdx.json`、`provenance.json`。
- `node tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs --manifest docs/v0.2/evidence/V2-M4-02/v02-release-artifact-manifest.json`。
- Canonical `.github/workflows/v02-release-packaging.yml`；本任务无权编辑。

## Security Constraints

- Archive/launcher 不接受或传递 private key、keystore、alias、password，不调用 APK signing tool/API。
- 仅打包 security-reviewed component exact bytes。
- 不含 fixture、test signer、明文 DEX、debug symbol、source map、credential 或用户路径。
- Smoke output必须由 product 生成为 unsigned；如安装，只能由外部 harness 用同一测试证书签副本。

## Compatibility Requirements

- Host：Windows x86_64、Ubuntu x86_64，预安装 Eclipse Temurin `17.0.19+10`。
- Runtime：`armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 全部存在。
- Input 仍为 standalone signed APK、`minSdk >= 29`；unsupported inputs 保持稳定错误码。
- ARM-only 和 exact VERIFIED cell claim 不扩大。

## Acceptance Criteria

- 三个 Gradle packaging/verify tasks 退出码为 0。
- 每个平台从 clean source 连续构建两次，archive SHA-256 分别完全一致。
- Archive entries、metadata、manifest、SHA256SUMS、SBOM、provenance 和 security component manifest 全部一致。
- Packager/launcher/Quickstart/schema/validator/workflow bytes 与 tuple 和 V2-M4-01 reviewed hashes 一致；official run 唯一且 `runAttempt=1`。
- 在断网 Windows/Ubuntu 环境、预安装 Eclipse Temurin `17.0.19+10` 后，`--help`、`--version` 和包外 signed fixture `protect` smoke PASS；其他 vendor/version/build稳定拒绝。
- Smoke 输入哈希不变，product output 未签名，JSON 报告 schema PASS，archive 不含 fixture/test signer。
- 四 ABI 齐全，sensitive/signing/debug scans 为零。
- 五个 V2-M4-01 tracked 输入全部存在；security-run-lock 对另外四个 canonical Git blob的 path/mode/blob/size/hash绑定均由 accepted merge commit复算通过。Packaging gate、由冻结 schema/validator验证的 artifact manifest与release-artifact-lock形成完整 official artifact闭包，且post-freeze verifier证明连续阶段/早期 evidence不可变/本阶段外零路径。
- 独立只读复核 `P0=0/P1=0/P2=0`。

## Required Tests

- Archive entry order与 ZIP method/level/flags/version/extra/descriptor/comment/DOS rounding/mode/ZIP64，TAR format/uid/gid/uname/gname/mode/mtime/link/PAX，GZIP level/CM/FLG/MTIME/XFL/OS/name/comment/header-CRC逐字段 mutation和 two-build hash。
- Launcher JRE missing/wrong version、argument/Unicode relative path、stdout/stderr/exit code。
- Offline protect、input read-only、unsigned output、report schema 和 failure cleanup。
- Component/SBOM allowlist、four ABI、fixture/key/DEX/debug/signing scans。
- Wrong tuple/component/hash、manual archive mutation 和 network access 负例。
- 五个 M4-01 tracked 输入逐一执行 missing/only-build/alternate path负例；对 SBOM、component manifest、security artifact manifest、security gate 四个 blob binding逐一执行 mode/blob/size/hash/byte drift与 security-run-lock missing-binding/extra-binding/wrong order负例，并覆盖 wrong repository/run/head/attempt。
- Post-security new/changed distribution byte、Quickstart/launcher drift、workflow/validator drift、second run 和 extra input 负例。
- Packaging artifact manifest schema/validator path drift、missing/extra/reorder/duplicate/wrong role/tuple/source/member size/hash、lock未绑定manifest，以及post-freeze stage/path/earlier-evidence drift负例。

## Required Evidence

- Tuple、implementation freeze、toolchain、platform、JRE 和全部 commands/exits。
- 两次 archive、SHA256SUMS、SBOM、manifest、provenance hashes 与 entry listings。
- Official run/artifact API identity、tracked release artifact lock bytes/hash、retention/expiry 和每个 archive member hash。
- Tracked packaging gate/artifact manifest Git blobs与official artifact完整 member closure复算报告。
- Offline smoke input/output/report hashes、signer relation、cleanup 和 network-block proof。
- Independent review、findings 和 residual packaging risks。

## Likely Files

- `docs/v0.2/evidence/V2-M4-02/`
- ignored `distribution/build/v0.2/` 与 immutable CI artifact

## Dependencies and Blockers

V2-M4-01 非 PASS、任一 frozen/reviewed source byte 漂移、component/SBOM mismatch、任一平台不可重现、offline smoke/JRE prerequisite 失败或 archive 含禁止内容时保持 blocked。不得新增/修改 distribution、手工修改 archive、跳过一个平台或重新运行选择更好 hash。

## Agent Handoff Requirements

使用分支 `chore/v2-m4-02-release-packaging`，只处理 Issue #93 并创建一个对应 PR。交接必须包含两个 archive、两次构建 hashes、entry manifests、offline smoke、SBOM/provenance、sensitive scans 和 independent review；不得发布产物或修改根 HandOff。
