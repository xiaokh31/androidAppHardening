# M2-07 Windows runner 20260818 independent read-only review 5

- Frozen candidate: `f08d7ba225bd60e2851c8344ac068579d0f9cdd0`
- Previous published candidate: `92ff8afcc44c4bd8cca8513b52504b834f859bd4`
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Result: `FAIL — P0=0/P1=0/P2=1`
- Scope: bounded independent read-only review of the per-image `cl.exe` correction; no modification, network, dependency download, Gradle, Host compilation, device, KVM, fuzz, equivalence or benchmark

## Finding

The technical correction is complete, but HandOff still described validating and freezing it as future work after `f08d7ba225bd60e2851c8344ac068579d0f9cdd0` was already the frozen candidate. This stale coordination timing statement is the only P2. The minimal correction is documentation-only: record the completed freeze and use a stable conditional publication gate for the latest committed successor.

## Confirmed positive evidence

- `cl_runtime_version` is part of every complete Windows runtime record: the first three bind `19.51.36252`, and only `20260818.207.1` binds `19.51.36256`.
- Build constructs its exact regex from `$selectedImage.cl_runtime_version`, asserts it, and emits the selected value. No global field or version range exists in the executable path.
- Strict lock equality, record order, fifth-image rejection, per-image field mutation and workflow fallback mutation all fail closed.
- ADR, provenance, maintenance evidence and README accurately distinguish the failed first PR #78 Build from the unexecuted replacement candidate.
- The increment has no product, Runtime, Native, fixture, device or benchmark change; diff check passes with a clean worktree.

No P0 or P1 finding exists. The successor may be published only after its bounded documentation review returns `P0=0/P1=0/P2=0`.
