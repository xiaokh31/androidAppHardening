# v0.2 测试策略

## 1. 目标

证明基线实现作为 `v0.2.0-rc.1` 时仍满足输入只读、未签名输出、DEX 认证加密、防二次打包成本、运行时 signer/完整性、四 ABI、环境风险和内存暴露成本控制，并形成新的性能与发布证据。

## 2. 测试层级

### 2.1 治理与 tuple

- V2 task ID、Issue、依赖和索引唯一性。
- v0.1 terminal baseline 祖先和历史字节复算。
- development tuple 的递归字典序规范 JSON 与 SHA-256。
- release-candidate tuple 字段、顺序、manifest 和变异负例。
- 旧任务、PR、run、artifact 或 predecessor tuple 作为 PASS 输入时必须失败。

### 2.2 Host 单元与集成

- APK/ZIP/AXML/DEX/signer 的长度、偏移、重复项和资源上界。
- AHDC v2 chunk/record authentication、nonce、压缩边界、尾随数据和清理。
- repacker 的输入只读、签名材料移除、alignment、atomic publish 和失败清理。
- CLI 参数、稳定错误码、`--version 0.2.0` 与 REPORT_V1 schema。
- Windows/Ubuntu 九 fixture full-flow 语义等价；随机密码学字段不得错误复用。

### 2.3 Runtime 与篡改

- package/current signer/lineage、ConfigV2、SPV1、shell DEX/SO 和 payload 完整性。
- Manifest、container header/table/chunk、SO、shell DEX、不同 signer 和错误 package 负例。
- payload lookup 前失败、`sessionPublished=false`、Native/Java 敏感缓冲清理。
- 环境信号的 `ALLOW/DEGRADE/BLOCK` 策略，但 signer/AEAD/integrity 失败不可降级。
- x86/x86_64 本身风险贡献为零。

### 2.4 Fuzz 与资源耗尽

- 既有 69 项 tamper catalog 重新绑定 exact candidate。
- Jazzer APK/AXML target 和 Native libFuzzer + ASan/UBSan。
- corpus、seed、toolchain、时长、crash/timeout/OOM 和 regression artifact manifest 完整记录。
- 任何新 crash 必须先修复并建立新 candidate，不能在 V2-M3-04 内忽略。

### 2.5 API/ABI 矩阵

完整清单为 API 29–36 × 四 ABI，共 32 格。fresh v0.2 强制格子为：

- API 29 `armeabi-v7a`，授权非 root physical user process。
- API 29 `arm64-v8a`，同一授权设备的 64-bit process。
- API 29 `x86_64`，固定 KVM environment。
- API 36 `x86_64`，固定 KVM environment。

每个强制格子运行适用的九 fixture、组件事件、不同 signer、authenticated-tag tamper 和 cleanup。其余格子保持 `UNVERIFIED`，除非任务开始前已固定新的授权环境和同一合同；不得从端点外推。

## 3. 性能协议

### 3.1 Fixture 与签名

固定 `java-single-dex`、`kotlin-multidex`、`jni-four-abi`。每轮从 source freeze 生成可重复 unsigned fixture，再在产品外生成一次性证书：签 input、运行产品、确认 input hash 不变、确认 product output 未签名、用同证书签测试副本。结束后删除证书和 signed copies。

### 3.2 Host

- Windows x86_64 和 Ubuntu x86_64。
- 每场景预热 3 次、保留 10 次。
- 记录 `hostProcessMs`、`hostPeakRssBytes`、输入/未签名输出/外部签名输出大小及分项。
- Frozen validator 直接解析 input/output APK，生成覆盖 `0..fileSize`、无 gap/overlap 的 entry/structure byte intervals；每段 offset、length、SHA-256、role 和 category 从 bytes 复算，不能信任报告摘要。
- 实际单 ABI 未签名输出严格满足 `outputUnsignedApkBytes = inputApkBytes + bootstrapDexEntryDataDeltaBytes + runtimeEntryDataDeltaBytes + manifestAndCopiedEntryDataDeltaBytes + encryptedContainerMetadataBytes + encryptedPayloadBytes + removedOriginalDexDataDeltaBytes + removedSignatureDataDeltaBytes + signingBlockDeltaBytes + zipStructureDeltaBytes`。原 DEX delta 必须小于 0；输入存在 v1 `META-INF` signature entries 时 `removedSignatureDataDeltaBytes < 0`，否则等于 0；输入存在 v2/v3 APK Signing Block 时 `signingBlockDeltaBytes < 0`，否则等于 0。输出两类签名材料都必须为 0，输入则必须已通过支持 v1/v2/v3 的 signer policy；ZIP structure 是解析 interval 的独立差值而不是 remainder；`fourAbiRuntimeBaselineBytes` 单独报告且不得计入实际输出。
- 除三个 fixture 外必须运行一个独立 100 MiB 合成输入 case，预热 3 次并保留 10 次；median `hostProcessMs <= 60000`、peak `hostPeakRssBytes <= 1073741824`，输入 SHA-256 前后必须相同。
- Windows runner 必须先以 suspended root process 创建 Job Object、禁用 breakaway、把 root 分配后再 resume，并用 completion-port 生命周期枚举整个 owned process tree；每 `<=50 ms` 读取所有 active PID 的 `GetProcessMemoryInfo.WorkingSetSize`，求同一采样点 aggregate bytes 的最大值作为 `hostPeakRssBytes`。报告固定 `source=win32-job-getprocessmemoryinfo-v1`、Job identity、PID/create-time、window start/end、每个 raw sample 和最大间隔；只测父进程、PID 重用、尾窗缺样、间隔 `>75 ms`、单位非 bytes 或 Job active-process count 未归零均失败。
- Ubuntu runner 必须把 root 及全部子进程放入唯一 delegated cgroup v2，禁止迁出，并从 `memory.peak` 读取整个 cgroup 的 kernel high-water bytes；同时每 `<=50 ms` 记录 `memory.current`，最大间隔不得超过 `75 ms`。报告固定 `source=cgroup-v2-memory.peak-v1`、mount/cgroup identity、window、raw samples 与 final `memory.events`；cgroup v2/delegation/`memory.peak` 不可用、单位/identity漂移、尾窗缺失或退出后仍有 process 都是 `BLOCKED`，不得降级成 `/proc` 稀疏父进程采样。

### 3.3 Android

- API 36 x86_64 与 API 29 arm64 reference environment。
- 同一 source/job/boot 内恰好 A/B 两个互补 campaign；不得有第三 campaign。
- 每 mode 预热 5 次、保留 30 次；任何无效样本使 candidate gate 失败。
- 记录 `processToApplicationOnCreateMs`、`processToInteractiveMs`、`peakPssBytes`、`nativeHeapPeakBytes`、`stablePssBytes` 和 `highProfileIncrementalMs`。
- instrumentation 和 runner 使用同一 boot-time monotonic clock；每个样本前 force-stop。
- Android 内存唯一来源是 pinned platform `dumpsys meminfo --checkin <pid>` parser。采样从目标 PID 的 process-start marker 开始，到 interactive marker 后最后一次有效 sample 结束，周期 `<=500 ms`、相邻有效 sample 最大间隔 `<=750 ms`，且每个 retained run 至少 6 个 peak-window samples；记录 API/build fingerprint、parser version、boot ID、PID、`/proc/<pid>/stat` starttime、每条原始输出 hash、sample monotonic time 和 bytes换算。`peakPssBytes` 取 TOTAL PSS 最大值，`nativeHeapPeakBytes` 取 Native Heap PSS 最大值。
- `stablePssBytes` 使用 interactive 后 3 秒开始的 5 个有效 TOTAL PSS samples，固定 1 秒间隔、最大 gap 1250 ms并取 median。错 PID/boot/starttime、sample 落在窗口外、只在进程结束时取一次、解析/单位错误、样本不足、缺 start/interactive/tail event 或任何 gap 超限均使该 retained run 和 candidate `BLOCKED`，不得插值、补样或使用 `am start -W` 摘要代替 raw source。

### 3.4 固定预算

| Metric | P50 delta | P95 delta |
| --- | ---: | ---: |
| 两个启动终点 | 300 ms | 500 ms |
| peak PSS | 48 MiB | 64 MiB |
| Native heap peak | 24 MiB | 32 MiB |
| stable PSS | 32 MiB | 48 MiB |

未签名 APK 增量不得超过 `max(12 MiB, input × 15%)`。三 fixture × 五 observed Android metric × 六类统计共 90 行的 A/B 对应差异统一使用 `abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10`；不得使用较大值、平均值或聚合结果作为分母。isolated HIGH 的 Native jitter 每样本保持 20–50 ms，wall time 不超过 250 ms；它单独报告，不冒充真实 HIGH 冷启动。

## 4. Canonical run 策略

在 V2-M3-02 freeze 前，非产品 canary 可验证 SDK、emulator、KVM、依赖、fixture build 和报告 plumbing，但不得产生 v0.2 product measurement。freeze 后 canonical workflow 的唯一事件是无 `inputs` 的 `workflow_dispatch`，只接受 `refs/heads/main`，禁止 `workflow_call` 与 caller/external artifact，固定 `concurrency.group=v02-performance-v0.2.0-rc.1` 且 `cancel-in-progress=false`。workflow 必须先验证 tuple、HEAD、workflow、toolchain 和 manifest，再执行一个 `runAttempt=1`。

V2-M3-02 不 dispatch。V2-M3-03 开始时，只有 `/root` 可执行一次 `gh workflow run v02-performance.yml --ref main`；工作 Agent 和外部 caller 均不得触发第二次或携带参数。

失败不得 rerun、补样、重命名或换平台。产品修复需要新任务和新 candidate tuple。

## 5. 发布验证

- 安全复核：只执行候选前冻结的 dependency lock/verification、CycloneDX SBOM、许可证、离线漏洞、秘密和产品签名能力 gate；工具与数据库遵守 `SUPPLY_CHAIN_TOOLCHAIN.md`。
- 打包：只执行 V2-M0-02 冻结且已由 V2-M4-01 审查的 packager/launcher/Quickstart bytes，验证固定 entry 顺序、时间、权限、owner/group、压缩参数和重复构建哈希。
- smoke：预安装 Eclipse Temurin `17.0.19+10`、断网、包外 signed fixture、输入只读和未签名输出。
- 文档：链接、命令、版本、兼容矩阵、性能预算、残余风险、evidence hash 全部可复算。

## 6. Evidence 格式

每个报告包含 task、tuple、commit、toolchain、environment、raw samples、判定、artifact SHA-256、命令、退出码、时间戳和 cleanup。报告 schema 拒绝缺字段、额外枚举、非有限数值、错误样本数、自报摘要替代原始值以及绝对用户路径。
