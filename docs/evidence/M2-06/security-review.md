# M2-06 independent security review

## Scope

- Initial implementation: `9839c8de321c82ddd12745006d6aca16f49ac370`
- Reviewed fix: `ac374ad03bce87ac7068cf124f4721441f79f59f`
- Reviewer: independent read-only `m2_06_security_review`
- Review mode: static audit and existing evidence verification; no file writes, downloads, emulator, physical device, or repeated full Gradle/device matrix

## First review

Result: `FAIL`, P0 `0`, P1 `1`, P2 `1`.

- P1: ordinary `ConfigV2::r_java` and `NativeShareSlotV1::r_native` copies were not deterministically scrubbed on every return path.
- P2: the HIGH-profile cryptographic jitter temporary was not scrubbed on success and error returns.

The initial implementation SHA is rejected as a completion candidate.

## Corrective review

Result: `PASS`, P0 `0`, P1 `0`, P2 `0`.

- `r_java` and `r_native` are copied immediately into bounded locked `SecureBuffer` instances; ordinary wire-structure copies are cleared immediately and protected by `noexcept` RAII scrubbers on every exit.
- Root material is reconstructed only from the two secure buffers.
- The vector test requires exact `2/2` share-scrub evidence for both the success and damaged-slot failure paths.
- `RandomValueScrubber` covers successful jitter, short `getrandom`, and failed/interrupted `nanosleep` exits.
- The M2-06 static verifier now locks the production share-scrubbers, secure-buffer consumption path, and jitter scrubber.

The bounded increment introduced no new P0, P1, or P2 finding.

## Final CI-fix review

- Reviewed range: `ac374ad03bce87ac7068cf124f4721441f79f59f..6cd2bc221ecfd1ea203813facf94519baa885fca`
- Result: `PASS`, P0 `0`, P1 `0`, P2 `0`

The release-count correction matches the actual SecureBuffer move/reset lifecycle and preserves the injected-failure total. The Runtime caller verifier admits only the frozen `MemoryControls.applyMemoryProfile` entry while retaining Guard ownership of authenticated open operations. The `smaps` check now accepts API 36's adjacent-VMA merge only when at least one new `dd` VMA exists and its total bytes cover both page-aligned DEX buffers; per-mapping Native capability bits, exact buffer count and the Node-side numeric check remain mandatory.

Exact-head Build `31671159532`, Governance `31671159537`, and API 29/36 KVM `31671159539` all passed. The independent conclusion is final for this production candidate unless production or security-boundary code changes.
