# ADR 0021: v0.2 Maven published-artifact source profiles

## Status

Proposed; implementation authorized by the user on 2026-08-26 for V2-M0-02 / PR #96. Acceptance still requires independent exact-head review and the existing merge/post-merge gates.

## Context

The locked dependency versions are not changing. The verification metadata already requires `org.ow2.asm:asm:9.9`, `javax.inject:javax.inject:1` and `org.jdom:jdom2:2.0.6`. Their exact verified POMs respectively declare GitLab, Google Code, and GitHub without an SCM tag. Maven permits an absent SCM tag (the model default is HEAD); a Maven release is not necessarily a GitHub Release. Requiring every transitive component to derive a GitHub release from its POM therefore prevents an honest machine lock.

Official references: [Maven POM SCM](https://maven.apache.org/pom.html#scm), [Maven model](https://maven.apache.org/ref/3.9.15/maven-model/maven.html), and [Maven Central immutability](https://central.sonatype.org/publish/requirements/immutability/). Repository publication and source-code provenance are different claims: neither a version string nor a matching binary hash proves a source commit or a reproducible upstream build.

## Decision

1. Retain every dependency coordinate/version and every existing verification checksum. Do not remove old metadata-only coordinates to reduce coverage. Enumerate the union of tracked verification metadata and the actual resolved Maven graph, with one independent record per exact coordinate and every consumed/verified artifact.
2. Keep the existing named source profiles and pinned release/advisory endpoints. Replace the impossible generic profile with `locked-maven-published-artifact-v2`. Its `published-artifact acquisition identity` is the exact versioned official repository POM plus complete artifact URL/size/SHA-256 closure, not a fabricated VCS identity. POM-only parents/BOMs/plugin markers are explicitly metadata components; executable artifacts are never relabelled metadata. Correct two demonstrably erroneous named artifact addresses, without changing any checksum: CycloneDX Gradle plugin 3.4.1 implementation POM/JAR/module use its official Plugin Portal endpoint (Central returned three 404s), and Jazzer artifact paths remove one duplicated `jazzer/` segment. These are exact-coordinate corrections, not a general repository fallback.
3. Use only configured Google Maven, Maven Central, and the exact CycloneDX Plugin Portal marker and 3.4.1 implementation endpoints. Select a unique repository before freeze by exact POM/artifact observations, record the selection evidence, and then require that fixed repository without fallback. Only the selection algorithm's complete pre-freeze 404 negative observations are allowed; partial component availability, other HTTP errors, changed hashes, ambiguity, graph/metadata omissions, and mismatched artifact names remain BLOCKED. A selected repository's 404 after freeze is always BLOCKED. The three implementation URLs have exactly one HTTP 303 to the official `plugins-artifacts.gradle.org` path containing the already-pinned SHA-256; lock that exact hop and final URL. A redirect hop binds status/Location, not a nonexistent body MIME/ETag. The marker's single fixed URL has no ETag: only it uses a fresh full GET with exact 653-byte/hash/application-xml identity before every run instead of conditional ETag revalidation. All other final successful endpoints retain ETag requirements.
4. Record the POM's literal project URL and SCM URL/connection/tag, and its explicit parent coordinate, separately from acquisition identity. Missing fields are JSON null with an enumerated reason; never infer tags from versions, replace Google Code/GitLab with a mirror, or treat a parent/root profile as the component's identity. A missing tag is not an immutable tag. A source JAR, when locked as an additional provenance artifact, proves only those source-archive bytes; it does not prove that they produced the binary.
5. M0-02 lock completion means complete immutable build-input acquisition identity. It is not a source-code provenance, vulnerability, support-status, or security PASS. Each generic component separately records `sourceCodeStatus=UNVERIFIED` and `securityReviewStatus=PENDING`. Neither value can satisfy a downstream PASS. V2-M3-01 must freeze validators that distinguish these states before a candidate exists; V2-M4-01 must still fail closed for missing source/reachability, maintenance/support, license, or advisory evidence. This ADR does not waive those requirements, approve an orphaned dependency, or authorize downstream changes to a frozen lock.
6. POM SCM/project URLs are untrusted `POM_DECLARED` statements, not verified vendor attribution. A mechanically derived GitHub advisory URL remains `advisoryStatus=UNVERIFIED`, not RESOLVED/PASS; non-GitHub or absent/ambiguous SCM is explicitly `advisoryInitialUrl=null`, `advisoryStatus=UNRESOLVED`, never a false empty result. Unresolved vendor coverage prevents a security PASS and requires a separately reviewed source-profile decision; OSV/GitHub-global/NVD/search results do not substitute. The complete M0-02 lock must expose these entries, not hide or skip them.
7. Freeze this ADR as a product-contract input by adding its exact path to the existing identity policy and updating only the corresponding pinned policy hash. Keep post-freeze path policy, manifest ownership, dependency versions, product logic and all merge identity requirements unchanged. The task's narrow authorized contract delta includes this ADR, the source contract, identity explanation/policy, its hash pins, and the task-card explanation.

## Consequences

M0-02 can freeze honestly identified build inputs without claiming missing source or security facts. The lock will also be a complete blocker inventory for pre-candidate planning: components with unresolved vendor/source provenance cannot silently reach V2-M4-01 PASS. M0-02 does not implement the V2-M3-01 security runner, download an OSV database, or form security evidence.

The absence of source-code provenance is a real residual supply-chain risk. After implementation freeze, changing any implementation-owned profile, endpoint or lock requires new authorization and a new implementation freeze, followed by a refreshed validation freeze; M3-01/M3-02 cannot fill the old lock even before candidate creation. Subsequent security findings belong in a separate evidence object bound to the exact lock hash, never by overwriting frozen UNVERIFIED/PENDING fields. A frozen candidate cannot repair this by editing a lock. This is deliberately not a guarantee that the current dependency baseline will be releasable.

## Rejected Alternatives

- Guess a GitHub mirror/tag or borrow a root profile: fabricates provenance and hides per-component gaps.
- Upgrade dependencies or discard verification records: exceeds this authorization and changes the baseline.
- Treat exact artifact hashes or OSV zero findings as source/vendor/security proof: conflates acquisition identity with security acceptance.
- Keep only generator rules in the lock: omits the actual coordinate/artifact observations needed for reproducibility.
- Let M4 fill missing frozen identities: violates pre-candidate ownership and makes evidence mutable.

## Security Impact

No signing capability, private-key interface, APK transformation, Runtime behavior, vulnerability severity rule, or security acceptance waiver is introduced. UNKNOWN/UNRESOLVED source or vendor evidence continues to block security PASS. Exact byte pinning, complete component coverage, no runtime fallback, and independent review remain mandatory.

## Compatibility Impact

No dependency version, API/ABI, minSdk, public product CLI, or APK format changes. Host targets remain Windows/Ubuntu; Runtime build targets remain four ABIs.

## Verification

- Recompute every coordinate/artifact from verification metadata and the resolved graph; reject missing, extra, duplicate, reordered, renamed, repository-switched, wrong-size/hash/MIME records.
- Exercise GitHub/no-tag, GitLab, Google Code, absent SCM, parent-only metadata and named-profile fixtures; missing source fields must remain explicit, never fabricated PASS.
- Rebuild manifests from the exact committed Git tree, including this ADR and the revised contract/policy.
- Preserve all existing local, dual-platform CI, independent review, MERGE_COMMIT two-parent, and unique main-push Governance attempt-1 gates.
