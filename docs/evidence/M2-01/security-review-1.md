# M2-01 independent security review 1

- Reviewed head: `c2b083032e585a6e95e5a5d0661724dc1a7b63bb`
- Base: `f4b773fc59129ea69c2dabde553438d8e62c549e`
- Mode: independent read-only review
- Result: `FAIL`
- Findings: P0 `0`, P1 `2`, P2 `1`

## Findings

1. **P1 — production no-original-Factory device path missing.** The API 29/36 M2-01 vectors always authenticated `OriginalAppComponentFactory`; the only device no-Factory case used the fixture-only M0-05 Legacy Shell. The production Guard/Shell default platform component path therefore lacked Release/R8 device evidence.
2. **P1 — Throwable classification could escape.** `HardeningBootstrap.classify(Throwable)` called an untrusted, overridable `getMessage()` without containment. A second exception from that accessor could leave the Coordinator in `INSTALLING` without a terminal result, breaking stable failure caching.
3. **P2 — test diagnostics entered the Release API.** Three `public testOnly*` methods were present in the production Shell `classes.jar` even though the compatibility PoC had moved to the fixture-only Legacy Shell.

## Required closure

- make failure classification total and add a hostile-Throwable regression;
- remove the three production public test diagnostics and confirm the Release public surface is only the constructor plus six platform callbacks;
- add one authenticated `original_factory=null` production-Shell Release/R8 target to the bounded API 29/36 acceptance, without repeating unrelated device matrices.

The reviewer did not modify the repository, run Gradle, start an emulator, or install on a physical device. It reused exact-head Build `31442253788`, Governance `31442253820`, KVM `31442253826`, and the already downloaded ignored artifacts.
