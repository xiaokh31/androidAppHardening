# ADR 0002: 仅输出未签名 APK

## Status

Accepted

## Context

任何 APK 内容修改都会使输入签名失效。若产品负责重新签名，就必须接收私钥、keystore、alias、密码、硬件签名权限或远程签名令牌。这会把项目从后处理工具扩大为高敏感签名系统，并增加凭据泄露、日志暴露、错误身份签名和权限治理风险。

Runtime 又需要验证安装应用的 signer 身份。输入 APK 已包含公开证书信息，因此身份策略可以在不接触私钥的情况下建立。

## Decision

产品只输出新的未签名 APK，不实现任何签名能力。

Host 必须：

- 使用标准 Android 签名验证库验证输入；
- 要求唯一当前 signer，并记录其证书 SHA-256；
- 移除因修改而失效的 JAR signature entries 和 APK Signing Block；
- 输出 `signing.required=true`、`signing.performed=false`；
- 验证最终文件确实未签名且可被标准工具签名；
- 不提供任何私钥、keystore、alias、密码或签名服务参数。

使用方必须在产品外以输入 APK 的同一当前 signer 签署输出。Runtime 在业务代码加载前比较安装 signer 与嵌入的允许证书 SHA-256。v0.1 不支持借此流程更换签名身份。

## Consequences

积极结果：

- 产品不成为签名秘密的保管者或处理者；
- Host 和 CI 无需签名权限；
- 安全边界、日志审查和开源发布更简单；
- signer 身份验证与签名操作明确分离。

代价：

- 使用方必须增加外部签名与签名后验证步骤；
- 使用不同证书签名的输出会在 Runtime 启动时被拒绝；
- 产品不能交付可直接安装的最终 APK；
- 端到端设备测试只能使用 fixture 专用测试身份，并与产品 CLI 隔离。

## Rejected Alternatives

- CLI 接收 keystore：扩大秘密处理面，并产生凭据泄露风险。
- 调用云端或硬件签名服务：引入网络、权限和厂商依赖，违反离线边界。
- 自动生成自签名证书：破坏应用升级身份，不能用于真实发行。
- 保留原签名块：修改后签名必然无效，容易误导使用方。
- 允许任意新 signer：削弱 Runtime 对重签篡改的检测。

## Security Impact

该决策移除最敏感的私钥处理面。公开证书 digest 可以记录并嵌入，不属于签名秘密。攻击者仍可修改 Runtime 后以新证书重签，因此 signer 绑定只提高篡改成本，不是不可绕过的信任根。

测试或文档不得把 fixture 测试密钥路径复制到产品参数。日志和 JSON 报告只记录证书 SHA-256 与验证状态。

## Compatibility Impact

输入必须具有可验证签名和唯一当前 signer。允许可验证的 signer 轮换历史，但输出必须仍由输入的当前 signer 签署。多当前 signer、无签名或签名无效输入在 v0.1 被拒绝。

## Verification

- 静态检查确认产品 CLI 不存在签名秘密参数。
- 集成测试确认输出没有有效 v1/v2/v3 签名。
- 标准 `apksigner` 可在产品外签署输出并验证成功。
- 使用输入同一当前 signer 签名后，Runtime 启动通过。
- 使用不同 signer 签名后，Runtime 在业务探针执行前拒绝。
- 日志、报告和发布包扫描不含签名秘密或 keystore 内容。
