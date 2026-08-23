# M3-14 independent review 1 remediation

- Rejected freeze: `3adde9d909d47719115deffd9fd19e74aa696236`
- Independent result: `FAIL — P0=0/P1=2/P2=2`
- Remediation timestamp: `2026-08-23T09:03:59+08:00`
- Scope: workflow-absent static implementation only

## Closed findings

1. **P1 terminal deletion bypass**: the terminal candidate now uses unfiltered `git diff --name-only`, so additions, modifications, renames and deletions all enter the exact one-path check. A named mutation restores the old deletion-excluding filter and must fail.
2. **P1 missing pre-publication CI phase**: the M3-14 task and HandOff now require the unchanged all-zero workflow-absent freeze to be pushed as the unique Issue #82 draft PR head and pass exact-head Ubuntu/Windows Build plus Governance before any direct-child workflow/ledger publication.
3. **P2 executable profile-generation surface**: unused `M310DexProfileTool.kt` was removed. The freeze validator now requires the old deriver, DEX transformer and profile preparation entry point all to remain absent. Retained-package verification and ignored scratch negative-mutation APK writing are the only remaining test-side rewrite capabilities.
4. **P2 collector self-test evidence**: local and Governance M3-14 gates now execute the collector's 5 redirect allow/deny cases plus 1 archive expansion-bound case.

## Bounded validation

- `node tools/governance/verify-m3-14-profile-freeze.mjs --self-test --base-ref 960eb9f406eb1a7b7c9b324598fb59936aa1c5b5` → exit `0`, 14 mutations.
- `node tools/validation/collect-m3-14-github-evidence.mjs --self-test` → exit `0`, redirects `5`, archive bounds `1`.
- `node tools/validation/verify-m3-14-startup-attribution.mjs self-test` → exit `0`, 59 rejected mutations.
- `node tools/validation/run-m3-14-startup-attribution.mjs --cleanup-self-test` → exit `0`, 8 failure injections.
- repository-local Gradle `:host:container:testClasses :host:container:m310MetadataSelfTest --offline --no-daemon` with Temurin 17 → exit `0` after deleting the transformer.
- Project Governance and `git diff --check` → exit `0`.

Both canonical workflow paths remain absent. No workflow was published; no API 36, emulator, Android device, ARM, API 29, M3-05 or owner remediation ran; `runAttempt=1` remains unconsumed. A new frozen Git object and second independent all-zero review are still required.
