# v0.2 产品需求

## 1. 产品定义

v0.2 是离线 APK 后处理器的首条新发布线。它只读取一个已签名且获授权的独立 APK（standalone APK），输出一个新的未签名 APK 和机器可读报告。使用者在产品外部完成后续签名。

本版本不新增保护能力；目标是把基线实现置于新 tuple 下，补齐 fresh 性能、兼容、安全和发布证据。

## 2. 输入合同

- 输入必须是单个 standalone APK，且在调用前具备可验证 signer。
- 输入要求精确表述为 `minSdk >= 29`。
- 输入路径与输出路径必须不同；输入在成功、失败和取消路径上保持字节不变。
- APK、ZIP、AXML、DEX、签名块、SO、所有偏移与长度均为不可信数据。
- 支持标准 Java/Kotlin、单/多 DEX、自定义 `Application` 和受支持的 `AppComponentFactory`。
- 不支持 AAB、APKS、split、dynamic feature、Flutter、Unity、React Native、热修复、插件框架和已有保护壳。

## 3. 输出合同

- 成功只产生一个新的 APK 和一个 JSON 报告。
- APK 必须未签名；产品不得保存或恢复输入签名块。
- 输出包含壳启动 DEX、AHDC v2 加密 payload、启动配置和与输入 ABI 策略相符的 Runtime SO。
- 失败不得留下可被误认为成功的最终输出，临时文件必须清理。
- 输出必须能够由使用者在产品外部正常签名；测试签名能力不进入产品。

## 4. 功能要求

| ID | 要求 |
| --- | --- |
| V2-FR-001 | 只读检查不可信 APK，并稳定拒绝超界、损坏和不支持输入 |
| V2-FR-002 | 捕获当前 signer 与 lineage，供 Runtime 独立复核 |
| V2-FR-003 | 仅修改授权的 Binary AXML 启动属性并保持其他字节语义 |
| V2-FR-004 | 使用 AHDC v2 分块认证加密 DEX，任何认证失败均不发布 payload |
| V2-FR-005 | 通过公开 API 29+ ClassLoader/Factory 路径恢复应用启动 |
| V2-FR-006 | 在 payload lookup 前完成 package、signer、lineage、container 和 shell 完整性门禁 |
| V2-FR-007 | 构建四 ABI Runtime，并对输入 Native ABI 给出明确兼容结果 |
| V2-FR-008 | 环境风险只影响成本防御策略，不得降级 signer、AEAD 或完整性失败 |
| V2-FR-009 | 对 DEX 映射使用清零、unmap、`DONTDUMP`、可用时锁页和有界抖动等成本控制 |
| V2-FR-010 | 输出稳定错误码与 JSON 报告，不泄漏密钥、绝对用户路径或明文 DEX |

## 5. 非功能要求

### 5.1 安全

- 禁止自制密码算法、动态依赖版本、未锁定下载和未审计远程脚本。
- 产品不得提供 APK 签名能力或请求任何签名秘密。
- DEX 防截取、环境检测和离线密钥隐藏只能提高攻击成本，不能绝对防御高权限攻击者。

### 5.2 兼容

- Runtime 构建 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。
- 四 ABI 构建不等于每个 API/ABI 设备格子已验证。
- x86/x86_64 本身不是风险信号。
- ARM-only 输入不能被宣传为可在 x86-only 设备运行。

### 5.3 性能与大小

- 未签名输出增量不得超过 `max(12 MiB, inputSize × 15%)`。
- 两个启动终点 delta P50 不超过 300 ms，P95 不超过 500 ms。
- peak PSS、Native heap peak、stable PSS 使用 `TEST_STRATEGY.md` 固定预算。
- “大小优化”仅表示控制增量，不保证输出小于输入。

### 5.4 可复现与可审计

- 所有 release evidence 必须绑定同一 product tuple、commit、toolchain 和 artifact manifest。
- Tuple 中七个 manifest hash 必须绑定 `IDENTITY_MANIFESTS.md` 的唯一 tracked preimage；release output 与 PASS validator 在候选前冻结。
- Windows/Ubuntu 发布包在相同输入下必须可重现。
- 完成状态必须提供命令、退出码、环境、时间、commit 和 SHA-256。

## 6. 发布要求

- v0.2 不接受任何 v0.1 历史结果作为 PASS 替代。
- canonical 性能 run 对 `v0.2.0-rc.1` 只允许一个 `runAttempt=1`；失败阻塞该候选。
- exact-tuple 回归必须重新覆盖九 fixture、篡改/fuzz、跨平台和四个强制 API/ABI 格子。
- 安全审查必须由非实现者完成，未解决 Critical/High 为零。
- SBOM/漏洞工具、数据库快照、schema 和 UNKNOWN severity 处理遵守 `SUPPLY_CHAIN_TOOLCHAIN.md`，任何不可复算状态失败关闭。
- 发布包只面向 Windows x86_64 和 Ubuntu x86_64 Host，包含四 ABI Android Runtime，不包含签名能力或测试材料。
