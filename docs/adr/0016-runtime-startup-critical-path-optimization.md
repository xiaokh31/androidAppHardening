# ADR 0016: Runtime startup critical-path optimization boundary

## Status

Proposed

## Context

The first and only ADR 0015 API 36 x86_64 A/B pair for M3-05 completed both campaigns on one exact head, job and emulator boot. Campaign A measured the protected `java-single-dex` process-to-`Application.onCreate` P50 delta at 331 ms and campaign B measured 432 ms, above the unchanged 300 ms budget in both orders. Campaign B also exceeded the process-to-interactive P50 budget, and 25 of 90 repeatability rows exceeded the fixed 10% limit. Artifact identity, sample counts, arithmetic and cleanup were valid, so the result is a product-performance rejection rather than an orchestration retry condition.

The protected startup path performs early APK signer verification, authenticated binding inspection, Native container authentication/decrypt/inflate, authenticated metadata verification, environment policy and memory controls, in-memory class-loader construction, and original Factory delegation. These stages protect distinct trust boundaries. Optimizing by skipping, deferring or caching a security decision across processes would invalidate the product rather than improve it.

## Decision

M2-10 owns one bounded production Runtime optimization selected only after stage attribution identifies a material contributor to the protected startup delta.

1. Attribution uses repository-generated `java-single-dex` Release/R8 fixtures on the pinned API 36 revision 2 x86_64 image and Emulator 37.1.11. It records one bounded diagnostic campaign with five warmups and fifteen retained measurements. It is not an ADR 0015 A/B acceptance run and cannot satisfy M3-05.
2. Stage evidence is test-only. It may use existing failure-probe boundaries, Android instrumentation, host Native fixtures and external monotonic timestamps, but it may not add a production timing API, manifest control, environment variable, intent extra, JNI export, log marker or distributable benchmark switch. Release AAR API, JNI export and packaged Runtime scans must prove the observer is absent.
3. The diagnostic report binds exact head, environment, APK hashes, sample counts and the ordered stages `signer_source`, `binding_precheck`, `native_open`, `metadata_policy`, `loader_session`, and `bootstrap_factory`. Invalid, missing, negative or non-finite samples fail closed and are not replaced.
4. A production optimization is eligible only when the same stage contributes at least 30 ms at P50 in both retained halves of the diagnostic samples. If no stage meets that condition, M2-10 stops as blocked instead of changing unrelated code.
5. The implementation may remove redundant parsing, allocation, copying or synchronization inside the identified stage, but it must preserve the same authenticated inputs, outputs, error categories, ownership and cleanup. It must not introduce persistent or cross-process verification caches.
6. Every new process still verifies the installed APK signer and signed source bytes before business DEX plaintext is published. APK/source identity, signer lineage, AEAD tags, authenticated metadata, package binding, Guard ordering and environment policy remain mandatory.
7. Decrypt and inflate still target anonymous direct memory only. No plaintext DEX, compressed plaintext, key material or diagnostic sample is written to product cache, code cache, external storage or logs. Existing zeroization, read-only mapping, `DONTDUMP`, lock-page budget and LOW/HIGH behavior remain unchanged.
8. M2-10 acceptance includes targeted unit/Native regression tests, tamper and failure/OOM paths, Release/R8 checks, four-ABI build/export checks, and one exact-head API 29/36 x86_64 KVM Runtime regression matrix. It does not run ARM and does not run the M3-05 A/B benchmark.
9. An independent read-only security review must report P0=0, P1=0 and P2=0 before publication. Any production change after the reviewed freeze invalidates the review.
10. The failed ADR 0015 pair remains the final result for its old product bytes. After M2-10 merges, a separately authorized M3-05 step may evaluate the changed product bytes with one new exact-head A/B pair; that future evidence does not erase or reinterpret the retained failure.

## Consequences

- Runtime optimization is tied to measured startup cost rather than speculative refactoring.
- Security checks remain per-process and fail closed, so the task cannot pass by weakening the protected product.
- M2-10 requires one bounded API 36 diagnostic campaign and one API 29/36 Runtime regression matrix, but no ARM or M3-05 benchmark run.
- M3-05, PR #63 and M4 remain blocked until M2-10 is reviewed and merged and a later acceptance step is explicitly authorized.

## Rejected Alternatives

- Raise the 300/500 ms budgets, relax the 10% limit, reduce samples or discard outliers: changes the release contract instead of improving Runtime.
- Re-run the failed ADR 0015 pair before changing product bytes: prohibited result selection.
- Trust a process-persistent signer result or skip source verification on later starts: creates a stale-verification and replacement window.
- Move signer, AEAD, metadata or Guard checks after class loading or `Application.onCreate`: publishes untrusted code before verification.
- Disable LOW/HIGH memory controls, mapping protections, four-ABI packaging or cleanup: measures a different product.
- Add a production benchmark flag or timing endpoint: creates an attacker-influenced and distributable control surface.
- Optimize multiple stages or perform unrelated refactoring in one PR: prevents attribution and exceeds the task boundary.

## Security Impact

The optimization may change internal Runtime implementation but not its trust decisions. A root, modified ART, Frida or kernel attacker may still capture runtime plaintext; the project continues to claim only increased extraction cost. Signer mismatch, AEAD failure and authenticated-integrity failure remain unconditional blockers. Environment policy remains separate and never becomes a signer or integrity bypass.

## Compatibility Impact

No public Java API, JNI API, container bytes, signer policy, minimum SDK, supported application type or ABI claim changes. The Runtime continues to build four ABIs and uses only public API 29+ startup mechanisms. API 29 and API 36 x86_64 regression evidence is mandatory; ARM compatibility evidence is inherited only for unchanged behavior and is not re-run in M2-10.

## Verification

- A task-specific validator checks the diagnostic schema, exact identity, ordered stages, fixed sample counts, two-half 30 ms eligibility rule, release-surface exclusions and retained M3-05 failure reference.
- Unit and Native tests prove byte-identical authenticated results, stable failures, no lookup before verification, exactly-once cleanup, OOM behavior, zeroization and no plaintext DEX files.
- Release AAR/JAR and four-ABI ELF inspection prove no timing observer, benchmark switch, new JNI export or debug symbol enters distributable artifacts.
- Exact-head Ubuntu/Windows Build and Governance plus API 29/36 x86_64 KVM must pass.
- Independent read-only security review must return P0=0/P1=0/P2=0 before merge.
