# v0.2 证据复用策略

## 1. 原则

v0.2 可以继承已合并源码和测试基础设施，但所有发布结论必须由新 release line、fresh candidate tuple 和 fresh evidence 产生。复用的对象与复用的结论必须分开登记。

固定规则是：源码/测试资产可复用，发布 PASS 不可复用。任何 predecessor 报告、run 或任务状态都不得成为 v0.2 PASS。

## 2. 可直接作为开发输入的内容

以下内容可作为 `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523` 的源码输入：

- Host、Runtime、fixture、integration-test 和 validation-tool 源码。
- AHDC v2、ConfigV2、SPV1、REPORT_V1 等已接受合同。
- 固定工具链、Gradle lockfile、dependency verification metadata 和来源记录。
- 已合并的 fuzz corpus、tamper catalog、兼容矩阵 schema 和设备 runner。
- 既有 ADR 中仍适用于未变产品边界的安全与兼容决策。

这些输入必须进入 development tuple 或 candidate manifests。继承不代表无需重新构建或测试。

## 3. 只能用于设计和风险分析的历史证据

- 已合并 M0-M3 的测试报告可帮助确定 V2 regression coverage。
- v0.1 性能不稳定和诊断失败可解释为何 V2 使用 prequalified、fresh、tuple-bound 流程。
- 旧安全复核发现可加入 V2 review checklist。
- 旧 API/ABI 格子可说明哪些环境曾经可用，但不得自动成为 v0.2 `VERIFIED`。

引用此类证据时必须标记 `historical_only`，不得赋予 `pass=true`、`release_gate=true` 或等价语义。

## 4. 禁止作为 v0.2 PASS 的内容

以下内容不得满足任何 V2 Acceptance Criteria：

- v0.1 M3-05、M3-10、M3-14 或 M4 的任务状态。
- PR #63、PR #79、PR #83 的分支字节、提交、workflow、run、job、artifact、profile、APK 或报告。
- predecessor product tuple `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`。
- 对旧失败进行重跑、rerun、改名、镜像、平台替换或复制后得到的结果。
- 不同 source commit、toolchain、fixture source、candidate ID 或 product tuple 的报告。
- 人工编辑的摘要布尔值、没有原始样本的统计或无法直接复算的哈希清单。

## 5. Fresh evidence 最低要求

每份 V2 release evidence 必须包含：

- `releaseLine=v0.2`、task ID、candidate ID 和 `productTupleSha256`。
- exact source commit、toolchain manifest、runner/workflow identity 和 environment identity。
- 输入、输出、报告和 artifact manifest SHA-256。
- 原始命令、退出码、UTC/带时区时间戳和执行平台。
- 测试签名材料的生成范围、同证书关系和清理结果。
- 适用的独立复核结论及 frozen commit。

缺少任一强制字段时保持 `BLOCKED`，不得回填一个来自历史任务的值。

## 6. 候选失败与后继

`v0.2.0-rc.1` 的 canonical 性能 run 仅允许 `runAttempt=1`。失败、取消、超时、缺 artifact、cleanup 失败或任何无效样本都阻塞该候选。不补样，不运行第三 campaign，也不提交“更稳定”的替代报告。

如果 fresh evidence 证明必须修改产品，协调者需要新增限定修复任务和新的 candidate ID。新 tuple 保留旧 candidate 的失败记录，不覆盖、不删除、不把新结果描述为旧结果的 retry。

## 7. 发布证据闭包

V2-M4-03 只接受以下同 tuple 闭包：

```text
V2-M3-03 performance PASS
+ V2-M3-04 exact-tuple validation PASS
+ V2-M4-01 security/SBOM PASS
+ V2-M4-02 reproducible package PASS
= v0.2 release evidence candidate
```

任何一项的 commit、tuple、manifest 或 artifact 不一致都会破坏闭包并阻塞发布。
