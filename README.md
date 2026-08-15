# Android App Hardening

面向标准 Android APK 的离线后处理加固工具项目。

项目目标是提供 DEX 加密、防二次打包、加固增量控制、DEX 内存截取成本提升、终端环境检测、四 ABI Runtime 与运行时签名校验。工具只产生新的未签名 APK，不接收私钥、keystore、alias 或密码，也不执行应用签名。

## 当前状态

M0 基础建设与可行性验证以及 M1 Host 处理链已经完成。M1-01 输入检查、M1-02 signer policy、M1-03 Binary AXML 转换器、M1-07 AHDC v2 分块认证容器合同、M1-04 AHDC v2 Host 实现、M1-05 APK 重打包与对齐、M1-06 CLI 与 JSON 报告均已合并并通过双平台门禁。M2 已完成并合并独立前置任务 M2-07、Native AHDC v2 解密与内存 DEX loader M2-02、Runtime signer/authenticated metadata/启动事务所有权门禁 M2-03、生产 Shell `AppComponentFactory` 启动链 M2-01、四 ABI Runtime 构建与装配 M2-04、版本化环境风险信号与评分引擎 M2-05，以及内存 dump 成本控制 M2-06。M3-01 的九个公开 Android fixture、Host full-flow、Ubuntu/Windows、API 29/36 x86_64 KVM 与 API 29 arm64 真机矩阵均已通过并由 PR #49 合并。M3-02 的 69 项篡改 catalog、Jazzer/Native sanitizer fuzz、Ubuntu/Windows 与 API 29/36 KVM 矩阵也已全部通过并由 PR #52 合并。M3-03 的九 fixture 双轮随机化 Windows/Ubuntu 语义等价矩阵、独立 AHDC/DEX 验证、稳定负例与输入只读门禁已通过并由 PR #55 合并。M3-06 进一步用 ADR 0012 固定逐格 `VERIFIED`/`FAILED`/`UNVERIFIED` 声明边界，四 ABI 构建能力不再被误写成所有 API/ABI 组合均已验证。M3-04 已生成完整 32 格 API/ABI 清单；API 29 ARM32/ARM64 与 API 29/36 x86_64 四个强制格子均由真实进程证据标记为 `VERIFIED`，其余 28 格明确为 `UNVERIFIED`，并由 PR #58 合并。随后 M2-07 的 Windows hosted-runner 精确锁已通过 PR #51 更新至已复核镜像 `20260810.198.2`，保持未知镜像失败关闭，且未重复 KVM 或真机矩阵。这些控制只提高攻击成本，不承诺阻止 root、注入或进程控制攻击者。生产 RuntimeBundle 与可发布端到端发行包仍属于后续任务。

开发者和 Agent 从 [`docs/README_FIRST.md`](docs/README_FIRST.md) 开始。项目统筹状态以 [`HandOff.md`](HandOff.md) 为准。

### 任务进度

| 阶段/任务 | 状态 | 说明 |
| --- | --- | --- |
| M0-01 ～ M0-06 | 已完成 | 仓库、治理、工具链、CI、API 29/36 ClassLoader 与早期启动/Factory/JNI 可行性验证均已合并并通过门禁 |
| [M1-01](docs/tasks/M1-01-untrusted-apk-inspector.md) | 已完成 | 不可信 APK 只读检查器，PR #33 |
| [M1-02](docs/tasks/M1-02-signer-policy.md) | 已完成 | signer policy，PR #34 |
| [M1-03](docs/tasks/M1-03-binary-axml-transformer.md) | 已完成 | Binary AXML 单属性转换器，PR #35 |
| [M1-07](docs/tasks/M1-07-chunk-authenticated-container-contract.md) | 已完成 | AHDC v2 合同、独立安全复核与双平台门禁，PR #37、Issue #36 |
| [M1-04](docs/tasks/M1-04-encrypted-dex-container.md) | 已完成 | AHDC v2 Host 容器实现；PR #38、Issue #9、独立安全复核、merger-ready 与 post-merge `main` 门禁均已关闭 |
| [M1-05](docs/tasks/M1-05-apk-repacker-and-alignment.md) | 已完成 | PR #39、Issue #10、四轮独立安全复核、merger-ready 与 post-merge `main` 双平台 CI、README 和 strict HandOff 均已关闭 |
| [M1-06](docs/tasks/M1-06-cli-and-json-report.md) | 已完成 | PR #40、Issue #11、冻结只读复核、Ubuntu/Windows full-flow/字节一致性 CI、expected-head 普通合并与 post-merge strict HandOff 均已关闭 |
| [M2-07](docs/tasks/M2-07-native-crypto-backend.md) | 已完成 | PR #42、Issue #41 完成 Native 密码后端；PR #51、Issue #50 完成 Windows runner `20260810.198.2` 精确锁维护与独立全零复核；双平台 Build/Governance 均已关闭 |
| [M2-02](docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md) | 已完成 | PR #43、Issue #13、第三轮独立安全复核、Ubuntu/Windows Build/Governance、API 29/36 KVM、API 29 arm64 非 root 真机与 strict HandOff 均已关闭 |
| [M2-03](docs/tasks/M2-03-runtime-signer-and-integrity.md) | 已完成 | PR #44、Issue #14、P0/P1/P2 全零复核、Ubuntu/Windows、API 29/36 KVM、API 29 arm64 非 root 真机与 strict HandOff 均已关闭 |
| [M2-01](docs/tasks/M2-01-shell-app-component-factory.md) | 已完成 | PR #45、Issue #12、P0/P1/P2 全零复核、Ubuntu/Windows Build/Governance、API 29/36 KVM 与 strict HandOff 均已关闭 |
| [M2-04](docs/tasks/M2-04-four-abi-runtime.md) | 已完成 | PR #46、Issue #15、P0/P1/P2 全零复核、Ubuntu/Windows、API 29/36 KVM、API 29 ARM 双 ABI 真机与 strict HandOff 均已关闭 |
| [M2-05](docs/tasks/M2-05-environment-risk-engine.md) | 已完成 | PR #47、Issue #16、P0/P1/P2 全零复核、Ubuntu/Windows、API 29/36 KVM、真实 JDWP、x86 与 Release/R8 门禁均已关闭 |
| [M2-06](docs/tasks/M2-06-memory-dump-cost-controls.md) | 已完成 | PR #48、Issue #17、P0/P1/P2 全零独立复核、Ubuntu/Windows、API 29/36 KVM、Release/R8、清零/只读/`DONTDUMP`/锁页/抖动、expected-head 合并与 post-merge strict HandOff 均已关闭 |
| [M2-08](docs/tasks/M2-08-native-parser-topology-bounds.md) | 已完成 | PR #54、Issue #53、精确 399-byte 回归、Ubuntu ASan/UBSan、Ubuntu/Windows Build/Governance、P0/P1/P2 全零独立复核及 expected-head 合并均已关闭；无需设备或 KVM |
| [M2-09](docs/tasks/M2-09-shell-factory-relaunch-lifecycle.md) | 已完成 | PR #60、Issue #59、P0/P1/P2 全零独立复核、Ubuntu/Windows Build/Governance、API 29/36 KVM、真实 `Activity.recreate()`、三种 M201 Release/R8 路径及 expected-head 合并均已关闭 |
| [M3-01](docs/tasks/M3-01-android-fixtures.md) | 已完成 | 九个公开 fixture、Host full-flow、Ubuntu/Windows、API 29/36 x86_64 KVM、API 29 arm64 真机与清理均已通过；PR #49、Issue #18 已关闭 |
| [M3-02](docs/tasks/M3-02-tamper-and-fuzz-tests.md) | 已完成 | 69 项篡改 catalog、Jazzer APK/AXML、Native libFuzzer + ASan/UBSan、Ubuntu/Windows、API 29/36 KVM 与独立全零复核均已通过；PR #52、Issue #19 已关闭 |
| [M3-03](docs/tasks/M3-03-windows-ubuntu-equivalence.md) | 已完成 | 九个 fixture 在 Windows/Ubuntu 各运行两轮；36 个输出的稳定语义等价、随机字段不复用、独立认证解密、稳定负例、输入只读与无绝对路径泄漏均通过；PR #55、Issue #20 已关闭 |
| [M3-06](docs/tasks/M3-06-api-abi-validation-claim-contract.md) | 已完成 | ADR 0012 与逐格声明合同已固定；PR #57 exact-head Ubuntu/Windows Build/Governance 通过，设备/KVM/fuzz 按治理范围未运行 |
| [M3-04](docs/tasks/M3-04-api-and-abi-matrix.md) | 已完成 | 完整 32 格清单、四个真实 `VERIFIED` 强制格子、28 个明确 `UNVERIFIED` 格子、JSON/Markdown 等价及清理均已通过；PR #58、Issue #21 已关闭 |
| M3-05 ～ M4 | 未启动 | M3-05 在本次 post-merge `main` 门禁通过后启动；发布阶段继续按任务依赖顺序执行 |

任务按 [`docs/tasks/INDEX.md`](docs/tasks/INDEX.md) 的依赖顺序执行。每个任务只有在 PR 合并、合并后门禁与证据完成后才在本表标记“已完成”；每个任务的收尾协调提交必须同步本 README，避免公开进度长期滞后。

## v0.1 边界

- 只接受独立 standalone APK，输入文件始终只读。
- 只产生一个新的未签名 APK，由用户在外部自行签名。
- 输入必须声明 `minSdk >= 29`。
- 目标应用为标准 Java/Kotlin，覆盖单 DEX、多 DEX、自定义 `Application` 和受支持的 `AppComponentFactory`。
- Runtime 构建 `armeabi-v7a`、`arm64-v8a`、`x86` 和 `x86_64`；不会把缺少 x86 客户 SO 的 ARM-only 应用变成 x86 应用。
- 不支持 AAB、APKS、Split APK、动态特性、Flutter、Unity、React Native、热修复、插件框架或已有第三方加固壳。
- DEX 防截取、环境检测和离线密钥隐藏只能提高攻击成本，不能绝对阻止有 root、Hook、定制 ART 或内核能力的攻击者。

完整产品合同见 [`docs/PRODUCT_REQUIREMENTS.md`](docs/PRODUCT_REQUIREMENTS.md)，安全边界见 [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md)。

## 构建基线

M0-03 固定 Eclipse Temurin `17.0.19+10`、Gradle `9.5.0`、Kotlin/JVM `2.4.10`、Android Gradle Plugin `9.3.0`、Android Platform `36`、Build Tools `36.1.0`、NDK `29.0.14206865`、CMake `4.1.2` 和 Node.js `24.12.0`。版本唯一来源是 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)；开发者必须让当前项目终端的 `JAVA_HOME` 指向固定 JDK 17，不需要也不应替换其他项目使用的全局 JDK。

Windows 基线：

```powershell
.\gradlew.bat --no-daemon clean check verifyGovernance
```

Ubuntu 全量基线：

```bash
./gradlew --no-daemon clean check lint verifyGovernance
./gradlew --no-daemon :runtime:native:assemble
```

当前工程已包含 M1-01 输入检查、M1-02 signer policy、M1-03 Binary AXML 转换、M1-04 AHDC v2 容器、M1-05 APK 重打包/对齐、M1-06 Host CLI/REPORT_V1，以及 M2-02/M2-03/M2-01/M2-04 的 Native 内存加载、Runtime signer、authenticated metadata、启动事务门禁、生产 Shell `AppComponentFactory` 启动链和四 ABI Runtime 装配。M1-06 生产入口只读取后续发行任务提供的固定 classpath RuntimeBundle；仓库合成 RuntimeBundle 仅用于忽略目录中的 full-flow 测试，不是可发布 Runtime。依赖解析继续使用严格 SHA-256 verification metadata、全 configuration lockfile 与 settings 级 `google()`/`mavenCentral()` 白名单。

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。
