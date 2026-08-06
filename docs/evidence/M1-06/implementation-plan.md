# M1-06 Implementation Plan

- task: `M1-06`
- branch: `feat/m1-06-cli-and-json-report`
- base: `55ef3c57e631cde65d3e04d58aa75d26a7e75ba8`
- validation mode: `full-flow`
- fixture boundary: repository-generated signed APK and synthetic RuntimeBundle contract fixture only; no customer APK, production Runtime, device, or emulator

## Scope

Implement the single `protect` command, stable exit/error mapping, ordered pipeline state machine, deterministic UTF-8/LF report writer, report schema, path/alias checks, bounded workspace ownership, cancellation cleanup, atomic report publication, and application launcher in `host/cli`. Consume the frozen M1-01 through M1-05 public APIs without changing their parser, container, AXML, packaging, ABI, or signer algorithms.

The production entry loads a fixed RuntimeBundle from classpath resources supplied by a later distribution assembly. M1-06 does not package synthetic Runtime bytes into product resources. Its full-flow integration test injects the M1-05-authorized deterministic synthetic RuntimeBundle contract fixture; a missing distribution RuntimeBundle fails closed as `INTERNAL_RUNTIME_BUNDLE_UNAVAILABLE`/`70`.

## Public Contract

- command: `android-app-hardening protect --input <apk> --output <apk> --report <json>`
- read-only global commands: `--help`, `--version`
- schema: `docs/specs/REPORT_V1.md` and `docs/specs/report-v1.schema.json`, version `1`
- exit codes: `0`, `2`, `10`, `11`, `12`, `13`, `14`, `15`, `70` exactly as the task card defines
- stdout: empty for every `protect` result; stderr: one sanitized summary line
- report top-level order: `schema_version`, then the eleven task-card objects/arrays from `tool` through `errors`; path tokens derive from role plus sanitized basename so normalized Windows/Ubuntu reports remain equal without serializing parent paths

`schema_version` is an explicit top-level discriminator; the task card's fixed top-level list is interpreted as the fields following that discriminator. Report SHA-256 is external evidence because embedding a file's own digest would be self-referential; the report contains input/output/container/config hashes.

M1-01 exposes APK DEX ordinals as one-based values while the frozen AHDC v2 wire contract is zero-based. The CLI creates an immutable zero-based orchestration view for M1-04/M1-05 and retains the original one-based inspection for REPORT_V1; neither upstream parser nor container wire format is changed.

## Failure and Publication Rules

1. Reject malformed arguments, pre-existing output/report, invalid parents, aliases, and signing-like options before entering the pipeline.
2. Enter stages only in the fixed order: inspect, signer, manifest, container, package, verify, publish.
3. Keep the input read-only and re-hash it after the final stage.
4. Let M1-05 publish only a verifier-approved unsigned APK. Build and fsync the success report in the report parent, then atomically publish it without replacement.
5. If success-report publication fails, remove the just-published output and return output failure. On any earlier failure, publish only a sanitized failure report when the report path was validated.
6. Cleanup only paths owned by the current invocation and located beneath its random workspace. Interruption maps to `INTERNAL_CANCELLED`/`70` and never leaves a success output.

## Acceptance Matrix

- positive: repository signed synthetic APK, single and multi DEX model coverage, application/original factory, Java-only and native ABI classification, valid unsigned output, valid schema/report hashes
- usage/path: help/version, unknown/repeated/missing options, signing-like options, input/output/report aliases, non-ASCII/space names, pre-existing targets
- mapped failures: INPUT/COMPAT, SIGNER, AXML, CONTAINER, PACKAGE, OUTPUT, INTERNAL
- injected failures: container/package/verify/report publish/cancellation, cleanup and input immutability
- deterministic: normalized success and failure reports identical on Windows/Ubuntu after removing UTC time, duration, generated fixture/input and random output/container/config hashes
- scans: no private-key/keystore/password/signing execution capability, absolute paths, stack traces, DEX bytes, or secret environment values in product output

No ADR is required because this task implements the already frozen CLI/report contract without changing cross-module wire formats, signer policy, ABI behavior, compatibility claims, or signing responsibility.
