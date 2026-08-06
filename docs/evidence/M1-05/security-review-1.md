# M1-05 independent ZIP/APK security review 1

- Frozen commit: `bb748f68ec3cfac255124c6bdfd0bbb242bed1c1`
- Base: `d32abe1d68d41910d72c90c3f9fc3d2831972756`
- Reviewer: independent read-only `m1_05_security_review`
- Result: **FAIL**
- Findings: `P0=0`, `P1=4`, `P2=1`

The reviewer changed no file, performed no network, device, emulator, push, or
Git operation, and ran the repository-local offline `:host:repacker:test` with
exit `0` in 49 seconds. Passing tests do not close the findings below.

## P1 findings

### 1. Key plan cleanup happens after publication and is skipped by early failures

`ApkRepacker.kt:24-31` performs path, input-hash, and binding checks before
`KeyPackagingPlanV2.consume`; any failure leaves the one-shot material owned by
the caller rather than clearing it as part of the failed repack transaction.
`ApkRepacker.kt:36-90` also verifies and atomically publishes inside the consume
callback, so `consume` clears ConfigV2/`R_native`/build/key-slot material only
after a successful output is already visible.

Minimal repair: put all fallible request checks inside the one-shot consume
transaction, return only a verified unpublished candidate, let consume cleanup
finish, and perform the atomic move afterward. Add early-validation and
cleanup-before-move observation tests.

### 2. Sensitive temporary arrays do not have transactional ownership

`ApkRepacker.kt:106-110` and `RuntimeMaterializer.kt:19-22` obtain ConfigV2,
`R_native`, build ID, and key-slot copies sequentially before establishing a
nullable-owner cleanup domain. An OOM or copy failure can orphan earlier copies.
The Runtime mapping can also orphan already-materialized SO buffers if a later
ABI fails. `ApkRepacker.kt:158-190` creates further ConfigV2/Runtime payload and
ExpectedOutput copies that survive normal success, while
`OutputVerifier.kt:40` materializes a Runtime SO containing `R_native` without
clearing it after verification.

Minimal repair: register every returned array in a nullable/list owner before
the next fallible operation; make prepared payloads explicitly closeable and
clear them after writer close; clear verifier materializations in `finally`;
inject copy/materialization/output-verifier OOM and success cleanup tests.

### 3. Input/output path identity checks are not bound to the channels used

`ApkRepacker.kt:27`, `:38`, `:60`, and `:74` hash or open the input through
separate path resolutions. `ApkRepacker.kt:368-383` reads an input file key and
resolved output parent but discards the key and does not revalidate either
identity. Replacing an input inode or parent symlink between checks can make the
inspection hash, copied ZIP bytes, final hash, and atomic destination refer to
different filesystem objects.

Minimal repair: hold one input archive channel for initial/final hashing and
copying, capture input/parent real path plus file key, revalidate identity and
output absence immediately before and after move, and prove inode/parent-swap
injections fail closed.

### 4. Required independent verifier mutation matrix is not implemented

`docs/evidence/M1-05/implementation-plan.md:58-61` claims coverage for duplicate
and conflicting entries, compressed/misaligned fixed assets, altered preserved
bytes, Runtime slot mismatch, original DEX, signature material, and trailing/gap
structures. The only candidate mutation in
`RepackerSelfTest.kt:136-144` flips one generic byte. This does not prove the
individual fail-closed branches or that a parser/writer common-mode defect is
detected.

Minimal repair: add named, targeted candidate mutations for every claimed class,
including local/central descriptor and offset topology, and record each stable
`OUTPUT_VERIFICATION_FAILED` result in the deterministic matrix.

## P2 finding

### 5. One error path can expose an untrusted entry name

`ZipIo.kt:448` uses `entry.expected.name` as the `PackageException.field` when
an alignment invariant fails, contradicting the evidence claim that exceptions
contain only stable labels. A crafted name can therefore reach logs on that
failure path.

Minimal repair: use a constant field or a short digest-derived identifier and
add an exception-message scan using control characters and path-like entry
names.

## Additional validation requirement

The official `apksigner` test currently asserts only a non-zero exit. The next
freeze must also assert the pinned output is the expected unsigned rejection
(`DOES NOT VERIFY` plus the absent v1 manifest/signature indication), so an
unrelated malformed-APK failure cannot satisfy the unsigned gate.

The frozen commit is invalidated. Publication and M1-06/M2 remain blocked until
all findings are fixed, a new clean SHA is frozen, and a fresh full independent
review returns P0/P1/P2 all zero.
