# Android App Hardening

面向标准 Android APK 的离线后处理加固工具项目。

项目目标是提供 DEX 加密、防二次打包、加固增量控制、DEX 内存截取成本提升、终端环境检测、四 ABI Runtime 与运行时签名校验。工具只产生新的未签名 APK，不接收私钥、keystore、alias 或密码，也不执行应用签名。

## 当前状态

仓库处于 M0 基础建设与可行性验证阶段，尚未提供可用的 APK 加固程序。开发任务必须从 [`docs/tasks/INDEX.md`](docs/tasks/INDEX.md) 领取，不应依据本 README 推测尚未实现的能力。

开发者和 Agent 从 [`docs/README_FIRST.md`](docs/README_FIRST.md) 开始。项目统筹状态以 [`HandOff.md`](HandOff.md) 为准。

## v0.1 边界

- 只接受独立 standalone APK，输入文件始终只读。
- 只产生一个新的未签名 APK，由用户在外部自行签名。
- 输入必须声明 `minSdk >= 29`。
- 目标应用为标准 Java/Kotlin，覆盖单 DEX、多 DEX、自定义 `Application` 和受支持的 `AppComponentFactory`。
- Runtime 构建 `armeabi-v7a`、`arm64-v8a`、`x86` 和 `x86_64`；不会把缺少 x86 客户 SO 的 ARM-only 应用变成 x86 应用。
- 不支持 AAB、APKS、Split APK、动态特性、Flutter、Unity、React Native、热修复、插件框架或已有第三方加固壳。
- DEX 防截取、环境检测和离线密钥隐藏只能提高攻击成本，不能绝对阻止有 root、Hook、定制 ART 或内核能力的攻击者。

完整产品合同见 [`docs/PRODUCT_REQUIREMENTS.md`](docs/PRODUCT_REQUIREMENTS.md)，安全边界见 [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md)。

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。
