# M2-06 implementation plan

## Scope

Implement only Issue #17 memory-exposure cost controls. Do not change AHDC/ConfigV2 bytes, signer or integrity policy, component order, ABI policy, Host packaging or M2-07 supply-chain locks.

## Ownership and interfaces

- Native owns move-only sensitive buffers, anonymous DEX mapping seals, lock-budget accounting and current-process OS controls.
- `LoadedPayload` retains the only primitive handle; the new facade borrows it only while synchronized and open.
- `PayloadRuntime.applyMemoryProfile` is the sole profile call across JNI. Policy exposes an immutable summary and derives profiles only from the frozen M2-05 level/action pair.
- Secure key buffers make an invariant bounded lock attempt during decrypt; retained DEX edge locking is selected later by `ELEVATED`/`HIGH`, as fixed by ADR 0011.

## Bounded validation

- Run the Native Host unit target and policy JVM matrix once after implementation.
- Compile/lint/assemble the affected modules and run the existing four-ABI ELF/JNI verifier.
- Use the existing bounded GitHub API 29/36 KVM workflow for device-only `/proc/self/smaps`, direct/extracted Release/R8 and delay/dumpability evidence. Do not start a local emulator or replay completed historical matrices.

## Security claims

These controls increase the cost of ordinary memory capture. They do not prevent extraction by process-controlling, root or kernel attackers and do not permit any signer/container fallback.
