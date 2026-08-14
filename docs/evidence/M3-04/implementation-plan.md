# M3-04 implementation plan and device-inventory gate

- Task: `M3-04`
- Issue: `#21`
- Branch: `chore/m3-04-api-abi-matrix`
- Base: `1a2c2d85be62502913066b301c1083b05de37d00`
- Validation mode: `full-flow`
- Adjacent-task boundary: M3-05 is not started.

## Fixed contract

The task card requires every integer API level from the version catalog minimum `29` through compile SDK `36`, crossed with four real process ABIs: `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`. This is 32 required device cells. A cell may not be filled by a build, simulated report, runner label, another ABI or another API level.

Each cell must report and verify framework device facts, run `java-single-dex` and `kotlin-multidex`, preserve its first failure and at most one retry, and provide immutable evidence hashes. The task also layers `custom-factory`, `jni-four-abi`, ARM-only limitation, signer mismatch, authenticated-container tag tamper and x86/x86_64 zero-risk checks exactly as specified by the task card.

## Verified inventory on 2026-08-15

- One connected physical `user` device reports API `29` and ABI list `arm64-v8a,armeabi-v7a,armeabi`; the shell is non-root. Its raw serial is deliberately not recorded.
- Repository provenance pins API 29 revision 8 and API 36 revision 2 x86_64 system images plus Emulator 37.1.11.
- No current evidence source provides real API 30-36 `arm64-v8a` and `armeabi-v7a` process cells.
- API 30-35 emulator package revisions and archive hashes are not yet approved in the repository supply-chain lock.

## Blocked decision

The implementation must not manufacture a green matrix while device facts are unavailable. Before code or downloads continue, the user must choose one of two bounded routes:

1. retain the 32-cell contract and provide/authorize a real ARM device farm for API 30-36, while also authorizing immutable API 30-35 emulator package pinning; or
2. authorize an independent ADR/task-contract revision that narrows the verified release claim and explicitly records every omitted API/ABI combination as unverified.

No emulator, installation, large download, product code, Runtime code or M3-05 benchmark work was started while this decision is pending.
