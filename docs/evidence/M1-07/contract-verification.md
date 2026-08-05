# M1-07 contract verification

## Scope

本记录只验证 ADR 0008 和任务依赖的可机械实现性，不验证尚未存在的 Host/Runtime 产品实现。基准为 `main@225ec169661e2a366736be36b1249fb79faf3dcc`，工作分支为 `docs/m1-07-chunk-authenticated-container-contract`，Issue 为 #36。

## Fixed layout arithmetic

| Structure | Size proof | Result |
| --- | --- | ---: |
| HeaderV2 | `4+2+2+2+2+4+4+4+4+4+8+16+16+32+32+4+20` | 160 bytes |
| RecordV2 | `4+2+2+8+8+4+4+8+8+24+32+24` | 128 bytes |
| ChunkV2 | `4+4+8+8+4+4` | 32 bytes |
| ConfigV2 | ADR 0006 unchanged | 768 bytes |

`SPV1` remains `44 + lineage_count * 32`, with `lineage_count=1..16`.

## Canonical chunk arithmetic

- `chunk_plaintext_max=65,536`.
- For every record, `chunk_count=ceil(compressed_length / 65,536)` and `compressed_length > 0`.
- Every non-final chunk is exactly 65,536 bytes; final chunk is `1..65,536` bytes.
- Each payload span is `plaintext_length + 16`, so the maximum one-shot crypto input is 65,552 bytes.
- Global `chunk_count<=65,536` and final container length `<=2,147,483,647`; all multiplication/addition is checked before allocation or seek.
- Record and chunk offsets are canonical prefix sums. The final record/chunk end must equal declared compressed/payload size, which excludes holes, overlap and trailing data.
- The complete chunk table is never materialized. Host streams canonical entries; Runtime streams the authenticated table then re-reads entries from the same verified container.

## Authentication chain

```text
installed signer + Framework package name
-> ConfigV2 envelope recovers CEK
-> K_manifest authenticates HeaderV2 + SPV1 + record table + chunk table
-> authenticated HeaderV2 binds full ConfigV2 digest
-> K_record_i + nonce_prefix/chunk_ordinal + AAD authenticates each payload chunk
-> authenticated compressed chunk enters the record's continuous zlib inflater
-> original length and SHA-256 bind recovered DEX
-> all DEX succeed before ClassLoader exposure
```

The AAD repeats version, build ID, key slot, signer, package, full RecordV2 and full ChunkV2. Table topology is also covered by the manifest MAC. No Provider output is consumed before one-shot GCM completion.

## Dependency proof

```text
M1-02 -> M1-07
M1-01 + M1-02 + M1-07 -> M1-04
M1-04 -> M2-02 -> M2-03
```

M1-07 does not depend on M1-04, and there is no edge or path from M1-04 back to M1-07, so the new dependency creates no cycle. AHDC v1 remains historical only and cannot be selected by ConfigV2 or any current task contract.

## Required review focus

- JCA and Native one-shot GCM authentication semantics.
- nonce uniqueness under per-record derived keys.
- table/AAD/MAC coverage and package/config cross-binding.
- maximum-count arithmetic, chunk explosion resistance and complete file consumption.
- sensitive-buffer cleanup and preservation of the primary failure.
- transaction-owned completed/partial mapping cleanup before a Native handle exists, plus immediate successful cleanup of CEK/KEK/derived keys/AAD/chunk/inflater/crypto scratch.
- primitive, allocation-safe ownership from Native handle through same-handle authenticated metadata and internal `LoadedPayload`, including exception/OOM injection at metadata, buffers, loader and return boundaries.
- mechanical package/current-signer constant-time comparison and ordered lineage equality from immutable, non-secret authenticated metadata.
- exactly-once Guard ownership from `LoadedPayload` through atomic `VerifiedPayloadSession` return, including identity/config/session/return failure injection.
- M3 publication-state, close-count, mapping cleanup, partial-reference cleanup and primary/suppressed error evidence for both ownership windows.
- exact cross-module metadata getter signatures, ranges, lengths, nullability and deep-copy rules; zero payload class/resource lookup, Factory construction or bootstrap publication before Guard rechecks complete.
- executable trusted-source mapping for every Guard comparison, with Factory/config tamper assigned to Native authentication and metadata encoding errors assigned to M2-02 golden parsing rather than a fabricated second source.
