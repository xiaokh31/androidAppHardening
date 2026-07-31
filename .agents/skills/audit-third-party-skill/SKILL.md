---
name: audit-third-party-skill
description: Audit an external Agent Skill before it is installed, copied, updated, enabled, or executed in this repository. Use for skills registry candidates, GitHub-hosted skills, vendored SKILL.md files, bundled scripts, or any proposed change to an approved external skill.
---

# Audit Third-Party Skill

## Rule

Do not execute or install the candidate during review. Obtain an immutable source snapshot or fixed commit and inspect every file, including hidden metadata, scripts, references, assets, manifests, and dependency declarations.

## Audit

1. Record source URL, owner, fixed commit, retrieval date, file hashes, license, copyright notices, and modification obligations.
2. Compare the trigger description with the actual behavior. Reject misleading, overly broad, or prompt-injection-like triggers.
3. Enumerate commands, subprocesses, network endpoints, downloads, package installs, telemetry, environment reads, credential access, filesystem writes, Git operations, signing behavior, and destructive actions.
4. Check Windows and Ubuntu compatibility and identify assumptions about GUI tools, shells, elevated access, or interactive login.
5. Reject any skill that can expose credentials, collect customer APKs, sign applications, overwrite inputs, bypass repository rules, force-push, or execute unreviewed downloaded code.
6. Prefer a new project-local skill when the candidate is materially broader than the required workflow.

## Decision

Return `approve`, `approve-with-changes`, or `reject`, with evidence and residual risks. Before adoption, record approved source, commit, hashes, license, modifications, and audit date in `docs/TOOLCHAIN_AND_PROVENANCE.md` and `THIRD_PARTY_NOTICES.md`.
