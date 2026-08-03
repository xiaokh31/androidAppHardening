# M1-02 implementation plan

## Scope

- Task: `M1-02` / Issue `#7` / branch `feat/m1-02-signer-policy`.
- Verify one read-only standalone APK with pinned Android `apksig 9.3.0` and minimum checked platform `29`.
- Produce immutable `SignerPolicyV1` data for the unique current signer, validated oldest-to-newest lineage, verified schemes and Host report fields.
- Preserve M1-01 input identity by comparing the same APK against `ApkInspection.inputSha256` before and after signer verification.
- Keep signing, private keys, keystores, aliases, passwords, signer migration, `SPV1` wire encoding and Runtime enforcement out of scope.

## Existing decisions

- ADR 0002 fixes the unsigned-output and same-current-signer boundary.
- ADR 0004 fixes the `SPV1` digest list constraints and byte layout consumed later by M1-04.
- No new ADR is required because this task does not change either accepted contract.

## Public contract

- `SignerPolicyVerifier.verify(Path, ApkInspection): SignerPolicyV1`.
- Stable errors: `SIGNER_UNSIGNED`, `SIGNER_INVALID`, `SIGNER_MULTIPLE_CURRENT`, `SIGNER_LINEAGE_INVALID`, `SIGNER_INPUT_CHANGED`, `SIGNER_INTERNAL`.
- `SignerPolicyV1` exposes defensive copies of 32-byte current and lineage digests, canonical lowercase hex, verified scheme names, `policyVersion=1`, `requiredAfterProtection=true`, and `performedByProduct=false`.
- A separate model-level `SPV1` validator enforces lineage count `1..16`, no duplicates and current digest as the final item; it does not serialize bytes.

## Failure behavior

- Unsigned input fails as `SIGNER_UNSIGNED`.
- Invalid, tampered, malformed or unsupported verified-signature structures fail as `SIGNER_INVALID` unless the official result proves multiple current signers or invalid lineage.
- More than one current signer fails as `SIGNER_MULTIPLE_CURRENT`.
- Invalid lineage shape or official lineage disagreement fails as `SIGNER_LINEAGE_INVALID`.
- Any mismatch between the M1-01 digest, signer-verification bytes and final bytes fails as `SIGNER_INPUT_CHANGED` and returns no policy.
- Unexpected failures are wrapped as `SIGNER_INTERNAL` without absolute paths, certificate bodies or raw `apksig` diagnostics.

## Test contract

1. Generate disposable synthetic signing identities and APK fixtures only under ignored module `build/` directories.
2. Cover valid v1, v2, v3, combined schemes and valid rotation lineage as supported by the pinned official tools.
3. Cross-check current certificate SHA-256 with pinned `apksigner verify --print-certs`.
4. Cover unsigned, tampered, truncated signing block, invalid lineage and multiple current signer inputs.
5. Mutate the APK during verification and require `SIGNER_INPUT_CHANGED` with no returned policy.
6. Cover current/lineage defensive copying, lowercase encoding, ordering and every `SPV1` model constraint.
7. Scan production source/API/bytecode for signing execution or secret-bearing entry points.
8. Emit canonical policy JSON and an error matrix for Windows/Ubuntu byte-equivalence CI.

## Security review

- Independent reviewer: `m1_02_security_review`.
- Start only after implementation and evidence are committed and frozen.
- Completion requires P0/P1/P2 findings to be closed or explicitly block the task.
