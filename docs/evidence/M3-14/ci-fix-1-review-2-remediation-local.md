# M3-14 CI fix 1 second review remediation

- Rejected freeze: `feb794b450825dddb1c295add8a8448217e74fa3`
- Independent result: `FAIL — P0=0/P1=1/P2=0`
- Timestamp: `2026-08-23T09:25:24+08:00`

The explicit environment-binding parser still admitted Kotlin whitespace/multiline variants and did not prove exact task context/cardinality. Rather than expanding a custom Kotlin parser, this remediation removes the exception and the environment binding surface entirely.

- `host:container:m310VerifyProfiles` now accepts eight explicit `m314*` Gradle project properties.
- `run-m3-14-startup-attribution.mjs` passes exact paths with `-P` arguments and no longer constructs or exports any M310 environment variable.
- `verify-m3-07-high-benchmark-contract.mjs` is byte-identical to the already reviewed workflow-absent head `2ba90618734bf4f509eb6052a3ce649e50aca71a`; there is no M3-14 allowlist or parser.
- No product task reads the `m314*` properties; they are consumed only by the test-source JavaExec verifier.

Local validation:

- M3-07 positive and 10 surface plus 20 report-negative self-test: exit `0`.
- M3-14 profile-freeze 14 mutations and cleanup 8 injections: exit `0`.
- Project Governance and diff check: exit `0`.
- Repository-local Gradle `:host:container:m310VerifyProfiles --offline --no-daemon` with all eight `-Pm314...` arguments: exit `0`; exact retained four-APK report SHA-256 `1610f895cb1a3003387a2c7f2e2e1474d6fbbfc523da8fc11c88d6cd283c5b93`.

SHA-256 values:

- reviewed M3-07 validator: `7bbd807ac243164f778a638345d751547cf6fd5338c60c88cc83345a0124234d`
- test-only Gradle boundary: `f13e7e2d67a614e38fba1215483e631c432577592e982b4b5c0eb19466a80057`
- successor runner: `b379c17bf98c13dd6bf4217677e1615a59777b4d005682f06f6cae4a5cec7804`

Both canonical workflows remain absent. No network, API 36 diagnostic, device, emulator or benchmark ran, and `runAttempt=1` remains unconsumed. A new frozen independent all-zero review is mandatory before PR #83 replacement push.
