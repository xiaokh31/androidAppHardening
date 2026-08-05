---
id: M3-03
title: "Windows 与 Ubuntu 等价性验证"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M0-03
  - M1-05
  - M1-06
  - M2-06
  - M3-01
required_skills:
  - validate-protected-apk
security_sensitive: false
---

## Goal

证明相同输入字节、相同配置和相同锁定工具链在 Windows 与 Ubuntu 上生成结构与行为等价的未签名 APK，以及语义一致的规范 JSON 报告；同时证明每次保护运行的随机密码学材料不会被错误地固定。

## Background

Host 后处理器是跨平台离线工具。路径分隔符、文件排序、ZIP 时间戳、区域设置和换行差异不得改变非随机结构与产品语义；产品在两个平台上都只输出未签名 APK。根据 ADR-0008，每次运行都会生成新的 CEK、build ID、key slot ID 和 nonce prefix，因此完整 APK、容器密文及其 SHA-256 必须允许不同，不能把位级相同作为跨平台门禁。

## Inputs

- M1-06 CLI、JSON schema 和退出码。
- M1-05 的确定性 ZIP/对齐规则。
- ADR-0008 的随机字段、分块认证容器和解密后验证合同。
- M2-06 已完成的最终 Runtime Native、policy 与 bootstrap 测试产物。
- M3-01 的九个合成 fixture。
- M0-03 锁定的 JDK、Gradle、Android build-tools 与依赖版本。

## Expected Outputs

- Windows 与 Ubuntu 对称 CI job。
- 跨平台 hash manifest、规范语义 manifest、随机字段清单、比较器和差异报告。
- 路径、locale、时区和换行扰动测试。
- 平台等价性发布证据。

## In Scope

- ZIP entry 集合、顺序、属性、时间戳、对齐、非替换 entry 字节和容器规范语义比较。
- 两侧容器独立认证、解密和解压后得到的 DEX 顺序、长度与 SHA-256 比较。
- 规范 JSON 中稳定业务字段、排序和路径表示比较。
- CEK、build ID、key slot ID、nonce、MAC、tag、ciphertext、输出 hash、时间和耗时等显式随机或运行字段的差异与非复用检查。
- Windows 长路径/反斜杠与 Ubuntu 大小写敏感路径场景。
- 输入 APK 只读和失败退出码等价性。

## Out of Scope

- 比较外部签名后的 APK 字节。
- 要求完整未签名 APK、容器密文或随机字段位级相同。
- Windows 与 Ubuntu UI 安装器。
- macOS 支持。
- 接受平台特有的算法、格式、排序或非随机 ZIP 输出差异。

## Implementation Decisions

- 两个平台固定 `TZ=UTC`、`LANG=C`、UTF-8、相同 JDK/Gradle/build-tools 版本，并使用 M1-05 固定的 ZIP 时间戳。
- 文件遍历、DEX 顺序和 ZIP entry 固定按 UTF-8 字节序排序，不依赖文件系统枚举顺序。
- JSON 使用 UTF-8、LF、排序键、稳定数组顺序和 `/` 路径分隔符；耗时、主机名、进程 ID 与绝对路径不得进入规范结果。
- 比较器固定把字段分为 deterministic、randomized 和 run-metadata 三类；分类表是版本化接口，禁止临时忽略出现差异的未知字段。
- 每个平台输出都必须独立通过签名缺失、ZIP/对齐、Manifest、Runtime ABI、AHDC 认证和解密后 DEX hash 验证。等价性门禁比较 entry 结构、保留 entry hash、bootstrap/Runtime 字节、Manifest 字节、AHDC v2 版本/record 顺序/名称/原始长度/原始 DEX SHA-256/压缩后长度/canonical chunk topology 及规范报告非随机字段。
- CEK、build ID、key slot ID、nonce、manifest MAC、GCM tag、ciphertext、容器/output SHA-256、时间与耗时只按固定分类归一化；两平台及同平台重复运行的 output SHA-256 必须不同，且 nonce 与标识符不得复用。
- JSON 先按 schema 拒绝未知字段，再对固定的非随机投影做字节级比较；不能通过删除整个 container、output 或 signing 对象获得通过。
- 命令分别使用 `gradlew.bat` 与 `./gradlew`，业务参数和相对 fixture 路径保持一致。
- 平台事实只能进入独立 CI 元数据，不得写入产品 APK 或规范报告；算法、格式或非随机语义出现平台分叉时失败。

## Public Interfaces

- Gradle 入口 `:integration-tests:crossPlatformCorpus`。
- 比较工具 `tools/compare-platform-results`。
- Windows 输出 `build/equivalence/windows/hashes.sha256`、`semantic-manifests.jsonl` 与 `reports.jsonl`，Ubuntu 输出对应文件。
- 汇总输出 `build/reports/equivalence-summary.json`。

## Security Constraints

- 输入 fixture 在两平台运行前后 SHA-256 不变。
- 比较对象只含未签名输出；产品及等价性工具不得接收签名私钥、keystore、alias 或密码。
- CI 日志和报告不得包含 runner 绝对路径、环境凭据或明文 DEX。
- 临时工作区在 job 结束时清理，不上传未授权中间明文。

## Compatibility Requirements

- 支持当前项目声明的 Windows 与 Ubuntu 版本和 x64 主机。
- 使用同一 Java 字节码和依赖锁；平台 launcher 只负责参数转发。
- 非 ASCII 相对路径 fixture 在两平台产生相同结果。
- 输出 APK 仍遵循 `minSdk >= 29` 和四 ABI Runtime 规则。

## Acceptance Criteria

- Windows 执行 `gradlew.bat :integration-tests:crossPlatformCorpus`、Ubuntu 执行 `./gradlew :integration-tests:crossPlatformCorpus`，退出码均为 `0`。
- 九个 fixture 的成对 output SHA-256 均不同，但规范语义 manifest、保留 entry hash、bootstrap/Runtime/Manifest 字节、解密后 DEX SHA-256/顺序及 JSON 非随机投影完全相同。
- 每个 fixture 在同一平台重复两次也产生不同 output/container SHA-256，全部 nonce、build ID 和 key slot ID 无复用；两个输出均独立通过认证与行为验证。
- 时区、locale、工作目录深度和非 ASCII 相对路径扰动不改变规范语义 manifest、错误码或非随机报告字段；不要求随机化输出哈希稳定。
- 同一组恶意/不支持输入在两平台返回相同稳定错误码且不产生部分输出。
- 两平台输入 APK 前后 SHA-256 一致，扫描确认报告不含绝对 runner 路径。

## Required Tests

- 九个 fixture 的双平台端到端语义等价性与同平台重复随机性测试。
- entry 排序、固定时间戳、权限位、换行、JSON key 顺序和路径规范化测试。
- 随机字段分类完整性、未知字段失败、nonce/标识符非复用、两侧独立认证解密与原始 DEX hash 测试。
- 时区、locale、长路径、非 ASCII 路径和大小写差异测试。
- 失败退出码、无部分输出和输入只读对比测试。

## Required Evidence

- 两个平台 OS/JDK/Gradle/build-tools 版本、命令与退出码。
- 双平台 `hashes.sha256`、规范语义 manifest、随机字段非复用报告、ZIP 元数据差异报告和 JSON 非随机投影比较结果。
- 输入前后哈希、扰动矩阵和绝对路径扫描结果。
- CI run 链接及汇总报告 SHA-256。

## Likely Files

- `.github/workflows/cross-platform-equivalence.yml`
- `integration-tests/src/test/kotlin/CrossPlatformCorpusTest.kt`
- `tools/compare-platform-results/`
- `gradle/libs.versions.toml`
- `integration-tests/build/equivalence/`

## Dependencies and Blockers

M1-05 的结构规则、ADR-0008 随机字段合同、M1-06 的 JSON schema 或 M2-06 的最终 Runtime 控制未冻结时不得设为发布门禁。证据必须来自包含 M2-06 的同一 Release Candidate commit；后续任何 Runtime Native、policy 或 bootstrap 字节变化都会使本任务证据失效并要求重跑。任一 fixture 出现非随机语义差异、相同随机标识符、认证失败或输出 hash 意外相同时任务保持 blocked；不得把平台名加入密钥派生、加密或 ZIP 输入来伪造差异或等价性。

## Agent Handoff Requirements

使用分支 `chore/m3-03-windows-ubuntu-equivalence`，只处理 Issue `M3-03` 并仅创建一个对应 PR。交接必须包含两平台环境、命令与退出码、九组成对 hash、规范语义差异报告、随机字段非复用证据、输入只读证据、CI 链接和报告 SHA-256；不得修改产品安全边界。
