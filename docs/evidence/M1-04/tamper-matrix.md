# M1-04 tamper and failure matrix

Timestamp: `2026-08-06T01:16:29+08:00`

The self-test builds a clean fixed-RNG container, copies it for every file-level
mutation, flips one bit at the selected field, and invokes a fresh verifier with a
fresh `ExpectedBinding`. Twenty-three mutated container files were exercised. The
SHA-256 of the UTF-8 sorted `<sha256> <filename>` corpus manifest is
`bf0593b2b7ec5098100b77a96c2e265b4ebfff793945863862bc8a267a7f5e79`.

| Boundary | Expected failure | Pre-inflate property |
| --- | --- | --- |
| magic | `CONTAINER_FORMAT` | no payload read |
| major version, flags, chunk maximum | `CONTAINER_VERSION` | no payload read |
| DEX count | `CONTAINER_LIMIT_EXCEEDED` | no payload read |
| payload length, reserved bytes | `CONTAINER_FORMAT` | no payload read |
| build ID, key-slot ID | `CONTAINER_FORMAT` | config/header mismatch before payload |
| config digest | `CONTAINER_AUTH_FAILED` | config binding fails before payload |
| SPV1 | `CONTAINER_FORMAT` | signer block rejected before payload |
| manifest MAC | `CONTAINER_AUTH_FAILED` | no payload read |
| record compressed length and payload offset | `CONTAINER_FORMAT` | topology rejected before payload |
| record nonce prefix and original digest | `CONTAINER_AUTH_FAILED` | manifest rejected before payload |
| chunk record/ordinal/compressed offset | `CONTAINER_FORMAT` | topology rejected before payload |
| ciphertext and tag | `CONTAINER_AUTH_FAILED` | affected chunk never reaches inflater |
| truncation and trailing byte | `CONTAINER_FORMAT` | exact file coverage rejected |
| wrong current signer/lineage | `CONTAINER_AUTH_FAILED` | no payload read |
| different package public binding | `CONTAINER_AUTH_FAILED` | CEK envelope authentication fails |
| ConfigV2 Factory slot | `CONTAINER_AUTH_FAILED` | complete config digest fails |
| ConfigV2 wrapped CEK tag and AAD prefix | `CONTAINER_AUTH_FAILED` | CEK is not returned |
| malformed Factory UTF-8 | `CONTAINER_FORMAT` | config parser fails closed |
| input changed between compression passes | `CONTAINER_INPUT_CHANGED` | partial output removed |
| CSPRNG failure or all-zero material | `CONTAINER_RANDOM_FAILED` | allocated material cleared; no output |
| cancellation between passes | `CONTAINER_INPUT_CHANGED` with field `cancelled` | partial output removed; material cleared |
| second `KeyPackagingPlanV2.consume` | `CONTAINER_KEY_MATERIAL` | action is not invoked |

The verifier calls `authenticatedBeforeInflate(record, chunk)` only after the
one-shot AES-GCM `doFinal(ciphertext || tag)` succeeds. The successful multi-chunk
fixture observed exactly one callback per canonical chunk; authentication failures
observed none for the affected chunk and produced no DEX output.
