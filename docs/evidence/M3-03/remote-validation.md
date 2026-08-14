# M3-03 remote validation

- Implementation head: `f53989e83b8a030139ec3e564ebfb41bdb81129a`
- Merge commit: `af0fe5c5d0e9098d8cca86b3d5de3e09ed8412fb`
- PR: [#55](https://github.com/xiaokh31/androidAppHardening/pull/55), closing Issue #20
- Device boundary: Host-only; the automatically triggered KVM and M3-02 fuzz workflows were cancelled because M3-03 changes no device or fuzz boundary.

## Exact-head CI

| Workflow | Run | Result |
| --- | --- | --- |
| Cross-platform equivalence | [31847937221](https://github.com/xiaokh31/androidAppHardening/actions/runs/31847937221) | seed, Windows, Ubuntu and final compare PASS |
| Build | [31847937347](https://github.com/xiaokh31/androidAppHardening/actions/runs/31847937347) | Ubuntu 24.04 and Windows 2025 PASS |
| Governance | [31847937260](https://github.com/xiaokh31/androidAppHardening/actions/runs/31847937260) | Ubuntu 24.04 and Windows 2025 PASS |

The summary reports `status=pass`, 9 fixtures, 4 platform runs, 36 compared outputs, deterministic-field equality, randomized-field distinction, independent authentication/decryption, immutable inputs, unsigned outputs, equivalent negative errors and zero absolute-path findings.

## GitHub artifact digests

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `m3-03-fixed-inputs` | 676366 | `cb1f8f9e2c0e91ced71e58bff12ddc32428f9a0bd41ae13bb1e2a3b0deb8c9ec` |
| `m3-03-runtime-bundle` | 692512 | `3218f5f408f377cc5c7b3bf69c5f5758689bdb9825f823121cb470d0a4215809` |
| `m3-03-windows` | 14390538 | `99b42ef16314ccb703c825f416ad5f90e348c59f74e94760737ad32b526e6fd9` |
| `m3-03-ubuntu` | 14390542 | `2c84b6731c185ed528c209790a6c913f94bdb36a05658e0a13671b435bfecc83` |
| `m3-03-equivalence-summary` | 583 | `6a4220157a96646de7b1c30fed410a75cdcc181bf551625d61176e5708136212` |

## Downloaded evidence-file hashes

| Evidence | Windows SHA-256 | Ubuntu SHA-256 |
| --- | --- | --- |
| `semantic-manifests.jsonl` | `0e83f2ff53d93f5a744e787356b414fe0f4fe2144a44dab891867dee05421d69` | same |
| `reports.jsonl` | `9646f105ea56d76c87cd0e1f2599d3c27758c9d6192af037a3ba7ed114e68cef` | same |
| `zip-metadata-diff.json` | `a2f60fdf121d95ea44612e01b638c15f48ab4ddd6054d8febfee7c7508c31f0d` | same |
| `negative-results.json` | `a6559f846a1cb347bb02b677001517d1a0b0198ad7b82649a62175d1e85a2f3d` | same |
| `random-fields.jsonl` | `d3ee73040ee08482a90aae037b6fe0c50d6ad0f4cfd506491d83512d2d4cf9cb` | `ed760b12c71771c303ad74a0868c5c3979ff9a7266c98a819fb923e1b5bd3e92` |
| `hashes.sha256` | `a1017ec72bbe88007f0d5bcfb16adf839886341332b175ceaa93ff3a0291d448` | `78b1391dbcd83125481f96395c816c69c3f7352f66e009c4c4ffaea30d9ada64` |

`equivalence-summary.json` is 3499 bytes with SHA-256 `9dfdb791d005f119e28063fec179c936e6400bcf3138c2f3755d79f4c6fd6383`. Random and full-output hashes intentionally differ; the independently parsed stable semantics and report projections are byte-identical.
