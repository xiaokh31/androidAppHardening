---
id: M1-06
title: Host CLI 编排与 JSON 报告
milestone: M1
status: planned
owner_role: host-pipeline-agent
depends_on:
  - M1-01
  - M1-02
  - M1-03
  - M1-04
  - M1-05
required_skills:
  - implement-apk-postprocessor
  - validate-protected-apk
security_sensitive: false
---

## Goal

提供单一离线 `protect` 命令，将 M1 各阶段按失败关闭顺序编排，并在成功或失败时输出稳定、可机器处理且不泄露敏感数据的 JSON 报告。

## Background

各 Host 模块只有在统一入口下才能保证输入只读、目标路径隔离、错误分类、清理和证据一致。CLI 不能吸收签名职责；人类诊断与自动化合同必须分别使用 stderr 和 JSON。

## Inputs

- 已实现并通过各自验收的 M1-01 至 M1-05 模块。
- 三个必填用户路径：input APK、全新 unsigned output、全新 JSON report。
- 固定版本 RuntimeBundle 与本地工作目录根。

## Expected Outputs

- `host/cli` 可运行入口及 `android-app-hardening` 启动脚本。
- 唯一业务命令 `protect`。
- JSON report schema version `1`、schema fixture 和稳定 exit/error mapping。
- 端到端正常、拒绝、故障、取消和跨平台测试。

## In Scope

- 参数/路径预检、阶段编排、受限临时目录、取消处理和清理。
- inspect → signer → manifest → container → repack → verify → publish 顺序。
- 成功/失败 JSON 报告、stderr 摘要和空 stdout。
- 输入末次 hash、输出/report 发布及回滚语义。
- `--help`、`--version` 和非法参数行为。

## Out of Scope

- keystore、private key、alias、密码、签名服务或调用 `apksigner` 签名。
- GUI、daemon、网络 API、批量目录、AAB/Split 或多 APK。
- 修改各 M1 模块已冻结的解析/格式/打包算法。

## Implementation Decisions

- v0.1 唯一业务命令固定为 `android-app-hardening protect --input input.apk --output output-unsigned.apk --report report.json`，三个参数必填且每项恰好一次。
- 不提供 overwrite/force、signing、key、keystore、alias、password、network、plugin 或 arbitrary temp path 选项；output/report 已存在即失败。
- input/output/report 两两不得解析为同一路径或链接身份；output 与 report 的 parent 必须已存在、可写且分别与 input 隔离。
- pipeline stage ID 固定为 `inspect`、`signer`、`manifest`、`container`、`package`、`verify`、`publish`，只能按该顺序进入。
- exit code 固定为：`0` success、`2` usage、`10` INPUT/COMPAT、`11` SIGNER、`12` AXML、`13` CONTAINER、`14` PACKAGE、`15` OUTPUT、`70` INTERNAL。
- JSON `schema_version=1`；顶层固定为 `tool`、`result`、`input`、`output`、`application`、`signing`、`dex`、`abi`、`compatibility`、`stages`、`size`、`errors`。字段类型和必填性写入 `docs/specs/REPORT_V1.md`。
- 路径只记录用户参数的 basename 和 SHA-256 路径 token，不记录 absolute/real path；durations 为非负 integer milliseconds，时间为 UTC RFC 3339。
- 成功时先完成 output/report 两个临时文件和全部验证，再原子发布 output，最后原子发布 success report；report 发布失败时删除本次新发布 output并返回 `15`。失败时尽最大可能只发布 failure report，不发布 output。
- stdout 保持空；stderr 只输出一行 `result/error_code/report_basename` 摘要。捕获异常通过稳定 code 映射，默认未知异常为 `INTERNAL_UNEXPECTED`/`70`。
- 分支名固定为 `feat/m1-06-cli-and-json-report`，Issue 标题固定为 `[M1-06] CLI and JSON report`，仅允许一个关联 PR。

## Public Interfaces

- 命令：`android-app-hardening protect --input <apk> --output <apk> --report <json>`。
- 全局只读入口：`android-app-hardening --help` 与 `android-app-hardening --version`。
- report schema：`docs/specs/REPORT_V1.md`，`schema_version: 1`。
- `result.status` 只允许 `success`、`rejected`、`failed`；`signing.required=true`、`signing.performed=false` 固定。
- `errors[]` 包含 `code`、`stage`、`message_id`，自动化不得依赖本地化 message。

## Security Constraints

- CLI parser 和生产 classpath 不得接受或引用签名 secret；环境变量也不作为隐藏参数来源。
- 不记录完整环境变量、DEX 内容、key/envelope material、证书本体、用户绝对路径或 stack trace 到普通 report。
- 临时目录随机且 owner-only；shutdown hook/取消只清理本次创建且已验证位于工作根下的路径。
- 不执行 APK 内容、entry 名、report 字段或 stderr 中的任何命令。
- output 只有 verifier 成功、input final SHA 一致后才可发布。

## Compatibility Requirements

- Windows x64 与 Ubuntu x64 对同一 fixture 给出相同 status、stage 顺序、error code、compatibility 分类和非随机 report 字段。
- 支持 v0.1 接受范围；不支持输入在 output 写入前 rejected。
- 文件名含空格和合法非 ASCII 字符时正常；路径大小写/链接差异按平台真实文件身份处理。
- JSON 为 UTF-8、无 BOM、LF、RFC 8259 有效且字段顺序固定以便审计。

## Acceptance Criteria

1. `./gradlew :host:cli:test :host:cli:integrationTest` 与 `.\gradlew.bat :host:cli:test :host:cli:integrationTest` 均退出 `0`。
2. 正常命令退出 `0`、stdout 空、stderr 为规定一行、output/report 均存在；output 与 report SHA-256 与 report 自身字段一致。
3. report 通过 JSON Schema 验证，包含工具/schema 版本、结果、脱敏路径、input/output/container SHA-256、package/SDK/DEX/ABI、signer、compatibility、阶段耗时、签名状态和大小增量。
4. 每个错误类别 fixture 返回固定 exit code/error code/stage，output 不存在且尽最大可能生成合法 failure report。
5. input/output/report alias、预存在目标、未知/重复/缺失参数均在写入前拒绝；输入 SHA-256 始终不变。
6. 对 container/package/verify/publish 注入失败或 Ctrl+C 后，无残留临时明文、无成功 output、句柄关闭；report failure 不含 stack trace 或绝对路径。
7. CLI/API/帮助文本扫描不存在签名 secret 选项或签名承诺，report 固定 `signing.required=true`、`signing.performed=false`。
8. Windows/Ubuntu 规范化 reports 去除时间、耗时和随机产物 hash 后结构相同，顶层状态与错误语义完全相同。

## Required Tests

- CLI parser、exit mapping、stage state machine 和 JSON schema 单元测试。
- 正常单/多 DEX、Application/factory、Java-only/各 ABI 端到端测试。
- 每类兼容性/签名/AXML/container/package/output failure test。
- 路径 alias、文件名编码、预存在目标、权限、disk-full、short write、atomic move/report publish failure injection。
- Ctrl+C/shutdown cleanup、日志/report 敏感信息和 input immutability tests。
- Windows/Ubuntu normalized-report equivalence tests。

## Required Evidence

- 两个平台所有命令、退出码、OS/JDK/tool versions 与 console capture。
- input/output/container/report、schema 和 RuntimeBundle 的 SHA-256。
- exit/error/stage 矩阵、normalized report diff 和 failure cleanup 清单。
- 提交 SHA、Issue 与唯一 PR 链接；若任何上游模块未满足合同则列为 blocker。

## Likely Files

- `host/cli/src/main/kotlin/`
- `host/cli/src/test/kotlin/`
- `host/cli/src/integrationTest/`
- `docs/specs/REPORT_V1.md`
- `docs/evidence/M1-06/`

## Dependencies and Blockers

- M1-01 至 M1-05 的公开接口、错误码和 verifier 必须全部稳定。
- success report 与 output 无法在目标文件系统按规定回滚时该运行失败，不放宽为部分成功。
- 新增 CLI 选项、report breaking change 或签名责任变化必须由 `/root` 批准并更新 ADR/schema。

## Agent Handoff Requirements

- 本任务固定使用分支 `feat/m1-06-cli-and-json-report`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、平台、input/output/report SHA-256、exit matrix 和跨平台 diff。
- worker 不修改根 `HandOff.md`，不顺手实现 release packaging、GUI 或签名流程。
- 上游合同冲突时提交 blocked 交接并指明接口/错误码差异，不在 CLI 内做兼容分叉。
