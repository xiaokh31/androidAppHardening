# M1-02 third independent security review

## Conclusion

- Frozen target: `902c20977d787ea9646078bbbe4c3c46bf0041cc`.
- Branch: `feat/m1-02-signer-policy`.
- Result: **PASS**.
- Findings: P0 `0`, P1 `0`, P2 `0`.
- Mode: independent read-only and offline. The reviewer did not check out, modify tracked files, stage, commit, push, create a PR, or start a device or emulator.

Both earlier review rounds' findings are closed. No new P0, P1 or P2 finding was identified.

## Independent checks

- The clean `:host:apk-inspector:signerPolicyTest` task exited `0` in 103.9 seconds.
- The root `clean check verifyGovernance` task exited `0` in 163.6 seconds with 256 actionable tasks. The M1-01 10,000-sample regression and the M1-02 matrix both passed.
- Governance validation, strict HandOff validation, `git diff --check` and the UTF-8 replacement-character scan all exited `0`.
- An ignored boundary probe ran with `-Xmx256m`, exited `0`, and was removed by the subsequent clean build.
- The tracked worktree was clean before and after review, and HEAD remained the exact frozen target.

Review environment: Windows 10 amd64, Eclipse Temurin `17.0.19+10`, Gradle `9.5.0`, Kotlin plugin `2.4.10`, apksig `9.3.0`, apksigner `0.9`, and Node.js `24.12.0`. The pinned apksig JAR SHA-256 was `562cd0a88890960d2ece48e116c61f12872222f1dcc306890799382bc019b201`.

## Boundary and failure semantics

- Signing Block sizes `Long.MIN_VALUE`, `Long.MIN_VALUE + 1`, `-2`, `-1` and `Long.MAX_VALUE` all returned `SIGNER_INVALID`.
- The exact 32 MiB boundary, over-limit and truncated declarations, and the minimum complete Signing Block all failed within the resource bound. No checked exception escaped and every public exception had `cause=null`.
- Magic-only inputs with size `0` or `23` remained `SIGNER_UNSIGNED`; complete or truncated malformed envelopes returned `SIGNER_INVALID`.
- A rendered public stack trace did not expose an absolute path, a lower-level exception, or raw apksig diagnostics.
- The 13-row error matrix matched the official verification state of each underlying APK.

## Signer identity and product boundary

All six positive APKs passed the pinned `apksigner verify --min-sdk-version 29 --print-certs` cross-check. The official and product DER SHA-256 values agreed:

```text
current: d183c6e5aa4fc22150451b37879c6bb8aa2fdc392b1dcf2fd45414fad9908a16
old:     ba2af0c4efd0cf314c1db1ed5fbb28f283ef194627f53931c3fcfc293c1f4645
```

The rotation policy is oldest-to-newest and ends in the current signer. API 29 minimum checking, exactly one current signer, `SPV1` model bounds, same-handle TOCTOU checks and the final input hash binding all passed. The 26-entry artifact manifest was complete and every SHA-256 matched.

Production sources, public API and bytecode contained no private key, keystore, password, signing executor, `ProcessBuilder` or `ApkSigner` entry point. The v4 `.idsig` remained outside standalone APK policy.

## Frozen report hashes

| Artifact | SHA-256 |
|---|---|
| `canonical-policy.json` | `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2` |
| `error-matrix.json` | `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6` |
| `official-cross-check.json` | `c63d706f08763819e30c1e682fff87448a999a3ce53a27c7253e35ef9f82e2ba` |
| `artifact-manifest.json` | `d74287aec49cfd3cb18af55c6119b3ea90689d2f03bc15df8e5e8d04f43eb201` |
| `capability-scan.txt` | `97c89653b10a7e7b2fd97b53e7ae2ccc53994d623de2fc7c56852d982adbfcfa` |

Ubuntu byte equivalence remains a publication-time GitHub CI requirement and is not claimed by this local review.
