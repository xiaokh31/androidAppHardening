# M3-12 review-2 remediation validation

- Task: `M3-12`
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Rejected remediation/evidence: `f651731b42b4a4f6158f3f51f7e299d49f639f17` / `a261b0ab58d6c386aececeaefa27a55397e8b08b`
- Review-2 result: `FAIL — P0=0/P1=1/P2=0`
- Descriptor remediation freeze: `be93584bf60ee89a683ed42473acf102625d21db`
- Timestamp: `2026-08-22T10:25:10.8138583+08:00`
- Dynamic scope: none; no workflow, Gradle, Android, KVM, device or benchmark

## Closure

The shared production APK scanner now:

- requires local CRC/compressed-size/uncompressed-size to equal central values whenever bit 3 is clear;
- accepts bit 3 only when local values are either all zero or all equal to central and a complete signed or signature-less descriptor repeats the exact central CRC and sizes;
- includes each descriptor in the local-record range and rejects overlap or unexplained gaps;
- permits a pre-central gap only when it is at most 4095 zero zipalign bytes followed by an aligned, size-reconciled APK Signing Block with exact magic;
- preserves strict bounds, CRC, decompression limit, traversal, duplicate, symlink and sensitive-byte checks.

The same `scanApkBytes` predicate now runs `24` sensitive/nested parser mutations. New cases use real retained baseline/protected APK bytes for descriptor signature/CRC/compressed-size/uncompressed-size, local CRC/compressed-size/uncompressed-size, encrypted flags, local offset, expanded size, symlink entry, overlapping local records and signing-block magic. It also tests a structurally valid duplicate-name archive and positive descriptors both with and without the optional signature.

## Commands

All commands exited `0`:

```text
node --check tools/validation/m3-12-security-scan.mjs
node --check tools/governance/verify-m3-12-profile-retention.mjs
node --check tools/validation/create-m3-12-profile-package.mjs
node tools/validation/create-m3-12-profile-package.mjs --source build/m3-10/review3-profile/package --output build/m3-12/remediation-c/m3-10-profile-package-v1.zip
node tools/governance/verify-m3-12-profile-retention.mjs --archive build/m3-12/remediation-fetch/m3-10-profile-package-v1.zip --self-test --base-ref 9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a
node tools/governance/validate-project-package.mjs
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean
git diff --check
```

The creator still produced the fixed `2184246`-byte archive with SHA-256 `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`. The verifier reported `lockMutations=24`, `archiveMutations=12`, `sensitiveMutations=24`; governance reported `37` task cards, `11` core documents and `17` ADRs.

## Frozen hashes

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `tools/validation/m3-12-security-scan.mjs` | 10756 | `2e198c289a8f60fa3fd9fa62ac1b5a9a3e5e45cd16ce215c1671d508304690d8` |
| `tools/governance/verify-m3-12-profile-retention.mjs` | 35572 | `f61d454719968885278059c814e0e94f3ee1275c22674d984adb14e58d1aff54` |
| `build/m3-12/remediation-c/m3-10-profile-package-v1.zip` | 2184246 | `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964` |

No retained APK/DEX/profile byte changed, no secret or local absolute path was published, and no product, fixture, benchmark, distribution or diagnostic workflow changed. Third independent read-only review is mandatory before branch publication or workflow creation.
