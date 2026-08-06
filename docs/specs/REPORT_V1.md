# Protection Report V1

`REPORT_V1` is the machine-readable result of the sole v0.1 business command:

```text
android-app-hardening protect --input <apk> --output <unsigned-apk> --report <json>
```

The normative JSON Schema is [`report-v1.schema.json`](report-v1.schema.json). Reports are UTF-8 without BOM, use LF, contain one trailing LF, and serialize fields in the order documented below. Readers must validate `schema_version` before consuming other fields.

## Top-level order

1. `schema_version`: integer constant `1`.
2. `tool`: product name and version.
3. `result`: `success`, `rejected`, or `failed`; stable error code or `null`; UTC RFC 3339 start/end times.
4. `input`: sanitized basename, SHA-256 path token, and input SHA-256 when readable.
5. `output`: sanitized output/report basenames and path tokens plus verified output, Manifest, AHDC, and ConfigV2 hashes on success.
6. `application`: inspected package/SDK/Application/original Factory and the fixed Shell Factory.
7. `signing`: input verification result, public certificate digests/schemes, `required=true`, `performed=false`.
8. `dex`: original DEX order, lengths and hashes plus AHDC version.
9. `abi`: actual input ABI set and injected Runtime ABI set.
10. `compatibility`: rules version and stable findings.
11. `stages`: entered stages in strict `inspect`, `signer`, `manifest`, `container`, `package`, `verify`, `publish` order.
12. `size`: input/output/delta bytes.
13. `errors`: stable `code`, `stage`, and language-independent `message_id`.

Nullable fields represent a stage that was not reached; they are not empty-string sentinels. A successful report has all seven stages with status `success`, an empty `errors` array, all artifact hashes, and `result.error_code=null`. A failure report contains no successful output hash and exactly one primary sanitized error.

## Privacy and integrity

- Basenames are limited to 128 non-control characters. Absolute paths are never serialized.
- `path_token` is lowercase SHA-256 of the field role plus sanitized basename and is only a cross-platform audit correlation value; it never incorporates or reveals a parent path.
- Certificate hashes are public identity material; certificates themselves are absent.
- Reports never contain DEX bytes, Runtime key material, stack traces, environment dumps, credentials, or signing capability.
- Report SHA-256 is recorded externally in M1-06 evidence. A report cannot safely contain its own digest because that would be self-referential.

## Stable exit mapping

| Exit | Category |
| ---: | --- |
| `0` | success |
| `2` | usage |
| `10` | INPUT/COMPAT |
| `11` | SIGNER |
| `12` | AXML |
| `13` | CONTAINER |
| `14` | PACKAGE |
| `15` | OUTPUT |
| `70` | INTERNAL/cancellation |

`stdout` is empty for every `protect` execution. `stderr` is exactly one line in the form `result/error_code/report_basename`; help and version are the only read-only global commands that write to stdout.
