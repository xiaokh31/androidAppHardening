# M3-14 CI fix 1 review remediation

- Rejected CI-fix freeze: `0a2e19a1e1c9201b968a0d7a3c7b674339947b6d`
- Remediation implementation: `62fc6007ceefb088fe75b831ff872cc42beb79e7`
- Independent result: `FAIL — P0=0/P1=1/P2=0`
- Timestamp: `2026-08-23T09:19:16+08:00`

The first exception removed exact mappings before the ordinary HIGH scan, but malformed non-profile mappings could remain without matching that generic regex. In particular, swapping `M310_ORIGINAL_BASELINE` with `M310_ORIGINAL_PROTECTED` or appending trailing text was incorrectly accepted.

The remediation makes the M310 artifact-binding grammar independently fail closed on the exact build file. Any line naming one of the eight fields or any `M310_*` environment variable must be a complete exact field-to-variable mapping; otherwise it emits an explicit scan error before the generic HIGH scan. Self-tests now cover all eight mappings under LF and CRLF, plus isolated wrong-field, swapped variable, unrelated variable, trailing text, HIGH override, risk and product-profile cases.

Local commands all exited `0`:

- `node --check tools/governance/verify-m3-07-high-benchmark-contract.mjs`
- `node tools/governance/verify-m3-07-high-benchmark-contract.mjs`
- `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test`
- `node tools/governance/validate-project-package.mjs`
- `git diff --check`

Remediated validator SHA-256: `d20da4a882b57751766604eb7eb5fab1633fca5ac7c335a4146fdc30a24f3cf7`.

Both canonical successor workflows remain absent. No API 36 diagnostic, device, emulator or benchmark ran, and `runAttempt=1` remains unconsumed. The remediation must be frozen and independently reviewed before it may replace PR #83 head.
