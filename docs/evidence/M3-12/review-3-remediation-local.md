# M3-12 review-3 remediation validation

- Review-3 result: `FAIL — P0=0/P1=0/P2=1`
- Rejected freeze/evidence: `be93584bf60ee89a683ed42473acf102625d21db` / `13ec402c6c5dbc02f3f8491b58de7dea0b37963d`
- Mutation remediation freeze: `3415e2826054b0ce31c32e8f934e973cb1a85cd0`
- Timestamp: `2026-08-22T10:31:49.6725695+08:00`
- Dynamic scope: none

The seven private-key/keystore/token/path vectors are now each the decompressed content of a valid generated APK ZIP and pass through production `scanApkBytes`; none calls only the lower-level byte scanner. The prior false overlap case is replaced by a structurally valid two-entry ZIP with one inserted byte between otherwise valid local records and corrected central/EOCD offsets. Its test requires the exact `local record overlap or gap` error, so an earlier unrelated rejection fails the self-test.

Commands, all exit `0`:

```text
node --check tools/governance/verify-m3-12-profile-retention.mjs
node tools/governance/verify-m3-12-profile-retention.mjs --archive build/m3-12/remediation-fetch/m3-10-profile-package-v1.zip --self-test --base-ref 9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a
node tools/governance/validate-project-package.mjs
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean
git diff --check
```

The exact result remains `lockMutations=24`, `archiveMutations=12`, `sensitiveMutations=24`, but all 24 sensitive/parser cases now traverse `scanApkBytes`. Verifier size is `36078` bytes and SHA-256 is `7eea0043a2abbfb8c771b22c696d99f3261873278f1c784ae674d5ec0d29e635`.

No asset/member/profile/APK/DEX byte changed; no workflow or Android environment ran. Fourth independent bounded review remains mandatory before push or workflow creation.
