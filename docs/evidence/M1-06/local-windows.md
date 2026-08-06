# M1-06 Windows full-flow evidence

- task: `M1-06`
- branch: `feat/m1-06-cli-and-json-report`
- base: `55ef3c57e631cde65d3e04d58aa75d26a7e75ba8`
- timestamp: `2026-08-06T22:47:54+08:00`
- environment: Microsoft Windows 10.0.19045 x64; Eclipse Temurin `17.0.19+10`; Gradle `9.5.0`; Android Build Tools `36.1.0`
- tool storage: existing SDK at `C:\Environment\Android\SDK`; all Gradle caches and generated APK/report artifacts remain in repository-local ignored directories; no download, device, or emulator

## Commands

```powershell
$env:JAVA_HOME=(Resolve-Path '.toolchains\jdk\jdk-17.0.19+10').Path
$env:GRADLE_USER_HOME=(Resolve-Path '.toolchains\gradle-user-home').Path
.\gradlew.bat --no-daemon --offline :host:cli:cliTest :host:cli:integrationTest
```

Exit code `0`. `M1-06 CLI unit matrix PASS` and `M1-06 full-flow CLI matrix PASS` were emitted. The official pinned `aapt2` produced the valid Binary AXML integration fixture, the repository-generated test certificate produced the install-independent signed input, and official `apksigner verify` rejected the resulting protected output as unsigned.

```powershell
$env:JAVA_HOME=(Resolve-Path '.toolchains\jdk\jdk-17.0.19+10').Path
$env:GRADLE_USER_HOME=(Resolve-Path '.toolchains\gradle-user-home').Path
$env:ANDROID_HOME='C:\Environment\Android\SDK'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat --no-daemon --offline clean check verifyGovernance `
  -Paapt2Executable="$env:ANDROID_HOME\build-tools\36.1.0\aapt2.exe" `
  -Paapt2AndroidJar="$env:ANDROID_HOME\platforms\android-36\android.jar"
```

Exit code `0`; `BUILD SUCCESSFUL in 2m 5s`, 273 actionable tasks. This clean root run repeated all Host M1 checks, Android lint checks included by module `check`, toolchain policy, Governance, the M1-06 unit matrix, and the M1-06 full-flow integration matrix. No network, device, emulator, or unpinned download was used. The Kotlin daemon could not write its user-profile marker under the execution sandbox in earlier targeted runs, then Gradle's documented in-process fallback compiled and passed; the clean root run reused valid repository-local cached compiler outputs and passed without relaxing any check.

The full-flow fixture contains two canonical DEX entries, a custom Application, an original AppComponentFactory, and all four supported ABIs. The successful CLI invocation returned exit `0`, empty stdout, and `success/NONE/report result.json` on stderr. Input SHA-256 remained unchanged. The report passed the repository REPORT_V1 schema/contract validator and contained all seven canonical stages.

## Deterministic evidence

| Artifact | SHA-256 |
| --- | --- |
| normalized success report | `71052641e1e8933b6104087362180125f88b4ff8dfe4a34b7e4851b1e304c213` |
| error matrix | `63a9dcbb1974cdc824f605fef2119fa8402a8ace1c14c8475f385275dbd31428` |
| cleanup matrix | `e7d9e68694e5df60a15f62b62e080459a39355a7cab2daea5934029cfeeed558` |
| path error matrix | `ea48b25b0c8f561bb533f0df578a0755f27a6a0c257ffd600a976c0687b1e4a5` |
| REPORT_V1 JSON Schema | `5d6ab65ccce2d548af8013df487caed911e9c87080dc4449bd8552f2ada49486` |

The failure matrix covers SIGNER, AXML, CONTAINER, PACKAGE, VERIFY, PUBLISH, missing RuntimeBundle, interruption, shutdown-hook cleanup, short-write/disk-full equivalents, report publication failure, and report target races. Every normal failure report passes the checked-in Draft 2020-12 JSON Schema and semantic contract validator, every case preserves its input, and no success output or owned workspace remains. A pre-existing/raced report target is preserved rather than overwritten.

## Product boundary

The production CLI has no keystore, private-key, alias, password, signing-tool execution, network, or arbitrary temporary-path capability. Test-only signing is confined to the ignored integration fixture build and does not enter production classes or distributions. The synthetic RuntimeBundle is injected only by tests; absence of a later fixed distribution RuntimeBundle fails closed with `INTERNAL_RUNTIME_BUNDLE_UNAVAILABLE`/`70`.
