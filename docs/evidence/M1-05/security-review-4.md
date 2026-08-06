# M1-05 independent ZIP/APK security review 4

- Frozen commit: `5b8163f7c1db15951e4eaf55399cc8e54f4224af`
- Tree: `91c41e509ee32331a859012bac98e137942651f0`
- Base: `d32abe1d68d41910d72c90c3f9fc3d2831972756`
- Branch: `feat/m1-05-apk-repacker-and-alignment`
- Reviewer: independent read-only `m1_05_security_review_4`
- Result: **PASS**
- Findings: `P0=0`, `P1=0`, `P2=0`
- Completed: `2026-08-06T11:28:36+08:00`

The reviewer confirmed the exact HEAD/tree, clean worktree, and base. It changed
no tracked file and performed no network, device, emulator, push, commit, or
other Git mutation.

## Dependency finding closure

- JNA/JNA Platform is fixed to `5.19.1` in the catalog.
- `host:repacker` and downstream `host:cli` runtime locks resolve only `5.19.1`;
  independent `dependencyInsight` confirmed the locked runtime selection.
- The four Maven Central JAR/POM SHA-256 values match Gradle verification
  metadata.
- Provenance and third-party notices record official tag `5.19.1`, commit
  `1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d`, license, purpose, and Host
  distribution boundary.
- The dated dependency review records zero GitHub Advisory results for both
  exact Maven packages and confirms that `CVE-2021-44549` applies to Apache Sling
  Commons Messaging Mail, not JNA.
- Remaining JNA `5.6.0` checksums and Android lock references are limited to
  AGP lint/UTP build configurations and are absent from Host runtime locks.

## Full implementation conclusion

All review-1 and review-2 findings remain closed: sensitive-array ownership,
single-buffer Runtime verification, cleanup-before-publication, final-operation
native no-replace move, fail-closed platform identities, candidate/input/
container/parent rechecks, gap/race matrices, input immutability, unsigned output,
error sanitization, and plaintext business-DEX absence all passed independent
review.

## Independent validation

- offline `:host:repacker:test`: exit `0`, 34.2 seconds;
- Host runtime `dependencyInsight`: exit `0`;
- Governance and strict HandOff: exit `0`;
- diff, UTF-8 replacement, and sensitive-material scans: exit `0`, zero findings;
- all six deterministic M1-05 report hashes matched the CI constants;
- environment: Windows 10 `10.0.19045` amd64, Temurin `17.0.19+10`, Gradle
  `9.5.0`, Node `24.12.0`.

The security-review gate is closed. Ubuntu CI must still execute the Linux
`renameat2(RENAME_NOREPLACE)` path before merger-ready freeze.
