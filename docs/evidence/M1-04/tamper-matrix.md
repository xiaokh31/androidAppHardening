# M1-04 tamper and failure matrix

Timestamp: `2026-08-06T06:03:17+08:00`

The self-test builds a clean fixed-RNG container, copies it for every file-level
mutation, flips one bit at the selected field, and invokes a fresh verifier with a
fresh `ExpectedBinding`. Twenty-six mutated container files plus the clean source
were exercised. The
SHA-256 of the UTF-8 sorted `<sha256> <filename>` corpus manifest is
`d4a1e6764a429946c0d79acb0025bd6b56ee9e651f23cee792e41909ee90475d`.

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
| authenticated zlib checksum | `CONTAINER_FORMAT` | all chunks authenticate, checksum fails closed |
| authenticated zlib dictionary request | `CONTAINER_FORMAT` | first chunk authenticates, dictionary is rejected |
| authenticated concatenated/trailing zlib stream | `CONTAINER_FORMAT` | first chunk authenticates, trailing stream is rejected |
| truncation and trailing byte | `CONTAINER_FORMAT` | exact file coverage rejected |
| wrong current signer/lineage | `CONTAINER_AUTH_FAILED` | no payload read |
| different package public binding | `CONTAINER_AUTH_FAILED` | CEK envelope authentication fails |
| ConfigV2 Factory slot | `CONTAINER_AUTH_FAILED` | complete config digest fails |
| ConfigV2 wrapped CEK tag and AAD prefix | `CONTAINER_AUTH_FAILED` | CEK is not returned |
| malformed Factory UTF-8 | `CONTAINER_FORMAT` | config parser fails closed |
| input changed between compression passes | `CONTAINER_INPUT_CHANGED` | partial output removed |
| CSPRNG failure or all-zero material | `CONTAINER_RANDOM_FAILED` | allocated material cleared; no output |
| colliding `R` and `R_java` | `CONTAINER_RANDOM_FAILED` | zero native share is never packaged |
| I/O injection and unsupported atomic move | `CONTAINER_FORMAT` | partial candidate removed; final output absent |
| early/middle cleanup callback failure | `CONTAINER_KEY_MATERIAL` | all remaining arrays cleared; final output absent |
| late cleanup callback plus primary action failure | primary action retained | cleanup failure suppressed and all plan arrays cleared |
| cancellation between passes | `CONTAINER_INPUT_CHANGED` with field `cancelled` | partial output removed; material cleared |
| second `KeyPackagingPlanV2.consume` | `CONTAINER_KEY_MATERIAL` | action is not invoked |

The verifier calls `authenticatedBeforeInflate(record, chunk)` only after the
one-shot AES-GCM `doFinal(ciphertext || tag)` succeeds. Every structural,
preflight and authentication mutation asserts zero callbacks. The three
authenticated malformed-zlib cases assert the precise number of callbacks before
the format failure. No failure publishes DEX output.
