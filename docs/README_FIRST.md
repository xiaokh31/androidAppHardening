# 开始开发前必读

本仓库用于开发一个离线 APK 后处理工具：接收一个已经签名的独立 APK，生成一个包含加固启动层与加密 DEX 容器的新 APK。输入文件始终按只读方式处理，输出文件始终未签名，产品不接收、不保存、也不调用任何私钥或签名凭据。

## 固定阅读顺序

每个 Agent 开始任务前必须按以下顺序阅读：

1. `AGENTS.md`
2. `HandOff.md`
3. 本文件
4. `docs/tasks/INDEX.md` 中对应的任务卡
5. 任务卡引用的 ADR、`docs/ARCHITECTURE.md` 与 `docs/THREAT_MODEL.md`
6. 任务卡声明的 `.agents/skills/*/SKILL.md`

任务卡、架构文档和 ADR 如有冲突，优先级为：已接受的 ADR、架构文档、任务卡。发现冲突时停止扩大实现范围，提交结构化阻塞交接，由 `/root` 决策。

## 不可变产品边界

- 仅处理单个、独立安装的 APK。
- 输入 APK 必须保持字节级不变；处理器不得覆盖、移动、重命名或原地修复输入。
- 输出路径必须与输入路径不同，输出是新的未签名 APK。
- 工具不得接收私钥、keystore、alias、密码、签名令牌或远程签名权限。
- v0.1 仅接受 `minSdk >= 29` 的应用。
- 支持标准 Java/Kotlin 应用、单 DEX、多 DEX、自定义 `Application` 和自定义 `AppComponentFactory`。
- Runtime 提供 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种 ABI。
- Runtime 的四 ABI 覆盖不等于转换原应用的原生库；ARM-only 应用仍然不能在仅支持 x86 的设备上运行。
- v0.1 不支持 AAB、Split APK、Flutter、Unity、React Native、热修复框架或已存在加固壳的 APK。
- DEX 内存截取防护、反调试和环境检测只提高攻击成本，不构成绝对防御。
- “APK 大小优化”仅指测量并控制加固增量，不承诺输出 APK 小于输入 APK。

## 开发工作流

一个任务卡对应一个 GitHub Issue、一个分支和一个 Pull Request。Agent 每次只领取一张任务卡，只修改该卡授权的文件和接口，不顺带完成相邻任务。

开始开发时：

1. 确认任务依赖均已完成，或已由 `/root` 明确豁免。
2. 从最新 `main` 创建符合 `docs/DEVELOPMENT.md` 的分支。
3. 将任务卡中的验收条件转成可执行测试。
4. 在安全敏感任务中预先指定独立安全复核者。

结束开发时：

1. 运行任务卡要求的全部测试。
2. 记录命令、退出码、操作系统、JDK、Android API/ABI 环境和产物 SHA-256。
3. 返回符合 `docs/HANDOFF_SPEC.md` 的 Worker 交接包。
4. 不直接修改根 `HandOff.md`；根交接文件仅由 `/root` 整合。

## 文档导航

- [项目计划](PROJECT_PLAN.md)
- [产品需求](PRODUCT_REQUIREMENTS.md)
- [系统架构](ARCHITECTURE.md)
- [威胁模型](THREAT_MODEL.md)
- [开发规范](DEVELOPMENT.md)
- [测试策略](TEST_STRATEGY.md)
- [工具链与来源治理](TOOLCHAIN_AND_PROVENANCE.md)
- [交接规范](HANDOFF_SPEC.md)
- [路线图](ROADMAP.md)
- [兼容性矩阵](COMPATIBILITY_MATRIX.md)
- [架构决策记录](adr/0001-apk-postprocessing-only.md)
- [开发任务索引](tasks/INDEX.md)

## 合并门禁

Pull Request 只有在以下条件全部满足时才能合并：

- 范围、接口和行为与任务卡一致。
- 自动化测试通过，验收证据完整且可复现。
- 输入只读与未签名输出约束没有被破坏。
- 没有真实客户 APK、明文 DEX、证书私钥或签名凭据进入仓库或日志。
- 依赖来源和许可证已登记，构建工具与 CI Action 已固定版本。
- 安全敏感改动完成独立安全复核。
- 文档链接、任务依赖、Skill 结构和交接格式校验通过。
