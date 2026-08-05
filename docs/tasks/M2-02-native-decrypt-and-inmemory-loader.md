---
id: M2-02
title: "Native 解密与内存 DEX 加载器"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-04
  - M1-04
required_skills:
  - implement-runtime-protection
security_sensitive: true
---

## Goal

在 Native 层解析并认证版本化加密 DEX 容器，对每条已认证的压缩记录执行有界 zlib 解压，将恢复出的多 DEX 按原始顺序放入匿名内存，并通过 API 29 公共 `InMemoryDexClassLoader` 接口交给后继 M2-03 的唯一启动 Guard；M2-01 不得直接调用本任务的低层 facade。

## Background

payload 不得以明文文件落盘。离线应用内密钥只能增加提取成本，无法在攻击者完全控制进程时提供绝对保密；实现必须如实保留这一安全边界。

## Inputs

- M1-04 的 `ContainerV2` 二进制规范、测试向量和顺序索引。
- ADR 0008 与 ADR 0006 规定的 AEAD、密钥封装和域分离参数。
- M0-04 的 `InMemoryDexClassLoader` 可行性结果。
- ADR 0003 冻结的 `ApplicationInfo.sourceDir` 早期只读输入与固定 asset 名称。

## Expected Outputs

- `:runtime:native` Android/NDK 模块、`libah_runtime.so` 与 Java 17 静态 JNI bridge。
- 严格的容器解析器、认证解密器、有界 zlib 解压器、匿名内存所有权对象和多 DEX 类加载器构造器。
- 与 M1-04 共享的兼容测试向量。
- Native 单元测试、instrumentation 测试和 sanitizer 报告。

## In Scope

- 容器头、条目表、长度、偏移、顺序、nonce、tag 和关联数据校验。
- `ConfigV2`、当前 ABI share slot、config digest 和 AHDC build/key slot 绑定校验。
- Native 内完成密钥派生、认证解密、有界 zlib 解压、原始长度/SHA-256 校验与关键中间值清零。
- 使用 direct `ByteBuffer[]` 和 M0-05 冻结的公开 Native 搜索路径构造三参数 `InMemoryDexClassLoader`。
- 单 DEX、多 DEX、空类查找、重复类优先级和损坏容器处理。

## Out of Scope

- Manifest 改写、APK 重打包和最终 APK 签名。
- 环境评分与反调试策略。
- 将 DEX 明文写入 cache、code cache、临时目录或日志。
- 自定义虚拟机、DEX 指令虚拟化和 API 28 及以下兼容。

## Implementation Decisions

- 模块路径固定为 `runtime/native`；Java bridge 源码位于 `src/main/java` 并使用 Java 17，不得应用 Kotlin Android plugin。
- Java Native 方法固定为有界预读 `static native byte[] nativeInspectBinding(String installedApkPath)`、认证打开 `static native long nativeOpenVerifiedPayload(String installedApkPath, String installedPackageName, byte[] installedSignerSha256)`、`static native ByteBuffer[] nativeDexBuffers(long handle)` 和 `static native void nativeClosePayload(long handle)`；路径与包名只能由 facade 从 Framework `ApplicationInfo.sourceDir`/`packageName` 取得，两个 asset 名是 Native 编译期常量，`long` 是带类型校验的句柄，不是裸指针。
- Java bridge 固定调用 `System.loadLibrary("ah_runtime")`，APK 内 Native 库名固定为 `libah_runtime.so`。
- 容器必须先完整校验头、边界、manifest MAC 和对应 record 的 AEAD tag，再将该 record 的已认证压缩字节交给 zlib；认证失败不允许解压或部分加载。
- `nativeInspectBinding` 只解析固定 ConfigV2、AHDC header 与 `SPV1` 的长度/格式上限，返回明确标记为未认证的当前 signer 摘要、build ID 和 key slot ID，不恢复 CEK、不暴露 Factory/策略、不分配 payload buffer。`nativeOpenVerifiedPayload` 使用实测安装 signer 与 Framework package name 恢复 CEK，认证覆盖 `SPV1` 的 manifest MAC，并再次比较已认证当前摘要；任一失配均在 record 解密前失败。
- Native 以只读方式打开 `ApplicationInfo.sourceDir` 指向的当前 APK，使用有界 ZIP central-directory/local-header locator 查找唯一规范条目 `assets/ah/runtime/payload.ahdc` 和 768-byte `assets/ah/runtime/config.bin`；条目必须为 `STORED`、无 encryption、无 data descriptor、CRC/长度一致且不存在重复名称。不得解压、复制到临时文件或接受调用方任意路径/asset 名；package name 只能取同一 Framework `ApplicationInfo.packageName` 并以精确 UTF-8 SHA-256 参与 ADR 0006 KEK 和 ADR 0008 chunk AAD。
- 恢复流程严格解析 ADR 0006 `ConfigV2` 和当前 ABI 的 104-byte `NativeShareSlotV1`，先验证结构、`slot_sha256`/ABI/build/key slot，以实测 signer/package binding 重组 `R` 并验证 CEK envelope，再用 CEK 验证 AHDC manifest MAC，随后从已认证 header 常量时间比较完整 config SHA-256，最后交叉比较 signer、版本和 build/key slot；任一步失败都不得暴露 Factory/策略或解密 record。
- AHDC v2 只接受 zlib-wrapped DEFLATE，不接受 raw DEFLATE、gzip wrapper、preset dictionary、多拼接流或流结束后的尾随字节。每个 canonical chunk 以一次性 GCM API 验证成功后才进入该 record 的唯一连续 inflater；不得接受 AHDC v1 或消费 tag 验证前的 plaintext。解压输出上限同时受 record 原始长度和项目冻结的单 DEX/总 DEX 上限约束。
- 解压必须恰好得到 record 声明的原始长度并命中原始 DEX SHA-256；提前结束、超长、zlib checksum 错误、要求 dictionary 或仍有未消费输入均 fail closed。
- 每个恢复后的原始 DEX 使用独立匿名映射，按 M1-04 索引升序形成 `ByteBuffer[]`；父加载器固定为传入的壳 `ClassLoader`。Java facade 必须逐字复用 M0-05 的 `NativeLibrarySearchPathResolver`，再调用 API 29 三参数 `InMemoryDexClassLoader`；不得使用空 search path、反射复制 parent path list 或假设 parent 能为 payload 类查找业务 SO。
- 内容密钥、派生材料和 tag 比较只存在于 Native；Java 层不得接触内容密钥。句柄关闭时立即清零可释放的密钥和临时缓冲。
- payload 映射的生命周期与返回的 payload `ClassLoader` 绑定，不在 ART 仍可能读取时提前擦除；M2-06 在此所有权模型上增加 dump 成本控制。
- 所有整数运算使用显式溢出检查，压缩输入、原始输出、单 DEX 和总 payload 大小上限采用 M1-04 的冻结常量；禁止根据未认证或未验证长度分配内存。

## Public Interfaces

- 低层 facade 固定为 `public final class ah.runtime.loader.PayloadRuntime`，提供 `public static UntrustedPayloadBinding inspectBinding(ApplicationInfo applicationInfo)` 与 `public static LoadedPayload openVerified(ClassLoader shellLoader, ApplicationInfo applicationInfo, byte[] installedSignerSha256)`；facade 拒绝空/非绝对 `sourceDir` 或空 `packageName`，并只把 Framework 的 source/package 字段传入 Native。名称和文档必须明确前者未认证、后者仍要求调用者先完成安装 APK signer 验证。
- 所有权对象固定为 `public final class ah.runtime.loader.LoadedPayload implements AutoCloseable`；只公开 `public ClassLoader classLoader()` 与幂等 `public void close()`，这里的 loader 是供 M2-01 原 Factory ClassLoader hook 消费的 provisional loader。对象内部强拥有 Native 句柄、direct buffers 和该 loader；M2-03 的 `VerifiedPayloadSession` 必须保留该对象，不得只保存裸 `ClassLoader`。
- `LoadedPayload.close()` 必须幂等且可计数验证：先阻止新访问，再关闭 Native handle、清零仍可安全清理的密钥/临时/direct buffer，最后清除自身强引用。清理子步骤失败时继续其余清理并返回稳定 cleanup failure，不得恢复已关闭句柄。
- `public final class ah.runtime.loader.UntrustedPayloadBinding` 只公开复制后的预读字段，类型名和访问器文档不得将其描述为已认证。
- `NativePayloadBridge`、`PayloadMemoryHandle` 与 `PayloadClassLoaders` 均位于 `ah.runtime.loader` 且为 package-private；它们只分别承担 JNI、Native 句柄所有权和 loader 构造，不构成跨模块 API。
- `:runtime:policy` 以 Gradle `implementation(project(":runtime:native"))` 消费本 facade，不能把它传递到 `:runtime:bootstrap` compile classpath；唯一生产调用者由 M2-03 的架构测试锁定为 `RuntimeStartupGuard`。
- Native 错误码前缀 `AAH-RUNTIME-CONTAINER-`，其中认证、zlib wrapper、dictionary、checksum、长度、SHA-256 和尾随数据具有稳定且互不混淆的分类；Java 侧统一映射为 `PayloadLoadException`。

## Security Constraints

- 认证失败、版本未知、边界越界、条目重叠、DEX 数量异常、zlib 格式/校验错误、解压炸弹上限和 OOM 前置检查失败均须 fail closed。
- 不得把明文 DEX、密钥、nonce/tag 原文或匿名映射地址写入日志、异常或 tombstone 自定义字段。
- 禁止使用固定 nonce；密钥与 nonce 规则必须逐字遵循 M1-04/ADR 0006，不得另创格式。
- 该设计只提高静态与运行期提取成本，不承诺防止 root、注入、调试器或进程内存控制下的明文截取。

## Compatibility Requirements

- API 29 及以上只使用公共 `InMemoryDexClassLoader`。
- 保持 `classes.dex`、`classes2.dex` 及后续 DEX 的原始查找顺序。
- 支持四个 Runtime ABI；本任务先提供 ABI 无关源代码，M2-04 负责完整构建矩阵。
- 支持 Java/Kotlin 与包含 JNI 调用的 payload；不承担输入应用原有 Native ABI 的转换。
- 同时支持 installer 已解压 SO 与 `extractNativeLibs=false` 的 APK 内直接加载路径，选择结果必须与当前进程 bitness 和输出实际 ABI entry 一致。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。

## Acceptance Criteria

- `./gradlew :runtime:native:test :runtime:native:connectedCheck :runtime:bootstrap:connectedCheck` 退出码为 `0`。
- 对 M1-04 的全部正向向量，认证解密并解压后的每个原始 DEX SHA-256 与源 fixture 完全一致，且类查找顺序测试通过。
- 对 tag、header、offset、length、entry count、ciphertext、zlib header/checksum 和压缩流尾部的单字节篡改，100% 在类加载前以对应 `AAH-RUNTIME-CONTAINER-` 错误失败。
- raw DEFLATE、gzip、preset dictionary、多拼接流、尾随字节、提前结束以及超过 record 原始长度或总上限的解压流均在分配超限内存或返回任何 `ByteBuffer` 前失败。
- instrumentation 运行期间扫描应用私有目录，不存在 DEX magic 开头的新增明文文件。
- `extractNativeLibs=true/false` fixture 均能由 payload 类加载业务 JNI；无匹配 ABI、重复 ABI 目录和路径篡改均在组件实例化前失败。
- `LoadedPayload.close()` 重复调用仍只关闭一次 Native handle；关闭后访问器拒绝，仍可安全清理的 direct/key/temp buffer 已清零，自身不再强引用 loader 或 buffer；任一清理子步骤失败不妨碍其余步骤。
- ASan/UBSan 主机解析测试无越界、整数溢出、use-after-free 或内存泄漏报告。

## Required Tests

- 共享测试向量的 Native 单元测试和 JVM/JNI 集成测试。
- 单/多 DEX 加载、重复类优先级、父加载器委派和句柄生命周期测试。
- `LoadedPayload` 幂等 close-count、Native handle、关闭后访问、direct/key/temp buffer 清零、强引用释放和多清理错误聚合测试。
- 截断、重叠、超大长度、未知版本、错误 tag、错误 signer/package 关联数据和随机输入测试。
- zlib wrapper、checksum、dictionary、尾随/拼接流、提前结束、声明长度不符、SHA-256 不符和解压炸弹测试。
- APK ZIP locator 的重复 asset、压缩 method、encryption、data descriptor、CRC/长度、local/central header 不一致、ZIP64 边界和截断测试。
- API 29 与最高受支持 API 的 instrumentation 启动测试。

## Required Evidence

- 所有命令、退出码、NDK/Clang/Android API 与设备信息。
- 正向向量的源 DEX 与认证解密/解压后 DEX SHA-256 对照表。
- 负向用例统计、sanitizer 报告和私有目录明文扫描结果。
- AAR、各 ABI `.so` 和测试报告的 SHA-256。

## Likely Files

- `runtime/native/build.gradle.kts`
- `runtime/native/src/main/cpp/CMakeLists.txt`
- `runtime/native/src/main/cpp/container_parser.cpp`
- `runtime/native/src/main/cpp/payload_memory.cpp`
- `runtime/native/src/main/cpp/jni_bridge.cpp`
- `runtime/native/src/main/java/ah/runtime/loader/PayloadRuntime.java`
- `runtime/native/src/main/java/ah/runtime/loader/LoadedPayload.java`
- `runtime/native/src/main/java/ah/runtime/loader/UntrustedPayloadBinding.java`
- `runtime/native/src/main/java/ah/runtime/loader/NativePayloadBridge.java`
- `runtime/native/src/main/java/ah/runtime/loader/PayloadMemoryHandle.java`
- `runtime/native/src/main/java/ah/runtime/loader/PayloadClassLoaders.java`
- `runtime/native/src/test/`
- `runtime/native/src/androidTest/`

## Dependencies and Blockers

M1-04 容器字节规范、AEAD 参数或测试向量未冻结时不得实现解析器。若 M0-04 证明公共内存类加载接口无法保持既定 DEX 顺序，必须阻塞并发起 ADR 评审，不得写入明文临时 DEX。

## Agent Handoff Requirements

使用分支 `feat/m2-02-native-decrypt-loader`，只处理 Issue `M2-02` 并仅创建一个对应 PR。交接必须包含 JNI 所有权说明、容器版本、测试向量哈希、命令与退出码、sanitizer 结果、设备矩阵、产物 SHA-256 及无法消除的内存提取风险；不得提交任何真实 APK、密钥或明文客户 DEX。
