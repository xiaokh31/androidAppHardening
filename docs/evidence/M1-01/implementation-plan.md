# M1-01 implementation plan

## Frozen scope

- Issue: #6, `[M1-01] Untrusted APK inspector`.
- Branch: `feat/m1-01-untrusted-apk-inspector`.
- Base: `main@e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030`.
- Module: `host/apk-inspector` only, plus task evidence and coordinator HandOff.
- No signer verification, AXML mutation, DEX encryption, APK output, device execution, local emulator, M1-02/M1-03 or M2 implementation.
- Input corpus is generated entirely from repository source. No customer APK is used or retained.

## Contract-first design

The public entry point remains `ApkInspector.inspect(Path): ApkInspection`. Public models make byte arrays defensive copies and expose insertion-ordered, unmodifiable collections. Automation consumes `InspectionException.code`; messages contain only a sanitized file name, entry index, limit name and stable marker IDs.

The fixed task-card limits are represented by `InspectionLimits` and copied into every successful model. ZIP offsets and sizes are parsed as unsigned values into `Long`, and checked arithmetic guards every derived range. Entry payloads are never extracted. Every entry is streamed once for actual CRC and uncompressed-length verification; Manifest and DEX data are then materialized one at a time in fixed 64 KiB segments and released after parsing. Full class-name lists are not retained: DEX parsing keeps only class count, digest and stable compatibility marker IDs.

## Failure and compatibility order

1. `INPUT_IO` and the APK byte limit.
2. EOCD, central directory, local header, data descriptor, CRC, overlap and compression-budget checks.
3. UTF-8 path safety and exact/NFC duplicate checks.
4. bounded Binary AXML and DEX validation.
5. `minSdk`, Split/AAB/APKS, reserved project namespace, existing shell and unsupported framework gates.
6. bind every parser read to an initial 64 KiB block-hash snapshot on the same read-only handle, re-hash that handle and verify path identity at the end, and override any earlier result with `INPUT_CHANGED` when bytes or identity changed.

The marker table is source-controlled, ordered and exposed as `compatibility-rules-v1` in every successful model. It includes Flutter, Unity, React Native, Tinker, Sophix, RePlugin, VirtualAPK, DroidPlugin, common existing-shell markers, unsupported or ELF-mismatched native ABI and all three project-reserved namespaces.

## Verification map

- Positive: single DEX, 64 DEX boundary, custom/no Application and Factory, four supported ABIs, maximum 1024-byte UTF-8 path, STORED and raw-DEFLATE/data-descriptor entries.
- ZIP negative: malformed EOCD, central/local conflict, offset overflow, Zip64, encryption, CRC/size mismatch, compression bomb, traversal, exact duplicate and NFC collision.
- AXML negative: missing/duplicate/illegal/invalid-UTF-8 package, duplicate string offset, fixed resource-ID mismatch, namespace scope, namespaced core elements, raw/typed conflicts, malformed chunks and unsupported value encodings.
- DEX negative: non-contiguous names, count 65, repeated string-data offsets under a five-second gate, explicit magic versions, file-size/checksum/signature/fixed-table/data/map/descriptor corruption.
- Native negative: invalid/truncated ELF and directory ABI disagreement with complete ELF32/ELF64 header, class, endianness, `e_version`, `e_ehsize` and `e_machine`.
- Compatibility negative: Split, AAB, APKS, Flutter, Unity, React Native, Tinker, Sophix, plugin Runtime, existing shell, unsupported ABI and each reserved namespace.
- Lifecycle: input SHA before/after, `INPUT_CHANGED`, cancellation, Windows rename-after-close and zero extraction artifacts.
- Fuzz: deterministic seed `0x4d312d3031`, exactly 10,000 generated structural samples, periodic duplicate-run determinism checks.
- Cross-platform: committed source produces canonical model/error JSON under Gradle reports; Windows and Ubuntu CI outputs must be byte-identical before completion.

## Security review gate

The independent read-only reviewer is pre-designated as `m1_01_security_review`. Review starts only after implementation, local gates and evidence are committed and the exact SHA is frozen. The branch is not published and no PR is created until P0/P1/P2 are all zero. A failed review returns the task to implementation and requires a new frozen SHA.
