# M3-10 first-and-only remote outcome

- Timestamp: `2026-08-22T13:40:48+08:00`
- Product tuple: `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`
- Diagnostic head: `790ae4579ce3562dc93f3c533ffb786a39517600`
- Diagnostic run/job: `32554806537` / `96987186584`
- Terminal request head: `415420223441578aa028a1687cb94ef79dfd1924`
- Terminal evidence run/job: `32554917303` / `96987454333`
- Draft PR: `#79` (intentionally blocked; not eligible for ready/merge)
- Outcome: `BLOCKED`; the first-and-only eligibility was consumed and no replacement run is permitted.

## Diagnostic run

The official workflow-runs API binds run `32554806537` to the canonical workflow path, exact diagnostic head, task/product run name, `push`, `run_attempt=1`, and terminal `failure`. Its only job is also terminal `failure`. The official artifacts API reports `total_count=0`.

The pinned Ubuntu runner, JDK, Node.js, API 36 r2 system image and Emulator 37.1.11 preparation all passed. The fixed M3-12 release `374769776`, asset `524507375`, archive size `2184246` and SHA-256 `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964` downloaded and passed the fetcher's byte lock. The subsequent full M3-12 provenance verifier failed closed with `upstream ancestry differs: implementation-to-evidence` because the diagnostic workflow checkout was shallow and did not contain the locked upstream ancestry.

The failure occurred before Native source preparation, Release build, AVD creation, APK installation, campaign execution or artifact upload. No sample, attribution owner or `UNATTRIBUTED` result exists. The downloaded SDK inputs do not constitute a device run.

## Terminal evidence run

The request commit `415420223441578aa028a1687cb94ef79dfd1924` is the diagnostic head's only direct child and changes only `docs/evidence/M3-10/diagnostic-terminal-request.json`. Terminal run `32554917303` passed exact parent/diff binding and pinned-runner checks, then fetched the official terminal pages and failed closed with `terminal artifact selection differs`, matching the diagnostic run's zero artifacts. It uploaded no terminal artifact.

This is the required permanent terminal state for the current tuple. The workflow may not be fixed and rerun on these product bytes. M3-05 remains blocked. Any future measurement eligibility requires a separately authorized ADR/task and a new identity boundary; it cannot be treated as an M3-10 retry.

## Draft PR governance correction

Draft PR #79 Governance run `32555201566` failed on both platforms because the frozen M3-07 scanner treated M3-10's test-only profile artifact path bindings as a prohibited production HIGH control. This did not run or change the canonical diagnostic. Bounded successor `77d8fda` filters only eight exact whole-line M3-10 test-artifact property/environment pairs in `host/container/build.gradle.kts`; a new mutation proves an M3-10-like HIGH override still fails. Independent bounded review returned `P0=0/P1=0/P2=0`. Out-of-scope KVM, fuzz and equivalence runs were cancelled; the superseded PR-head jobs are not acceptance evidence.

## Links

- [Diagnostic run 32554806537](https://github.com/xiaokh31/androidAppHardening/actions/runs/32554806537)
- [Terminal evidence run 32554917303](https://github.com/xiaokh31/androidAppHardening/actions/runs/32554917303)
- [Draft PR #79](https://github.com/xiaokh31/androidAppHardening/pull/79)
