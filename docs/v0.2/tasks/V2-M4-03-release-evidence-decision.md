---
id: V2-M4-03
title: "v0.2 发布证据与最终决定"
milestone: V2-M4
status: planned
owner_role: qa-governance-agent
depends_on:
  - V2-M4-02
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - coordinate-project-handoff
  - validate-protected-apk
security_sensitive: true
---

## Goal

汇总同一 v0.2 tuple 的性能、验证、安全和可重现打包 evidence，完成用户文档、release notes 和机器可读索引，由 `/root` 作出最终 `PASS|BLOCKED` release decision。

## Background

发布文档属于安全边界。旧 v0.1 没有发布，旧 M4-03 不可启动。本任务只能引用 fresh V2 evidence 闭包，不能用旧 M3/M4 报告填补缺口，也不能在文档阶段增加功能或修改产品字节。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M3-03 performance PASS、V2-M3-04 validation PASS。
- V2-M4-01 security/SBOM PASS。
- V2-M4-02 两个平台 archives、manifests、provenance、hashes 和 offline smoke。
- 已合并 tracked `docs/v0.2/evidence/V2-M4-02/release-artifact-lock.json` 与其固定的 same-repository official artifact。
- v0.2 product requirements、compatibility matrix、threat model 和 CLI help/report schema。

## Expected Outputs

- `docs/v0.2/releases/RELEASE_NOTES_v0.2.0.md`、archive 外 `docs/v0.2/releases/QUICKSTART.md`、安全/兼容说明；两者都是 required tracked output。
- `docs/v0.2/releases/release-evidence-v0.2.0.json`、候选前冻结的 schema 和 SHA-256 evidence index；该 index 是 required tracked output。
- `docs/v0.2/evidence/V2-M4-03/v02-release-evidence-artifact-manifest.json` 与 `docs/v0.2/releases/release-decision-v0.2.0.json`，绑定 docs smoke、link/schema、download/API和final decision raw evidence。
- Final raw artifact manifest 只接受候选前冻结的 `tools/release-evidence-v02/schemas/release-evidence-artifact-manifest-v1.schema.json` 与 `tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs`；本任务五个 required tracked outputs恰为上述 Quickstart、release notes、evidence index、decision与artifact manifest。
- Windows/Ubuntu documentation-driven smoke reports。
- `/root` 可核验的 structured handoff 与 final `PASS|BLOCKED` decision package。

## In Scope

- 安装 prerequisite、唯一 `protect` 命令、JSON report、error codes 和 external signing boundary。
- Supported/unsupported scope、fresh API/ABI cells、performance budgets/values、known limitations 和 residual risks。
- Archive、SBOM、provenance、test reports、tuple 和 hash revalidation。
- 文档 links、commands、filenames、versions 和 exact behavior smoke。

## Out of Scope

- 修改 Host、Runtime、Harness、distribution bytes、tuple、budget 或 compatibility results。
- 添加 APK signing、keystore example、真实 certificate/path 或 customer APK。
- 宣称绝对防逆向、防调试、防 dump、输出必然变小或未验证设备支持。
- 自动创建 Git tag、GitHub Release 或上传 archive；需要用户单独发布授权。

## Implementation Decisions

- Quickstart 使用用户包外、自有或获授权的 `work/input/signed-app.apk`，输出 `work/output/protected-unsigned.apk` 与 report；发布包不提供 input。
- Archive 内 `distribution/docs/QUICKSTART.md` 是 V2-M0-02 冻结且经 V2-M4-01 审查、V2-M4-02 打包的不可变 bytes；本任务只能复核其 hash/命令。新建的 `docs/v0.2/releases/QUICKSTART.md` 位于 archive 外，不进入 distribution component 或既有 archive。
- Docs smoke harness 在 package 外构建合成 fixture、生成一次性证书、签 input 并挂载给解包发行版；archive 白名单禁止 fixture/test signer。
- Release evidence index 的每个条目包含 role、tuple、commit、path、size、SHA-256 和 source task。
- Frozen release-evidence workflow不接受 artifact input/URL。它只从 tracked lock 读取 numeric run/artifact ID，先通过 official API核对 repository、head、`runAttempt=1`、name、size、GitHub digest、retention/expiry，再下载并在任何 smoke/claim 前复算两个 member hash。missing、expired、deleted、second、mismatch 或 cache/alternate-run fallback 均 `BLOCKED`，不得重建 archive。
- 兼容文档只列 V2-M3-04 fresh VERIFIED cells；其余明确 UNVERIFIED。
- 防护措辞固定为提高攻击成本，明确 root/Hook/custom ART/kernel attacker residual risk。
- `/root` 只有在九个 V2 Issue/PR、exact-head CI、independent review 和 post-merge gates 全部关闭后才能决定 PASS。
- `docsCheckV02`、`releaseEvidenceCheckV02`、schema 和 `.github/workflows/v02-release-evidence.yml` 均来自候选前 `validationManifestSha256`/`releaseGateContractSha256`；本任务只执行，不编辑 gate。只有 `/root` 可在 `main` 无 inputs 执行一次，要求 `runAttempt=1`。
- 执行前必须通过 frozen post-freeze verifier `--stage V2-M4-03 --phase pre-run`并要求五个 required tracked outputs全部缺失；evidence PR运行`--phase evidence-pr`并要求五个输出全部存在，合并后main运行`--phase post-merge`。只能修改由 `post-freeze-path-policy-v1.json` 为 V2-M4-03 固定的 archive 外 release docs、evidence manifest、decision、README 与 HandOff exact paths；policy 本身、早期 evidence、产品、distribution、gate、七 manifest和五组 workflow bytes全部不可变。
- Release-evidence artifact manifest 按 `identity-path-policy-v1.json.canonicalPaths.artifactManifestSchemas.release-evidence` 与 `.artifactManifestValidators.release-evidence` 固定的 schema/validator，完整枚举 official run/artifact内每个 docs smoke、link/schema、API/download、hash-index与decision raw member的 path、role、size、SHA-256、tuple、source run/artifact。最终 evidence index与decision必须绑定其 tracked Git blob，missing/extra/build-only/alternate member失败。

## Public Interfaces

- Gradle `./gradlew docsCheckV02 releaseEvidenceCheckV02`。
- `docs/v0.2/releases/RELEASE_NOTES_v0.2.0.md`。
- `docs/v0.2/releases/QUICKSTART.md`，archive 外用户文档。
- `docs/v0.2/releases/release-evidence-v0.2.0.json` 及 schema。
- `docs/v0.2/releases/release-decision-v0.2.0.json` 与 `docs/v0.2/evidence/V2-M4-03/v02-release-evidence-artifact-manifest.json`。
- `node tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs --manifest docs/v0.2/evidence/V2-M4-03/v02-release-evidence-artifact-manifest.json`。
- Quickstart CLI：`android-app-hardening protect --input work/input/signed-app.apk --output work/output/protected-unsigned.apk --report work/output/protect.json`。
- Final decision 枚举 `PASS|BLOCKED`。

## Security Constraints

- 文档不要求产品接收 private key、keystore、alias 或 password。
- Example/report/evidence 不包含真实客户 APK、明文 DEX、device serial、用户绝对路径或 credential。
- Old v0.1 artifact/run/tuple 只能出现在 historical blocked statement，不得进入 PASS evidence list。
- 每个 security/compat/performance claim 必须有可复算 fresh evidence。

## Compatibility Requirements

- Windows/Ubuntu commands分别可复制，差异仅 launcher 与 shell syntax。
- Eclipse Temurin `17.0.19+10` prerequisite、standalone APK、`minSdk >= 29` 和 unsigned output 清楚可见。
- 四 ABI 只表示 Runtime build；ARM-only 不转换，exact device claims 与 V2-M3-04 一致。
- Unsupported formats/frameworks 完整列出且 error behavior 与 CLI 一致。

## Acceptance Criteria

- `docsCheckV02` 与 `releaseEvidenceCheckV02` 退出码为 0。
- Windows/Ubuntu Quickstart 在断网、包外 signed fixture 下 PASS：input hash 不变、new unsigned APK、REPORT schema valid。
- Release notes 完整列出 supported/unsupported、fresh compatibility cells、performance gate、known limitations、SBOM/security decision 和 residual risks。
- Evidence index 每个文件存在、size/hash 可复算、tuple/commit 一致，无旧 PASS substitution。
- Release artifact lock canonical/schema/hash通过，下载 bytes 与唯一 V2-M4-02 official artifact/member identity一致且未过期；无第二或替代 archive来源。
- Markdown links、commands、versions、archive filenames 和 manifest entries 全部匹配实际 release bytes。
- Archive 内 Quickstart hash 与 V2-M0-02/V2-M4-01/V2-M4-02 一致，archive 外 Quickstart 不出现在 archive allowlist；frozen docs/evidence validator/workflow hash 与 tuple 一致。
- Absolute-security/signing-capability/sensitive-material/absolute-path scans 为零。
- `/root` 核验所有 V2 tasks、PRs、CI、reviews、clean worktree 后记录 final `PASS|BLOCKED`；未获用户发布授权时不创建 tag/release。
- Post-freeze verifier证明六个阶段连续、每个前驱canonical path在其accepted merge后未变、本阶段外零新路径；final evidence manifest闭包覆盖每个official raw member并被decision/index绑定。

## Required Tests

- Markdown links、CLI commands、filenames、version、JSON schema 和 hash index。
- Windows/Ubuntu docs-driven offline smoke、input read-only、unsigned output 和 cleanup。
- Required boundary phrases 与 forbidden absolute-security/signing/size/compatibility claims。
- Evidence existence/hash/tuple/commit/task closure 和 archive allowlist。
- Missing/mutated evidence、old artifact injection、UNVERIFIED→supported 和 product-byte drift 负例。
- Archive-internal Quickstart drift、archive 外文档被打包、post-freeze gate change、second run 和 extra input 负例。
- Release artifact lock missing/expired/deleted、wrong repository/head/runAttempt/numeric ID/name/size/digest/member hash、第二 artifact、caller URL/input、Actions cache 和 archive rebuild负例。
- 五个 required tracked output逐一 missing负例；final evidence artifact manifest schema/validator path drift、missing/extra/reorder/duplicate/wrong role/tuple/source/member hash、decision/index未绑定manifest，以及post-freeze stage/path/earlier-evidence/workflow drift负例。

## Required Evidence

- Docs/evidence commands、exit codes、OS/JRE/toolchain、time 和 exact tuple。
- Smoke input/output/report hashes、signer relation、network block 和 cleanup。
- Release notes、evidence index、link/schema reports、archive/SBOM/provenance hashes。
- Final decision、tracked release-evidence artifact manifest Git blob/hash与official raw member closure复算报告。
- Structured worker handoff、independent documentation/security review 和 `/root` final decision record。

## Likely Files

- `README.md`，仅在 successor governance 允许且保留 v0.1 terminal history时
- `docs/v0.2/releases/`
- `docs/v0.2/evidence/V2-M4-03/`

## Dependencies and Blockers

V2-M4-02 非 PASS、任一 fresh evidence/hash 缺失、tuple/commit mismatch、archive-internal Quickstart或 frozen gate漂移、文档与 CLI/archive 不一致或边界措辞冲突时保持 blocked。不得省略失败项、复用旧证据、编辑 product/distribution/validator 或重建 archive 获得通过。

## Agent Handoff Requirements

使用分支 `docs/v2-m4-03-release-evidence`，只处理 Issue #94 并创建一个对应 PR。交接必须列出文档、commands/exits、dual-platform smoke、links/schema、evidence/archive hashes、known limitations 和 final recommendation；根 HandOff 和任何实际 release publication 只由 `/root` 在用户授权后处理。
