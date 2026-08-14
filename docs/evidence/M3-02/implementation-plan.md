# M3-02 implementation plan

- Task: `M3-02`
- Issue: `#19`
- Branch: `chore/m3-02-tamper-fuzz`
- Base: `ea30f51373003981cdcdae60dda795ba1fefd587`
- Validation mode: `full-flow`
- Scope: deterministic synthetic tamper catalog, JVM Jazzer targets, Native libFuzzer/ASan/UBSan target, bounded CI, and API 29/36 Runtime tamper evidence. No product signing capability, customer APK, local emulator, new production module, or adjacent M3 task.

## Fixed contracts

- Keep the public Gradle entry points in the existing `:tools:validation` module: `regressionFuzz`, `tamperTest`, `prFuzz`, and `nightlyFuzz`.
- Store only minimal synthetic seeds/regressions under `tools/validation/src/fuzz/resources/`; execute mutations against copies under ignored `build/fuzz-work/` and require the tracked source hash to remain unchanged.
- Use Jazzer/Jazzer API `0.29.1` from Maven Central with exact JAR/POM SHA-256 and Gradle dependency verification. Use Ubuntu Clang `18.1.3` libFuzzer + ASan + UBSan for the Native target.
- Enforce a 2 GiB RSS/heap boundary, 5-second per-input timeout, 4 MiB maximum input, 600 seconds per PR target, and 3600 seconds per nightly target.
- Run APK and AXML targets independently on reviewed Ubuntu and Windows images; run the Native target independently on reviewed Ubuntu. Unknown hosted-runner images fail closed.
- Extend the existing bounded API 29/36 KVM workflow with real nonce/tag/ciphertext startup mutations and map the existing JNI/Guard failure-injection evidence into the complete versioned catalog. No local emulator or physical device is required by this task card.

## Evidence and completion boundary

- Local development uses short deterministic regression/tamper gates and a one-second engine smoke only. The required 10-minute targets run once on the frozen PR head in parallel CI; nightly remains a scheduled ongoing control, not a reason to delay the PR indefinitely.
- Freeze a clean implementation commit, then obtain an independent read-only security review. Any production or security-contract change invalidates that review.
- M3-02 is complete only after exact-head Ubuntu/Windows fuzz, Native sanitizer, API 29/36 KVM, Governance/Build, evidence-only coordination, PR merge, README synchronization, and post-merge strict HandOff gates.
