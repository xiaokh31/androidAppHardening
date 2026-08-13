---
id: M2-06
title: "内存截取成本控制"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M2-02
  - M2-04
  - M2-05
required_skills:
  - implement-runtime-protection
  - validate-protected-apk
security_sensitive: true
---

## Goal

在不破坏 ART 类加载兼容性的前提下，缩短密钥和临时明文生命周期、降低常规 core dump 可见性并对高风险环境启用额外内存保护，从而提高 DEX 内存截取成本。

## Background

ART 可能在 payload `ClassLoader` 生命周期内继续依赖解密 DEX 映射，因此不能承诺立即擦除全部 payload。具有 root、注入或进程控制能力的攻击者仍可能取得明文；本任务只做可验证的成本控制，不作绝对防御声明。

## Inputs

- M2-02 的公开 `LoadedPayload`、内部 `PayloadMemoryHandle`、匿名映射所有权和密钥清零点。
- M2-05 的 `RiskReportV1`、固定风险等级和 `RiskAction`。
- 威胁模型中的内存转储攻击与可接受兼容性边界。
- M2-04 的四 ABI 构建配置。

## Expected Outputs

- `:runtime:native` 的 `libah_runtime.so` 安全内存封装、清零函数和映射保护策略。
- `LOW`、`MEDIUM`、`HIGH` 对应的固定控制配置。
- 内存映射、core dump、密钥生命周期和应用稳定性测试。
- 明确列出未能防御的进程内攻击。

## In Scope

- 内容密钥、派生材料、压缩临时区和认证临时区的确定性清零。
- payload 匿名映射的 `MADV_DONTDUMP`、只读保护和有上限的 best-effort `mlock`。
- 高风险时设置本进程不可 dump、增加固定抖动并再次验证映射属性。
- 异常、取消、部分解析和进程退出路径的资源清理。

## Out of Scope

- 声称防止所有内存 dump、root、内核级攻击、硬件调试或 ART hook。
- 在 ART 仍依赖 payload 时擦除或释放 DEX 映射。
- 使用隐藏 API、内核模块、常驻守护进程或额外系统权限。
- 基于 CPU ABI 单独启用高风险策略。

## Implementation Decisions

- Native 实现固定在 `runtime/native/src/main/cpp`，Java 策略接口固定在 `runtime/policy/src/main/java` 并使用 Java 17；Android Runtime 模块不得应用 Kotlin Android plugin。
- 所有密钥与短期明文使用 `SecureBuffer`，释放时调用不会被编译器优化掉的显式清零函数；异常路径采用同一 RAII 清理。
- payload 映射认证完成后切换为只读，并调用 `madvise(MADV_DONTDUMP)`；不支持时记录稳定能力位，不降低为磁盘明文。
- `mlock` 只覆盖密钥页和每个 DEX 首尾各 64 KiB，进程总上限 1 MiB；失败是可观测的 best-effort 结果，不造成兼容性崩溃。由于首个 `LoadedPayload` 返回前密钥已销毁，密钥 `SecureBuffer` 在自身生命周期内始终作受限 best-effort 锁页尝试；风险等级只控制返回后仍保留的 DEX 边缘锁页，详见 ADR 0011。
- `LOW` 启用清零、只读与 `DONTDUMP`，同时继承上述短生命周期密钥锁页不变量；`MEDIUM` 额外启用受限 DEX 边缘 `mlock`；`HIGH` 再调用 `prctl(PR_SET_DUMPABLE, 0)` 并施加 20–50 ms 的密码学随机启动抖动。
- `RiskAction.ALLOW` 使用 `LOW` 基础控制，`RiskAction.DEGRADE` 再按 `MEDIUM`/`HIGH` 逐级增强；环境结果没有拒绝动作，任何等级都不允许降低签名/容器校验，x86/x86_64 ABI 本身不能改变策略。
- payload 映射保留到 `PayloadMemoryHandle` 安全关闭；接口注释必须说明 ART 生命周期约束和残余可读窗口。

## Public Interfaces

- Native `SecureBuffer`、`PayloadMapping::seal()` 与 `PayloadMapping::capabilities()`。
- `ah.runtime.loader.PayloadRuntime.applyMemoryProfile(LoadedPayload payload, MemoryProfile profile)` 是 policy 到 native 的唯一控制入口；`MemoryProfile` 固定为 `BASELINE`、`ELEVATED`、`HIGH`，返回不可变 `MemoryProtectionCapabilities`。facade 校验 payload 所有权后才能访问内部 handle。
- `public final class MemoryProtectionReport`，以只读访问器公开 `boolean dontDump`、`long lockedBytes`、`boolean processDumpable` 和 `RiskLevel level`。
- `public final class MemoryControls`，通过模块内静态方法 `static MemoryProtectionReport apply(LoadedPayload payload, RiskReportV1 risk)` 映射 profile 并调用上述 facade；必须同时校验 `risk.action()` 与 `risk.level()` 的固定映射，失配时以稳定策略错误失败。
- 错误/能力码前缀 `AAH-RUNTIME-MEMORY-`。

## Security Constraints

- 清零必须可通过生成代码检查或专用测试证明未被优化；不得使用普通可消除的 `memset` 作为唯一措施。
- 映射地址、内存内容、密钥、完整路径和详细注入证据不得进入日志。
- `PR_SET_DUMPABLE` 仅影响当前应用进程，不修改系统设置或其他进程。
- 文档必须明确这些措施提高攻击成本但不能绝对阻止内存截取。

## Compatibility Requirements

- API 29 及以上、四个 Runtime ABI 行为一致。
- 不支持的 `madvise`/`mlock` 能力以报告位表达，核心类加载仍可继续。
- 不改变原始应用组件顺序、JNI 行为或 payload 类查找顺序。
- 高风险抖动最大 50 ms，不能无限等待或访问网络。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。

## Acceptance Criteria

- `./gradlew :runtime:native:test :runtime:native:connectedCheck :runtime:policy:test :runtime:policy:connectedCheck :runtime:bootstrap:connectedCheck` 退出码为 `0`。
- 四 ABI 的密钥与临时缓冲清零测试通过；异常注入后的所有可释放敏感区均为零。
- 支持设备的 `/proc/self/smaps` 显示 payload 映射不可写且排除 core dump；不支持能力时报告准确且应用正常启动。
- `ALLOW/LOW` 与 `DEGRADE/MEDIUM|HIGH` 映射严格启用既定控制，高风险额外延迟落在 20–50 ms，单独改变为 x86/x86_64 不改变等级；任何环境风险组合都不终止应用。
- 类加载稳定性测试证明 payload 映射未被过早擦除；安全文档没有“绝对防止”“无法提取”等不可验证承诺。

## Required Tests

- `SecureBuffer` 正常、异常、移动和重复释放的 Native 单元测试。
- 映射只读、`DONTDUMP`、`mlock` 上限、能力不可用和 `PR_SET_DUMPABLE` 测试。
- API 29 与最高受支持 API、四 ABI 的启动与延迟测试。
- 调试/注入高风险场景和 x86/x86_64 零额外风险回归测试。

## Required Evidence

- 命令、退出码、NDK/设备/API/ABI 信息。
- 清零检查、`smaps` 摘要、能力矩阵、锁页字节数和抖动分布。
- Native 库、AAR、测试报告和 `MemoryProtectionReport` 样例的 SHA-256。
- 独立安全复核对残余内存提取风险的签字结论。

## Likely Files

- `runtime/native/src/main/cpp/secure_buffer.h`
- `runtime/native/src/main/cpp/secure_buffer.cpp`
- `runtime/native/src/main/cpp/payload_memory.cpp`
- `runtime/native/src/main/cpp/memory_controls.cpp`
- `runtime/native/src/test/`
- `runtime/native/src/androidTest/`
- `runtime/policy/src/main/java/ah/runtime/MemoryControls.java`
- `runtime/policy/src/main/java/ah/runtime/MemoryProtectionReport.java`
- `runtime/native/src/main/java/ah/runtime/loader/MemoryProfile.java`
- `runtime/native/src/main/java/ah/runtime/loader/MemoryProtectionCapabilities.java`
- `runtime/policy/src/test/`
- `runtime/bootstrap/src/androidTest/`

## Dependencies and Blockers

M2-02 未冻结映射所有权或 M2-05 未冻结评分规则时不得接线。若只读或 `DONTDUMP` 破坏特定 ART 版本，必须以设备证据阻塞并评审最小兼容调整，不得回退到磁盘明文或宣称不存在风险。

## Agent Handoff Requirements

使用分支 `feat/m2-06-memory-dump-controls`，只处理 Issue `M2-06` 并仅创建一个对应 PR。交接必须列出每档控制、能力失败行为、清零和映射证据、性能影响、全部命令与退出码、产物 SHA-256、独立安全复核及仍可被进程控制攻击者绕过的风险。
