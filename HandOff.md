---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260801-133957
updated_at: 2026-08-01T13:39:57+08:00
updated_by: /root
state: active
source_branch: docs/m0-06-early-startup-config-contract
base_commit: 43e10c38569dfdd64bc41d688d23d23e005906fb
working_tree: clean
current_milestone: M0
active_task: M0-06
next_owner: runtime-security-agent
---

# Project HandOff

## Objective

在 APK-only、输入只读、输出未签名和 `minSdk >= 29` 的边界内完成独立 M0-06 ADR/任务合同修订。把启动配置从已被真实设备证伪的 `ApplicationInfo.metaData` 迁移到 `ApplicationInfo.sourceDir` 中固定且受认证的 ConfigV2；本任务只改治理与设计文档，不修改 M0-05 Runtime/fixture 实现。

## Current State

- M0-04 的 PR #29 已合并并通过正式 API 29/36 x86_64 验收、独立安全复核与合并后 strict HandOff。
- M0-05 的本地 blocked 分支固定在 `3d716ddc4be513a07be0b5cf2d986529d9e0dc06`；API 29 arm64 非 root 真机的早期 signer 通过，但 Framework callback `metaData` 为 null，触发 `AAH-P009` 且未创建 payload loader。
- 用户已批准启动独立 ADR/任务合同修订；GitHub Issue [#30](https://github.com/xiaokh31/androidAppHardening/issues/30) 已创建，分支为 `docs/m0-06-early-startup-config-contract`，基线为 `main@43e10c38569dfdd64bc41d688d23d23e005906fb`。
- ADR 0007 与 M0-06 任务卡已新增；ADR 0003/0006、架构、威胁模型、路线图和 M0-05/M1/M2 任务合同已同步并通过冻结前治理校验。
- 当前设计固定使用 768-byte ConfigV2：完整 config SHA-256 由已认证 AHDC HeaderV1 绑定，原 Factory/策略只能在 CEK envelope、manifest MAC、config digest、signer 和 build/key slot 全部验证后使用。
- Manifest 变换缩减为只替换 `android:appComponentFactory`；原 `android:name` 和既有 metadata 保持，Runtime 不读取 `ApplicationInfo.metaData`。
- M0-05 继续 blocked，M1/M2 不得启动；本分支未推送、未创建 PR，也未运行任何模拟器或真机命令。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-04 | `runtime-security-agent` | `spike/m0-04-classloader-poc` | done | M0-03 | PR #29 已合并，正式设备矩阵和独立复核通过 |
| M0-06 | `runtime-security-agent` | `docs/m0-06-early-startup-config-contract` | in_progress | M0-04 | 完成文档一致性校验，冻结提交并交 `m0_06_security_review` 只读复核 |
| M0-05 | `runtime-security-agent` | `spike/m0-05-application-factory-provider-jni-poc` | blocked | M0-04, M0-06 | M0-06 合并后，从既有 blocked 提交恢复实现与双平台设备矩阵 |

## Decisions and Invariants

- 继续遵守 ADR 0001 至 ADR 0007；ADR 0007 明确 sourceDir 固定配置通道，ADR 0006 固定 ConfigV2 wire layout。
- 输入 APK 只读；产品输出为新的未签名 APK；生产模块不得读取、传递或使用签名凭据。
- API 29+ 只使用公开 `AppComponentFactory.instantiateClassLoader()`、Framework `ApplicationInfo` 和只读文件 API；不用 Context、PackageManager、Framework 私有对象、反射或 hidden API 回退。
- 启动固定读取 `assets/ah/runtime/config.bin` 与 `assets/ah/runtime/payload.ahdc`，生产接口不接受调用方路径或 asset 名。
- ConfigV2 在完整 digest 被已认证 AHDC header 绑定前是不可信输入；原 Factory、risk policy 和版本不得提前暴露。
- Manifest 只替换 `android:appComponentFactory`，不新增七个废弃 `ah.runtime.*` metadata；原 Application 由 Framework `className` 提供。
- ConfigV1 在产品实现/发布前被替代；v0.1 只接受 Config major 2，不提供兼容回退。
- 本任务是 docs-only；M0-05 实现、设备矩阵、PR、M1/M2 均不在本分支范围。
- 安全敏感文档提交必须由未参与修订的 `m0_06_security_review` 对冻结 SHA 做只读复核，P0/P1/P2 全部关闭后才可完成。

## Changes Since Previous Handoff

- 根据 M0-05 blocked 证据建立独立 Issue #30 和 M0-06 文档分支，未复用 M0-05 的 Issue/分支。
- 新增 ADR 0007，记录真实 callback 冲突、sourceDir 配置决策、固定认证顺序、拒绝替代方案和兼容性影响。
- 将 ADR 0006 从预发布 176-byte ConfigV1 修订为 768-byte ConfigV2，并定义 Factory flag/length/UTF-8 slot、132-byte AAD、完整 digest 绑定和 KeyPackagingPlanV2。
- 修订 ADR 0003 与架构：Runtime 不读取 metadata；Manifest 只替换 Shell Factory；原 Application 不重复存储。
- 新增 M0-06 任务卡并把关键路径改为 `M0-04 -> M0-06 -> M0-05`。
- 修订 M0-05 验收：`metaData == null` 为正向用例，新增 `EARLY_CONFIG_PARSED/AUTHENTICATED`、`AAH-P009/P010` 和 ConfigV2 tamper matrix。
- 同步 M1-01/M1-03/M1-04/M1-05 与 M2-01/M2-02/M2-03 的配置所有权、API 和格式引用。

## Verification Evidence

### M0-04 completed dependency

- task_id: M0-04
- git_commit: e9f89734aa3d4148ec6ebe9a6b970a9276128d00
- command: `gradlew.bat --offline --no-daemon :fixtures:android:connectedClassloaderPocDebugAndroidTest`; `node tools/validation/run-m0-04-cold-start.mjs`; `node tools/validation/run-m0-04-tamper-start.mjs`; independent read-only review
- exit_code: 0
- environment: Windows 10 10.0.19045 x64; Emulator 37.1.11; API 29 rev8 and API 36 rev2 x86_64 non-root AVDs; independent `m0_04_security_review`
- timestamp: 2026-07-31T15:06:44+08:00
- artifact: `docs/evidence/M0-04/formal-api29-api36.md`
- sha256: 57ed7fda2539a8053ea7e361b1db51950dc0096305ae2c514780cc9ec6edef0b
- result: PASS; both devices passed instrumentation, cold starts and tamper matrices, with no remaining P0/P1/P2 review finding

### M0-06 issue and branch initialization

- task_id: M0-06
- git_commit: 43e10c38569dfdd64bc41d688d23d23e005906fb
- command: `create GitHub Issue #30 through authenticated in-app browser`; `git switch -c docs/m0-06-early-startup-config-contract main`
- exit_code: 0
- environment: GitHub authenticated session; Windows 10 10.0.19045 x64; Git 2.52.0
- timestamp: 2026-08-01T13:34:16+08:00
- artifact: `https://github.com/xiaokh31/androidAppHardening/issues/30`
- sha256: not_applicable
- result: PASS; independent task identity, Issue and branch established from current main without modifying M0-05 branch

## Blockers and Required Approvals

- M0-05 remains blocked on M0-06 merge and subsequent implementation adaptation; this is intentional dependency enforcement, not authorization to modify M0-05 code here.
- M0-06 completion is pending governance validation, a frozen commit and independent read-only security review.
- Branch push and PR creation require separate user authorization; current user approval covers starting the local independent ADR/task revision only.

## Ordered Next Actions

1. Finish cross-document contract synchronization and remove stale production references to ConfigV1, seven metadata keys and caller-supplied asset names.
2. Run governance validator, strict HandOff validator, link/search checks and `git diff --check`; fix all failures.
3. Commit a frozen docs-only SHA and start independent `m0_06_security_review` against exactly that SHA.
4. Resolve every P0/P1/P2 finding, rerun validation and update this HandOff with final evidence.
5. Stop before push/PR unless the user explicitly authorizes publication; do not resume M0-05 before M0-06 is merged.

## Relevant Files and Artifacts

- `HandOff.md`
- `docs/adr/0003-api29-public-classloader-hook.md`
- `docs/adr/0006-offline-key-protection-boundary.md`
- `docs/adr/0007-source-dir-startup-configuration.md`
- `docs/tasks/M0-06-early-startup-config-contract.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/tasks/INDEX.md`
- `docs/ARCHITECTURE.md`
- `docs/THREAT_MODEL.md`
- `docs/ROADMAP.md`
- `docs/PROJECT_PLAN.md`
- `https://github.com/xiaokh31/androidAppHardening/issues/30`

## Resume Checklist

- [ ] 确认当前分支为 `docs/m0-06-early-startup-config-contract`，基线为 `main@43e10c38569dfdd64bc41d688d23d23e005906fb`。
- [ ] 只修改 M0-06 文档范围，保留 `spike/m0-05-application-factory-provider-jni-poc@3d716dd`。
- [ ] 运行 `node tools/governance/validate-project-package.mjs`。
- [ ] 运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
- [ ] 运行全仓废弃合同搜索、链接检查和 `git diff --check`。
- [ ] 冻结提交后由独立 reviewer 复核同一 SHA；P0/P1/P2 未关闭前不完成。
- [ ] 未经授权不推送、不创建 PR；M0-06 合并前不恢复 M0-05，不启动 M1/M2。

## Handoff Sign-off

- Coordinator `/root` 已核验当前 Git 分支、main 基线、M0-05 blocked 提交/证据和 Issue #30。
- 当前为 active docs-only 修订；冻结前治理、strict HandOff、固定工具链和 diff 校验已通过，尚未声明独立安全复核或 M0-06 完成。
- 本任务未启动模拟器、未访问真机、未修改 M0-05 实现，也未下载任何工具链。
