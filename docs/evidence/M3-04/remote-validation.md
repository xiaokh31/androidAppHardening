# M3-04 remote validation

- Frozen implementation head: `015e2b375a2fd24fa99c8748671f56ed142b19f9`
- PR: [#58](https://github.com/xiaokh31/androidAppHardening/pull/58), closing Issue #21
- Scope: exact Ubuntu/Windows Build/Governance and one bounded API 29/36 x86_64 KVM run; no additional fuzz workflow.

## Exact implementation-head CI

| Workflow | Run | Result |
| --- | --- | --- |
| Build | [31864724608](https://github.com/xiaokh31/androidAppHardening/actions/runs/31864724608) | Ubuntu and Windows PASS at `015e2b3` |
| Governance | [31864724604](https://github.com/xiaokh31/androidAppHardening/actions/runs/31864724604) | Ubuntu and Windows PASS at `015e2b3` |
| M0-05 Linux KVM | [31864724589](https://github.com/xiaokh31/androidAppHardening/actions/runs/31864724589) | API 29 and API 36 x86_64 PASS at PR head `015e2b3` |

Both KVM cells contain 9/9 passing fixture rows, zero retries, exact component-event checks, different-signer and authenticated-tag rejection before lookup/session publication, ARM-only classification, and successful cleanup.

## GitHub artifact metadata

| Artifact | ID | Bytes | Digest |
| --- | ---: | ---: | --- |
| `m0-05-api-29-x86_64-evidence` | `9241871356` | 3821991 | `sha256:728331385bb24f6f6fbbc7dcc7289082e045162872b222acd35915b471417d40` |
| `m0-05-api-36-x86_64-evidence` | `9241899220` | 3170432 | `sha256:d6bae2b5ffcf5916cda857f6822667efbe6898a3c3d4808e9f05514a0a09bd1e` |

The GitHub pull-request checkout exposed `GITHUB_SHA` as a synthetic merge commit even though run metadata bound `headSha` to `015e2b3`. The downloaded raw reports were deterministically re-evaluated by the repository `cell` command with that exact PR head. The workflow is corrected to use `github.event.pull_request.head.sha || github.sha`, preventing future cells from recording the synthetic checkout. No test result, device fact, event, hash of an input report, or pass/fail classification was edited.
