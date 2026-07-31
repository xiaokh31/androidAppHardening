---
id: M0-01
title: 仓库初始化与远程安全落地
milestone: M0
status: planned
owner_role: qa-governance-agent
depends_on: []
required_skills:
  - plan-apk-hardening-change
security_sensitive: false
---

## Goal

建立可审计的公开仓库基线，在确认远程没有任何引用后，将 Apache-2.0 许可和最小治理文件作为唯一种子提交写入 `main`。

## Background

项目从空仓库开始。首次写入若覆盖已有远程历史将不可接受，因此远程非空是硬阻塞条件；种子提交只建立仓库边界，不包含 Host 或 Runtime 业务实现。

## Inputs

- 远程地址 `https://github.com/xiaokh31/androidAppHardening.git`。
- GitHub 已认证且对目标仓库具有写权限的工作环境。
- 本任务定义的五个种子文件及 Apache License 2.0 标准文本。

## Expected Outputs

- 名为 `origin` 的远程指向指定 HTTPS 地址。
- `main` 上只有一个标题为 `chore(repo): initialize repository` 的种子提交。
- 种子提交只包含 `README.md`、`LICENSE`、`.gitignore`、`.gitattributes`、`.editorconfig`。
- 文本包建立后补建的同编号 Issue，链接种子提交及验证证据；M0-01 是唯一允许直接初始化空远程 `main` 且没有任务分支/PR 的引导例外。

## In Scope

- 检查目标远程可访问性、可见性和引用状态。
- 初始化 Git、设置 `main`、配置 `origin`。
- 写入五个 UTF-8 文本文件并创建种子提交。
- 确认仓库为 public，默认分支为 `main`，禁止强推。

## Out of Scope

- 项目策划文档、任务卡、Skills、CI 或业务模块。
- 任何 APK、DEX、证书、签名文件或依赖二进制。
- 重写、合并或删除已存在的远程历史。

## Implementation Decisions

- 远程检查使用 `git ls-remote --heads --tags origin`；输出非空时立即标记任务 blocked，不执行 push。
- 许可证固定为 Apache-2.0，`README.md` 只描述项目目标、非目标和文档入口。
- 文本文件统一 UTF-8、LF；`.gitattributes` 固定 Markdown、YAML、JSON、Kotlin、Java、C/C++ 和脚本的行尾策略。
- 由于远程在本任务开始时没有默认分支或治理规则，种子提交直接推送到 `main`；这是一次性引导例外。M0-02 合并后所有任务（包括治理任务）都必须使用同编号分支、Issue 和唯一 PR，禁止援引本例外。
- 补建 Issue 标题固定为 `[M0-01] Repository bootstrap`，正文只链接种子提交、五文件清单与验证证据，不虚构任务分支或 PR。
- 禁止使用 `--force`、`--force-with-lease`、`git reset --hard` 或覆盖式远程操作。

## Public Interfaces

- Git 远程接口：`origin`。
- 默认分支：`main`。
- 提交标题：`chore(repo): initialize repository`。
- 仓库许可证标识：`Apache-2.0`。

## Security Constraints

- 认证令牌不得出现在命令参数、提交、日志附件或交接文本中。
- 提交前扫描五个文件，不得含私钥、证书、密码、客户路径或 APK 数据。
- 远程已有任意 branch 或 tag 都视为不可安全初始化，必须交由 `/root` 决策。

## Compatibility Requirements

- Git 操作必须能在 Windows PowerShell 和 Ubuntu shell 中复现。
- 文件名大小写与本任务完全一致。
- 克隆后的文本编码必须为 UTF-8，且不得出现 Unicode replacement character。

## Acceptance Criteria

1. `git remote get-url origin` 的标准输出严格等于指定远程地址，退出码为 `0`。
2. 初次写入前保存的 `git ls-remote --heads --tags origin` 输出为空；若不为空，任务状态只能为 blocked。
3. `git show -s --format=%s main` 输出 `chore(repo): initialize repository`。
4. `git diff-tree --root --no-commit-id --name-only -r main` 排序后严格等于五个种子文件名。
5. `git show main:LICENSE` 包含 `Apache License` 和 `Version 2.0, January 2004`。
6. `git status --porcelain` 为空，远程 `main` 与本地 `main` 指向同一 40 位提交 SHA。
7. GitHub 仓库可见性为 public；补建 Issue 链接种子提交并明确记录“一次性无 PR 引导例外”，不存在虚构任务分支或 PR。

## Required Tests

- 在 Windows 与 Ubuntu 各执行一次只读克隆，运行 `git fsck --full` 并确认退出码为 `0`。
- 对五个文件执行 UTF-8 解码、CRLF/LF 策略和敏感信息扫描。
- 使用 GitHub API 或 `gh repo view --json visibility,defaultBranchRef` 验证 public 与 `main`。

## Required Evidence

- 远程空状态检查命令、退出码和脱敏后的空输出。
- 种子提交 40 位 SHA、文件清单和 `git fsck --full` 结果。
- 两个测试环境的 OS、Git 版本、命令和退出码。
- 五个种子文件的 SHA-256。
- Issue 与种子提交链接，以及“一次性无 PR 引导例外”的说明；若任务 blocked，则提供远程引用名称而不修改远程。

## Likely Files

- `README.md`
- `LICENSE`
- `.gitignore`
- `.gitattributes`
- `.editorconfig`

## Dependencies and Blockers

- 目标远程必须存在、保持 public 且无 branch/tag。
- GitHub 认证或仓库写权限缺失时提交 blocked 交接。
- 远程非空时不得通过删除引用、变基或强推解除阻塞。

## Agent Handoff Requirements

- 本任务是唯一直接初始化空远程 `main` 的例外；完成后补建同编号 Issue 并链接种子提交，不创建或声称存在虚构的任务 PR。
- 完成状态必须列出执行命令、逐项退出码、测试环境、种子提交 SHA 和五个文件 SHA-256。
- 工作 Agent 不修改根 `HandOff.md`；将结构化交接包返回 `/root`。
- 发现远程状态与任务前提不一致时，仅提交 blocked 交接，不扩大任务范围。
