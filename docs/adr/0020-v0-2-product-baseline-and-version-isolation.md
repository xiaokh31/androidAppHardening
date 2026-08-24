# ADR 0020: v0.2 product baseline and version isolation

## Status

Proposed

## Context

ADR 0019 terminated the current v0.1 release line with decision `STOP_CURRENT_V0_1_RELEASE_LINE`. Its M3-05 benchmark and M4 release path cannot be retried, renewed, renamed, substituted or treated as passed. The repository nevertheless contains completed Host, Runtime and validation implementation that may form the source baseline of a separately authorized release line.

The user authorized planning v0.2 on 2026-08-24. The accepted source baseline is repository `xiaokh31/androidAppHardening` at `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`, whose Git tree is `1f8a4c387b4715443df31457fa498c7463b1f23f`. This authorization does not reopen any v0.1 task or convert historical evidence into v0.2 release evidence.

## Decision

v0.2 is a distinct release line with task IDs `V2-M0-01` through `V2-M4-03`, its own task index under `docs/v0.2/tasks/`, and its own product, validation and release tuples. New task `depends_on` entries may name only other `V2-*` tasks. The accepted v0.1 source is referenced through `baseline_inputs`, not through a dependency on a terminal v0.1 task.

The v0.2 product boundary remains unchanged:

1. Only one standalone APK is accepted as input.
2. Input bytes remain read-only and output is a distinct unsigned APK.
3. The product never accepts or uses a private key, keystore, alias or password and never signs an APK.
4. Input `minSdk` must be at least 29.
5. Runtime continues to build `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`; this does not convert an ARM-only application into an x86-compatible application.
6. AAB, APKS, split APK, dynamic feature, Flutter, Unity, React Native, hotfix, plugin frameworks and existing protection shells remain unsupported.
7. DEX-memory, anti-debug, environment and offline-key controls remain cost-increasing defenses, not absolute prevention.

`docs/v0.2/development-product-tuple.json` fixes the development baseline and the inherited boundary. It is not a release-candidate tuple and cannot authorize performance or release claims.

The predecessor v0.1 product tuple is exactly `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`. It remains a historical identifier only and must never equal a v0.2 development or candidate tuple.

`V2-M3-02` must create the first release-candidate tuple. Its candidate ID is `v0.2.0-rc.1`; its exact compact UTF-8 JSON preimage contains these fields in this order:

1. `schemaVersion` = `2`.
2. `releaseLine` = `v0.2`.
3. `releaseVersion` = `0.2.0`.
4. `candidateId` = `v0.2.0-rc.1`.
5. `terminalV01MainSha`.
6. `implementationFreezeSha`.
7. `implementationManifestSha256`.
8. `validationFreezeSha`.
9. `validationManifestSha256`.
10. `toolchainManifestSha256`.
11. `productContractManifestSha256`.
12. `fixtureSourceManifestSha256`.
13. `performanceContractSha256`.
14. `releaseGateContractSha256`.

`productTupleSha256` is SHA-256 of those exact JSON bytes. The two freeze SHAs are uniquely selected as the exact `head_sha` of each task's successful post-merge `main` Governance `runAttempt=1`; they are not worker-selected PR heads. Tracked freeze-acceptance locks bind the reviewed PR head, required `MERGE_COMMIT`, merge commit, official post-merge run identity, manifest hashes, ancestry and changed-path report. The freeze commits precede the tuple lock so the hash is not self-referential. Run-generated one-time certificates and signed test copies are bound in run artifact manifests, not in the product tuple.

The seven manifest hash fields have exactly one tracked preimage each, fixed in `docs/v0.2/IDENTITY_MANIFESTS.md` and machine-readable `docs/v0.2/identity-path-policy-v1.json`. V2-M0-02 owns the implementation, toolchain and product-contract preimages. V2-M3-01 owns the fixture-source, validation, performance-contract and release-gate-contract preimages. Each file uses the fixed canonical schema, complete selector, path-to-role rules and Git-blob identity rules; a future worker or validator cannot redefine the set. V2-M3-02 may only read and recompute those bytes from their accepted freeze commits; an empty, arbitrary, ignored, generated or alternate preimage is invalid.

All bytes that can affect release output or a release PASS decision freeze before this tuple is created. V2-M0-02 therefore owns the distribution packager, both launchers, archive schema and archive-internal Quickstart in addition to Host/Runtime version identity. V2-M3-01 owns performance and exact-tuple release-validation harnesses, security/SBOM tooling, packaging verification, candidate-neutral tuple/freeze/post-freeze verifiers, package/HandOff candidate adapters and every workflow candidate. Downstream V2-M3-02 through V2-M4-03 execute frozen bytes and write only the task-specific evidence paths fixed in `docs/v0.2/post-freeze-path-policy-v1.json`; they do not add or alter validators, workflows, launchers, archive content templates or distribution documentation.

Schema 2 HandOff uses a two-state tuple identity. Before `docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json` exists, `product_tuple_sha256` must equal the exact non-releasable development tuple. The V2-M0-01 validators deliberately fail closed if any candidate lock exists. V2-M3-01 must then implement and freeze candidate-neutral exact verification plus package/HandOff adapters before the lock exists. After V2-M3-02 creates that lock, the adapters may switch the field only after the frozen aggregate verifier has independently rebuilt both freeze locks, all seven manifests, ancestry, workflow equality and tuple bytes. An arbitrary third tuple, the terminal v0.1 tuple, a self-certified lock, a candidate tuple without its exact proof, or a lock/HandOff mismatch is invalid. V2-M3-02 owns only the atomic data transition and byte-for-byte publication; it must not add, upgrade or repair validator code.

All v0.2 release claims require fresh evidence bound to that candidate tuple. Existing merged M0-M3 implementation and tests may be reused as source and test infrastructure. Old v0.1 results may be cited only as historical risk input; they cannot satisfy a v0.2 acceptance criterion. PR #63 and PR #83 remain closed and unmerged, and their code, workflows, runs, artifacts and identities cannot be relabelled or copied into a v0.2 PASS record.

V2-M3-03 is the mandatory fresh size, startup and memory release gate. V2-M4-01 cannot start until V2-M3-03 and the exact-tuple regression task have both passed; no security review or packaging task may waive the performance gate.

The original v0.1 terminal bytes remain auditable at the fixed baseline commit. Any successor-aware governance change must verify that commit as an ancestor and verify its locked bytes from Git history, while separately checking that current v0.2 documentation preserves all terminal prohibitions. It must not rewrite ADR 0019 or the old task cards to appear active.

## Consequences

- v0.2 can progress without weakening or bypassing the v0.1 terminal decision.
- The source implementation is inherited, but compatibility, performance, security and release evidence must be reproduced for the fresh tuple.
- Benchmark, release-gate and distribution tooling are implemented only by the pre-candidate V2-M0-02/V2-M3-01 freezes even though the old M3-05 and M4 tasks remain unstartable.
- Any product-code change after candidate freeze invalidates `v0.2.0-rc.1`; a separately authorized remediation must create a new candidate ID and tuple.
- No new protection feature, format or compatibility claim is authorized by this ADR.

## Rejected Alternatives

- Resume M3-05 or M4: violates ADR 0019.
- Reopen or cherry-pick PR #63 or PR #83: obscures terminal history and reuses a consumed release path.
- Call the current source v0.1 complete without performance evidence: creates an unsupported release claim.
- Reuse old API 36 runs as v0.2 PASS evidence: they are tied to another tuple and include a terminal failure.
- Add AAB, framework or lower-API support while restarting release work: expands product scope without evidence or an independent architecture decision.
- Put all new tasks in the locked v0.1 index: weakens version isolation and makes the old graph appear resumable.

## Security Impact

Version isolation prevents result selection and unsupported release claims. It preserves input immutability, unsigned output, signer and AEAD fail-closed behavior, key-lifetime controls and residual-risk wording. v0.2 tests may generate one-time non-production signing material only in ignored build directories and must delete it after use; no signing ability enters the product.

## Compatibility Impact

The supported input and Runtime-build boundaries do not expand. Previous device results show that the implementation has exercised four mandatory API/ABI cells, but v0.2 may publish a positive compatibility claim only for cells freshly validated against its exact candidate tuple. Other cells remain `UNVERIFIED` and make no compatibility promise.

## Verification

- `V2-M0-01` validates the new task graph, version isolation and old-terminal negative cases without changing product code.
- `V2-M3-02` recomputes the candidate tuple from the seven fixed tracked preimages and rejects every field, order, path-set, mode or byte mutation.
- Every v0.2 evidence manifest contains the candidate tuple hash and exact source commit.
- Governance rejects an old tuple hash, old PR/run/artifact as PASS evidence, any V2 dependency on an old task, or any text that revives v0.1 M3-05/M4.
- Security-sensitive V2 tasks require independent read-only review with `P0=0/P1=0/P2=0`, exact-head CI and post-merge validation before a dependent task starts.
