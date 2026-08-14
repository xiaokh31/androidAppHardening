---
id: M2-08
title: "Native parser topology bounds hardening"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M2-02
required_skills:
  - plan-apk-hardening-change
  - implement-runtime-protection
  - validate-protected-apk
security_sensitive: true
---

## Goal

Make AHDC v2 topology validation fail closed before deriving or reading any chunk-table pointer when untrusted record counts exceed the authenticated table bounds.

## Background

M3-02 Native fuzz run `31768402808`, job `94668992052`, found an ASan heap-buffer-overflow in `validateTopology -> parseChunkV2 -> u32`. The minimized synthetic input is 399 bytes with SHA-256 `61b51e45d160f1c2ab5fa5fe7e52bb971e3f4a987b98a659087f3ce287867dd9`. M3-02 and PR #52 remain blocked until this independent fix is merged.

## Inputs

- The synthetic minimized crash artifact from M3-02.
- The frozen AHDC v2 parser and M2-02 test/toolchain contracts.

## Expected Outputs

- A bounds-safe `validateTopology()` implementation.
- A stable crash regression and exact-head sanitizer/review evidence.

## In Scope

- Checked record-to-global-chunk bounds before pointer derivation.
- Checked byte-offset bounds before every chunk-table read.
- The minimized crash regression plus adjacent count/table mismatch cases.
- Pinned Host tests and Ubuntu ASan/UBSan execution.
- Independent read-only security review.

## Out of Scope

- AHDC wire-format, cryptography, public C++/JNI/Java interfaces, or stable status changes.
- M3-02 fuzz infrastructure or memory tuning.
- Android device, emulator, KVM, or APK acceptance.

## Implementation Decisions

- Keep all frozen wire structs and parser entry points unchanged.
- Reject malformed topology with `Status::kFormat` before constructing an out-of-range view.
- Track the exact crash input as a text hex fixture; it is synthetic and contains no customer material.
- No ADR is required because format, interface, compatibility, and error contracts do not change.

## Public Interfaces

No public interface changes. Existing `validateTopology()` signature and `Status` values remain frozen.

## Security Constraints

- Treat every count, size, offset, record, and chunk as untrusted.
- Perform checked bounds validation before pointer arithmetic or dereference.
- Do not weaken existing fail-closed parsing or disclose input bytes in logs.

## Compatibility Requirements

- Preserve AHDC v2 wire bytes and existing positive parser behavior.
- Preserve C++17, pinned Native backend, Windows x64, Ubuntu x64, and Android ABI builds.

## Acceptance Criteria

- The minimized crash input returns `Status::kFormat` under ordinary, ASan, and UBSan Host tests without a sanitizer finding.
- A record claiming more chunks than the header/table and an inconsistent header count/table pair fail before any chunk read.
- Existing positive AHDC v2 parser and crypto tests remain green on Ubuntu and Windows.
- Governance and strict HandOff validation pass.
- A frozen implementation commit receives an independent read-only review with P0/P1/P2 all zero.

## Required Tests

- Exact minimized crash input through the original header/record/topology call sequence.
- Adjacent record chunk overflow and header/table inconsistency negatives.
- Existing Native Host self-test on Windows and Ubuntu.
- Ubuntu Host self-test with ASan and UBSan enabled.

## Required Evidence

- Issue #53, branch `fix/m2-08-native-parser-bounds`, frozen SHA, commands, exit codes, OS/toolchain, timestamps, and fixture hash.
- Exact-head Ubuntu/Windows Build and Governance results; Ubuntu Build must run ASan/UBSan.
- Independent review report and expected-head protected merge.

## Likely Files

- `runtime/native/src/main/cpp/container_format.cpp`
- `runtime/native/src/main/cpp/container_format_test.cpp`
- `runtime/native/src/main/cpp/testdata/`
- `docs/evidence/M2-08/`

## Dependencies and Blockers

Any sanitizer failure, parser regression, evidence mismatch, or open P0/P1/P2 review finding blocks merge and keeps M3-02 paused.

## Agent Handoff Requirements

Use Issue #53 and the single branch/PR above. After merge, resume PR #52 CI without rerunning Android device or KVM acceptance for this parser-only fix.
