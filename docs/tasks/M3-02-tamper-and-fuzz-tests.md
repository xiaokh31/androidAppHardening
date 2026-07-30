---
id: M3-02
title: "篡改与 Fuzz 测试"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M1-03
  - M1-04
  - M1-06
  - M2-02
  - M2-03
  - M2-06
  - M3-01
required_skills:
  - validate-protected-apk
security_sensitive: true
---

## Goal

建立覆盖 APK/AXML/容器/Runtime 边界的确定性篡改矩阵和持续 fuzz 体系，证明恶意输入不会绕过认证、生成明文泄漏或导致未受控崩溃。

## Background

Host 处理器面对不可信 APK，Runtime 面对可被篡改的本地容器。测试重点是严格拒绝、资源上限和可复现错误，不将一次无崩溃运行描述为绝对安全。

## Inputs

- M1-03 Binary AXML 解析与改写入口。
- M1-04 `ContainerV1` 解析器和共享测试向量。
- M1-06 CLI、错误码与 JSON 报告。
- M2-02/M2-03/M2-06 的 Runtime 负向接口。
- M3-01 合成 fixture。

## Expected Outputs

- 版本化 tamper case catalog 和自动变异器。
- JVM 侧 Jazzer fuzz targets、Native libFuzzer targets 与种子语料。
- ASan/UBSan、超时、OOM 和崩溃归档规则。
- 可在本地、PR CI 和 nightly CI 执行的测试任务。

## In Scope

- ZIP 中央目录、重复条目、路径穿越、压缩炸弹、Manifest AXML 和 DEX/container 元数据。
- header、版本、长度、偏移、条目重叠、nonce、tag、ciphertext、签名者策略和元数据摘要篡改。
- Host 解析器与 Native 容器解析器的内存安全、超时和资源上限。
- 最小化 crash corpus 与已修复回归语料。

## Out of Scope

- 对真实客户 APK 或闭源 APK 进行 fuzz。
- 网络服务、Android 内核、ART 本体或第三方签名工具的 fuzz。
- 通过测试驱动器向产品加入签名能力。
- 以 fuzz 时长作为不存在漏洞的证明。

## Implementation Decisions

- JVM/Kotlin 的 APK inspector 与 AXML target 固定使用 Jazzer；Native `ContainerV1` target 固定使用 libFuzzer 并启用 ASan/UBSan。
- PR CI 每个 target 运行固定回归语料并 fuzz 10 分钟；nightly 每个 target 运行 60 分钟。任何 crash、sanitizer 报告、未捕获异常、超时或超出内存上限均失败。
- 所有变异在 `build/fuzz-work/` 的输入副本执行；原始 fixture 在运行前后必须保持相同 SHA-256。
- tamper catalog 为每个变异固定记录目标字节域、预期 Host/Runtime 阶段、错误码和“payload 未加载”断言。
- 外部安装验证只能使用 M3-01 的一次性非生产证书流程；产品永不签名，测试密钥不进入 corpus。
- crash 输入经自动最小化后进入 `fuzz-regressions/`，只允许合成无敏感数据的最小字节样本。

## Public Interfaces

- Gradle 入口 `:fuzz-tests:regressionFuzz`、`:fuzz-tests:prFuzz` 和 `:fuzz-tests:nightlyFuzz`。
- `tamper-tests/catalog.yaml`，字段为 `id`、`target`、`mutation`、`expectedStage`、`expectedCode` 和 `payloadLoaded`。
- 统一结果 `build/reports/security/fuzz-summary.json`。
- corpus 目录 `fuzz-tests/corpus/` 与回归目录 `fuzz-tests/fuzz-regressions/`。

## Security Constraints

- fuzz 子进程设置 2 GiB 内存上限、单输入 5 秒超时和隔离工作目录。
- 输入解析失败必须返回稳定错误，不输出明文 DEX、密钥、完整证书或用户绝对路径。
- tag 或签名策略篡改后，即使重新打包也不得到达 payload 类加载。
- fuzz 结论只能描述已运行范围与残余风险，不能宣称防内存截取或防篡改绝对有效。

## Compatibility Requirements

- JVM targets 同时在 Windows 与 Ubuntu 运行；Native sanitizer targets 在 Ubuntu Clang 环境运行。
- Runtime tamper 用例至少覆盖 API 29 与最高受支持 API。
- 容器回归语料在四 ABI 解析结果与错误码一致。
- x86/x86_64 不是风险信号，相关设备上的篡改结果必须由真实篡改触发。

## Acceptance Criteria

- `./gradlew :fuzz-tests:regressionFuzz :tamper-tests:test` 退出码为 `0`。
- 每个 JVM/Native target 完成 10 分钟 PR fuzz，无 crash、sanitizer、超时、OOM 或未捕获异常。
- tamper catalog 的全部案例均在预期阶段返回预期错误码，且 `payloadLoaded` 全为 `false`。
- 原始 fixture 前后 SHA-256 完全一致，构建输出中不存在落盘明文 payload。
- 所有已发现 crash 都有最小回归样本；修复后连续两次回归执行结果一致。

## Required Tests

- 每个 parser 的固定语料回归、随机变异和结构感知变异测试。
- ZIP path traversal、重复条目、压缩比上限与解压大小上限测试。
- AXML chunk/字符串池/资源引用和容器整数溢出/重叠/tag 篡改测试。
- 签名者策略、元数据摘要、外部重签与 Runtime payload 未加载测试。

## Required Evidence

- 每个 target 的命令、退出码、版本、时长、执行次数和 corpus SHA-256。
- sanitizer 配置、资源上限、tamper 结果表和 payload 未加载证据。
- 原始 fixture 前后哈希与明文扫描结果。
- 失败样本最小化记录和独立安全复核结论。

## Likely Files

- `fuzz-tests/build.gradle.kts`
- `fuzz-tests/src/jazzer/`
- `fuzz-tests/src/native/`
- `fuzz-tests/corpus/`
- `fuzz-tests/fuzz-regressions/`
- `tamper-tests/catalog.yaml`
- `tamper-tests/src/test/`

## Dependencies and Blockers

AXML、容器、CLI 或 Runtime 错误码未冻结时不得冻结 tamper catalog。发现可复现认证绕过、明文落盘或内存安全错误时，本任务和相关发布任务立即 blocked，必须先形成独立安全修复任务。

## Agent Handoff Requirements

使用分支 `test/m3-02-tamper-fuzz`，只处理 Issue `M3-02` 并仅创建一个对应 PR。交接必须给出 targets、语料来源、命令与退出码、运行时长、sanitizer 结果、最小样本哈希、篡改矩阵和独立安全复核；不得附真实 APK、测试私钥或敏感 crash 数据。
