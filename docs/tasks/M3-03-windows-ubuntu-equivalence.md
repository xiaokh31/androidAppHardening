---
id: M3-03
title: "Windows 与 Ubuntu 等价性验证"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M1-06
  - M3-01
required_skills:
  - validate-protected-apk
security_sensitive: false
---

## Goal

证明相同输入字节、相同配置和相同锁定工具链在 Windows 与 Ubuntu 上生成字节一致的未签名 APK及语义一致的规范 JSON 报告。

## Background

Host 后处理器是跨平台离线工具。路径分隔符、文件排序、ZIP 时间戳、区域设置和换行差异不得改变产品产物；产品在两个平台上都只输出未签名 APK。

## Inputs

- M1-06 CLI、JSON schema 和退出码。
- M1-05 的确定性 ZIP/对齐规则。
- M3-01 的九个合成 fixture。
- M0-03 锁定的 JDK、Gradle、Android build-tools 与依赖版本。

## Expected Outputs

- Windows 与 Ubuntu 对称 CI job。
- 跨平台 hash manifest、规范 JSON 比较器和差异报告。
- 路径、locale、时区和换行扰动测试。
- 平台等价性发布证据。

## In Scope

- 未签名 APK 字节、ZIP entry 顺序/属性/时间戳、对齐和容器字节比较。
- 规范 JSON 中稳定业务字段、排序和路径表示比较。
- Windows 长路径/反斜杠与 Ubuntu 大小写敏感路径场景。
- 输入 APK 只读和失败退出码等价性。

## Out of Scope

- 比较外部签名后的 APK 字节。
- Windows 与 Ubuntu UI 安装器。
- macOS 支持。
- 接受平台特有的加密、排序或 ZIP 输出。

## Implementation Decisions

- 两个平台固定 `TZ=UTC`、`LANG=C`、UTF-8、相同 JDK/Gradle/build-tools 版本，并使用 M1-05 固定的 ZIP 时间戳。
- 文件遍历、DEX 顺序和 ZIP entry 固定按 UTF-8 字节序排序，不依赖文件系统枚举顺序。
- JSON 使用 UTF-8、LF、排序键、稳定数组顺序和 `/` 路径分隔符；耗时、主机名、进程 ID 与绝对路径不得进入规范结果。
- 等价性以未签名 APK SHA-256 完全相同为硬门禁；JSON 先按 schema 拒绝未知字段，再做字节级比较。
- 命令分别使用 `gradlew.bat` 与 `./gradlew`，业务参数和相对 fixture 路径保持一致。
- 平台差异只能进入独立 CI 元数据，不得写入产品 APK 或规范报告。

## Public Interfaces

- Gradle 入口 `:integration-tests:crossPlatformCorpus`。
- 比较工具 `tools/compare-platform-results`。
- Windows 输出 `build/equivalence/windows/hashes.sha256` 与 `reports.jsonl`，Ubuntu 输出 `build/equivalence/ubuntu/hashes.sha256` 与 `reports.jsonl`。
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
- 九个 fixture 的未签名 APK SHA-256、ZIP entry 元数据和规范 JSON 字节在两平台完全相同。
- 时区、locale、工作目录深度和非 ASCII 相对路径扰动不改变输出哈希。
- 同一组恶意/不支持输入在两平台返回相同稳定错误码且不产生部分输出。
- 两平台输入 APK 前后 SHA-256 一致，扫描确认报告不含绝对 runner 路径。

## Required Tests

- 九个 fixture 的双平台端到端等价性测试。
- entry 排序、固定时间戳、权限位、换行、JSON key 顺序和路径规范化测试。
- 时区、locale、长路径、非 ASCII 路径和大小写差异测试。
- 失败退出码、无部分输出和输入只读对比测试。

## Required Evidence

- 两个平台 OS/JDK/Gradle/build-tools 版本、命令与退出码。
- 双平台 `hashes.sha256`、ZIP 元数据差异报告和 JSON 字节比较结果。
- 输入前后哈希、扰动矩阵和绝对路径扫描结果。
- CI run 链接及汇总报告 SHA-256。

## Likely Files

- `.github/workflows/cross-platform-equivalence.yml`
- `integration-tests/src/test/kotlin/CrossPlatformCorpusTest.kt`
- `tools/compare-platform-results/`
- `gradle/libs.versions.toml`
- `integration-tests/build/equivalence/`

## Dependencies and Blockers

M1-05 的确定性规则或 M1-06 的 JSON schema 未冻结时不得设为发布门禁。任一 fixture 出现平台哈希差异时任务保持 blocked，必须定位并消除差异，不得把平台名加入加密或 ZIP 输入来规避比较。

## Agent Handoff Requirements

使用分支 `test/m3-03-windows-ubuntu-equivalence`，只处理 Issue `M3-03` 并仅创建一个对应 PR。交接必须包含两平台环境、命令与退出码、九组哈希、差异报告、输入只读证据、CI 链接和报告 SHA-256；不得修改产品安全边界。
