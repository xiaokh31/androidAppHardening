# ADR 0016: End-to-end startup attribution boundary

## Status

Accepted

## Context

M3-05 retained a final API 36 A/B rejection. For `java-single-dex`, the protected-minus-baseline `processToApplicationOnCreateMs` P50 delta was `331 ms` in campaign A and `432 ms` in campaign B, so both campaigns exceeded the unchanged `300 ms` budget. Their cross-campaign variation was about `30.5%`, above the unchanged `10%` repeatability limit, so this evidence is a retained budget failure and diagnostic input, not a stable release baseline. Its interactive P50 deltas were `168/376 ms`; `432 ms` was not an interactive endpoint. The later M2-10 first-and-only diagnostic was valid and completed, but its `t0..t6` interval began immediately before signer measurement and ended after Runtime bootstrap/factory completion. No individual inner stage contributed the required 30 ms in both fixed sample partitions, so that diagnostic correctly selected no stage.

The two results answer different questions. M3-05 measures an end-to-end platform/application interval. M2-10 measures only a nested Runtime interval. Time before the Shell factory, between the final loader return and Application construction, in Application/Provider/Activity lifecycle dispatch, and between UI traversal and interactivity was not part of `t0..t6`. It is therefore invalid to attribute the remaining end-to-end delta to Runtime, to rerun M2-10 until a stage becomes eligible, or to weaken an existing release budget.

This ADR replaces the unmerged M2-10 attribution proposal in PR #67. It does not invalidate run `32099991400`, authorize another M2-10 run, or treat that result as a failed attempt. The retained result is conclusive only for its documented inner boundary.

## Decision

Startup attribution uses one outer `p0..p15` sequence on the real Release/R8 cold-start path and one protected-only inner sequence. Every timestamp uses Android `CLOCK_BOOTTIME` represented in nanoseconds. A later implementation may use profile-only bytecode probes plus Perfetto/Android trace events, but the observer, its manifest control and all keep rules must be absent from Release AARs, production fixture APKs, the CLI and distribution artifacts.

The common outer sequence is exactly:

1. `p0`: process start from the platform process-start event.
2. `p1`: benchmark Application constructor entry.
3. `p2`: benchmark Application constructor exit.
4. `p3`: `Application.attachBaseContext` entry.
5. `p4`: `Application.attachBaseContext` exit.
6. `p5`: fixture `ContentProvider.onCreate` entry.
7. `p6`: fixture `ContentProvider.onCreate` exit.
8. `p7`: benchmark `Application.onCreate` entry.
9. `p8`: benchmark `Application.onCreate` exit and the M3-05 application endpoint.
10. `p9`: benchmark Activity constructor entry.
11. `p10`: benchmark Activity constructor exit.
12. `p11`: benchmark Activity `onCreate` entry.
13. `p12`: benchmark Activity `onCreate` exit.
14. `p13`: benchmark Activity `onResume` entry.
15. `p14`: benchmark Activity `onResume` exit.
16. `p15`: first focused/interactive marker and the M3-05 interactive endpoint.

Each outer stage is exactly one adjacent difference `p(n+1)-p(n)`. All fifteen differences are retained, even when their value is zero. Their sum must equal `p15-p0` exactly after canonical nanosecond conversion; omission, overlap, duplication, reordered timestamps or an unexplained residual fails closed. `p8-p0` must reconcile to the process-to-Application interval and `p15-p0` to the process-to-interactive interval used by M3-05. First-frame/fully-drawn trace slices may be retained as auxiliary evidence inside `p14..p15`, but they are not inserted into the canonical chain because frame presentation and focus callbacks do not have a portable total order. The existing 300/500 ms P50/P95 budgets and the 10% repeatability limit remain unchanged.

For the protected APK, `p0..p1` is additionally decomposed by this exact nested sequence:

1. `h0`: production `ShellAppComponentFactory.instantiateClassLoader` entry.
2. `h1..h7`: the existing M2-10 `t0..t6` timestamps, in the same order and with the same six stage meanings.
3. `h8`: production `instantiateClassLoader` return after the final loader is published.

The ordered chain `p0,h0,h1,h2,h3,h4,h5,h6,h7,h8,p1` must be monotonic. Its ten adjacent differences must sum exactly to `p1-p0`. The contiguous Runtime bootstrap owner is `h0..h8`; `p0..h0` is `platform_pre_shell`, and `h8..p1` is `platform_post_loader`. Neither platform segment may be relabelled as Runtime. The baseline has no synthetic Shell or no-op factory inserted into its manifest; its common `p0..p1` interval remains the comparison boundary.

The future diagnostic uses only the retained failing `java-single-dex` fixture and exactly two order-reversed campaigns in one API 36 x86_64 job, one emulator boot and one exact head:

- Campaign `A`: baseline then protected.
- Campaign `B`: protected then baseline.
- Each mode has five warmups followed by exactly fifteen retained cold starts.
- Nearest-rank P50 for fifteen retained values is the eighth value after ascending sort.
- No sample may be dropped, replaced, duplicated or reordered. A timeout, missing marker, trace loss, non-monotonic sequence or failed reconciliation invalidates the only diagnostic and blocks the task.

Attribution is computed per campaign by pairing baseline and protected retained samples with the same immutable ordinal `1..15`. For every pair, the validator constructs exactly nine signed owner contributions in nanoseconds:

1. `RUNTIME_BOOTSTRAP = protected(h8-h0)`.
2. `PRE_APPLICATION_RESIDUAL = protected[(h0-p0)+(p1-h8)] - baseline(p1-p0)`.
3. Seven common owners `P1_P2` through `P7_P8`, each equal to `protected(p(n+1)-p(n)) - baseline(p(n+1)-p(n))`.

The nine signed contributions must sum exactly, for every ordinal, to `protected(p8-p0) - baseline(p8-p0)`. Negative contributions are retained in reconciliation and percentile inputs but are never eligible. Only after all fifteen pairs reconcile does the validator calculate nearest-rank P50 independently for each owner and for the total delta. Owner P50 values are not added together because percentile is nonlinear. The total-delta P50 must exceed the unchanged 300 ms M3-05 process-to-Application budget in both campaigns or the result is `UNATTRIBUTED`.

An owner is eligible only if its P50 is at least 30 ms in both campaigns, is positive in both, has cross-campaign variation `abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10`, and has `ownerP50 / totalDeltaP50 >= 0.50` independently in A and B. Exactly one owner must satisfy every condition. Zero or multiple eligible owners, a non-positive total, a budget not reproduced, or any arithmetic/reconciliation failure produces `UNATTRIBUTED`; no tie-break by order, largest observed value or reviewer choice is allowed. `PRE_APPLICATION_RESIDUAL` and non-Runtime common owners require a separately scoped platform, artifact, fixture or benchmark task rather than a Runtime change.

The future diagnostic is first-and-only for its exact task. Its canonical diagnostic workflow path is `.github/workflows/m3-09-startup-attribution.yml`, task key is `M3-09-DIAGNOSTIC-V1`, and `runAttempt` must equal `1`. It only produces and uploads the raw diagnostic package; it does not attempt to prove its own final conclusion or artifact existence while still running. After that run reaches a terminal state, a separate non-diagnostic evidence workflow at `.github/workflows/m3-09-startup-attribution-evidence.yml` fetches and archives the raw official GitHub workflow-runs, jobs and artifacts API pages. The evidence verifier parses official `total_count`, pagination, run/attempt/status/conclusion, job ID and artifact metadata, requires the diagnostic run to be completed/successful with its exact artifact, and filters the canonical workflow, exact head and task-key/product tuple fixed before dispatch. Failed, cancelled and no-artifact history remains in the raw pages. Exactly one matching diagnostic run must exist and its run ID/job ID must equal the report. The product tuple must be present in the immutable pre-run task ledger and canonical run name because GitHub's workflow-runs response has no custom product-tuple field. Changing workflow path, job name or artifact name cannot create a new eligibility tuple. An invalid or ineligible result cannot be replaced by a later run on identical product bytes. This rule is separate from and does not reopen the completed M2-10 diagnostic.

The M3-11 canonical artifact lock fixes the only eligible original pair to the exact signed APKs retained by PR #63's first-and-only failure: exact head `1c030334d607bc10054b876dd969ea8048725cb3`, run `31931428130`, job `95126754768`, artifact `9260244215`. The canonical `java-single-dex` baseline is `29962` bytes with SHA-256 `4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf`; the canonical protected APK is `1287876` bytes with SHA-256 `1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9`. Their exact 218-byte UTF-8 tuple serialization hashes to `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`. The lock additionally binds and the verifier parses the artifact manifest, both campaign reports and repeatability aggregate to prove the selected pair produced application delta P50 `331/432 ms` with repeatability failure. A source rebuild, ZIP normalization, re-signing, reconstructed container or semantically similar APK is not an original and cannot reuse this tuple. If both the official artifact and every exact-hash retained copy become unavailable, M3-10 fails closed; regeneration is not a recovery path.

Both locked originals are valid v3 APKs signed by the same synthetic PR #63 signer. The ephemeral private key is absent by design and is not an accepted input. Before any workflow is created, M3-10 must independently prove an installable profile derivation from these exact originals that preserves or explicitly and safely accounts for signer, Guard, authenticated-container, manifest, lifecycle and cleanup semantics. If the design requires the unavailable key, rebuilds a different fixture, or changes the claimed original identity, M3-10 remains blocked and the unique diagnostic eligibility is not consumed.

M3-10 resolves that implementation question with a separate profile identity, not with recovery or imitation of the canonical signer. Both profile APKs use one newly generated ephemeral test signer; the protected profile signer policy, ConfigV2 key material, AHDC AAD and four ABI native share slots are rebound as one transaction to that signer. The canonical APKs remain read-only and keep their exact hashes and signer. The profile key, password, full signer digest and deterministic container seed remain in ignored temporary storage and are destroyed before evidence publication. Given the same canonical bytes, observer DEX, seed and profile certificate digest, two independent post-build derivations and pinned-tool signing operations must be byte-identical. Changing any one of those inputs creates a different named profile package and cannot create another diagnostic eligibility.

This separate identity is acceptable only because a byte-level verifier proves the security semantics rather than asserting identity equivalence. It verifies v3 and one signer on all four APKs, the same signer within each pair and a different signer across original/profile pairs; exact Manifest and resource equality; no baseline Factory; exact reviewed DEX call sites; unchanged non-profile entries; authenticated decryption of the canonical container before transformation; authenticated reconstruction of the profile container; and byte equality for every runtime library byte outside its one 104-byte share slot. The profile Runtime must still reject any APK/config/container/signer disagreement. No diagnostic workflow may exist until an independent review accepts this mechanism with `P0=0/P1=0/P2=0`.

The diagnostic preflight does not trust caller-authored identity inputs. Its profile, Release/tool and API 36 environment locks are copied from reviewed repository paths and later required byte-for-byte in the evidence package. Before the first install, the runner itself obtains and archives the complete first official GitHub jobs API page, selects exactly one current job by canonical name and `GITHUB_RUN_ID`, derives `jobId` from that official object, and binds the raw-page hash into the package. The fixed product tuple is an exact constant at every campaign/package/GitHub boundary, not merely a hash-shaped value.

The future profile artifacts are derived from the original Release/R8 baseline and protected APKs under a deterministic post-build probe manifest. The later implementation task must first pin and implement a byte-level evidence verifier; no diagnostic run is authorized until that verifier exists. It receives the four actual APK files, structural diff manifest, raw A/B calibration samples, raw A/B security/lifecycle events and all Release/distribution artifacts. It independently hashes every input, parses the APK/DEX entry and instruction surfaces, permits only the fixed `p1..p15` common call sites plus protected `h0..h8`, compares manifest/resources/native libraries/security configuration and every non-probe instruction, scans real Release artifacts for observer absence, and compares the raw event sequences. Baseline receives no synthetic Factory. The verifier independently computes nearest-rank P95 from fifteen raw calibration values in each campaign; `calibrationP95Ns * maximumProtectedProbeCount` must be at most `5,000,000` ns separately in A and B. Probe overhead is never subtracted from samples. A missing byte input, unpinned verifier, hash/diff/event failure, probe-count/location drift or overhead above 5 ms forces `UNATTRIBUTED` and cannot select a production owner. Self-reported equivalence booleans or hash-shaped strings are never acceptance evidence.

ADR 0017 and M3-12 bind the already-reviewed profile package to GitHub release ID `374769776`, numeric asset ID `524507375`, archive SHA-256 `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964` and exact member hashes. GitHub reports `immutable=false`, so tag/name alone is never trusted: consumers have `contents: read`, download the numeric asset, verify every byte before emulator creation, and fail closed if the object is deleted, replaced or unavailable. The package cannot be regenerated or substituted.

M3-09 is governance-only. It may update this ADR, task/dependency documents, HandOff/README and governance validation, but it may not implement probes, workflows, Runtime changes or benchmark orchestration and may not run KVM, emulator, ARM or M3-05.

## Consequences

- M2-10 remains valid evidence that no measured inner stage met its eligibility threshold; it is not retried.
- End-to-end startup cost is no longer assumed to be Runtime cost. Common lifecycle, platform residual and protected Runtime spans are named separately and reconcile to the same endpoints.
- A later implementation task may run one bounded API 36 diagnostic for the failing fixture. Its result chooses the owner of any subsequent optimization task.
- M3-10 depends on the merged M3-11 lock and must use the exact PR #63 pair; the rejected rebuilt-original candidate cannot be repaired by relabelling its bytes.
- M3-05 remains blocked. M3-09 alone does not authorize resuming PR #63, running ARM, or changing production code.
- If the only dominant contribution is unowned/platform residual, the project records `UNATTRIBUTED` and plans a narrower diagnostic instead of guessing.

## Rejected Alternatives

- Rerun M2-10 with the same boundary: violates first-and-only evidence and cannot observe the missing interval.
- Aggregate all process-to-Application time as Runtime: attributes platform and fixture work to code that did not execute there.
- Insert a no-op baseline `AppComponentFactory`: changes the baseline path and creates false stage symmetry.
- Rebuild an M3-10-only baseline/protected fixture: diagnoses different product bytes and severs the retained failure's provenance.
- Use wall clock or subtract timestamps from different clocks/processes: cannot produce an adjacent, reconcilable timeline.
- Lower the 30 ms eligibility threshold, raise release budgets or relax the 10% limit: selects a cause by changing the decision rule.
- Run all fixtures, ARM or the full M3-05 matrix during contract work: adds cost without deciding the attribution model.
- Add a Release timing API, environment switch, intent extra, manifest flag or public/package-private hook: creates a distributable or attacker-influenced diagnostic surface.

## Security Impact

Signer verification, authenticated container parsing, metadata binding, Guard ordering, memory controls, read-only and `DONTDUMP` mappings, cleanup and four-ABI behavior remain unchanged. Attribution probes are test/profile-only and must be proven absent from product artifacts. The profile diff manifest, original/instrumented APK hashes, unchanged security/lifecycle event sequence and 5 ms maximum calibrated overhead are acceptance inputs, not advisory metadata. No finding in performance evidence may authorize skipping, caching across trust epochs, deferring or weakening a security control.

## Compatibility Impact

No wire format, minSdk, API, ABI, supported application type, fixture behavior or public interface changes. The future diagnostic is limited to the fixed API 36 revision 2 x86_64 reference image and the exact retained Release/R8 `java-single-dex` pair. It makes no new compatibility claim and cannot replace API 29/36 Runtime acceptance or ARM evidence.

## Verification

- Governance verifies that ADR 0016, M3-09, M3-05, TEST_STRATEGY, ROADMAP and INDEX agree on the new dependency and scope.
- M3-11 independently recomputes the locked pair tuple and optionally hashes the two actual files; M3-10 must execute that actual-byte mode before any profile derivation or workflow run.
- The M3-09 governance validator is explicitly a synthetic contract-model validator: it recomputes every adjacent stage, nine-owner per-ordinal reconciliation, nearest-rank P50, 300 ms reproduction, 30 ms/10%/50% eligibility, unique owner/`UNATTRIBUTED`, two-campaign calibration P95 and the two-phase identity schema. It sets `contractModelOnly=true` and `realEvidenceAccepted=false`; it cannot accept a real diagnostic package.
- The later implementation task must add the separate byte-level profile verifier and post-diagnostic GitHub API evidence verifier described above, pass their own independent review, and only then create either canonical workflow. M3-09's synthetic PASS is not evidence that APK bytes, API history or a completed run were verified.
- Named mutation tests alter report timestamps, ordinals, raw hashes, owner summaries, run enumeration, attempt, product tuple, profile diff/overhead, thresholds, selected owner, M3-05 dependency and budget text; every mutation must be executed and rejected.
- M3-09 requires independent read-only review with P0=0/P1=0/P2=0 and exact-head Ubuntu/Windows Build/Governance before merge; no device or benchmark evidence is accepted for this governance task.
