# M2-10 first and only API 36 startup diagnostic

- Timestamp: `2026-08-18T04:40:18Z` to `2026-08-18T04:47:18Z`
- Head: `977b0585b5a0b3c5f1270ffb39be8e4e1ef6a03f`
- Run: [`32099991400`](https://github.com/xiaokh31/androidAppHardening/actions/runs/32099991400)
- Job: `95598521722`
- Event / attempt: `push` / `1`
- Environment: `api36-x86_64-r2-emulator-37.1.11`
- Boot ID hash prefix: `c851d1f78221`
- Artifact: `9311346051`, `m2-10-first-startup-diagnostic`, 733997 bytes
- Artifact service digest: `sha256:01b9b2f8fed8865565ca31ea7ddb468396a57f526cd8386d0a1bf13d19c8274d`
- Result: `BLOCKED`; the workflow intentionally concluded failure because no stage was eligible

## Fixed partitions

| Stage | Early P50, samples 1..7 | Late P50, samples 8..15 | Eligible in both at 30 ms |
|---|---:|---:|---|
| `signer_source` | 30,412,616 ns | 17,530,626 ns | No |
| `binding_precheck` | 1,828,598 ns | 1,175,759 ns | No |
| `payload_open` | 2,365,514 ns | 1,838,571 ns | No |
| `metadata_policy` | 8,109,967 ns | 5,900,377 ns | No |
| `session_commit` | 55,372 ns | 51,946 ns | No |
| `bootstrap_factory` | 78,812 ns | 56,493 ns | No |

`eligibleStages` is empty and `selectedStage` is `null`. ADR 0016 therefore prohibits a production optimization and prohibits a replacement diagnostic on unchanged product bytes.

## Immutable files

| File | Size | SHA-256 |
|---|---:|---|
| `artifact-manifest.json` | 973 | `e3a3532efb9715ae244fd68ad73416198d927c803acf58ef595c4f0120624fb5` |
| `baseline.apk` | 29960 | `1db5221c3c7618fc0d31667c8f79e6c4dd094b2d9ce72870b91411064c33e200` |
| `profiling.apk` | 1287874 | `bb8fadfc9c896a3331219e37434dcdc748357552635861da963471460f80deaf` |
| `runtime-startup-raw.json` | 12273 | `d8840c50f956b8cb02d77e875ed1d02503844b68aa472859ddee901643c1e9e7` |
| `runtime-startup-stages.json` | 1728 | `6ecd595ddc2eab3b97595b83b4dffcba66be713e6fb1352fabb546ebd8563be3` |

The report records 5 warmups, 15 retained samples, one report, cleanup passed and the exact head/run/job/attempt/environment/boot identity. Local revalidation reproduced the expected fail-closed message `M2-10 validation failed: diagnostic has no eligible stage`. No retry, ARM run or M3-05 A/B run was started.
