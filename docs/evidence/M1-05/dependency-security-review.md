# M1-05 JNA dependency security review

- Review date: `2026-08-06`
- Selected version: JNA/JNA Platform `5.19.1`
- Official release tag: `java-native-access/jna` `5.19.1`
- Official release commit: `1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d`
- Release commit date: `2026-06-12T16:47:07Z`
- Distribution source: Maven Central
- License: LGPL-2.1-or-later or Apache-2.0

## Need and scope

Java 17 does not expose a portable, specified no-replace atomic rename plus a
stable Windows file ID API. M1-05 uses JNA only for Windows `MoveFileExW` and
`GetFileInformationByHandleEx`, and Linux `renameat2(RENAME_NOREPLACE)`. It does
not parse the input APK, handle cryptographic material, execute input code, or
load a caller-selected native library name.

## Maintenance and vulnerability check

The official repository tag API identified `5.19.1` at the release commit above.
GitHub's official global-advisory API was queried on the review date for Maven
packages `net.java.dev.jna:jna` and `net.java.dev.jna:jna-platform`; both queries
returned zero published advisories. The third-review reference
`CVE-2021-44549` was also checked against GitHub Advisory Database and NVD: it
applies to Apache Sling Commons Messaging Mail, not JNA. No risk acceptance is
being used; the prior direct dependency on `5.6.0` is removed and all distributed
JNA components are upgraded together to the current official `5.19.1` tag.

The verification file still contains `5.6.0` checksums because AGP's existing
Android lint tool configurations resolve that version as a build-only transitive
dependency. The Host `repacker` and downstream `cli` locks contain only `5.19.1`;
the older build-tool artifact is not part of the Host distribution.

## Resolved artifacts

| Maven Central artifact | SHA-256 |
| --- | --- |
| `net/java/dev/jna/jna/5.19.1/jna-5.19.1.jar` | `4fb141dd8ef6b0585ffceea4bc49602fbc6312fa977e2c488794ea3e6aafecae` |
| `net/java/dev/jna/jna/5.19.1/jna-5.19.1.pom` | `911b754a03b66af0fed2bbdfdc3a86807360dd036ad3b481ef5162c685e456bf` |
| `net/java/dev/jna/jna-platform/5.19.1/jna-platform-5.19.1.jar` | `3b3864f5b449e9c3c24b16861524b622b086563f44e0cd8384c8efc5a6052f82` |
| `net/java/dev/jna/jna-platform/5.19.1/jna-platform-5.19.1.pom` | `3e9603f6f0e7a49c88378d2a82bcaf53e33ad67dbc90a8d5a27e09a9736fce50` |

## Supply-chain controls

- exact versions are fixed by the version catalog and per-project lockfiles;
- JAR and POM SHA-256 values are recorded in Gradle verification metadata;
- repositories remain restricted to the project-approved Maven sources;
- `THIRD_PARTY_NOTICES.md` and toolchain provenance identify the source,
  version, release commit, license, purpose, and distribution boundary;
- Windows module tests exercise the production JNA publication and file-ID
  paths; Ubuntu CI must exercise the Linux path before merge.

This review is point-in-time evidence, not a claim that future advisories cannot
appear. Any later advisory is handled by the repository vulnerability-response
process.
