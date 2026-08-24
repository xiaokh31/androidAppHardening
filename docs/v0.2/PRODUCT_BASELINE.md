# v0.2 产品基线

## 1. 基线身份

| 字段 | 固定值 |
| --- | --- |
| Repository | `xiaokh31/androidAppHardening` |
| Baseline ref | `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523` |
| Git tree | `1f8a4c387b4715443df31457fa498c7463b1f23f` |
| Release line | `v0.2` |
| Planned product version | `0.2.0` |
| First candidate | `v0.2.0-rc.1` |
| Previous disposition | `STOP_CURRENT_V0_1_RELEASE_LINE` |

该 commit 是 v0.2 的源码输入，不是发布候选，也不代表 v0.1 已发布。`docs/v0.2/development-product-tuple.json` 记录同一事实。

Development tuple identity is `tuple_kind=development_baseline`, `releasable=false`, with canonical SHA-256 `5fb0205d9fc0c2523cd33734145bf23a901303f4f563eef866e5883ca81fd4c2`. This identity cannot be used as a release-candidate PASS token.

## 2. 可继承的实现

v0.2 可在 fresh evidence 下使用基线中已经合并的以下实现：

- Host APK inspector、signer policy、Binary AXML 转换、AHDC v2 容器、repacker、alignment、CLI 与 `REPORT_V1`。
- Runtime 的 `AppComponentFactory` 启动、Native 认证解密、内存 DEX loader、运行时 signer/完整性门禁、四 ABI、环境风险引擎和内存暴露成本控制。
- 九个合成 fixture 的源码、篡改 catalog、Jazzer/native fuzz 工具、跨平台等价性工具和 API/ABI 设备探针。
- 固定工具链、依赖锁、verification metadata、双平台 Build/Governance 和既有项目 Skills。

继承源码不等于继承发布证据。v0.2 必须在候选冻结后重建并验证 Host/Runtime 产物、fixture、性能、兼容、安全审查和发布包。

## 3. 不可继承的通过状态

以下内容只能用于理解风险或设计 fresh 测试，不能满足任何 v0.2 Acceptance Criteria：

- M3-05 的报告、分支、PR #63、Issue #22、APK pair、campaign 或性能数值。
- M3-10、M3-14、PR #79、PR #83、Issue #82 的执行身份、workflow、runner、profile package、run、job、artifact 或失败边界。
- v0.1 M4-01 至 M4-03 的任务卡或未实现输出。
- 任何声称旧 M3/M4 已为 v0.2 提供 PASS 的摘要。

## 4. 版本隔离

- V2 task ID 固定为 `V2-M0-*`、`V2-M3-*`、`V2-M4-*`。
- V2 `depends_on` 只能引用 V2 ID。
- 旧 main 只出现在 `baseline_inputs` 和 tuple 基线字段中。
- v0.2 文档、任务卡与证据使用 `docs/v0.2/` 命名空间。
- v0.1 终态 commit 和锁定字节保留在 Git 历史中；当前文档也必须继续声明旧任务不可恢复。

## 5. 候选冻结

V2-M0-02 固定产品实现、版本身份、distribution packager、launcher、archive schema 与 archive-internal Quickstart。V2-M3-01 固定 fresh performance/release-validation Harness、security/SBOM gate、packaging/evidence validator 和全部非执行 workflow candidate。V2-M3-02 在二者均经独立复核后，从 `IDENTITY_MANIFESTS.md` 指定的七个 tracked preimage 生成 `v0.2.0-rc.1` product tuple。

根 `HandOff.md` 的 tuple 字段按锁状态切换：RC lock 不存在时只能等于本页 development tuple；RC lock 经 V2-M3-01 已冻结的 exact verifier验证并提交后，必须等于 lock 的 exact `productTupleSha256`。V2-M3-02 必须在同一 PR 内原子提交两份 freeze lock、RC lock、根 HandOff 与 live workflow；不得修改 validator，不允许任意第三 tuple 或“先切字段、后补 lock”。

冻结后：

- Host、Runtime、distribution、fixture source、validation/security/package/evidence tooling、工具链或合同任一字节变化都使候选失效。
- 测试生成的随机证书和签名副本不改变产品 tuple，但必须记录在各 run artifact manifest 中。
- 失败候选不得覆盖。需要产品修复时必须获得新任务、candidate ID 和 tuple。

## 6. 发布必要条件

只有 V2-M3-03、V2-M3-04、V2-M4-01、V2-M4-02 和 V2-M4-03 对同一 tuple 顺序通过，才可形成 v0.2 发布就绪结论。任何缺失、哈希不一致、环境替代、产品变化或未解决安全发现都保持 `BLOCKED`。
