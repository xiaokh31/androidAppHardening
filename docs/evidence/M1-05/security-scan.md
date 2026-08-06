# M1-05 local security scan

Timestamp: `2026-08-06T11:22:43+08:00`

Scope:

- `host/repacker/src/main/kotlin/`
- `host/repacker/src/test/kotlin/`
- `host/repacker/build.gradle.kts`
- `.github/workflows/build.yml`
- `docs/evidence/M1-05/`

Results:

- production code contains no private-key, keystore, alias, password, signing
  executor, `ProcessBuilder`, `apksigner sign`, or `jarsigner` capability;
- the sole external `apksigner` call is a test-only `verify` command whose
  non-zero result must also contain `DOES NOT VERIFY` and the missing v1
  manifest/signature reason;
- no client APK, client path, certificate body, plaintext DEX dump, reusable key,
  token, credential, or UTF-8 replacement character is present;
- the direct Native bridge is fixed to the maintained official JNA/JNA Platform
  `5.19.1` tag, with Maven Central JAR/POM SHA-256 verification and a dated
  GitHub Advisory/NVD review in `dependency-security-review.md`; no vulnerability
  risk acceptance or dynamic version is used;
- one read-only input channel is held for inspection and raw entry transfer;
  input, container, candidate, and output-parent identities are captured and
  revalidated before publication using fail-closed platform file identities;
- entries are never extracted by untrusted name; the writer consumes bounded
  buffers, in-memory approved replacements, or the authorized container path;
- ZIP64, encryption, unsupported flags/methods, unsafe or normalized duplicate
  names, overlapping local ranges, malformed/trailing deflate data, invalid CRC,
  data descriptors in output, gaps, and APK Signing Blocks are rejected;
- JAR signature deletion is restricted to direct, case-insensitive standard
  `META-INF` signing names; unrelated `META-INF` data remains byte-preserved;
- the output verifier independently reparses the candidate, checks exact order,
  metadata, compressed/uncompressed hashes, alignment, fixed-entry bindings,
  Runtime slots, ABI set, signature absence, and plaintext DEX absence before the
  atomic move;
- output publication runs only after one-shot key-plan cleanup, uses fixed
  Windows/Linux native atomic no-replace primitives with no fallback, and is the
  final fallible operation; targeted structural/content/alignment, gap,
  identity-swap, output-race, write, disk, close, verification, and move failures
  leave no candidate or published success output;
- ConfigV2, `R_native`, build/key-slot IDs, materialized Runtime bytes, prepared
  payloads, verifier Runtime reads, digests, inflate buffers, and transfer
  buffers have transactional owners; `OwnedBytesPlan` covers construction and
  ownership transfer, Runtime reads materialize directly into one clearable
  array, and copy/plan/materialization/verifier OOM plus normal-success probes
  observe zeroed cleanup;
- exception messages expose stable codes and field labels only, never absolute
  paths, entry names, key material, package identifiers, or content bytes;
- no claim of absolute, unbreakable, or impossible-to-extract protection was
  added.

An independent read-only ZIP/APK security review remains mandatory on the clean
frozen commit. This local scan is implementation evidence, not a substitute for
that gate.
