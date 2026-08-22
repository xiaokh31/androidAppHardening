# M3-13 local validation

- Task: `M3-13`
- Issue: `#80`
- Branch: `docs/m3-13-diagnostic-identity-contract`
- Base: `c9399b40884778f027ffbe33f96786197365acb3`
- Implementation freeze: `55997e61a2f734ab3d7ed5f8a44a44064b526ac3`
- Timestamp: `2026-08-23T00:08:18+08:00`
- Environment: Windows `10.0.19045.0`; Node.js `v24.12.0`; Git `2.52.0.windows.1`
- Duration: `39 seconds` across the recorded static validation command groups.
- JDK: `not_applicable` (no Java or Gradle command ran).
- Android API: `not_applicable`.
- ABI: `not_applicable`.

## Fixed identities

- Predecessor product tuple SHA-256: `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`
- Predecessor diagnostic: run `32554806537`, job `96987186584`, attempt `1`, head `790ae4579ce3562dc93f3c533ffb786a39517600`
- Predecessor terminal evidence: run `32554917303`, job `96987454333`, attempt `1`, head `415420223441578aa028a1687cb94ef79dfd1924`
- Official proof compact JSON: `5274` UTF-8 bytes, SHA-256 `b3faa34fcee76adb5223c99ccc854fc3000133244cce5a23c8ff2d9432d0d643`
- Contract identity preimage: `1033` UTF-8 bytes, SHA-256 `4104670bbe53aaa193740e4e34128051332657bb8dc8c65b57dd133443387faf`
- Successor task key: `M3-13-SUCCESSOR-DIAGNOSTIC-V1`
- Successor run limit/attempt: exactly `1` / `1`; further renewal is forbidden.

The official proof retains all `17` diagnostic-job steps and all `10` terminal-job steps. Android package preparation succeeded, but canonical APK/profile provenance failed before Native preparation, Release build, AVD creation, device commands, APK installation, sampling or artifact upload. Both official artifact counts are zero.

## Commands and results

All commands below ran from the repository root and exited `0`.

```text
node --check tools/governance/verify-m3-13-diagnostic-identity-contract.mjs
node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs
node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --self-test
node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --base-ref c9399b40884778f027ffbe33f96786197365acb3
node tools/governance/verify-m3-07-high-benchmark-contract.mjs
node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test
node tools/governance/verify-m3-08-startup-stability-contract.mjs
node tools/governance/verify-m3-08-startup-stability-contract.mjs --self-test
node tools/governance/verify-m3-09-startup-attribution-contract.mjs
node tools/governance/verify-m3-09-startup-attribution-contract.mjs --self-test
node tools/governance/verify-m3-11-canonical-artifact-contract.mjs
node tools/governance/verify-m3-11-canonical-artifact-contract.mjs --self-test
node tools/governance/verify-m3-12-profile-retention.mjs --self-test
node tools/governance/validate-project-package.mjs
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean
git diff --check
```

The exact sensitive-material and UTF-8 scan command for this initial freeze was `node tools/governance/validate-project-package.mjs`; it exited `0` and reported `OK: 38 task cards, 11 core docs, 18 ADRs`. This validator scans tracked project text for credential/private-key material, absolute user paths and Unicode replacement characters.

The M3-13 self-test rejected `53` named mutations: `43` identity/proof/document/workflow-presence mutations and `10` base-diff mutations covering Runtime, Host, fixture, benchmark, APK, DEX, private-key, distribution and both canonical workflow paths. Project governance reported `38` task cards, `11` core documents and `18` ADRs. The M3-07, M3-08, M3-09, M3-11 and M3-12 compatibility validators remained green.

## Tracked evidence hashes

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `docs/evidence/M3-13/diagnostic-eligibility-lock.json` | 2564 | `a34549da2817fdc3da13a626148f21bb0a15f63db6a196631b23e440551238ed` |
| `docs/evidence/M3-13/predecessor-official-proof.json` | 6242 | `2571a9cc0dcf5fa7762c7051a467dc0989c37afc20f729db1ffb2e44a8d7df0f` |
| `tools/governance/verify-m3-13-diagnostic-identity-contract.mjs` | 22456 | `f8cbbe729ab8212d38af0d02940591b44c3afce918f20fe5785e6840d6614e32` |

## Scope statement

This freeze changes only ADR/task/governance/evidence/coordination files. It does not add either canonical successor workflow and does not modify Runtime, Host, fixtures, benchmark code, profile/APK/DEX bytes, keys, distribution artifacts or product interfaces. No Gradle, Android SDK setup, device, emulator, KVM, ARM, API 29, API 36 diagnostic, benchmark or M3-05 action ran. Independent review 1 subsequently returned `P0=0/P1=3/P2=1`; this initial freeze is superseded and cannot be published.
