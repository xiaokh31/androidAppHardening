# ADR 0009: Native 密码后端使用固定 Mbed TLS/TF-PSA-Crypto

## Status

Accepted

## Context

M2-02 必须在 Native 层完成 ADR 0006/0008 规定的 AES-256-GCM 认证解密和 HKDF-SHA-256。NDK 29 不提供可供应用稳定链接的公共 AES-GCM/HKDF 库；Android 平台 BoringSSL 的私有 ABI 也不是 NDK 应用合同。自行实现密码原语、复制零散源码或构建时下载浮动依赖均违反项目供应链和密码工程边界。

候选包括 Mbed TLS、OpenSSL、AWS-LC 与 BoringSSL。BoringSSL 官方明确不保证第三方 API/ABI 稳定且不是 NDK 公共库；OpenSSL 3.5 LTS 和 AWS-LC 能满足算法要求，但其 provider/汇编/构建面明显大于本项目所需的两个原语。Mbed TLS 4.1 是至 2029-03 的 LTS 分支，并把 PSA Crypto 独立为可单独静态链接的 TF-PSA-Crypto。

最初候选 4.1.0 在 2026-07-07 官方公告中被列为受多个安全问题影响，包括 CVE-2026-54435，因此不得采用。4.1.1 是同一 LTS 分支的修复版本。

## Decision

固定 Mbed TLS `4.1.1` 官方完整归档，并且只构建其中 bundled TF-PSA-Crypto `1.1.1`：

- release tag `mbedtls-4.1.1`
- annotated tag object `783058d12831aedd3ef57a64577f6f8a88d23bd3`
- commit `0a8fda272a5a0abef3b47c91bed37185d5a726b1`
- archive `mbedtls-4.1.1.tar.bz2`，`7099934` bytes
- SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`
- license choice Apache-2.0 from upstream dual `Apache-2.0 OR GPL-2.0-or-later`

GitHub 自动生成的 `Source code (zip/tar.gz)` 不得使用；只有官方 release asset 包含配置所需 submodule/generated 内容。下载和解压目录固定为仓库根 `.toolchains/native-crypto/` 并保持 Git 忽略；Gradle/CMake 本身不得访问网络。

Runtime 只从归档的 `tf-psa-crypto` 子目录构建静态 target，不构建、不链接 Mbed TLS 的 TLS/X.509 库。内部 facade 固定为 AES-256-GCM authenticated decrypt、HKDF-SHA-256 与 secure zero；PSA 的 AES/GCM/SHA-256/HMAC/HKDF 之外不构成可消费能力，所有符号默认隐藏。

TF-PSA-Crypto `1.1.1` 的 PSA Crypto API 不提供完整并发安全保证。facade 因此以进程内全局 mutex 串行化每个完整 AES-GCM/HKDF backend transaction，包括初始化、key import、operation、abort/destroy；对调用方保留多线程可调用合同，但不宣称后端内部并行。

供应链准备严格分为认证前与认证后：网络归档必须先独立命中精确长度/SHA-256，随后才允许 archive parser 将其解压到新建空临时目录；完整常规文件树固定为 `3927` files、`60515866` bytes、SHA-256 `7c4ba6554fed6eb67c201054bc75b124fcdc0649e2f56cd762746e01a25d2140`。Unix 解包还必须恰有 `147` 个 symlink 且全部位于未启用的 ML-DSA examples 前缀；固定 Windows CMake 可安全跳过这些 symlink，因此也接受 `0`，其他数量或位置均失败。只有树、许可证和版本全部通过并写入归档/树身份 stamp 后才能原子提升为 CMake/Gradle 可消费目录，失败时清理归档、临时目录和候选目录。

上游 4.1.1 release body 声称 bundled TF-PSA-Crypto 从 1.1.0 升级到 1.2.0，但官方完整归档的 `tf-psa-crypto/CMakeLists.txt` 与 `ChangeLog` 均明确为 1.1.1。构建和审计以已锁定归档的实际 bytes 为准；机器校验要求 1.1.1，并把 release-note 不一致保留为审计证据。

### Point-in-time vulnerability review

截至 2026-08-07：

- 4.1.0 受 2026-07 官方公告影响；4.1.1 包含同 LTS 分支修复，故拒绝 4.1.0。
- bundled TF-PSA-Crypto 1.1.1 修复 CVE-2026-54435、CVE-2026-50584、CVE-2026-50587 等上游列出的 1.1.0 问题。
- CVE-2025-66442 描述的 compiler-induced timing issue 影响 RSA 与 CBC/ECB decrypt，并要求特定 RISC-V/LLVM 条件；本项目不启用这些算法且 Android ABI 不含 RISC-V，因此当前不可达，但仍记录而非宣称库“无漏洞”。
- CVE-2026-25832 明确影响 Mbed TLS 4.1.1 的 TLS 1.3 client HelloRetryRequest group-policy 校验，4.1.2 修复；本选择版本仍受影响，但本项目不构建或链接 TLS/SSL target，因此当前不可达。任何 TLS 能力启用都必须先阻断并升级/重做 ADR，而不能沿用本结论。
- TLS、X.509、PKCS、RSA、ECC、ChaCha20、CBC/ECB 均不在构建消费与动态导出面；未来若扩大算法面，必须新增 ADR/任务并重做公告可达性分析。

安全公告是 point-in-time 证据，不是永久保证。每次发布前和依赖公告出现时，M4-01 必须重新查询官方 advisory，任何可达问题触发独立升级 PR。

## Consequences

积极结果：M2-02 使用成熟、维护中的标准实现；版本、bytes、许可证和漏洞边界可重复；Android 四 ABI 共用同一源归档；不依赖系统私有 ABI。

代价：构建前必须显式准备约 7 MB 官方源归档；静态代码增加 Runtime 体积；项目必须持续跟踪 4.1 LTS 与 bundled TF-PSA-Crypto 公告；标准向量通过不能被描述为 FIPS 140 认证。

## Rejected Alternatives

- 自制 AES/GCM/HKDF：密码实现和审计风险不可接受。
- Android/BoringSSL 私有 `libcrypto`：不是稳定 NDK ABI，官方不支持一般第三方依赖。
- OpenSSL 3.5 LTS：成熟但 provider、构建和代码面超出当前两个原语的最小需求。
- AWS-LC：可支持 Android，但面向更广的 TLS/crypto 集成；当前没有优于 Mbed TLS LTS 最小子库的项目收益。
- Mbed TLS 4.1.0：已被 2026-07 官方安全公告明确列为受影响版本。
- vendor 上游源码进 Git：扩大仓库、复核和升级 diff；改用不可变官方归档与失败关闭准备步骤。

## Security Impact

固定修复版 LTS 和最小静态链接面降低了自制算法、私有 ABI、浮动下载与不必要协议代码的风险。认证失败输出清零、operation abort 和 key destroy 是 facade 的强制合同。该成本防御仍不阻止已控制进程读取成功解密后的明文，且 point-in-time 漏洞复核不构成未来“无漏洞”保证。

## Compatibility Impact

该选择不改变 AHDC v2 wire format、Host JCA 输出、`minSdk 29` 或输入 APK 的 ABI 范围。固定 facade 在同一源归档上构建 Android 四 ABI，并在 Windows/Ubuntu Host 运行标准向量；Mbed TLS/PSA 类型不进入产品公共 Java/JNI ABI。未来升级若改变结果、错误语义、尺寸或 ABI，必须通过独立任务和 ADR 重新批准。

## Verification

- 机器锁精确校验归档 bytes/SHA-256、tag/commit、license hashes、TF-PSA version、源 URL、算法/ABI 清单与完整解压树；归档校验发生在解包前。
- NIST AES-256-GCM 与 RFC 5869 case 1 在同一 C++ facade 上通过；tag/nonce/key/output、零长度、HKDF 8160/8161、null/length 参数矩阵失败关闭，并通过多线程压力测试。
- NDK 29/CMake 4.1.2 构建四 ABI Release；ELF scan 不出现 `libcrypto`、TLS/X.509 动态依赖、非预期导出或超范围本地密码符号。
- Ubuntu/Windows Host Release self-test、独立只读安全复核、PR CI 和 post-merge `main` 门禁全部通过后，M2-02 才能恢复。
