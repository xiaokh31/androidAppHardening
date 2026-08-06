# AHDC v2 encrypted DEX container

## Status and scope

AHDC v2 is the only container format accepted by v0.1. It packages the canonical
`classes.dex`, `classes2.dex`, ... sequence from one inspected standalone APK.
AHDC v1 and ConfigV1 are rejected; there is no compatibility fallback.

This specification describes the `host:container` boundary only. It does not inject
the runtime, rewrite the APK, sign an output, load DEX on Android, or provide a CLI.
The format raises the cost of static extraction but does not make client-side DEX
or key recovery impossible for an attacker who controls the process.

## Public host API

```text
DexContainerBuilder(inputApk).build(inspection, signer, encryptedTemp)
  -> ContainerBuildResult(descriptor, keyPackagingPlan)

DexContainerVerifier.verify(container, expectedBinding)
  -> DexContainerDescriptor
```

`inputApk` is opened read-only. `encryptedTemp` must not already exist. A failed
build removes its internal partial file and does not publish `encryptedTemp`.
`KeyPackagingPlanV2` owns ConfigV2, one native share, build/key-slot identifiers,
and the selected ABI set in memory. It can be consumed exactly once and clears its
owned copies after consumption or `close()`; it is not a persistent file format.

## File layout

All integers are unsigned little-endian fixed-width values. The file is exactly:

```text
HeaderV2[160]
SPV1[signer_policy_size]
RecordV2[dex_count][128]
ChunkV2[chunk_count][32]
Payload[payload_size]
```

There is no padding, hole, overlap, or trailing data. `dex_count` is `1..64`,
`chunk_count` is `1..65536`, and the complete container is at most 2,147,483,647
bytes.

### HeaderV2 (160 bytes)

| Offset | Size | Field | Required value or rule |
| ---: | ---: | --- | --- |
| 0 | 4 | magic | ASCII `AHDC` |
| 4 | 2 | major | `2` |
| 6 | 2 | minor | `0` |
| 8 | 2 | header_size | `160` |
| 10 | 2 | flags | `0` |
| 12 | 4 | dex_count | `1..64` |
| 16 | 4 | signer_policy_size | exact SPV1 size |
| 20 | 4 | record_table_size | `dex_count * 128` |
| 24 | 4 | chunk_count | `1..65536` |
| 28 | 4 | chunk_table_size | `chunk_count * 32` |
| 32 | 8 | payload_size | exact encrypted payload bytes |
| 40 | 16 | build_id | per-build CSPRNG value |
| 56 | 16 | key_slot_id | per-build CSPRNG value |
| 72 | 32 | config_sha256 | SHA-256 of the complete 768-byte ConfigV2 |
| 104 | 32 | manifest_mac | HMAC-SHA-256 described below |
| 136 | 4 | chunk_plaintext_max | `65536` |
| 140 | 20 | reserved | all zero |

### SPV1

SPV1 uses ADR 0004 byte-for-byte: magic `SPV1`, schema version `1`, flags `0`,
one to sixteen lineage digests, and a 32-byte current signer digest. Lineage is
oldest to newest, contains no duplicate, and its last item equals the current
signer.

### RecordV2 (128 bytes)

| Offset | Size | Field | Required value or rule |
| ---: | ---: | --- | --- |
| 0 | 4 | ordinal | zero-based canonical DEX ordinal |
| 4 | 2 | name_length | `1..24` |
| 6 | 2 | flags | `0` |
| 8 | 8 | original_length | `1..536870912` |
| 16 | 8 | compressed_length | positive zlib stream length |
| 24 | 4 | chunk_count | canonical ceil division by 65536 |
| 28 | 4 | first_chunk_index | cumulative prior chunk count |
| 32 | 8 | payload_offset | cumulative prior encrypted payload length |
| 40 | 8 | nonce_prefix | per-record non-zero CSPRNG value |
| 48 | 24 | name | canonical ASCII name followed by zero fill |
| 72 | 32 | original_sha256 | SHA-256 of the original DEX |
| 104 | 24 | reserved | all zero |

Record zero is `classes.dex`; record `n > 0` is `classes{n+1}.dex`. Original DEX
order is preserved.

### ChunkV2 (32 bytes)

| Offset | Size | Field | Required value or rule |
| ---: | ---: | --- | --- |
| 0 | 4 | record_ordinal | owning record |
| 4 | 4 | chunk_ordinal | zero-based within the record |
| 8 | 8 | compressed_offset | cumulative compressed bytes in the record |
| 16 | 8 | payload_offset | cumulative encrypted bytes in the file payload |
| 24 | 4 | plaintext_length | `1..65536`; non-final chunks are `65536` |
| 28 | 4 | reserved | `0` |

Each payload chunk is exactly:

```text
ciphertext[plaintext_length] || gcm_tag[16]
```

The chunk table is canonical, continuous, non-empty, non-overlapping, and exactly
covers every record's compressed stream and the complete payload.

## Compression and two-pass input binding

Each DEX is one continuous zlib-wrapped DEFLATE level-9 stream with no dictionary.
Chunking happens after compression and does not reset zlib state.

The builder reads each DEX twice without writing original or compressed plaintext
to disk. Pass one records original length, original SHA-256, compressed length, and
canonical chunk topology. Pass two repeats the same compression and encrypts each
full chunk immediately. Any length, digest, compressed-length, or final APK hash
difference fails with `CONTAINER_INPUT_CHANGED`.

## Cryptographic contract

The OS CSPRNG supplies a fresh 256-bit CEK, root material, Java share, 96-bit wrap
nonce, build ID, key-slot ID, and non-zero nonce prefix for every record. ConfigV2
and `R_native = R XOR R_java` follow ADR 0006 exactly.

```text
K_manifest = HKDF-SHA-256(CEK, build_id, "AHDC manifest v2", 32)
K_record_i = HKDF-SHA-256(
  CEK,
  build_id,
  "AHDC record v2" || ordinal_u32le,
  32
)
```

Chunk nonce:

```text
nonce_prefix[8] || chunk_ordinal_u32le
```

Chunk AAD:

```text
ASCII("AHDC-GCM-V2")
|| header[4,8)
|| build_id
|| key_slot_id
|| current_signer_sha256
|| package_name_sha256
|| RecordV2
|| ChunkV2
```

Each chunk uses a one-shot standard AES-256-GCM operation. The manifest MAC covers,
in file order, HeaderV2 with bytes `[104,136)` zeroed, SPV1, the complete record
table, and the complete chunk table. Payload bytes are authenticated by per-chunk
GCM tags.

ConfigV2 is exactly 768 bytes and has `container_major=2`. Its CEK envelope uses
the ADR 0006 KEK and config prefix `[0,132)` as AAD. Header `config_sha256` binds
the entire config, including the strictly encoded original AppComponentFactory.

## Verification order and limits

1. Reject wrong version, flags, reserved bytes, lengths, counts, arithmetic, file
   size, topology, overlaps, holes, and trailing bytes.
2. Cross-check SPV1, package digest, canonical DEX order, lengths, and digests with
   `ExpectedBinding`.
3. Verify ConfigV2 digest and recover CEK; verify the manifest before payload work.
4. Read at most `65536 + 16` payload bytes, authenticate the complete chunk with a
   one-shot GCM operation, then and only then pass authenticated compressed bytes
   into that record's continuous inflater.
5. Require an exact zlib end, no dictionary, concatenated stream, unconsumed input,
   or trailing bytes, and match original length and SHA-256.
6. Rehash the container after verification and fail if it changed during use.

No code path allocates a buffer proportional to DEX, payload, or total chunk-table
size. Individual crypto, compression, inflater, and I/O buffers stay below the
1 MiB working-buffer contract. Temporary keys, shares, AAD, authenticated compressed
chunks, and inflater scratch are cleared on success and failure paths.

## Error codes

- `CONTAINER_FORMAT`
- `CONTAINER_VERSION`
- `CONTAINER_LIMIT_EXCEEDED`
- `CONTAINER_INPUT_CHANGED`
- `CONTAINER_CRYPTO`
- `CONTAINER_AUTH_FAILED`
- `CONTAINER_KEY_MATERIAL`
- `CONTAINER_RANDOM_FAILED`

Errors contain only a stable code and safe field label. They do not expose keys,
shares, nonces, DEX content, customer paths, signer material, or package content.
