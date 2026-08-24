---
name: implement-apk-postprocessor
description: Implement or review one host-side APK post-processing task in this repository. Use for APK and ZIP inspection, signer extraction, binary AndroidManifest editing, DEX compression or encryption, runtime injection, signature removal, alignment, atomic output, CLI behavior, and machine-readable protection reports.
---

# Implement APK Postprocessor

## Start

1. Read `AGENTS.md` and `HandOff.md`; route solely by the active release line in `release_line`.
2. Load exactly one assigned task card. For v0.1, use one M1 card under `docs/tasks/`. For v0.2, use one `docs/v0.2/tasks/` V2 task whose `required_skills` explicitly contains `implement-apk-postprocessor`; do not require or invent an old M1 dependency.
3. Read the mandatory documents from that release line, confirm the branch matches the task, and inspect existing user changes before editing.
4. Use only synthetic or explicitly authorized fixtures. Keep generated APKs and reports in ignored build or artifact directories.

## Implement

- Treat every APK, ZIP entry, AXML chunk, DEX header, certificate, and length field as untrusted input.
- Reject path traversal, duplicate normalized paths, unsupported split metadata, malformed structures, resource exhaustion, and output/input path aliasing.
- Preserve the input byte-for-byte and publish the output by an atomic final move only after every verification passes.
- Remove only old signing material. Preserve unrelated `META-INF` entries and unchanged APK entries as defined by the assigned task.
- Compress each original DEX before authenticated encryption and never leave a successful-looking partial output.
- Keep signing outside the product. Do not add signing commands, keystore parameters, private-key APIs, or convenience signing helpers.
- Preserve the input package, version, target SDK, resources, customer native libraries, and declared components unless the task and an ADR explicitly authorize a narrow change.
- Report actual application ABI support; never infer x86 support merely because the shell can build for x86.

## Verify

Run every acceptance command from the task card. Include unit, malformed-input, tamper, compatibility, and failure-atomicity tests. Inspect the final diff for accidental signing code, dynamic dependency versions, real APKs, certificates, secrets, and unrelated changes.

## Hand Off

Return the task ID, status, behavior completed, files changed, commands with exit codes, environment, artifacts with hashes, decisions, risks, blockers, and exact next action. Do not modify root `HandOff.md`.
