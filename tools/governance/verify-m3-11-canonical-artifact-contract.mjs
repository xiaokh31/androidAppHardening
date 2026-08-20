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
  reportJobId: "device",
  artifactId: "9260244215",
  artifactName: "m0-05-api-36-x86_64-evidence",
  artifactSizeBytes: 3316848,
  artifactArchiveSha256: "98c5cedce457775e4f4365226647b1bf1d49cb3f824d07ae5f9450c31803d5ae",
  bootIdHashPrefix: "a3cf719802bc",
  environmentId: "api36-x86_64",
  fixtureId: "java-single-dex",
  baselinePath: "benchmarks/android/build/reports/performance/apks/java-single-dex-baseline.apk",
  baselineSize: 29962,
  baselineSha256: "4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf",
  protectedPath: "benchmarks/android/build/reports/performance/apks/java-single-dex-protected.apk",
  protectedSize: 1287876,
  protectedSha256: "1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9",
  signerPrefix: "0696de7d3f22",
  tupleSha256: "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd",
  artifactManifestPath: "benchmarks/android/build/reports/performance/benchmark-artifact-manifest.json",
  artifactManifestSha256: "d2166e07f5e959a9868c0da4ddd05a19e40f961559bec4367c8e8c00fba56089",
  repeatabilityPath: "benchmarks/android/build/reports/performance/benchmark-repeatability.json",
  repeatabilitySha256: "81b0982e4c5b6ae5a34d71218df6602cd44706d879c3909400a2809e5e4f55d8",
  campaignAReportPath: "benchmarks/android/build/reports/performance/campaign-a/benchmark-results.json",
  campaignAReportSha256: "f7528353cb5a3b4c8114546d4dcd53ab1e3efd7420e210abe7eb51067a8ddd2b",
  campaignBReportPath: "benchmarks/android/build/reports/performance/campaign-b/benchmark-results.json",
  campaignBReportSha256: "6845d3c9d7eba0d84aefe0d05da485e87f754f5fe63e7a57ba6807159d9a0979",
  metric: "processToApplicationOnCreateMs",
  statistic: "deltaP50",
  campaignAValueMs: 331,
  campaignBValueMs: 432,
  variation: 0.30513595166163143,
  repeatabilityLimit: 0.1,
});

const campaignDefinitions = Object.freeze({
  A: Object.freeze({
    fixtureOrder: Object.freeze(["java-single-dex", "kotlin-multidex", "jni-four-abi"]),
    modeOrder: "baseline_then_protected",
    reportSha256: expected.campaignAReportSha256,
    delta: expected.campaignAValueMs,
    p50: 489,
    baseline: 158,
  }),
  B: Object.freeze({
    fixtureOrder: Object.freeze(["jni-four-abi", "kotlin-multidex", "java-single-dex"]),
    modeOrder: "protected_then_baseline",
    reportSha256: expected.campaignBReportSha256,
    delta: expected.campaignBValueMs,
    p50: 506,
    baseline: 74,
  }),
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

function expectArray(actual, wanted, label) {
  if (!Array.isArray(actual) || JSON.stringify(actual) !== JSON.stringify(wanted)) fail(`${label} mismatch`);
}

function unique(values, predicate, label) {
  const matches = values.filter(predicate);
  if (matches.length !== 1) fail(`${label} must have exactly one match`);
  return matches[0];
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

function nearestRankP50(values, label) {
  if (!Array.isArray(values) || values.length !== 30 || values.some((value) => !Number.isFinite(value))) {
    fail(`${label} must contain exactly 30 finite samples`);
  }
  return [...values].sort((a, b) => a - b)[14];
}

function validateLock(lock) {
  exactKeys(lock, ["schemaVersion", "taskId", "fixtureId", "source", "failureEvidence", "canonicalPair", "retention"], "lock");
  expectEqual(lock.schemaVersion, 1, "schemaVersion");
  expectEqual(lock.taskId, "M3-11", "taskId");
  expectEqual(lock.fixtureId, expected.fixtureId, "fixtureId");

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

  exactKeys(lock.failureEvidence, [
    "artifactManifestPath", "artifactManifestSha256", "repeatabilityPath", "repeatabilitySha256",
    "campaignAReportPath", "campaignAReportSha256", "campaignBReportPath", "campaignBReportSha256",
    "metric", "statistic", "campaignAValueMs", "campaignBValueMs", "variation", "repeatabilityLimit",
    "repeatabilityPass",
  ], "failureEvidence");
  for (const key of [
    "artifactManifestPath", "artifactManifestSha256", "repeatabilityPath", "repeatabilitySha256",
    "campaignAReportPath", "campaignAReportSha256", "campaignBReportPath", "campaignBReportSha256",
    "metric", "statistic", "campaignAValueMs", "campaignBValueMs", "variation", "repeatabilityLimit",
  ]) expectEqual(lock.failureEvidence[key], expected[key], `failureEvidence.${key}`);
  expectEqual(lock.failureEvidence.repeatabilityPass, false, "failureEvidence.repeatabilityPass");

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
  const tuple = canonicalTuple(lock);
  expectEqual(lock.canonicalPair.tupleSerialization, tuple, "tupleSerialization");
  const tupleBytes = Buffer.from(tuple, "utf8");
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

function contained(parent, candidate) {
  const relative = path.relative(parent, candidate);
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative));
}

function assertNoLinkSegments(base, candidate, label) {
  const relative = path.relative(base, candidate);
  if (!contained(base, candidate)) fail(`${label} escapes repository root`);
  let current = base;
  for (const segment of relative.split(path.sep).filter(Boolean)) {
    current = path.join(current, segment);
    const stat = fs.lstatSync(current);
    if (stat.isSymbolicLink()) fail(`${label} contains a symbolic link or junction`);
  }
}

function secureArtifactFile(resolvedRoot, relativePath, label) {
  if (typeof relativePath !== "string" || relativePath.includes("\\") || path.isAbsolute(relativePath)) {
    fail(`${label} must be a repository-relative POSIX path`);
  }
  const candidate = path.resolve(resolvedRoot, ...relativePath.split("/"));
  if (!contained(resolvedRoot, candidate) || candidate === resolvedRoot) fail(`${label} escapes artifact root`);
  assertNoLinkSegments(root, candidate, label);
  const realRoot = fs.realpathSync.native(resolvedRoot);
  const realCandidate = fs.realpathSync.native(candidate);
  if (!contained(realRoot, realCandidate) || realCandidate === realRoot) fail(`${label} realpath escapes artifact root`);
  const stat = fs.lstatSync(candidate);
  if (!stat.isFile() || stat.isSymbolicLink()) fail(`${label} must be a regular non-symlink file`);
  return { candidate, stat };
}

function resolveArtifactRoot(lock, artifactRoot) {
  const expectedRoot = path.resolve(root, ...lock.retention.ignoredArtifactRoot.split("/"));
  const suppliedRoot = path.resolve(root, artifactRoot);
  if (suppliedRoot !== expectedRoot) fail("--artifact-root must be the exact ignoredArtifactRoot from the lock");
  assertNoLinkSegments(root, expectedRoot, "artifact root");
  const repoReal = fs.realpathSync.native(root);
  const artifactReal = fs.realpathSync.native(expectedRoot);
  if (!contained(repoReal, artifactReal) || artifactReal === repoReal) fail("artifact root realpath escapes repository");
  return expectedRoot;
}

function readHashedJson(resolvedRoot, relativePath, expectedHash, label) {
  const { candidate } = secureArtifactFile(resolvedRoot, relativePath, label);
  const bytes = fs.readFileSync(candidate);
  expectEqual(sha256Bytes(bytes), expectedHash, `${label} sha256`);
  try { return JSON.parse(bytes.toString("utf8")); } catch { fail(`${label} must be valid JSON`); }
}

function validateCommonIdentity(value, label) {
  expectEqual(value.headSha, expected.headSha, `${label}.headSha`);
  expectEqual(String(value.runId), expected.runId, `${label}.runId`);
  expectEqual(String(value.jobId), expected.reportJobId, `${label}.jobId`);
  expectEqual(value.runAttempt, 1, `${label}.runAttempt`);
  expectEqual(value.bootIdHashPrefix, expected.bootIdHashPrefix, `${label}.bootIdHashPrefix`);
}

function validateEvidenceModels(lock, models) {
  const { manifest, repeatability, reportA, reportB } = models;
  for (const [label, value] of Object.entries(models)) {
    if (value === null || typeof value !== "object" || Array.isArray(value)) fail(`${label} must be an object`);
  }

  validateCommonIdentity(manifest, "manifest");
  expectEqual(manifest.schemaVersion, 1, "manifest.schemaVersion");
  expectEqual(manifest.environmentId, expected.environmentId, "manifest.environmentId");
  if (!Array.isArray(manifest.campaigns) || manifest.campaigns.length !== 2) fail("manifest must contain exactly two campaigns");
  for (const id of ["A", "B"]) {
    const definition = campaignDefinitions[id];
    const campaign = unique(manifest.campaigns, (item) => item.id === id, `manifest campaign ${id}`);
    expectArray(campaign.fixtureOrder, definition.fixtureOrder, `manifest campaign ${id} fixtureOrder`);
    expectEqual(campaign.modeOrder, definition.modeOrder, `manifest campaign ${id} modeOrder`);
    expectEqual(campaign.reportSha256, definition.reportSha256, `manifest campaign ${id} reportSha256`);
  }
  if (!Array.isArray(manifest.artifacts) || manifest.artifacts.length !== 6) fail("manifest must contain exactly six APK artifacts");
  for (const role of ["baseline", "protected"]) {
    const expectedEntry = lock.canonicalPair[role];
    const item = unique(manifest.artifacts, (entry) => entry.artifactId === `${lock.fixtureId}-${role}`, `manifest ${role} APK`);
    expectEqual(item.fileName, path.posix.basename(expectedEntry.relativePath), `manifest ${role} fileName`);
    expectEqual(item.sha256, expectedEntry.sha256, `manifest ${role} sha256`);
  }

  validateCommonIdentity(repeatability, "repeatability");
  expectEqual(repeatability.schemaVersion, 1, "repeatability.schemaVersion");
  expectEqual(repeatability.environmentId, expected.environmentId, "repeatability.environmentId");
  expectEqual(repeatability.artifactManifestSha256, lock.failureEvidence.artifactManifestSha256, "repeatability.artifactManifestSha256");
  expectEqual(repeatability.allBudgetsPass, false, "repeatability.allBudgetsPass");
  expectEqual(repeatability.repeatabilityPass, false, "repeatability.repeatabilityPass");
  expectEqual(repeatability.cleanupPassed, true, "repeatability.cleanupPassed");
  if (!Array.isArray(repeatability.campaigns) || repeatability.campaigns.length !== 2) fail("repeatability must contain exactly two campaigns");
  for (const id of ["A", "B"]) {
    const definition = campaignDefinitions[id];
    const campaign = unique(repeatability.campaigns, (item) => item.id === id, `repeatability campaign ${id}`);
    validateCommonIdentity(campaign, `repeatability campaign ${id}`);
    expectEqual(campaign.environmentFingerprint, expected.environmentId, `repeatability campaign ${id} environmentFingerprint`);
    expectArray(campaign.fixtureOrder, definition.fixtureOrder, `repeatability campaign ${id} fixtureOrder`);
    expectEqual(campaign.modeOrder, definition.modeOrder, `repeatability campaign ${id} modeOrder`);
    expectEqual(campaign.artifactManifestSha256, lock.failureEvidence.artifactManifestSha256, `repeatability campaign ${id} manifest hash`);
    expectEqual(campaign.reportSha256, definition.reportSha256, `repeatability campaign ${id} reportSha256`);
    expectEqual(campaign.warmups, 5, `repeatability campaign ${id} warmups`);
    expectEqual(campaign.measurements, 30, `repeatability campaign ${id} measurements`);
    expectEqual(campaign.allBudgetsPass, false, `repeatability campaign ${id} allBudgetsPass`);
    expectEqual(campaign.cleanupPassed, true, `repeatability campaign ${id} cleanupPassed`);
  }
  if (!Array.isArray(repeatability.comparisons) || repeatability.comparisons.length !== 90) fail("repeatability must contain exactly 90 comparisons");
  const comparison = unique(repeatability.comparisons, (item) => item.fixtureId === lock.fixtureId
    && item.metric === lock.failureEvidence.metric && item.statistic === lock.failureEvidence.statistic, "canonical failure comparison");
  expectEqual(comparison.campaignA, lock.failureEvidence.campaignAValueMs, "comparison.campaignA");
  expectEqual(comparison.campaignB, lock.failureEvidence.campaignBValueMs, "comparison.campaignB");
  expectEqual(comparison.variation, lock.failureEvidence.variation, "comparison.variation");
  expectEqual(comparison.limit, lock.failureEvidence.repeatabilityLimit, "comparison.limit");
  expectEqual(comparison.pass, false, "comparison.pass");
  const recomputedVariation = Math.abs(comparison.campaignA - comparison.campaignB)
    / Math.max(1, Math.min(Math.abs(comparison.campaignA), Math.abs(comparison.campaignB)));
  expectEqual(recomputedVariation, comparison.variation, "comparison recomputed variation");

  for (const [id, report] of [["A", reportA], ["B", reportB]]) {
    const definition = campaignDefinitions[id];
    expectEqual(report.schemaVersion, 1, `report ${id} schemaVersion`);
    expectEqual(report.campaignId, id, `report ${id} campaignId`);
    expectArray(report.fixtureOrder, definition.fixtureOrder, `report ${id} fixtureOrder`);
    expectEqual(report.modeOrder, definition.modeOrder, `report ${id} modeOrder`);
    expectEqual(report.environmentId, expected.environmentId, `report ${id} environmentId`);
    expectEqual(report.warmups, 5, `report ${id} warmups`);
    expectEqual(report.measurements, 30, `report ${id} measurements`);
    expectEqual(report.allBudgetsPass, false, `report ${id} allBudgetsPass`);
    expectEqual(report.cleanupPassed, true, `report ${id} cleanupPassed`);
    if (!Array.isArray(report.results)) fail(`report ${id}.results must be an array`);
    const result = unique(report.results, (item) => item.fixtureId === lock.fixtureId
      && item.measurementMode === "observed_cold_start" && item.metric === lock.failureEvidence.metric, `report ${id} canonical result`);
    expectEqual(result.p50, definition.p50, `report ${id} p50`);
    expectEqual(result.baseline, definition.baseline, `report ${id} baseline`);
    expectEqual(result.delta, definition.delta, `report ${id} delta`);
    expectEqual(result.p50Budget, 300, `report ${id} p50Budget`);
    expectEqual(result.pass, false, `report ${id} pass`);
    expectEqual(nearestRankP50(result.samples, `report ${id} samples`), result.p50, `report ${id} recomputed p50`);
    expectEqual(nearestRankP50(result.baselineSamples, `report ${id} baselineSamples`), result.baseline, `report ${id} recomputed baseline p50`);
    expectEqual(result.p50 - result.baseline, result.delta, `report ${id} recomputed delta`);
  }
}

function verifyArtifactRoot(lock, artifactRoot) {
  const resolvedRoot = resolveArtifactRoot(lock, artifactRoot);
  for (const role of ["baseline", "protected"]) {
    const entry = lock.canonicalPair[role];
    const { candidate, stat } = secureArtifactFile(resolvedRoot, entry.relativePath, `${role} APK`);
    expectEqual(stat.size, entry.sizeBytes, `${role} actual size`);
    expectEqual(sha256Bytes(fs.readFileSync(candidate)), entry.sha256, `${role} actual sha256`);
  }
  const failure = lock.failureEvidence;
  const models = {
    manifest: readHashedJson(resolvedRoot, failure.artifactManifestPath, failure.artifactManifestSha256, "artifact manifest"),
    repeatability: readHashedJson(resolvedRoot, failure.repeatabilityPath, failure.repeatabilitySha256, "repeatability report"),
    reportA: readHashedJson(resolvedRoot, failure.campaignAReportPath, failure.campaignAReportSha256, "campaign A report"),
    reportB: readHashedJson(resolvedRoot, failure.campaignBReportPath, failure.campaignBReportSha256, "campaign B report"),
  };
  validateEvidenceModels(lock, models);
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
    ]) if (!text.includes(token)) fail(`${label} missing canonical token ${token}`);
  }
  for (const phrase of ["Rebuilding an original APK from source", "depends_on:\n  - M3-09\n  - M3-11", "private key is absent"]) {
    if (!m310.includes(phrase)) fail(`M3-10 missing fail-closed phrase: ${phrase}`);
  }
  if (!adr.includes("M3-11 canonical artifact lock")) fail("ADR 0016 missing M3-11 canonical lock decision");
  if (!adr.includes("not a stable release baseline")) fail("ADR 0016 must preserve repeatability failure semantics");
}

function syntheticEvidenceModels(lock) {
  const common = {
    headSha: expected.headSha,
    runId: expected.runId,
    jobId: expected.reportJobId,
    runAttempt: 1,
    bootIdHashPrefix: expected.bootIdHashPrefix,
  };
  const campaign = (id) => ({
    id,
    fixtureOrder: [...campaignDefinitions[id].fixtureOrder],
    modeOrder: campaignDefinitions[id].modeOrder,
    reportSha256: campaignDefinitions[id].reportSha256,
  });
  const repeatabilityCampaign = (id) => ({
    ...common,
    ...campaign(id),
    environmentFingerprint: expected.environmentId,
    artifactManifestSha256: expected.artifactManifestSha256,
    warmups: 5,
    measurements: 30,
    allBudgetsPass: false,
    cleanupPassed: true,
  });
  const report = (id) => {
    const definition = campaignDefinitions[id];
    const protectedSamples = Array(30).fill(definition.p50);
    const baselineSamples = Array(30).fill(definition.baseline);
    return {
      schemaVersion: 1,
      campaignId: id,
      fixtureOrder: [...definition.fixtureOrder],
      modeOrder: definition.modeOrder,
      environmentId: expected.environmentId,
      warmups: 5,
      measurements: 30,
      allBudgetsPass: false,
      cleanupPassed: true,
      results: [{
        fixtureId: lock.fixtureId,
        measurementMode: "observed_cold_start",
        metric: lock.failureEvidence.metric,
        samples: protectedSamples,
        baselineSamples,
        p50: definition.p50,
        baseline: definition.baseline,
        delta: definition.delta,
        p50Budget: 300,
        pass: false,
      }],
    };
  };
  return {
    manifest: {
      schemaVersion: 1,
      ...common,
      environmentId: expected.environmentId,
      campaigns: [campaign("A"), campaign("B")],
      artifacts: [
        { artifactId: `${lock.fixtureId}-baseline`, fileName: path.posix.basename(lock.canonicalPair.baseline.relativePath), sha256: lock.canonicalPair.baseline.sha256 },
        { artifactId: `${lock.fixtureId}-protected`, fileName: path.posix.basename(lock.canonicalPair.protected.relativePath), sha256: lock.canonicalPair.protected.sha256 },
        ...Array.from({ length: 4 }, (_, index) => ({ artifactId: `other-${index}`, fileName: `other-${index}.apk`, sha256: "0".repeat(64) })),
      ],
    },
    repeatability: {
      schemaVersion: 1,
      ...common,
      environmentId: expected.environmentId,
      artifactManifestSha256: expected.artifactManifestSha256,
      campaigns: [repeatabilityCampaign("A"), repeatabilityCampaign("B")],
      comparisons: [
        {
          fixtureId: lock.fixtureId,
          metric: lock.failureEvidence.metric,
          statistic: lock.failureEvidence.statistic,
          campaignA: lock.failureEvidence.campaignAValueMs,
          campaignB: lock.failureEvidence.campaignBValueMs,
          variation: lock.failureEvidence.variation,
          limit: lock.failureEvidence.repeatabilityLimit,
          pass: false,
        },
        ...Array.from({ length: 89 }, (_, index) => ({ fixtureId: `other-${index}`, metric: "other", statistic: "other" })),
      ],
      allBudgetsPass: false,
      repeatabilityPass: false,
      cleanupPassed: true,
    },
    reportA: report("A"),
    reportB: report("B"),
  };
}

function runSelfTest(lock) {
  validateEvidenceModels(lock, syntheticEvidenceModels(lock));
  const lockMutations = [
    ["fixture_id", (x) => { x.fixtureId = "kotlin-multidex"; }],
    ["head_sha", (x) => { x.source.headSha = "0".repeat(40); }],
    ["run_id", (x) => { x.source.runId = "31931428131"; }],
    ["job_id", (x) => { x.source.jobId = "95126754769"; }],
    ["artifact_id", (x) => { x.source.artifactId = "9260244216"; }],
    ["baseline_path", (x) => { x.canonicalPair.baseline.relativePath = "replacement.apk"; }],
    ["baseline_size", (x) => { x.canonicalPair.baseline.sizeBytes += 1; }],
    ["protected_hash", (x) => { x.canonicalPair.protected.sha256 = "0".repeat(64); }],
    ["signer_prefix", (x) => { x.canonicalPair.protected.signerCertificateSha256Prefix = "000000000000"; }],
    ["tuple_serialization", (x) => { x.canonicalPair.tupleSerialization += "\n"; }],
    ["tuple_hash", (x) => { x.canonicalPair.productTupleSha256 = "0".repeat(64); }],
    ["manifest_hash", (x) => { x.failureEvidence.artifactManifestSha256 = "0".repeat(64); }],
    ["failure_value", (x) => { x.failureEvidence.campaignAValueMs = 330; }],
    ["repeatability_pass", (x) => { x.failureEvidence.repeatabilityPass = true; }],
    ["artifact_root", (x) => { x.retention.ignoredArtifactRoot = "build/m3-11/substitute"; }],
    ["regeneration", (x) => { x.retention.regenerationPermitted = true; }],
    ["fail_open", (x) => { x.retention.failClosedIfExactBytesUnavailable = false; }],
    ["extra_key", (x) => { x.source.unreviewed = true; }],
  ];
  for (const [name, mutate] of lockMutations) {
    const candidate = structuredClone(lock);
    mutate(candidate);
    let rejected = false;
    try { validateLock(candidate); } catch { rejected = true; }
    if (!rejected) fail(`lock mutation was accepted: ${name}`);
  }

  const evidenceMutations = [
    ["manifest_wrong_fixture_mapping", (x) => { x.manifest.artifacts[0].artifactId = "kotlin-multidex-baseline"; }],
    ["manifest_wrong_apk_hash", (x) => { x.manifest.artifacts[1].sha256 = "0".repeat(64); }],
    ["repeatability_wrong_report_hash", (x) => { x.repeatability.campaigns[0].reportSha256 = "0".repeat(64); }],
    ["repeatability_wrong_delta_a", (x) => { x.repeatability.comparisons[0].campaignA = 330; }],
    ["repeatability_pass_true", (x) => { x.repeatability.repeatabilityPass = true; }],
    ["campaign_report_wrong_fixture", (x) => { x.reportA.results[0].fixtureId = "kotlin-multidex"; }],
    ["campaign_report_wrong_p50", (x) => { x.reportB.results[0].p50 = 505; }],
  ];
  for (const [name, mutate] of evidenceMutations) {
    const candidate = syntheticEvidenceModels(lock);
    mutate(candidate);
    let rejected = false;
    try { validateEvidenceModels(lock, candidate); } catch { rejected = true; }
    if (!rejected) fail(`evidence mutation was accepted: ${name}`);
  }
  let externalRootRejected = false;
  try { resolveArtifactRoot(lock, path.join("..", "m3-11-substitute")); } catch { externalRootRejected = true; }
  if (!externalRootRejected) fail("path mutation was accepted: external_artifact_root");
  return lockMutations.length + evidenceMutations.length + 1;
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
    "docs/adr/0015-startup-performance-measurement-stability.md",
    "docs/adr/0016-end-to-end-startup-attribution-boundary.md",
    "docs/tasks/INDEX.md",
    "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
    "docs/tasks/M3-09-startup-attribution-boundary-contract.md",
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
    fixtureId: lock.fixtureId,
    productTupleSha256: lock.canonicalPair.productTupleSha256,
    actualBytesVerified: Boolean(args.artifactRoot),
    mutationCount: mutations,
    changedFileCount,
  }));
} catch (error) {
  console.error(`M3-11 canonical artifact contract validation failed: ${error.message}`);
  process.exit(1);
}
