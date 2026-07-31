---
schema_version: 1
project: androidAppHardening
handoff_id: HO-20260731-080900
updated_at: 2026-07-31T08:09:00+08:00
updated_by: /root
state: active
source_branch: docs/m0-project-package
base_commit: 1e5264e208ac37f3b8bd5331f59ed5a6d5eef78b
working_tree: clean
current_milestone: M0
active_task: M0-02
next_owner: /root
---

# Project HandOff

## Objective

以只读独立 APK 为输入，开发一个离线后处理工具，将业务 DEX 转换为版本化认证加密容器并注入四 ABI Android Runtime，最终只生成新的未签名 APK。当前工作仅完成 M0-02 治理、任务合同与交接门禁，不包含 Host 或 Android Runtime 业务实现。

## Current State

- M0-01 仓库种子已经位于 `main`，提交为 `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`。
- M0-02 的核心文档、6 份 ADR、25 张任务卡、6 个项目 Skills、HandOff 工具和双平台治理工作流正在 [PR #26](https://github.com/xiaokh31/androidAppHardening/pull/26) 审查。
- 当前分支为 `docs/m0-project-package`；本 HandOff 随待提交工作树进入该 PR，提交后工作树预期为 clean。
- M0-03 尚未分配，必须在 PR #26 合并且 M0-02 证据完成后领取。

## Active Workstreams

| Task | Owner | Branch | Status | Dependencies | Next checkpoint |
|---|---|---|---|---|---|
| M0-02 | `/root` | `docs/m0-project-package` | review | M0-01 | 提交治理工具并取得 Windows/Ubuntu CI 证据 |

## Decisions and Invariants

- [ADR-0001](docs/adr/0001-apk-postprocessing-only.md)：仅处理已签名的独立 APK；输入始终只读。
- [ADR-0002](docs/adr/0002-unsigned-output-only.md)：输出始终是新的未签名 APK；产品不接收或使用私钥、keystore、alias 或密码。
- [ADR-0003](docs/adr/0003-api29-public-classloader-hook.md)：v0.1 要求 `minSdk >= 29`，只采用公开 `AppComponentFactory` ClassLoader 接入。
- [ADR-0004](docs/adr/0004-versioned-encrypted-dex-container.md)：AHDC v1 采用固定字节合同、zlib-wrapped DEFLATE level 9 与 AES-256-GCM。
- [ADR-0005](docs/adr/0005-runtime-abi-policy.md)：Runtime 覆盖 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，但不把 ARM-only 应用转换为 x86 应用。
- [ADR-0006](docs/adr/0006-offline-key-protection-boundary.md)：离线密钥隐藏、内存截取控制、反调试和环境检测只提高攻击成本，不能构成绝对防御。
- “APK 大小优化”只表示测量并控制加固增量，不保证输出小于输入。

## Changes Since Previous Handoff

- `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`：建立 Apache-2.0 公共仓库种子和基础文本规范。
- `1e5264e208ac37f3b8bd5331f59ed5a6d5eef78b`：加入核心策划文档、ADR、25 张任务卡、Agent 角色规则和 GitHub 模板。
- 待提交工作树：加入 6 个项目 Skills、严格 HandOff 校验及负向测试、项目包校验、不可变提交树哈希工具和双平台治理工作流，并修正启动早期公开 API、模块边界、依赖图与二进制协议合同。

## Verification Evidence

### M0-01 immutable seed manifest

- task_id: `M0-01`
- git_commit: `1fc5fb6380b97ba2a2a54df0409429f4730f6d77`
- command: `node tools/governance/hash-project-package.mjs --commit 1fc5fb6380b97ba2a2a54df0409429f4730f6d77`
- exit_code: `0`
- environment: `Windows NT 10.0.19045; Git 2.52.0; Node.js 24.12.0`
- timestamp: `2026-07-31T08:08:37+08:00`
- artifact: `immutable commit-tree manifest over 5 files, excluding HandOff.md`
- sha256: `2db831de0981c715a62df6007d8c2c9219069940d5f951950a6f5b5ca41b977f`
- result: `PASS; the root commit contains only README.md, LICENSE, .gitignore, .gitattributes, and .editorconfig`

## Blockers and Required Approvals

None

## Ordered Next Actions

1. 分配并完成 M0-03 工具链与 CI。
   - Owner: `unassigned`；前置条件：PR #26 合并且 M0-02 验证完成；证据：双平台构建命令、退出码、工具版本和产物 SHA-256。
2. 并行启动 M0-04 ClassLoader PoC。
   - Owner: `unassigned`；前置条件：M0-03 通过；证据：API 29/36 设备测试、失败用例和 PoC 产物 SHA-256。
3. M0-04 通过后启动 M0-05 兼容性 PoC。
   - Owner: `unassigned`；前置条件：M0-04 公开 API 门禁通过；证据：Application、Factory、Provider、multidex 与 JNI 兼容矩阵。
4. M0 门禁通过后冻结容器接口，再并行启动 M1 与 M2。
   - Owner: `/root`；前置条件：M0-03 至 M0-05 验收；证据：冻结合同、关联 ADR 和独立安全复核记录。

## Relevant Files and Artifacts

- `AGENTS.md`
- `HandOff.md`
- `docs/README_FIRST.md`
- `docs/ARCHITECTURE.md`
- `docs/THREAT_MODEL.md`
- `docs/HANDOFF_SPEC.md`
- `docs/tasks/INDEX.md`
- `.agents/skills/coordinate-project-handoff/SKILL.md`
- `.agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs`
- `.github/workflows/governance.yml`
- `tools/governance/validate-project-package.mjs`
- `tools/governance/hash-project-package.mjs`
- `tools/governance/test-handoff-validator.mjs`

## Resume Checklist

1. 确认当前分支、HEAD 和工作树状态，并按固定顺序读取治理文件。
2. 运行 `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`。
3. 运行 `node tools/governance/validate-project-package.mjs --require-governance-only` 与 `node tools/governance/test-handoff-validator.mjs`。
4. 核对 PR #26 两个平台检查、Issue #1 至 #25 映射和 M0-02 独立审计结论。
5. 仅在全部门禁通过并合并后，把状态更新为 `ready`、活动任务更新为 `M0-03`。

## Handoff Sign-off

- generated_by: `/root`
- generated_at: `2026-07-31T08:09:00+08:00`
- validation_command: `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean`
- validation_result: `PASS expected after local validation`
