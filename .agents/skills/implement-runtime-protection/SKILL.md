---
name: implement-runtime-protection
description: Implement or review one Android Java, JNI, or NDK runtime-protection task in this repository. Use for AppComponentFactory bootstrapping, in-memory DEX loading, native decrypt or inflate logic, signer and integrity checks, environment signals, memory-dump cost controls, and ARM or x86 runtime builds.
---

# Implement Runtime Protection

## Start

1. Read the mandatory documents, the assigned M0 or M2 task card, the frozen payload contract, and applicable ADRs.
2. Confirm the task owns the Java, JNI, C++, or build files being changed.
3. Use only project-generated fixtures and non-production test material.

## Runtime Rules

- Use the API 29 public `AppComponentFactory.instantiateClassLoader()` path; do not add hidden-API `LoadedApk` or `dexElements` mutation to v0.1.
- Keep the original `Application` declaration and delegate the original `AppComponentFactory` after the protected class loader is ready.
- Authenticate signer policy and protected configuration before releasing DEX plaintext to the class loader.
- Decrypt and inflate into direct anonymous memory without writing plaintext DEX to files, cache, code cache, or external storage.
- Clear temporary compressed plaintext and key material when safe. Do not clear buffers still required by ART.
- Treat signer mismatch, AEAD failure, and critical integrity failure as unconditional blocking failures.
- Keep environment policy separate: v0.1 maps `LOW` to `ALLOW` and both `MEDIUM` and `HIGH` to progressively stronger `DEGRADE` cost controls. Environment signals never block startup by themselves; signer, AEAD, and authenticated-integrity failures remain unconditional blocking failures outside the risk engine.
- Never classify x86 or x86_64 alone as emulator or risk evidence.
- Build the shell runtime for four ABIs, but inject only compatible ABIs when the input contains customer native libraries.
- Hide non-JNI symbols, strip release debug data, and retain symbols separately for authorized crash analysis.

## Claims

State explicitly that an offline client contains recoverable key material and that a root, Frida, modified ART, or kernel attacker may still capture runtime plaintext. Implement measurable cost controls without absolute claims.

## Verify and Hand Off

Run assigned unit, golden-vector, emulator, device, tamper, wrong-signer, JNI, and ABI checks. Return evidence in the worker handoff packet and never edit root `HandOff.md`.
