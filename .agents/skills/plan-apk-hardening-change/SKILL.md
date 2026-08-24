---
name: plan-apk-hardening-change
description: Plan a bounded change to this APK-only post-processing hardener. Use before adding or changing features, APK layout, manifest rewriting, DEX payloads, signer policy, runtime defenses, ABI behavior, toolchains, public CLI contracts, compatibility claims, or cross-module architecture.
---

# Plan APK Hardening Change

## Workflow

1. Read `AGENTS.md` and `HandOff.md`, then use the frontmatter `release_line` as the only release-line router. For v0.1 read `docs/README_FIRST.md` and `docs/tasks/<task>.md`; for v0.2 read `docs/v0.2/README_FIRST.md` and `docs/v0.2/tasks/<task>.md`. Reject an unknown or missing release line.
2. Read the relevant product, architecture, threat-model, test, evidence-policy, and ADR documents from that active release line. For v0.2 also read `docs/v0.2/identity-path-policy-v1.json` and `docs/v0.2/post-freeze-path-policy-v1.json`; task cards and prose may explain these policies but may not redefine them. Do not let a v0.1 task state satisfy a V2 dependency.
3. Inspect the current branch, worktree, implementation, tests, and dependent task cards. Treat repository evidence as authoritative.
4. Keep the change inside one task. If the requested behavior crosses task boundaries, record the dependency and return it to the coordinator instead of silently expanding scope.
5. Define the input, output, public interface, compatibility impact, security claim, failure behavior, and observable acceptance criteria.
6. Identify positive, tamper, compatibility, and failure-path tests before implementation begins.
7. Require an ADR for a cross-module, security-sensitive, wire-format, signer, ABI, compatibility, or hard-to-reverse decision.
8. Return a structured worker handoff packet. Never edit the root `HandOff.md`.

## Invariants

- Process only standalone APK files in v0.1 and v0.2.
- Keep the input APK read-only and require a distinct, initially absent output path.
- Produce only an unsigned APK. Never request or use a keystore, private key, alias, or password.
- Require `minSdk >= 29`; do not silently raise the input minimum SDK.
- Describe anti-dump, anti-debug, environment detection, and offline key hiding as cost-increasing defenses, never absolute prevention.
- Do not claim that adding the shell's x86 library converts an ARM-only application into an x86 application.
- Reject unsupported AAB, split, Flutter, Unity, React Native, hotfix, plugin, or existing-shell scenarios rather than inventing compatibility.

## Deliverable

Produce or update the assigned task card and any required ADR. Make every decision needed by the implementing agent, while leaving implementation details that do not affect contracts or safety to that agent.
