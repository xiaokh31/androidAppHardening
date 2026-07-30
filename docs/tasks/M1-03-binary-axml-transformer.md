---
id: M1-03
title: Binary AXML 白名单变换器
milestone: M1
status: planned
owner_role: host-pipeline-agent
depends_on:
  - M1-01
  - M0-05
required_skills:
  - implement-apk-postprocessor
security_sensitive: true
---

## Goal

直接、最小化地变换 binary `AndroidManifest.xml`，只接入 Shell `AppComponentFactory` 并写入冻结的 `ah.runtime.*` 元数据，同时证明所有未授权语义保持不变。

## Background

文本化再编译 Manifest 会重排资源、丢失未知 chunk 或改变未理解的属性。Host 必须保留现有 string index、namespace、resource ID 和未知数据，只追加必要结构，并以解析后的语义 diff 作为成功门禁。

## Inputs

- M1-01 的 `ManifestSummary` 与原始 binary Manifest bytes。
- M0-05 冻结的 Application/factory 委托契约。
- 容器路径 `assets/ah/runtime/payload.ahdc`、container major `1`、signer policy `1`、risk policy `1`。

## Expected Outputs

- `host/axml` 的有界 reader/writer、`ManifestTransformRequest`、`ManifestTransformResult` 和 `ManifestSemanticDiff`。
- 变换后的 binary Manifest bytes。
- 允许差异白名单验证器与 AXML malformed/fuzz corpus。
- 与 Android `aapt2` 独立解析结果一致的证据。

## In Scope

- 解析/校验 string pool、resource map、namespace、start/end element、attribute 和未知 chunk 边界。
- 规范化原 Application 和原 factory 全限定类名。
- 替换/新增 application 的 `android:appComponentFactory`。
- 追加项目保留 metadata，并保留所有既有元素、属性、值类型和资源引用语义。
- 变换后重新解析，执行严格白名单 semantic diff。

## Out of Scope

- 修改 package、version、SDK、权限、组件、intent filter、provider、resource 或 `extractNativeLibs`。
- 文本 XML、Apktool/aapt2 重编译作为生产变换路径。
- 重签名、DEX 或 ZIP 写出。

## Implementation Decisions

- Shell factory 类名固定为 `ah.runtime.bootstrap.ShellAppComponentFactory`。
- metadata 键固定为 `ah.runtime.original_application`、`ah.runtime.original_app_component_factory`、`ah.runtime.has_original_app_component_factory`、`ah.runtime.container_asset`、`ah.runtime.container_major`、`ah.runtime.signer_policy_version`、`ah.runtime.risk_policy_version`。
- 原 Application 缺失时记录 `android.app.Application`；原 factory 缺失时不写 string 值，boolean `has_original_app_component_factory=false`，存在时为 `true` 并写规范化全限定名。
- container asset 值固定为 `assets/ah/runtime/payload.ahdc`，三个版本字段均为 binary integer `1`。
- 输入已声明任一保留 metadata、Shell factory 或保留 namespace 时返回 `AXML_RESERVED_COLLISION`，不覆盖。
- string pool 只追加新字符串，保持所有原 index 不变；resource map 保持原项并只为新增 Android 属性追加 SDK 35 的固定 framework resource ID。未知 chunk 按原 bytes/顺序保留。
- 分支名固定为 `feat/m1-03-binary-axml-transformer`，Issue 标题固定为 `[M1-03] Binary AXML transformer`，仅允许一个关联 PR。

## Public Interfaces

- `BinaryManifestTransformer.transform(ByteArray input, ManifestTransformRequest request): ManifestTransformResult`。
- `ManifestTransformRequest` 只接受已验证的 package/application/factory 与固定版本值，不接受任意属性变更。
- `ManifestTransformResult` 包含 `bytes`、`beforeSha256`、`afterSha256`、`semanticDiff`。
- 错误码：`AXML_MALFORMED`、`AXML_LIMIT_EXCEEDED`、`AXML_APPLICATION_MISSING`、`AXML_RESERVED_COLLISION`、`AXML_UNSUPPORTED_ENCODING`、`AXML_DIFF_VIOLATION`。

## Security Constraints

- 所有 chunk/offset/count/UTF length 采用 checked arithmetic 和 M1-01 预算；不得递归处理无界 XML 深度。
- 不执行 manifest 中的类，不解析资源引用指向的数据，不访问网络。
- semantic diff 出现白名单外变化即销毁结果并返回 `AXML_DIFF_VIOLATION`。
- 日志只记录 chunk 类型、offset、错误码和摘要，不输出完整 Manifest 或用户路径。
- 本任务需要独立 parser/security reviewer。

## Compatibility Requirements

- 支持 UTF-8/UTF-16 string pool、未知 chunk、现有 resource map、缩写/相对/全限定类名。
- 保持 package、min/target SDK、Application、组件、权限、intent、provider 和所有未批准属性语义。
- Windows/Ubuntu 对相同输入与 request 产生字节相同输出和相同 diff。
- 变换结果必须可由 API 29/35 framework 与固定 `aapt2` 读取。

## Acceptance Criteria

1. `./gradlew :host:axml:test` 退出码为 `0`。
2. 有/无原 Application、有/无原 factory、UTF-8/UTF-16、未知 chunk 和 resource reference fixtures 均通过 round-trip 及 `aapt2 dump xmltree`。
3. semantic diff 只包含 factory 属性变化和七个规定 metadata 的新增，不含其他 element/attribute/value 差异。
4. 原 string index、未知 chunk bytes/顺序和未变属性 typed value/resource ID 均保持。
5. 任一保留键冲突、截断 chunk、长度溢出、重复 application 或白名单外变化返回对应 `AXML_*` code 且不产生结果。
6. API 29/35 安装解析测试确认 framework 读取到 Shell factory 与正确 typed metadata。
7. Windows 与 Ubuntu 输出 SHA-256 相同；5,000 个 seeded malformed samples 无崩溃或超预算分配。

## Required Tests

- binary AXML reader/writer round-trip 与 typed value 单元测试。
- 类名规范化和七个 metadata 组合测试。
- semantic diff 白名单正反测试。
- malformed chunk、string length、resource map 和 nesting fuzz/property tests。
- `aapt2` 交叉解析及 API 29/35 安装读取测试。

## Required Evidence

- 测试/aapt2/adb 命令、退出码、OS/JDK/Android 工具版本。
- fixture、before/after Manifest、semantic diff 和 corpus manifest 的 SHA-256。
- 原 index/未知 chunk 保留对照和跨平台输出对照。
- 独立 reviewer 结论、提交 SHA、Issue 与唯一 PR 链接。

## Likely Files

- `host/axml/src/main/kotlin/`
- `host/axml/src/test/kotlin/`
- `host/axml/src/test/resources/`
- `docs/evidence/M1-03/`

## Dependencies and Blockers

- M1-01 的 manifest 边界与 M0-05 的元数据契约必须稳定。
- 若 ADR 或 Runtime 使用不同 metadata 名称/类型，先由 `/root` 统一合同。
- 发现必须修改白名单外字段才能启动时任务 blocked，不扩展 transformer 权限。

## Agent Handoff Requirements

- 本任务固定使用分支 `feat/m1-03-binary-axml-transformer`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、平台、before/after/diff SHA-256、fuzz seed 和 reviewer 结论。
- worker 不修改根 `HandOff.md`，不实现 ZIP 写出、容器或 Runtime。
- framework 解析与自有解析不一致时提交最小 synthetic fixture 和 blocked 交接。
