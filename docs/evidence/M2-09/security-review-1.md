# M2-09 independent read-only security review 1

- Reviewed implementation: `9ba6ec28c7d1450c3ca51175f78e3aa2d292331f`
- Evidence child: `82bf4aa68e4b1457b8c85fa7dfb85318e8e2d6fb`
- Result: `FAIL`
- Findings: `P0=0`, `P1=0`, `P2=1`
- Reviewer mode: independent, read-only; no repository modification, Gradle, device, emulator, KVM or network

## P2-1: incomplete JVM second-Shell state matrix

Production behavior was found sound, but the JVM suite checked `Coordinator.readyResult()` directly for only `NEW` and `FAILED`. It did not drive the second `ShellAppComponentFactory` through READY attach, loader mismatch, `NEW`, `INSTALLING` and `FAILED`, nor prove at that boundary that Guard open and Factory construction/hook counts remain unchanged.

Required remediation: use the second Shell wrapper boundary, directly or through a test-only reflection helper, to require the same READY result for the identical final loader; stable `AAH-RUNTIME-BOOT-COMPONENT` for loader mismatch, `NEW`, `INSTALLING` and `FAILED`; one Guard open; one Factory construct/hook; and no FAILED retry.

## Confirmed properties

- The synchronized coordinator lookup exposes only a complete `READY` result.
- Loader reference equality is checked before cache publication and component delegation.
- `NEW`, `INSTALLING` and `FAILED` cannot reopen Guard through the production attachment path.
- The terminal result remains the sole owner of session, provisional/final loader and original Factory.
- No public API, hidden API, disk DEX or cross-process sharing was added.
- Local artifact size and SHA-256 evidence matched.

This review permanently rejects `9ba6ec2` as the final review freeze. Remediation commit `dd78179f41c97aab7e3f38c0f571c4e6198f8939` is limited to the JVM test and requires an incremental independent read-only review.
