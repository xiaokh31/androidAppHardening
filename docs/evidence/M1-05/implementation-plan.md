# M1-05 implementation plan

## Scope

Implement only the `pre-cli` Host APK assembler/repacker contract in
`host/repacker`. The task consumes already-verified M1-01/M1-02/M1-03/M1-04
models and produces a distinct, unsigned, independently re-read APK. It does
not add CLI orchestration, product signing, Android Runtime implementation, or
device execution.

## Fixed inputs and outputs

- Input: read-only standalone APK plus its `ApkInspection`, `SignerPolicyV1`,
  transformed Manifest, AHDC v2 container descriptor/path, bootstrap DEX,
  four-template `RuntimeBundle`, and one-shot `KeyPackagingPlanV2`.
- Output: an initially absent path on the same file system, atomically
  published only after independent verification.
- Result: immutable `OutputVerification` with entry changes, hashes,
  alignment offsets, effective ABI set, container/config/Manifest identity,
  input immutability, and `signingPerformed=false`.

## Contract boundaries

- Preserve every allowed input entry's raw compressed payload, method, CRC,
  uncompressed bytes, and approved ZIP metadata.
- Remove only original `classes*.dex`, standard JAR signature entries, the APK
  Signing Block that naturally disappears during reconstruction, and the
  replaced Manifest bytes.
- Append one DEFLATED level-9 bootstrap `classes.dex`, canonical STORED AHDC
  and ConfigV2 assets, and only ADR-0005-selected Runtime libraries.
- Materialize each selected Runtime template only after validating its SHA-256,
  ELF class/machine, unique read-only 104-byte `.ah_share_v1` placeholder, and
  ABI ID. Do not patch bootstrap DEX.
- Align Runtime/customer STORED `.so` data at 16 KiB, AHDC/ConfigV2 at 4 KiB,
  and every other STORED entry at 4 bytes.
- Reject normalized/symlink/hardlink aliases, existing output, unsupported
  input ABI, output tamper, write/close/disk-full failures, and unsupported
  atomic move without modifying the input or publishing a success output.

No new ADR is required: ADR 0005, ADR 0006, ADR 0007, ADR 0008, the architecture,
and the M1-05 task card already freeze layout, ABI, key materialization,
alignment, output publication, and unsigned-output policy.

## Acceptance mapping

1. `RepackerSelfTest` covers single/multi-DEX repack, exact signature removal,
   non-signature `META-INF` preservation, entry comparison, and input hashes.
2. ABI cases cover Java-only, ARM-only, x86-only, mixed supported ABI, and
   unsupported ABI rejection without inventing customer ABI support.
3. Runtime materializer tests cover four ELF machines, template digest,
   placeholder uniqueness/size/flags/ABI, slot bindings, untouched bootstrap,
   unselected ABI absence, and one-shot plan cleanup.
4. Alignment tests inspect data offsets and run pinned `zipalign -c -P 16 -v 4`.
5. External tests run pinned `aapt2 dump xmltree` and require pinned
   `apksigner verify` to fail specifically because the output is unsigned.
6. Failure tests cover same path, symlink/hardlink alias, existing target,
   disk-full/write/close faults, verifier tamper, and atomic-move refusal.
7. Independent verifier mutations cover duplicate/conflicting entries,
   compressed or misaligned fixed assets, altered preserved bytes, Runtime
   slot mismatch, original DEX presence, signature material, and trailing/gap
   structures.
8. Canonical entry/error/alignment reports are byte-compared on Ubuntu and
   Windows CI; randomized APK/container bytes are excluded from equivalence.

## Evidence and review sequence

1. Run the module matrix and pinned external-tool cross-check on Windows.
2. Run repository Governance, strict HandOff, diff, UTF-8, sensitive-material,
   signing-capability, plaintext-DEX, and ignored-artifact scans.
3. Freeze a clean implementation commit and start independent read-only
   reviewer `m1_05_security_review`; any finding invalidates that freeze.
4. After P0/P1/P2 reach zero, push the fixed branch, create the sole draft PR
   closing Issue #10, and require Ubuntu/Windows Build and Governance.
5. Re-freeze merger-ready HandOff, repeat CI at the exact head, merge with
   expected-head protection, then complete post-merge `main` strict/CI and
   README/HandOff synchronization before marking M1-05 done.
