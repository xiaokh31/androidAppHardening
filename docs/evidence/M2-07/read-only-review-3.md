# M2-07 independent read-only security review 3

## Frozen input

- Commit: `0662b9e2ce22f5728bb7a757ada6b6bac8a94536`
- Reviewer: independent `m2_07_security_review_3` Agent
- Result: **FAIL**; `P0=0`, `P1=0`, `P2=1`
- Files changed, branch/build/download/device/emulator actions: none

## Prior finding disposition

All seven review-1 findings and all three review-2 findings were independently **CLOSED**. The review confirmed archive-before-parser and full-tree promotion, complete AES/HKDF boundaries and concurrency serialization, Release four-ABI scans, complete machine lock and vulnerability table, platform-exact symlink counts, fixed Ubuntu/GNU assertions, the exact two-entry Windows hosted-image mapping, and truthful README/HandOff/PR scope.

Exact-SHA Build `31139693696`, Governance `31139693779` and API 29/36 KVM `31139693700` all succeeded and executed the expected image/compiler, Host crypto, 147-link, Release ABI, device acceptance and cleanup gates.

## New finding

### P2 — symlink wrong-prefix gate lacked a substantive negative test

`verifySymlinkSurface` rejected wrong-prefix paths in production code, but `--self-test` covered only Unix zero count, valid full sets, Windows zero/full counts and Windows partial count. Mutating the lock's prefix proved deep-lock equality only; it did not prove that an actual 147-entry symlink inventory containing one path outside the reviewed prefix is rejected.

If a later change removed or weakened the `startsWith` surface check, the self-test could remain green while the regular-tree hash intentionally excluded symlinks and a wrong-location set received a stamp. Add wrong-prefix rejection for both Linux and Windows full-count inventories, retain the existing zero/partial cases, create a new SHA and rerun all exact-head gates and independent review.

## Conclusion

This frozen SHA is permanently rejected. **FAIL: P0=0, P1=0, P2=1.**
