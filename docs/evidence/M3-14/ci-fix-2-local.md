# M3-14 CI fix 2 local validation

- Timestamp: `2026-08-23T09:32:34+08:00`
- Branch: `feat/m3-14-successor-startup-diagnostic`
- Failed exact head: `018f5fe499e99687059e2260154b819faae04fa7`
- Failed Governance: `32610524766` (Ubuntu and Windows)
- Cancelled stale Build: `32610524678`
- Cancelled out-of-scope runs: KVM `32610524667`, fuzz `32610524697`, equivalence `32610524709`
- Canonical workflows: absent
- API 36 / `runAttempt=1`: not consumed

## Root cause and bounded correction

Both Governance jobs failed in `Validate project package` after M3-07 passed. The M3-08 validator still required the historical placeholder sequence:

`M3-07 → M3-08 → M3-09 → M3-11 → M3-12 → M3-10 → M3-13 → separately authorized successor implementation → M3-05`

The project INDEX now correctly names the authorized implementation and required owner phase:

`M3-07 → M3-08 → M3-09 → M3-11 → M3-12 → M3-10 → M3-13 → M3-14 → owner remediation → M3-05`

The bounded fix updates only that exact `requireText` token in `verify-m3-08-startup-stability-contract.mjs`; it does not change M3-08 arithmetic, campaign, mutation, production-diff, diagnostic, Android or product behavior.

## Validation

All commands ran on Windows 10.0.19045 with Node.js v24.12.0 and exited `0`:

- `node --check tools/governance/verify-m3-08-startup-stability-contract.mjs`
- `node tools/governance/verify-m3-08-startup-stability-contract.mjs`
- `node tools/governance/verify-m3-08-startup-stability-contract.mjs --self-test`
  - `1 diff + 45 package negatives + 2 arithmetic positives`
- `node tools/governance/verify-m3-07-high-benchmark-contract.mjs`
- `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test`
  - `10 surface + 20 report negatives`
- `node tools/governance/verify-m3-14-profile-freeze.mjs --base-ref 960eb9f406eb1a7b7c9b324598fb59936aa1c5b5`
  - workflow absent; production observer absent
- `node tools/governance/validate-project-package.mjs`
  - `39` task cards, `11` core documents, `18` ADRs
- `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean`
- `git diff --check`

No Gradle, Android, emulator, device, KVM, fuzz, equivalence, benchmark or canonical diagnostic workflow was run by this correction.
