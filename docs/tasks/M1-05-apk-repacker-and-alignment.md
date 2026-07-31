---
id: M1-05
title: APK 重打包、对齐与独立输出验证
milestone: M1
status: planned
owner_role: host-pipeline-agent
depends_on:
  - M1-02
  - M1-03
  - M1-04
required_skills:
  - implement-apk-postprocessor
  - validate-protected-apk
security_sensitive: true
---

## Goal

从只读输入构建一个新的、结构有效、正确对齐且明确未签名的 APK，只替换批准条目，并在独立重读验证全部通过后原子发布。

## Background

修改 APK 会使所有输入签名失效。重打包还可能无意改变资源、原生库压缩方式或 ABI 表象。成功不能以 ZIP writer 未抛异常判断，必须对输出结构、保留内容、签名缺失、对齐和业务 DEX 消失做二次验证。

## Inputs

- M1-01 `ApkInspection`、M1-02 `SignerPolicy`、M1-03 transformed Manifest、M1-04 encrypted container。
- 版本匹配的 `RuntimeBundle`：bootstrap `classes.dex`、四 ABI 各含唯一 `.ah_share_v1` placeholder 的 `libah_runtime.so` template，以及 M1-04 一次性 `KeyPackagingPlanV1`。
- 不存在且与 input 不同文件身份的目标 output path。

## Expected Outputs

- `host/repacker` 的 assembler、ZIP writer、alignment planner、output verifier 和 atomic publisher。
- 一个新的 `output-unsigned.apk`，输入文件保持字节不变。
- `OutputVerification`，包含条目、保留 hash、alignment、ABI、container、manifest 与 unsigned 状态。
- 正常、I/O fault、disk-full、tamper 和 alias fixtures。

## In Scope

- 复制所有允许保留 entry 的原 compressed payload 与语义 metadata。
- 移除原业务 DEX、JAR signing entries 和 APK Signing Block，替换 Manifest。
- 注入 bootstrap DEX、AHDC、config 与按 ABI policy 选择的 Runtime SO。
- 对 STORED entry 做对齐、规范化新增条目、重读并独立验证。
- 输入 SHA-256 前后比较、临时输出清理和同文件系统原子发布。

## Out of Scope

- 生成或调用任何 APK 签名。
- 修改资源、业务 assets、客户原生库、package/version/SDK 或组件。
- 将 ARM-only 应用声明为 x86 compatible。
- CLI 参数与 JSON report 编排；属于 M1-06。

## Implementation Decisions

- 输出布局固定为 transformed `AndroidManifest.xml`、单个 bootstrap `classes.dex`、`assets/ah/runtime/payload.ahdc`、`assets/ah/runtime/config.bin` 和选定 ABI 的 `lib/<abi>/libah_runtime.so`；不得存在 `classes2.dex` 或原业务 DEX。
- `payload.ahdc` 与 `config.bin` 必须各只有一个规范名称，使用 ZIP method `STORED`、预先计算 CRC/size、不使用 data descriptor，并按 4 KiB 对齐其数据起点，使无 `Context` 的 Runtime 可从 `ApplicationInfo.sourceDir` 有界定位；重复、压缩或未对齐均由输出 verifier 拒绝。
- `config.bin` 必须是 `KeyPackagingPlanV1` 提供的精确 176-byte `ConfigV1`。对每个选中 ABI，materializer 按 ADR 0006 验证 template SHA-256、ELF machine、唯一 104-byte `.ah_share_v1` placeholder 和 ABI ID，再写入 `NativeShareSlotV1`；bootstrap DEX 不做每 APK patch。
- 删除项仅为原 `classes*.dex`、`META-INF/MANIFEST.MF`、`META-INF/*.SF`、`META-INF/*.RSA`、`META-INF/*.DSA`、`META-INF/*.EC`、`META-INF/SIG-*` 及重建时自然消失的 APK Signing Block；其他 `META-INF` entry 保留。
- 输入没有 native library 时注入四 ABI Runtime；输入存在 native library 时只为输入实际 ABI 集合注入对应 Runtime，遇到四 ABI 之外的 native ABI 返回 `COMPAT_ABI_UNSUPPORTED`。该策略不补造客户 ABI。
- Runtime SO 与 AHDC/config 使用 STORED；SO data offset 对齐 `16384` bytes，其他 STORED entry 对齐 `4` bytes。bootstrap DEX 使用 DEFLATED level `9`。
- 原 entry 顺序保持，Manifest 在原位置替换；新增项目 entry 按 `classes.dex`、assets、ABI 字典序追加，时间固定为 ZIP DOS epoch，extra/comment 只保留已批准字段。
- 临时文件在目标同目录以 `CREATE_NEW` 创建；output 已存在即拒绝。全部独立验证通过后使用 `ATOMIC_MOVE` 发布，不支持时返回 `OUTPUT_ATOMIC_MOVE_UNSUPPORTED`，禁止非原子降级。
- output 与 input 通过 normalized absolute path、resolved parent、file key 和硬链接身份检查去重；任何 alias 在写入前拒绝。
- 分支名固定为 `feat/m1-05-apk-repacker-and-alignment`，Issue 标题固定为 `[M1-05] APK repacker and alignment`，仅允许一个关联 PR。

## Public Interfaces

- `ApkRepacker.repack(RepackRequest request): OutputVerification`。
- `RepackRequest` 包含已验证输入、目标、Manifest、container、signer policy、RuntimeBundle 和一次性 `KeyPackagingPlanV1`，不含签名 secret。
- `OutputVerifier.verify(Path candidate, ExpectedOutput expected): OutputVerification`。
- 错误码：`PACKAGE_ENTRY_CONFLICT`、`PACKAGE_ABI_MISMATCH`、`PACKAGE_ALIGNMENT`、`PACKAGE_WRITE_FAILED`、`OUTPUT_PATH_ALIAS`、`OUTPUT_ALREADY_EXISTS`、`OUTPUT_VERIFICATION_FAILED`、`OUTPUT_ATOMIC_MOVE_UNSUPPORTED`、`OUTPUT_INPUT_CHANGED`。

## Security Constraints

- 输入始终以只读 channel 使用，不按 entry name 在磁盘创建中间文件。
- 不信任上游 bytes；writer 前再次校验长度，verifier 使用独立 parser 重读。
- 任何失败关闭所有句柄、删除本任务临时输出、清零 `KeyPackagingPlanV1` 及 Runtime materializer 的敏感 buffer，并保持 input/output 目标不变。
- 日志不含绝对路径、DEX 内容、key material 或证书本体。
- 未签名状态是强制验收，不得以测试便利加入生产签名分支。
- 本任务须由独立 ZIP/APK 安全 reviewer 复核。

## Compatibility Requirements

- Windows x64 与 Ubuntu x64 生成结构语义等价的 unsigned APK。
- Java-only APK 获得四 ABI Runtime；原生 APK 的报告/entry 只声明原应用实际 ABI 与对应 Runtime。
- 保持原资源、assets、客户 SO、package、version、SDK、组件和未批准 Manifest 语义。
- 输出可被固定 `aapt2`、`zipalign`、`apksigner` 读取，并可在产品外签名。

## Acceptance Criteria

1. `./gradlew :host:repacker:test` 退出码为 `0`。
2. output 重读验证确认只有 bootstrap `classes.dex`，AHDC/config 路径正确，无原 DEX entry、明文 DEX副本或保留命名空间冲突。
3. 除批准替换/删除/新增项外，每个输入 entry 的 uncompressed SHA-256、compression method、CRC 和内容保持；客户 SO bytes 完全相同。
4. `zipalign -c -P 16 -v 4 output-unsigned.apk` 退出码为 `0`，SO offset 为 16384 对齐，其他 STORED entry 为 4 对齐。
5. `apksigner verify output-unsigned.apk` 以“未签名”失败，内部 verifier 明确 `signingPerformed=false`；生产流程没有调用签名工具。
6. 每个输出 SO 的唯一 share slot magic/version/ABI/build/key slot/digest 与 `ConfigV1` 一致；未选中 ABI 不在输出，bootstrap DEX 与 RuntimeBundle 模板 SHA-256 匹配且未被个性化 patch。
7. input/output 同路径、symlink/hardlink alias、output 预存在、写入异常、磁盘空间耗尽、验证篡改和 atomic move 不支持均非零失败，输入 SHA-256 不变且无成功 output。
8. Java-only、ARM-only、x86-only 与混合 ABI fixtures 产生规定 Runtime ABI 集；ARM-only 不出现 x86 Runtime entry。
9. Windows/Ubuntu 的规范化 entry manifest、保留 hashes、错误码和 alignment 结果相同。

## Required Tests

- 单/多 DEX、resources/assets/META-INF/native libs 的正向重打包测试。
- 签名材料精确删除与非签名 `META-INF` 保留测试。
- 四类 ABI policy、16 KiB/4-byte alignment 测试。
- 同路径/链接 alias、预存在目标、disk-full、short write、close failure、tampered candidate 和 atomic move failure injection。
- 输入前后 hash、句柄释放、临时清理和独立 verifier mutation tests。
- `aapt2`、`zipalign`、`apksigner` 外部工具交叉验证。

## Required Evidence

- 所有 Gradle/Android 工具命令、退出码、OS/JDK/tool versions。
- input、Manifest、container、RuntimeBundle、candidate、final output 和 verification JSON 的 SHA-256。
- entry-level preserved/changed/deleted/added 对照、ABI 表和 alignment offsets。
- 故障注入清理证据、独立 reviewer 结论、提交 SHA、Issue 与唯一 PR 链接。

## Likely Files

- `host/repacker/src/main/kotlin/`
- `host/repacker/src/test/kotlin/`
- `host/repacker/src/test/resources/`
- `docs/evidence/M1-05/`

## Dependencies and Blockers

- M1-02/M1-03/M1-04 的 signer、Manifest 与 container contract 必须稳定。
- RuntimeBundle schema 与 ADR-0005 ABI policy 必须可用；生产 Runtime binaries 可在 M3 集成前替换 synthetic contract fixture。
- 目标文件系统不支持原子 rename 时该运行明确失败，不通过复制降级。

## Agent Handoff Requirements

- 本任务固定使用分支 `feat/m1-05-apk-repacker-and-alignment`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、平台、所有产物 SHA-256、entry/ABI/alignment 对照和 reviewer 结论。
- worker 不修改根 `HandOff.md`，不实现 CLI 或签名便利功能。
- 发现合法 APK 无法按白名单保留时提交最小 synthetic fixture 和 blocked 交接，不扩大删除/重写范围。
