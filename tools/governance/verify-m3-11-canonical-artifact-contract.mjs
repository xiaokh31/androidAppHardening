#!/usr/bin/env node

import crypto from "node:crypto";
import childProcess from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const lockPath = path.join(root, "docs", "evidence", "M3-11", "canonical-artifact-lock.json");
const taskPath = path.join(root, "docs", "tasks", "M3-11-canonical-startup-artifact-contract.md");
const m310Path = path.join(root, "docs", "tasks", "M3-10-startup-attribution-diagnostic.md");
const adrPath = path.join(root, "docs", "adr", "0016-end-to-end-startup-attribution-boundary.md");

const expected = Object.freeze({
  headSha: "1c030334d607bc10054b876dd969ea8048725cb3",
  runId: "31931428130",
  jobId: "95126754768",
  artifactId: "9260244215",
  artifactName: "m0-05-api-36-x86_64-evidence",
  artifactSizeBytes: 3316848,
  artifactArchiveSha256: "98c5cedce457775e4f4365226647b1bf1d49cb3f824d07ae5f9450c31803d5ae",
  bootIdHashPrefix: "a3cf719802bc",
  baselinePath: "benchmarks/android/build/reports/performance/apks/kotlin-multidex-baseline.apk",
  baselineSize: 30022,
  baselineSha256: "f666ea37d4f5dcc96fb994066ab97659a11119a33d637606b5cc0636efdf4c36",
  protectedPath: "benchmarks/android/build/reports/performance/apks/kotlin-multidex-protected.apk",
  protectedSize: 1287876,
  protectedSha256: "f265688bd8eea4f85def8c4edf50aae14e287688523e2ccafdf9ca04e891b658",
  signerPrefix: "0696de7d3f22",
  tupleSha256: "a7131f59ab69769c3ebe3dcc4d7295b3e11ae84c823701f6985c953803068c4a",
});

function fail(message) {
  throw new Error(message);
}

function exactKeys(value, keys, label) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) fail(`${label} must be an object`);
  const actual = Object.keys(value).sort();
  const wanted = [...keys].sort();
  if (actual.join("|") !== wanted.join("|")) fail(`${label} keys must be exactly ${wanted.join(",")}`);
}

function expectEqual(actual, wanted, label) {
  if (actual !== wanted) fail(`${label} mismatch`);
}

function canonicalTuple(lock) {
  return JSON.stringify({
    schemaVersion: 1,
    fixtureId: lock.fixtureId,
    baselineSha256: lock.canonicalPair.baseline.sha256,
    protectedSha256: lock.canonicalPair.protected.sha256,
  });
}

function sha256Bytes(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function validateLock(lock) {
  exactKeys(lock, ["schemaVersion", "taskId", "fixtureId", "source", "canonicalPair", "retention"], "lock");
  expectEqual(lock.schemaVersion, 1, "schemaVersion");
  expectEqual(lock.taskId, "M3-11", "taskId");
  expectEqual(lock.fixtureId, "kotlin-multidex", "fixtureId");

  exactKeys(lock.source, [
    "repository", "pullRequest", "headSha", "workflowName", "runId", "runAttempt", "jobId", "jobName",
    "artifactId", "artifactName", "artifactSizeBytes", "artifactArchiveSha256", "bootIdHashPrefix",
  ], "source");
  expectEqual(lock.source.repository, "xiaokh31/androidAppHardening", "source.repository");
  expectEqual(lock.source.pullRequest, 63, "source.pullRequest");
  expectEqual(lock.source.headSha, expected.headSha, "source.headSha");
  expectEqual(lock.source.workflowName, "M0-05 Linux KVM", "source.workflowName");
  expectEqual(lock.source.runId, expected.runId, "source.runId");
  expectEqual(lock.source.runAttempt, 1, "source.runAttempt");
  expectEqual(lock.source.jobId, expected.jobId, "source.jobId");
  expectEqual(lock.source.jobName, "API 36 x86_64 (Linux/KVM)", "source.jobName");
  expectEqual(lock.source.artifactId, expected.artifactId, "source.artifactId");
  expectEqual(lock.source.artifactName, expected.artifactName, "source.artifactName");
  expectEqual(lock.source.artifactSizeBytes, expected.artifactSizeBytes, "source.artifactSizeBytes");
  expectEqual(lock.source.artifactArchiveSha256, expected.artifactArchiveSha256, "source.artifactArchiveSha256");
  expectEqual(lock.source.bootIdHashPrefix, expected.bootIdHashPrefix, "source.bootIdHashPrefix");

  exactKeys(lock.canonicalPair, ["tupleSerialization", "productTupleSha256", "baseline", "protected"], "canonicalPair");
  for (const [role, wantedPath, wantedSize, wantedHash] of [
    ["baseline", expected.baselinePath, expected.baselineSize, expected.baselineSha256],
    ["protected", expected.protectedPath, expected.protectedSize, expected.protectedSha256],
  ]) {
    const item = lock.canonicalPair[role];
    exactKeys(item, ["relativePath", "sizeBytes", "sha256", "signatureScheme", "signerCount", "signerCertificateSha256Prefix"], `canonicalPair.${role}`);
    expectEqual(item.relativePath, wantedPath, `${role}.relativePath`);
    expectEqual(item.sizeBytes, wantedSize, `${role}.sizeBytes`);
    expectEqual(item.sha256, wantedHash, `${role}.sha256`);
    expectEqual(item.signatureScheme, "v3", `${role}.signatureScheme`);
    expectEqual(item.signerCount, 1, `${role}.signerCount`);
    expectEqual(item.signerCertificateSha256Prefix, expected.signerPrefix, `${role}.signerCertificateSha256Prefix`);
  }
  if (!lock.canonicalPair.tupleSerialization.includes("UTF-8 JSON without BOM or trailing newline")) {
    fail("tupleSerialization must fix UTF-8/no-BOM/no-trailing-newline semantics");
  }
  const tupleBytes = Buffer.from(canonicalTuple(lock), "utf8");
  expectEqual(tupleBytes.length, 218, "canonical tuple byte length");
  expectEqual(sha256Bytes(tupleBytes), expected.tupleSha256, "recomputed product tuple");
  expectEqual(lock.canonicalPair.productTupleSha256, expected.tupleSha256, "recorded product tuple");

  exactKeys(lock.retention, ["trackedInRepository", "ignoredArtifactRoot", "regenerationPermitted", "failClosedIfExactBytesUnavailable"], "retention");
  expectEqual(lock.retention.trackedInRepository, false, "retention.trackedInRepository");
  expectEqual(lock.retention.ignoredArtifactRoot, "build/m3-11/provenance-artifact", "retention.ignoredArtifactRoot");
  expectEqual(lock.retention.regenerationPermitted, false, "retention.regenerationPermitted");
  expectEqual(lock.retention.failClosedIfExactBytesUnavailable, true, "retention.failClosedIfExactBytesUnavailable");
  return lock;
}

function verifyArtifactRoot(lock, artifactRoot) {
  const resolvedRoot = path.resolve(root, artifactRoot);
  for (const role of ["baseline", "protected"]) {
    const entry = lock.canonicalPair[role];
    const candidate = path.resolve(resolvedRoot, ...entry.relativePath.split("/"));
    const prefix = `${resolvedRoot}${path.sep}`;
    if (!candidate.startsWith(prefix)) fail(`${role} path escapes artifact root`);
    const stat = fs.lstatSync(candidate);
    if (!stat.isFile() || stat.isSymbolicLink()) fail(`${role} must be a regular non-symlink file`);
    expectEqual(stat.size, entry.sizeBytes, `${role} actual size`);
    expectEqual(sha256Bytes(fs.readFileSync(candidate)), entry.sha256, `${role} actual sha256`);
  }
}

function validateDocuments(lock) {
  const task = fs.readFileSync(taskPath, "utf8");
  const m310 = fs.readFileSync(m310Path, "utf8");
  const adr = fs.readFileSync(adrPath, "utf8");
  for (const [label, text] of [["M3-11", task], ["M3-10", m310], ["ADR 0016", adr]]) {
    for (const token of [
      lock.source.headSha, lock.source.runId, lock.source.jobId, lock.source.artifactId,
      lock.canonicalPair.baseline.sha256, lock.canonicalPair.protected.sha256,
      lock.canonicalPair.productTupleSha256,
    ]) {
      if (!text.includes(token)) fail(`${label} missing canonical token ${token}`);
    }
  }
  for (const phrase of ["Rebuilding an original APK from source", "depends_on:\n  - M3-09\n  - M3-11", "private key is absent"]) {
    if (!m310.includes(phrase)) fail(`M3-10 missing fail-closed phrase: ${phrase}`);
  }
  if (!adr.includes("M3-11 canonical artifact lock")) fail("ADR 0016 missing M3-11 canonical lock decision");
}

function runSelfTest(lock) {
  const mutations = [
    ["head_sha", (x) => { x.source.headSha = "0".repeat(40); }],
    ["run_id", (x) => { x.source.runId = "31931428131"; }],
    ["job_id", (x) => { x.source.jobId = "95126754769"; }],
    ["artifact_id", (x) => { x.source.artifactId = "9260244216"; }],
    ["baseline_path", (x) => { x.canonicalPair.baseline.relativePath = "replacement.apk"; }],
    ["baseline_size", (x) => { x.canonicalPair.baseline.sizeBytes += 1; }],
    ["protected_hash", (x) => { x.canonicalPair.protected.sha256 = "0".repeat(64); }],
    ["signer_prefix", (x) => { x.canonicalPair.protected.signerCertificateSha256Prefix = "000000000000"; }],
    ["tuple_hash", (x) => { x.canonicalPair.productTupleSha256 = "0".repeat(64); }],
    ["regeneration", (x) => { x.retention.regenerationPermitted = true; }],
    ["fail_open", (x) => { x.retention.failClosedIfExactBytesUnavailable = false; }],
    ["extra_key", (x) => { x.source.unreviewed = true; }],
  ];
  for (const [name, mutate] of mutations) {
    const candidate = structuredClone(lock);
    mutate(candidate);
    let rejected = false;
    try { validateLock(candidate); } catch { rejected = true; }
    if (!rejected) fail(`mutation was accepted: ${name}`);
  }
  return mutations.length;
}

function verifyGovernanceOnlyDiff(baseRef) {
  if (!/^[0-9a-f]{40}$/u.test(baseRef)) fail("--base-ref must be a full lowercase commit SHA");
  const files = childProcess.execFileSync("git", ["diff", "--name-only", `${baseRef}...HEAD`], {
    cwd: root,
    encoding: "utf8",
  }).split(/\r?\n/u).filter(Boolean);
  const allowedExact = new Set([
    "HandOff.md",
    "README.md",
    ".github/workflows/governance.yml",
    "docs/PROJECT_PLAN.md",
    "docs/ROADMAP.md",
    "docs/TEST_STRATEGY.md",
    "docs/adr/0016-end-to-end-startup-attribution-boundary.md",
    "docs/tasks/INDEX.md",
    "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
    "docs/tasks/M3-10-startup-attribution-diagnostic.md",
    "docs/tasks/M3-11-canonical-startup-artifact-contract.md",
    "tools/governance/validate-project-package.mjs",
    "tools/governance/verify-m3-08-startup-stability-contract.mjs",
    "tools/governance/verify-m3-09-startup-attribution-contract.mjs",
    "tools/governance/verify-m3-11-canonical-artifact-contract.mjs",
  ]);
  for (const file of files) {
    const allowedEvidence = file.startsWith("docs/evidence/M3-11/");
    if (!allowedExact.has(file) && !allowedEvidence) fail(`non-governance file changed: ${file}`);
  }
  return files.length;
}

function parseArgs(args) {
  let artifactRoot = null;
  let selfTest = false;
  let baseRef = null;
  for (let i = 0; i < args.length; i += 1) {
    if (args[i] === "--self-test") selfTest = true;
    else if (args[i] === "--artifact-root" && i + 1 < args.length) artifactRoot = args[++i];
    else if (args[i] === "--base-ref" && i + 1 < args.length) baseRef = args[++i];
    else fail(`unknown or incomplete argument: ${args[i]}`);
  }
  return { artifactRoot, baseRef, selfTest };
}

try {
  const args = parseArgs(process.argv.slice(2));
  const lock = validateLock(JSON.parse(fs.readFileSync(lockPath, "utf8")));
  validateDocuments(lock);
  if (args.artifactRoot) verifyArtifactRoot(lock, args.artifactRoot);
  const mutations = args.selfTest ? runSelfTest(lock) : 0;
  const changedFileCount = args.baseRef ? verifyGovernanceOnlyDiff(args.baseRef) : 0;
  console.log(JSON.stringify({
    taskId: lock.taskId,
    result: "PASS",
    productTupleSha256: lock.canonicalPair.productTupleSha256,
    actualBytesVerified: Boolean(args.artifactRoot),
    mutationCount: mutations,
    changedFileCount,
  }));
} catch (error) {
  console.error(`M3-11 canonical artifact contract validation failed: ${error.message}`);
  process.exit(1);
}
