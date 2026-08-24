# v0.2 路线图

## 路线原则

v0.2 是独立发布线，不恢复 v0.1 M3-05 或 M4。旧路线决定 `STOP_CURRENT_V0_1_RELEASE_LINE` 保持有效。所有任务只依赖其他 V2 任务；基线源码通过 `baseline_inputs` 固定到 `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。

## 任务

| ID | Issue | 目标 | Depends on |
| --- | --- | --- | --- |
| V2-M0-01 | #86 | 产品基线、版本隔离、ADR 和任务图 | None |
| V2-M0-02 | #87 | 版本、产品、distribution/launcher 与 component baseline freeze | V2-M0-01 |
| V2-M3-01 | #88 | fresh 性能、release-validation、安全/打包/evidence gate 与 workflow candidate freeze | V2-M0-01 |
| V2-M3-02 | #89 | `v0.2.0-rc.1` product tuple 与 canonical workflow freeze | V2-M0-02, V2-M3-01 |
| V2-M3-03 | #90 | exact-tuple 大小、启动和内存门禁 | V2-M3-02 |
| V2-M3-04 | #91 | exact-tuple full-flow、fuzz、跨平台和 API/ABI 回归 | V2-M3-03 |
| V2-M4-01 | #92 | 独立安全、许可证、SBOM 与供应链门禁 | V2-M3-03, V2-M3-04 |
| V2-M4-02 | #93 | Windows/Ubuntu 可重现发布包 | V2-M4-01 |
| V2-M4-03 | #94 | 发布证据、用户文档和最终决定 | V2-M4-02 |

## 并行与所有权

V2-M0-02 与 V2-M3-01 可在 V2-M0-01 合并后并行开发：前者拥有版本、产品、distribution/launcher、archive-internal Quickstart 和候选 component baseline；后者拥有 performance/release-validation、安全/SBOM、packaging/evidence validator 和 workflow candidate。二者不得修改对方文件。

并行开发不代表可任意排序合并，固定顺序如下：

1. V2-M0-02 必须先更新到最新 `main`、通过独立复核并以 `MERGE_COMMIT` 合并；随后唯一成功的 `main` official Governance `runAttempt=1` exact `head_sha` 才是 `implementationFreezeSha`。PR head、merge commit、run ID/head/conclusion、三个 manifest hashes与changed paths必须进入后续 tracked freeze-acceptance lock。
2. V2-M3-01 随后必须更新到包含该 `implementationFreezeSha` 的最新 `main`，重新运行全部 changed-path、production-exclusion、canary、独立复核和 CI，再以 `MERGE_COMMIT` 合并；随后唯一成功的 `main` official Governance `runAttempt=1` exact `head_sha` 才是 `validationFreezeSha`，并以相同规则持久化。
3. V2-M3-02 只有在确认 `implementationFreezeSha` 是 `validationFreezeSha` 的祖先、两者之间只含 V2-M3-01 授权 validation paths 后才能开始。

V2-M3-01 必须在 candidate前冻结 freeze/tuple/post-freeze verifier、package/HandOff candidate adapters、五个 workflow candidate与所有 run/artifact lock schema。V2-M3-02 只把已经发生的 PR/merge/run身份写入两份 freeze lock、生成 tuple lock和报告、原子更新 HandOff并逐字发布五个 workflow；不得新增或修改 gate代码。

违反上述 merge order 时不得改写 ancestry 合同；相关分支必须重新建立可验证的 freeze，或由 `/root` 提交阻塞交接。

V2-M3-02 至 V2-M4-03 的每个新 `main` HEAD还必须通过 `docs/v0.2/post-freeze-path-policy-v1.json`：completed task是固定顺序的连续前缀、当前 changed paths属于该阶段 exact allowlist、先前阶段 canonical evidence不再变化、七 manifest blobs与五组 candidate/live workflow bytes保持冻结。任一未知路径、阶段跳跃或 build-only/external证据使后继不可启动。

其余任务顺序执行，避免在性能 candidate 已失败时继续消耗设备、安全审查和打包资源。

## 退出门禁

### V2-M0

- ADR 0020 与九卡 DAG 通过独立语义审查。
- development tuple 可规范重算。
- 产品版本 `0.2.0`、实现 manifest、工具链和合同 freeze 完成。
- v0.1 terminal history 仍可复算，当前文本不包含任何恢复授权。

### V2-M3

- fresh Harness 与全部 release gate tooling 不含生产 override、旧 artifact 依赖或候选后新增 validator。
- candidate tuple 唯一且所有 manifests 可重算。
- canonical performance run PASS。
- 九 fixture、tamper/fuzz、Windows/Ubuntu 和四个强制 API/ABI 格子以同 tuple fresh PASS。

### V2-M4

- 独立安全决定为 PASS，Critical/High 为零。
- Windows/Ubuntu archive 两次构建哈希一致，离线 smoke PASS。
- release evidence 闭包、文档与 hash 索引一致。
- `/root` 完成 exact-head、CI、独立审查、干净工作区和最终授权核验。

## Critical path

```text
V2-M0-01
  -> (V2-M0-02 + V2-M3-01 parallel development)
  -> V2-M0-02 merge/post-merge PASS
  -> V2-M3-01 refresh on that main, reverify, merge/post-merge PASS
  -> V2-M3-02
  -> V2-M3-03
  -> V2-M3-04
  -> V2-M4-01
  -> V2-M4-02
  -> V2-M4-03
```

## 阻塞策略

- V2-M3-03 失败终结 `v0.2.0-rc.1`，不 rerun 同 tuple。
- 产品修复必须新任务、新 candidate ID、新 tuple，不覆盖旧 evidence。
- V2-M3-04 任一 mandatory cell `FAILED` 或缺环境时保持 blocked，不用其他格子替代。
- 安全门禁、SBOM 或 archive reproducibility 失败时不得进入下一任务。
