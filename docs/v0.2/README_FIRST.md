# v0.2 开发前必读

v0.2 是在已终结 v0.1 之后建立的独立发布线。它继承 `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523` 的 Host、Runtime、fixture 和验证源码，但不继承 v0.1 的性能或发布通过状态。

## 阅读顺序

1. 仓库根 `AGENTS.md`。
2. 仓库根 `HandOff.md`。
3. 本文件。
4. `docs/v0.2/PRODUCT_BASELINE.md`。
5. 当前 `docs/v0.2/tasks/<task>.md`。
6. 任务引用的 `docs/v0.2/` 架构、威胁模型、测试策略与证据复用策略。
7. `docs/adr/0020-v0-2-product-baseline-and-version-isolation.md` 及任务指定的既有 ADR。
8. 任务 `required_skills` 指定的项目 Skill。

## 不可跨越的版本边界

- v0.1 M3-05 保持终态阻塞，旧诊断不得重试、替换、续期、改名或换平台。
- v0.1 M4-01 至 M4-03 保持不可启动。
- PR #63 和 PR #83 保持关闭且未合并；不得 cherry-pick、复制 workflow 或将其 artifact/run 作为 v0.2 PASS。
- V2 任务只依赖其他 V2 任务。旧 `main` 通过 `baseline_inputs` 引用，不伪造成已完成的新依赖。
- v0.2 的每项正向发布声明都必须引用 fresh candidate tuple 和本发布线新生成的 evidence manifest。

## 产品边界

- 只处理单个 standalone APK。
- 输入 APK 字节只读，输出路径必须不同且输出未签名。
- 产品不接收私钥、keystore、alias 或密码，不调用 APK 签名能力。
- 输入 `minSdk >= 29`。
- 标准 Java/Kotlin、单/多 DEX、自定义 `Application/AppComponentFactory` 保持目标范围。
- Runtime 构建四 ABI；ARM-only 客户应用不会因此获得 x86 兼容性。
- 不支持 AAB、APKS、split、dynamic feature、Flutter、Unity、React Native、热修复、插件框架和已有加固壳。
- 动态保护只能提高攻击成本；大小目标只控制加固增量。

## 工作方式

一个任务对应一个 Issue、一个包含任务 ID 的分支和一个 PR。工作 Agent 一次只领取一张任务卡，不修改根 `HandOff.md`，也不在验证任务内修复产品代码。任何候选冻结后的产品变化都会使当前 tuple 失效，必须由协调者建立新候选，而不是覆盖旧证据。

所有安全敏感任务在合并前需要独立只读复核、固定 commit、本地门禁、Ubuntu/Windows CI 和结构化交接。设备、证书、APK 和报告只进入忽略的构建目录或正式 evidence manifest 允许的脱敏文本；不提交生成 APK、DEX、keystore 或私钥。

## 导航

- [产品基线](PRODUCT_BASELINE.md)
- [产品需求](PRODUCT_REQUIREMENTS.md)
- [架构](ARCHITECTURE.md)
- [威胁模型](THREAT_MODEL.md)
- [证据复用策略](EVIDENCE_REUSE_POLICY.md)
- [身份 Manifest 合同](IDENTITY_MANIFESTS.md)
- [SBOM 与漏洞扫描工具合同](SUPPLY_CHAIN_TOOLCHAIN.md)
- [测试策略](TEST_STRATEGY.md)
- [路线图](ROADMAP.md)
- [兼容性矩阵](COMPATIBILITY_MATRIX.md)
- [开发产品 tuple](development-product-tuple.json)
- [任务索引](tasks/INDEX.md)
