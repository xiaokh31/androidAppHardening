---
id: M3-12
title: "Diagnostic profile package retention"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-11
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Publish the already-reviewed M3-10 profile package as one fixed, content-verified remote asset without regenerating any profile APK, then make deletion, replacement or byte drift fail closed before the unique API 36 diagnostic.

## Background

M3-10 implementation review reached `P0=0/P1=0/P2=0`, but a separate execution-readiness audit found that the exact profile package existed only in ignored local storage. The ephemeral signer and container seed had correctly been destroyed, and `regenerationPermitted=false`; therefore a GitHub runner had no legitimate input source. Issue #75 authorizes this bounded retention task.

## Inputs

- M3-11 canonical original contract and accepted ADR 0016.
- M3-10 implementation freeze `86ec37475fd7a96b4baf764530baefc3fe3d4cde`, evidence successor `7a384b321e9afa8df5f683ad1a2b78ba2cb31bd0`, and the all-zero review/workflow-readiness record committed at `ac2d969392556fd9b338399e6cc2e9c22c90daed`.
- The nine exact files under ignored `build/m3-10/review3-profile/package/`.
- Signed profile hashes `a062e0994482b1db417ff710c554364ec80e9f8d5fa84b5745ff5753308b764b` and `1ce941404d8e6105764d041c449a60016312bc9c9671a8f8eb97c4e8b6820a10`.

## Expected Outputs

- ADR 0017 and this task contract.
- Deterministic archive creator and fail-closed ZIP/member verifier.
- Machine-readable release/asset/member lock and normalized official metadata evidence.
- One test-only prerelease asset addressed by numeric asset ID and archive SHA-256.
- Independent all-zero review; no diagnostic workflow in this task.

## In Scope

- Package the exact existing nine files plus a deterministic member manifest.
- Scan every outer member name/byte and every APK ZIP entry name/decompressed byte for signing secrets, keystores, credentials/tokens and Windows/Unix absolute user paths.
- Publish one GitHub prerelease asset and re-download it for byte comparison.
- Fix release/asset identity, archive and member hashes, availability semantics and read-only consumer permissions.
- Update ADR/task/dependency/provenance/governance/HandOff documents.

## Out of Scope

- Recreating or changing an APK, signer, certificate, seed, AHDC, ConfigV2, probe or manifest.
- Creating either M3-10 canonical workflow.
- API 36, KVM, emulator, ARM, benchmark, M3-05 or production optimization.
- Product distribution, customer APKs or general artifact hosting.

## Implementation Decisions

- The source package is byte-exact; archive creation may wrap but never transform its members.
- The machine lock fixes the upstream implementation/evidence/review commits, the all-zero review record and canonical profile-lock path/size/SHA-256 (`a9e130bb4e66e14443d83ea01ef0d60a95adddefa9dc92a9bdc980e5728dab4b`), and the accepted four-APK report SHA-256. Git object ancestry and bytes are verified.
- Release ID `374769776` and asset ID `524507375` are mandatory identity fields. Tag/name URLs are descriptive and cannot substitute the numeric asset endpoint.
- The archive is `2184246` bytes with SHA-256 `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964` and exactly ten regular root members.
- GitHub reports `immutable=false`. Read-only consumption plus asset ID, archive SHA-256 and member hashes creates the acceptance boundary; administrator deletion or mutation causes a permanent fail-closed blocker.
- No fallback, cache, rebuild, regenerated package or alternate asset may preserve the same diagnostic eligibility.
- The tracked repository contains only code, contracts, hashes and normalized metadata; ZIP/APK/DEX bytes remain ignored.

## Public Interfaces

- `node tools/validation/create-m3-12-profile-package.mjs --source <ignored-package> --output <new-build-zip>`
- `node tools/validation/fetch-m3-12-profile-package.mjs --output <new-build-zip>`
- `node tools/governance/verify-m3-12-profile-retention.mjs --self-test`
- `node tools/governance/verify-m3-12-profile-retention.mjs --archive <downloaded-zip> --self-test`
- `docs/evidence/M3-12/profile-package-retention-lock.json`
- No product interface.

## Security Constraints

- Only repository-generated synthetic fixture bytes are authorized.
- No private key, keystore, password, seed, token, customer APK/path, plaintext customer DEX or local absolute path may enter Git, release notes, logs or evidence.
- ZIP names must be unique UTF-8 root basenames with no directory, traversal, slash, backslash, NUL, drive or symlink semantics.
- Archive parsing must bounds-check all counts, offsets and lengths and reject unsupported methods, extra members and trailing bytes.
- Nested APK parsing must reconcile central and local CRC/size fields, strictly validate signed or signature-less data descriptors, reject overlapping/gapped local records, and recognize only bounded zero zipalign padding plus a structurally bounded APK Signing Block before the central directory.
- The future workflow receives only `contents: read` and verifies before emulator creation or installation.

## Compatibility Requirements

- No new Android compatibility claim; no API or ABI is executed.
- The asset remains tied only to the M3-10 API 36 x86_64 diagnostic fixture.
- Production build and distribution surfaces remain unchanged.

## Acceptance Criteria

- Local archive creation reads all nine exact members and produces the locked ZIP.
- Official release metadata, remote download, archive and all ten members match the lock.
- The upstream M3-10 all-zero review record and canonical profile lock match their fixed Git commits, sizes and SHA-256 values and map exactly to all retained profile members.
- Replacement, wrong IDs, wrong digest/size, unavailable asset, malformed ZIP, duplicate/extra/missing member, traversal, truncation, trailing bytes and member mutation fail closed.
- ADR 0016, M3-10, INDEX, ROADMAP, PROJECT_PLAN, TEST_STRATEGY, provenance, README and HandOff agree on the M3-12 gate.
- Base-to-head diff contains no Runtime, Host, fixture, benchmark, product build or diagnostic workflow change.
- Independent read-only review returns `P0=0/P1=0/P2=0` before any M3-10 workflow is added.

## Required Tests

- Creator source-entry set, size/hash, recursive APK sensitive scan, exact locked archive output, two-run determinism and new-output enforcement.
- Lock schema and named field mutation tests.
- Strict actual ZIP parsing and member size/hash verification.
- Archive byte flip, truncation, trailing-byte, duplicate/extra/missing/substituted/traversal member, method, flags, local offset and local/central mismatch mutations.
- Real nested-APK descriptor signature/CRC/size, local CRC/size, encrypted flag, bounds, expansion, duplicate, overlap, symlink and signing-block mutations through the production scanner.
- Output-root symlink/junction escape rejection for creator, fetcher and verifier.
- Remote re-download byte equality, governance, strict HandOff and diff checks.

## Required Evidence

- Branch, base/head, Issue #75, timestamp, OS/Node/GitHub CLI and exact commands/exits.
- Release ID, asset ID/name/size/digest, tag, target commit and `immutable=false` fact.
- Archive plus ten member sizes/SHA-256 values and remote byte equality.
- Explicit no-regeneration/no-secret/no-device/no-workflow statement.
- Exact creator positive commands, two-run byte equality and expected-nonzero existing-output/extra-entry/source-hash failures.
- Independent review result and exact-head Ubuntu/Windows Build/Governance before merge.

## Likely Files

- `docs/adr/0017-profile-package-retention-boundary.md`
- `docs/tasks/M3-12-profile-package-retention.md`
- `docs/evidence/M3-12/`
- `tools/validation/create-m3-12-profile-package.mjs`
- `tools/governance/verify-m3-12-profile-retention.mjs`
- governance and dependency documents
- `HandOff.md`

## Dependencies and Blockers

M3-12 depends on merged M3-11 and consumes the already independently reviewed M3-10 package as an immutable input. M3-10 workflow creation and M3-05 remain blocked until M3-12 merges and the M3-10 branch incorporates its lock and verifier.

## Agent Handoff Requirements

Use Issue #75, branch `docs/m3-12-profile-package-retention` and one unique PR. Record the exact asset and member identities, remote re-download, archive mutations, zero-product diff and independent review. State that no profile regeneration, workflow, Android environment, benchmark or M3-05 action occurred.
