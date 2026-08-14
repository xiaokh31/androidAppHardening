# M3-03 local validation

- Timestamp: `2026-08-15T05:32:47+08:00`
- Branch: `chore/m3-03-windows-ubuntu-equivalence`
- Base: `68c4fd25c86cae61dc00039af118b4e35b566741`
- Implementation freeze: `c4f68b176875f642487e25cf2405fd726ebcb51e`
- Host: Windows 10.0.19045 x64; Eclipse Temurin `17.0.19+10`; Gradle `9.5.0`; Node.js `24.12.0`
- Device boundary: no emulator or physical device was started; M3-03 is Host-only.

## Commands

| Command | Exit | Result |
|---|---:|---|
| repository-local offline Gradle `:integration-tests:crossPlatformCorpus -Pm303Platform=windows -Pm303RuntimeBundle=integration-tests/build/generated/m3-01/runtime-bundle` | 0 | One Windows development full-flow run produced and independently authenticated/decrypted all 18 protected outputs (nine fixtures, two randomized passes), verified unsigned ZIP/AHDC/DEX/report semantics, both negatives, input immutability, and signing cleanup. This run preceded the final environment/CRC/topology assertion-only edits and is development evidence, not the exact-head release gate. |
| repository-local offline Gradle `:integration-tests:compileKotlin` | 0 | Exact current Kotlin sources compiled successfully in 36 seconds; 11 tasks, one executed and ten up-to-date. |
| `node --check tools/compare-platform-results/index.mjs` | 0 | Exact current comparator syntax passed. |
| `node tools/compare-platform-results/index.mjs self-test` | 0 | Unknown report-field rejection self-test passed. |
| `node tools/governance/validate-project-package.mjs` | 0 | 29 task cards, 11 core documents, and 11 ADRs passed. |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | Active M3-03 handoff passed strict validation. |
| `git diff --check` | 0 | No whitespace errors. |

## Development artifact boundary

- The successful Windows development run demonstrated the expensive product full flow before the final candidate freeze. The last source changes only add fail-closed comparator projections and pin/record `UTC`, `Locale.ROOT`, Gradle, and build-tools metadata; they do not change product APK generation.
- Development outputs remain ignored under `build/equivalence/windows` and are not committed. Their prior environment record used the host default timezone, so they are deliberately not relabelled as exact-current evidence after the new UTC assertion.
- The exact frozen-head Windows and Ubuntu platform artifacts, hashes, and `build/reports/equivalence-summary.json` remain mandatory PR CI evidence. No local result substitutes for that cross-platform gate.

## Pending gates

- Publish the frozen implementation on the unique Issue #20 draft PR.
- Run one exact-head Windows/Ubuntu equivalence workflow and the normal Build/Governance checks.
- Record both platform artifact hashes, summary SHA-256, runner/toolchain facts, then update README/task status/HandOff and merge before starting M3-04.
