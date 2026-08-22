# M3-12 first independent read-only review

- Reviewed implementation freeze: `69b2fa75b0362992a3544188ae843106c66bb347`
- Reviewed evidence successor: `3c98db78f69f5e980ed1043681ef8d617e3c2b45`
- Result: `FAIL — P0=0/P1=2/P2=3`
- Scope: strict read-only static review; no modification, network, Gradle, device, KVM or benchmark

## Findings

1. `P1`: the machine lock did not bind the true M3-10 all-zero review record at `ac2d969392556fd9b338399e6cc2e9c22c90daed` or the canonical profile-lock bytes, so the “already reviewed” source chain was incomplete.
2. `P1`: sensitive scanning covered only outer bytes and a short fixed marker list; it did not parse the six APKs and inspect every entry name and decompressed byte for broader private-key, keystore, token and absolute-user-path material.
3. `P2`: actual archive self-tests covered only trailing, truncation and byte-flip cases, not duplicate/extra/missing/substituted/traversal members, method/flags/offset or local/central mismatches.
4. `P2`: the creator printed but did not enforce the final locked archive size/SHA-256, and evidence omitted its exact positive, two-run deterministic and expected-failure commands.
5. `P2`: creator, fetcher and verifier used lexical `build/m3-12` checks that did not reject intermediate symlink/junction escape.

## Required disposition

The reviewed freezes are rejected for publication. A bounded remediation must close all five findings, produce a new clean freeze and undergo a second independent strict read-only review. The unique API 36 workflow remains forbidden and uncreated.
