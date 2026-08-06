# M1-04 independent security review 1

- Review target: `a3c16619549abce6057e20b39ac83128f1ca401d`
- Implementation target: `0ec2b5e740542f85c71e137faae042e6cdcde7f8`
- Reviewer role: independent read-only security reviewer
- Result: `FAIL`
- Findings: `P0=0`, `P1=4`, `P2=2`

## Findings

1. **P1 - publication was not the final fallible operation.** The builder moved
   the candidate before validating target ABIs and constructing all return
   models, and silently fell back to a non-atomic move. Validate and construct
   everything before publishing; reject an unsupported atomic move.
2. **P1 - streaming limits were not immediate.** The first pass could read past
   the 512 MiB DEX limit and the second pass could exceed observed original or
   compressed lengths before detecting the changed input. The complete
   container-size check also occurred after payload writing.
3. **P1 - cleanup callbacks could interrupt cleanup and replace the primary
   error.** All sensitive arrays must still be cleared, cleanup failures must be
   aggregated or suppressed, and early/middle/late cleanup injection must prove
   precedence.
4. **P1 - required executable evidence was incomplete.** The frozen tests did
   not take 1/65535/65536/65537-byte DEX inputs through build and verify, did not
   take a 512 MiB DEX through the complete path, omitted I/O/cleanup and
   authenticated malformed-zlib cases, and did not emit a cross-language
   ConfigV2/R_native consumption vector protected by both-platform CI hashes.
5. **P2 - tamper and production-randomness assertions were incomplete.** Early
   tamper cases did not assert that no inflater callback occurred; production
   outputs were not verified and their full descriptor semantics were not
   compared.
6. **P2 - random collision handling was incomplete.** `R == R_java` could yield
   an all-zero `R_native`; production evidence did not independently compare
   CEK, R, R_java, wrapping nonce and record nonce prefixes, and SecureRandom
   initialization was outside stable error mapping.

## Reviewer verification

The reviewer ran the frozen `:host:container:test` and module `check` offline on
Windows with JDK 17 and Gradle 9.5; both exited `0`. The deterministic AHDC hash
was `3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d`.
Read-only proof recorded the same `a3c1661` HEAD and a clean worktree before and
after review.

## Required disposition

Keep remediation within M1-04, re-run the complete validation set, freeze a new
commit, and obtain a new independent read-only security review before any push
or PR.
