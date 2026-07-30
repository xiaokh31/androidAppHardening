---
id: M4-02
title: "跨平台发布打包"
milestone: M4
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-03
  - M4-01
required_skills:
  - validate-protected-apk
  - plan-apk-hardening-change
security_sensitive: true
---

## Goal

从已通过安全门禁的同一 commit 生成可重现的 Windows x86_64 与 Ubuntu x86_64 v0.1.0 发布包、校验和、SBOM 和来源证明。

## Background

发布包提供离线 Host CLI，并嵌入四 ABI Android Runtime 资源。CLI 只读取独立 APK 并生成新的未签名 APK；发布包不得包含或提供私钥管理与 APK 签名功能。

## Inputs

- M4-01 `PASS` 的 Release Candidate commit 和 SBOM。
- M3-03 的跨平台确定性规则。
- M1-06 Host CLI 与 M2-04 四 ABI Runtime Release 产物。
- Apache-2.0 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`。

## Expected Outputs

- `androidAppHardening-0.1.0-windows-x86_64.zip`。
- `androidAppHardening-0.1.0-ubuntu-x86_64.tar.gz`。
- `SHA256SUMS`、CycloneDX SBOM、构建 provenance 和发布 manifest。
- 两个平台的离线 smoke test 与可重现构建报告。

## In Scope

- 平台 launcher、Host 应用、运行依赖、嵌入 Runtime、许可证和最小操作文档。
- 固定 entry 顺序、时间戳、权限位、压缩参数和文件名。
- 解包后 `protect`、`--help`、`--version` 与未签名输出检查。
- 发布包内容清单和 SHA-256。

## Out of Scope

- APK 签名、keystore/alias/密码输入、证书托管和商店发布。
- macOS、Linux 非 x86_64 和 Windows 非 x86_64 Host 包。
- 自动下载 JDK、Android SDK 或在线依赖。
- 在发布包中包含测试私钥、fixture APK 或调试符号。

## Implementation Decisions

- 固定包名与版本为 `androidAppHardening-0.1.0-windows-x86_64.zip` 和 `androidAppHardening-0.1.0-ubuntu-x86_64.tar.gz`。
- 两包根目录固定包含 `bin/`、`lib/`、`runtime/`、`docs/QUICKSTART.md`、`LICENSE`、`THIRD_PARTY_NOTICES.md`、`bom.cdx.json` 和 `release-manifest.json`。
- 构建时间取 Release Candidate commit 时间并转换为 UTC；文件按 UTF-8 字节序排序，ZIP 使用固定时间/权限，tar 使用固定 owner/group，gzip 禁用原始文件名和当前时间。
- launcher 只转发参数并强制 UTF-8；Windows 为 `aah.cmd`，Ubuntu 为可执行 `aah`。
- `release-manifest.json` 固定记录版本、commit、toolchain、平台、文件 SHA-256、Runtime 四 ABI 和输出“unsigned APK only”能力声明。
- GitHub/OIDC provenance 可证明发布包来源，但不得被描述为 APK 签名；产品二进制内不得存在签名命令。

## Public Interfaces

- Gradle 入口 `:distribution:packageWindows`、`:distribution:packageUbuntu` 和 `:distribution:verifyRelease`。
- CLI 入口 `bin/aah.cmd` 与 `bin/aah`。
- 发布文件名固定为上述两个压缩包、`SHA256SUMS`、`bom.cdx.json` 和 `provenance.json`。
- `release-manifest.json` schema 位于 `distribution/release-manifest.schema.json`。

## Security Constraints

- 发布包与 launcher 不得接收、读取或转发私钥、keystore、alias 或密码，也不得调用 APK 签名工具。
- 仅包含经过 M4-01 SBOM 审查的依赖；内容哈希与 SBOM 不一致即失败。
- 不包含 source map、未剥离 Native 符号、测试证书、fixture APK、明文 DEX 或环境凭据。
- smoke test 输入使用合成 fixture，若需安装则在产品外部使用一次性非生产证书。

## Compatibility Requirements

- Windows 与 Ubuntu x86_64 Host 离线运行。
- 嵌入 Runtime 同时包含 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。
- 输入仍要求独立 APK 且 `minSdk >= 29`；不支持项返回 M1-06 的稳定错误码。
- ARM-only 输入限制保持原样，不宣称转换为 x86 应用。

## Acceptance Criteria

- `./gradlew :distribution:packageWindows :distribution:packageUbuntu :distribution:verifyRelease` 退出码为 `0`。
- 在干净 Windows/Ubuntu 环境解包后，`--help`、`--version` 和 `protect` smoke test 均成功，输出通过未签名检查且输入 SHA-256 不变。
- 同一 commit 与 toolchain 连续构建两次，各平台发布包 SHA-256 分别完全一致。
- 发布包内容与 `release-manifest.json`、`SHA256SUMS` 和 SBOM 完全一致，四 ABI Runtime 齐全。
- 敏感/签名能力扫描为零，离线 smoke test 在网络阻断时仍成功。

## Required Tests

- archive entry 顺序、时间、权限、owner/group 和重复构建哈希测试。
- Windows/Ubuntu launcher 参数、Unicode 相对路径和退出码测试。
- 离线 `protect`、输入只读和未签名输出测试。
- 发布包内容白名单、SBOM 一致性、四 ABI 与敏感材料扫描。

## Required Evidence

- Release Candidate commit、toolchain、平台、全部命令与退出码。
- 两次构建的压缩包、`SHA256SUMS`、SBOM、manifest 和 provenance SHA-256。
- 干净环境与断网 smoke test 结果。
- 产品无签名能力、无敏感材料和四 ABI 内容证明。

## Likely Files

- `distribution/build.gradle.kts`
- `distribution/src/main/scripts/aah`
- `distribution/src/main/scripts/aah.cmd`
- `distribution/release-manifest.schema.json`
- `distribution/docs/QUICKSTART.md`
- `.github/workflows/release-packaging.yml`

## Dependencies and Blockers

M4-01 未 `PASS`、跨平台等价性失败或依赖/SBOM 与候选 commit 不一致时不得打包。任一平台无法重复生成相同哈希时任务保持 blocked，不得手工修改压缩包。

## Agent Handoff Requirements

使用分支 `chore/m4-02-release-packaging`，只处理 Issue `M4-02` 并仅创建一个对应 PR。交接必须提供两个发布包内容清单、两次构建哈希、命令与退出码、离线 smoke test、SBOM/provenance、敏感扫描和独立安全复核；不得发布或上传未获 `/root` 批准的产物。
