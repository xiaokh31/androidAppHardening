# M3-05 remote validation and blocker

- Timestamp: `2026-08-16T04:35:41+08:00`
- Branch: `chore/m3-05-performance-benchmarks`
- PR: `#63` (draft), Issue: `#22` (open)
- Final evaluated head: `61652979caea471a8dae058c08bf5b4620321fe5`
- Environment: GitHub `ubuntu-24.04`, `windows-2025`, pinned API 29/36 x86_64 KVM images; no local emulator and no physical device run.

## Passing gates

- Build `31905067470`: Ubuntu job `95061480539` and Windows job `95061480729` passed.
- Governance `31905066201`: Ubuntu and Windows passed.
- Host benchmarks `31905067854`: Ubuntu and Windows passed two fixed runs, every Host budget, and the unchanged 10% within-platform repeatability gate.
- API 29 KVM job `95061478402` passed and uploaded artifact `9252201610`, size `455269`, digest `sha256:122ff80c1007d6526d2e3b5e5e4ddd0dfc13752a0a9d735213d832a3e655ee69`.
- API 36 KVM job `95061478417` generated a complete schema-valid report and proved cleanup before failing the release budget. Artifact `9252626411`, size `474382`, digest `sha256:5b65006aec2ab9f4a1362dfae69374a0afaabf5356fe52d97cd8c6ae18595ce9`.
- `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --report <api36-report>` passed on the downloaded API 36 report.

Host artifact metadata:

| Platform | Artifact ID | Bytes | Artifact digest |
|---|---:|---:|---|
| Ubuntu | `9252173928` | 91783099 | `sha256:9eb50922fec2d398b7183d702baa095ad54197d6435c397e1300feb57f3d5b99` |
| Windows | `9252216001` | 91787524 | `sha256:be0db6a2a8c8ee48fc3045446a25cbae2f38ea4442a2e3bd930c3c88de5de70e` |

Exact-head Host report hashes:

| Report | Bytes | SHA-256 |
|---|---:|---|
| Linux run 1 | 7344 | `052fc77d81a726d9a4b3ac2300e3d2d3c7b7014b2b12eb3a6c962ed9fd514d8e` |
| Linux run 2 | 7347 | `225be0c8058e632566cc72eddecc2dd9a32a52cfefdfa05bd79ef6da8830dbf7` |
| Windows run 1 | 7432 | `3e0f3de1d3b5e9e7230ff27846bb111d2069ebc349667aab88ce5bb545bb8616` |
| Windows run 2 | 7446 | `2b9c32d9ddef33affe0254ea75fb73210857c75ed6fdece39178649d912fe3b0` |

## Blocking budget and repeatability result

The exact-head API 36 report has `cleanupPassed=true` and `allBudgetsPass=false`. The failing row is:

- fixture: `kotlin-multidex`
- metric: `processToApplicationOnCreateMs`
- protected P50/P95: `460/635 ms`
- baseline P50/P95: `129/549 ms`
- delta P50/P95: `331/86 ms`
- fixed budget P50/P95: `300/500 ms`

The report SHA-256 is `30852bf2c807db353327420f3a74ed82fec686fa3a98b9ff393a62041b4d36ef`.

The prior complete API 36 report at `065f97b7413113420be7b12257469637cf69cb0b` passed all budgets, but comparing the two complete reports produces six Android P50/P95 summaries above the fixed 10% repeatability gate. The maximum is `21.30%` for `kotlin-multidex/processToInteractiveMs` P50. A third rerun would not close that deterministic acceptance failure.

## Decision

M3-05 remains `blocked`. No threshold, sample count, security control or product Runtime behavior was changed. The planned API 29 ARM64 physical run was not started because API 36 is not stable. PR #63 stays draft and M4 must not start. Recovery requires a separate bounded startup-performance optimization/measurement-stability ADR and task, followed by one API 36 replacement and exactly one ARM64 physical matrix only after the remote budget and repeatability gates pass.
