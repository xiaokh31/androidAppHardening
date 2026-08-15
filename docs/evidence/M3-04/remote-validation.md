# M3-04 remote validation

- Frozen implementation head: `015e2b375a2fd24fa99c8748671f56ed142b19f9`
- Merger-ready evidence head: `c6d86663dee243ec6fabedbabeff2fd53063ae54`
- Merge commit: `d29664129be659cbc3deeda86be8c50c4f7250dd`
- PR: [#58](https://github.com/xiaokh31/androidAppHardening/pull/58), merged with expected-head protection; Issue #21 closed
- Scope: exact Ubuntu/Windows Build/Governance and one bounded API 29/36 x86_64 KVM run; no additional fuzz workflow.

## Exact implementation-head CI

| Workflow | Run | Result |
| --- | --- | --- |
| Build | [31864724608](https://github.com/xiaokh31/androidAppHardening/actions/runs/31864724608) | Ubuntu and Windows PASS at `015e2b3` |
| Governance | [31864724604](https://github.com/xiaokh31/androidAppHardening/actions/runs/31864724604) | Ubuntu and Windows PASS at `015e2b3` |
| M0-05 Linux KVM | [31864724589](https://github.com/xiaokh31/androidAppHardening/actions/runs/31864724589) | API 29 and API 36 x86_64 PASS at PR head `015e2b3` |
| Evidence-head Build | [31867027270](https://github.com/xiaokh31/androidAppHardening/actions/runs/31867027270) | Ubuntu and Windows PASS at `c6d8666` |
| Evidence-head Governance | [31867027316](https://github.com/xiaokh31/androidAppHardening/actions/runs/31867027316) | Ubuntu and Windows PASS at `c6d8666` |

Both KVM cells contain 9/9 passing fixture rows, zero retries, exact component-event checks, different-signer and authenticated-tag rejection before lookup/session publication, ARM-only classification, and successful cleanup.

## GitHub artifact metadata

| Artifact | ID | Bytes | Digest |
| --- | ---: | ---: | --- |
| `m0-05-api-29-x86_64-evidence` | `9241871356` | 3821991 | `sha256:728331385bb24f6f6fbbc7dcc7289082e045162872b222acd35915b471417d40` |
| `m0-05-api-36-x86_64-evidence` | `9241899220` | 3170432 | `sha256:d6bae2b5ffcf5916cda857f6822667efbe6898a3c3d4808e9f05514a0a09bd1e` |

The GitHub pull-request checkout exposed `GITHUB_SHA` as a synthetic merge commit even though run metadata bound `headSha` to `015e2b3`. The downloaded raw reports were deterministically re-evaluated by the repository `cell` command with that exact PR head. The workflow now reads `pull_request.head.sha` from the fixed GitHub event payload for pull-request runs, falls back to `GITHUB_SHA` for other events, and rejects a non-40-hex value. This prevents future cells from recording the synthetic checkout without relying on a workflow expression that is not valid for every event payload. No test result, device fact, event, hash of an input report, or pass/fail classification was edited.
