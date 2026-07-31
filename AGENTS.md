# Repository Agent Rules

本文件是仓库内所有开发 Agent 的最高级项目规则。任务卡、Skill 或聊天指令与本文件冲突时，停止工作并交由项目协调者裁决。

## 必读顺序

1. `AGENTS.md`
2. `HandOff.md`
3. `docs/README_FIRST.md`
4. 当前 `docs/tasks/<task>.md`
5. 任务引用的架构、威胁模型、测试文档与 ADR
6. `required_skills` 指定的 `.agents/skills/<skill>/SKILL.md`

## 产品不变量

- v0.1 只处理独立 APK。AAB、APKS、split、dynamic feature、Flutter、Unity、React Native、热修复、插件框架和已有加固壳均不在范围内。
- 输入 APK 只读；不得原地覆盖、改名替换或在失败后留下冒充成功的输出。
- 输出必须未签名。生产模块不得接收、读取或传递私钥、keystore、alias、签名密码，也不得调用签名工具。
- 集成测试可在忽略的构建目录生成一次性非生产测试证书，只用于安装测试副本；该能力不得进入产品模块、分发包或版本库。
- v0.1 要求输入 `minSdk >= 29`，不得静默提高输入的最低 SDK。
- Runtime 可构建四个 ABI，但不得把壳的 x86 能力宣传为客户 ARM-only 应用的 x86 兼容能力。
- 反 dump、反调试、环境检测、签名校验和离线密钥隐藏均为成本防御，不得使用“无法破解”“绝对防护”等表述。

## 工作范围

- 一个 Agent 同时只领取一个任务卡；除 M0-01 对全空远程直接创建首个 `main` 的一次性引导例外外，一个任务对应一个 Issue、一个工作分支和一个 PR。M0-01 必须补建 Issue 并链接种子提交，不得虚构分支或 PR；M0-02 的首个治理文本包按用户预先批准的 `docs/m0-project-package` 分支执行，此后不得援引这两个引导例外。
- 不实现相邻任务，不借安全修复进行无关重构，不改变任务卡外的公共接口。
- 发现任务缺失关键决策、依赖不成立或真实环境冲突时，提交 `blocked` 交接包，不自行扩大范围。
- 跨模块、容器格式、签名、ABI、兼容性、安全策略或难以撤销的决策必须先新增或更新 ADR。
- 多 Agent 并行时遵守协调者给出的文件所有权；不得改写其他 Agent 的未完成文件。

## 安全与数据

- 只使用仓库生成的合成 fixture 或有明确授权的 APK。
- 不提交、上传或粘贴客户 APK、客户路径、明文 DEX、反编译源码、生产证书、私钥、密码、令牌或凭据。
- APK、ZIP、AXML、DEX、证书和所有长度字段均视为不可信输入。
- 禁止引入自制密码算法、动态依赖版本、未锁定下载、未审计 Skill 或在构建时执行未知远程脚本。
- 使用外部 Skill 前必须运行 `audit-third-party-skill` 流程；审计完成前不得安装或执行。

## Git 与提交

- 开始前检查分支、HEAD、远程和工作区；保留用户与其他 Agent 的既有改动。
- 禁止 `git reset --hard`、强推、删除未确认分支、批量覆盖或不经检查暂存整个混合工作区。
- 分支使用 `feat/`、`fix/`、`docs/`、`chore/` 或 `spike/` 加任务 ID；仅 M0-02 初始治理文本包使用预先批准的 `docs/m0-project-package`。
- 提交与 PR 标题使用 Conventional Commits。一个 PR 只解决一个任务。
- 依赖升级、安全逻辑和格式化重写不得混入同一个 PR。

## 验证与完成

- 每项功能同时覆盖正向、篡改、兼容和失败路径。
- 完成状态必须附命令、退出码、操作系统、工具链版本、时间戳、提交和产物哈希。
- 工具缺失时报告准确阻塞，不自动下载未在 `docs/TOOLCHAIN_AND_PROVENANCE.md` 固定的工具。
- 测试生成物只能进入忽略的 `build/`、`artifacts/` 或临时目录。
- 完成前检查 diff、敏感信息、UTF-8 替换字符、文档链接和任务验收表。
- 修改治理文档、ADR 或任务卡后运行：

```text
node tools/governance/validate-project-package.mjs
```

## 交接

- 根 `HandOff.md` 只由 `/root` 项目协调者修改；需要更换协调者时先由用户更新本规则和交接 schema。
- 工作 Agent 使用 `.agents/skills/coordinate-project-handoff/assets/worker-handoff-template.md` 返回结构化交接包。
- 协调者必须核验实际 Git 状态、命令和产物后再整合 HandOff；不得仅根据聊天声明标记完成。
- `HandOff.md` 必须通过：

```text
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict
```

## Skill 路由

- 规划或改变范围：`plan-apk-hardening-change`
- Host APK 后处理：`implement-apk-postprocessor`
- Android/Native Runtime：`implement-runtime-protection`
- 加固产物验证：`validate-protected-apk`
- 项目交接：`coordinate-project-handoff`
- 外部 Skill 审计：`audit-third-party-skill`
