#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const argv = process.argv.slice(2);
let selfTest = false;
let baseRef = null;
let reportFile = null;
for (let index = 0; index < argv.length; index += 1) {
  const argument = argv[index];
  if (argument === "--self-test") {
    selfTest = true;
    continue;
  }
  if (argument === "--base-ref" || argument === "--report") {
    if (index + 1 >= argv.length || argv[index + 1].startsWith("--")) {
      fail(`${argument} requires a value`);
    }
    const value = argv[index + 1];
    if (argument === "--base-ref") baseRef = value;
    else reportFile = value;
    index += 1;
    continue;
  }
  fail(`unknown argument: ${argument}`);
}

const OUTER = Array.from({ length: 16 }, (_, index) => `p${index}`);
const INNER = ["p0", "h0", "h1", "h2", "h3", "h4", "h5", "h6", "h7", "h8", "p1"];
const OWNERS = [
  "RUNTIME_BOOTSTRAP", "PRE_APPLICATION_RESIDUAL",
  "P1_P2", "P2_P3", "P3_P4", "P4_P5", "P5_P6", "P6_P7", "P7_P8",
];
const COMMON_PROBES = Array.from({ length: 15 }, (_, index) => `p${index + 1}`);
const PROTECTED_PROBES = Array.from({ length: 9 }, (_, index) => `h${index}`);
const WORKFLOW_PATH = ".github/workflows/m3-09-startup-attribution.yml";
const EVIDENCE_WORKFLOW_PATH = ".github/workflows/m3-09-startup-attribution-evidence.yml";
const TASK_KEY = "M3-09-DIAGNOSTIC-V1";
const THRESHOLDS = Object.freeze({
  applicationBudgetMs: 300,
  minimumContributionMs: 30,
  maximumVariation: 0.10,
  minimumDeltaShare: 0.50,
  maximumProbeOverheadNs: 5_000_000,
});

const documents = readDocumentBundle();
validateDocuments(documents);
if (reportFile) validateReport(JSON.parse(fs.readFileSync(path.resolve(reportFile), "utf8")));
if (baseRef) validateDiff(baseRef);
let mutationNames = [];
if (selfTest) mutationNames = runSelfTests(documents);

console.log(`OK: M3-09 startup attribution contract${selfTest ? `; ${mutationNames.length} named mutations rejected` : ""}`);
if (selfTest) console.log(`MUTATIONS: ${mutationNames.join(",")}`);

function validateReport(report) {
  object(report, "report");
  equal(report.contractModelOnly, true, "contractModelOnly");
  equal(report.realEvidenceAccepted, false, "realEvidenceAccepted");
  equal(report.evidencePhase, "post_diagnostic_governance", "evidencePhase");
  equal(report.evidenceWorkflowPath, EVIDENCE_WORKFLOW_PATH, "evidenceWorkflowPath");
  equal(report.diagnosticCompletedBeforeEvidence, true, "diagnosticCompletedBeforeEvidence");
  equal(report.schemaVersion, 1, "schemaVersion");
  equal(report.taskKey, TASK_KEY, "taskKey");
  equal(report.workflowPath, WORKFLOW_PATH, "workflowPath");
  hex(report.headSha, 40, "headSha");
  decimal(report.runId, "runId");
  decimal(report.jobId, "jobId");
  equal(report.runAttempt, 1, "runAttempt");
  hex(report.bootIdHashPrefix, 12, "bootIdHashPrefix");
  equal(report.clock, "CLOCK_BOOTTIME", "clock");
  equal(report.fixtureId, "kotlin-multidex", "fixtureId");
  equal(report.androidApi, 36, "androidApi");
  equal(report.imageRevision, 2, "imageRevision");
  equal(report.emulatorVersion, "37.1.11", "emulatorVersion");
  equal(report.m210RunId, "32099991400", "m210RunId");
  equal(report.m210Reopened, false, "m210Reopened");
  equal(report.cleanupPassed, true, "cleanupPassed");
  deepEqual(report.thresholds, THRESHOLDS, "thresholds");

  validateProfile(report.profile);
  const expectedTuple = sha256(`${report.profile.baselineOriginalApkSha256}:${report.profile.protectedOriginalApkSha256}`);
  equal(report.productTupleSha256, expectedTuple, "productTupleSha256");
  validateRunEnumeration(report.runEnumeration, report, expectedTuple);

  if (!Array.isArray(report.campaigns) || report.campaigns.length !== 2) fail("campaigns must contain A and B");
  const expectedCampaigns = [["A", "baseline_then_protected"], ["B", "protected_then_baseline"]];
  const computed = {};
  for (let campaignIndex = 0; campaignIndex < 2; campaignIndex += 1) {
    computed[expectedCampaigns[campaignIndex][0]] = validateCampaign(
      report.campaigns[campaignIndex], ...expectedCampaigns[campaignIndex],
    );
  }

  const eligible = OWNERS.filter((owner) => {
    const a = computed.A.ownerP50Ns[owner];
    const b = computed.B.ownerP50Ns[owner];
    const positive = a >= THRESHOLDS.minimumContributionMs * 1_000_000
      && b >= THRESHOLDS.minimumContributionMs * 1_000_000;
    const variation = Math.abs(a - b) / Math.max(1, Math.min(Math.abs(a), Math.abs(b)));
    const shareA = a / computed.A.totalDeltaP50Ns;
    const shareB = b / computed.B.totalDeltaP50Ns;
    return positive && variation <= THRESHOLDS.maximumVariation
      && shareA >= THRESHOLDS.minimumDeltaShare && shareB >= THRESHOLDS.minimumDeltaShare;
  });
  const reproduced = computed.A.totalDeltaP50Ns > THRESHOLDS.applicationBudgetMs * 1_000_000
    && computed.B.totalDeltaP50Ns > THRESHOLDS.applicationBudgetMs * 1_000_000;
  const selected = reproduced && eligible.length === 1 ? eligible[0] : "UNATTRIBUTED";
  deepEqual(report.eligibleOwners, eligible, "eligibleOwners");
  equal(report.selectedOwner, selected, "selectedOwner");
  if (report.selectedOwner !== "UNATTRIBUTED" && !OWNERS.includes(report.selectedOwner)) fail("unknown selected owner");
  if (selected === "UNATTRIBUTED") fail("diagnostic attribution unresolved");
  return { campaigns: computed, eligible, selected };
}

function validateCampaign(campaign, expectedId, expectedModeOrder) {
  object(campaign, `campaign ${expectedId}`);
  equal(campaign.id, expectedId, "campaign id");
  equal(campaign.modeOrder, expectedModeOrder, "campaign modeOrder");
  equal(campaign.warmups, 5, "warmups");
  equal(campaign.measurements, 15, "measurements");
  if (!Array.isArray(campaign.samples) || campaign.samples.length !== 15) fail("campaign sample count");
  equal(campaign.rawSamplesSha256, sha256(JSON.stringify(campaign.samples)), "rawSamplesSha256");

  const ownerValues = Object.fromEntries(OWNERS.map((owner) => [owner, []]));
  const totalValues = [];
  const seen = new Set();
  for (let index = 0; index < campaign.samples.length; index += 1) {
    const sample = campaign.samples[index];
    equal(sample.ordinal, index + 1, "sample ordinal");
    if (seen.has(sample.ordinal)) fail("duplicate sample ordinal");
    seen.add(sample.ordinal);
    hex(sample.traceSha256, 64, "traceSha256");
    const baseline = validateTimeline(sample.baseline, false);
    const protectedTimeline = validateTimeline(sample.protected, true);
    const vector = ownerVector(baseline, protectedTimeline);
    const total = duration(protectedTimeline.outer, 0, 8) - duration(baseline.outer, 0, 8);
    const vectorSum = OWNERS.reduce((sum, owner) => sum + vector[owner], 0);
    equal(vectorSum, total, "per-ordinal owner reconciliation");
    deepEqual(sample.ownerContributionsNs, vector, "ownerContributionsNs");
    equal(sample.totalDeltaNs, total, "totalDeltaNs");
    totalValues.push(total);
    for (const owner of OWNERS) ownerValues[owner].push(vector[owner]);
  }

  const ownerP50Ns = Object.fromEntries(OWNERS.map((owner) => [owner, nearestRankP50(ownerValues[owner])]));
  const totalDeltaP50Ns = nearestRankP50(totalValues);
  deepEqual(campaign.ownerP50Ns, ownerP50Ns, "ownerP50Ns");
  equal(campaign.totalDeltaP50Ns, totalDeltaP50Ns, "totalDeltaP50Ns");
  return { ownerP50Ns, totalDeltaP50Ns };
}

function validateTimeline(value, protectedMode) {
  object(value, protectedMode ? "protected timeline" : "baseline timeline");
  equal(value.clock, "CLOCK_BOOTTIME", "timeline clock");
  deepEqual(Object.keys(value.outer), OUTER, "outer checkpoints");
  monotonic(OUTER.map((name) => value.outer[name]), "outer timestamps");
  if (protectedMode) {
    deepEqual(Object.keys(value.inner), INNER, "inner checkpoints");
    monotonic(INNER.map((name) => value.inner[name]), "inner timestamps");
    equal(value.inner.p0, value.outer.p0, "inner p0 binding");
    equal(value.inner.p1, value.outer.p1, "inner p1 binding");
    let sum = 0;
    for (let index = 0; index < INNER.length - 1; index += 1) {
      sum += value.inner[INNER[index + 1]] - value.inner[INNER[index]];
    }
    equal(sum, value.outer.p1 - value.outer.p0, "inner adjacency sum");
  } else {
    equal(value.inner, null, "baseline inner must be null");
  }
  let outerSum = 0;
  for (let index = 0; index < OUTER.length - 1; index += 1) {
    outerSum += value.outer[OUTER[index + 1]] - value.outer[OUTER[index]];
  }
  equal(outerSum, value.outer.p15 - value.outer.p0, "outer adjacency sum");
  return value;
}

function ownerVector(baseline, protectedTimeline) {
  const vector = {
    RUNTIME_BOOTSTRAP: protectedTimeline.inner.h8 - protectedTimeline.inner.h0,
    PRE_APPLICATION_RESIDUAL:
      (protectedTimeline.inner.h0 - protectedTimeline.inner.p0)
      + (protectedTimeline.inner.p1 - protectedTimeline.inner.h8)
      - (baseline.outer.p1 - baseline.outer.p0),
  };
  for (let index = 1; index < 8; index += 1) {
    vector[`P${index}_P${index + 1}`] = duration(protectedTimeline.outer, index, index + 1)
      - duration(baseline.outer, index, index + 1);
  }
  return vector;
}

function validateProfile(profile) {
  object(profile, "profile");
  equal(profile.generationMode, "deterministic_post_build_probe_manifest", "profile generationMode");
  for (const field of [
    "baselineOriginalApkSha256", "baselineInstrumentedApkSha256",
    "protectedOriginalApkSha256", "protectedInstrumentedApkSha256", "structuralDiffManifestSha256",
  ]) hex(profile[field], 64, field);
  if (profile.baselineOriginalApkSha256 === profile.baselineInstrumentedApkSha256
      || profile.protectedOriginalApkSha256 === profile.protectedInstrumentedApkSha256) {
    fail("instrumented APK must be distinct and bound");
  }
  deepEqual(profile.commonProbeLocations, COMMON_PROBES, "common probe locations");
  deepEqual(profile.protectedProbeLocations, PROTECTED_PROBES, "protected probe locations");
  equal(profile.maximumProtectedProbeCount, 24, "maximumProtectedProbeCount");
  equal(profile.baselineSyntheticFactory, false, "baselineSyntheticFactory");
  equal(profile.manifestResourcesNativeSecurityEquivalent, true, "profile structural equivalence");
  equal(profile.nonProbeInstructionsEquivalent, true, "non-probe instruction equivalence");
  equal(profile.securityLifecycleEventsEquivalent, true, "security/lifecycle equivalence");
  equal(profile.releaseArtifactsClean, true, "Release artifact cleanliness");
  object(profile.calibrationSamplesNs, "calibrationSamplesNs");
  object(profile.calibrationP95Ns, "calibrationP95Ns");
  object(profile.aggregateProbeOverheadNs, "aggregateProbeOverheadNs");
  for (const campaign of ["A", "B"]) {
    const samples = profile.calibrationSamplesNs[campaign];
    if (!Array.isArray(samples) || samples.length !== 15) fail(`calibration ${campaign} sample count`);
    for (const sample of samples) integer(sample, `calibration ${campaign} sample`);
    const p95 = nearestRank(samples, 0.95);
    equal(profile.calibrationP95Ns[campaign], p95, `calibration ${campaign} P95`);
    const aggregate = p95 * profile.maximumProtectedProbeCount;
    equal(profile.aggregateProbeOverheadNs[campaign], aggregate, `calibration ${campaign} aggregate`);
    if (aggregate > THRESHOLDS.maximumProbeOverheadNs) fail(`profile probe overhead exceeds 5 ms in ${campaign}`);
  }
}

function validateRunEnumeration(enumeration, report, tuple) {
  object(enumeration, "runEnumeration");
  equal(enumeration.complete, true, "run enumeration completeness");
  equal(enumeration.includesFailedCancelledAndNoArtifact, true, "run enumeration scope");
  if (!Array.isArray(enumeration.allRuns) || enumeration.allRuns.length < 1) fail("run enumeration must include allRuns");
  integer(enumeration.apiTotalCount, "run enumeration apiTotalCount");
  equal(enumeration.apiTotalCount, enumeration.allRuns.length, "run enumeration total count");
  equal(enumeration.queryResponseSha256, sha256(JSON.stringify(enumeration.allRuns)), "queryResponseSha256");
  for (const [index, run] of enumeration.allRuns.entries()) {
    object(run, `enumerated allRuns[${index}]`);
    equal(run.workflowPath, WORKFLOW_PATH, "allRuns workflowPath");
    hex(run.headSha, 40, "allRuns headSha");
    hex(run.productTupleSha256, 64, "allRuns product tuple");
    decimal(run.runId, "allRuns runId");
    decimal(run.jobId, "allRuns jobId");
    integer(run.runAttempt, "allRuns runAttempt");
    if (!["success", "failure", "cancelled"].includes(run.conclusion)) fail("allRuns conclusion");
    if (typeof run.artifactPresent !== "boolean") fail("allRuns artifactPresent");
  }
  const filtered = enumeration.allRuns.filter((run) => run.workflowPath === WORKFLOW_PATH
    && run.headSha === report.headSha && run.productTupleSha256 === tuple);
  if (!Array.isArray(enumeration.matchingRuns) || enumeration.matchingRuns.length !== 1) {
    fail("first-and-only matching run count");
  }
  deepEqual(enumeration.matchingRuns, filtered, "matchingRuns filter result");
  equal(enumeration.matchingRunsSha256, sha256(JSON.stringify(enumeration.matchingRuns)), "matchingRunsSha256");
  const run = enumeration.matchingRuns[0];
  equal(run.workflowPath, WORKFLOW_PATH, "enumerated workflowPath");
  equal(run.headSha, report.headSha, "enumerated headSha");
  equal(run.productTupleSha256, tuple, "enumerated product tuple");
  equal(run.runId, report.runId, "enumerated runId");
  equal(run.jobId, report.jobId, "enumerated jobId");
  equal(run.runAttempt, 1, "enumerated runAttempt");
  equal(run.conclusion, "success", "enumerated conclusion");
  equal(run.artifactPresent, true, "enumerated artifact presence");
}

function buildSyntheticReport() {
  const headSha = "1".repeat(40);
  const baselineOriginal = "2".repeat(64);
  const protectedOriginal = "3".repeat(64);
  const tuple = sha256(`${baselineOriginal}:${protectedOriginal}`);
  const report = {
    schemaVersion: 1,
    contractModelOnly: true,
    realEvidenceAccepted: false,
    evidencePhase: "post_diagnostic_governance",
    evidenceWorkflowPath: EVIDENCE_WORKFLOW_PATH,
    diagnosticCompletedBeforeEvidence: true,
    taskKey: TASK_KEY,
    workflowPath: WORKFLOW_PATH,
    headSha,
    runId: "9001",
    jobId: "9002",
    runAttempt: 1,
    bootIdHashPrefix: "abcdef123456",
    clock: "CLOCK_BOOTTIME",
    fixtureId: "kotlin-multidex",
    androidApi: 36,
    imageRevision: 2,
    emulatorVersion: "37.1.11",
    m210RunId: "32099991400",
    m210Reopened: false,
    thresholds: { ...THRESHOLDS },
    profile: {
      generationMode: "deterministic_post_build_probe_manifest",
      baselineOriginalApkSha256: baselineOriginal,
      baselineInstrumentedApkSha256: "4".repeat(64),
      protectedOriginalApkSha256: protectedOriginal,
      protectedInstrumentedApkSha256: "5".repeat(64),
      structuralDiffManifestSha256: "6".repeat(64),
      commonProbeLocations: [...COMMON_PROBES],
      protectedProbeLocations: [...PROTECTED_PROBES],
      maximumProtectedProbeCount: 24,
      baselineSyntheticFactory: false,
      manifestResourcesNativeSecurityEquivalent: true,
      nonProbeInstructionsEquivalent: true,
      securityLifecycleEventsEquivalent: true,
      releaseArtifactsClean: true,
      calibrationSamplesNs: { A: Array(15).fill(100_000), B: Array(15).fill(100_000) },
      calibrationP95Ns: { A: 100_000, B: 100_000 },
      aggregateProbeOverheadNs: { A: 2_400_000, B: 2_400_000 },
    },
    productTupleSha256: tuple,
    runEnumeration: {
      complete: true,
      includesFailedCancelledAndNoArtifact: true,
      apiTotalCount: 2,
      allRuns: [
        { workflowPath: WORKFLOW_PATH, headSha: "9".repeat(40), productTupleSha256: "8".repeat(64), runId: "8999", jobId: "8998", runAttempt: 1, conclusion: "failure", artifactPresent: false },
        { workflowPath: WORKFLOW_PATH, headSha, productTupleSha256: tuple, runId: "9001", jobId: "9002", runAttempt: 1, conclusion: "success", artifactPresent: true },
      ],
    },
    campaigns: [makeCampaign("A", "baseline_then_protected", 0), makeCampaign("B", "protected_then_baseline", 1)],
    eligibleOwners: ["RUNTIME_BOOTSTRAP"],
    selectedOwner: "RUNTIME_BOOTSTRAP",
    cleanupPassed: true,
  };
  report.runEnumeration.queryResponseSha256 = sha256(JSON.stringify(report.runEnumeration.allRuns));
  report.runEnumeration.matchingRuns = report.runEnumeration.allRuns.filter((run) => run.headSha === headSha
    && run.productTupleSha256 === tuple);
  report.runEnumeration.matchingRunsSha256 = sha256(JSON.stringify(report.runEnumeration.matchingRuns));
  return report;
}

function makeCampaign(id, modeOrder, campaignOffset) {
  const samples = [];
  for (let ordinal = 1; ordinal <= 15; ordinal += 1) {
    const start = 10_000_000_000 + campaignOffset * 100_000_000_000 + ordinal * 1_000_000_000;
    const baselineDurations = [100, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10].map(ms);
    const protectedDurations = [350, 20, 20, 20, 20, 20, 20, 20, 10, 10, 10, 10, 10, 10, 10].map(ms);
    const baselineOuter = timelineFromDurations(start, baselineDurations, OUTER);
    const protectedOuter = timelineFromDurations(start + 500_000_000, protectedDurations, OUTER);
    const innerDurations = [50, 5, 5, 5, 5, 5, 5, 5, 205, 60].map(ms);
    const protectedInner = timelineFromDurations(protectedOuter.p0, innerDurations, INNER);
    const sample = {
      ordinal,
      traceSha256: sha256(`${id}:${ordinal}:trace`),
      baseline: { clock: "CLOCK_BOOTTIME", outer: baselineOuter, inner: null },
      protected: { clock: "CLOCK_BOOTTIME", outer: protectedOuter, inner: protectedInner },
    };
    sample.ownerContributionsNs = ownerVector(sample.baseline, sample.protected);
    sample.totalDeltaNs = duration(sample.protected.outer, 0, 8) - duration(sample.baseline.outer, 0, 8);
    samples.push(sample);
  }
  const ownerP50Ns = Object.fromEntries(OWNERS.map((owner) => [owner, nearestRankP50(samples.map(s => s.ownerContributionsNs[owner]))]));
  return {
    id, modeOrder, warmups: 5, measurements: 15, samples,
    rawSamplesSha256: sha256(JSON.stringify(samples)),
    ownerP50Ns,
    totalDeltaP50Ns: nearestRankP50(samples.map(s => s.totalDeltaNs)),
  };
}

function refreshCampaign(campaign) {
  for (const sample of campaign.samples) {
    sample.ownerContributionsNs = ownerVector(sample.baseline, sample.protected);
    sample.totalDeltaNs = duration(sample.protected.outer, 0, 8) - duration(sample.baseline.outer, 0, 8);
  }
  campaign.rawSamplesSha256 = sha256(JSON.stringify(campaign.samples));
  campaign.ownerP50Ns = Object.fromEntries(OWNERS.map((owner) => [
    owner, nearestRankP50(campaign.samples.map((sample) => sample.ownerContributionsNs[owner])),
  ]));
  campaign.totalDeltaP50Ns = nearestRankP50(campaign.samples.map((sample) => sample.totalDeltaNs));
}

function refreshEnumeration(report) {
  report.runEnumeration.queryResponseSha256 = sha256(JSON.stringify(report.runEnumeration.allRuns));
  report.runEnumeration.matchingRuns = report.runEnumeration.allRuns.filter((run) => run.workflowPath === WORKFLOW_PATH
    && run.headSha === report.headSha && run.productTupleSha256 === report.productTupleSha256);
  report.runEnumeration.matchingRunsSha256 = sha256(JSON.stringify(report.runEnumeration.matchingRuns));
}

function runSelfTests(documentBundle) {
  const valid = buildSyntheticReport();
  validateReport(structuredClone(valid));
  const mutations = [
    ["missing_outer_checkpoint", r => { delete r.campaigns[0].samples[0].protected.outer.p4; }],
    ["reordered_outer_checkpoint", r => { r.campaigns[0].samples[0].protected.outer = reorder(r.campaigns[0].samples[0].protected.outer, 2, 3); }],
    ["non_monotonic_outer", r => { r.campaigns[0].samples[0].protected.outer.p5 = r.campaigns[0].samples[0].protected.outer.p4 - 1; }],
    ["missing_inner_checkpoint", r => { delete r.campaigns[0].samples[0].protected.inner.h4; }],
    ["reordered_inner_checkpoint", r => { r.campaigns[0].samples[0].protected.inner = reorder(r.campaigns[0].samples[0].protected.inner, 3, 4); }],
    ["inner_endpoint_mismatch", r => { r.campaigns[0].samples[0].protected.inner.p1 += 1; }],
    ["cross_clock", r => { r.campaigns[0].samples[0].baseline.clock = "wall_clock"; }],
    ["sample_deleted", r => { r.campaigns[0].samples.pop(); }],
    ["sample_duplicated", r => { r.campaigns[0].samples[1] = structuredClone(r.campaigns[0].samples[0]); }],
    ["sample_reordered", r => { [r.campaigns[0].samples[0], r.campaigns[0].samples[1]] = [r.campaigns[0].samples[1], r.campaigns[0].samples[0]]; }],
    ["raw_hash_wrong", r => { r.campaigns[0].rawSamplesSha256 = "0".repeat(64); }],
    ["owner_vector_wrong", r => { r.campaigns[0].samples[0].ownerContributionsNs.RUNTIME_BOOTSTRAP += 1; }],
    ["owner_summary_wrong", r => { r.campaigns[0].ownerP50Ns.RUNTIME_BOOTSTRAP += 1; }],
    ["total_summary_wrong", r => { r.campaigns[0].totalDeltaP50Ns += 1; }],
    ["selected_owner_wrong", r => { r.selectedOwner = "P1_P2"; }],
    ["eligible_owner_wrong", r => { r.eligibleOwners = []; }],
    ["warmups_four", r => { r.campaigns[0].warmups = 4; }],
    ["warmups_six", r => { r.campaigns[0].warmups = 6; }],
    ["samples_fourteen", r => { r.campaigns[0].samples.pop(); r.campaigns[0].measurements = 14; }],
    ["samples_sixteen", r => {
      const extra = structuredClone(r.campaigns[0].samples[14]);
      extra.ordinal = 16;
      extra.traceSha256 = sha256("A:16:trace");
      r.campaigns[0].samples.push(extra);
      r.campaigns[0].measurements = 16;
    }],
    ["fixture_changed", r => { r.fixtureId = "simple-java"; }],
    ["api_changed", r => { r.androidApi = 35; }],
    ["image_changed", r => { r.imageRevision = 3; }],
    ["emulator_changed", r => { r.emulatorVersion = "37.1.12"; }],
    ["campaign_order_changed", r => { [r.campaigns[0], r.campaigns[1]] = [r.campaigns[1], r.campaigns[0]]; }],
    ["threshold_30_changed", r => { r.thresholds.minimumContributionMs = 29; }],
    ["threshold_10_changed", r => { r.thresholds.maximumVariation = 0.11; }],
    ["threshold_50_changed", r => { r.thresholds.minimumDeltaShare = 0.49; }],
    ["budget_300_changed", r => { r.thresholds.applicationBudgetMs = 301; }],
    ["run_attempt_two", r => { r.runAttempt = 2; }],
    ["workflow_path_changed", r => { r.workflowPath = ".github/workflows/other.yml"; }],
    ["real_evidence_claim", r => { r.contractModelOnly = false; r.realEvidenceAccepted = true; }],
    ["same_phase_evidence", r => { r.diagnosticCompletedBeforeEvidence = false; }],
    ["evidence_workflow_changed", r => { r.evidenceWorkflowPath = ".github/workflows/other-evidence.yml"; }],
    ["multiple_matching_runs", r => { r.runEnumeration.allRuns.push({ ...structuredClone(r.runEnumeration.matchingRuns[0]), runId: "9003", jobId: "9004" }); refreshEnumeration(r); }],
    ["enumerated_run_changed", r => { r.runEnumeration.allRuns[1].runId = "9999"; refreshEnumeration(r); }],
    ["enumeration_incomplete", r => { r.runEnumeration.complete = false; }],
    ["enumeration_history_omitted", r => { r.runEnumeration.allRuns.shift(); refreshEnumeration(r); }],
    ["job_id_missing", r => { delete r.jobId; }],
    ["boot_id_missing", r => { delete r.bootIdHashPrefix; }],
    ["enumeration_hash_missing", r => { delete r.runEnumeration.queryResponseSha256; }],
    ["product_tuple_changed", r => { r.productTupleSha256 = "8".repeat(64); }],
    ["baseline_synthetic_factory", r => { r.profile.baselineSyntheticFactory = true; }],
    ["profile_location_changed", r => { r.profile.commonProbeLocations[0] = "p0"; }],
    ["profile_count_changed", r => { r.profile.maximumProtectedProbeCount = 23; }],
    ["profile_diff_failed", r => { r.profile.nonProbeInstructionsEquivalent = false; }],
    ["security_events_changed", r => { r.profile.securityLifecycleEventsEquivalent = false; }],
    ["probe_overhead_A_exceeded", r => {
      r.profile.calibrationSamplesNs.A = Array(15).fill(300_000);
      r.profile.calibrationP95Ns.A = 300_000;
      r.profile.aggregateProbeOverheadNs.A = 7_200_000;
    }],
    ["probe_overhead_B_exceeded", r => {
      r.profile.calibrationSamplesNs.B = Array(15).fill(300_000);
      r.profile.calibrationP95Ns.B = 300_000;
      r.profile.aggregateProbeOverheadNs.B = 7_200_000;
    }],
    ["release_artifact_polluted", r => { r.profile.releaseArtifactsClean = false; }],
    ["m210_reopened", r => { r.m210Reopened = true; }],
    ["cleanup_failed", r => { r.cleanupPassed = false; }],
    ["zero_eligible_owners", r => {
      for (const campaign of r.campaigns) {
        for (const sample of campaign.samples) sample.protected.inner.h8 = sample.protected.inner.p0 + ms(175);
        refreshCampaign(campaign);
      }
      r.eligibleOwners = [];
      r.selectedOwner = "UNATTRIBUTED";
    }],
    ["multiple_eligible_owners", r => {
      for (const campaign of r.campaigns) {
        for (const sample of campaign.samples) {
          sample.baseline.outer = timelineFromDurations(sample.baseline.outer.p0,
            [100, 20, 20, 20, 20, 20, 20, 20, 10, 10, 10, 10, 10, 10, 10].map(ms), OUTER);
          const protectedDurations = [500, 8, 7, 7, 7, 7, 7, 7, 10, 10, 10, 10, 10, 10, 10].map(ms);
          sample.protected.outer = timelineFromDurations(sample.protected.outer.p0, protectedDurations, OUTER);
          sample.protected.inner.p1 = sample.protected.outer.p1;
          sample.protected.inner.h8 = sample.protected.inner.p0 + ms(250);
        }
        refreshCampaign(campaign);
        equal(campaign.totalDeltaP50Ns, ms(310), "multiple owner mutation total");
        equal(campaign.ownerP50Ns.RUNTIME_BOOTSTRAP, ms(200), "multiple owner mutation Runtime");
        equal(campaign.ownerP50Ns.PRE_APPLICATION_RESIDUAL, ms(200), "multiple owner mutation residual");
      }
      r.eligibleOwners = ["RUNTIME_BOOTSTRAP", "PRE_APPLICATION_RESIDUAL"];
      r.selectedOwner = "UNATTRIBUTED";
    }],
  ];
  const names = [];
  for (const [name, mutate] of mutations) {
    const candidate = structuredClone(valid);
    mutate(candidate);
    if (name !== "raw_hash_wrong") {
      for (const campaign of candidate.campaigns ?? []) {
        if (Array.isArray(campaign.samples)) campaign.rawSamplesSha256 = sha256(JSON.stringify(campaign.samples));
      }
    }
    expectRejected(() => validateReport(candidate), name);
    names.push(name);
  }

  const documentMutations = [
    ["m305_dependency_removed", bundle => { bundle.index = bundle.index.replace(", M3-09 |", " |"); }],
    ["m305_budget_weakened", bundle => { bundle.m305 = bundle.m305.replaceAll("300 ms", "301 ms"); }],
    ["old_run_retry_wording", bundle => { bundle.adr = bundle.adr.replace("cannot be replaced", "may be replaced"); }],
  ];
  for (const [name, mutate] of documentMutations) {
    const candidate = structuredClone(documentBundle);
    mutate(candidate);
    expectRejected(() => validateDocuments(candidate), name);
    names.push(name);
  }
  if (isAllowedGovernanceFile("runtime/bootstrap/src/main/java/Probe.java")) fail("production diff mutation accepted");
  names.push("production_diff");
  return names;
}

function readDocumentBundle() {
  return {
    adr: read("docs/adr/0016-end-to-end-startup-attribution-boundary.md"),
    task: read("docs/tasks/M3-09-startup-attribution-boundary-contract.md"),
    strategy: read("docs/TEST_STRATEGY.md"),
    m305: read("docs/tasks/M3-05-size-startup-memory-benchmarks.md"),
    index: read("docs/tasks/INDEX.md"),
    roadmap: read("docs/ROADMAP.md"),
    plan: read("docs/PROJECT_PLAN.md"),
    handoff: read("HandOff.md"),
  };
}

function validateDocuments(bundle) {
  requirePhrases(bundle.adr, [
    "p0..p15", "p0,h0,h1,h2,h3,h4,h5,h6,h7,h8,p1", "RUNTIME_BOOTSTRAP",
    "PRE_APPLICATION_RESIDUAL", "sum exactly", "ordinal `1..15`", "nearest-rank P50",
    "abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10", "Exactly one owner",
    WORKFLOW_PATH, EVIDENCE_WORKFLOW_PATH, TASK_KEY, "runAttempt", "5,000,000", "cannot be replaced", "UNATTRIBUTED",
  ], "ADR 0016");
  requirePhrases(bundle.task, [
    "p0..p15", "nine signed owner contributions", "300 ms", "30 ms", "10%", "50%",
    WORKFLOW_PATH, EVIDENCE_WORKFLOW_PATH, TASK_KEY, "runAttempt=1", "5 ms", "Issue #68",
  ], "M3-09 task");
  requirePhrases(bundle.strategy, ["ADR 0016", "p0..p15", "h0..h8", "UNATTRIBUTED", "32099991400"], "TEST_STRATEGY");
  requirePhrases(bundle.m305, ["M3-09", "ADR 0016", "PR #63 保持阻塞", "300 ms"], "M3-05 task");
  requirePhrases(bundle.index, ["| M3-09 | [#68]", "M3-08 → M3-09 → M3-05", ", M3-09 |"], "task index");
  requirePhrases(bundle.roadmap, ["| M3-09 |", "M3-08, M3-09"], "roadmap");
  requirePhrases(bundle.plan, ["M3-09：端到端启动性能归因边界合同"], "project plan");
  requirePhrases(bundle.handoff, ["M3-09 is the active governance prerequisite", "M3-05 PR #63 remains blocked"], "HandOff");
}

function validateDiff(base) {
  const result = spawnSync("git", ["diff", "--name-only", `${base}...HEAD`], { cwd: root, encoding: "utf8", timeout: 30_000 });
  if (result.error || result.status !== 0) fail(`git diff failed: ${result.stderr || result.error?.message}`);
  for (const file of result.stdout.split(/\r?\n/).filter(Boolean).map(normalize)) {
    if (!isAllowedGovernanceFile(file)) fail(`M3-09 contains implementation change: ${file}`);
  }
}

function isAllowedGovernanceFile(file) {
  return file === "HandOff.md" || file === "README.md" || file === ".github/workflows/governance.yml"
    || file === "tools/governance/validate-project-package.mjs"
    || file === "tools/governance/verify-m3-09-startup-attribution-contract.mjs"
    || file.startsWith("docs/");
}

function timelineFromDurations(start, durations, names) {
  const result = { [names[0]]: start };
  for (let index = 0; index < durations.length; index += 1) result[names[index + 1]] = result[names[index]] + durations[index];
  return result;
}
function duration(outer, start, end) { return outer[`p${end}`] - outer[`p${start}`]; }
function nearestRankP50(values) { return [...values].sort((a, b) => a - b)[7]; }
function nearestRank(values, quantile) { return [...values].sort((a, b) => a - b)[Math.ceil(quantile * values.length) - 1]; }
function ms(value) { return value * 1_000_000; }
function sha256(value) { return createHash("sha256").update(value).digest("hex"); }
function reorder(objectValue, left, right) {
  const entries = Object.entries(objectValue);
  [entries[left], entries[right]] = [entries[right], entries[left]];
  return Object.fromEntries(entries);
}
function monotonic(values, label) {
  for (let index = 0; index < values.length; index += 1) {
    integer(values[index], label);
    if (index > 0 && values[index] < values[index - 1]) fail(`${label} not monotonic`);
  }
}
function read(relative) {
  const text = fs.readFileSync(path.join(root, relative), "utf8");
  if (text.includes("\uFFFD")) fail(`${relative} contains replacement character`);
  return text;
}
function requirePhrases(text, phrases, label) { for (const phrase of phrases) if (!text.includes(phrase)) fail(`${label} missing phrase: ${phrase}`); }
function expectRejected(callback, name) { let rejected = false; try { callback(); } catch { rejected = true; } if (!rejected) fail(`mutation accepted: ${name}`); }
function object(value, label) { if (!value || typeof value !== "object" || Array.isArray(value)) fail(`${label} must be object`); }
function integer(value, label) { if (!Number.isSafeInteger(value) || value < 0) fail(`${label} must be non-negative safe integer`); }
function decimal(value, label) { if (typeof value !== "string" || !/^[1-9][0-9]*$/.test(value)) fail(`${label} must be decimal string`); }
function hex(value, length, label) { if (typeof value !== "string" || !new RegExp(`^[0-9a-f]{${length}}$`).test(value)) fail(`${label} must be lowercase hex`); }
function equal(actual, expected, label) { if (actual !== expected) fail(`${label} mismatch`); }
function deepEqual(actual, expected, label) { if (JSON.stringify(actual) !== JSON.stringify(expected)) fail(`${label} mismatch`); }
function normalize(file) { return file.replaceAll("\\", "/"); }
function fail(message) { throw new Error(message); }
