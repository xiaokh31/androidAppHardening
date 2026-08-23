# M3-14 PR #83 Governance CI fix 1

- Rejected exact head: `2ba90618734bf4f509eb6052a3ce649e50aca71a`
- Fix implementation: `ca64e2c5d5dca972f9e3ef43b55b605e1dc8560d`
- Draft PR: `#83`
- Governance run: `32609765769`
- Build run cancelled after supersession: `32609765737`
- Timestamp: `2026-08-23T09:13:59+08:00`

Ubuntu and Windows Governance both failed at `Validate project package` before the M3-14 gate with `host/container/build.gradle.kts: prohibited environment HIGH control`. The eight M310 environment variables are exact test-only file/report inputs for the retained-profile verifier; none selects HIGH risk, alters production behavior, regenerates a profile or enters a release artifact.

The bounded fix changes only `tools/governance/verify-m3-07-high-benchmark-contract.mjs`. It removes from scanning only an exact field-to-variable mapping on the exact `host/container/build.gradle.kts` path. Unknown fields, wrong variables, trailing content and `M310_FORCE_HIGH_PROFILE` remain scanned and rejected. Existing 10 production-surface mutations plus 20 report negatives continue to pass.

Local commands all exited `0`:

- `node --check tools/governance/verify-m3-07-high-benchmark-contract.mjs`
- `node tools/governance/verify-m3-07-high-benchmark-contract.mjs`
- `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test`
- `node tools/governance/validate-project-package.mjs`
- `git diff --check`

Candidate validator SHA-256: `20801624424f0eb255f94fb27822e3f46d4127d43943d7a30c37e39c9de05718`.

Automatically triggered KVM `32609765741`, fuzz `32609765726` and equivalence `32609765768` were cancelled as out of scope; none is evidence. Both canonical successor workflows remain absent and the API 36 successor entitlement remains unconsumed. This CI fix requires a new exact freeze and independent all-zero review before replacement push.
