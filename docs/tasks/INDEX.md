# Development Task Index

本目录中的任务卡是技术范围、接口、依赖和验收标准的权威来源。GitHub Issue 只跟踪负责人、状态、讨论和 PR。除 M0-01 空远程种子提交与 M0-02 预先批准的引导分支外，一个任务只能对应一个含任务 ID 的工作分支和一个 PR；后续任务不得复用例外。

状态由协调者在 GitHub Issue 和根 `HandOff.md` 中维护；任务卡中的初始 `status: planned` 不应由工作 Agent自行修改。

## 领取流程

1. 确认所有 `depends_on` 任务已经由协调者验收。
2. 在 GitHub Issue 中确认没有现有负责人。
3. 由协调者分配任务和允许修改的模块。
4. 创建包含任务 ID 的分支。
5. 加载任务卡 `required_skills` 指定的 Skill。
6. 完成后提交标准工作交接包，不修改根 `HandOff.md`。

## M0 Foundation and PoC

| Task | Issue | Task card | Owner role | Depends on |
|---|---|---|---|---|
| M0-01 | [#1](https://github.com/xiaokh31/androidAppHardening/issues/1) | [Repository bootstrap](M0-01-repository-bootstrap.md) | `qa-governance-agent` | None |
| M0-02 | [#2](https://github.com/xiaokh31/androidAppHardening/issues/2) | [Governance, Skills, and HandOff](M0-02-governance-skills-handoff.md) | `qa-governance-agent` | M0-01 |
| M0-03 | [#3](https://github.com/xiaokh31/androidAppHardening/issues/3) | [Toolchain, Gradle, and CI](M0-03-toolchain-gradle-ci.md) | `qa-governance-agent` | M0-02 |
| M0-04 | [#4](https://github.com/xiaokh31/androidAppHardening/issues/4) | [API 29 ClassLoader PoC](M0-04-api29-classloader-poc.md) | `runtime-security-agent` | M0-03 |
| M0-06 | [#30](https://github.com/xiaokh31/androidAppHardening/issues/30) | [Early startup configuration contract](M0-06-early-startup-config-contract.md) | `runtime-security-agent` | M0-04 |
| M0-05 | [#5](https://github.com/xiaokh31/androidAppHardening/issues/5) | [Application, factory, Provider, and JNI PoC](M0-05-application-factory-provider-jni-poc.md) | `runtime-security-agent` | M0-04, M0-06 |

M0 门禁：API 29 公共加载链、原 Application/factory 代理、Provider、multidex 和客户 JNI fixture 全部通过后，才能冻结 Host/Runtime 共享合同。

## M1 Host Processor

| Task | Issue | Task card | Owner role | Depends on |
|---|---|---|---|---|
| M1-01 | [#6](https://github.com/xiaokh31/androidAppHardening/issues/6) | [Untrusted APK inspector](M1-01-untrusted-apk-inspector.md) | `host-pipeline-agent` | M0-05 |
| M1-02 | [#7](https://github.com/xiaokh31/androidAppHardening/issues/7) | [Signer policy](M1-02-signer-policy.md) | `host-pipeline-agent` | M1-01 |
| M1-03 | [#8](https://github.com/xiaokh31/androidAppHardening/issues/8) | [Binary AXML transformer](M1-03-binary-axml-transformer.md) | `host-pipeline-agent` | M1-01, M0-05 |
| M1-07 | [#36](https://github.com/xiaokh31/androidAppHardening/issues/36) | [Chunk-authenticated DEX container contract](M1-07-chunk-authenticated-container-contract.md) | `host-pipeline-agent` | M1-02 |
| M1-04 | [#9](https://github.com/xiaokh31/androidAppHardening/issues/9) | [Encrypted DEX container](M1-04-encrypted-dex-container.md) | `host-pipeline-agent` | M1-01, M1-02, M1-07 |
| M1-05 | [#10](https://github.com/xiaokh31/androidAppHardening/issues/10) | [APK repacker and alignment](M1-05-apk-repacker-and-alignment.md) | `host-pipeline-agent` | M1-02, M1-03, M1-04 |
| M1-06 | [#11](https://github.com/xiaokh31/androidAppHardening/issues/11) | [CLI and JSON report](M1-06-cli-and-json-report.md) | `host-pipeline-agent` | M1-01, M1-02, M1-03, M1-04, M1-05 |

## M2 Android Runtime

| Task | Issue | Task card | Owner role | Depends on |
|---|---|---|---|---|
| M2-07 | [#41](https://github.com/xiaokh31/androidAppHardening/issues/41) | [Native cryptography backend and supply-chain pinning](M2-07-native-crypto-backend.md) | `runtime-security-agent` | M0-03, M1-04 |
| M2-01 | [#12](https://github.com/xiaokh31/androidAppHardening/issues/12) | [Shell AppComponentFactory](M2-01-shell-app-component-factory.md) | `runtime-security-agent` | M0-05, M1-03, M1-04, M2-03 |
| M2-02 | [#13](https://github.com/xiaokh31/androidAppHardening/issues/13) | [Native decrypt and in-memory loader](M2-02-native-decrypt-and-inmemory-loader.md) | `runtime-security-agent` | M0-04, M1-04, M2-07 |
| M2-08 | [#53](https://github.com/xiaokh31/androidAppHardening/issues/53) | [Native parser topology bounds hardening](M2-08-native-parser-topology-bounds.md) | `runtime-security-agent` | M2-02 |
| M2-09 | [#59](https://github.com/xiaokh31/androidAppHardening/issues/59) | [Shell Factory configuration-relaunch lifecycle](M2-09-shell-factory-relaunch-lifecycle.md) | `runtime-security-agent` | M2-01 |
| M2-03 | [#14](https://github.com/xiaokh31/androidAppHardening/issues/14) | [Runtime signer and integrity](M2-03-runtime-signer-and-integrity.md) | `runtime-security-agent` | M1-02, M1-04, M2-02 |
| M2-04 | [#15](https://github.com/xiaokh31/androidAppHardening/issues/15) | [Four-ABI runtime](M2-04-four-abi-runtime.md) | `runtime-security-agent` | M0-03, M1-01, M2-01, M2-02, M2-03 |
| M2-05 | [#16](https://github.com/xiaokh31/androidAppHardening/issues/16) | [Environment risk engine](M2-05-environment-risk-engine.md) | `runtime-security-agent` | M2-01, M2-03, M2-04 |
| M2-06 | [#17](https://github.com/xiaokh31/androidAppHardening/issues/17) | [Memory-dump cost controls](M2-06-memory-dump-cost-controls.md) | `runtime-security-agent` | M2-02, M2-04, M2-05 |

M1-07 的 AHDC v2 合同必须先经独立安全复核并合并；随后 M1 与 M2 只有在 M1-04 容器格式和黄金向量冻结后才能并行。共享格式变化必须新增 ADR，不能在单侧实现中隐式改变或回退 AHDC v1。

## M3 Validation

| Task | Issue | Task card | Owner role | Depends on |
|---|---|---|---|---|
| M3-01 | [#18](https://github.com/xiaokh31/androidAppHardening/issues/18) | [Android fixtures](M3-01-android-fixtures.md) | `qa-governance-agent` | M1-06, M2-04 |
| M3-02 | [#19](https://github.com/xiaokh31/androidAppHardening/issues/19) | [Tamper and fuzz tests](M3-02-tamper-and-fuzz-tests.md) | `qa-governance-agent` | M1-03, M1-04, M1-06, M2-02, M2-03, M2-06, M2-08, M3-01 |
| M3-03 | [#20](https://github.com/xiaokh31/androidAppHardening/issues/20) | [Windows and Ubuntu equivalence](M3-03-windows-ubuntu-equivalence.md) | `qa-governance-agent` | M0-03, M1-05, M1-06, M2-06, M3-01 |
| M3-06 | [#56](https://github.com/xiaokh31/androidAppHardening/issues/56) | [API/ABI validation-claim contract](M3-06-api-abi-validation-claim-contract.md) | `qa-governance-agent` | M0-03, M2-04, M3-01, M3-02 |
| M3-04 | [#21](https://github.com/xiaokh31/androidAppHardening/issues/21) | [API and ABI matrix](M3-04-api-and-abi-matrix.md) | `qa-governance-agent` | M0-03, M2-04, M2-09, M3-01, M3-02, M3-06 |
| M3-05 | [#22](https://github.com/xiaokh31/androidAppHardening/issues/22) | [Size, startup, and memory benchmarks](M3-05-size-startup-memory-benchmarks.md) | `qa-governance-agent` | M1-06, M2-04, M2-06, M3-01 |

## M4 Release

| Task | Issue | Task card | Owner role | Depends on |
|---|---|---|---|---|
| M4-01 | [#23](https://github.com/xiaokh31/androidAppHardening/issues/23) | [Security and supply-chain review](M4-01-security-and-supply-chain-review.md) | `qa-governance-agent` | M3-02, M3-03, M3-04, M3-05 |
| M4-02 | [#24](https://github.com/xiaokh31/androidAppHardening/issues/24) | [Cross-platform release packaging](M4-02-cross-platform-release-packaging.md) | `qa-governance-agent` | M1-06, M2-04, M3-03, M4-01 |
| M4-03 | [#25](https://github.com/xiaokh31/androidAppHardening/issues/25) | [Release evidence and documentation](M4-03-release-evidence-and-documentation.md) | `qa-governance-agent` | M3-03, M3-04, M3-05, M4-01, M4-02 |

## Critical path

```text
M0-01 → M0-02 → M0-03 → M0-04 → M0-06 → M0-05
M0-05 → M1-01 → M1-02 → M1-07 → M1-04
M1-01 → M1-03
M1-04 → M2-07 → M2-02 → M2-03
M1-03 + M1-04 + M2-03 → M2-01 → M2-04 → M2-05 → M2-06
M2-04 + M1-06 → M3-01
M2-02 → M2-08 → M3-02
M2-01 → M2-09 → M3-04
M2-06 + M3-01 + M2-08 → M3-02 → M3-06 → M3-04
M2-06 + M3-01 → M3-03
M2-06 + M3-01 → M3-05
M3-02 + M3-03 + M3-04 + M3-05 → M4-01 → M4-02 → M4-03
```

任何依赖未满足、验收证据不足或安全复核未完成的任务不得沿关键路径标记完成。
