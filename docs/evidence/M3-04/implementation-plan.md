# M3-04 implementation plan

- Task: `M3-04`
- Issue: `#21`
- Branch: `chore/m3-04-api-abi-matrix`
- Base: `a65433ae0bda651fc1088d187913b2dbfa7b02d1`
- Validation mode: `full-flow`
- Contract dependency: M3-06/ADR 0012 is merged and complete.
- Adjacent-task boundary: M3-05 is not started.

## Complete inventory contract

The generator enumerates every API `29..36` crossed with `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`: exactly 32 unique cells. Each cell is exactly one of `VERIFIED`, `FAILED`, or `UNVERIFIED`.

- `VERIFIED` requires Android-reported API/process ABI facts, mandatory positive and payload-before-load negative results, artifact hashes, retry count, and cleanup proof.
- `FAILED` retains the first failure and at most one retry and blocks M3-04.
- `UNVERIFIED` requires a stable reason, `deviceFacts: null`, no positive fixture result, and a human-readable “not validated/no compatibility claim” rendering.
- Missing, duplicate, unknown, contradictory, inferred, or overclaimed cells fail schema/summary validation.

## Mandatory available baseline

- API 29 `armeabi-v7a` on the authorized non-root physical `user` device.
- API 29 `arm64-v8a` on the same physical device.
- API 29 `x86_64` on pinned system image revision 8.
- API 36 `x86_64` on pinned system image revision 2.

The repository already fixes the Linux emulator and image provenance. No API 30-35 image, new tool, or large local download is authorized. Every other cell remains explicit `UNVERIFIED` unless an already authorized real environment is discovered before freeze.

## Bounded validation layers

1. Implement deterministic inventory generation, JSON schema, semantic validator, Markdown renderer, and mutation self-tests.
2. Reuse the existing M3-01 Host full-flow and M3-02/M2 device runners; add only M3-04 orchestration and exact result extraction, not another Runtime or fixture implementation.
3. For every mandatory cell, run single/multidex; run custom Factory on x86_64, applicable four-ABI JNI, signer mismatch, authenticated tag tamper, payload/session-before-load assertions, ARM-only classification, x86 zero-risk, and cleanup.
4. Preserve the first failure, allow at most one retry, and never retry transport/device authorization failures into a false product pass.
5. Generate one `compatibility-matrix.json` and `docs/generated/COMPATIBILITY_RESULTS.md` from the same normalized model; verify artifact hashes and scan retained evidence for device identifiers, paths, key material, and plaintext DEX magic.

## Efficiency boundary

- Do not rerun unchanged fuzz, Host equivalence, benchmarks, or historical M0/M1/M2 matrices.
- Inherit an existing artifact only when the production Runtime, fixture, acceptance script, and protected APK are byte-identical and the commit/diff boundary is recorded; otherwise execute the mandatory cell once.
- API 29/36 x86_64 runs only in bounded GitHub KVM with overall timeout and unconditional cleanup. No local emulator is started.
- Physical-device work is one bounded two-ABI campaign with package/file cleanup and no repeated install loop.

## Completion boundary

- All 32 cells are present; the four mandatory cells are `VERIFIED`; no cell is `FAILED`; unavailable cells are `UNVERIFIED` and never rendered as supported.
- Local static/generator checks, physical ARM campaign, exact-head API 29/36 KVM, Ubuntu/Windows Build/Governance, artifact/hash verification, README, HandOff, and expected-head merge are complete.
- Only after M3-04 post-merge main gates pass may M3-05 start.
