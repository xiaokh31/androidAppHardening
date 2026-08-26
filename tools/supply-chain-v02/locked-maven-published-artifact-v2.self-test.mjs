#!/usr/bin/env node
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { metadataComponents, pomDeclaration, advisoryUrl, namedProfile, parseXml, validateMavenLock, validateResolvedGraph } from "./locked-maven-published-artifact-v2.mjs";
import { parseStrictJson } from "./canonicalize-cyclonedx-v02.mjs";

const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
const metadata = readFileSync("gradle/verification-metadata.xml");
const graph = parseStrictJson(readFileSync("build/v0.2/resolved-maven-graph.json"));
const tools = parseStrictJson(readFileSync("tools/validation/v02-supply-chain-tools.json"));
const locked = tools.mavenLock;
const result = validateMavenLock(locked, metadata, graph);
assert.equal(result.securityPass, false);
assert.equal(result.componentCount, metadataComponents(metadata).length);
let negativeCount = 0;
const clone = (value) => JSON.parse(JSON.stringify(value));
function reject(label, mutate) {
  const value = clone(locked); mutate(value);
  assert.notDeepEqual(value, locked, `no-op ${label}`);
  assert.throws(() => validateMavenLock(value, metadata, graph), undefined, label);
  negativeCount++;
}
for (const key of Object.keys(locked)) reject(`missing lock ${key}`, (value) => { delete value[key]; });
reject("extra lock", (value) => { value.extra = true; });
reject("security PASS", (value) => { value.securityPass = true; });
reject("metadata identity", (value) => { value.metadata.sha256 = "0".repeat(64); });
reject("graph identity", (value) => { value.resolvedGraph.sizeBytes++; });
reject("graph document", (value) => { value.resolvedGraph.document.coordinates.pop(); });
reject("missing component", (value) => { value.components.pop(); });
reject("extra component", (value) => { value.components.push(clone(value.components[0])); });
reject("reordered", (value) => { value.components.reverse(); });
const first = locked.components[0];
for (const key of Object.keys(first)) reject(`missing component ${key}`, (value) => { delete value.components[0][key]; });
for (const [key, changed] of Object.entries({ sourceCodeStatus: "VERIFIED", securityReviewStatus: "PASS", sourceProfile: "root-profile", coverageClass: "tool-binary", componentKind: "metadata", advisoryStatus: "RESOLVED", releaseInitialUrl: "https://example.invalid/release", advisoryInitialUrl: "https://example.invalid/advisory", repositoryId: "maven-central", version: "latest" })) {
  reject(`component ${key}`, (value) => { value.components[0][key] = changed; });
}
for (const [key, changed] of Object.entries({ artifactName: "wrong.jar", artifactInitialUrl: "https://example.invalid/artifact", artifactFinalUrl: "https://example.invalid/redirect", initialStatus: 302, status: 404, sizeBytes: 1, sha256: "0".repeat(64), contentType: "text/plain", revalidationPolicy: "cache" })) {
  reject(`artifact ${key}`, (value) => { value.components[0].artifacts[0][key] = changed; });
}
for (const key of ["projectUrl", "scmUrl", "scmConnection", "scmDeveloperConnection", "scmTag", "parentCoordinate"]) {
  reject(`POM declaration ${key}`, (value) => { value.components[0].pomDeclared[key] = { value: "invented", status: "POM_DECLARED" }; });
}
reject("POM bytes", (value) => { value.components[0].pom.bytesBase64 = Buffer.from("<project/>").toString("base64"); });
reject("POM size", (value) => { value.components[0].pom.sizeBytes++; });
reject("POM hash", (value) => { value.components[0].pom.sha256 = "0".repeat(64); });
reject("repository extra", (value) => { value.components[0].repositoryObservations["mirror"] = []; });
reject("repository swapped", (value) => {
  const observations = value.components[0].repositoryObservations;
  [observations["google-maven"], observations["maven-central"]] = [observations["maven-central"], observations["google-maven"]];
});
reject("MIME coherently changed", (value) => {
  const record = value.components[0]; record.artifacts[0].contentType = "text/plain";
  record.repositoryObservations[record.repositoryId][0].contentType = "text/plain";
});
for (const coordinate of ["org.ow2.asm:asm:9.9", "javax.inject:javax.inject:1", "org.jdom:jdom2:2.0.6"]) {
  const record = locked.components.find((entry) => entry.coordinate === coordinate);
  assert(record, coordinate); assert.equal(record.sourceProfile, "locked-maven-published-artifact-v2");
  assert.equal(record.pomDeclared.scmTag.value, null); assert.equal(record.pomDeclared.scmTag.status, "NOT_DECLARED");
  assert.equal(record.sourceCodeStatus, "UNVERIFIED");
}
assert.equal(locked.components.find((entry) => entry.coordinate === "javax.inject:javax.inject:1").advisoryStatus, "UNRESOLVED");
// JDOM's literal scp-style URL contains an extra slash; the closed normalizer does not guess equivalence.
assert.equal(locked.components.find((entry) => entry.coordinate === "org.jdom:jdom2:2.0.6").advisoryStatus, "UNRESOLVED");
const github = pomDeclaration(Buffer.from("<project><scm><url>https://github.com/example/project</url><connection>scm:git:git@github.com:example/project.git</connection></scm></project>"));
assert.equal(advisoryUrl(github), "https://api.github.com/repos/example/project/security-advisories?per_page=100");
for (const [coordinate, profile] of [
  ["com.android.tools.build:gradle:9.3.0", "android-gradle-plugin-maven-v1"],
  ["org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10", "kotlin-gradle-plugin-maven-github-v1"],
  ["org.cyclonedx:cyclonedx-gradle-plugin:3.4.1", "cyclonedx-gradle-plugin-maven-github-v1"],
  ["net.java.dev.jna:jna:5.19.1", "jna-maven-github-v1"],
  ["com.code-intelligence:jazzer:0.29.1", "jazzer-maven-github-v1"],
]) {
  const record = locked.components.find((entry) => entry.coordinate === coordinate);
  assert.equal(namedProfile(record), profile); assert.equal(record.sourceProfile, profile);
  assert.equal(record.advisoryStatus, "UNVERIFIED");
}
const plugin = locked.components.find((entry) => entry.coordinate === "org.cyclonedx:cyclonedx-gradle-plugin:3.4.1");
for (const entry of plugin.artifacts) {
  assert.equal(entry.initialStatus, 303); assert(entry.artifactFinalUrl.includes("/org.cyclonedx/"));
  assert.equal(entry.contentType, "binary/octet-stream");
  const pinned = tools.cycloneDxGradlePlugin.artifacts.find((artifact) => artifact.name === entry.artifactName);
  assert.equal(pinned.initialUrl, entry.artifactInitialUrl); assert.equal(pinned.sizeBytes, entry.sizeBytes); assert.equal(pinned.sha256, entry.sha256);
}
const marker = locked.components.find((entry) => entry.coordinate === "org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.4.1");
assert.equal(marker.artifacts[0].revalidationPolicy, "full-get-fixed-identity");
reject("marker exception widened", (value) => { const record = value.components.find((entry) => entry.coordinate === plugin.coordinate);
  record.artifacts[0].revalidationPolicy = "full-get-fixed-identity";
  record.repositoryObservations[record.repositoryId][0].revalidationPolicy = "full-get-fixed-identity";
});
for (const xml of ["<!DOCTYPE project><project/>", "<project><scm/><scm/></project>", "<project><url>&external;</url></project>", "<project><url>bare & text</url></project>", "<project><url></project>"]) {
  assert.throws(() => pomDeclaration(Buffer.from(xml))); negativeCount++;
}
assert.throws(() => parseXml(Buffer.from([0xff, 0xfe]))); negativeCount++;
const parentOnly = pomDeclaration(Buffer.from("<project><parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1</version></parent></project>"));
assert.equal(parentOnly.parentCoordinate.value, "org.example:parent:1");
assert.equal(parentOnly.scmUrl.value, null); assert.equal(advisoryUrl(parentOnly), null);
for (const source of ["https://gitlab.com/example/project", "http://code.google.com/p/example", "${scm.url}"]) {
  const declared = pomDeclaration(Buffer.from(`<project><scm><url>${source}</url></scm></project>`));
  assert.equal(declared.scmUrl.value, source); assert.equal(advisoryUrl(declared), null);
}
for (const mutate of [(value) => { value.coordinates.pop(); }, (value) => { value.configurations = {}; }, (value) => { value.coordinates.reverse(); }]) {
  const changed = clone(graph); mutate(changed); assert.throws(() => validateResolvedGraph(changed)); negativeCount++;
}
assert.equal(hash(Buffer.from(JSON.stringify(locked.resolvedGraph.document, null, 2) + "\n")), locked.resolvedGraph.sha256);
console.log(`V2-M0-02 Maven acquisition lock PASS: ${result.componentCount} components, ${negativeCount} negatives, ${result.unresolvedAdvisoryCount} UNRESOLVED, securityPass=false`);
