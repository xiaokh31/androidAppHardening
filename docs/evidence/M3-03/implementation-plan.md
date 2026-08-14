# M3-03 implementation plan

- Task: `M3-03`
- Issue: `#20`
- Branch: `chore/m3-03-windows-ubuntu-equivalence`
- Base: `68c4fd25c86cae61dc00039af118b4e35b566741`
- Validation mode: `full-flow`
- Scope: one fixed synthetic signed-input corpus, one Release Candidate Runtime bundle, two randomized Host protection runs per platform, an independent semantic comparator, and a symmetric Windows/Ubuntu CI workflow. No product signing capability, customer APK, device, emulator, Runtime behavior change, or adjacent M3 task.

## Fixed contracts

- Preserve the public task `:integration-tests:crossPlatformCorpus` and comparator entry `tools/compare-platform-results` from the task card.
- Generate the nine signed synthetic inputs once in the seed job, delete the ephemeral integration-test keystore before artifact upload, and feed byte-identical inputs plus one byte-identical Runtime bundle to both platform jobs.
- Run every fixture twice per platform. Require distinct output/container hashes, build IDs, key-slot IDs, nonce prefixes, MACs, tags, and ciphertext while comparing deterministic ZIP, Runtime, Manifest, authenticated-container, decrypted-DEX, and report semantics.
- Parse ZIP/AHDC independently of the product implementation. Authenticate the manifest, recover the test-only CEK in memory, authenticate/decrypt every chunk, inflate each record, and verify DEX order, length, topology, and SHA-256 without persisting CEK or plaintext DEX.
- Reject unknown report fields and unclassified drift. Normalize only the reviewed randomized Native share slot; do not normalize whole artifacts or delete whole report objects.
- Lock Java `17.0.19`, Gradle `9.5.0`, Android build-tools `36.1.0`, `UTC`, `Locale.ROOT`, and UTF-8. Exercise a deep non-ASCII work path and scan published reports for absolute runner paths.
- Keep the two negative inputs fail-closed on both platforms, require identical stable failure semantics, no partial output, immutable inputs, and deleted test-signing material.

## Evidence and completion boundary

- Local validation is bounded to compilation, comparator self-tests, governance/HandOff checks, and one Windows development full-flow run. The exact implementation head runs the full Windows/Ubuntu matrix once in CI.
- M3-03 is complete only after the unique Issue #20 PR passes the exact-head equivalence workflow plus Build/Governance, the summary and platform artifact hashes are recorded, README/HandOff are synchronized, and the PR is merged with expected-head protection.
- M3-04 and M3-05 remain untouched until their predecessor is completely closed.
