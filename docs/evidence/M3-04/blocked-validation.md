# M3-04 blocked validation

- Candidate head: `96e1fa91b8293622714ad61adb3932ede67027cb`
- Draft PR: `#58`
- Issue: `#21`
- Result: `BLOCKED`
- Recorded at: `2026-08-15T10:55:20+08:00`

## Passing gates

- Build run `31859363997`: Ubuntu and Windows passed on the candidate head.
- Governance run `31859363978`: Ubuntu and Windows passed on the candidate head.
- KVM run `31859364008`, API 36 x86_64 job `94949834605`: the bounded Release/R8 device matrix, cleanup, and artifact upload passed.
- Downloaded API 36 evidence remains ignored under `build/m3-04/remote/96e1fa9/api36/`.
- API 36 normalized cell SHA-256: `d8af022feedf249c60f32f271072e73264a1cf425636d6b6426efd61693c47ee`.
- API 36 fixture/runtime/signer report SHA-256 values: `e292b2c813a6d78d0485e5623290a559d1e6b3e8a890b5a1daca92d60d927458`, `b793bf0f814474a93cbd0cf3cd7c1403b6c8a240136be136e9d2c7244abefd2b`, and `00bd2ee83621554ad9ea6578076e2c991c39a9e50b91b43f870cc560583ce805`.

The API 36 cell is retained as historical evidence only. Its `sourceCommit` is the pull-request merge checkout `c03a8238ebfd688e994ae7499ef4ba4661bec776`, not the candidate head. The resumed M3-04 run must read the pull-request head SHA from the event payload and regenerate the cell; this artifact is not final exact-head evidence.

## Blocking failures

### API 29 x86_64 Runtime relaunch

- First retained failure: KVM run `31858315765`, API 29 job `94947032544` at head `5adf1647c8c015e1a09135362a08805262176060`.
- Single allowed retry: KVM run `31859364008`, API 29 job `94949834547` at candidate head `96e1fa91b8293622714ad61adb3932ede67027cb`.
- Both attempts recorded the complete expected first startup event sequence and then an API 29 Activity configuration relaunch.
- The second component instantiation failed with stable code `AAH-RUNTIME-BOOT-COMPONENT`; the retry failed on `startup-provider` after the first `provider.ready`, `startup_provider.create`, and `activity.create` sequence.
- The runner retained the failure and cleanup artifact. No third retry was started and the assertion was not weakened.

This is a production M2-01 Shell/component lifecycle compatibility defect, not an M3-04 inventory-only change. It requires a separate bounded ADR/task/Issue/branch/PR before M3-04 may resume.

### API 29 ARM32/ARM64 physical device

The authorized non-root API 29 `user` device reports both `arm64-v8a` and `armeabi-v7a`. The bounded campaign and its single retry both stopped before the first product assertion because the OEM rejected the first installation with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. The retry build and test APK generation passed, no product result was substituted, and no install loop was used.

Both ARM cells remain unverified mandatory cells. A user-present install approval is still required after the Runtime relaunch fix merges.

## Completion impact

- API 29 x86_64: not `VERIFIED`.
- API 29 arm64-v8a: not `VERIFIED`.
- API 29 armeabi-v7a: not `VERIFIED`.
- API 36 x86_64: passing historical run, but final exact-head normalization must be regenerated after the dependency fix.
- PR #58 stays draft and must not merge.
- M3-05 must not start.
