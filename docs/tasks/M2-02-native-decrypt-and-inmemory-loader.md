---
id: M2-02
title: "Native 解密与内存 DEX 加载器"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M0-04
  - M1-04
  - M2-01
required_skills:
  - implement-runtime-protection
security_sensitive: true
---

## Goal

在 Native 层解析并认证版本化加密 DEX 容器，将解密后的多 DEX 按原始顺序放入匿名内存，并通过 API 29 公共 `InMemoryDexClassLoader` 接口交给 M2-01。

## Background

payload 不得以明文文件落盘。离线应用内密钥只能增加提取成本，无法在攻击者完全控制进程时提供绝对保密；实现必须如实保留这一安全边界。

## Inputs

- M1-04 的 `ContainerV1` 二进制规范、测试向量和顺序索引。
- ADR 0004 与 ADR 0006 规定的 AEAD、密钥封装和域分离参数。
- M0-04 的 `InMemoryDexClassLoader` 可行性结果。
- M2-01 的 `HardeningBootstrap` 接口。

## Expected Outputs

- `:runtime:native` Android/NDK 模块、`libah_runtime.so` 与 Java 17 静态 JNI bridge。
- 严格的容器解析器、认证解密器、匿名内存所有权对象和多 DEX 类加载器构造器。
- 与 M1-04 共享的兼容测试向量。
- Native 单元测试、instrumentation 测试和 sanitizer 报告。

## In Scope

- 容器头、条目表、长度、偏移、顺序、nonce、tag 和关联数据校验。
- Native 内完成密钥派生、认证解密与关键中间值清零。
- 使用 direct `ByteBuffer[]` 构造 `InMemoryDexClassLoader`。
- 单 DEX、多 DEX、空类查找、重复类优先级和损坏容器处理。

## Out of Scope

- Manifest 改写、APK 重打包和最终 APK 签名。
- 环境评分与反调试策略。
- 将 DEX 明文写入 cache、code cache、临时目录或日志。
- 自定义虚拟机、DEX 指令虚拟化和 API 28 及以下兼容。

## Implementation Decisions

- 模块路径固定为 `runtime/native`；Java bridge 源码位于 `src/main/java` 并使用 Java 17，不得应用 Kotlin Android plugin。
- Java Native 方法固定为 `static native long nativeOpenPayload(AssetManager assets, String assetName, byte[] authenticatedMetadata)`、`static native ByteBuffer[] nativeDexBuffers(long handle)` 和 `static native void nativeClosePayload(long handle)`；`long` 是带类型校验的句柄，不是裸指针。
- Java bridge 固定调用 `System.loadLibrary("ah_runtime")`，APK 内 Native 库名固定为 `libah_runtime.so`。
- 容器必须先完整校验头、边界和 AEAD tag，再向 Java 返回任何 buffer；认证失败不允许部分加载。
- 每个 DEX 使用独立匿名映射，按 M1-04 索引升序形成 `ByteBuffer[]`；父加载器固定为传入的壳 `ClassLoader`。
- 内容密钥、派生材料和 tag 比较只存在于 Native；Java 层不得接触内容密钥。句柄关闭时立即清零可释放的密钥和临时缓冲。
- payload 映射的生命周期与返回的 payload `ClassLoader` 绑定，不在 ART 仍可能读取时提前擦除；M2-06 在此所有权模型上增加 dump 成本控制。
- 所有整数运算使用显式溢出检查，大小上限采用 M1-04 的冻结常量；禁止根据未验证长度分配内存。

## Public Interfaces

- `final class NativePayloadBridge`，只提供上述静态 JNI 方法并禁止实例化。
- `final class PayloadMemoryHandle implements AutoCloseable`，拥有 Native 句柄与 direct buffers，`close()` 必须幂等。
- `final class PayloadClassLoaders`，通过静态方法 `static ClassLoader create(ClassLoader shellLoader, PayloadMemoryHandle handle)` 构造 payload loader。
- Native 错误码前缀 `AAH-RUNTIME-CONTAINER-`，Java 侧统一映射为 `PayloadLoadException`。

## Security Constraints

- 认证失败、版本未知、边界越界、条目重叠、DEX 数量异常和 OOM 前置检查失败均须 fail closed。
- 不得把明文 DEX、密钥、nonce/tag 原文或匿名映射地址写入日志、异常或 tombstone 自定义字段。
- 禁止使用固定 nonce；密钥与 nonce 规则必须逐字遵循 M1-04/ADR 0006，不得另创格式。
- 该设计只提高静态与运行期提取成本，不承诺防止 root、注入、调试器或进程内存控制下的明文截取。

## Compatibility Requirements

- API 29 及以上只使用公共 `InMemoryDexClassLoader`。
- 保持 `classes.dex`、`classes2.dex` 及后续 DEX 的原始查找顺序。
- 支持四个 Runtime ABI；本任务先提供 ABI 无关源代码，M2-04 负责完整构建矩阵。
- 支持 Java/Kotlin 与包含 JNI 调用的 payload；不承担输入应用原有 Native ABI 的转换。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。

## Acceptance Criteria

- `./gradlew :runtime:native:test :runtime:native:connectedCheck :runtime:bootstrap:connectedCheck` 退出码为 `0`。
- 对 M1-04 的全部正向向量，解密后的每个 DEX SHA-256 与源 fixture 完全一致，且类查找顺序测试通过。
- 对 tag、header、offset、length、entry count 和 ciphertext 的单字节篡改，100% 在类加载前以 `AAH-RUNTIME-CONTAINER-` 错误失败。
- instrumentation 运行期间扫描应用私有目录，不存在 DEX magic 开头的新增明文文件。
- ASan/UBSan 主机解析测试无越界、整数溢出、use-after-free 或内存泄漏报告。

## Required Tests

- 共享测试向量的 Native 单元测试和 JVM/JNI 集成测试。
- 单/多 DEX 加载、重复类优先级、父加载器委派和句柄生命周期测试。
- 截断、重叠、超大长度、未知版本、错误 tag、错误关联数据和随机输入测试。
- API 29 与最高受支持 API 的 instrumentation 启动测试。

## Required Evidence

- 所有命令、退出码、NDK/Clang/Android API 与设备信息。
- 正向向量的源 DEX 与解密 DEX SHA-256 对照表。
- 负向用例统计、sanitizer 报告和私有目录明文扫描结果。
- AAR、各 ABI `.so` 和测试报告的 SHA-256。

## Likely Files

- `runtime/native/build.gradle.kts`
- `runtime/native/src/main/cpp/CMakeLists.txt`
- `runtime/native/src/main/cpp/container_parser.cpp`
- `runtime/native/src/main/cpp/payload_memory.cpp`
- `runtime/native/src/main/cpp/jni_bridge.cpp`
- `runtime/native/src/main/java/ah/runtime/NativePayloadBridge.java`
- `runtime/native/src/main/java/ah/runtime/PayloadMemoryHandle.java`
- `runtime/native/src/main/java/ah/runtime/PayloadClassLoaders.java`
- `runtime/native/src/test/`
- `runtime/native/src/androidTest/`

## Dependencies and Blockers

M1-04 容器字节规范、AEAD 参数或测试向量未冻结时不得实现解析器。若 M0-04 证明公共内存类加载接口无法保持既定 DEX 顺序，必须阻塞并发起 ADR 评审，不得写入明文临时 DEX。

## Agent Handoff Requirements

使用分支 `feat/m2-02-native-decrypt-loader`，只处理 Issue `M2-02` 并仅创建一个对应 PR。交接必须包含 JNI 所有权说明、容器版本、测试向量哈希、命令与退出码、sanitizer 结果、设备矩阵、产物 SHA-256 及无法消除的内存提取风险；不得提交任何真实 APK、密钥或明文客户 DEX。
