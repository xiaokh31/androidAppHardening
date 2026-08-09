# M2-02 third independent read-only security review

## Verdict

- Frozen SHA: `73208102f13330bc062b6d64e1808254005feb3c`
- Implementation parent: `39a883f75cde6ef510f10e24eefe089fbd08b142`
- Main merge-base: `e78fcaed58dd5211a465ea37a94db45dddc17dfa`
- Result: **PASS**
- Findings: **P0=0, P1=0, P2=0**

The independent reviewer performed a read-only review and did not edit, stage, commit, push, download, start an emulator, or install an APK. The frozen commit changes only `docs/evidence/M2-02/local-validation.md` relative to the implementation parent.

## Scope

The review covered the complete merge-base-to-freeze diff, `AGENTS.md`, `docs/README_FIRST.md`, the M2-02 task contract, ADR 0003/0006/0008, the M1-07 container contract, the M2-07 Native cryptography supply-chain contract, the threat model and test strategy, Native C++, JNI, Java facade and ownership code, Host vectors, fixtures, workflows, acceptance scripts, raw device evidence, and both rejected M2-02 freezes with their remediations.

## Previous findings closed

1. **Non-empty connected tests:** Runtime Native and Bootstrap each contain a real Java instrumentation runner configured in their Android modules. API 29 and API 36 KVM logs show compilation and execution of both `connectedAndroidTest`/`connectedCheck` suites. The runners finish with Android `RESULT_OK` or `RESULT_CANCELED`.
2. **JNI cleanup primary/suppressed errors:** production open, install and explicit-close paths use the shared JNI exception helper. The fixture exercises real-handle rollback and explicit-close cleanup failures, verifies stable primary and suppressed codes, and proves the primitive handle is retired.
3. **Ten authenticated metadata getters:** the device runner compares all ten getters field by field. Build ID and key-slot ID come from independent Host-vector output and are passed as instrumentation arguments. Both final variants report `metadata_golden=true`, `metadata_negative=true` and `cross_handle=true`.
4. **Complete ZIP local-entry overlap:** fixed-asset overlap is checked over the complete local-entry range from local-header offset through data end. A deterministic regression places the second local header/name/extra inside the first asset while keeping data ranges disjoint and requires the stable format failure.
5. **Exact source APK limit:** the production maximum remains exactly `2,147,483,647` bytes and is covered by a constant test.
6. **zlib state/window zeroization:** the custom allocator performs checked size arithmetic, clears the allocation header and complete payload before free, and is exercised across normal, failed and multi-record inflater lifecycles. Host probes require all frees to be zeroized and live allocations to return to zero.

## CI and artifact evidence

- Build run `31304148760` is for implementation parent `39a883f75cde6ef510f10e24eefe089fbd08b142`; Windows job `93221663769` and Ubuntu job `93221663809` succeeded.
- Sanitizer artifact `9035423917` was `712` bytes and unexpired at review time.
- KVM run `31304148764` is for the same implementation parent; API 29 job `93221663854` and API 36 job `93221663823` succeeded.
- KVM artifacts `9035506907` and `9035563776` were unexpired at review time.
- The ignored `native-release.aar` was independently matched at `435539` bytes and SHA-256 `cd367353fc7615776ff8872e61c4775bb51043669f8280b1301fdc983a2622a1`; all four stripped Runtime SO hashes matched the frozen evidence.

## API 29 arm64 review4 evidence

- `report.json`: SHA-256 `c15151c51d7952e3ae347fee9bc63ff1fb85410ab250015b51f27b7a4c96a609`
- `commands.json`: SHA-256 `ff9008c804f5d304ed3974bd8dd03597ebc4040685c33e680aee18236d85d36a`
- extracted/direct instrumentation transcripts: SHA-256 `74acf92d908cc5743d0b164137f1a49a3aee22be7521ffd53bae3278299087ed`

The reviewer independently confirmed target/test APK sizes and hashes, exactly 20 cold starts and 20 meminfo samples per variant, instrumentation PASS, ten failure windows, cross-DEX, JNI, metadata golden/negative/cross-handle, cleanup, and zero plaintext DEX. The 227 recorded commands had no timeout; nonzero exits were only expected absence checks. The device was API 29 arm64-v8a, shell UID 2000, `ro.secure=1`, and `ro.debuggable=0`. Reports, APKs, vectors and the ephemeral test certificate remained covered by build-directory ignore rules.

## Repository checks

- `git diff --check e78fcaed58dd5211a465ea37a94db45dddc17dfa..73208102f13330bc062b6d64e1808254005feb3c`: exit `0`.
- The reviewed worktree was clean.
- No APK, DEX, keystore, private key, build output, customer artifact/path, production credential, plaintext customer DEX, recovery secret or new production dependency was tracked.
- No independently actionable JNI local-reference, pending-exception, OOM fallback, typed-handle generation, rollback/close retirement, allocator alignment/overflow, or lifetime finding remained.

## Residual risk

- Root, process injection, a modified ART/kernel, or complete process control can still observe runtime plaintext; the design increases extraction cost and does not provide absolute protection.
- The arm64 device evidence covers one Xiaomi API 29 OEM environment, not every vendor ROM.
- GitHub Actions artifacts expire; the frozen SHA, checked-in evidence and recorded hashes remain the durable traceability anchors.

## Final conclusion

Frozen SHA `73208102f13330bc062b6d64e1808254005feb3c` satisfies the independent-review gate with **P0=0, P1=0, P2=0**.
