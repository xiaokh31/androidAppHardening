# M1-05 local security scan

Timestamp: `2026-08-06T09:46:44+08:00`

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
  required non-zero result proves the output is unsigned;
- no client APK, client path, certificate body, plaintext DEX dump, reusable key,
  token, credential, or UTF-8 replacement character is present;
- the input is opened only through read-only channels and is rehashed before
  planning, before atomic publication, and after publication;
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
- output publication has no non-atomic fallback, and the injected write, disk,
  close, verification, input-change, and move failures leave no candidate or
  published success output;
- exception messages expose stable codes and field labels only, never absolute
  paths, entry names, key material, package identifiers, or content bytes;
- no claim of absolute, unbreakable, or impossible-to-extract protection was
  added.

An independent read-only ZIP/APK security review remains mandatory on the clean
frozen commit. This local scan is implementation evidence, not a substitute for
that gate.
