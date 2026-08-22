# M2-07 Windows runner 20260818 independent read-only review 1

- Frozen candidate: `b1895f6688482731f3c00fca1322ed55f0ade554`
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Result: `FAIL — P0=0/P1=0/P2=2`
- Scope: complete independent read-only security/supply-chain review; no modification, dependency download, Gradle, Host compilation, device, KVM, fuzz, equivalence or benchmark

## Findings

1. `clang_cl_version` was fixed in the machine lock but Build compared a duplicated hardcoded `20.1.8`, and the static workflow validator did not require consumption of the lock field. A workflow-only clang drift could therefore evade the static self-test.
2. HandOff still described local validation, freezing and starting the first review as future actions after all three had already occurred.

The bounded remediation must derive the exact clang regex from `$toolchainLock.clang_cl_version`, statically bind it, add a workflow-side mutation, and reconcile HandOff. No version, runtime/ref, per-image VS/x64 value, product input or test scope may change.

## Confirmed positive evidence

The reviewer independently confirmed the official lightweight ref, commit/tree/blob, manifest size/SHA-256 and Git blob identity; all four runtime/ref mappings; old `18.8.*` and new `18.9.*` per-image VS/x64 bindings; shared LLVM `20.1.8`, SDK `10.0.26100.0` and CMake `4.1.2` inventory; runtime-to-complete-record selection; unknown-image failure; M3-02 lock consistency; ten-file bounded diff; and all local Node/governance/strict/diff checks. No P0 or P1 finding exists.
