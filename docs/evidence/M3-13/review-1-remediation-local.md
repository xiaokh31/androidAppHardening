# M3-13 review-1 remediation local validation

- Task: `M3-13`
- Issue: `#80`
- Branch: `docs/m3-13-diagnostic-identity-contract`
- Base: `c9399b40884778f027ffbe33f96786197365acb3`
- Superseded freeze/evidence: `55997e61a2f734ab3d7ed5f8a44a44064b526ac3` / `bec3d0ddeccc356c31f69add2e37e197cd127531`
- Remediation freeze: `7ea0f4198bfccf57808a4c976c46b2b1cb87bf6e`
- Timestamp: `2026-08-23T02:12:06+08:00`
- Duration: `28 seconds` for the final static validation group before evidence-only coordination.
- Environment: Windows `10.0.19045.0`; Node.js `v24.12.0`; Git `2.52.0.windows.1`; GitHub CLI `2.96.0`
- JDK: `not_applicable`.
- Android API: `not_applicable`.
- ABI: `not_applicable`.

## Finding closure

1. Execution identity is no longer embedded in either candidate workflow or the run name. The exact run name binds only the fixed task key, contract identity and product tuple; the ledger/artifact manifest binds the candidate hashes and implementation freeze without a hash cycle.
2. Six exact official API response pages are retained. The validator checks each endpoint/path/byte length/SHA-256, parses run/job-step/artifact facts, and obtains reviewed workflow/runner/verifier/environment-lock bytes directly from fixed historical Git objects.
3. M3-05 treats M3-10 as terminal historical input, removes it from the formal completion dependency list and requires the concrete successor implementation/remediation task IDs once those tasks exist.
4. Evidence records duration, JDK/API/ABI applicability and an exact sensitive-scan command/result.

## Fixed identities

- Official proof compact JSON: `6871` UTF-8 bytes, SHA-256 `9e06abb32d9e0a933e4254bea6fd781cd2a2a95d2980835fd79956e4b315f117`.
- Contract preimage: `1033` UTF-8 bytes, SHA-256 `580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419`.
- Product tuple: `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`.
- Changed-file count from base through remediation freeze: `26`.

## Commands and results

All completed commands ran from the repository root and exited `0`:

```text
node --check tools/governance/verify-m3-13-diagnostic-identity-contract.mjs
node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs
node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --self-test
node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --base-ref c9399b40884778f027ffbe33f96786197365acb3
node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --sensitive-only --base-ref c9399b40884778f027ffbe33f96786197365acb3
node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test
node tools/governance/verify-m3-08-startup-stability-contract.mjs --self-test
node tools/governance/verify-m3-09-startup-attribution-contract.mjs --self-test
node tools/governance/verify-m3-11-canonical-artifact-contract.mjs --self-test
node tools/governance/verify-m3-12-profile-retention.mjs --self-test
node tools/governance/validate-project-package.mjs
git diff --check c9399b40884778f027ffbe33f96786197365acb3...HEAD
```

M3-13 rejected `65` named mutations. The explicit sensitive command reported `OK: M3-13 sensitive scan; 26 changed files inspected`. Project governance reported `38` task cards, `11` core documents and `18` ADRs. Strict HandOff is rerun without exemption on the clean evidence-only head.

## Tracked hashes

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `docs/evidence/M3-13/diagnostic-eligibility-lock.json` | 2564 | `d394b99d5060fcbb1e0ad93c179648cd0932128bfdb8030635f6299b2a0f9352` |
| `docs/evidence/M3-13/predecessor-official-proof.json` | 8104 | `95612fd9d52fa749066bcda5b6365f804227a2f47ccb8ce789f5df69366cbd7a` |
| `tools/governance/verify-m3-13-diagnostic-identity-contract.mjs` | 33803 | `88598ca5d8a5dbd201bebf7a767bbc771a74765496a91895fe3c4c8053131825` |
| `raw/diagnostic-run.json` | 13444 | `ef5cb179d65c0f37dadbfa6a66de3fff305f9f5e596496ad34e11ac00fcdd3d8` |
| `raw/diagnostic-jobs-page-1.json` | 4005 | `31b285456b24499ff362715c4c1a0ae2d937e644d4c6e7a8f2c680c9840b5cda` |
| `raw/diagnostic-artifacts-page-1.json` | 33 | `d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42` |
| `raw/terminal-run.json` | 13381 | `25da806a36a0ec12916ca915eaa5496916527ba6d0cf1acff862b28b04f4f783` |
| `raw/terminal-jobs-page-1.json` | 2730 | `27f9c924ee42a83cd42e5a8c7ef9a8bb20eaad7a8e4de60de1d441bc593d3ef2` |
| `raw/terminal-artifacts-page-1.json` | 33 | `d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42` |

## Scope statement

The remediation changes only M3-13 governance/contracts/evidence and the M3-05 dependency wording needed to make the successor route executable. It does not add either canonical diagnostic workflow, change Runtime/Host/fixture/benchmark implementation or regenerate APK/DEX/profile bytes. No Gradle, Java, Android SDK, API 36 diagnostic, device, emulator, KVM, ARM, API 29, benchmark or M3-05 execution occurred. A second independent all-zero review remains mandatory before push or draft PR.
