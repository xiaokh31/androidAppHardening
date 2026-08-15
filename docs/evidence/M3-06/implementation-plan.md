# M3-06 implementation plan

- Task: `M3-06`
- Issue: `#56`
- Branch: `docs/m3-06-api-abi-validation-contract`
- Base: `1a2c2d85be62502913066b301c1083b05de37d00`
- Validation mode: `governance-only`
- Paused task: `M3-04` remains preserved on local branch `chore/m3-04-api-abi-matrix` at blocker commit `72a5fce85bbee5b0f1888028049f096487febb7e`.

## Fixed scope

- Add ADR 0012 and a separate task contract before changing M3-04 acceptance semantics.
- Enumerate every API 29 through locked `compileSdk` by four-ABI cell.
- Reserve `VERIFIED` for real Android-reported process evidence, use `FAILED` for an executed regression, and use `UNVERIFIED` for an unavailable environment without making a positive claim.
- Require the currently provisioned baseline: API 29 ARM32/ARM64 physical processes and API 29/36 x86_64 pinned KVM processes.
- Keep Runtime four-ABI build capability, input native-ABI compatibility, and release device validation as separate statements.
- Change governance documents and the fixed governance task registry only. Do not install an APK, start a device/emulator, download a system image, run fuzz/benchmarks, or modify production/fixture code.

## Completion boundary

- Governance, strict HandOff, claim scan, UTF-8/sensitive scan, and diff checks pass on a clean frozen commit.
- The unique Issue #56 PR passes exact-head Ubuntu/Windows Build and Governance. Out-of-scope device/KVM/fuzz workflows are cancelled or skipped.
- README and HandOff record M3-06 completion after merge. Only then may the preserved M3-04 branch be rebased or recreated from current `main`; M3-05 remains unstarted until M3-04 completes.
