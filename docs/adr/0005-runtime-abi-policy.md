# ADR 0005: Runtime ABI 策略

## Status

Accepted

## Context

Runtime 包含 Native 容器解析、密钥恢复和内存解密代码，必须与设备进程 ABI 匹配。项目要求覆盖 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。但向一个 ARM-only 原生应用注入 x86 Runtime 库可能让包结构看似支持 x86，原应用自身的 JNI 库仍然缺失，不能形成真实跨架构能力。

因此必须区分“项目 Runtime 可构建的 ABI”与“单个输出 APK 实际兼容的 ABI”。

## Decision

Runtime 发布和 CI 始终构建并验证四个 ABI：

```text
armeabi-v7a
arm64-v8a
x86
x86_64
```

Host 根据输入原生库集合决定单个输出的 Runtime ABI：

- 输入没有原生库时，注入全部四个 Runtime ABI。
- 输入含原生库时，只为输入已提供的受支持 ABI 注入对应 Runtime。
- 输入原生库 ABI 与四 ABI 集合无交集时拒绝。
- 不复制、翻译、二进制转换或声明输入缺失的业务 Native ABI。
- 不因项目拥有 x86 Runtime 就把 ARM-only 输入报告为 x86 compatible。
- 每个被注入 ABI 的 Runtime 使用相同源码、容器版本、JNI 接口和错误合同。

Host 报告同时列出 `runtime_available_abis`、`input_native_abis` 和 `output_effective_abis`，避免将构建能力与应用能力混淆。

## Consequences

积极结果：

- 项目可覆盖真实设备和常见模拟器；
- 不制造 ARM-only 应用可在 x86-only 设备运行的错误预期；
- 含 Native 库的输出保持原应用 ABI 边界；
- 不必要的 Runtime ABI 不进入受限输出，可减少大小增量。

代价：

- 不同输入的输出 ABI 集合可能不同；
- 四 ABI 都需要 Native 测试、sanitizer 和发布证据；
- ABI 检查器必须正确处理目录、ELF header 和同名库冲突；
- 无 Native 库输入注入四 ABI 会产生明显大小增量。

## Rejected Alternatives

- 每个输出无条件注入四 ABI：对 ARM-only 应用产生虚假 x86 兼容信号并增加大小。
- 只支持 `arm64-v8a`：不满足项目四 ABI 目标和 32-bit/模拟器覆盖。
- 使用 Native bridge 假定跨架构：依赖设备实现，不能成为 APK 能力承诺。
- 对缺失 ABI 生成占位 `.so`：会把安装问题转化为运行崩溃。
- 构建每个输入自定义 Native 业务库：后处理器没有源码，技术上也不等价。

## Security Impact

四 ABI 必须共享安全逻辑，避免某一架构缺少边界检查、清零或完整性校验。CI 比较导出符号和 golden vectors，并对可运行 Native 目标启用 sanitizer。

减少不适用 ABI 也减少攻击面和包大小，但不改变离线密钥可恢复的残余风险。

## Compatibility Impact

无 Native 业务库的标准 Java/Kotlin 应用支持四 ABI。含 Native 库的应用只支持原 APK 已提供且属于四 ABI 集合的交集。ARM-only 保持 ARM-only；64-bit-only 不获得 32-bit 能力。未知 ABI 或 Native 打包冲突必须在 Host 写出前明确拒绝。

## Verification

- CI 为四 ABI 构建相同 Runtime 版本并比较导出 JNI symbol 清单。
- API 29 最低边界和最高声明 API 对四 ABI 执行启动用例。
- 无 Native fixture 的输出含四 ABI 并分别启动。
- ARM-only fixture 的输出只含输入 ARM ABI；在 x86-only 环境得到明确不兼容结果。
- 对每种输入 ABI 子集验证 `output_effective_abis` 和实际 ZIP 条目一致。
- 替换、删除或混用 Runtime `.so` 时在业务代码前失败。
