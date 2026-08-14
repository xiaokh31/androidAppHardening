# M2-08 Implementation Plan

- Issue: #53
- Branch: `fix/m2-08-native-parser-bounds`
- Base: `ea30f51373003981cdcdae60dda795ba1fefd587`
- Source failure: GitHub Actions run `31768402808`, job `94668992052`, artifact `9207233925`
- Minimized input: 399 bytes, SHA-256 `61b51e45d160f1c2ab5fa5fe7e52bb971e3f4a987b98a659087f3ce287867dd9`
- Scope: topology bounds, exact regression, adjacent count/table negatives, Host ASan/UBSan, independent review
- Excluded: wire/API changes, M3-02 infrastructure, Android device and KVM tests
- ADR: not required; frozen AHDC v2 bytes, interfaces, limits, and status semantics remain unchanged

M3-02 and PR #52 remain blocked until M2-08 is independently reviewed and merged.
