# M2-07 independent read-only security review 4

## Frozen input

- Commit: `a764d102492f4c6074d928a240ae1c62abc2d320`
- Reviewer: independent `m2_07_security_review_4` Agent
- Result: **FAIL**; `P0=0`, `P1=0`, `P2=2`
- Files changed, branch/build/download/device/emulator actions: none

## Prior finding disposition

All seven review-1 findings, all three review-2 findings and the sole review-3 finding were independently **CLOSED**. The review confirmed archive-before-parser and full-tree promotion, AES/HKDF boundaries and transaction serialization, Release four-ABI scans, deep machine-lock negatives, vulnerability reachability, platform-exact symlink counts, Ubuntu reviewed-image/compiler gates, truthful README state, and Linux/Windows full-147 wrong-prefix rejection.

Exact-SHA Build `31141984739`, Governance `31141984713` and API 29/36 KVM `31141984706` all succeeded and executed the expected source, crypto, ABI, governance, device and cleanup gates.

## New findings

### P2 — Windows gate did not consume two locked component versions

The task and machine lock required exact Visual Studio `18.8.12023.21` and x64 tools component `18.8.11901.359` runtime assertions. The Windows workflow selected one of two reviewed image identities and asserted LLVM `20.1.8`, the fixed `VsDevCmd.bat` path and `cl.exe 19.51.36252`, but it did not read and compare the two locked VS/component values. A reviewed image mapping reduces drift risk but does not satisfy the declared per-component gate.

Read the installed Enterprise instance through the runner's `vswhere`, read its selected `Microsoft.VisualStudio.Component.VC.Tools.x86.x64` package from the matching installer state, compare both values byte-for-byte with the machine lock, print them, and exercise mismatch negatives.

### P2 — HandOff ordered actions predated the already-pushed freeze

The current-state text and ordered next actions still instructed a future Agent to commit/push the evidence-only successor even though frozen HEAD `a764d102...` was that successor and its exact-head workflows had already passed. Reconcile the root HandOff to the real review-4 failure and remediation state.

## Conclusion

This frozen SHA is permanently rejected. **FAIL: P0=0, P1=0, P2=2.** PR #42 must remain draft and M2-02 must remain paused.
