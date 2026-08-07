---
id: M2-07
title: "Native 密码后端与供应链固定"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-03
  - M1-04
required_skills:
  - plan-apk-hardening-change
  - implement-runtime-protection
security_sensitive: true
---

## Goal

在 M2-02 开始实现容器解密前，选择、固定并验证唯一 Native 密码后端，提供最小 AES-256-GCM 认证解密与 HKDF-SHA-256 接口，并建立可重复、失败关闭的下载、许可证、漏洞和四 ABI 供应链门禁。

## Background

NDK 29 提供 zlib，但不提供可供产品链接的稳定 AES-GCM/HKDF 公共库。M2-02 不得自制密码算法、调用 Android 私有 `libcrypto`、依赖不稳定的 BoringSSL ABI，或在构建时获取未固定远程代码。本任务是 M2-02 的独立前置合同，不实现 AHDC 解析、DEX 解压、内存映射、JNI handle 或 ClassLoader。

## Inputs

- ADR 0006/0008 的 AES-256-GCM、HKDF-SHA-256、nonce、AAD 和密钥清理合同。
- M0-03 固定的 NDK `29.0.14206865`、CMake `4.1.2` 与四 ABI。
- 上游官方发布归档、许可证和安全公告。

## Expected Outputs

- ADR 0009 的库选择、拒绝方案、版本边界和升级策略。
- `tools/validation/m2-07-native-crypto.json` 机器可读锁与下载校验脚本。
- 仅链接 TF-PSA-Crypto 静态库的最小 C++ facade；不把 TLS、X.509、RSA 或 ECC 接口暴露给 Runtime。
- NIST AES-256-GCM、RFC 5869 HKDF-SHA-256、tag 篡改与参数失败测试。
- 四 ABI 构建、符号/依赖检查、双平台 Host 向量与独立安全复核证据。

## In Scope

- 固定 Mbed TLS `4.1.1` 官方完整归档、annotated tag/commit、字节数、SHA-256 和 bundled TF-PSA-Crypto `1.1.1`。
- 选择 Apache-2.0 许可选项并履行 NOTICE/源代码获取说明。
- facade 只启用/消费 AES-256-GCM decrypt、SHA-256、HMAC、HKDF；TF-PSA 的 `psa_crypto_init()` 要求内部 CTR-DRBG/entropy，GCM 与 DRBG 共同保留内部 AES block (`mbedtls_aes_crypt_ecb`) 实现，但不启用或暴露 PSA ECB/CBC 算法能力；统一错误分类和输出清零。
- 归档下载到仓库根 `.toolchains/native-crypto/`，该目录保持 Git 忽略。
- 截至复核日期的官方安全公告可达性分析和后续升级门禁。

## Out of Scope

- AHDC v2 parser、manifest MAC、record/chunk 派生、zlib、DEX 映射和 `InMemoryDexClassLoader`。
- TLS、X.509、PKCS、RSA、ECC、随机密钥生成、证书验证或网络功能。
- FIPS 140 模块认证声明；标准向量通过不等于产品获得 FIPS 认证。
- 提交上游归档、解压源代码、预编译库或其他大体积工具。

## Implementation Decisions

- 唯一来源为 Mbed TLS `4.1.1` release 的 `mbedtls-4.1.1.tar.bz2`；拒绝 GitHub 自动生成的 source archives，因为它们不包含完整 submodule/generated 内容。
- 归档必须同时命中字节数 `7099934` 与 SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`；tag object、commit 与 bundled TF-PSA 版本也必须匹配锁文件。
- 构建只 `add_subdirectory` 官方归档中的 `tf-psa-crypto`，静态链接到隐藏符号的 `libah_runtime.so`；不构建或链接 `mbedtls` TLS 与 `mbedx509`。
- `ah_crypto_config.h` 的精确 SHA-256 和四 ABI 必须保留的内部 CTR-DRBG/entropy/AES-block/platform-hook 本地符号名称与 `t/d` 绑定类型进入机器锁；这些符号仅为 PSA 初始化、GCM 与 DRBG 内部依赖，不构成 facade 或 PSA ECB/CBC/随机密钥生成能力。任何名称、数量、类型增加/删除/变化或动态导出均失败关闭并要求重新做可达性复核。
- C++ facade 只提供 `aes256GcmDecrypt`、`hkdfSha256` 和不可优化掉的 `secureZero`。认证失败必须清零调用方输出并返回独立错误；不得返回未认证 plaintext。
- 下载与解压是显式准备步骤。Gradle/CMake 不访问网络，依赖缺失、版本/许可证不匹配或锁校验失败时立即失败。
- 下载归档在任何 archive parser 处理前必须先核对精确机器锁；只能解压到新建空临时目录，完整常规文件树清单校验通过并写入锁定 stamp 后再原子提升。Gradle/CMake 只消费该 stamped 目录。
- 上游 4.1.1 release note 把 bundled TF-PSA-Crypto 写为 `1.2.0`，但官方完整归档的 `tf-psa-crypto/CMakeLists.txt` 与 `ChangeLog` 均为 `1.1.1`；合同以锁定归档内容为准并把该不一致记录为供应链审计发现。

## Public Interfaces

- `ah::crypto::Status aes256GcmDecrypt(...)`：仅接受 32-byte key、12-byte nonce 和 16-byte tag；成功才保留输出。
- `ah::crypto::Status hkdfSha256(...)`：RFC 5869 SHA-256，输出长度必须为 `1..8160`。
- `void ah::crypto::secureZero(void*, size_t)`：不得被优化移除。
- facade 允许多个 Runtime/JNI 线程调用，但 TF-PSA-Crypto `1.1.1` 的完整 AES/HKDF 后端事务必须由 facade 内部全局 mutex 串行化；不得只保护初始化或 key store。
- 以上接口保持 Native 内部可见，不构成 Host CLI 或外部 SDK API。

## Security Constraints

- 不实现或复制 AES、GCM、GHASH、SHA-256、HMAC 或 HKDF 算法。
- 不调用平台私有 `libcrypto`/BoringSSL，不允许运行时动态加载替代后端。
- GCM tag 失败、backend 错误和异常路径均不得向调用方留下 plaintext；key handle 与 operation 必须 abort/destroy。
- 不记录 key、nonce、tag、IKM、PRK、OKM 或 plaintext。
- 任何版本升级都必须独立 PR，重新固定哈希、许可证、漏洞、四 ABI 和标准向量。

## Compatibility Requirements

- 使用 M0-03 固定的 NDK `29.0.14206865`、CMake `4.1.2`、C++17 和 `minSdk 29`。
- 同一 facade 必须在 Windows x64、Ubuntu x64 与 Android `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 编译；算法结果不得依赖字节序或指针宽度。
- 本任务不改变 AHDC v2 wire bytes、Host JCA 输出或输入 APK 的 SDK/ABI 范围。
- 后端是静态实现细节，不承诺 Mbed TLS/PSA API 为产品公共 ABI。

## Acceptance Criteria

- 机器锁与官方归档的 URL、字节数、SHA-256、tag object、commit、许可证哈希和 bundled TF-PSA 版本逐项匹配。
- Ubuntu/Windows Host self-test 对 NIST AES-256-GCM 与 RFC 5869 case 1 逐字节通过；tag/nonce/key/output 边界负例失败关闭且输出全零。
- Ubuntu/Windows 的同一 Release self-test 必须通过至少 8 线程的 AES/HKDF 并发压力矩阵，证明 facade 的串行化合同。
- Ubuntu Host 与 KVM 必须失败关闭断言锁定的 runtime image、官方 manifest ref 和 GNU C/C++ 精确版本；Windows 只接受机器锁中逐项审查的有限 runtime/manifest 映射，并保持 LLVM、VS/x64 tools 与 `cl.exe` 精确断言。任何未列入的托管镜像均要求重新审查。
- NDK 29/CMake 4.1.2 构建 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`；四个 `libah_runtime.so` 均无 `libcrypto`、TLS 或 X.509 动态依赖。
- 最终链接只保留 facade 需要的 TF-PSA 对象；默认符号隐藏，无非预期 crypto 导出。
- 四 ABI 的未剥离 Release ELF 必须逐字匹配机器锁中的十七个内部 CTR-DRBG/entropy/AES-block/platform-hook 符号名称和 local `t/d` 类型；PSA ECB/CBC 算法仍不得启用，上游符号不得动态导出。
- 官方公告 point-in-time 复核记录所有影响 4.1.0 的 2026-07 安全项已由 4.1.1 修复；CVE-2025-66442 的 padding-decrypt 路径在本 facade 不可达：无 RSA/CBC/ECB PSA 算法、无 padding API、仅 GCM authenticated decrypt，内部 `mbedtls_aes_crypt_ecb` 仅提供 GCM/CTR-DRBG 所需 block operation；后续新公告仍触发升级评估。
- 独立只读安全复核 P0/P1/P2 为零后才允许合并；M2-02 只在本任务合并且 `main` 门禁通过后恢复。

## Required Tests

- archive/length/hash/license/version lock positive and one-byte/field tamper negative tests。
- NIST AES-256-GCM decrypt、错误 tag、错误 key/nonce/tag 长度、零长度与输出不足。
- RFC 5869 case 1、最大长度边界、超过 `255 * HashLen`、空指针/长度组合。
- Ubuntu/Windows Host Release self-test；四 ABI Android Release build、ELF dependency/export scan 与精确内部符号集合比较。
- 多线程重复 AES/HKDF 调用；任何线程的状态码或结果漂移均失败。

## Required Evidence

- 官方来源 URL、发布/复核时间、tag/commit、archive/hash/license 与安全公告清单。
- Host/四 ABI 命令、退出码、OS、NDK/CMake/Clang 版本、产物大小与 SHA-256。
- 独立复核结论、冻结提交 SHA、PR CI 与合并后 `main` 门禁。

## Likely Files

- `docs/adr/0009-native-cryptography-backend.md`
- `docs/TOOLCHAIN_AND_PROVENANCE.md`
- `THIRD_PARTY_NOTICES.md`
- `tools/validation/m2-07-native-crypto.json`
- `tools/validation/verify-m2-07-native-crypto.mjs`
- `runtime/native/src/main/cpp/`
- `.github/workflows/build.yml`

## Dependencies and Blockers

官方归档、许可证或安全公告无法复核，四 ABI 任一构建失败，标准向量不通过，或独立复核存在未关闭发现时，本任务保持 blocked，M2-02 不得恢复。

## Agent Handoff Requirements

使用分支 `chore/m2-07-native-crypto-backend`，只处理 Issue #41 并创建唯一 PR。交接必须给出锁定归档、漏洞可达性、四 ABI、标准向量、独立复核和合并后门禁证据；不得实现 M2-02 的容器/加载器业务。
