# M3-14 terminal blocked evidence

- Task: M3-14 / Issue #82 / Draft PR #83
- Execution identity: `96837a115f89e3866d56c315928314c9532b4b635859b6f5860c8d1c442e5357`
- Product tuple: `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`
- Publication head: `9fe48737d97853d1566cc2e642009d8ff1b8ab52`
- Terminal request head: `b0771d4853e0a7de7fb9db802cac719e34c67229`
- Timestamp verified: `2026-08-23T10:18:45+08:00`

## Canonical diagnostic

- Workflow run: `32611656930`; job: `97125597267`; event: `push`; attempt: `1`; conclusion: `failure`.
- Publication/ancestry, first-and-only history, pinned Ubuntu runner, JDK/Node, pinned API 36 r2 plus Emulator 37.1.11 preparation, canonical input retrieval, Native crypto preparation and exact Release-surface build steps all completed successfully.
- Step 13, `Execute first-and-only API 36 attribution diagnostic`, failed. The retained official API pages do not expose a more precise boundary inside that monolithic step, so no preflight, campaign, sample or cleanup detail is claimed.
- Upload step 14 was skipped.
- Official artifacts response: `{"total_count":0,"artifacts":[]}`.
- No retry, replacement run or ARM/API 29/physical-device substitute is permitted.

## Terminal evidence

- Direct-child request changed only `docs/evidence/M3-14/diagnostic-terminal-request.json` and bound diagnostic run `32611656930` to the reviewed publication bytes.
- Workflow run: `32612414400`; job: `97127412040`; event: `push`; attempt: `1`; conclusion: `failure`.
- Direct-child binding, pinned runner and Node setup steps 2-5 passed. Collection step 6 failed, upload step 7 was skipped and artifact count is zero. The retained official pages do not prove a narrower failure boundary inside step 6.
- Official terminal-run artifacts response: `{"total_count":0,"artifacts":[]}`.

## Commands and conclusion

The coordinator queried the two immutable run/job records with `gh run view <run-id> --json databaseId,headSha,event,status,conclusion,attempt,jobs,url`, read failed-step logs with `gh run view <run-id> --log-failed`, and queried each `/actions/runs/<run-id>/artifacts` endpoint. All queries completed with exit code `0` on Windows 10, GitHub CLI `2.96.0`, at the timestamp above. These were read-only queries and triggered no workflow.

The six raw official API response bodies are retained byte-for-byte under `docs/evidence/M3-14/raw/`. `terminal-official-proof.json` binds their exact endpoint, byte count and SHA-256 and the governance verifier independently parses the fixed run, job, step and zero-artifact facts. The retained page identities are:

| Page | Bytes | SHA-256 |
| --- | ---: | --- |
| diagnostic run | 13584 | `95d174a5c369dd89cb31d345620aca06111a879b78b3e4dd29bbd2dbacd54a11` |
| diagnostic jobs page 1 | 4490 | `3e2e36ed673982b61deccfc1ace4f37dd90d1edf2debe9b1204e8dc9dc113219` |
| diagnostic artifacts page 1 | 33 | `d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42` |
| terminal run | 13365 | `5862c875de76e180374c5a7279dad28360a06de7530a4e9846e376188fab6343` |
| terminal jobs page 1 | 2738 | `d7f8bda3c7ca1cef6a504ec7fefab08897b10d4695b81cc1c1cbbef63e18d4b3` |
| terminal artifacts page 1 | 33 | `d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42` |

ADR 0018 defines failure, invalid/missing artifact and terminal-evidence rejection as entitlement-consuming outcomes. Therefore `runAttempt=1` is permanently consumed, M3-14 is blocked rather than complete, no performance owner was selected, and M3-05 remains blocked.

The terminal-state coordination also corrects two post-publication governance assumptions without changing either canonical workflow or the terminal request: the M3-13 workflow-presence mutations now toggle the actual state instead of assigning an already-true value, and the PR M3-14 gate explicitly permits only the ledger-bound reviewed workflow pair through `--allow-reviewed-workflows`. Candidate/live hashes, the recomputed execution identity, the fixed publication parent and its exact three-path diff, and the immutable current terminal-request blob are all required; incomplete, coordinated-drifted, ledger-drifted or request-drifted publication is rejected.
