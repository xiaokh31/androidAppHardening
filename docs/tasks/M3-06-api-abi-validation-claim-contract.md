---
id: M3-06
title: "API/ABI validation-claim contract"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M0-03
  - M2-04
  - M3-01
  - M3-02
required_skills:
  - plan-apk-hardening-change
  - validate-protected-apk
  - coordinate-project-handoff
security_sensitive: false
---

## Goal

Before resuming M3-04, define an evidence-accurate API/ABI compatibility contract that enumerates the complete matrix while separating real-device `VERIFIED` results from unavailable `UNVERIFIED` combinations.

## Background

The original M3-04 contract required real-device evidence for all API 29-36 and four-ABI cells. The authorized inventory cannot supply API 30-35 or the full ABI set on API 36. Substituting build success, another ABI, or an unpinned environment is forbidden. ADR 0012 makes the absence explicit instead of inventing evidence or blocking forever.

## Inputs

- M0-03 locked Android API and toolchain provenance.
- M2-04 four-ABI build and packaging contract.
- M3-01 public Android fixture catalog and completed device evidence.
- M3-02 payload-before-load tamper assertions.
- M3-04 device inventory blocker recorded on its paused task branch.

## Expected Outputs

- ADR 0012 defining `VERIFIED`, `FAILED`, and `UNVERIFIED` claim semantics.
- Revised M3-04 task, compatibility matrix, test strategy, roadmap, and project plan.
- A complete machine-readable cell contract that cannot omit unavailable combinations.
- Governance evidence showing dependency consistency, links, UTF-8, HandOff, and claim wording validation.

## In Scope

- Compatibility-claim terminology and release wording.
- M3-04 dependencies, acceptance criteria, result schema, mandatory available baseline, and unavailable-cell handling.
- Documentation consistency across project governance sources.
- Recording that M3-04 is paused at its blocker commit and resumes only after this task merges.

## Out of Scope

- Android production code, Host implementation, Runtime binaries, fixture behavior, or wire formats.
- Device installation, physical-device execution, KVM, fuzz, benchmarks, or downloading new system images.
- Declaring any API/ABI cell verified.
- Implementing M3-04 or starting M3-05.

## Implementation Decisions

- Add a new ADR because compatibility claims are cross-module and release-facing.
- Keep all API 29 through locked `compileSdk` by four-ABI cells in the output; never shorten the grid to the available inventory.
- Require exactly one status per cell: `VERIFIED`, `FAILED`, or `UNVERIFIED`.
- Require real Android-reported device facts and complete acceptance evidence before `VERIFIED` is legal.
- Permit M3-04 completion with `UNVERIFIED` cells only when every such cell has a stable reason and no positive compatibility claim, every mandatory available baseline cell is `VERIFIED`, and no cell is `FAILED`.
- Keep four-ABI Runtime build capability distinct from device compatibility claims.

## Public Interfaces

- ADR 0012 compatibility-claim vocabulary.
- M3-04 `compatibility-matrix.json` status and evidence contract.
- Human-readable compatibility wording consumed by M4 release documentation.

## Security Constraints

- Do not weaken M3-02 signer/container tamper or payload-before-load assertions for any `VERIFIED` cell.
- Do not store raw device identifiers, customer paths, signing secrets, plaintext DEX, or full sensitive logs.
- Do not convert missing evidence into a positive or inferred claim.

## Compatibility Requirements

- Preserve `minSdk >= 29` as an input rule and preserve all four Runtime build ABIs.
- Preserve API/ABI cells as distinct dimensions; another API or ABI cannot substitute.
- Preserve ARM-only input limitations independently of Runtime build capability.
- Treat only exact M3-04 `VERIFIED` cells as release-validated combinations.

## Acceptance Criteria

- ADR 0012 and all affected governance documents use one consistent status model.
- M3-04 depends on M3-06 and defines the four-cell mandatory current baseline plus explicit handling for every other cell.
- No document still promises automatic support for every API from 29 through `compileSdk` without exact matrix evidence.
- `node tools/governance/validate-project-package.mjs` exits `0`.
- `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` exits `0` on the clean frozen branch.
- `git diff --check` exits `0`, links resolve, and no UTF-8 replacement character or sensitive material is introduced.
- The unique Issue #56 PR passes exact-head Ubuntu/Windows Build and Governance; device, KVM, and fuzz workflows are not required because no executable input changes.

## Required Tests

- Governance package validation and task dependency-cycle validation.
- Strict HandOff validation.
- Static claim scan for stale blanket API-support wording.
- Complete-grid/status semantic review against ADR 0012.
- Diff, UTF-8 replacement-character, absolute-path, and sensitive-material scans.

## Required Evidence

- Base and frozen commit SHA, changed-file list, commands, exit codes, environment, and timestamps.
- Issue/branch/PR uniqueness and exact-head Build/Governance results.
- Proof that no production, fixture, device, KVM, fuzz, or benchmark file changed.
- Post-merge README/HandOff synchronization before M3-04 resumes.

## Likely Files

- `docs/adr/0012-api-abi-validation-claim-boundary.md`
- `docs/tasks/M3-06-api-abi-validation-claim-contract.md`
- `docs/tasks/M3-04-api-and-abi-matrix.md`
- `docs/tasks/INDEX.md`
- `docs/COMPATIBILITY_MATRIX.md`
- `docs/TEST_STRATEGY.md`
- `docs/PROJECT_PLAN.md`
- `docs/ROADMAP.md`
- `tools/governance/validate-project-package.mjs`
- `HandOff.md`

## Dependencies and Blockers

If any affected document still implies that build capability, `minSdk`, endpoint testing, or another ABI proves an unavailable cell, this task remains blocked and M3-04 cannot resume.

## Agent Handoff Requirements

Use branch `docs/m3-06-api-abi-validation-contract`, only Issue #56, and one corresponding PR. The handoff must include the accepted claim model, complete affected-file list, validation commands, exact-head CI, and the preserved paused M3-04 commit. Do not run devices or start M3-05.
