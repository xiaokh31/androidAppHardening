# M2-07 independent read-only security review 1

## Frozen input

- Commit: `f428e4ac8cc12223ad6c6d2dabdf83c55f0f987a`
- Reviewer: independent `m2_07_security_review` Agent
- Timestamp: `2026-08-07T08:15:45+08:00`
- Result: **FAIL**; `P0=0`, `P1=3`, `P2=4`
- Files changed by reviewer: none

## Findings

1. **P1 — unauthenticated archive parsing and incomplete source-tree binding.** Build and KVM workflows extracted the network archive before hash verification, then the verifier checked only the archive, two license files and one version file. Required remediation: verify bytes before parsing, extract into a new empty temporary directory, verify the complete regular-file tree, write a locked identity stamp, atomically promote, and clean every failure path.
2. **P1 — incomplete cryptographic failure/boundary matrix.** The Host test omitted wrong nonce/tag lengths, insufficient output, zero-length AES, HKDF 8160/8161 and null/length combinations. Every required case must assert status plus output-size/zeroing semantics on Ubuntu and Windows.
3. **P1 — TF-PSA backend concurrency contract was unsafe.** `call_once` protected initialization only, while upstream does not guarantee the complete PSA API is thread-safe. The full AES/HKDF backend transaction must be serialized and covered by a multi-thread stress test.
4. **P2 — CI scanned Debug rather than Release Android ELF.** Both platforms must scan the four Release artifacts and retain exact hashes/symbol evidence.
5. **P2 — the machine lock did not verify all immutable identity fields.** Exact release/archive URL, tag object, commit, selected license, algorithm/ABI lists and complete extracted tree must reject mutations.
6. **P2 — Windows `clang-cl` path existed but its version was not asserted.** CI must require the reviewed `20.1.8` version and retain the immutable runner manifest evidence.
7. **P2 — CVE-2026-25832 was absent from the point-in-time table.** Mbed TLS 4.1.1 remains affected; it is currently unreachable only because no TLS target is built or linked. Any TLS enablement must invalidate the decision and force an upgrade/review.

## Positive evidence retained

- Official archive bytes/hash, annotated tag/commit, TF-PSA `1.1.1`, Apache-2.0 notices and license hashes matched the recorded upstream evidence.
- Existing local four-ABI ELF evidence contained only `libm`/`libdl`/`libc`, `BIND_NOW`, no upstream dynamic exports and no out-of-scope linked algorithm symbols.
- No customer APK, key, credential, archive, extracted source or plaintext DEX was committed.

This frozen commit is permanently rejected. A new clean SHA must close all seven findings, rerun local/remote gates, and receive a fresh full independent read-only review before merge or M2-02 resume.
