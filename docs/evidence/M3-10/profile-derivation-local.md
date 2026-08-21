# M3-10 canonical profile derivation local evidence

- Timestamp: `2026-08-21T11:05:43+08:00`
- OS: Windows `10.0.19045` x64
- Branch: `feat/m3-10-startup-attribution-diagnostic`
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Implementation freeze: `2eb462b1d3e410d20e684ec70be84aab3cd09562`
- Toolchain: Node.js `24.12.0`; Temurin/OpenJDK `17.0.19`; Gradle `9.5.0`; Android Platform `36`; Build Tools `36.1.0`; apksigner `0.9`; dexlib2 `2.5.2` test-only with lock and dependency verification metadata.

## Scope and authorization boundary

This freeze only solves deterministic post-build profile derivation, independent test signing and byte-level security-semantic verification for the exact M3-11 canonical pair. It adds no canonical workflow and ran no API 36 diagnostic, KVM, emulator, physical device, ARM, benchmark or M3-05 command. The rejected rebuilt-original candidate is not an input.

Both canonical APKs were read directly from ignored `build/m3-11/provenance-artifact/`. They were never modified. All derived APKs and reports remain under ignored `build/m3-10/`; no APK is tracked.

## Actual-byte identities

| File role | Bytes | SHA-256 |
|---|---:|---|
| canonical baseline | 29962 | `4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf` |
| canonical protected | 1287876 | `1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9` |
| observer DEX | 4748 | `537b1ba424961d3897d574c10ec155e7b01cfffa313d71a0ade1d0c06e26dc88` |
| unsigned profile baseline | 23107 | `8db4df8b68e7905b7747ae1de5b8ce3b14b6d1d0a91e0ca103d0a1f8b2f5674c` |
| unsigned profile protected | 1252556 | `6a8958b3f2f2819f29039363447ddd6a487dfa1f1bd8b6f65458bf6c0e61a5c9` |
| signed profile baseline | 33971 | `ae0244f0a73bba737861a49d2a36239e299d8d028ded788e4d548fbabf6ae3f9` |
| signed profile protected | 1287848 | `fdec4d283dc09b916c95a3dd1828a2b1cbd3ba838dca230f8eea4913413ba202` |
| derivation manifest | 1161 | `2ab955c9603841e877fd406dea7c7cc197180558308852e92a9080f21351ecee` |
| verifier report | 709 | `0afc3caf8ca2fc23e6892a172387d234e6390cf6d9d21e81108258b942385aaa` |

The profile signer was newly generated for this local package. Both derivatives have one v3 signer and the same non-canonical signer certificate prefix `1445c5c32c5d`; originals retain their separate locked identity. No full profile signer digest is recorded.

## Determinism and semantic checks

Two independent derivations used the same canonical APKs, observer DEX, 32-byte seed and ephemeral certificate digest. Their unsigned baseline APK, unsigned protected APK and derivation manifest hashes matched exactly. Pinned `zipalign -P 16 4096` outputs and pinned v3-only `apksigner` outputs also matched byte-for-byte across both derivations.

`m310VerifyProfiles` independently verified:

- both original hashes and sizes before use;
- one v3 signer per APK, one common profile signer and a profile identity different from the canonical identity;
- exact entry topology and compression methods, byte-identical Manifests/resources and no baseline Factory;
- exact outer `p1..p15` and protected `h0..h8` call graphs, including observer presence only in the required profile DEXes;
- authenticated decryption of the canonical protected container to the canonical baseline DEX before transformation;
- transactional regeneration of ConfigV2, signer policy, AHDC AAD/key material and all four ABI shares;
- byte equality of every runtime library byte outside its single 104-byte `AHS1` share slot;
- authenticated verification and decryption of the new container to the exact reviewed protected payload DEX.

## Commands and exits

| Command class | Exit | Result |
|---|---:|---|
| compile observer with pinned `javac`, convert with pinned `d8 --min-api 29` | 0 | fixed observer DEX produced |
| `:host:container:m310CanonicalProfiles` for derivation A | 0 | three DEX transforms and authenticated profile package produced |
| same task for derivation B with identical inputs | 0 | byte-identical unsigned APKs and manifest |
| pinned `zipalign` plus v3-only `apksigner` for both A/B pairs | 0 | aligned/signed outputs byte-identical; all signatures verify |
| `:host:container:m310VerifyProfiles` on the four actual APKs | 0 | exact call graph, signer, Manifest/resource, container and four-ABI checks pass |
| `:host:container:test --offline` | 0 | 13 existing container/cleanup/tamper cases plus module tests pass |
| `node tools/governance/verify-m3-10-profile-freeze.mjs --self-test` | 0 | six production-surface mutations rejected; canonical workflows absent |
| `node tools/governance/validate-project-package.mjs` | 0 | 36 tasks, 11 core docs and 16 ADRs pass |
| strict HandOff validator and `git diff --check` | 0 | pass |

## Cleanup and remaining gate

After signing and verification, both temporary `build/m3-10/signing*` trees were resolved under the allowed build root and deleted. They contained the private key/keystore, runtime-only password, public certificate and container seed. No signing material is recoverable from tracked evidence. The derived APKs are test outputs and remain ignored.

This implementation freeze has not received the required independent read-only `P0=0/P1=0/P2=0` review. Therefore neither canonical workflow may be added or executed, and the unique API 36 diagnostic eligibility remains unconsumed.
