---
id: M4-03
title: "发布证据与用户文档"
milestone: M4
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-03
  - M3-04
  - M3-05
  - M4-01
  - M4-02
required_skills:
  - coordinate-project-handoff
  - validate-protected-apk
security_sensitive: false
---

## Goal

汇总 v0.1.0 的可复算发布证据，完成面向使用者和后续 Agent 的最终文档，并确保所有能力、限制、外部签名边界和验证范围表述准确一致。

## Background

发布文档是产品安全边界的一部分。使用者必须清楚产品只处理独立 APK、输入始终只读、输出始终未签名，以及 Runtime 防护只能提高攻击成本。

## Inputs

- M4-01 安全审查、SBOM、发现项与残余风险。
- M4-02 两个平台发布包、manifest、provenance 和哈希。
- M3-03/M3-04/M3-05 的跨平台、兼容和性能证据。
- 产品需求、架构、ADR、任务卡和 CLI help。

## Expected Outputs

- 更新后的 README、快速开始、开发者文档、安全边界和兼容矩阵。
- `RELEASE_NOTES_v0.1.0.md` 与 `release-evidence-v0.1.0.json`。
- 所有发布证据的 SHA-256 索引和可复算说明。
- 供 `/root` 更新根 `HandOff.md` 的结构化交接包。

## In Scope

- 安装、唯一 `protect` 命令、JSON 报告、失败码和外部签名边界说明。
- 支持/不支持范围、API/ABI 证据、ARM-only 限制和性能预算。
- 安全模型、可绕过的动态防护与漏洞报告入口。
- 发布文件、SBOM、provenance、测试报告和哈希的索引。

## Out of Scope

- 在产品中增加 APK 签名步骤或请求任何签名秘密。
- 宣称绝对防逆向、防调试、防内存截取或输出必然变小。
- 新增未经过任务卡、测试和安全评审的功能。
- 由工作 Agent 直接修改根 `HandOff.md` 或创建最终 GitHub Release。

## Implementation Decisions

- 快速开始使用固定合成路径：`fixtures/java-single-dex.apk` 作为只读输入，`build/out/java-single-dex-protected-unsigned.apk` 作为新输出。
- 示例流程固定为 `android-app-hardening protect`、验证 JSON 与输出未签名；后续生产签名明确由使用者在产品外部的既有安全发布流水线完成。
- 支持声明固定包含独立 APK、`minSdk >= 29`、Java/Kotlin、单/多 DEX、自定义 `Application/AppComponentFactory` 和四 ABI Runtime。
- 不支持声明固定包含 AAB、Split APK、Flutter、Unity、React Native、热修复和已有加固壳。
- 安全说明固定写明 DEX 内存截取、反调试和环境检测只提高成本；x86/x86_64 本身不是风险信号；大小优化只控制增量、不保证输出小于输入。
- `release-evidence-v0.1.0.json` 只引用与 Release Candidate commit 一致且 SHA-256 可复算的证据。

## Public Interfaces

- CLI 示例：`android-app-hardening protect --input fixtures/java-single-dex.apk --output build/out/java-single-dex-protected-unsigned.apk --report build/out/protect.json`
- 发布说明 `docs/releases/RELEASE_NOTES_v0.1.0.md`。
- 证据索引 `docs/releases/release-evidence-v0.1.0.json` 及其 JSON schema。

## Security Constraints

- 文档不得要求产品接收私钥、keystore、alias 或密码；产品永不签名。
- 外部签名只描述责任边界和官方工具链入口，不嵌入真实密钥、证书路径或凭据示例。
- 文档、日志样例和证据不得包含真实客户 APK、明文客户 DEX、设备序列号或用户绝对路径。
- 安全能力必须使用可验证措辞，并明确 root/注入/进程控制攻击的残余风险。

## Compatibility Requirements

- Windows 与 Ubuntu 文档命令分别可复制执行，差异仅限 launcher 名称。
- 兼容表与 M3-04 JSON 完全一致，不扩展未验证 API/ABI。
- 四 ABI 声明仅针对 Runtime；ARM-only 输入不承诺在 x86 设备运行。
- 所有内部 Markdown 链接、文件名、CLI 参数和错误码与发布包一致。

## Acceptance Criteria

- `./gradlew docsCheck releaseEvidenceCheck` 退出码为 `0`。
- 在干净 Windows 与 Ubuntu 环境按快速开始执行，输入哈希不变、生成新的未签名 APK、JSON 通过 schema 校验。
- 发布说明完整列出支持项、不支持项、API/ABI 证据、性能门禁、已知限制和残余风险。
- 证据索引的每个文件存在且 SHA-256 可复算，并全部对应同一 Release Candidate commit。
- 文档扫描确认无绝对安全承诺、无产品签名能力、无敏感材料、无失效内部链接。

## Required Tests

- Markdown 内部链接、命令、文件名、版本号和 JSON schema 测试。
- Windows/Ubuntu 快速开始的文档驱动 smoke test。
- 支持/不支持范围、未签名输出、x86 非风险和大小声明的必备文本测试。
- 证据 commit 一致性、文件存在性和 SHA-256 复算测试。

## Required Evidence

- 文档检查和 smoke test 命令、退出码、OS/toolchain 版本。
- 快速开始输入前后哈希、未签名输出证明和 JSON schema 结果。
- 发布文档、证据索引、链接报告和最终发布包 SHA-256。
- 结构化 worker handoff，供 `/root` 生成最终根交接。

## Likely Files

- `README.md`
- `docs/README_FIRST.md`
- `docs/COMPATIBILITY_MATRIX.md`
- `docs/SECURITY_GUIDE.md`
- `docs/releases/RELEASE_NOTES_v0.1.0.md`
- `docs/releases/release-evidence-v0.1.0.json`
- `docs/releases/release-evidence.schema.json`

## Dependencies and Blockers

M4-01 未 `PASS`、M4-02 产物未冻结、任何证据哈希不可复算或文档与 CLI 行为不一致时不得完成。发现边界表述冲突时必须阻塞并由 `/root` 统一决策，不得自行扩大产品承诺。

## Agent Handoff Requirements

使用分支 `docs/m4-03-release-evidence`，只处理 Issue `M4-03` 并仅创建一个对应 PR。交接必须列出文档、全部命令与退出码、双平台 smoke test、链接/schema 检查、证据与发布包 SHA-256、已知限制和残余风险；不得修改根 `HandOff.md`，由 `/root` 整合后决定发布。
