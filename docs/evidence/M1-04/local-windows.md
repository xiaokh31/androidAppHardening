# M1-04 local Windows validation

- Timestamp: `2026-08-06T01:16:29+08:00`
- Branch: `feat/m1-04-encrypted-dex-container`
- Starting commit: `ff0d5ab6be6ef39f3ea06ddb06063ed3fcda3276`
- OS: Windows 10 `10.0` x64
- Java: Eclipse Temurin `17.0.19`
- Gradle: `9.5.0`
- JCA AES/GCM provider: `SunJCE`
- Compression: JDK `java.util.zip.Deflater`, zlib-wrapped level `9`, no dictionary
- Validation mode: `pre-cli`

## Commands and results

All Gradle commands used the repository-local JDK, Gradle installation, and
`GRADLE_USER_HOME` below `.toolchains`; no tool or dependency was downloaded to
the system drive.

| Command | Exit | Result |
| --- | ---: | --- |
| `gradle :host:container:compileKotlin --offline -Pkotlin.compiler.execution.strategy=in-process` | `0` | Kotlin main sources compiled with warnings as errors |
| `gradle :host:container:test --offline --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process` | `0` | 11 self-test groups passed |
| `gradle :host:container:check --offline --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process` | `0` | Module check passed |
| `node tools/governance/validate-project-package.mjs` | `0` | `OK: 27 task cards, 11 core docs, 8 ADRs` |
| `git diff --check` | `0` | no whitespace errors |

The repository-wide `gradle check --offline` was also attempted. It stopped in
project configuration before executing tests because the repository-local Android
SDK does not contain AGP's default NDK `28.2.13676358`. The project provenance
pins NDK `29.0.14206865`, but `fixtures:android` does not declare that version and
there is no NDK package in this local SDK. No unpinned download or unrelated fixture
change was made. This does not affect the passing `host:container` module check;
Ubuntu/Windows root checks remain a publication CI gate.

## Bounded-memory and cleanup evidence

The self-test processed a synthetic `536870912`-byte stream without allocating a
DEX-sized buffer. Instrumented arrays observed:

- largest single buffer: `65552` bytes (`65536` ciphertext bytes plus GCM tag);
- largest simultaneous tracked live buffers: `262431` bytes;
- contract limit: `1048576` bytes;
- clear hook failures: `0`;
- failed build outputs after input-change, random-source failure, and cancellation:
  absent.

The temporary work tree contains only authorized synthetic input APKs, encrypted
AHDC outputs, and JSON reports under ignored `host/container/build/`. The builder
never writes original DEX or compressed plaintext to a standalone file.

## Fixed and production-random outputs

| Artifact | SHA-256 |
| --- | --- |
| fixed-RNG AHDC v2 | `3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d` |
| production build A | `17c32a53426705e0b76f5f41a3791df3b9c3a20e4feb708c311348b0d5850652` |
| production build B | `3ca086e9ad7599d492f9f67d5e98b65fe7e701a1378e969a348a8f9de6380dc7` |

The two production hashes and all exposed packaging materials differed. Both
descriptors retained identical package, signer, DEX order, lengths, and digests.
The fixed-RNG hash is enforced in both Ubuntu and Windows jobs in
`.github/workflows/build.yml`.

## Input DEX binding

| Entry | Bytes | SHA-256 |
| --- | ---: | --- |
| `classes.dex` | `1024` | `e2b552e6e65ef03871d031dbf5000dded1151b89d600c0cc0839faa6ea943459` |
| `classes2.dex` | `190000` | `56c1da2ecd6a964411915937b70843977cd9bfb7f017b36fa3dfed0914e36eec` |

The independent verifier matched both records' size, SHA-256, and canonical order
after per-chunk authentication and continuous zlib inflation.

## Standard vectors

- RFC 5869 SHA-256 test case 1, 42-byte output.
- NIST SP 800-38D AES-256-GCM zero-key/zero-IV, 16-byte plaintext vector.
- zlib-wrapped level-9 `hello` vector:
  `78dacb48cdc9c90700062c0215`.
- HKDF domain separation between manifest, record zero, and record one keys.
- nonce separation between adjacent chunk ordinals.
