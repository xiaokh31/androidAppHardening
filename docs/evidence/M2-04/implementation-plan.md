# M2-04 implementation plan

## Frozen scope

- Task: M2-04, Issue #15, branch `feat/m2-04-four-abi-runtime`, base `main@9ea71927aea01cd28ba993df71d50b82213dd87d`.
- Validation mode: `pre-cli`. M1-06 remains the Host integration driver; M2-05 environment scoring is not started.
- Production changes are limited to the four-ABI Native build/package contract and the immutable ABI compatibility model. Existing REPORT_V1 ABI fields are aligned to accepted ADR-0005 names.
- Test-only changes reuse the authenticated M2-02 container fixture on every executable ABI. No customer APK, production key, local emulator, network download or architecture risk heuristic is introduced.

## Implementation boundaries

1. Build the same Native sources with pinned NDK `29.0.14206865`/Clang `21.0.0` for `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`.
2. Fail unless the Release AAR contains exactly one stripped `libah_runtime.so` per ABI, the debug-symbol archive is separate, and each ELF has the fixed machine/ABI ID, one read-only 104-byte `.ah_share_v1`, RELRO/NOW, non-executable stack and exactly five JNI exports.
3. Evaluate input Native ABI compatibility in Java without converting ARM-only inputs to x86 and without treating x86/x86_64 as risk evidence.
4. Reuse one source DEX pair and authenticated container vector for both packaging modes. Each device run asserts the actual loaded ELF ABI, JNI handle/cleanup, metadata, cross-DEX and stable negative behavior.

## Bounded device matrix

| Environment | Required ABI runs | Bound |
|---|---|---|
| API 29 Linux/KVM x86_64 system image | `x86_64`, `x86` | extracted/direct, one instrumentation and one cold start each |
| API 36 Linux/KVM x86_64 system image | `x86_64` | extracted/direct, one instrumentation and one cold start each |
| API 29 non-root physical device | `arm64-v8a`, `armeabi-v7a` | extracted/direct, one instrumentation and one cold start each |

The KVM job owns a 45-minute overall timeout, bounded boot/test commands and unconditional emulator cleanup. The physical runner never retries an installation, changes secure settings or escalates privileges. If either ARM ABI cannot execute, M2-04 remains blocked as required by the task contract.

## Completion gates

- One targeted local Gradle matrix plus the deterministic ELF/AAR verifier.
- One API 29/36 KVM workflow execution at the frozen implementation SHA.
- One successful two-ABI physical-device execution at the same production source lineage.
- Independent read-only security review returning `P0=0`, `P1=0`, `P2=0`.
- Only after those gates: unique PR publication, README completion update, strict HandOff and final `main` gates.
