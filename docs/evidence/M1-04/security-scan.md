# M1-04 local security scan

Timestamp: `2026-08-06T01:16:29+08:00`

Scope:

- `host/container/src/main/kotlin/`
- `host/container/src/test/kotlin/`
- `docs/specs/AHDC_V2.md`
- `.github/workflows/build.yml`

Results:

- no private-key PEM marker, keystore path, password assignment, passphrase
  assignment, fixed content key, customer path, or credential was found;
- no UTF-8 replacement character was found;
- no claim of absolute, unbreakable, or impossible-to-extract protection was found;
- product code accepts no private key, keystore, alias, signing password, user key,
  passphrase, network KMS, token, or signing tool;
- input APK access is read-only and output is a new unsigned container artifact;
- no `AHDC` v1 or `ConfigV1` parser, fallback, or compatibility branch exists;
- no full DEX, compressed DEX, payload-sized array, or materialized chunk-object
  table exists in product code;
- exception messages expose only stable error codes and field labels, not paths,
  package contents, DEX bytes, key material, shares, or nonces.

Review of the cryptographic sequence confirmed standard JCA AES-256-GCM and
HMAC-SHA-256, RFC 5869 extract/expand, independent manifest/record domains,
canonical 96-bit chunk nonces, complete metadata MAC coverage, complete ConfigV2
digest binding, and authenticate-before-inflate ordering.

This is the implementer's local review, not the task's required independent
cryptographic/binary-format approval. Independent review must target the frozen
implementation commit before publication.
