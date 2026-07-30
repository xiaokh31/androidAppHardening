# 开发规范

## 1. 开始工作

Agent 必须完成 [开始开发前必读](README_FIRST.md) 的阅读顺序，并确认：

- 当前任务卡状态允许开始；
- `depends_on` 中的任务已完成；
- 当前 `main` 与根 `HandOff.md` 一致；
- 分支只承载一张任务卡；
- 安全敏感任务已有独立复核安排。

Worker 不修改根 `HandOff.md`。发现文档、环境与任务卡冲突时，保留现场并提交阻塞交接，不扩展任务范围。

## 2. 分支与提交

分支格式：

```text
feat/m1-01-untrusted-apk-inspector
fix/m2-03-signer-verification
docs/m0-02-handoff-rules
spike/m0-04-classloader-poc
```

提交信息使用英文 Conventional Commit：

```text
feat(host): inspect untrusted apk entries
fix(runtime): reject signer mismatch before dex load
test(matrix): cover custom provider startup
docs(adr): define container version policy
```

一个提交应表达一个可审查意图。不得混入格式化全仓库、升级无关依赖或相邻任务实现。

## 3. 目录与模块边界

目标模块：

| 目录 | 所有者 | 允许职责 |
| --- | --- | --- |
| `host/cli` | host-pipeline-agent | 参数、编排、报告和退出码 |
| `host/apk-inspector` | host-pipeline-agent | 不可信 APK、兼容性与 signer 输入模型 |
| `host/axml` | host-pipeline-agent | 二进制 Manifest 读取、白名单修改和 diff |
| `host/container` | host-pipeline-agent | 容器序列化与认证加密 |
| `host/repacker` | host-pipeline-agent | 新 APK 写出、对齐和输出验证 |
| `runtime/bootstrap` | runtime-security-agent | Shell Factory 与公开 ClassLoader 接入 |
| `runtime/native` | runtime-security-agent | 容器解析、密钥恢复和内存解密 |
| `runtime/policy` | runtime-security-agent | signer、完整性和环境风险决策 |
| `fixtures/android` | qa-governance-agent | 仅含合成代码的兼容性 fixture |
| `tools/validation` | qa-governance-agent | 文档、结构、矩阵和发布校验 |

跨模块合同只能通过已记录接口修改。Host 不引用 Runtime 内部实现；Runtime 不读取 Host 报告作为启动依赖。

## 4. 编码要求

### 4.1 通用

- 文件使用 UTF-8，无替换字符。
- 生产日志采用结构化事件 ID，不记录密钥、DEX 内容、签名凭据或用户绝对路径。
- 所有外部输入有长度和计数上限。
- 失败路径释放句柄、清理临时文件并清零敏感缓冲。
- 公共接口、容器字段、错误码和安全决策必须有测试。

### 4.2 Kotlin/Java

- 使用明确 nullability，避免以异常作为正常解析分支。
- Android Runtime 只使用 `minSdk 29` 可用公开 API；更高 API 行为使用显式版本分支。
- 不使用 hidden API、反射修改 Framework 私有字段或动态下载代码。
- 启动路径避免静态初始化执行业务逻辑。

### 4.3 Native

- 解析长度使用固定宽度无符号类型和 checked arithmetic。
- JNI 边界验证数组长度、异常状态、local/global reference 生命周期。
- 密钥与明文缓冲不得进入普通日志；使用可验证不会被优化移除的清零方式。
- 禁止将解密 DEX 写入文件系统。
- 四 ABI 共享同一源代码与导出符号清单，差异通过编译配置表达。

### 4.4 Host 文件处理

- 输入以只读共享语义打开。
- 不按 ZIP 条目名直接创建文件。
- 临时目录必须随机、受限且位于明确工作根下。
- 输出采用临时文件、独立重读验证和原子发布。
- 成功和失败都重新计算输入 SHA-256。

## 5. 测试先行要求

实现前将任务卡的 Acceptance Criteria 映射为测试名称。最低要求：

- 正常路径单元测试；
- 每个稳定错误码至少一个负面测试；
- 安全边界的篡改或畸形输入测试；
- 改动涉及 Android 启动时提供设备或模拟器集成测试；
- 改动涉及跨平台 Host 时提供 Windows 与 Ubuntu 证据；
- 修复缺陷时先添加能稳定复现的回归测试。

测试不得使用真实客户 APK。fixture 必须由仓库源码构建，证书只允许使用公开、专用于测试且无生产价值的固定测试身份。

## 6. 依赖变更

新增或升级依赖前：

1. 说明为何标准库或现有依赖不足。
2. 核对官方来源、许可证、维护状态和已知漏洞。
3. 固定版本及可用的校验和。
4. 更新 dependency verification、SBOM 输入和 `THIRD_PARTY_NOTICES.md`。
5. 对解析、加密、签名或 Native 依赖安排安全复核。

不得从临时网盘、个人构建附件或未固定分支引入二进制。

## 7. Pull Request

PR 描述必须包含：

- 任务卡与 Issue；
- 范围内变更和明确未做事项；
- 接口或 ADR 影响；
- 风险及回滚方式；
- 测试命令、退出码和环境；
- 产物 SHA-256；
- 安全敏感性与复核者；
- Worker 交接包链接或正文。

PR 合并前更新到最新 `main` 并重新运行受影响测试。并行 PR 按 `/root` 指定顺序依次更新和合并，避免使用过期 HandOff 或接口。

## 8. Definition of Done

任务只有在以下条件全部满足时标记 `done`：

- 任务卡全部验收条件可观察地通过。
- 所有必需测试成功，命令和退出码已记录。
- 记录操作系统、CPU 架构、JDK、Android API/ABI 及相关工具版本。
- 记录关键输入、输出、报告或发布产物的 SHA-256。
- 没有超出任务范围的未解释改动。
- 文档、公共接口和错误码与实现同步。
- 安全敏感任务完成独立复核。
- Worker 交接包通过 [交接规范](HANDOFF_SPEC.md) 校验。

## 9. 禁止事项

- 覆盖或原地修改输入 APK。
- 增加签名参数、读取 keystore 或请求密码。
- 提交真实客户 APK、私钥、凭据、明文业务 DEX 或用户目录路径。
- 使用隐藏 API 绕过 Android 生命周期。
- 将不支持组合静默降级为“尽力处理”。
- 以关闭测试、吞掉异常或放宽认证来通过 CI。
- 未经 ADR 修改改变容器格式、密钥边界、ClassLoader 接入或 ABI 策略。
