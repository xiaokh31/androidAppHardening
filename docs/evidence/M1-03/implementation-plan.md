# M1-03 implementation plan

## Scope

- Task: `M1-03` / Issue `#8` / branch `feat/m1-03-binary-axml-transformer`.
- Consume M1-01's verified `ManifestSummary` and original binary `AndroidManifest.xml` bytes.
- Produce a new binary Manifest whose only allowed semantic change is the application `android:appComponentFactory` value becoming `ah.runtime.bootstrap.ShellAppComponentFactory`.
- Preserve the original `android:name`, metadata, elements, attributes, typed values, resource references, existing string indexes, namespace sequence and unknown chunk bytes/order.
- Keep APK/ZIP output, DEX handling, signing, ConfigV2 encoding, Runtime changes and all M1-04/M2 behavior out of scope.

## Existing decisions

- ADR 0003 fixes the API 29 public `AppComponentFactory` hook and original Factory delegation.
- ADR 0007 fixes `ApplicationInfo.sourceDir` as the startup configuration source and forbids new Manifest metadata.
- The architecture and task card already fix the single-attribute whitelist, Shell Factory name and compileSdk 36 resource ID `0x0101057a`; no new ADR is required.

## Public contract

- `BinaryManifestTransformer.transform(ByteArray, ManifestTransformRequest): ManifestTransformResult`.
- `ManifestTransformRequest` accepts one immutable M1-01 `ManifestSummary`; its Shell Factory value is a compile-time constant and cannot be caller-selected.
- `ManifestTransformResult` returns defensive copies of transformed bytes and before/after SHA-256 plus an immutable `ManifestSemanticDiff`.
- Stable errors are `AXML_MALFORMED`, `AXML_LIMIT_EXCEEDED`, `AXML_APPLICATION_MISSING`, `AXML_RESERVED_COLLISION`, `AXML_UNSUPPORTED_ENCODING` and `AXML_DIFF_VIOLATION`.
- Public exceptions expose only the stable code and optional numeric chunk type/offset. They have no raw Manifest, input path or nested cause.

## Transform and failure contract

1. Copy caller bytes, enforce the M1-01 16 MiB Manifest limit and parse every chunk with checked arithmetic.
2. Validate UTF-8/UTF-16 string pools, style references, resource map, balanced namespaces/elements, attributes, typed values, exactly one direct application and one `uses-sdk`.
3. Match package digest, SDK values and normalized original Application/Factory against the supplied M1-01 summary.
4. Reject an existing Shell Factory, conflicting reserved attribute name/resource ID, duplicate application or missing active Android namespace.
5. Append only missing strings, preserving every old string index. Preserve old resource-map entries and append only the fixed Factory resource ID when needed.
6. Replace or append exactly one application Factory attribute. Copy every other chunk byte-for-byte.
7. Reparse the result and compare a full ordered semantic event model with only the application Factory attribute removed from both sides.
8. Return bytes only when original string indexes, resource-map prefix, unknown chunk hashes/order and all non-whitelisted semantics match. Otherwise return `AXML_DIFF_VIOLATION` with no result.

The parser additionally caps chunks and active namespaces before object allocation, maintains constant-time namespace activity counts, applies a global input-proportional style-span work budget with duplicate-offset caching, and aggregates unknown-chunk bytes/order into one anchored digest. Existing extended attribute records preserve every byte after the standard 20-byte fields.

## Test contract

- Cover UTF-8 and UTF-16 pools, absent/custom Application, absent/existing Factory, absent/existing resource map, metadata, resource reference and an unknown XML-node chunk.
- Cover non-zero extended Factory attributes, high-bit unsigned resource IDs/typed values, namespace/chunk budgets and overlapping style-chain amplification.
- Independently link a real Manifest with pinned Build Tools `36.1.0` `aapt2`, transform it and require `aapt2 dump xmltree` to retain Application/metadata and expose the Shell Factory.
- Cover truncation, oversized root, unsupported flags, excessive string count, truncated string length/resource map, duplicate/missing application, excessive nesting, Shell/reserved-ID collision and summary mismatch.
- Run at least 5,000 deterministic malformed samples with seed `0x4d313033`; no non-AXML exception, crash or unbounded sample allocation is allowed.
- Emit canonical transform, error, fuzz and `aapt2` reports for Windows/Ubuntu byte-equivalence CI.
- API 29/36 install/startup parsing remains a separate required device gate and cannot be substituted by Host parser or `aapt2` evidence.

## Security review

- Independent reviewer: `m1_03_security_review`.
- Start only after implementation, Host evidence and device-test harness are committed and frozen.
- Completion requires all P0/P1/P2 findings closed or the task remains blocked.
- Review 1 of frozen commit `9fee22df524f0465f5a9fc310bec153b6d37696b` failed with P0 `0`, P1 `3`, P2 `1`; it is invalid and archived in `security-review-1.md`.
