# v0.2 架构

## 1. 架构原则

v0.2 不改变既有数据面，而是在其外增加独立的版本、候选和证据控制面。产品仍是离线 Host 后处理器加嵌入 APK 的 Android Runtime。

## 2. 产品数据面

```text
signed standalone input APK (read-only)
  -> untrusted APK inspector
  -> signer policy
  -> Binary AXML transformer
  -> AHDC v2 DEX container builder
  -> Runtime assembler
  -> atomic APK repacker and verifier
  -> unsigned protected APK + REPORT_V1
```

Runtime 启动固定顺序为：

```text
Shell AppComponentFactory
  -> sourceDir early configuration
  -> package/signer/shell integrity checks
  -> Native AHDC v2 authentication and decrypt
  -> in-memory DEX class loaders
  -> verified payload session
  -> original factory/application/components
```

任一 signer、AEAD、结构或完整性失败都在 payload class lookup 和 session 发布前 fail closed。环境风险策略不得降低这些强制门禁。

## 3. v0.2 控制面

```text
v0.1 terminal main baseline
  -> development product tuple
  -> product + distribution implementation freeze
  + performance/release-gate validation freeze
  -> v0.2.0-rc.1 product tuple
  -> canonical performance evidence
  -> exact-tuple validation evidence
  -> independent security gate and SBOM
  -> reproducible distributions
  -> release evidence index
```

开发 tuple 只说明 v0.2 从何处开始。release-candidate tuple 才能授权 release evidence。每个 downstream manifest 必须同时记录 tuple hash、source commit、artifact SHA-256 和环境身份。

## 4. 版本隔离

- v0.1 终态文件在固定 Git commit 中保持可复算。
- v0.2 合同、任务和 evidence 位于 `docs/v0.2/`。
- v0.2 不调用旧 M3-05/M3-10/M3-14 workflow，也不下载旧 profile/APK artifact。
- 旧结果可触发更严格测试，但不能成为布尔 PASS 输入。
- candidate freeze 之后产品路径不可修改；修复必须产生新 candidate tuple。

## 5. Tuple 与 manifest

`implementationManifestSha256` 覆盖生产 Host、Runtime、distribution、launcher、archive-internal Quickstart、根构建配置和生产锁文件的规范路径清单。`validationManifestSha256` 覆盖 fresh benchmark、exact-tuple release validation、security/SBOM、packaging/release-evidence validator 和非执行 workflow candidate。工具链、产品合同、fixture source、performance 和 release gate 分别有独立 manifest hash；七个 preimage 的唯一 tracked 路径、schema、allowlist 与 owner 由 `IDENTITY_MANIFESTS.md` 固定，避免任意/空 manifest 或随机测试材料进入身份。

执行 artifact manifest 记录本轮生成的 unsigned fixture、signed input、unsigned protected output、externally signed test copy、报告和环境文件。它必须证明输入前后哈希相同、产品输出未签名以及一次性证书已清理。

## 6. Benchmark 架构

- Host：Windows/Ubuntu 各预热 3 次、保留 10 次，记录时间、RSS、大小和分项调和。
- Android：API 36 x86_64 与 API 29 arm64 reference profile，各使用互补 A/B 顺序、每 mode 预热 5 次、保留 30 次。
- 启动时钟使用同一 boot-time monotonic clock；instrumentation 记录 `Application.onCreate` 与首个 Activity 可交互终点。
- PSS 与 Native heap 通过有界轮询采集，HIGH 只由 Android-test bridge 对 fresh authenticated handle 测量。
- Harness 不修改生产 manifest、BuildConfig、环境变量、公共 Runtime API 或风险决策。

## 7. 发布架构

V2-M0-02 在候选前实现并冻结 packager、Windows/Ubuntu launcher、archive-byte-contract schema 与 archive-internal Quickstart；V2-M3-01 在候选前实现并冻结 security/SBOM、package verification 与 release-evidence gates。V2-M4-01 只执行冻结工具，生成并审查 component manifest、CycloneDX SBOM 和 security gate。V2-M4-02 只能用冻结 packager 打包该 manifest 中的 exact bytes，不新增 source 或 archive entry。发布包依赖预安装 Eclipse Temurin `17.0.19+10`，不接受模糊 JRE 17且不静默联网下载工具。

Final archives 不提交 Git。V2-M4-02 把唯一 same-repository official run/artifact 的 numeric identity、GitHub digest、retention 和 member hashes写入 tracked canonical release artifact lock；V2-M4-03 只能通过该 lock 下载并先验证 bytes，不能接受 workflow input、URL、cache、alternate run 或重建 archive。

产品包不得包含 fixture、证书、私钥、明文 DEX、未剥离 Native 调试符号或 APK 签名命令。smoke test 从包外工作目录挂载本轮已签名合成输入，验证输入只读和未签名输出。

## 8. 失败行为

- 任何最终输出发布前失败：删除临时输出并返回稳定错误码。
- canonical performance 失败：阻塞当前 candidate，不补样、不替换 run。
- exact-tuple 回归失败：阻塞并要求独立修复任务和新 tuple。
- security gate 失败：禁止打包。
- 归档不可复现或内容偏离 component manifest：禁止发布文档形成 PASS。
