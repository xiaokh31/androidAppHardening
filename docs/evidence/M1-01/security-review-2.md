# M1-01 independent review 2

## Scope

- Reviewer: independent read-only `m1_01_reliability_review_2`.
- Reviewed branch: `feat/m1-01-untrusted-apk-inspector`.
- Reviewed frozen commit: `02e6334e916581f3d49c89ec512f6e9a9ec4a245`.
- Core implementation: `d3dbfaa8ce4317d8b394f22478ddbb185fd480cb`.
- Reviewer made no file, Git, remote, device or emulator changes.

## Conclusion

FAIL: P0 `0`, P1 `4`, P2 `3`.

## Findings

1. P1: Binary AXML resource-map values were not bound to fixed Android attribute IDs; element namespaces, attribute namespace scope and raw/typed string agreement were incomplete, while the positive fixture omitted real resource-map and namespace chunks.
2. P1: DEX string IDs could reuse one string-data offset and force repeated descriptor scans, allowing CPU work disproportionate to file bytes.
3. P1: Native ABI reporting trusted only `lib/<abi>/` paths and did not verify ELF magic, class, byte order or `e_machine`; the positive `.so` fixtures were single bytes.
4. P1: Required deterministic regressions were missing for package absence/duplication/UTF-8, actual ZIP CRC corruption, independent DEX header/table/descriptor failures, AXML semantic conflicts and ELF/path ABI mismatch.
5. P2: DEX magic accepted nonexistent version `036` through a numeric range.
6. P2: The compatibility marker table had no explicit version in the result model or evidence.
7. P2: HandOff still described the already-created frozen commit as a future action.

## Gate effect

The reviewed frozen commit is invalid for completion or publication. All findings require remediation, a new frozen SHA and a new independent read-only review. The reviewer could not independently rerun Gradle because its isolated offline cache lacked the required artifacts; its static findings and clean/read-only Git verification were conclusive for the FAIL gate.
