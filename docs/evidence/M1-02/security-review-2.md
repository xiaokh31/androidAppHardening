# M1-02 independent security review 2

## Review target and result

- Reviewer: independent `m1_02_security_review_2`.
- Frozen target: `8718975255cfbdab4fc2ce29eae67c18f21b62ed` on `feat/m1-02-signer-policy`.
- Result: **FAIL**.
- Findings: P0 `0`, P1 `0`, P2 `1`.
- Review mode: read-only and offline; no tracked mutation, checkout, staging, commit, push, PR, device or emulator.

The target remains unpublished and is invalid as a passing M1-02 review target.

## P2: high-bit Signing Block sizes are misclassified as unsigned

The bounded envelope parser decoded the unsigned 64-bit APK Signing Block size into a signed `Long`. Values `0x8000000000000000` and `0xffffffffffffffff` became negative and entered the `declaredSize < 24` branch, which treated the block as absent. An unsigned APK with either footer therefore returned `SIGNER_UNSIGNED` rather than the stable malformed/truncated result `SIGNER_INVALID`.

Independent probes reported:

```text
footer_size=9223372036854775808 result=SIGNER_UNSIGNED
footer_size=18446744073709551615 result=SIGNER_UNSIGNED
footer_size=9223372036854775807 result=SIGNER_INVALID
```

This is not a signature bypass because every result rejects the APK, but it violates the task's stable malformed Signing Block error contract.

Required closure: classify negative decoded size values as `MALFORMED` before the small/absent check, and add `Long.MIN_VALUE`, `-1L` and other high-bit footer regressions that require `SIGNER_INVALID`.

## Confirmed review-1 closures

- Exactly-at-limit, above-limit and truncated 32 MiB block probes remained bounded and stable.
- Public exceptions had no cause; complete rendered stack traces contained no absolute path or raw apksig diagnostic.
- Zero-size magic-only padding returned `SIGNER_UNSIGNED`; ordinary malformed blocks returned `SIGNER_INVALID`.
- Six positive official digest checks, rotated current/old output, product old/current lineage, every negative official status and all 24 manifest hashes were verified.
- API 29, current signer, DER SHA-256, `SPV1`, TOCTOU binding and production no-signing boundaries had no new finding.

## Independent commands and evidence

| Command | Exit | Result |
|---|---:|---|
| clean signer matrix | 0 | 1 minute 43 seconds |
| root `clean check verifyGovernance` | 0 | 2 minutes 43 seconds; 256 tasks |
| Governance, strict HandOff, diff and UTF-8 scans | 0 | PASS |
| 43 ignored Signing Block boundary/mutation probes | 0 | no internal failure, uncaught exception or cause leakage; high-bit P2 reproduced |

Frozen report hashes were policy `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2`, error matrix `dce3c1a17647a96e93da291033e28c169ad0f5daee5d7544c6555392d66fc7eb`, official cross-check `c63d706f08763819e30c1e682fff87448a999a3ce53a27c7253e35ef9f82e2ba`, artifact manifest `fddc19d2a1ed3068c8ac5cdf8bc44299df0279a927a62da0af33be7cc1a0eab8`, and capability scan `97c89653b10a7e7b2fd97b53e7ae2ccc53994d623de2fc7c56852d982adbfcfa`.

Final reviewer state remained exact HEAD, correct branch and clean tracked worktree. Temporary ignored probes were deleted.
