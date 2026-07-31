# M0-04 API 29/36 正式设备验收

## 结论

- task: `M0-04`
- validation mode: `pre-cli`
- branch: `spike/m0-04-classloader-poc`
- reviewed implementation commit: `e9f89734aa3d4148ec6ebe9a6b970a9276128d00`
- environment: `Windows 10 10.0.19045 x64; Temurin 17.0.19+10; Gradle 9.5.0; Node.js 24.12.0; Emulator 37.1.11 build 15917651`
- timestamp: `2026-07-31T15:06:44+08:00`
- result: `PASS`
- independent security review: `PASS; no remaining P0/P1/P2 findings`

本报告只证明 M0-04 的 API 29+ 公开 ClassLoader 接入可行性，不声明生产 signer 校验、加密容器、原始 Factory 委托、Provider/JNI、多 DEX 或 root/Frida/kernel 攻击防护已经实现。

## 固定设备工具链

| Package | Revision | Official archive SHA-1 | Project SHA-256 | Result |
|---|---:|---|---|---|
| `emulator` | `37.1.11` build `15917651` | `54fa750822ff462d57e04fc8e98e60f08df2bb61` | `5ff441f3b12ace9b13e9cf96fb0007d233967718652a8110705e995ac47bfeb7` | PASS |
| `system-images;android-29;default;x86_64` | `8` | `e4b798d6fcddff90d528d74ef22ce3dd4a2ca798` | `b5c3fda1f4b4931c30518d342e4ad5f7464945e0cdced3538d4ff2e12f7bf201` | PASS |
| `system-images;android-36;default;x86_64` | `2` | `829c076e8ff448a336097ae25a355b495ba36e2c` | `e1b9d9fb665001ef27b16e57d8762a2d54aec6bff617e17506edb8676667b9da` | PASS |

命令：

```text
node tools/validation/verify-m0-04-android-packages.mjs
```

退出码为 `0`。下载归档、解压后的 SDK、AVD、Android 用户状态和 Gradle 新缓存均位于被 Git 忽略的项目根 `.toolchains/`；验收后没有遗留本地 Emulator 或 watchdog。C 盘用户 Android 目录没有新增文件；全局 SDK 只更新了 16 字节 `.knownPackages` 元数据，没有下载大体积程序。

## 被测产物

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| fixture APK | 2826455 | `cceb28247c8598dd92cae5b336385a9091456193abfa563e72c3e794ff96b4a9` |
| packaged payload DEX | 1164 | `77fdfdb6e35a0f09747c09c28b245a289cdc5126af0c7e2a719581548318cda1` |
| instrumentation APK | 106580 | `2dcc8ecf4654e5b73e5cf74316007fb33730cdc242d6bc5feb392c0f20c6c2e1` |

静态合同命令：

```text
node tools/validation/verify-m0-04-apk.mjs fixtures/android/build/outputs/apk/classloaderPoc/debug/android-classloaderPoc-debug.apk fixtures/android/build/generated/m0-04/classloaderPocDebug/assets/ah/poc/classes.dex fixtures/android/build/outputs/apk/androidTest/classloaderPoc/debug/android-classloaderPoc-debug-androidTest.apk
```

退出码为 `0`。payload 是唯一规范名称的非空 `STORED` DEX，无 encryption 或 data descriptor；payload 类未泄漏到根 DEX；源码策略扫描通过。

## API 29 revision 8

- device: `m0_04_api29_r8_x86_64(AVD) - 10`
- fingerprint: `Android/sdk_phone_x86_64/generic_x86_64:10/QSR1.210820.001/7663313:userdebug/test-keys`
- ABI: `x86_64`
- shell: `uid=2000(shell)`
- instrumentation: `1/1`, failures `0`, errors `0`
- cold starts: `20/20`
- private/external before/after snapshots: all successful
- forbidden logs, forbidden files and payload hash matches: `0`
- missing/corrupt/empty packaged payload failure-close rows: `3/3 PASS`

Evidence：

| Ignored artifact | SHA-256 |
|---|---|
| `build/m0-04/evidence/api29-connected.xml` | `e81dcc299c4ec00081265fc4a81a4797382fbbc993dc7f2411c77848e8b6207b` |
| `build/m0-04/evidence/api29-connected-test-results.log` | `10dd0917e9a49594b6c73a40a2ce7abc9f5677e450600cbb83ff60ca0a9ad0c4` |
| `build/m0-04/evidence/api29-cold.json` | `3908ab248fb66cc25b9b2167ac5eda5d1ec443c8531c3829bcf5d00aeedd561b` |
| `build/m0-04/evidence/api29-tamper.json` | `b06496d82c1ae7f9d5b93feba1d40d5e2a3fcb7dd7385957254ddf622fd2246c` |

## API 36 revision 2

- device: `m0_04_api36_r2_x86_64(AVD) - 16`
- fingerprint: `Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys`
- ABI: `x86_64`
- shell: `uid=2000(shell)`
- instrumentation: `1/1`, failures `0`, errors `0`
- cold starts: `20/20`
- private/external before/after snapshots: all successful
- forbidden logs, forbidden files and payload hash matches: `0`
- missing/corrupt/empty packaged payload failure-close rows: `3/3 PASS`

Evidence：

| Ignored artifact | SHA-256 |
|---|---|
| `build/m0-04/evidence/api36-connected.xml` | `e8096eebfe0c15faf3c354bdadb53db5206dd2537009c88ae924fde83b63dd4c` |
| `build/m0-04/evidence/api36-connected-test-results.log` | `2f364304a8a17f4b59bc9771067f8e24fc9086613d50eeea1745f986429e2561` |
| `build/m0-04/evidence/api36-cold.json` | `6d44c86188b2bb2a44b35178dd01aed99f9465d427ec1ea3bb82887aeb397a5a` |
| `build/m0-04/evidence/api36-tamper.json` | `65ad6ba9f90ed001d29abdca7ddc8390e9a7a028fc416976306cf1f7711b76aa` |

## 共同断言

两台设备均由以下任务入口退出 `0`：

```text
.\gradlew.bat --offline --no-daemon :fixtures:android:connectedClassloaderPocDebugAndroidTest
```

Instrumentation 确认：

```text
FACTORY_ENTER < LOADER_CREATED < APPLICATION_CREATED < ACTIVITY_CREATED
```

返回值为 `dalvik.system.InMemoryDexClassLoader`，Application 与 Activity 使用同一返回 loader，payload-only 方法成功。缺失、损坏、空和重复 payload 的进程内输入均以 `AAH-P001` 失败关闭，异常链不包含 `sourceDir`。

三种重签测试 APK 只使用忽略构建目录内的一次性非生产证书；均通过 `apksigner verify` 与 `zipalign -c -P 16 4`。真实冷启动都进入 `FACTORY_ENTER` 后以 `AAH-P001` 终止，没有 `LOADER_CREATED`、Application/Activity、payload marker、父 ClassLoader 回退或存活进程。签名能力和测试证书均未进入产品模块或版本库。

附加校验全部退出 `0`：

```text
node tools/validation/run-m0-04-cold-start.mjs --self-test
node tools/governance/validate-project-package.mjs
node tools/validation/verify-m0-toolchain.mjs
node --check tools/validation/create-m0-04-tampered-apks.mjs
node --check tools/validation/run-m0-04-cold-start.mjs
node --check tools/validation/run-m0-04-tamper-start.mjs
git diff --check
```

本地离线 lint 尝试因新的 D 盘 Gradle cache 尚未包含 Android lint 工具依赖而退出非零；这不是源码或验收失败。连接设备任务已重新编译相关 Java，PR CI 仍须在推送后完成完整 Build/Governance 门禁。
