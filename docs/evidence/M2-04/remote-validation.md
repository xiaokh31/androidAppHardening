# M2-04 remote validation

## PR #46 initial head `f9bc93e54cf2ced1125e7d6fe0190e9d4050679f`

- Draft PR: `https://github.com/xiaokh31/androidAppHardening/pull/46`, base `main`, closes Issue #15.
- Build run `31511953664` and Governance run `31511953804` started normally.
- KVM run `31511953811` failed before JDK, Android tools, build or emulator startup. Both API jobs rejected newly deployed runner image `ubuntu24/20260810.271.1` because the fail-closed allowlist ended at `20260804.265.1`.
- GitHub's official `actions/runner-images` manifest at ref `ubuntu24/20260810.271` identifies image `20260810.271.1`, Ubuntu `24.04.4 LTS`, GNU C/C++ `13.3.0`, and file blob SHA `8a92fe558f0741f9c2e2ca77deae648bd30bfcd8`.
- The bounded correction adds only that exact image/ref pair to the existing Ubuntu allowlist in Build and KVM. Compiler checks remain fixed at GCC/G++ `13.3.0`; no Android, Runtime, device or acceptance behavior changes.

Replacement exact-head run IDs and artifacts will be appended only after the corrected head completes. Successful jobs will not be manually rerun.
