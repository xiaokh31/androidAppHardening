---
id: M1-01
title: 不可信独立 APK 检查器
milestone: M1
status: planned
owner_role: host-pipeline-agent
depends_on:
  - M0-05
required_skills:
  - implement-apk-postprocessor
security_sensitive: true
---

## Goal

实现只读、有界、失败关闭的 APK 检查器，在任何变换或解压落盘前生成不可变分析模型，并拒绝结构恶意、超出资源预算或不在 v0.1 支持范围内的输入。

## Background

APK 是攻击者可控 ZIP。路径、计数、长度、压缩比、AXML、DEX header 和框架特征均不可直接信任。后续 signer、manifest、container 和 repacker 只能消费检查器确认过的模型，不能各自重新猜测输入结构。

## Inputs

- 一个以只读共享模式打开的独立 `.apk` 文件。
- M0-05 冻结的兼容性 gate 与项目保留命名空间。
- 仓库源码生成的正常、边界、损坏和恶意 ZIP/APK fixtures。

## Expected Outputs

- `host/apk-inspector` 模块。
- 不可变 `ApkInspection`、`ZipEntryRecord`、`ManifestSummary`、`DexSummary`、`NativeAbiSummary` 和 `CompatibilityFinding` 模型。
- 稳定 `INPUT_*`/`COMPAT_*` 错误码和显式资源限制。
- 不修改、不提取输入文件的单元、模糊和跨平台测试。

## In Scope

- 交叉验证 EOCD、central directory、local header、偏移、长度、CRC 和条目名称。
- 拒绝路径穿越、绝对路径、反斜杠、NUL、Unicode 规范化冲突、重复条目、加密条目和保留命名空间冲突。
- 有界解析 binary `AndroidManifest.xml`、DEX header/class descriptor 和原生库 ABI。
- 确认单体 APK、`minSdk >= 29`、连续 DEX 序号和支持的 Java/Kotlin 应用类型。
- 检测 Split/AAB、Flutter、Unity、React Native、热修复、插件化 Runtime 和已有加固壳的高置信特征。

## Out of Scope

- 签名密码学验证；由 M1-02 完成。
- 修改 AXML、加密 DEX、写出 APK 或执行 Android 代码。
- 反编译方法体、资源或业务逻辑。
- 对未知框架做“尽力处理”输出。

## Implementation Decisions

- 不解压 ZIP 条目到文件系统；使用 seekable 只读 channel 与显式 bounded stream。
- v0.1 限制固定为：APK 小于等于 `2147483647` bytes、条目不超过 `65535`、UTF-8 路径不超过 `1024` bytes、单条目解压后不超过 `1073741824` bytes、总解压量不超过 `4294967296` bytes、压缩比不超过 `200:1`、Manifest 不超过 `16777216` bytes、单 DEX 不超过 `536870912` bytes、DEX 不超过 `64` 个。
- 仅接受合法 UTF-8 条目名；以 Unicode NFC 和 `/` 规范化后检测碰撞，但在模型中保留原始名称 bytes 的 SHA-256。
- DEX 名称只接受 `classes.dex`、`classes2.dex` 至 `classes64.dex`，序号必须从 1 连续且 header magic/file size/header checksum 可验证。
- package name 必须是 AXML 中唯一、非空且可由 `aapt2` 接受的应用 ID；模型保留解码后的精确字符串及其 UTF-8 SHA-256，不做大小写折叠或 Unicode 规范化。后续 Host 任务只能消费该字段，不得重新解析或猜测包名。
- 保留命名空间固定为 `assets/ah/runtime/`、`ah/runtime/` 类描述符及 `lib/*/libah_runtime.so`；任一冲突返回 `COMPAT_RESERVED_NAMESPACE`。
- 框架规则以版本化只读表实现；Flutter、Unity、React Native、Tinker/Sophix 等热修复、插件 Runtime 或已有壳出现至少一个高置信 marker 即拒绝，并在模型中记录命中的 marker ID。
- 分支名固定为 `feat/m1-01-untrusted-apk-inspector`，Issue 标题固定为 `[M1-01] Untrusted APK inspector`，仅允许一个关联 PR。

## Public Interfaces

- `ApkInspector.inspect(Path input): ApkInspection`。
- `ApkInspection` 至少包含 `inputSha256`、`packageName`、`minSdk`、`targetSdk`、`applicationClass`、`appComponentFactoryClass`、`dexEntries`、`nativeAbis`、`findings`、`limitsApplied`。
- `ApkInspection.packageNameSha256` 是 `packageName` 精确 UTF-8 bytes 的 32-byte SHA-256 只读副本。
- `InspectionException.code` 使用 `INPUT_IO`、`INPUT_ZIP_STRUCTURE`、`INPUT_LIMIT_EXCEEDED`、`INPUT_DUPLICATE_ENTRY`、`INPUT_PATH_UNSAFE`、`INPUT_MANIFEST_INVALID`、`INPUT_DEX_INVALID`、`COMPAT_MIN_SDK`、`COMPAT_SPLIT`、`COMPAT_FRAMEWORK`、`COMPAT_EXISTING_SHELL`、`COMPAT_RESERVED_NAMESPACE`。
- 模型集合保持输入顺序且不可变；错误自动化只解析 code，不解析自然语言。

## Security Constraints

- 所有 offset、size、count、ratio 计算使用 checked arithmetic；溢出即 `INPUT_ZIP_STRUCTURE`。
- 不按 entry name 创建路径，不信任 ZIP 声明大小进行一次性分配。
- 错误和日志只记录安全文件名、entry index、限制名和稳定 code，不记录 DEX 内容或用户绝对路径。
- 输入在成功与失败结束时重新计算 SHA-256；与开始值不同则返回 `INPUT_CHANGED`。
- 安全敏感实现必须由非作者 reviewer 独立复核。

## Compatibility Requirements

- Windows x64 与 Ubuntu x64 对同一 fixture 返回相同顶层结果、错误码、DEX 顺序、ABI 与 marker IDs。
- 接受标准 Java/Kotlin 单/多 DEX、自定义 `Application`/`AppComponentFactory`、受支持 ABI 的独立 APK。
- AAB、APKS、Split、dynamic feature、Flutter、Unity、React Native、热修复和已有壳必须在变换前拒绝。
- 原生 ABI 只报告 APK 实际提供值，不根据 Runtime 能构建的 ABI 补全。

## Acceptance Criteria

1. `./gradlew :host:apk-inspector:test` 退出码为 `0`，覆盖每个公开错误码。
2. 路径穿越、重复 NFC 名、central/local 长度冲突、offset 溢出、Zip64、加密 entry、压缩炸弹和超限 fixture 均在读取预算内拒绝且不创建提取文件。
3. 单 DEX、多 DEX、自定义 Application/factory 和四种独立 ABI fixture 产生预期不可变模型。
4. 每类不支持应用 fixture 返回规定 `COMPAT_*` code 与稳定 marker ID，且没有输出 APK。
5. 处理前后输入 SHA-256 相同；成功、异常和取消路径都关闭文件句柄。
6. Windows 与 Ubuntu corpus 运行结果 JSON 规范化后字节相同。
7. 10,000 个由 seeded fuzzer 生成的结构样本不发生进程崩溃、无限循环、未界定分配或非稳定错误。
8. package name 的缺失、重复/冲突字符串池引用、非法语法与解码异常均返回 `INPUT_MANIFEST_INVALID`；正常模型的 `packageNameSha256` 与独立 UTF-8 SHA-256 一致。

## Required Tests

- 正常单/多 DEX 与 manifest/ABI 模型单元测试。
- 每项 ZIP/AXML/DEX 边界和稳定错误码负向测试。
- 框架、Split、保留命名空间兼容性测试。
- 文件句柄释放、输入变化检测和零落盘测试。
- seeded property/fuzz corpus 测试，以及 Windows/Ubuntu 等价测试。

## Required Evidence

- 测试命令、退出码、OS/JDK 版本、seed 和 corpus 样本数。
- 每个错误码至少一个 fixture 名、输入 SHA-256、期望/实际结果。
- 正常模型的脱敏 JSON、资源峰值和零提取目录快照。
- 安全 reviewer 结论、提交 SHA、Issue 与唯一 PR 链接。

## Likely Files

- `host/apk-inspector/src/main/kotlin/`
- `host/apk-inspector/src/test/kotlin/`
- `host/apk-inspector/src/test/resources/`
- `docs/evidence/M1-01/`

## Dependencies and Blockers

- M0-05 compatibility gate 必须通过并冻结保留命名空间。
- binary AXML 只读能力若无法在资源限制内提供所需字段，任务 blocked；不得调用 Apktool/JADX 绕过。
- 新增第三方解析依赖前必须完成来源、许可证、漏洞和 verification metadata 审计。

## Agent Handoff Requirements

- 本任务固定使用分支 `feat/m1-01-untrusted-apk-inspector`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、平台、fixture/corpus SHA-256、资源峰值和 reviewer 证据。
- worker 不修改根 `HandOff.md`，不实现 signer、写出或相邻 transformer。
- 真实 APK 暴露新结构冲突时只提交脱敏最小合成复现和 blocked 交接。
