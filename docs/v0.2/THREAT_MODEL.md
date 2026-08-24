# v0.2 威胁模型

## 1. 资产

- 输入 APK 的完整字节、signer 与原始组件行为。
- 原始 DEX 内容、AHDC v2 密钥材料和认证元数据。
- Runtime Native 映射、已验证 payload session 和启动顺序。
- product tuple、证据 manifest、SBOM、发布归档和兼容/性能声明。
- 用户在产品外部管理的生产签名材料；产品不得接触该资产。

## 2. 信任边界

- Host 进程将 APK/ZIP/AXML/DEX/SO/签名块视为不可信。
- APK 文件系统、Android package metadata、进程环境和风险信号均可能被攻击者影响。
- 测试 runner、GitHub workflow、设备、artifact storage 和报告不是天然可信；必须以固定 commit、工具链、tuple 和 SHA-256 验证。
- 测试签名器位于产品外，仅使用被忽略目录中的一次性材料。

## 3. 攻击者能力

覆盖损坏或恶意 APK、二次打包、signer 替换、容器/Manifest/SO/壳 DEX 篡改、异常长度和 ZIP 拓扑、调试/Hook/模拟环境、内存读取，以及对 CI evidence 的替换、遗漏、重命名和结果选择。

不承诺抵抗完全控制设备内核、定制 ART、持续 root 注入或能任意修改进程代码与数据的攻击者。对这类能力只能增加成本和缩短明文生命周期。

## 4. 必须保持的安全属性

- 输入永不被产品修改；失败不留下伪成功输出。
- 输出永远未签名；生产依赖图不存在签名执行能力。
- signer、lineage、package、shell 和 AEAD 认证在 payload 发布前完成。
- 认证失败不可被环境策略降级。
- 密钥、AAD、压缩明文和 scratch 在 handle 发布前清零；DEX mapping 在生命周期结束时清零/unmap。
- 报告不泄漏密钥、明文 DEX、设备序列号或用户绝对路径。
- x86/x86_64 架构本身不构成风险信号。

## 5. v0.2 特有证据威胁

| 威胁 | 控制 |
| --- | --- |
| 把旧 v0.1 失败 relabel 为 v0.2 PASS | tuple/version/任务命名隔离，旧 ID 与 artifact 禁止作为 PASS 输入 |
| 修改产品后继续使用旧性能结果 | candidate manifest 与 source commit 强绑定，产品路径变更使 tuple 失效 |
| 选择更有利的重复 run | canonical runAttempt 固定，禁止补样、第三 campaign 和结果替换 |
| 用其他 API/ABI 外推兼容性 | 完整矩阵逐格 `VERIFIED/UNVERIFIED`，只发布 fresh verified cells |
| 在打包后替换 component | security-reviewed manifest、SBOM 和 archive entry 哈希逐项匹配 |
| 测试签名能力泄漏到产品 | source-set、依赖和归档扫描；一次性材料清理证据 |

## 6. 残余风险

离线密钥隐藏、`DONTDUMP`、锁页、抖动、反调试和环境风险信号不能阻止高权限攻击者在解密后观察 DEX 或绕过检测。兼容性只对 exact verified cells 有证据。性能值受固定 reference environment 限定，不能代表所有设备。发布文档必须明确这些边界。

## 7. 审查要求

所有 `security_sensitive: true` V2 任务由非实现者进行独立只读复核。审查同时覆盖产品边界、证据身份、失败原子性、测试签名隔离和残余风险措辞；仅自动化绿灯不足以替代语义审查。
