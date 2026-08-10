# M2-03 remote validation

- Frozen implementation commit: `7db6873d05bcb3c384d653f0e8debd0376a1dbea`
- Timestamp: `2026-08-10T06:45:10Z`
- Validation mode: `pre-cli`

## Ubuntu and Windows

GitHub Actions Build run `31362136455` completed successfully on the exact frozen commit.

- Ubuntu 24.04 job `93372932280`: full clean checks, M2-02 sanitizer/fuzz/failure injection, dependency-verification negative and four Native ABIs passed.
- Windows 2025 job `93372932191`: full clean checks, byte-consistency regressions and four Native ABIs passed.
- Run: `https://github.com/xiaokh31/androidAppHardening/actions/runs/31362136455`

## API 29 and API 36 Linux/KVM

GitHub Actions KVM run `31362136472` completed successfully on the same frozen commit. Both jobs used the pinned system image, a 45-minute job timeout, per-command timeouts and unconditional package/emulator cleanup.

- API 29 x86_64 job `93372932509`: extracted/direct instrumentation passed; each reported six Guard failure-injection windows, signer and authenticated metadata verification, session close ownership, cross-DEX, JNI and zero plaintext DEX files. Each variant completed exactly 20 cold starts. Report SHA-256: `28e0174168ac2a54de8d43472d20bfb101cd9006a57d164c59994fecc697c9e0`.
- API 36 x86_64 job `93372932483`: the same matrix passed with exactly 20 cold starts per variant. Report SHA-256: `29e678128f295f4b8a4372c1c2d69bdb008769df516910c6b649cb268b6f45e7`.
- The standalone policy instrumentation ran non-empty on both devices and returned `policy_connected=true cases=5` with `INSTRUMENTATION_CODE: -1`.
- API 29 artifact `9053041162`: SHA-256 `06679b79b33c0f9d0bd07052061ec2b75ff2cdea90446501db358c836ccc0a3b`.
- API 36 artifact `9053121523`: SHA-256 `98b0ec40fc5f27546abfa08812a08139f1c0ae2d72295a7a3a481d8dd9f5a303`.
- Run: `https://github.com/xiaokh31/androidAppHardening/actions/runs/31362136472`

The downloaded evidence copies live only under ignored `build/m2-03/remote-api29` and `build/m2-03/remote-api36`; no APK or large artifact is tracked.

## Remaining external gate

API 29 arm64 on the authorized non-root Xiaomi device remains unaccepted because MIUI rejected both bounded normal ADB installation attempts with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. The target and test APK signer SHA-256 values were identical before the attempt, and cleanup ran. No root, secure-setting change or UI bypass was attempted. M2-03 must remain draft until a normal system-authorized install succeeds and an independent read-only review reports `P0=0`, `P1=0`, `P2=0`.
