# M2-08 Local Validation

- Timestamp: `2026-08-15T00:31:05+08:00`
- Branch: `fix/m2-08-native-parser-bounds`
- Base: `ea30f51373003981cdcdae60dda795ba1fefd587`
- Environment: Windows 10 x64, MSVC `19.44.35222`, C++17
- Validation mode: parser-only Host security regression; no APK, device, emulator, or KVM

## Synthetic crash fixture

- Path: `runtime/native/src/main/cpp/testdata/m2_08_topology_oob.regression.hex`
- Decoded size: `399`
- Decoded SHA-256: `61b51e45d160f1c2ab5fa5fe7e52bb971e3f4a987b98a659087f3ce287867dd9`
- Source: M3-02 run `31768402808`, job `94668992052`, artifact `9207233925`

## Results

| Command | Exit | Result |
|---|---:|---|
| standalone MSVC compile of `container_format.cpp` + `container_format_test.cpp` with `/W4 /WX` | 0 | PASS |
| `m2-08-container-format-test.exe` from the fixture directory | 0 | PASS; exact crash and adjacent bounds regressions rejected |
| `node tools/governance/validate-project-package.mjs` | 0 | PASS; 29 task cards, 11 core docs, 11 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | PASS |
| `git diff --check` | 0 | PASS |

The local full TF-PSA target was not used as acceptance evidence because this workstation has MSVC rather than the reviewed CI clang-cl environment and the existing upstream configuration rejects zero-length array declarations before reaching this change. Exact-head Windows/Ubuntu Build remains required; Ubuntu Build supplies the required ASan/UBSan execution.
