# ADR 0001: 仅采用 APK 后处理

## Status

Accepted

## Context

项目需要保护已经由标准 Android 构建链生成的应用，并允许使用方在不改造业务源码或 Gradle 工程的情况下接入。目标输入是一个完整、已签名的独立 APK，而不是源码、AAB 或多 APK 集合。

如果同时支持 Gradle plugin、源码插桩、AAB 和 APK 后处理，会产生多套生命周期、签名、资源和兼容性合同，无法在 v0.1 内形成可验证边界。后处理还必须保证输入只读，避免破坏唯一发行产物。

## Decision

v0.1 只提供离线 Host CLI，对单个独立 APK 执行后处理：

```text
signed standalone APK
-> inspect and transform in isolated workspace
-> new unsigned hardened APK + JSON report
```

Host：

- 以只读方式打开输入，并在处理前后比较 SHA-256；
- 在随机受限工作目录创建全部中间状态；
- 只向不同的输出路径发布新 APK；
- 不要求或读取业务源码、Gradle 工程；
- 不接受 AAB、Split APK、动态特性模块或 APK set；
- 不修复不支持输入，而是在写出前以稳定错误码拒绝。

未来若支持其他输入形态，必须建立独立架构决策、产品合同和测试矩阵，不能复用“APK 后处理”名称静默扩大范围。

## Consequences

积极结果：

- 接入与业务构建系统解耦；
- 输入/输出边界清晰，便于做字节级不变性验证；
- Windows 与 Ubuntu 可使用同一 Host 流程；
- 任务可按 Inspector、AXML、Container、Repacker 分离。

代价：

- 无法利用编译期语义做细粒度变换；
- 必须正确处理二进制 AXML、签名块和 ZIP 结构；
- 框架、热修复和已有壳的兼容性只能保守拒绝；
- 输出修改使原签名失效，必须由使用方重新签名。

## Rejected Alternatives

- Gradle plugin：要求修改业务工程，扩大工具链接入面，并不能覆盖只有 APK 的场景。
- 源码/字节码编译期插件：需要源代码和构建上下文，不符合输入合同。
- AAB 后处理：涉及 bundletool、拆分和设备定向签名，超出 v0.1。
- 原地修改 APK：违反输入只读原则，失败时可能损坏唯一输入。
- 同时提供多种入口：会在首版形成无法统一验证的多套行为。

## Security Impact

APK 被视为完全不可信输入，解析器必须有边界、资源上限和模糊测试。独立工作目录和只读输入降低路径穿越与误覆盖风险。后处理不能防止恶意业务代码本身运行；产品只在 Host 中解析，不执行输入 APK 代码。

## Compatibility Impact

仅标准 Java/Kotlin 独立 APK进入兼容性承诺。AAB、Split APK、Flutter、Unity、React Native、热修复、插件化和已有加固壳被明确拒绝。APK 必须声明 `minSdk >= 29`。

## Verification

- 成功、解析失败、磁盘空间不足和强制中断测试均比较输入前后 SHA-256。
- 输入输出同路径、硬链接或解析为同一文件时在写入前拒绝。
- AAB、Split 特征和多个 APK 输入返回稳定兼容性错误。
- 输出只在最终重读验证成功后原子发布。
- Host 流程中不存在执行输入代码的步骤。
