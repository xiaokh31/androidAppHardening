# M1-05 local Windows validation

- Timestamp: `2026-08-06T09:46:44+08:00`
- Branch: `feat/m1-05-apk-repacker-and-alignment`
- Base commit: `d32abe1d68d41910d72c90c3f9fc3d2831972756`
- OS: Windows 10 `10.0.19045` x64
- Java: Eclipse Temurin `17.0.19`
- Gradle: `9.5.0`
- Kotlin/JVM: `2.4.10`, target `17`, warnings as errors
- Android Build Tools: `36.1.0`; AAPT2 `2.20-14042983`; apksigner `0.9`
- Validation mode: `pre-cli`; no device or emulator

All Gradle work used the repository-local JDK, Gradle installation, and
`GRADLE_USER_HOME` under `.toolchains`. No dependency or tool was downloaded.

## Commands and results

| Command | Exit | Result |
| --- | ---: | --- |
| `gradle :host:repacker:clean :host:repacker:test --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process -Paapt2Executable=<Build Tools 36.1.0>/aapt2.exe -Paapt2AndroidJar=<Platform 36>/android.jar` | `0` | Four ABI policies, preservation/alignment, external Android tools, and eleven failure cases passed from a clean module output |
| `gradle check verifyGovernance --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process -Paapt2Executable=... -Paapt2AndroidJar=...` | `0` | `245` tasks; all M1-01 through M1-05 host tests, Android checks, toolchain policy, and governance passed in `1m29s` |
| `node tools/governance/validate-project-package.mjs` | `0` | `OK: 27 task cards, 11 core docs, 8 ADRs` |
| `git diff --check` | `0` | no whitespace errors |
| `aapt2 dump xmltree output-unsigned.apk --file AndroidManifest.xml` | `0` | binary Manifest parsed and shell factory was present |
| `zipalign -c -P 16 -v 4 output-unsigned.apk` | `0` | all stored entries and SOs satisfied the pinned alignment rules |
| `apksigner verify --min-sdk-version 29 output-unsigned.apk` | `1` | expected unsigned failure; internal report states `signingPerformed=false` |

## Positive and compatibility matrix

The synthetic fixture contains two root DEX entries, stored and deflated
resources/assets, JAR signing entries, a non-signing `META-INF` entry, and optional
customer native libraries. Every run rebuilds and independently authenticates a
fresh AHDC v2 container before packaging.

| Fixture | Input native ABI set | Injected Runtime ABI set |
| --- | --- | --- |
| Java-only | none | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |
| ARM-only | `armeabi-v7a`, `arm64-v8a` | `armeabi-v7a`, `arm64-v8a` |
| x86-only | `x86`, `x86_64` | `x86`, `x86_64` |
| mixed | `arm64-v8a`, `x86_64` | `arm64-v8a`, `x86_64` |

For each fixture the verifier found exactly one bootstrap `classes.dex`, no
`classes2.dex`, no JAR signature entry, no plaintext DEX magic in another entry,
one canonical AHDC/config path, and only the policy-selected Runtime libraries.
The original stored and deflated preserved entries retained method, CRC,
compressed bytes, and uncompressed SHA-256. Customer SO bytes were unchanged.

Each selected Runtime template was checked for its pinned SHA-256, ELF class and
machine, unique read-only 104-byte `.ah_share_v1` placeholder, placeholder ABI,
and unchanged bytes outside the slot. Independent output verification rechecked
the `AHS1` magic/version/ABI, build ID, key-slot ID, `R_native`, and slot digest
against the exact ConfigV2 material.

## Failure and cleanup matrix

| Case | Stable result |
| --- | --- |
| normalized same path, hardlink alias, symlink alias | `OUTPUT_PATH_ALIAS` |
| pre-existing distinct output | `OUTPUT_ALREADY_EXISTS` and original target bytes retained |
| short write, synthetic disk full, close failure | `PACKAGE_WRITE_FAILED` |
| tampered closed candidate | `OUTPUT_VERIFICATION_FAILED` |
| input changed before publish | `OUTPUT_INPUT_CHANGED` |
| unsupported atomic move | `OUTPUT_ATOMIC_MOVE_UNSUPPORTED` |
| unsupported native ABI | `COMPAT_ABI_UNSUPPORTED` |

Every failure retained the input SHA-256, left no `.ah-repack-*.part`, and did not
publish a success output. Windows hardlink coverage ran directly. Symlink creation
is conditional on host privilege; Linux CI always exercises the real symlink
branch while the normalized-path and hardlink algorithms cover Windows without
Developer Mode.

## Artifact hashes

The following hashes belong only to the ignored, synthetic local run and are not
checked into Git. Random AHDC key material deliberately changes the container and
final APK hashes on each run.

| Artifact | SHA-256 |
| --- | --- |
| synthetic input APK | `d2f5b2f1a8062f06b1ca4b2e78cacdfd029d90f4151895459eba150e7f92a0b3` |
| transformed Manifest | `e6613214a7437b5edf59198896e5aeedef51938dbf78fcb1673f81591d06eb4d` |
| AHDC v2 container | `2368f074ea3e2e46eed5a1ccb36f108df70e166054a47d97e571f7924c314781` |
| ConfigV2 | `81be7acb62bf3f51c5e0bf1a2180e4b63f519c2cf48eb3a65dbbef27772e5a01` |
| candidate/final unsigned APK | `573ddd2ad869284427cfbe0c93af2fd226debc462a082863dec40a54b6c1dcb6` |
| Runtime `armeabi-v7a` template | `810c8aa6c928a6e789dd0d9c669b819de206064daa090c66e5b6074e3f5e3e10` |
| Runtime `arm64-v8a` template | `95ae3c5b119329bf0a2c43c1232ab8c6ab184aa68d0b07fffb310a9a7c637be3` |
| Runtime `x86` template | `84b1b0569724c97bf388f25216f8ba2d1f4eda2c8a1215e4248befab44572d73` |
| Runtime `x86_64` template | `8f4167428f3be86fc08703918b0dff82334a98d1b7047684cade361ce0ca109b` |

The cross-platform byte gates deliberately cover only normalized, deterministic
reports:

| Report | SHA-256 |
| --- | --- |
| entry manifest | `606eb0d4a28d2235d054273b749740eaaff2fdf0891d84e582291ff327e2d687` |
| error matrix | `3c5b1283874f101b9007d67c2bdceff002afff0505292f91398401ab4cb2f941` |
| alignment report | `c173698b586d53b61874611cc36930725dd8ce7ea2f737c51b02d5afa7e427bd` |
| ABI matrix | `add443496d258e389917d7fabaf1ea7d59b120d7d57b088969bb89976da3f5b8` |
| external tools | `9723e87adedf97b176ea186baf0309159981e0154fedd25f46841d53f0bde29b` |

