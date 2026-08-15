# M3-07 independent read-only security review 1

- Frozen commit: `ab166d0ad396c11595ad3d7a5d6bef5b14f44635`
- Base: `3584b379f6abd1ba85726831aa1f68a2fac4183b`
- Reviewer: independent read-only `m3_07_security_review`
- Result: **FAIL**
- Findings: `P0=0`, `P1=2`, `P2=0`
- Reviewer modifications: none
- Device/Gradle/network execution: none

## P1 findings

1. The production override scan covered only `EnvironmentRiskEngine.java` and `RuntimeStartupGuard.java`. It did not enumerate other Runtime main sources, Host/CLI, production fixture, Manifest/resource, Gradle/ProGuard or distribution surfaces, and it omitted environment-variable controls. The isolated-string self-tests did not prove real path enumeration or base-to-HEAD production-diff exclusion.
2. The result-shape function ran only inside self-test and was not available as a formal report-validation entry. It accepted missing or ill-typed values, three-sample examples, inconsistent LOW/action pairs, absent `claimType`, and cold-start aliases outside one exact rejected string.

## Required remediation

- Enumerate all product main/Release surfaces, cover manifest/property/environment/intent/file/preferences/BuildConfig/setter controls, and add an M3-07 base-to-HEAD zero-production-diff gate.
- Add the formal `--report <benchmark-results.json>` entry, strict finite numeric and explicit-null checks, exact Host 10/Android 30 sample counts, fixed mode/metric/claim enums, LOW/ALLOW consistency, and isolated ownership/jitter/cleanup checks.
- Drive mutations through representative temporary production layouts and serialized reports using the same production scanner/report CLI path.

This review is retained as a rejected freeze. It cannot be inherited as a PASS by a successor commit.

