# M2-07 independent read-only security review 9

- Frozen commit: `7190a6ee61285fe065d5de6cbe836a648482d658`
- Review timestamp: `2026-08-07T13:25:15+08:00`
- Result: **PASS**
- Severity totals: `P0=0`, `P1=0`, `P2=0`
- New findings: none
- Mutation: none; the reviewer changed no files or Git state and performed no build, download, device or emulator operation

## Prior findings

All twenty findings from reviews 1 through 8 were independently verified **CLOSED**. This includes archive/source binding, AES/HKDF failure matrices, PSA transaction concurrency, Release ELF selection, immutable machine locks, Windows compiler/tool versions, security-advisory reachability, exact symlink handling, pinned Ubuntu tooling, README and HandOff truthfulness, the complete seventeen-entry four-ABI type-and-name surface, zero related dynamic exports, uppercase/global symbol collection and the explicit `d→t` parser mutation.

## Independent evidence

- Mbed TLS `4.1.1` archive: `7099934` bytes, SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`, tag object `783058d12831aedd3ef57a64577f6f8a88d23bd3`, commit `0a8fda272a5a0abef3b47c91bed37185d5a726b1`; bundled TF-PSA-Crypto is `1.1.1` and the unsigned tag state is recorded rather than overstated.
- Apache-2.0 selection, both locked license hashes, NOTICE/source obligations and the CVE reachability decisions remain consistent with the built surface: TLS 1.3, RSA and padded CBC/ECB decryption are not built or exposed by the minimal facade.
- Build run `31149909030` resolved to the frozen SHA and passed on Ubuntu/Windows, including NIST AES-256-GCM, RFC 5869, failure/concurrency matrices and all four ABIs with `expected=17` related local symbols and `expected=0` related dynamic exports.
- Governance run `31149909021` resolved to the frozen SHA and passed on Ubuntu/Windows.
- KVM run `31149909014` resolved to the frozen SHA and passed on API 29 and API 36 x86_64. Both jobs passed locked dependency preparation, two Release/R8 build passes, bounded device acceptance, cleanup diagnostics and evidence upload; the logs reported `cleanup_passed=true`.
- Read-only Node syntax, shared parser self-test, dependency verifier self-test, governance, strict HandOff, sensitive-information scan and committed-large-dependency scan all passed. The shared parser self-test reported `PASS expected=17` and explicitly rejected both `t→d` and `d→t` mutations.

## Conclusion

The frozen commit satisfies the M2-07 Native cryptography backend, immutable supply-chain, license/advisory, NIST/RFC vector, four-ABI and independent-review gates. It may proceed to an evidence-only archive successor, final exact-head checks and expected-head merge. Optional wording or additional defense-in-depth tests must not reopen this completed review unless they expose a real acceptance-surface or security-boundary defect.
