# M1-01 independent review 3

## Scope and conclusion

- Reviewer: independent read-only `m1_01_reliability_review_3`.
- Frozen commit: `0bbbeb6da8573ab770b0ca4ec1f6227e444244a1`.
- Branch: `feat/m1-01-untrusted-apk-inspector`.
- Conclusion: FAIL; P0 `0`, P1 `4`, P2 `0`.
- The reviewer made no file, Git, remote, device or emulator changes.

## Findings

1. P1: initial hash, parsing and final hash used separately opened handles, so a path could be exchanged and restored while the model described bytes different from its hash.
2. P1: the DEX offset fix allocated both `IntArray(stringCount)` and boxed `LinkedHashSet<Int>` up to 16,777,216 items, allowing approximately 64 MiB of table bytes to amplify toward a 1 GiB heap failure.
3. P1: native ABI recognition accepted only 20 bytes of ELF identification and `e_machine`; the canonical positive `.so` fixtures were themselves truncated 20-byte files.
4. P1: canonical DEX positives omitted `map_list` and left `map_off=0`; the parser did not close `data_size/data_off`, map entries and fixed tables.

## Independent verification

- Module test: exit `0`; 54 named fixtures, 10,000 samples, about 74 seconds.
- Root `check`: exit `0`; 231 actionable tasks, about 76 seconds.
- Governance, strict HandOff and `git diff --check`: exit `0`.
- HEAD and branch stayed frozen and clean; no network, download, device or emulator was used.

## Gate effect

Frozen commit `0bbbeb6da8573ab770b0ca4ec1f6227e444244a1` is invalid for completion or publication. All four P1 findings require remediation, new evidence and a new independent review.
