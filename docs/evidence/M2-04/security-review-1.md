# M2-04 independent read-only security review 1

## Scope

- Frozen commit: `3cc8dc2cc693c90cf757a39d794c3284ec73f6f6`
- Base: `9ea71927aea01cd28ba993df71d50b82213dd87d`
- Reviewer mode: independent, read-only; no edit, download, Gradle, device, emulator or push.

## Verdict

`FAIL`: P0 `0`, P1 `2`, P2 `2`. This frozen commit is permanently rejected.

## Findings

1. P1 — `AbiCompatibility`/`AbiCompatibilityPolicy` used Java 9 collection factories (`List.of`/`List.copyOf`) on the API 29 production path without core-library desugaring. This could produce `NoSuchMethodError`.
2. P1 — M202 ABI observation always opened `nativeLibraryDir/libah_runtime.so`, which cannot represent direct packaging; the Native search-path assertion also admitted only the two 64-bit ABIs. Direct and 32-bit device gates would fail.
3. P2 — the public policy emitted `OUTPUT_LIMITED_TO_INPUT_NATIVE_ABIS` for a complete four-ABI input while REPORT_V1 did not.
4. P2 — the ELF verifier checked section flags but did not prove `.ah_share_v1` was fully covered by one non-writable `PT_LOAD`, so a writable segment mutation could evade CI.

## Required remediation

- Use API 29-safe immutable collection construction and call the public policy/getters in connected coverage.
- Read extracted ELF headers from `nativeLibraryDir`, direct ELF headers from the bounded APK ZIP entry selected for the actual process ABI, and accept all four exact Native paths.
- Emit a limitation only when the effective set is a strict subset of the available four-ABI Runtime.
- Parse `PT_LOAD`, require exactly one non-writable covering segment, and reject a directed writable-segment mutation.

External gates were also correctly reported pending: the first ARM install was user-restricted, KVM/dual-platform CI had not run, and no PR had been published.
