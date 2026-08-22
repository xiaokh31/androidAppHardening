# M3-10 workflow successor independent review

- Timestamp: `2026-08-22T13:32:08+08:00`
- Base: `c9399b40884778f027ffbe33f96786197365acb3`
- Reviewed implementation freeze: `4c58759bb4e4e53eb56fc9d3cbd3c8d0929ba1d0`
- Environment: Windows 10.0.19045 amd64; Node.js 24.12.0; Temurin 17.0.19; Gradle 9.5.0
- Result: `PASS — P0=0/P1=0/P2=0`

## Scope and conclusion

The independent read-only review covered the two canonical workflows, the runner, complete verifier, terminal collector, profile-freeze governance gate and the coordination facts. It confirmed the fixed M3-12 numeric release/asset/archive source, the fixed M3-11 original artifact, first-and-only branch-push identity, bounded API 36 execution and forced cleanup, terminal raw GitHub pages, artifact/ledger binding, complete package member hashes, result recomputation, reviewed-byte inheritance and exclusion of ARM, API 29 and M3-05.

Findings discovered during the review were fixed before this freeze: the current Actions artifact media type and Azure result redirect; canonical history uniqueness across different heads; exact package member/manifest verification; bounded page/download/ZIP expansion; and the requirement that the terminal request be the diagnostic head's only direct child and only changed file. Final review and the bounded page-reader incremental review both returned all zero.

## Fixed executable hashes

| File | SHA-256 |
|---|---|
| `.github/workflows/m3-09-startup-attribution.yml` | `a09145d499d06a769cce38e4229019fa3360bfbff26be6ed18ca1552ab1d5559` |
| `.github/workflows/m3-09-startup-attribution-evidence.yml` | `5aeed26107a4bcd62575c561018596eae6baa889d8039fd1e9fd12a0e52fd6ec` |
| `tools/validation/collect-m3-10-github-evidence.mjs` | `606046579a79fbb905628326bb2b70fd05addd0afa6b07b3d0746f23be2978bd` |
| `tools/validation/run-m3-10-startup-attribution.mjs` | `640c40d502a410f7609c0b1113a7b096a56730f5039aa18c6dffd1ddb741d228` |
| `tools/validation/verify-m3-10-startup-attribution.mjs` | `d4781da1888bbacc0e52b0851d3ab1e61bcf315a55b2a068ae9d0d320985486a` |
| `tools/governance/verify-m3-10-profile-freeze.mjs` | `74c4d13d83215e77da097b642c12423e03e7c951cd8f37f5631acaebca1cd195` |

## Bounded verification

| Check | Result |
|---|---|
| Canonical/profile actual-byte Gradle verifier | PASS; report SHA-256 `1610f895cb1a3003387a2c7f2e2e1474d6fbbfc523da8fc11c88d6cd283c5b93` |
| Profile actual-file self-test | PASS; 17 mutations rejected |
| Complete verifier self-test | PASS; 49 mutations rejected |
| Cleanup command-result self-test | PASS; 8 mutations rejected |
| Terminal collector self-test | PASS; 5 redirect cases and 1 archive-bound case |
| Profile-freeze self-test | PASS; 13 mutations rejected; workflows reviewed; production observer absent |
| M3-12 retained package verifier | PASS; release `374769776`, asset `524507375`, archive `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964` |
| M3-11 actual-byte provenance verifier | PASS; artifact `9260244215`, not expired |
| Project governance / strict HandOff / diff check | PASS |

The remote branch had zero prior push runs before publication. A read-only request against retained artifact `9260244215` proved that `Accept: application/octet-stream` now fails with HTTP 415, while the pinned `application/vnd.github+json` request succeeds and redirects to the anchored `productionresultssaN.blob.core.windows.net` result host. No token is sent on the second hop.

No emulator, device, KVM, benchmark, ARM, API 29, M3-05 or diagnostic workflow ran during this review. The unique API 36 eligibility remained unused at the freeze.
