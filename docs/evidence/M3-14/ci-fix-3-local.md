# M3-14 CI fix 3 local validation

- Timestamp: `2026-08-23T09:41:48+08:00`
- Branch: `feat/m3-14-successor-startup-diagnostic`
- Failed exact head: `dc894f042778bc79deb85b86585180514fe4e0a4`
- Failed Governance: `32610885085` (Ubuntu and Windows)
- Cancelled stale Build: `32610885081`
- Cancelled out-of-scope runs: KVM `32610885101`, fuzz `32610885099`, equivalence `32610885086`
- Canonical workflows: absent
- API 36 / `runAttempt=1`: not consumed

## Root cause and bounded correction

Both Governance jobs passed M3-07 and M3-08, then failed in M3-09 because that historical validator retained two pre-M3-14 INDEX assertions:

- the execution route stopped at `M3-13 → separately authorized successor implementation → M3-05`;
- the M3-05 dependency fragment stopped at `M3-09, M3-13`.

The bounded correction synchronizes both assertions with the current formal route and dependency list, and updates the existing `m305_dependency_removed` mutation so it still deletes M3-09 from the exact current list. No attribution algebra, owner selection, report schema, thresholds, production-diff scope, diagnostic implementation or Android behavior changes.

## Exact local CI sequence

The complete `Validate project package` command sequence from Governance was run locally, followed by the M3-14 workflow-absent gate. Every command exited `0` on Windows 10.0.19045 with Node.js v24.12.0:

- project package: `39` task cards, `11` core documents, `18` ADRs;
- M3-07 positive plus `10 surface + 20 report negatives`;
- M3-08 positive plus `1 diff + 45 package negatives + 2 arithmetic positives`;
- M3-09 positive plus `58` named mutations, including `m305_dependency_removed`;
- M3-11 positive plus `26` mutations;
- M3-13 positive plus `66` mutations;
- M3-14 profile freeze with both canonical workflows absent and production observer absent.

No Gradle, Android, emulator, device, KVM, fuzz, equivalence, benchmark or canonical diagnostic workflow was run by this correction.
