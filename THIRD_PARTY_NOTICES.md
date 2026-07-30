# Third-Party Notices

## 当前状态

本治理文本包没有复制或安装第三方 Agent Skill，也没有引入 Host 或 Runtime 业务依赖。项目专用 Skills 从零编写并随本仓库以 Apache-2.0 发布。

JADX、Apktool 和 Android SDK 工具仅作为候选验证工具记录，不随当前仓库分发。其固定版本、来源、哈希和许可证必须在实际采用任务中写入 `docs/TOOLCHAIN_AND_PROVENANCE.md`。

## 登记要求

任何新增库、二进制、源码、生成器、GitHub Action、Agent Skill 或测试资产必须记录：

- 名称、版本和不可变来源。
- 上游项目和固定提交或 SHA-256。
- 许可证、版权和 NOTICE 义务。
- 在本项目中的用途与分发方式。
- 本地修改和安全审计日期。
- 负责 Issue 和批准者。

未完成来源、许可证与哈希登记的第三方内容不得进入发布产物。
