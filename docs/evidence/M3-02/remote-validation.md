# M3-02 Remote Validation

- Task: `M3-02`
- Branch: `chore/m3-02-tamper-fuzz`
- Pull request: [#52](https://github.com/xiaokh31/androidAppHardening/pull/52)
- Issue: [#19](https://github.com/xiaokh31/androidAppHardening/issues/19)
- Final implementation freeze: `90ef2ecf662371c82fed5f3d0fa92dbf9324e9e2`
- Final fuzz/device implementation head: `d961d4a27cabc8c33a1ac8262096c12cc490b6b1`
- Final CI-lock head: `699ea233201d6630a8b621d550b2f20b54c816df`

## Independent review

The final bounded independent read-only review of `90ef2ecf662371c82fed5f3d0fa92dbf9324e9e2` returned `P0=0`, `P1=0`, and `P2=0`. The later production Native parser fix was handled and reviewed independently as M2-08 before it was merged into this branch. Changes from `90ef2ec` through `d961d4a` are limited to reviewed parser-fix integration and CI execution corrections; `699ea23` changes only deterministic Build/report wiring and evidence.

## Fuzz matrix

M3-02 Fuzz run [`31830770675`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31830770675) completed successfully at `d961d4a27cabc8c33a1ac8262096c12cc490b6b1`:

| Target | Platform | Job | Result |
|---|---|---:|---|
| Jazzer APK inspector | Ubuntu 24.04 | `94865718692` | PASS |
| Jazzer Binary AXML | Ubuntu 24.04 | `94865718813` | PASS |
| Jazzer APK inspector | Windows 2025 | `94865718723` | PASS |
| Jazzer Binary AXML | Windows 2025 | `94865718737` | PASS |
| Native libFuzzer + ASan/UBSan | Ubuntu 24.04 | `94865718720` | PASS |
| Unified fail-closed summary | Ubuntu 24.04 | `94869026186` | PASS |

Each PR target retained the fixed 600-second duration, 2 GiB RSS ceiling, five-second input timeout and fixed corpus/regression contract. The final CI-lock child did not change fuzz sources, inputs, limits or workflow semantics, so its automatically triggered duplicate run `31832372708` was cancelled instead of repeating the completed matrix.

## Build and governance

- Build [`31832372574`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31832372574), exact head `699ea233201d6630a8b621d550b2f20b54c816df`:
  - Ubuntu 24.04 job `94870826954`: PASS.
  - Windows 2025 job `94870826893`: PASS.
- Governance [`31832372727`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31832372727), exact head `699ea233201d6630a8b621d550b2f20b54c816df`: PASS.

## API 29/36 KVM

M0-05 Linux KVM run [`31832372549`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31832372549) completed successfully at exact head `699ea233201d6630a8b621d550b2f20b54c816df`:

| Platform | Job | Artifact | Size | Artifact SHA-256 | Result |
|---|---:|---:|---:|---|---|
| API 29 x86_64 | `94870826832` | `9231598533` | `3814732` | `4d0425de5c7e1d1f16cd66a7e2eae8afe6423ce386f1db02f6ae79295884b128` | PASS |
| API 36 x86_64 | `94870826886` | `9231692037` | `3176821` | `61f22133de3049aa1101728faf5d26736948f67b87e7d998bf8f736874fd846c` | PASS |

Both jobs executed the existing bounded Release/R8 device acceptance and the exact 69-case M3-02 Runtime summary. The M2-06 report consumed by the summary is produced by the same M202 loader runner and preserves the exact two-variant, 21-case, publication, close-count, mapping cleanup and primary/suppressed-error checks. No local emulator or physical device was started for M3-02.

## Result

All required bounded local, fuzz, sanitizer, dual-platform Build/Governance and API 29/36 KVM gates passed. No crash, sanitizer finding, timeout, OOM, unexpected publication or cleanup failure remains in the accepted evidence. This result describes only the executed scope and does not claim absolute tamper resistance.
