# M2-03 independent read-only security review

## Verdict

- Reviewed implementation parent: `0ed9240617527a321a4baaf38a4d7e15f5d2eb33`
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

No other P0, P1 or P2 finding remained.

## Code and ownership evidence

- `RuntimeStartupGuard` preserves signer → pre-read → Native open → same-handle metadata → identity/config/session ordering, commits only after complete session construction, and retains the primary error while suppressing cleanup failure.
- `M203DeviceRunner` covers 12 exception/OOM windows, 12 metadata/cross-handle/cross-session rejections and real Native handle retirement.
- `PolicyConnectedRunner` uses a shared barrier for true primary/secondary-process overlap and proves per-process miss → hit behavior with different PIDs.
- The KVM workflow signs the historical-only target with v3 lineage only; valid rotation reports lineage count 2 while historical-only is rejected.
- Local offline `:runtime:policy:assembleRelease` passed. `policy-release.aar` is `22052` bytes with SHA-256 `1279240a67dbcb2e6a0aef8cb82519cbf8efbde6e723483566be4723bfb05aff`; it contains only expected production Guard classes and no test/fixture, APK, private key or certificate.

## Exact-head CI and artifacts

- Build run `31415786223`: Ubuntu job `93544350382` and Windows job `93544350324` succeeded.
- Governance run `31415786181`: Ubuntu job `93544350167` and Windows job `93544350282` succeeded.
- KVM run `31415786339`: API 29 job `93544350615` and API 36 job `93544350706` succeeded.

API 29 artifact `9073752802`, digest `6218c77446216d29a980a28c245f43dfa14af6dde7bb4d4aac9f67aee31bc38f`:

- report `765f15c79d1a7c8b9d303f9871c1fe1691212c2d63545498d41d496d0bad8659`
- commands `83a9e9056cba30fef2fd8b1a04d12a1694a702149597f175de6f22ae53dc54a0`
- signer report `4b5284b2438fc2a7f07c13e0494360d59d4b8bb580a4752c6f9e988ea2af7873`
- signer commands `cb4caa8f7afb27c3cad16bfc5917908fd659527d69d6e0218c5ed7cbc1ac48ae`

API 36 artifact `9073865744`, digest `5b7edbdd8018c35bb78bc68759d38d1caf725c47396c035df594e4b3288cc410`:

- report `0bf34c210a8340d3e204df59a4206b7f004718bfd4c45e9029bb0ed000234959`
- commands `cfb8a8dacc744620badfa202e85d3222e279883a1760a8bfffb9fee1829226b7`
- signer report `a4bca0f49f12d09187759a4f648c17b65b4b34e69753b3edcb96a882dd4a6553`
- signer commands `788f572999a2965fd22aca4819e9e3cdf9e020087ac47d39138fd3fdf4adbf8b`

Both platforms passed extracted/direct, 12+12 matrices, 20 cold starts per variant, JNI, cross-DEX, authenticated metadata, zero plaintext DEX and cleanup. All four KVM instrumentation summaries have SHA-256 `9f56d43e4794b86259f5f274c147dcd2fd9941a8cea8bcefee7ee0871ebe9623`.

## API 29 arm64 inheritance boundary

The physical-device report was generated at `659c2b8614f0f30b76d22d8269803925a06924a5` and is validly inherited to the reviewed parent because the intervening diff changes only fixture Activity token logging, the host signer runner and the static gate. Production `runtime/**`, Native four-ABI code, `M203DeviceRunner`, Guard/payload/metadata/JNI/DEX behavior are unchanged.

- report SHA-256 `cf418b7d2cc2803b394d7be4a234f69e96b5c3eb8011bc8f29ebfc2d08234446`
- commands SHA-256 `9a955a563d6b28d09b6197cff59aab7f10cc312123dd02c607afe91679997025`
- extracted/direct instrumentation SHA-256 `7451ff9c7531cb32d0ba0f89ef8d84d56b56b3d8053ee93d2b16e15ed60f263d`

The inherited physical report proves arm64 Native/Guard positive and ownership behavior; it does not prove the run-token negative matrix. That fix is proven only by the reviewed exact-head API 29/36 KVM artifacts.

## Residual risk

- Signer binding and offline key hiding are cost defenses. Runtime replacement, process hooks or memory access remain within the threat model's residual attacker capability.
- KVM uses userdebug images; the API 29 arm64 `user/release-keys`, `ro.debuggable=0` physical evidence supplements that difference.
- GitHub artifacts expire; the reviewed implementation parent, checked-in evidence and recorded digests remain the durable traceability anchors.

## Final conclusion

Implementation parent `0ed9240617527a321a4baaf38a4d7e15f5d2eb33` satisfies the independent-review gate with **P0=0, P1=0, P2=0**. The following evidence-only child must contain no implementation change and must keep all recorded SHA/run/job/hash values consistent.
