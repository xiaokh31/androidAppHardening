#!/usr/bin/env node
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync, mkdtempSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { parseStrictJson } from "./canonicalize-cyclonedx-v02.mjs";

const PROFILE = "locked-maven-published-artifact-v2";
const LOCK_PATH = "tools/validation/v02-supply-chain-tools.json";
const METADATA_PATH = "gradle/verification-metadata.xml";
const GRAPH_PATH = "build/v0.2/resolved-maven-graph.json";
const REPOSITORIES = {
  "google-maven": "https://dl.google.com/dl/android/maven2/",
  "maven-central": "https://repo.maven.apache.org/maven2/",
};
const MARKER = "org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.4.1";
const PLUGIN = "org.cyclonedx:cyclonedx-gradle-plugin:3.4.1";
const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
const canonical = (value) => Buffer.from(`${JSON.stringify(value, null, 2)}\n`);
const compare = (a, b) => Buffer.compare(Buffer.from(a), Buffer.from(b));
const fail = (message) => { throw new Error(message); };
const assert = (condition, message) => { if (!condition) fail(message); };
const keys = (value, expected, label) => assert(value && JSON.stringify(Object.keys(value)) === JSON.stringify(expected), `${label}: exact keys/order required`);

// Deliberately small non-validating XML reader: no DTD, entities, external resolution, XInclude or namespace rewriting.
// Only predefined/numeric character references are accepted. Duplicate attributes and malformed nesting fail closed.
export function parseXml(bytes) {
  assert(bytes.length > 0 && bytes.length <= 16 * 1024 * 1024, "XML size invalid");
  const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  assert(!/<!DOCTYPE|<!ENTITY/iu.test(text), "XML DTD/entities forbidden");
  const decode = (value) => value.replace(/&([^;]+);/gu, (_, entity) => {
    const named = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };
    if (Object.hasOwn(named, entity)) return named[entity];
    if (/^#(?:[0-9]+|x[0-9a-f]+)$/iu.test(entity)) {
      const number = entity[1] === "x" ? parseInt(entity.slice(2), 16) : Number(entity.slice(1));
      assert(number > 0 && number <= 0x10ffff && !(number >= 0xd800 && number <= 0xdfff), "XML character invalid");
      return String.fromCodePoint(number);
    }
    fail("XML entity unsupported");
  });
  const document = { name: "#document", children: [], text: "", attributes: {} };
  const stack = [document];
  let offset = 0;
  const tokens = /<!--[\s\S]*?-->|<\?[\s\S]*?\?>|<!\[CDATA\[[\s\S]*?\]\]>|<[^>]*>|[^<]+/gu;
  for (const match of text.matchAll(tokens)) {
    assert(match.index === offset, "XML token gap");
    const token = match[0]; offset += token.length;
    const parent = stack.at(-1);
    if (token.startsWith("<!--") || token.startsWith("<?")) continue;
    if (token.startsWith("<![CDATA[")) { parent.text += token.slice(9, -3); continue; }
    if (!token.startsWith("<")) { assert(!/&(?!(?:[^;&\s]+);)/u.test(token), "XML bare ampersand"); parent.text += decode(token); continue; }
    if (token.startsWith("</")) {
      assert(stack.length > 1 && token === `</${parent.name}>`, "XML nesting invalid"); stack.pop(); continue;
    }
    const tag = /^<([A-Za-z_][\w.:-]*)([\s\S]*?)(\/?)>$/u.exec(token);
    assert(tag, "XML tag invalid");
    const attributes = Object.create(null);
    let remaining = tag[2];
    while (remaining.trim()) {
      const attribute = /^\s+([A-Za-z_][\w.:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')/u.exec(remaining);
      assert(attribute && !Object.hasOwn(attributes, attribute[1]), "XML attributes invalid/duplicate");
      attributes[attribute[1]] = decode(attribute[2] ?? attribute[3]); remaining = remaining.slice(attribute[0].length);
    }
    const node = { name: tag[1], attributes, children: [], text: "" };
    parent.children.push(node);
    if (!tag[3]) { assert(stack.length < 128, "XML depth exceeded"); stack.push(node); }
  }
  assert(offset === text.length && stack.length === 1 && document.children.length === 1 && !document.text.trim(), "XML document invalid");
  return document.children[0];
}
const children = (node, name) => node.children.filter((entry) => entry.name === name);
function child(node, name, required = false) {
  const found = children(node, name); assert(found.length <= 1 && (!required || found.length === 1), `XML ${name} missing/duplicate`);
  return found[0] ?? null;
}
function literal(node, name) {
  const value = node && child(node, name);
  if (!value) return null;
  assert(value.children.length === 0, `XML ${name} is not literal`);
  return value.text.trim() || null;
}
function token(value) {
  assert(typeof value === "string" && /^[A-Za-z0-9_][A-Za-z0-9_.+-]*$/u.test(value) && !value.includes("..") && !/SNAPSHOT|latest|^[+]/iu.test(value), "unsafe Maven token");
  return value;
}
export function metadataComponents(bytes, requirePom = true) {
  const xml = parseXml(bytes); assert(xml.name === "verification-metadata", "wrong verification document");
  const result = children(child(xml, "components", true), "component").map((component) => {
    const { group, name, version } = component.attributes;
    [group, name, version].forEach(token);
    const artifacts = children(component, "artifact").map((artifact) => {
      const artifactName = token(artifact.attributes.name);
      const checksums = children(artifact, "sha256").map((entry) => entry.attributes.value);
      assert(checksums.length === 1 && /^[0-9a-f]{64}$/u.test(checksums[0]), "one exact SHA-256 per artifact required");
      return { artifactName, sha256: checksums[0] };
    }).sort((a, b) => compare(a.artifactName, b.artifactName));
    assert(artifacts.length && new Set(artifacts.map((entry) => entry.artifactName)).size === artifacts.length, "duplicate/empty artifacts");
    if (requirePom) assert(artifacts.some((entry) => entry.artifactName === `${name}-${version}.pom`), `exact POM missing: ${group}:${name}:${version}`);
    return { coordinate: `${group}:${name}:${version}`, group, name, version, artifacts };
  }).sort((a, b) => compare(a.coordinate, b.coordinate));
  assert(result.length && new Set(result.map((entry) => entry.coordinate)).size === result.length, "duplicate/empty coordinates");
  return result;
}
export function namedProfile(component) {
  const { coordinate, group, name, version } = component;
  if (coordinate === "com.android.tools.build:gradle:9.3.0") return "android-gradle-plugin-maven-v1";
  if (coordinate === "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10") return "kotlin-gradle-plugin-maven-github-v1";
  if (coordinate === "org.cyclonedx:cyclonedx-gradle-plugin:3.4.1" || coordinate === "org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.4.1") return "cyclonedx-gradle-plugin-maven-github-v1";
  if (group === "net.java.dev.jna" && ["jna", "jna-platform"].includes(name) && ["5.19.1", "5.6.0"].includes(version)) return "jna-maven-github-v1";
  if (group === "com.code-intelligence" && ["jazzer", "jazzer-api"].includes(name) && version === "0.29.1") return "jazzer-maven-github-v1";
  return PROFILE;
}
function namedEndpoints(component, pomUrl, declared) {
  const profiles = {
    "android-gradle-plugin-maven-v1": ["https://developer.android.com/build/releases/agp-9-3-0-release-notes", "https://source.android.com/docs/security/bulletin/asb-overview"],
    "kotlin-gradle-plugin-maven-github-v1": ["https://api.github.com/repos/JetBrains/kotlin/releases/tags/v2.4.10", "https://api.github.com/repos/JetBrains/kotlin/security-advisories?per_page=100"],
    "cyclonedx-gradle-plugin-maven-github-v1": ["https://api.github.com/repos/CycloneDX/cyclonedx-gradle-plugin/releases/tags/cyclonedx-gradle-plugin-3.4.1", "https://api.github.com/repos/CycloneDX/cyclonedx-gradle-plugin/security-advisories?per_page=100"],
    "jna-maven-github-v1": [`https://api.github.com/repos/java-native-access/jna/releases/tags/${component.version}`, "https://api.github.com/repos/java-native-access/jna/security-advisories?per_page=100"],
    "jazzer-maven-github-v1": ["https://api.github.com/repos/CodeIntelligenceTesting/jazzer/releases/tags/v0.29.1", "https://api.github.com/repos/CodeIntelligenceTesting/jazzer/security-advisories?per_page=100"],
  };
  const [releaseInitialUrl, advisoryInitialUrl] = profiles[namedProfile(component)] ?? [pomUrl, advisoryUrl(declared)];
  return { releaseInitialUrl, advisoryInitialUrl };
}
function artifactUrl(repositoryId, component, artifactName) {
  if (repositoryId === "cyclonedx-plugin-portal") {
    assert([MARKER, PLUGIN].includes(component.coordinate), "Plugin Portal not authorized");
    return `https://plugins.gradle.org/m2/${component.group.replaceAll(".", "/")}/${component.name}/${component.version}/${artifactName}`;
  }
  assert(Object.hasOwn(REPOSITORIES, repositoryId), "repository not allowed");
  return `${REPOSITORIES[repositoryId]}${component.group.replaceAll(".", "/")}/${component.name}/${component.version}/${artifactName}`;
}
function repositoryIds(component) {
  return [MARKER, PLUGIN].includes(component.coordinate) ? ["cyclonedx-plugin-portal"] : Object.keys(REPOSITORIES);
}
function finalUrl(repositoryId, component, artifact) {
  if (repositoryId === "cyclonedx-plugin-portal" && component.coordinate === PLUGIN) {
    return `https://plugins-artifacts.gradle.org/org.cyclonedx/cyclonedx-gradle-plugin/3.4.1/${artifact.sha256}/${artifact.artifactName}`;
  }
  return artifactUrl(repositoryId, component, artifact.artifactName);
}
function expectedMime(repositoryId, component, artifact, status) {
  if (status === 404) return "text/html";
  if (repositoryId === "cyclonedx-plugin-portal") return component.coordinate === MARKER ? "application/xml" : "binary/octet-stream";
  const extension = artifact.artifactName.split(".").at(-1);
  const mapping = repositoryId === "google-maven" ? {
    jar: "application/java-archive", pom: "application/octet-stream", module: "application/octet-stream",
  } : { jar: "application/java-archive", pom: "text/xml", module: "application/vnd.org.gradle.module+json" };
  assert(Object.hasOwn(mapping, extension), "unapproved Maven artifact extension");
  return mapping[extension];
}
export function pomDeclaration(bytes) {
  const pom = parseXml(bytes); assert(pom.name === "project", "POM project required");
  const scm = child(pom, "scm"), parent = child(pom, "parent");
  const values = {
    projectUrl: literal(pom, "url"), scmUrl: literal(scm, "url"), scmConnection: literal(scm, "connection"),
    scmDeveloperConnection: literal(scm, "developerConnection"), scmTag: literal(scm, "tag"),
    parentCoordinate: parent ? [literal(parent, "groupId"), literal(parent, "artifactId"), literal(parent, "version")].map(token).join(":") : null,
  };
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, { value, status: value === null ? "NOT_DECLARED" : "POM_DECLARED" }]));
}
export function advisoryUrl(declared) {
  const urls = ["scmUrl", "scmConnection", "scmDeveloperConnection"].map((key) => declared[key].value).filter((value) => value !== null);
  if (!urls.length) return null;
  const parsed = urls.map((value) => {
    const match = /^(?:scm:git:)?(?:https?:\/\/github\.com\/|git@github\.com:|ssh:\/\/git@github\.com\/)([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+?)(?:\.git)?\/?$/u.exec(value);
    return match ? `${match[1]}/${match[2]}` : null;
  });
  return parsed.every((value) => value !== null && value === parsed[0]) ? `https://api.github.com/repos/${parsed[0]}/security-advisories?per_page=100` : null;
}
export async function observation(repositoryId, component, artifact, cache) {
  const artifactInitialUrl = artifactUrl(repositoryId, component, artifact.artifactName);
  const cachePath = resolve(cache, `${hash(Buffer.from(artifactInitialUrl))}.json`);
  let response = await fetch(artifactInitialUrl, { redirect: "manual", signal: AbortSignal.timeout(120000) });
  const initialStatus = response.status, artifactFinalUrl = finalUrl(repositoryId, component, artifact);
  if (artifactInitialUrl !== artifactFinalUrl) {
    assert(initialStatus === 303 && response.headers.get("location") === artifactFinalUrl,
      `Plugin Portal fixed redirect mismatch: ${artifactInitialUrl} status=${initialStatus} location=${response.headers.get("location")} expected=${artifactFinalUrl}`);
    await response.body?.cancel();
    response = await fetch(artifactFinalUrl, { redirect: "manual", signal: AbortSignal.timeout(120000) });
  }
  assert([200, 404].includes(response.status), `HTTP ${response.status}: ${artifactInitialUrl}`);
  assert(response.url === artifactFinalUrl, `unexpected artifact redirect: ${artifactInitialUrl}`);
  const contentType = response.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase();
  assert(contentType, `missing Content-Type: ${artifactInitialUrl}`);
  assert(contentType === expectedMime(repositoryId, component, artifact, response.status), `unapproved MIME: ${artifactInitialUrl} HTTP=${response.status} MIME=${contentType}`);
  if (response.status === 200 && component.coordinate !== MARKER) assert(response.headers.get("etag"), `missing ETag: ${artifactInitialUrl}`);
  const digest = createHash("sha256"), pieces = []; let sizeBytes = 0;
  for await (const piece of response.body) {
    sizeBytes += piece.length; assert(sizeBytes <= 256 * 1024 * 1024, "artifact too large"); digest.update(piece);
    if (artifact.artifactName.endsWith(".pom") && response.status === 200) pieces.push(piece);
  }
  const value = { artifactName: artifact.artifactName, artifactInitialUrl, initialStatus, artifactFinalUrl,
    status: response.status, sizeBytes, sha256: digest.digest("hex"), contentType,
    revalidationPolicy: component.coordinate === MARKER ? "full-get-fixed-identity" : "etag-conditional" };
  if (response.status === 200) assert(value.sha256 === artifact.sha256, `verification mismatch: ${artifactInitialUrl}`);
  const captured = { observation: value, retrievedAt: new Date().toISOString(), finalUrl: response.url, etag: response.headers.get("etag"), lastModified: response.headers.get("last-modified"), pomBase64: pieces.length ? Buffer.concat(pieces).toString("base64") : null };
  writeFileSync(cachePath, canonical(captured), { flag: "wx" });
  return captured;
}
export function componentRecord(component, captures) {
  const observations = Object.fromEntries(Object.entries(captures).map(([id, values]) => [id, values.map((entry) => entry.observation)]));
  const successful = Object.entries(observations).filter(([, values]) => values.every((entry) => entry.status === 200));
  assert(successful.length === 1, `repository identity ambiguous/missing: ${component.coordinate}`);
  const [repositoryId, artifacts] = successful[0];
  for (const [id, values] of Object.entries(observations)) if (id !== repositoryId) assert(values.every((entry) => entry.status === 404), `partially available alternate repository: ${component.coordinate}`);
  const pomName = `${component.name}-${component.version}.pom`;
  const pomCapture = captures[repositoryId].find((entry) => entry.observation.artifactName === pomName);
  const pomBytes = Buffer.from(pomCapture.pomBase64, "base64");
  assert(hash(pomBytes) === pomCapture.observation.sha256, "captured POM mismatch");
  assert(pomBytes.length === pomCapture.observation.sizeBytes, "captured POM size mismatch");
  assert(pomBytes.toString("base64") === pomCapture.pomBase64, "noncanonical POM base64");
  const pomDeclared = pomDeclaration(pomBytes);
  const { releaseInitialUrl, advisoryInitialUrl } = namedEndpoints(component, pomCapture.observation.artifactInitialUrl, pomDeclared);
  return {
    coordinate: component.coordinate, group: component.group, name: component.name, version: component.version,
    sourceProfile: namedProfile(component), coverageClass: "osv-package",
    componentKind: component.artifacts.some((entry) => !/\.(?:pom|module)$/u.test(entry.artifactName)) ? "binary" : "metadata",
    repositoryId, pom: { artifactName: pomName, sizeBytes: pomBytes.length, sha256: hash(pomBytes), bytesBase64: pomCapture.pomBase64 },
    artifacts, repositoryObservations: observations, pomDeclared, sourceCodeStatus: "UNVERIFIED",
    releaseInitialUrl, advisoryInitialUrl, advisoryStatus: advisoryInitialUrl === null ? "UNRESOLVED" : "UNVERIFIED", securityReviewStatus: "PENDING",
  };
}
export function validateResolvedGraph(graph) {
  keys(graph, ["schemaVersion", "configurations", "coordinates"], "resolved graph");
  assert(graph.schemaVersion === 1, "graph schema mismatch");
  const configurationNames = Object.keys(graph.configurations);
  assert(configurationNames.length > 0 && JSON.stringify(configurationNames) === JSON.stringify([...configurationNames].sort(compare)), "graph configurations empty/unsorted");
  const union = new Set();
  for (const [configuration, coordinates] of Object.entries(graph.configurations)) {
    assert(/^:(?:[A-Za-z0-9_-]*:)+[A-Za-z0-9_-]+$/u.test(configuration), "configuration identity invalid");
    assert(Array.isArray(coordinates) && JSON.stringify(coordinates) === JSON.stringify([...new Set(coordinates)].sort(compare)), "graph coordinates duplicate/unsorted");
    for (const coordinate of coordinates) {
      assert(typeof coordinate === "string" && coordinate.split(":").length === 3, "graph coordinate invalid");
      coordinate.split(":").forEach(token); union.add(coordinate);
    }
  }
  assert(union.size > 0 && JSON.stringify(graph.coordinates) === JSON.stringify([...union].sort(compare)), "graph union mismatch");
}
function graphBinding(graph) {
  validateResolvedGraph(graph);
  const bytes = canonical(graph);
  return { path: GRAPH_PATH, sizeBytes: bytes.length, sha256: hash(bytes), document: graph };
}
export function validateMavenLock(lock, metadataBytes, resolvedGraph) {
  keys(lock, ["schemaVersion", "identityKind", "securityPass", "metadata", "resolvedGraph", "components"], "Maven lock");
  assert(lock.schemaVersion === 2 && lock.identityKind === "published-artifact-acquisition" && lock.securityPass === false, "acquisition is not security PASS");
  assert(JSON.stringify(lock.metadata) === JSON.stringify({ path: METADATA_PATH, sizeBytes: metadataBytes.length, sha256: hash(metadataBytes) }), "metadata binding mismatch");
  assert(JSON.stringify(lock.resolvedGraph) === JSON.stringify(graphBinding(resolvedGraph)), "resolved graph binding mismatch");
  const expected = metadataComponents(metadataBytes), coordinates = new Set(expected.map((entry) => entry.coordinate));
  for (const coordinate of resolvedGraph.coordinates) assert(coordinates.has(coordinate), `resolved coordinate unverified: ${coordinate}`);
  assert(Array.isArray(lock.components) && lock.components.length === expected.length, "component set mismatch");
  lock.components.forEach((record, index) => {
    const component = expected[index];
    assert(record.coordinate === component.coordinate, "component order/identity mismatch");
    const repositories = repositoryIds(component);
    keys(record.repositoryObservations, repositories, "repository set");
    const captures = {};
    for (const id of repositories) {
      const values = record.repositoryObservations[id]; assert(values.length === component.artifacts.length, "observation set mismatch");
      captures[id] = values.map((value, artifactIndex) => {
        const artifact = component.artifacts[artifactIndex];
        keys(value, ["artifactName", "artifactInitialUrl", "initialStatus", "artifactFinalUrl", "status", "sizeBytes", "sha256", "contentType", "revalidationPolicy"], "observation");
        assert(value.artifactName === artifact.artifactName && value.artifactInitialUrl === artifactUrl(id, component, artifact.artifactName), "artifact URL/name mismatch");
        assert([200, 404].includes(value.status) && Number.isSafeInteger(value.sizeBytes) && value.sizeBytes > 0 && /^[0-9a-f]{64}$/u.test(value.sha256), "observation identity invalid");
        assert(value.artifactFinalUrl === finalUrl(id, component, artifact) && value.initialStatus === (value.artifactInitialUrl === value.artifactFinalUrl ? value.status : 303), "redirect identity mismatch");
        assert(value.contentType === expectedMime(id, component, artifact, value.status), "MIME invalid");
        assert(value.revalidationPolicy === (component.coordinate === MARKER ? "full-get-fixed-identity" : "etag-conditional"), "revalidation policy mismatch");
        if (value.status === 200) assert(value.sha256 === artifact.sha256, "artifact hash mismatch");
        return { observation: value, pomBase64: value.artifactName === record.pom.artifactName && value.status === 200 ? record.pom.bytesBase64 : null };
      });
    }
    const reconstructed = componentRecord(component, captures);
    assert(JSON.stringify(record) === JSON.stringify(reconstructed), `component fields/state/bytes mismatch: ${component.coordinate}`);
    // Parent is only recorded as a literal declaration; no inheritance or parent SCM is evaluated here.
  });
  return { componentCount: expected.length, unresolvedAdvisoryCount: lock.components.filter((entry) => entry.advisoryStatus === "UNRESOLVED").length, securityPass: false };
}
async function main() {
  assert(process.argv.length === 3 && ["complete-poms", "generate", "validate"].includes(process.argv[2]), "usage: locked-maven-published-artifact-v2.mjs complete-poms|generate|validate");
  const toolLock = parseStrictJson(readFileSync(LOCK_PATH)), metadataBytes = readFileSync(METADATA_PATH);
  if (process.argv[2] === "complete-poms") {
    const previous = metadataComponents(metadataBytes, false);
    let metadata = metadataBytes.toString("utf8");
    for (const component of previous) {
      const artifactName = `${component.name}-${component.version}.pom`;
      if (component.artifacts.some((entry) => entry.artifactName === artifactName)) continue;
      const observations = await Promise.all(Object.keys(REPOSITORIES).map(async (id) => {
        const url = artifactUrl(id, component, artifactName);
        const response = await fetch(url, { signal: AbortSignal.timeout(60000) });
        assert([200, 404].includes(response.status), `POM HTTP ${response.status}: ${url}`);
        const bytes = Buffer.from(await response.arrayBuffer());
        assert(bytes.length <= 16 * 1024 * 1024, "POM too large");
        return { status: response.status, bytes, url };
      }));
      const success = observations.filter((entry) => entry.status === 200);
      assert(success.length === 1 && observations.filter((entry) => entry.status === 404).length === 1, `POM repository ambiguous: ${component.coordinate}`);
      pomDeclaration(success[0].bytes);
      const checksum = hash(success[0].bytes);
      const start = `<component group="${component.group}" name="${component.name}" version="${component.version}">`;
      const startIndex = metadata.indexOf(start), endIndex = metadata.indexOf("      </component>", startIndex);
      assert(startIndex >= 0 && endIndex > startIndex, "metadata component formatting mismatch");
      metadata = metadata.slice(0, endIndex) + `         <artifact name="${artifactName}">\n            <sha256 value="${checksum}" origin="V2-M0-02 exact published POM acquisition"/>\n         </artifact>\n` + metadata.slice(endIndex);
      process.stdout.write(`${component.coordinate} ${checksum}\n`);
    }
    const completed = metadataComponents(Buffer.from(metadata));
    assert(completed.length === previous.length, "coordinate set changed");
    previous.forEach((component, index) => component.artifacts.forEach((artifact) => {
      assert(completed[index].artifacts.some((entry) => entry.artifactName === artifact.artifactName && entry.sha256 === artifact.sha256), "existing verification hash changed");
    }));
    writeFileSync(METADATA_PATH, metadata);
    return;
  }
  if (process.argv[2] === "validate") {
    const result = validateMavenLock(toolLock.mavenLock, metadataBytes, parseStrictJson(readFileSync(GRAPH_PATH)));
    process.stdout.write(`${JSON.stringify(result)}\n`); return;
  }
  const resolvedGraph = parseStrictJson(readFileSync(GRAPH_PATH));
  const components = metadataComponents(metadataBytes), results = new Array(components.length);
  const acquisitionRoot = resolve("build/v0.2/maven-acquisition"); mkdirSync(acquisitionRoot, { recursive: true });
  const cache = mkdtempSync(resolve(acquisitionRoot, "attempt-"));
  let next = 0;
  await Promise.all(Array.from({ length: 4 }, async () => {
    while (next < components.length) {
      const index = next++, component = components[index];
      const repositories = repositoryIds(component);
      const captures = Object.fromEntries(await Promise.all(repositories.map(async (repositoryId) => [repositoryId,
        await Promise.all(component.artifacts.map((artifact) => observation(repositoryId, component, artifact, cache))),
      ])));
      results[index] = componentRecord(component, captures);
      process.stdout.write(`${index + 1}/${components.length} ${component.coordinate}\n`);
    }
  }));
  const mavenLock = { schemaVersion: 2, identityKind: "published-artifact-acquisition", securityPass: false,
    metadata: { path: METADATA_PATH, sizeBytes: metadataBytes.length, sha256: hash(metadataBytes) }, resolvedGraph: graphBinding(resolvedGraph), components: results };
  validateMavenLock(mavenLock, metadataBytes, resolvedGraph);
  const plugin = results.find((entry) => entry.coordinate === PLUGIN);
  for (const artifact of toolLock.cycloneDxGradlePlugin.artifacts) {
    const acquired = plugin.artifacts.find((entry) => entry.artifactName === artifact.name);
    if (acquired) {
      assert(artifact.sha256 === acquired.sha256 && artifact.sizeBytes === acquired.sizeBytes, "named plugin artifact identity changed");
      artifact.initialUrl = acquired.artifactInitialUrl;
    }
  }
  const module = plugin.artifacts.find((entry) => entry.artifactName.endsWith(".module"));
  if (!toolLock.cycloneDxGradlePlugin.artifacts.some((entry) => entry.name === module.artifactName)) {
    toolLock.cycloneDxGradlePlugin.artifacts.splice(3, 0, {
      name: module.artifactName, initialUrl: module.artifactInitialUrl, sizeBytes: module.sizeBytes, sha256: module.sha256,
    });
  }
  delete toolLock.lockedMavenComponentGenerator;
  toolLock.mavenLock = mavenLock;
  writeFileSync(LOCK_PATH, canonical(toolLock));
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => { process.stderr.write(`V02_MAVEN_LOCK_BLOCKED: ${error.message}\n`); process.exitCode = 1; });
}
