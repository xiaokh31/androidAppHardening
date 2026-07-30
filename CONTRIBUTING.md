# Contributing

## 开始任务

1. 按 `docs/README_FIRST.md` 的顺序阅读项目资料。
2. 在 `docs/tasks/INDEX.md` 中选择一个依赖已满足的任务。
3. 确认对应 GitHub Issue 未被其他人占用。
4. 从最新 `main` 创建分支：

```text
feat/m1-01-untrusted-apk-inspector
fix/m2-03-signer-verification
docs/m0-02-handoff-rules
spike/m0-04-classloader-poc
```

5. 在 Issue 中记录负责人和分支。不得同时领取第二个任务。

## 开发要求

- 遵守 `AGENTS.md`、任务卡、ADR 和对应 Skill。
- 只修改任务拥有的模块或文件。
- 使用合成 fixture；APK、报告和临时测试证书留在忽略目录。
- 产品模块不得实现签名；安装测试只能在集成测试层生成一次性证书。
- 新增依赖必须固定版本，更新依赖锁、校验元数据、许可证记录和供应链说明。
- 公共 CLI、报告 schema、Payload 格式、Signer 语义、Manifest 改写或 ABI 策略变化必须有 ADR。

## 提交与 PR

提交采用 Conventional Commits，例如：

```text
feat(host): inspect untrusted APK structure
fix(runtime): reject mismatched signer before decrypt
test(qa): add multidex tamper fixture
docs(adr): freeze payload container v1
```

PR 必须：

- 只关联一个任务 Issue。
- 说明行为变化、边界、安全影响和兼容性影响。
- 列出全部验证命令与退出码。
- 附产物 ID、SHA-256 或可复现的测试证据。
- 完成任务卡的全部验收项。
- 对 `security_sensitive: true` 任务取得独立安全复核。
- 向协调者提交工作交接包；工作 Agent 不编辑根 `HandOff.md`。

## 合并

只允许通过通过必需检查的 PR 合并到 `main`。禁止强推和删除 `main`。项目有第二位审阅者后，安全与生产代码 PR 至少需要一名非作者批准。
