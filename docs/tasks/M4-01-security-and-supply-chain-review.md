---
id: M4-01
title: "安全与供应链发布审查"
milestone: M4
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-02
  - M3-03
  - M3-04
  - M3-05
required_skills:
  - validate-protected-apk
  - audit-third-party-skill
security_sensitive: true
---

## Goal

对 v0.1 Release Candidate 执行独立安全、密码边界、依赖、许可证和构建来源审查，以机器可验证证据决定允许发布或阻塞。

## Background

加固只能提高静态与动态分析成本，不能提供绝对防护。发布审查必须验证实现与威胁模型一致、产品无 APK 签名能力、第三方材料可追溯，并如实记录残余风险。

## Inputs

- M3-02 tamper/fuzz 与 sanitizer 结果。
- M3-03 跨平台等价性结果。
- M3-04 API/ABI 兼容矩阵。
- M3-05 性能预算结果。
- ADR、威胁模型、依赖锁、源码和 Release Candidate commit。

## Expected Outputs

- `security-review-v0.1.0.md` 与机器可读发布门禁结果。
- CycloneDX SBOM、依赖/许可证清单和漏洞扫描报告。
- 密钥/签名能力/敏感材料扫描报告。
- 由非实现者完成的安全复核结论和残余风险登记。

## In Scope

- Host 不可信输入边界、Runtime 容器/签名/内存保护和错误处理。
- 依赖锁、来源、哈希、许可证、已知漏洞和构建插件。
- 仓库、构建产物、日志和发布包的秘密/明文 DEX 扫描。
- fuzz、篡改、兼容、性能与可重现证据的完整性。

## Out of Scope

- 宣称无法逆向、无法调试或绝对防止内存截取。
- 对 Android 平台或第三方 build-tools 作超出本产品调用边界的全面审计。
- 接收生产私钥、keystore、alias 或密码进行测试。
- 用审查文档替代未完成的设备或 fuzz 证据。

## Implementation Decisions

- 由未实现被审查安全敏感任务的 `security-review-agent` 执行复核，`/root` 只负责确认门禁与合并；实现作者不得自我批准。
- Gradle 依赖必须启用 dependency locking 与 verification metadata；SBOM 固定为 CycloneDX JSON，组件包含版本、许可证、来源和哈希。
- 漏洞门禁固定为：未解决 Critical/High 为失败；Medium 必须有负责人、影响分析、到期日和 `/root` 明确接受；Low 进入风险登记。
- 分发依赖必须具有 SPDX 可识别许可证并登记于 `THIRD_PARTY_NOTICES.md`；未知许可证、来源不明二进制或未固定版本为失败。
- 源码/产物扫描必须确认产品不调用 APK 签名工具或 API，不包含私钥、keystore、alias、密码、真实客户 APK、明文客户 DEX或凭据。
- 安全声明固定使用“提高攻击成本”和“残余风险”，不得使用绝对防御措辞。

## Public Interfaces

- Gradle 门禁 `./gradlew securityReview`。
- `build/reports/security/release-gate.json`，字段为 `commit`、`sbomSha256`、`checks`、`findings`、`residualRisks`、`reviewer` 和 `decision`。
- `build/reports/security/bom-v0.1.0.cdx.json`。
- 审查决定仅允许 `PASS` 或 `BLOCKED`。

## Security Constraints

- 审查环境不得导入或请求任何生产签名材料；产品永不签名。
- 可安装集成测试只允许使用构建输出中的一次性非生产证书，并在完成后清理。
- 报告不得嵌入密钥、完整敏感文件内容、真实 APK 或用户绝对路径。
- 任何认证绕过、明文落盘、未受控 Native 内存错误或未解决 Critical/High 漏洞直接阻塞发布。

## Compatibility Requirements

- 审查对象必须是与 M3-03/M3-04/M3-05 相同 commit 和锁定 toolchain 构建的 Release Candidate。
- 四 ABI、API 29 至最高受支持 API、Windows 与 Ubuntu 证据必须完整。
- ARM-only 限制和 x86/x86_64 非风险信号规则必须出现在审查清单。
- SBOM 同时覆盖 Host 分发依赖与嵌入 APK 的 Runtime 依赖。

## Acceptance Criteria

- `./gradlew securityReview` 退出码为 `0`，`release-gate.json` 的 `decision` 为 `PASS`。
- SBOM 中每个分发组件都有固定版本、来源、SHA-256 和 SPDX 许可证，且与依赖锁及发布包内容一致。
- 未解决 Critical/High 漏洞为零，所有 Medium 均具备完整接受记录，未知许可证和来源不明二进制为零。
- 源码与产物扫描确认产品无 APK 签名能力、无私钥/keystore/密码、无真实客户 APK 和明文客户 DEX。
- 独立复核者确认 M3 全部证据哈希可复算，文档明确动态防护只能提高成本。

## Required Tests

- dependency lock/verification、SBOM schema、组件与发布包一致性测试。
- 漏洞严重度门禁、许可证 allowlist/denylist 和未知来源失败测试。
- 产品签名能力、秘密、证书、APK/DEX 敏感材料与绝对安全措辞扫描。
- tamper/fuzz、API/ABI、跨平台和性能证据引用完整性测试。

## Required Evidence

- 所有扫描命令、工具版本、退出码和 Release Candidate commit。
- SBOM、依赖锁、许可证报告、漏洞报告和发布门禁文件 SHA-256。
- 独立复核者、发现项处置、残余风险与最终决定。
- 一次性测试证书清理结果和产品无签名能力证明。

## Likely Files

- `build.gradle.kts`
- `gradle/verification-metadata.xml`
- `gradle/dependency-locks/`
- `THIRD_PARTY_NOTICES.md`
- `docs/security/security-review-v0.1.0.md`
- `tools/security-review/`
- `.github/workflows/security-review.yml`

## Dependencies and Blockers

任一 M3 门禁未完成、证据 commit 不一致、存在未解决 Critical/High、许可证不明或独立复核者不可用时，本任务保持 blocked。不得通过降低严重度、删除测试或省略组件来获得通过。

## Agent Handoff Requirements

使用分支 `chore/m4-01-security-supply-chain-review`，只处理 Issue `M4-01` 并仅创建一个对应 PR。交接必须包含审查者独立性、全部命令与退出码、发现项、接受记录、SBOM/报告 SHA-256、最终 `PASS/BLOCKED` 和残余风险；根 `HandOff.md` 仅由 `/root` 更新。
