#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const FIXTURES = ["java-single-dex", "kotlin-multidex", "jni-four-abi"];
const REVERSE_FIXTURES = [...FIXTURES].reverse();
const METRICS = [
  "processToApplicationOnCreateMs", "processToInteractiveMs", "peakPssBytes",
  "nativeHeapPeakBytes", "stablePssBytes",
];
const STATISTICS = [
  "baselineP50", "baselineP95", "protectedP50", "protectedP95", "deltaP50", "deltaP95",
];
const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const MIB = 1024 * 1024;
const BUDGETS = new Map([
  ["processToApplicationOnCreateMs", [300, 500]],
  ["processToInteractiveMs", [300, 500]],
  ["peakPssBytes", [48 * MIB, 64 * MIB]],
  ["nativeHeapPeakBytes", [24 * MIB, 32 * MIB]],
  ["stablePssBytes", [32 * MIB, 48 * MIB]],
]);

function hashFile(file) {
  return createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function sameArray(actual, expected) {
  return Array.isArray(actual) && actual.length === expected.length
    && actual.every((value, index) => value === expected[index]);
}

function nearestRank(values, quantile, label) {
  if (!Array.isArray(values) || values.length !== 30
      || values.some(value => typeof value !== "number" || !Number.isFinite(value) || value < 0)) {
    throw new Error(`${label} requires exactly 30 finite non-negative samples`);
  }
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(quantile * sorted.length) - 1)];
}

function expectedVariation(left, right) {
  return Math.abs(left - right) / Math.max(1, Math.min(Math.abs(left), Math.abs(right)));
}

function readCampaign(file, id, fixtureOrder, modeOrder) {
  const report = JSON.parse(fs.readFileSync(file, "utf8"));
  if (report.campaignId !== id || !sameArray(report.fixtureOrder, fixtureOrder)
      || report.modeOrder !== modeOrder) throw new Error(`campaign ${id} identity or order mismatch`);
  if (report.warmups !== 5 || report.measurements !== 30) throw new Error(`campaign ${id} sample contract mismatch`);
  if (typeof report.allBudgetsPass !== "boolean") throw new Error(`campaign ${id} budget result missing`);
  if (report.cleanupPassed !== true) throw new Error(`campaign ${id} cleanup failed`);
  if (!Array.isArray(report.results)) throw new Error(`campaign ${id} results missing`);
  const summaries = new Map();
  for (const fixtureId of FIXTURES) {
    for (const metric of METRICS) {
      const rows = report.results.filter(row => row.fixtureId === fixtureId && row.metric === metric);
      if (rows.length !== 1) throw new Error(`campaign ${id} requires one ${fixtureId}|${metric} row`);
      const row = rows[0];
      const values = {
        baselineP50: nearestRank(row.baselineSamples, 0.50, `${id}:${fixtureId}|${metric}:baseline`),
        baselineP95: nearestRank(row.baselineSamples, 0.95, `${id}:${fixtureId}|${metric}:baseline`),
        protectedP50: nearestRank(row.samples, 0.50, `${id}:${fixtureId}|${metric}:protected`),
        protectedP95: nearestRank(row.samples, 0.95, `${id}:${fixtureId}|${metric}:protected`),
      };
      values.deltaP50 = values.protectedP50 - values.baselineP50;
      values.deltaP95 = values.protectedP95 - values.baselineP95;
      summaries.set(`${fixtureId}|${metric}`, values);
    }
  }
  return { report, summaries, sha256: hashFile(file) };
}

function copyCanonicalArtifacts(targets, artifactRoot) {
  fs.mkdirSync(artifactRoot, { recursive: true });
  const artifacts = [];
  for (const fixtureId of FIXTURES) {
    for (const [mode, sourceName] of [["baseline", "signed-input.apk"], ["protected", "protected-signed.apk"]]) {
      const artifactId = `${fixtureId}-${mode}`;
      const fileName = `${artifactId}.apk`;
      const source = path.join(targets, fixtureId, sourceName);
      const destination = path.join(artifactRoot, fileName);
      if (!fs.existsSync(source) || !fs.statSync(source).isFile()) throw new Error(`missing canonical source ${artifactId}`);
      fs.copyFileSync(source, destination);
      artifacts.push({ artifactId, fileName, sha256: hashFile(destination) });
    }
  }
  return artifacts;
}

function createEvidence(options) {
  const campaignA = readCampaign(options.campaignA, "A", FIXTURES, "baseline_then_protected");
  const campaignB = readCampaign(options.campaignB, "B", REVERSE_FIXTURES, "protected_then_baseline");
  if (campaignA.report.environmentId !== options.environment || campaignB.report.environmentId !== options.environment) {
    throw new Error("campaign environment does not match executing environment");
  }
  if (campaignA.sha256 === campaignB.sha256) throw new Error("campaign reports must have distinct bytes");
  fs.mkdirSync(options.output, { recursive: true });
  const artifactRoot = path.join(options.output, "apks");
  const artifacts = copyCanonicalArtifacts(options.targets, artifactRoot);
  const manifest = {
    schemaVersion: 1,
    headSha: options.head,
    environmentId: options.environment,
    runId: options.runId,
    jobId: options.jobId,
    runAttempt: options.runAttempt,
    bootIdHashPrefix: options.bootHash,
    campaigns: [
      { id: "A", fixtureOrder: FIXTURES, modeOrder: "baseline_then_protected", reportSha256: campaignA.sha256 },
      { id: "B", fixtureOrder: REVERSE_FIXTURES, modeOrder: "protected_then_baseline", reportSha256: campaignB.sha256 },
    ],
    artifacts,
  };
  const manifestPath = path.join(options.output, "benchmark-artifact-manifest.json");
  fs.writeFileSync(manifestPath, `${JSON.stringify(manifest)}\n`, "utf8");
  const manifestHash = hashFile(manifestPath);
  const comparisons = [];
  for (const fixtureId of FIXTURES) {
    for (const metric of METRICS) {
      const left = campaignA.summaries.get(`${fixtureId}|${metric}`);
      const right = campaignB.summaries.get(`${fixtureId}|${metric}`);
      for (const statistic of STATISTICS) {
        const variation = expectedVariation(left[statistic], right[statistic]);
        comparisons.push({
          fixtureId,
          metric,
          statistic,
          campaignA: left[statistic],
          campaignB: right[statistic],
          variation,
          limit: 0.10,
          pass: variation <= 0.10,
        });
      }
    }
  }
  const campaignMetadata = (id, fixtureOrder, modeOrder, reportSha256, allBudgetsPass) => ({
    id,
    fixtureOrder,
    modeOrder,
    headSha: options.head,
    environmentFingerprint: options.environment,
    runId: options.runId,
    jobId: options.jobId,
    runAttempt: options.runAttempt,
    bootIdHashPrefix: options.bootHash,
    artifactManifestSha256: manifestHash,
    reportSha256,
    warmups: 5,
    measurements: 30,
    allBudgetsPass,
    cleanupPassed: true,
  });
  const aggregate = {
    schemaVersion: 1,
    headSha: options.head,
    environmentId: options.environment,
    runId: options.runId,
    jobId: options.jobId,
    runAttempt: options.runAttempt,
    bootIdHashPrefix: options.bootHash,
    artifactManifestSha256: manifestHash,
    campaigns: [
      campaignMetadata("A", FIXTURES, "baseline_then_protected", campaignA.sha256, campaignA.report.allBudgetsPass),
      campaignMetadata("B", REVERSE_FIXTURES, "protected_then_baseline", campaignB.sha256, campaignB.report.allBudgetsPass),
    ],
    comparisons,
    allBudgetsPass: campaignA.report.allBudgetsPass && campaignB.report.allBudgetsPass,
    repeatabilityPass: comparisons.every(row => row.pass),
    cleanupPassed: true,
  };
  const aggregatePath = path.join(options.output, "benchmark-repeatability.json");
  fs.writeFileSync(aggregatePath, `${JSON.stringify(aggregate)}\n`, "utf8");
  fs.writeFileSync(path.join(options.output, "sha256-manifest.txt"), [
    `${campaignA.sha256}  campaign-a-benchmark-results.json`,
    `${campaignB.sha256}  campaign-b-benchmark-results.json`,
    `${manifestHash}  benchmark-artifact-manifest.json`,
    `${hashFile(aggregatePath)}  benchmark-repeatability.json`,
    ...artifacts.map(artifact => `${artifact.sha256}  apks/${artifact.fileName}`),
    "",
  ].join("\n"), "utf8");
  return { aggregate, aggregatePath, manifestPath, artifactRoot };
}

function parseArguments(argv) {
  if (argv.length === 1 && argv[0] === "--self-test") return { selfTest: true };
  const mappings = new Map([
    ["--campaign-a", "campaignA"], ["--campaign-b", "campaignB"], ["--targets", "targets"],
    ["--output", "output"], ["--head", "head"], ["--run-id", "runId"], ["--job-id", "jobId"],
    ["--run-attempt", "runAttempt"], ["--environment", "environment"], ["--boot-hash", "bootHash"],
  ]);
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = mappings.get(argv[index]);
    if (!key || !argv[index + 1] || options[key] !== undefined) throw new Error(`unknown, duplicate or incomplete argument: ${argv[index]}`);
    options[key] = argv[index + 1];
  }
  for (const key of mappings.values()) if (options[key] === undefined) throw new Error(`missing ${key}`);
  options.runAttempt = Number(options.runAttempt);
  for (const key of ["campaignA", "campaignB", "targets", "output"]) options[key] = path.resolve(options[key]);
  if (!/^[0-9a-f]{40}$/i.test(options.head)) throw new Error("head must be 40 hex");
  if (!/^[a-z0-9_.:-]{1,128}$/i.test(options.runId) || !/^[a-z0-9_.:-]{1,128}$/i.test(options.jobId)
      || !/^[a-z0-9_.:-]{1,128}$/i.test(options.environment)) throw new Error("execution identity token invalid");
  if (!Number.isInteger(options.runAttempt) || options.runAttempt < 1) throw new Error("run attempt invalid");
  if (!/^[0-9a-f]{12,16}$/i.test(options.bootHash)) throw new Error("boot hash invalid");
  return options;
}

function syntheticReport(id, fixtureOrder, modeOrder, baseline, protectedValue) {
  const results = FIXTURES.flatMap(fixtureId => [
    ...METRICS.map(metric => {
      const [p50Budget, p95Budget] = BUDGETS.get(metric);
      return {
        fixtureId, environmentId: "api36-x86_64", measurementMode: "observed_cold_start",
        observedRiskLevel: "LOW", observedRiskAction: "ALLOW", riskObservationTiming: "post_start",
        metric, samples: Array(30).fill(protectedValue), p50: protectedValue, p95: protectedValue,
        baseline, delta: protectedValue - baseline, budget: p95Budget, pass: true,
        baselineSamples: Array(30).fill(baseline), baselineP95: baseline,
        deltaP95: protectedValue - baseline, p50Budget, claimType: null, freshProcess: null,
        sameHandle: null, lookupCountBeforeUpgrade: null, lookupCountAfterUpgrade: null,
        cleanupPassed: null, nativeJitterMs: null,
      };
    }),
    {
      fixtureId, environmentId: "api36-x86_64", measurementMode: "isolated_high_upgrade",
      observedRiskLevel: null, observedRiskAction: null, riskObservationTiming: null,
      metric: "highProfileIncrementalMs", samples: Array(30).fill(30), p50: 30, p95: 30,
      baseline: 0, delta: 30, budget: 250, pass: true, claimType: "incremental_profile",
      freshProcess: true, sameHandle: true, lookupCountBeforeUpgrade: 0,
      lookupCountAfterUpgrade: 1, cleanupPassed: true, nativeJitterMs: Array(30).fill(25),
    },
  ]);
  return {
    schemaVersion: 1,
    campaignId: id,
    fixtureOrder,
    modeOrder,
    environmentId: "api36-x86_64",
    warmups: 5,
    measurements: 30,
    results,
    allBudgetsPass: true,
    cleanupPassed: true,
  };
}

function runSelfTest() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aah-m3-05-ab-"));
  try {
    const campaignA = path.join(root, "campaign-a.json");
    const campaignB = path.join(root, "campaign-b.json");
    const targets = path.join(root, "targets");
    fs.writeFileSync(campaignA, `${JSON.stringify(syntheticReport("A", FIXTURES, "baseline_then_protected", 100, 110))}\n`);
    fs.writeFileSync(campaignB, `${JSON.stringify(syntheticReport("B", REVERSE_FIXTURES, "protected_then_baseline", 101, 111))}\n`);
    for (const fixtureId of FIXTURES) {
      const fixtureRoot = path.join(targets, fixtureId);
      fs.mkdirSync(fixtureRoot, { recursive: true });
      fs.writeFileSync(path.join(fixtureRoot, "signed-input.apk"), `baseline-${fixtureId}\n`);
      fs.writeFileSync(path.join(fixtureRoot, "protected-signed.apk"), `protected-${fixtureId}\n`);
    }
    const result = createEvidence({
      campaignA, campaignB, targets, output: path.join(root, "output"), head: "1".repeat(40),
      runId: "123", jobId: "device", runAttempt: 1, environment: "api36-x86_64", bootHash: "1234567890ab",
    });
    if (result.aggregate.comparisons.length !== 90 || result.aggregate.repeatabilityPass !== true
        || result.aggregate.campaigns.length !== 2 || result.aggregate.campaigns[0].id !== "A"
        || result.aggregate.campaigns[1].id !== "B") throw new Error("self-test aggregate mismatch");
    const validation = spawnSync(process.execPath, [
      path.join(repositoryRoot, "tools/governance/verify-m3-08-startup-stability-contract.mjs"),
      "--report", result.aggregatePath,
      "--campaign-a", campaignA,
      "--campaign-b", campaignB,
      "--expected-head", "1".repeat(40),
      "--expected-run-id", "123",
      "--expected-job-id", "device",
      "--expected-run-attempt", "1",
      "--expected-environment", "api36-x86_64",
      "--expected-boot-hash", "1234567890ab",
      "--artifact-manifest", result.manifestPath,
      "--artifact-root", result.artifactRoot,
    ], { cwd: repositoryRoot, encoding: "utf8", timeout: 30_000 });
    if (validation.error || validation.status !== 0) {
      throw new Error(`formal validator rejected generated evidence: ${(validation.stderr || validation.error?.message || "unknown").trim()}`);
    }
    const failedCampaign = JSON.parse(fs.readFileSync(campaignB, "utf8"));
    failedCampaign.allBudgetsPass = false;
    fs.writeFileSync(campaignB, `${JSON.stringify(failedCampaign)}\n`, "utf8");
    const failed = createEvidence({
      campaignA, campaignB, targets, output: path.join(root, "failed-output"), head: "1".repeat(40),
      runId: "123", jobId: "device", runAttempt: 1, environment: "api36-x86_64", bootHash: "1234567890ab",
    });
    if (failed.aggregate.allBudgetsPass !== false) throw new Error("failed campaign escaped aggregate budget gate");
    console.log("OK: M3-05 A/B evidence generator");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}

try {
  const options = parseArguments(process.argv.slice(2));
  if (options.selfTest) runSelfTest();
  else {
    const result = createEvidence(options);
    console.log(`M3-05 A/B evidence generated comparisons=${result.aggregate.comparisons.length} repeatability=${result.aggregate.repeatabilityPass}`);
  }
} catch (failure) {
  console.error(`ERROR: ${failure.message}`);
  process.exit(1);
}
