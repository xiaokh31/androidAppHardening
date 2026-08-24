# v0.2 Development Task Index

v0.2 是与已终结 v0.1 隔离的新发布线。状态由 GitHub Issue 和根 `HandOff.md` 管理；工作 Agent 不直接修改任务卡的 `status: planned`。

## 领取规则

1. 一个任务对应一个 Issue、一个含完整 V2 task ID 的分支和一个 PR。
2. `depends_on` 中所有任务必须已经合并、完成 post-merge 门禁并由 `/root` 标记完成。
3. `baseline_inputs` 是不可变源码输入，不等价于依赖已通过新版本门禁。
4. 任何产品路径在 V2-M3-02 freeze 后变化都会使 candidate tuple 失效。
5. 旧 M3-05、M3-10、M3-14、M4、PR #63 和 PR #83 永远不能成为 v0.2 PASS 输入。

## V2-M0 Baseline

| ID | Issue | Task | Owner | Depends on |
| --- | --- | --- | --- | --- |
| V2-M0-01 | [#86](https://github.com/xiaokh31/androidAppHardening/issues/86) | [产品基线与版本隔离](V2-M0-01-product-baseline-version-isolation.md) | `/root` | None |
| V2-M0-02 | [#87](https://github.com/xiaokh31/androidAppHardening/issues/87) | [版本化候选基线](V2-M0-02-versioned-candidate-baseline.md) | `host-pipeline-agent` | `V2-M0-01` |

## V2-M3 Validation

| ID | Issue | Task | Owner | Depends on |
| --- | --- | --- | --- | --- |
| V2-M3-01 | [#88](https://github.com/xiaokh31/androidAppHardening/issues/88) | [Fresh 性能 Harness](V2-M3-01-fresh-performance-harness.md) | `qa-governance-agent` | `V2-M0-01` |
| V2-M3-02 | [#89](https://github.com/xiaokh31/androidAppHardening/issues/89) | [Product tuple 冻结](V2-M3-02-product-tuple-freeze.md) | `/root` | `V2-M0-02`, `V2-M3-01` |
| V2-M3-03 | [#90](https://github.com/xiaokh31/androidAppHardening/issues/90) | [大小、启动与内存门禁](V2-M3-03-size-startup-memory-gate.md) | `qa-governance-agent` | `V2-M3-02` |
| V2-M3-04 | [#91](https://github.com/xiaokh31/androidAppHardening/issues/91) | [Exact-tuple 发布验证](V2-M3-04-exact-tuple-release-validation.md) | `qa-governance-agent` | `V2-M3-03` |

## V2-M4 Release

| ID | Issue | Task | Owner | Depends on |
| --- | --- | --- | --- | --- |
| V2-M4-01 | [#92](https://github.com/xiaokh31/androidAppHardening/issues/92) | [安全与供应链复核](V2-M4-01-security-supply-chain-review.md) | `security-review-agent` | `V2-M3-03`, `V2-M3-04` |
| V2-M4-02 | [#93](https://github.com/xiaokh31/androidAppHardening/issues/93) | [可重现发布打包](V2-M4-02-reproducible-release-packaging.md) | `host-pipeline-agent` | `V2-M4-01` |
| V2-M4-03 | [#94](https://github.com/xiaokh31/androidAppHardening/issues/94) | [发布证据与最终决定](V2-M4-03-release-evidence-decision.md) | `qa-governance-agent` | `V2-M4-02` |

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

## Freeze merge order

V2-M0-02 与 V2-M3-01 只允许并行开发，不允许任意排序合并。两者都固定使用 `MERGE_COMMIT`；各自唯一 freeze SHA 是合并后 `main` official Governance `runAttempt=1` 的 exact `head_sha`，并由 tracked freeze-acceptance lock绑定 PR head、merge commit、run身份、manifest hashes与changed paths。V2-M0-02 必须先完成该流程；V2-M3-01 随后更新到包含 `implementationFreezeSha` 的 `main`，重跑全部门禁和独立复核后再完成相同流程。V2-M3-02 必须验证 `implementationFreezeSha` 是 `validationFreezeSha` 的祖先，且两者之间没有产品路径变化。

Candidate之后每个任务只可写 `docs/v0.2/post-freeze-path-policy-v1.json` 中本阶段 exact paths；frozen verifier在每个 current HEAD验证阶段连续、先前 evidence不可变、七 manifest blobs与五组 candidate/live workflow相等。未知路径、build-only证据或后冻结 gate修改立即阻塞。

## Candidate failure rule

`v0.2.0-rc.1` 的 V2-M3-03 canonical run 或任一后继 release gate 失败时，相关任务保持 `blocked`。不得重跑同 tuple 选择新结果。产品修复、新 candidate ID 和新 task graph amendment 必须由 `/root` 与用户另行授权。
