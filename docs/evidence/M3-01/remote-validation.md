# M3-01 remote validation

- Implementation freeze: `c281a3a011229632cfe7a361d998eb8255b22b75`
- Branch: `chore/m3-01-android-fixtures`
- Pull request: `#49`
- Timestamp: `2026-08-14T05:25:46+08:00`

## GitHub Actions

| Workflow | Run | Jobs | Result |
|---|---:|---|---|
| Build | `31744719469` | Ubuntu 24.04 `94596350603`; Windows 2025 `94596350688` | PASS |
| Governance | `31744719457` | Ubuntu 24.04 `94596350928`; Windows 2025 `94596351122` | PASS |
| M0-05 Linux KVM | `31744719467` | API 29 x86_64 `94596350585`; API 36 x86_64 `94596350522` | PASS |

Both KVM jobs ran the bounded nine-fixture Release/R8 full flow, exact lifecycle/event contracts, unsigned-input, multiple-current-signer and different-output-signer negatives, ephemeral signing cleanup and package cleanup. On x86_64 the ARM-only fixture was correctly not installed and reported its ABI limitation.

## Immutable KVM evidence

| Platform | Fixture report SHA-256 | Artifact ID | Artifact ZIP SHA-256 | Bytes |
|---|---|---:|---|---:|
| API 29 x86_64 | `3c47db443ae624bea7be9e9364695c59d88380274d6595a0edb392dd0feef080` | `9198688979` | `f28b471fc92d305e397c4c1806a799db9c3bb0a0b0518a0395eac8d5d3ca52dd` | `5206` |
| API 36 x86_64 | `28f6a0bc029bfa77da557bf92239b1e59c33df5e1ba8ea569a5a40be6099f23f` | `9198698091` | `a6f8027520671d3b9a9289737ddf63fbe58b8bda63dfb52a40c5a03c538792b0` | `6094` |

Each report has `status=pass`, `fixture_count=9`, `test_signing_cleanup=true`, `failure=null`; every installed row has exact expected/observed events, `same_current_signer=true`, `product_output_unsigned=true` and `package_cleanup=true`.

## Evidence boundary

These results freeze production/test implementation `c281a3a011229632cfe7a361d998eb8255b22b75`. A later evidence-only child may inherit them only when its diff changes documentation/HandOff/README and no fixture, driver, Host, Runtime, workflow or dependency input.

API 29 arm64-v8a physical-device acceptance was subsequently completed against this exact implementation freeze. Its ignored report SHA-256 is `37fafda7ebe08513dcd381c3658cfce2b50bef272ee1412d88ec480001683160`; all nine fixtures, including `jni-arm-only`, exact events, signer negatives and cleanup passed.
