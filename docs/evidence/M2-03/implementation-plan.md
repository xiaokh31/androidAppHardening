# M2-03 Runtime signer and integrity implementation plan

- Base: `main@dec1ef68f69eea26ae1bc6a1132bf26bf39ba0f8`
- Issue: `#14`
- Branch: `feat/m2-03-runtime-integrity`
- Scope: `runtime/policy`, M2-03 fixtures/tests, bounded CI and evidence only.

## Frozen sequence

1. Read the installed APK only from Framework `ApplicationInfo.sourceDir` and verify it with pinned `apksig 9.3.0`, minimum checked platform 29.
2. Require exactly one current signer; normalize DER SHA-256 and an ordered, unique, 1..16 old-to-new lineage ending at the current signer.
3. Hash the exact Framework package name, inspect the bounded unauthenticated binding, and constant-time precompare its current signer.
4. Call `PayloadRuntime.openVerified` with the measured signer. Before any provisional-loader lookup, recheck same-handle authenticated package, signer, lineage, build/key snapshot and versions `2.0/1/1`.
5. Construct immutable identity/configuration/session under a local unique owner. Every exception or OOM before return closes the payload exactly once and preserves the primary failure with cleanup suppressed.
6. Keep verification records process-local, idempotent and bounded; the full signer and metadata checks still run for every new handle/session.

## Stable results

All rejections use the `AAH-RUNTIME-INTEGRITY-` prefix. Audit-safe identity is limited to the first 12 lowercase hex characters; no certificate, APK path, full digest, config bytes or key material is logged.

## Acceptance

- Dependency locks and provenance retain pinned `apksig 9.3.0`.
- `:runtime:policy:test` runs non-empty pure-Java checks; `:runtime:policy:connectedCheck` runs a real instrumentation runner.
- The M2-03 extracted/direct Release/R8 fixture validates success, signature classes, metadata negatives, failure injection, close ownership and no pre-accept loader use.
- Architecture and sensitive-capability scans enforce the task contract.
- API 29 arm64 uses the authorized non-root device with bounded commands. API 29/36 x86_64 uses GitHub Linux/KVM with job timeout and unconditional emulator cleanup.
- After a frozen commit, an independent read-only security review must report `P0=0`, `P1=0`, `P2=0` before the unique Issue #14 PR can become ready or merge.
