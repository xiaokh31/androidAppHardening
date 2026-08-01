---
id: M0-06
title: 启动早期受认证配置合同修订
milestone: M0
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-04
required_skills:
  - plan-apk-hardening-change
security_sensitive: true
---

## Goal

根据 M0-05 的真实设备阻塞证据，把启动配置从不可靠的 `ApplicationInfo.metaData` 迁移到 `ApplicationInfo.sourceDir` 中固定且受认证的 ConfigV2 条目，并在重新启动 M0-05 前冻结 Host/Runtime 的唯一合同。

## Background

API 29 arm64 非 root 设备的真实 `instantiateClassLoader` 回调中，`ApplicationInfo.metaData` 为 `null`，而 APK Manifest 实际包含原合同规定的七个键。`sourceDir`、早期 signer 验证和固定 ZIP 条目读取均可工作。继续读取 metadata 会把厂商行为差异误判成安全失败；通过 Context、PackageManager 或 hidden API 绕过又违反 ADR 0003。

## Inputs

- M0-04 已通过的公开 ClassLoader hook 与设备证据。
- M0-05 blocked 提交及 `docs/evidence/M0-05/arm64-api29-metadata-blocker.md`。
- ADR 0003、0004、0006 与相关 M1/M2 任务合同。
- GitHub Issue [#30](https://github.com/xiaokh31/androidAppHardening/issues/30)。

## Expected Outputs

- ADR 0007，明确 `sourceDir` 固定配置条目、认证顺序、兼容性与拒绝方案。
- ADR 0006 的 ConfigV2 精确 wire layout、AAD 和版本迁移规则。
- M0-05 及所有下游 Host/Runtime 任务卡的一致接口、依赖和验收修订。
- 路线图、架构、威胁模型和任务索引同步。
- 独立只读安全复核结论与严格 HandOff 证据。

## In Scope

- 文档、ADR、任务合同、依赖图与错误/测试语义。
- 固定 `assets/ah/runtime/config.bin` 的 ConfigV2 768-byte 格式。
- 原始 Factory、策略版本、密钥绑定和完整 config digest 的认证顺序。
- Manifest 变换白名单缩减与 `metaData == null` 正向兼容性要求。

## Out of Scope

- 修改 Runtime、Host、fixture、Gradle 或 CI 实现。
- 重新运行 M0-05 设备矩阵。
- 启动 M1/M2 或扩大 v0.1 支持范围。
- hidden API、Context/PackageManager 回退或新的远程配置/密钥服务。

## Implementation Decisions

- 分支固定为 `docs/m0-06-early-startup-config-contract`，Issue 标题固定为 `[M0-06] Early startup configuration contract`，只允许一个关联 PR。
- M0-06 依赖 M0-04；M0-05 新增 M0-06 依赖并继续 blocked，直到本任务合并后才可从其既有 blocked 提交恢复。
- `ConfigV2`、`KeyPackagingPlanV2`、固定 asset 名称及认证顺序以 ADR 0006/0007 为唯一来源；任务卡不得另创格式。
- 文档提交冻结后，由未参与修订的 `m0_06_security_review` 对同一 SHA 做只读复核；P0/P1/P2 未关闭前不得推送 PR 或恢复 M0-05。

## Public Interfaces

- `assets/ah/runtime/config.bin`：固定 768-byte `ConfigV2`。
- `assets/ah/runtime/payload.ahdc`：固定 AHDC v1 条目。
- `KeyPackagingPlanV2`：Host 内存一次性所有权对象。
- PoC 启动事件：`EARLY_CONFIG_PARSED`、`EARLY_CONFIG_APK_AUTHENTICATED`；后者只表示固定测试 signer 的 APK 签名覆盖。生产 `VerifiedStartupConfiguration` 必须等待 ADR 0007 全链认证。
- PoC 失败语义：配置定位/结构失败 `AAH-P009`，配置认证/绑定失败 `AAH-P010`。

## Security Constraints

- ConfigV2 在完整 digest 被已认证 AHDC HeaderV1 绑定前不得作为可信策略或类名使用。
- Runtime 不接受调用方路径、asset 名、Factory 名称、版本或 signer binding。
- 不用 metadata、Context、PackageManager、Framework 私有对象或 hidden API 解锁 payload。
- 所有长度、offset、UTF-8、flag、reserved、ZIP 字段和认证值均视为不可信输入并有界验证。

## Compatibility Requirements

- `ApplicationInfo.metaData == null` 是 API 29/36 x86_64 与 API 29+ arm64 的合法正向输入。
- 保留原 `android:name`；Framework 的 `instantiateApplication` `className` 是原 Application 的唯一来源。
- v0.1 只接受 Config major `2`，不回退 ConfigV1。

## Acceptance Criteria

1. ADR 0007 记录 M0-05 证据、唯一配置来源、认证顺序、拒绝方案和残余风险。
2. ADR 0006 给出无歧义的 768-byte offset 表、flag/length/UTF-8/reserved 规则、AAD 和完整 digest 绑定。
3. M0-05 的依赖、事件、失败码和设备验收不再要求 `ApplicationInfo.metaData`，并把空 Bundle 作为正向用例。
4. M1-03 只允许替换 `android:appComponentFactory`；M1-01/M1-04 负责把规范化原 Factory 写入 ConfigV2。
5. M2-01/M2-02/M2-03 只消费已认证启动配置，公开入口不接受调用方 asset 名或 Factory 名。
6. 原 Factory 的 `instantiateClassLoader` 与五类组件入口均有唯一委托合同；provisional/final loader 顺序、identity、null/异常失败语义在 M0-05/M2-01 一致。
7. 任务索引与路线图的关键路径固定为 `M0-04 -> M0-06 -> M0-05`，M1/M2 仍在 M0-05 完成前 blocked。
8. `node tools/governance/validate-project-package.mjs` 和严格 HandOff 校验退出 `0`。
9. 独立安全复核对冻结提交给出 PASS，且没有未关闭 P0/P1/P2。

## Required Tests

- 治理包结构、链接、frontmatter 和依赖图校验。
- 全仓搜索确认生产合同不再依赖七个 `ah.runtime.*` metadata 或 `ApplicationInfo.metaData`。
- ConfigV2 offset/总长/AAD 的手工算术复核和独立语义复核。
- 严格 HandOff 校验。

## Required Evidence

- 基线、分支、Issue #30、冻结提交 SHA 和最终提交 SHA。
- 修改文件清单与 `git diff --check`。
- 所有校验命令、退出码、OS、时间戳与 Node 版本。
- 独立复核者、复核 SHA、发现及处置结论。

## Likely Files

- `docs/adr/0003-api29-public-classloader-hook.md`
- `docs/adr/0006-offline-key-protection-boundary.md`
- `docs/adr/0007-source-dir-startup-configuration.md`
- `docs/tasks/M0-06-early-startup-config-contract.md`
- `docs/tasks/M0-05-application-factory-provider-jni-poc.md`
- `docs/tasks/M1-*.md`
- `docs/tasks/M2-*.md`
- `docs/ARCHITECTURE.md`
- `docs/PRODUCT_REQUIREMENTS.md`
- `docs/TEST_STRATEGY.md`
- `docs/THREAT_MODEL.md`
- `docs/ROADMAP.md`
- `docs/PROJECT_PLAN.md`
- `tools/governance/validate-project-package.mjs`
- `docs/tasks/INDEX.md`
- `HandOff.md`

## Dependencies and Blockers

- 若无法建立无循环的 ConfigV2/AHDC 认证顺序，任务 blocked，M0-05 不得恢复。
- 若下游 Host 与 Runtime 任务合同无法对同一字节格式和所有权达成一致，任务 blocked。
- 独立复核未通过时不得把本任务标记 done。

## Agent Handoff Requirements

- 本任务只修改文档，不顺手修改 M0-05 实现或运行设备。
- `/root` 核验实际 Git、校验输出和复核结论后才更新根 `HandOff.md`。
- 未经用户另行授权不推送分支、不创建 PR、不合并。
