# M1-03 independent parser/security review 1

## Verdict

- Frozen commit: `9fee22df524f0465f5a9fc310bec153b6d37696b`.
- Branch: `feat/m1-03-binary-axml-transformer`.
- Result: **FAIL**.
- Findings: P0 `0`, P1 `3`, P2 `1`.
- Mode: independent, read-only and offline. The reviewer did not modify files, use a device or emulator, publish the branch or create a PR.

The frozen commit is invalid as completion evidence. All findings must be fixed, the implementation and evidence must be frozen again, and a new independent review must return zero P0/P1/P2.

## Findings

### P1-1: existing attribute extension bytes were zeroed

The parser accepted `attributeSize > 20`, but replacement of an existing `appComponentFactory` cleared every byte after the standard 20-byte attribute fields. A vendor or future extension could therefore be silently changed while the semantic diff still reported only the Factory value. The fix must preserve the extension for an existing attribute and test a non-zero 24-byte-or-larger attribute record byte-for-byte.

### P1-2: style and namespace validation allowed superlinear CPU work

Every style offset independently traversed its entire span chain, including duplicate or overlapping offsets. A compact style table could therefore request extremely large repeated work. Namespace validation also scanned the complete active stack for every namespaced reference without an independent namespace bound. The fix must impose input-proportional global style work, cache repeated style offsets, cap active namespaces and use constant-time active-URI lookup.

### P1-3: tiny unknown chunks allowed large heap amplification

Each minimum-size unknown chunk produced a raw-array copy, chunk object, SHA-256 string, semantic event and unknown record, and the limit allowed one million chunks in both before and after documents. A sub-8-MiB input could therefore expand to hundreds of MiB or more and escape through `OutOfMemoryError`. The fix must set a realistic pre-allocation chunk budget and aggregate unknown-chunk preservation evidence rather than retaining per-chunk hash/event objects.

### P2-1: high-bit and explicit preservation evidence was incomplete

The positive resource-reference fixture only covered low unsigned values. The frozen reports also lacked explicit old string-index, resource-map-prefix and unknown-byte/order preservation summaries. The fix must cover resource IDs and typed values in `0x80000000..0xffffffff` and emit deterministic preservation hashes/counts in the canonical report.

## Independent commands and evidence

- `:host:axml:test`: exit `0`; five positives, thirteen negatives and 5,000 seeded malformed samples.
- `check verifyGovernance`: exit `0`; 237 actionable tasks.
- strict HandOff, both Node syntax checks and `git diff --check base..HEAD`: exit `0`.
- Environment: Windows `10.0.19045` x64, Temurin `17.0.19+10`, Gradle `9.5.0`, Node `24.12.0`, aapt2 `2.20-14042983`.
- Frozen report hashes:
  - transform: `01757e930cd3bb0a60a9b7b2445db4fa0000f050c65f9e88dba82e1d4b3c5d63`
  - errors: `54a785f3a4ccd698452fccedaec8e98d509907efcbf67578dd16d6cedcf4956b`
  - fuzz: `7910c602477e7ff1b560075468468dd4ccfd9985468655ddca3a2174fdbca199`
  - aapt2: `e9e3b0307b6953e5b8cc98b651925f6c6cfc5a15b081115138e225efcf69f38e`

The reviewer separately confirmed that test-only ZIP/signing code remained outside production, KVM timeout/cleanup behavior was retained, the rejected API 29 install was not misreported as PASS, and API 36/Ubuntu validation remained honestly pending publication.
