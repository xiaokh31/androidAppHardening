# M2-03 independent read-only security review

## Verdict

- Reviewed implementation parent: `8211a60dca604ac1aab56b4839bcd96d5494aa05`
- Full-review parent: `0ed9240617527a321a4baaf38a4d7e15f5d2eb33`; `8211a60` received a separate read-only incremental review.
- Main merge-base: `dec1ef68f69eea26ae1bc6a1132bf26bf39ba0f8`
- Result: **PASS**
- Findings: **P0=0, P1=0, P2=0**

The independent reviewer worked read-only and did not edit, stage, commit, push, download, start an emulator or install an APK. The review covered the complete merge-base-to-parent diff, the M2-03 task contract, ADR 0003/0006/0007/0008, the M1-02/M1-04/M2-02 contracts, threat model and test strategy.

## Scope and confirmed properties

- One current signer, ordered valid lineage, historical-only rejection and fixed `apksig 9.3.0` behavior.
- Same-handle authenticated metadata, Guard atomic ownership and primary/suppressed cleanup.
- Native handle/mapping/session idempotent close and exactly-once rollback.
- Bounded cache, thread concurrency and real two-process concurrent apksig validation.
- Release/R8 fixture keeps, production dependency/API boundary and absence of production signing capability.
- Sensitive-material, plaintext DEX, device/host path and evidence-transcript boundaries.
- Extracted/direct Release/R8 behavior on API 29/36 x86_64 KVM and API 29 arm64 physical hardware.

## Review finding closed

The superseded parent `659c2b8614f0f30b76d22d8269803925a06924a5` allowed API 29 startup evidence to match retained tagged logcat from an earlier scenario. The accepted parent closes that P1:

1. `M203ColdStartActivity` accepts only a bounded 16-hex run token and includes it in the startup marker.
2. `run-m2-03-signer-matrix.mjs` creates a random token per scenario, filters only that token and requires exactly one exact marker.
3. `verify-m2-03-runtime-integrity.mjs` locks the token/uniqueness gate.
4. Both exact-head KVM artifacts contain seven one-line log files with seven distinct tokens. Historical-only and repeated `CONTAINER` scenarios cannot match earlier output.

The first evidence-only child later exposed an API 29 KVM `force-stop`/start orchestration race. The incremental review confirmed that `8211a60` changes only the shared host acceptance script, adds a 150 ms stabilization window only when `taskId` is M2-03, and still requires the exact Activity, a live PID and parseable PSS. Any anomaly writes cold-start logcat and fails immediately; there is no retry, skipped sample or replacement sample. M2-02 does not enter the new wait branch and its acceptance contract is unchanged.

No other P0, P1 or P2 finding remained. The full review plus the `8211a60` incremental review conclude **P0=0, P1=0, P2=0**.

## Code and ownership evidence

- `RuntimeStartupGuard` preserves signer → pre-read → Native open → same-handle metadata → identity/config/session ordering, commits only after complete session construction, and retains the primary error while suppressing cleanup failure.
- `M203DeviceRunner` covers 12 exception/OOM windows, 12 metadata/cross-handle/cross-session rejections and real Native handle retirement.
- `PolicyConnectedRunner` uses a shared barrier for true primary/secondary-process overlap and proves per-process miss → hit behavior with different PIDs.
- The KVM workflow signs the historical-only target with v3 lineage only; valid rotation reports lineage count 2 while historical-only is rejected.
- Local offline `:runtime:policy:assembleRelease` passed. `policy-release.aar` is `22052` bytes with SHA-256 `1279240a67dbcb2e6a0aef8cb82519cbf8efbde6e723483566be4723bfb05aff`; it contains only expected production Guard classes and no test/fixture, APK, private key or certificate.

## Exact-head CI and artifacts

- Build run `31419276164`: Ubuntu job `93555830810` and Windows job `93555830952` succeeded.
- Governance run `31419276874`: Ubuntu job `93555832970` and Windows job `93555833035` succeeded.
- PR KVM run `31419279082`: API 29 job `93555839095` and API 36 job `93555839055` succeeded.

API 29 artifact `9075019028`, digest `fe0ac06176604c9988e33dc836c090258b4654fdf42e0d7f6809f8e3fcdbb62f`:

- report `5d620f56de5cdd4b6b1aa674e5e53afb3f8df22e36d750066d32a66a28dd7428`
- commands `93e4f0fcb3bbafed801dc7664894e38b388273c3d6e6e9f34a3cd95fd7bae802`
- signer report `b4ecb1586446c3d863e0b92df6c0f57b2cfe505f6e404382d09f0f65cca4ab54`
- signer commands `415f10ac6e48331ea96395c3852264af21cc67d14e9dafe24813d5f84af845bc`

API 36 artifact `9075172980`, digest `bd9a6b0eaf45afe10ea51d4ead2790b5304944720fe4243602c0fd7a52bbd3ff`:

- report `22ad1b3dc984458eb8b4a5643ff9c0940bbde8de3e67086cb39b224f104e4311`
- commands `f67a35b78a0824c945809e8491a56ba892a07a1bf8d4bc33c982f1a2325f423b`
- signer report `139612d0d4d30295a1944cfcb6ad0fdfd960b10a3e455059c3360f9dc62fe34c`
- signer commands `35895c1dad16c9b594a001ac7a0c368c47e6059191580ec798d07ac2e1461d25`

Both platforms passed extracted/direct, 12+12 matrices, 20 cold starts per variant, JNI, cross-DEX, authenticated metadata, zero plaintext DEX and cleanup. All four KVM instrumentation summaries have SHA-256 `9f56d43e4794b86259f5f274c147dcd2fd9941a8cea8bcefee7ee0871ebe9623`.

## API 29 arm64 inheritance boundary

The physical-device report was generated at `659c2b8614f0f30b76d22d8269803925a06924a5` and is validly inherited to the reviewed parent because the intervening diff changes only fixture Activity token logging, host signer/static validation and host-side cold-start orchestration. Production `runtime/**`, Native four-ABI code, target/test APKs, `M203DeviceRunner`, Guard/payload/metadata/JNI/DEX behavior are unchanged.

- report SHA-256 `cf418b7d2cc2803b394d7be4a234f69e96b5c3eb8011bc8f29ebfc2d08234446`
- commands SHA-256 `9a955a563d6b28d09b6197cff59aab7f10cc312123dd02c607afe91679997025`
- extracted/direct instrumentation SHA-256 `7451ff9c7531cb32d0ba0f89ef8d84d56b56b3d8053ee93d2b16e15ed60f263d`

The inherited physical report proves arm64 Native/Guard positive and ownership behavior; it does not prove the run-token negative matrix or KVM orchestration stabilization. Those fixes are proven only by the reviewed exact-head API 29/36 KVM artifacts.

## Residual risk

- Signer binding and offline key hiding are cost defenses. Runtime replacement, process hooks or memory access remain within the threat model's residual attacker capability.
- KVM uses userdebug images; the API 29 arm64 `user/release-keys`, `ro.debuggable=0` physical evidence supplements that difference.
- GitHub artifacts expire; the reviewed implementation parent, checked-in evidence and recorded digests remain the durable traceability anchors.

## Final conclusion

Implementation parent `8211a60dca604ac1aab56b4839bcd96d5494aa05` satisfies the full plus incremental independent-review gate with **P0=0, P1=0, P2=0**. The following evidence-only child must contain no implementation change and must keep all recorded SHA/run/job/hash values consistent.
