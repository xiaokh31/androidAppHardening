# M3-05 ADR 0015 remote validation and blocker

- Timestamp: `2026-08-16T15:42:53+08:00`
- Branch: `chore/m3-05-performance-benchmarks`
- PR: `#63` (draft), Issue: `#22` (open)
- Exact evaluated head: `1c030334d607bc10054b876dd969ea8048725cb3`
- Environment: GitHub `ubuntu-24.04`/image `20260810.271.1`, API 36 r2 x86_64, one KVM job and one emulator boot; no API 29 job and no ARM or physical-device run.

## Exact-head gates

- Build `31931429739`: Ubuntu job `95126758141` and Windows job `95126758071` passed.
- Governance `31931429726`: Ubuntu job `95126757738` and Windows job `95126757806` passed.
- Host benchmarks `31931429740`: Ubuntu job `95126757977` and Windows job `95126757973` passed both fixed runs, every Host budget and the unchanged within-platform repeatability gate.
- Pull-request KVM `31931429715` was skipped by contract. The sole branch-push KVM run was `31931428130`, job `95126754768`, attempt `1`, containing only `API 36 x86_64`.
- Out-of-scope Cross-platform equivalence `31931429701` and M3-02 Fuzz `31931429775` were cancelled.

## First and only ADR 0015 A/B pair

The one API 36 job used environment `api36-x86_64`, boot hash prefix `a3cf719802bc`, campaign A order `java-single-dex,kotlin-multidex,jni-four-abi` with `baseline_then_protected`, and campaign B with both axes reversed. Each mode retained five warmups and thirty measurements. The six target APK hashes before and after campaign B are byte-identical (`6/6` lines, zero differences). Both campaign reports, the six-APK canonical manifest and all ninety comparison rows were generated; cleanup is `true`.

Artifact `9260244215` is `3316848` bytes with archive digest `sha256:98c5cedce457775e4f4365226647b1bf1d49cb3f824d07ae5f9450c31803d5ae`.

| Evidence | SHA-256 |
|---|---|
| Campaign A report | `f7528353cb5a3b4c8114546d4dcd53ab1e3efd7420e210abe7eb51067a8ddd2b` |
| Campaign B report | `6845d3c9d7eba0d84aefe0d05da485e87f754f5fe63e7a57ba6807159d9a0979` |
| Canonical artifact manifest | `d2166e07f5e959a9868c0da4ddd05a19e40f961559bec4367c8e8c00fba56089` |
| A/B repeatability aggregate | `81b0982e4c5b6ae5a34d71218df6602cd44706d879c3909400a2809e5e4f55d8` |

## Final failure result

The reports are complete and structurally bound, but both campaigns fail unchanged release budgets and `25/90` repeatability rows exceed the unchanged `0.10` limit.

- Campaign A: `java-single-dex/processToApplicationOnCreateMs` protected P50/P95 `489/657 ms`, baseline P50/P95 `158/647 ms`, delta P50/P95 `331/10 ms`; P50 exceeds the `300 ms` budget.
- Campaign B: the same metric has protected `506/690 ms`, baseline `74/562 ms`, delta `432/128 ms`; P50 exceeds `300 ms`.
- Campaign B: `java-single-dex/processToInteractiveMs` has protected `888/1089 ms`, baseline `512/942 ms`, delta `376/147 ms`; P50 exceeds `300 ms`.
- The largest repeatability failure is `java-single-dex/processToInteractiveMs/deltaP95`: campaign A `3 ms`, campaign B `147 ms`, variation `48.0` against limit `0.10`.

The formal M3-08 validator exits `1` only on the two campaign budget booleans, the twenty-five failed comparison rows and aggregate budget/repeatability booleans. Identity, order, sample counts, report hashes, artifact bytes, environment, run/job/attempt, boot binding and cleanup are accepted.

## Decision

This is the first and only result permitted by ADR 0015, so it is final and must not be replaced by a rerun. M3-05 remains `blocked`; PR #63 remains draft, ARM stays forbidden, and M4 must not start. Recovery requires a separate Runtime startup-performance optimization ADR/task and independent review. That new task may not weaken budgets, sample counts, signer/AEAD/Guard/memory controls or reinterpret this failed evidence.
