# Worker Handoff

## Task

- task_id: `<M0-00>`
- branch: `<allowed-prefix/m0-00-short-name>`
- commit: `<40-character-lowercase-sha-or-UNCOMMITTED>`
- owner_role: `<task-card-owner-role>`

## Outcome

- status: `<done-or-blocked>`
- summary: `<one-verifiable-sentence>`

## Scope

- completed: `<task-card-items-completed>`
- not_completed: `None`

## Files Changed

- `<repository-relative-path>`: `<purpose>`

## Public Interfaces

None

## Verification Evidence

- command: `<exact-command>`
- exit_code: `<integer>`
- environment: `<os-and-toolchain>`
- timestamp: `<ISO-8601-with-timezone>`
- artifact: `<repository-relative-path-or-controlled-artifact-id>`
- sha256: `<64-lowercase-hex-or-not_applicable>`

## Security and Compatibility

- input_immutable: `<verified-or-not_applicable>`
- signing_boundary: `<verified-or-not_applicable>`
- api_abi_impact: `<verified-impact>`
- independent_security_review: `<review-link-or-not_required>`

## Remaining Risks

None

## Blockers

None

## Recommended Next Action

`<one-task-from-the-dependency-graph-or-None>`
