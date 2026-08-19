#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(import.meta.url);
const repositoryRoot = path.resolve(path.dirname(scriptPath), "../..");
const m307Validator = path.join(repositoryRoot, "tools/governance/verify-m3-07-high-benchmark-contract.mjs");
const FIXTURES = ["java-single-dex", "kotlin-multidex", "jni-four-abi"];
const REVERSE_FIXTURES = [...FIXTURES].reverse();
const METRICS = [
  "processToApplicationOnCreateMs", "processToInteractiveMs", "peakPssBytes",
  "nativeHeapPeakBytes", "stablePssBytes",
];
const STATISTICS = [
  "baselineP50", "baselineP95", "protectedP50", "protectedP95", "deltaP50", "deltaP95",
];
const MIB = 1024 * 1024;
const BUDGETS = new Map([
  ["processToApplicationOnCreateMs", [300, 500]],
  ["processToInteractiveMs", [300, 500]],
  ["peakPssBytes", [48 * MIB, 64 * MIB]],
  ["nativeHeapPeakBytes", [24 * MIB, 32 * MIB]],
  ["stablePssBytes", [32 * MIB, 48 * MIB]],
]);
const TOP_FIELDS = [
  "schemaVersion", "headSha", "environmentId", "runId", "jobId", "runAttempt",
  "bootIdHashPrefix", "artifactManifestSha256", "campaigns", "comparisons",
  "allBudgetsPass", "repeatabilityPass", "cleanupPassed",
];
const CAMPAIGN_FIELDS = [
  "id", "fixtureOrder", "modeOrder", "headSha", "environmentFingerprint", "runId",
  "jobId", "runAttempt", "bootIdHashPrefix", "artifactManifestSha256", "reportSha256",
  "warmups", "measurements", "allBudgetsPass", "cleanupPassed",
];
const COMPARISON_FIELDS = [
  "fixtureId", "metric", "statistic", "campaignA", "campaignB", "variation", "limit", "pass",
];
const MANIFEST_FIELDS = [
  "schemaVersion", "headSha", "environmentId", "runId", "jobId", "runAttempt",
  "bootIdHashPrefix", "campaigns", "artifacts",
];
const MANIFEST_CAMPAIGN_FIELDS = ["id", "fixtureOrder", "modeOrder", "reportSha256"];
const MANIFEST_ARTIFACT_FIELDS = ["artifactId", "fileName", "sha256"];
const ARTIFACT_IDS = FIXTURES.flatMap(fixture => [`${fixture}-baseline`, `${fixture}-protected`]);
const PACKAGE_ARGUMENTS = [
  "report", "campaignA", "campaignB", "expectedHead", "expectedRunId", "expectedJobId",
  "expectedRunAttempt", "expectedEnvironment", "expectedBootHash", "artifactManifest", "artifactRoot",
];

function parseArguments(argv) {
  const options = {
    selfTest: false, baseRef: null, report: null, campaignA: null, campaignB: null,
    expectedHead: null, expectedRunId: null, expectedJobId: null, expectedRunAttempt: null,
    expectedEnvironment: null, expectedBootHash: null, artifactManifest: null, artifactRoot: null,
  };
  const mappings = new Map([
    ["--report", "report"], ["--campaign-a", "campaignA"], ["--campaign-b", "campaignB"],
    ["--expected-head", "expectedHead"], ["--expected-run-id", "expectedRunId"],
    ["--expected-job-id", "expectedJobId"], ["--expected-run-attempt", "expectedRunAttempt"],
    ["--expected-environment", "expectedEnvironment"], ["--expected-boot-hash", "expectedBootHash"],
    ["--artifact-manifest", "artifactManifest"], ["--artifact-root", "artifactRoot"], ["--base-ref", "baseRef"],
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--self-test" && !options.selfTest) {
      options.selfTest = true;
      continue;
    }
    const key = mappings.get(argument);
    if (!key || !argv[index + 1] || options[key] !== null) throw new Error(`unknown, duplicate or incomplete argument: ${argument}`);
    options[key] = argv[index + 1];
    index += 1;
  }
  if (options.selfTest && argv.length !== 1) throw new Error("--self-test cannot be combined with other inputs");
  const supplied = PACKAGE_ARGUMENTS.filter(key => options[key] !== null);
  if (supplied.length !== 0 && supplied.length !== PACKAGE_ARGUMENTS.length) {
    throw new Error(`report validation requires all package inputs; received ${supplied.join(",")}`);
  }
  return options;
}

function normalize(value) {
  return value.replaceAll("\\", "/");
}

function hashFile(file) {
  return createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function exactFields(value, fields, label) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) return [`${label} must be an object`];
  const actual = Object.keys(value).sort();
  const expected = [...fields].sort();
  return actual.length === expected.length && actual.every((field, index) => field === expected[index])
    ? [] : [`${label} fields must be exactly ${fields.join(",")}`];
}

function finite(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function sameArray(actual, expected) {
  return Array.isArray(actual) && actual.length === expected.length
    && actual.every((value, index) => value === expected[index]);
}

function nearestRank(values, quantile) {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(quantile * sorted.length) - 1)];
}

function expectedVariation(left, right) {
  return Math.abs(left - right) / Math.max(1, Math.min(Math.abs(left), Math.abs(right)));
}

function sensitiveStringErrors(value, label) {
  const errors = [];
  const normalized = normalize(value);
  if (/[A-Za-z]:\//.test(normalized) || normalized.startsWith("//") || normalized.startsWith("/")
      || /(?:^|[\s"'=])\/(?:Users|home|root|data|sdcard|storage|mnt|tmp)\//i.test(normalized)) {
    errors.push(`${label}: absolute or user path prohibited`);
  }
  if (/-----BEGIN(?: RSA| EC| OPENSSH)? PRIVATE KEY-----/i.test(value)) errors.push(`${label}: private key marker prohibited`);
  return errors;
}

function sensitiveErrors(value, label = "report") {
  const errors = [];
  if (Array.isArray(value)) {
    value.forEach((item, index) => errors.push(...sensitiveErrors(item, `${label}[${index}]`)));
  } else if (value !== null && typeof value === "object") {
    for (const [key, item] of Object.entries(value)) {
      if (/^(?:serial|deviceSerial|adbSerial)$/i.test(key)) errors.push(`${label}.${key}: device serial field prohibited`);
      errors.push(...sensitiveStringErrors(key, `${label} key`));
      errors.push(...sensitiveErrors(item, `${label}.${key}`));
    }
  } else if (typeof value === "string") {
    errors.push(...sensitiveStringErrors(value, label));
  }
  return errors;
}

function readSecureJson(file, label) {
  try {
    const data = JSON.parse(fs.readFileSync(path.resolve(file), "utf8"));
    return { data, errors: sensitiveErrors(data, label) };
  } catch (failure) {
    return { data: null, errors: [`${label}: cannot parse JSON: ${failure.message}`] };
  }
}

function validateToken(value, label) {
  return typeof value === "string" && /^[a-z0-9_.:-]{1,128}$/i.test(value) ? [] : [`${label} invalid`];
}

function validateArtifactManifest(manifest, expected, reportHashes, actualArtifactHashes) {
  const errors = exactFields(manifest, MANIFEST_FIELDS, "artifact manifest");
  if (errors.length > 0) return errors;
  if (manifest.schemaVersion !== 1) errors.push("artifact manifest schemaVersion must be 1");
  if (manifest.headSha !== expected.head) errors.push("artifact manifest head mismatch");
  if (manifest.environmentId !== expected.environment) errors.push("artifact manifest environment mismatch");
  if (manifest.runId !== expected.runId || manifest.jobId !== expected.jobId
      || manifest.runAttempt !== expected.runAttempt) errors.push("artifact manifest job identity mismatch");
  if (manifest.bootIdHashPrefix !== expected.bootHash) errors.push("artifact manifest boot mismatch");
  if (!Array.isArray(manifest.campaigns) || manifest.campaigns.length !== 2) {
    errors.push("artifact manifest requires exactly two campaigns");
  } else {
    const wanted = [
      { id: "A", fixtureOrder: FIXTURES, modeOrder: "baseline_then_protected", reportSha256: reportHashes[0] },
      { id: "B", fixtureOrder: REVERSE_FIXTURES, modeOrder: "protected_then_baseline", reportSha256: reportHashes[1] },
    ];
    manifest.campaigns.forEach((campaign, index) => {
      const label = `artifact manifest campaign ${wanted[index].id}`;
      const fieldErrors = exactFields(campaign, MANIFEST_CAMPAIGN_FIELDS, label);
      errors.push(...fieldErrors);
      if (fieldErrors.length > 0) return;
      if (campaign.id !== wanted[index].id) errors.push(`${label} id mismatch`);
      if (!sameArray(campaign.fixtureOrder, wanted[index].fixtureOrder)) errors.push(`${label} fixture order mismatch`);
      if (campaign.modeOrder !== wanted[index].modeOrder) errors.push(`${label} mode order mismatch`);
      if (campaign.reportSha256 !== wanted[index].reportSha256) errors.push(`${label} report SHA-256 mismatch`);
    });
    if (manifest.campaigns[0]?.reportSha256 === manifest.campaigns[1]?.reportSha256) {
      errors.push("artifact manifest campaigns must bind distinct report bytes");
    }
  }
  if (!Array.isArray(manifest.artifacts) || manifest.artifacts.length !== ARTIFACT_IDS.length) {
    errors.push(`artifact manifest requires exactly ${ARTIFACT_IDS.length} APK artifacts`);
  } else {
    const seenIds = new Set();
    const seenHashes = new Set();
    manifest.artifacts.forEach((artifact, index) => {
      const label = `artifact manifest artifacts[${index}]`;
      const fieldErrors = exactFields(artifact, MANIFEST_ARTIFACT_FIELDS, label);
      errors.push(...fieldErrors);
      if (fieldErrors.length > 0) return;
      if (!ARTIFACT_IDS.includes(artifact.artifactId) || seenIds.has(artifact.artifactId)) errors.push(`${label} identity invalid or duplicate`);
      if (artifact.fileName !== `${artifact.artifactId}.apk`) errors.push(`${label} canonical fileName mismatch`);
      if (typeof artifact.sha256 !== "string" || !/^[0-9a-f]{64}$/i.test(artifact.sha256)
          || seenHashes.has(artifact.sha256)) errors.push(`${label} SHA-256 invalid or duplicate`);
      if (actualArtifactHashes.get(artifact.artifactId) !== artifact.sha256) errors.push(`${label} does not match APK bytes`);
      seenIds.add(artifact.artifactId);
      seenHashes.add(artifact.sha256);
    });
    for (const artifactId of ARTIFACT_IDS) if (!seenIds.has(artifactId)) errors.push(`artifact manifest missing ${artifactId}`);
  }
  return errors;
}

function validateAggregateMetadata(report, expected, actualManifestHash, actualReportHashes) {
  const errors = exactFields(report, TOP_FIELDS, "report");
  if (errors.length > 0) return errors;
  if (report.schemaVersion !== 1) errors.push("schemaVersion must be 1");
  if (report.headSha !== expected.head) errors.push("headSha does not match expected head");
  errors.push(...validateToken(report.environmentId, "environmentId"));
  if (report.environmentId !== expected.environment) errors.push("environmentId does not match expected environment");
  if (report.runId !== expected.runId) errors.push("runId does not match expected run");
  if (report.jobId !== expected.jobId) errors.push("jobId does not match expected job");
  if (report.runAttempt !== expected.runAttempt) errors.push("runAttempt does not match expected attempt");
  if (report.bootIdHashPrefix !== expected.bootHash) errors.push("bootIdHashPrefix does not match expected boot");
  if (report.artifactManifestSha256 !== actualManifestHash) errors.push("artifactManifestSha256 does not match manifest bytes");
  if (!Array.isArray(report.campaigns) || report.campaigns.length !== 2) {
    errors.push("exactly two campaigns are required");
    return errors;
  }
  const campaignExpectations = [
    { id: "A", fixtureOrder: FIXTURES, modeOrder: "baseline_then_protected", reportHash: actualReportHashes[0] },
    { id: "B", fixtureOrder: REVERSE_FIXTURES, modeOrder: "protected_then_baseline", reportHash: actualReportHashes[1] },
  ];
  report.campaigns.forEach((campaign, index) => {
    const wanted = campaignExpectations[index];
    const label = `campaign ${wanted.id}`;
    const fieldErrors = exactFields(campaign, CAMPAIGN_FIELDS, label);
    errors.push(...fieldErrors);
    if (fieldErrors.length > 0) return;
    if (campaign.id !== wanted.id) errors.push(`${label} id mismatch`);
    if (!sameArray(campaign.fixtureOrder, wanted.fixtureOrder)) errors.push(`${label} fixture order mismatch`);
    if (campaign.modeOrder !== wanted.modeOrder) errors.push(`${label} mode order mismatch`);
    if (campaign.headSha !== expected.head) errors.push(`${label} head mismatch`);
    if (campaign.environmentFingerprint !== expected.environment) errors.push(`${label} environment mismatch`);
    if (campaign.runId !== expected.runId || campaign.jobId !== expected.jobId
        || campaign.runAttempt !== expected.runAttempt) errors.push(`${label} job identity mismatch`);
    if (campaign.bootIdHashPrefix !== expected.bootHash) errors.push(`${label} boot mismatch`);
    if (campaign.artifactManifestSha256 !== actualManifestHash) errors.push(`${label} artifact manifest mismatch`);
    if (campaign.reportSha256 !== wanted.reportHash) errors.push(`${label} report SHA-256 mismatch`);
    if (campaign.warmups !== 5) errors.push(`${label} warmups must be 5`);
    if (campaign.measurements !== 30) errors.push(`${label} measurements must be 30`);
    if (campaign.allBudgetsPass !== true) errors.push(`${label} budgets must pass`);
    if (campaign.cleanupPassed !== true) errors.push(`${label} cleanup must pass`);
  });
  return errors;
}

function validateComparisonRows(report) {
  const errors = [];
  const expectedKeys = new Set(FIXTURES.flatMap(fixture => METRICS.flatMap(metric =>
    STATISTICS.map(statistic => `${fixture}|${metric}|${statistic}`))));
  const seen = new Map();
  if (!Array.isArray(report.comparisons) || report.comparisons.length !== expectedKeys.size) {
    return [`comparisons must contain exactly ${expectedKeys.size} rows`];
  }
  report.comparisons.forEach((row, index) => {
    const label = `comparisons[${index}]`;
    const fieldErrors = exactFields(row, COMPARISON_FIELDS, label);
    errors.push(...fieldErrors);
    if (fieldErrors.length > 0) return;
    const key = `${row.fixtureId}|${row.metric}|${row.statistic}`;
    if (!expectedKeys.has(key)) errors.push(`${label} identity invalid`);
    if (seen.has(key)) errors.push(`${label} duplicate identity`);
    seen.set(key, row);
    if (!finite(row.campaignA) || !finite(row.campaignB)) errors.push(`${label} values must be finite`);
    if (!row.statistic.startsWith("delta") && (row.campaignA < 0 || row.campaignB < 0)) {
      errors.push(`${label} absolute summaries must be non-negative`);
    }
    if (row.limit !== 0.10) errors.push(`${label} limit must be 0.10`);
    if (finite(row.campaignA) && finite(row.campaignB)) {
      const variation = expectedVariation(row.campaignA, row.campaignB);
      if (!finite(row.variation) || Math.abs(row.variation - variation) > 1e-12) errors.push(`${label} variation arithmetic mismatch`);
      if (row.pass !== (variation <= 0.10)) errors.push(`${label} pass arithmetic mismatch`);
      if (row.pass !== true) errors.push(`${label} repeatability must pass`);
    }
  });
  for (const key of expectedKeys) if (!seen.has(key)) errors.push(`missing comparison ${key}`);
  for (const fixture of FIXTURES) {
    for (const metric of METRICS) {
      for (const [campaignField, campaignLabel] of [["campaignA", "A"], ["campaignB", "B"]]) {
        const get = statistic => seen.get(`${fixture}|${metric}|${statistic}`)?.[campaignField];
        const expectedP50 = get("protectedP50") - get("baselineP50");
        const expectedP95 = get("protectedP95") - get("baselineP95");
        if (!finite(expectedP50) || get("deltaP50") !== expectedP50) errors.push(`${fixture}|${metric}|${campaignLabel} deltaP50 mismatch`);
        if (!finite(expectedP95) || get("deltaP95") !== expectedP95) errors.push(`${fixture}|${metric}|${campaignLabel} deltaP95 mismatch`);
      }
    }
  }
  if (report.allBudgetsPass !== true) errors.push("allBudgetsPass must be true");
  if (report.repeatabilityPass !== true) errors.push("repeatabilityPass must be true");
  if (report.cleanupPassed !== true) errors.push("cleanupPassed must be true");
  return errors;
}

function validateCampaignReport(file, label, expectedEnvironment) {
  const errors = [];
  const m307 = spawnSync(process.execPath, [m307Validator, "--report", path.resolve(file)], {
    cwd: repositoryRoot, encoding: "utf8", timeout: 30_000,
  });
  if (m307.error || m307.status !== 0) {
    errors.push(`${label}: M3-07 report validation failed: ${(m307.stderr || m307.error?.message || "unknown").trim()}`);
  }
  const parsed = readSecureJson(file, label);
  errors.push(...parsed.errors);
  if (!parsed.data) return { errors, summaries: new Map() };
  const report = parsed.data;
  if (report.environmentId !== expectedEnvironment) errors.push(`${label}: environmentId mismatch`);
  if (report.warmups !== 5 || report.measurements !== 30) errors.push(`${label}: report warmups/measurements mismatch`);
  if (report.allBudgetsPass !== true || report.cleanupPassed !== true) errors.push(`${label}: report budget/cleanup must pass`);
  if (!Array.isArray(report.results) || report.results.length !== 18) {
    errors.push(`${label}: expected exactly 18 Android result rows`);
    return { errors, summaries: new Map() };
  }
  const summaries = new Map();
  const observed = report.results.filter(row => METRICS.includes(row.metric));
  const high = report.results.filter(row => row.metric === "highProfileIncrementalMs");
  if (observed.length !== 15 || high.length !== 3) errors.push(`${label}: expected 15 observed and 3 isolated HIGH rows`);
  const highFixtures = new Set();
  for (const row of high) {
    if (!FIXTURES.includes(row.fixtureId) || highFixtures.has(row.fixtureId)) errors.push(`${label}: invalid or duplicate isolated HIGH row`);
    highFixtures.add(row.fixtureId);
    if (row.baseline !== 0 || row.delta !== row.p50 || row.budget !== 250 || row.pass !== true || row.cleanupPassed !== true) {
      errors.push(`${label}: ${row.fixtureId} isolated HIGH budget/cleanup arithmetic mismatch`);
    }
  }
  for (const row of observed) {
    const key = `${row.fixtureId}|${row.metric}`;
    if (!FIXTURES.includes(row.fixtureId) || !METRICS.includes(row.metric) || summaries.has(key)) {
      errors.push(`${label}: invalid or duplicate observed row ${key}`);
      continue;
    }
    if (!Array.isArray(row.samples) || row.samples.length !== 30 || row.samples.some(value => !finite(value) || value < 0)
        || !Array.isArray(row.baselineSamples) || row.baselineSamples.length !== 30
        || row.baselineSamples.some(value => !finite(value) || value < 0)) {
      errors.push(`${label}: ${key} requires 30 finite baseline and protected samples`);
      continue;
    }
    const values = {
      baselineP50: nearestRank(row.baselineSamples, 0.50),
      baselineP95: nearestRank(row.baselineSamples, 0.95),
      protectedP50: nearestRank(row.samples, 0.50),
      protectedP95: nearestRank(row.samples, 0.95),
    };
    values.deltaP50 = values.protectedP50 - values.baselineP50;
    values.deltaP95 = values.protectedP95 - values.baselineP95;
    const [p50Budget, p95Budget] = BUDGETS.get(row.metric);
    const pass = values.deltaP50 <= p50Budget && values.deltaP95 <= p95Budget;
    const bindings = [
      ["p50", values.protectedP50], ["p95", values.protectedP95], ["baseline", values.baselineP50],
      ["baselineP95", values.baselineP95], ["delta", values.deltaP50], ["deltaP95", values.deltaP95],
      ["p50Budget", p50Budget], ["budget", p95Budget], ["pass", pass],
    ];
    bindings.forEach(([field, expected]) => {
      if (row[field] !== expected) errors.push(`${label}: ${key} ${field} arithmetic mismatch`);
    });
    if (!pass) errors.push(`${label}: ${key} fixed budget failed`);
    summaries.set(key, values);
  }
  for (const fixture of FIXTURES) for (const metric of METRICS) {
    if (!summaries.has(`${fixture}|${metric}`)) errors.push(`${label}: missing observed row ${fixture}|${metric}`);
  }
  return { errors, summaries };
}

function validatePackage(options) {
  const errors = [];
  const expectedAttempt = Number(options.expectedRunAttempt);
  if (!/^[0-9a-f]{40}$/i.test(options.expectedHead)) errors.push("expected head must be 40 hex");
  errors.push(...validateToken(options.expectedRunId, "expected run id"));
  errors.push(...validateToken(options.expectedJobId, "expected job id"));
  errors.push(...validateToken(options.expectedEnvironment, "expected environment"));
  if (!Number.isInteger(expectedAttempt) || expectedAttempt < 1) errors.push("expected run attempt must be positive integer");
  if (!/^[0-9a-f]{12,16}$/i.test(options.expectedBootHash)) errors.push("expected boot hash must be 12-16 hex");
  for (const file of [options.report, options.campaignA, options.campaignB, options.artifactManifest]) {
    if (!fs.existsSync(path.resolve(file)) || !fs.statSync(path.resolve(file)).isFile()) errors.push(`required file missing: ${file}`);
  }
  if (!fs.existsSync(path.resolve(options.artifactRoot)) || !fs.statSync(path.resolve(options.artifactRoot)).isDirectory()) {
    errors.push(`artifact root missing: ${options.artifactRoot}`);
  }
  if (path.resolve(options.campaignA) === path.resolve(options.campaignB)) errors.push("campaign A and B must be distinct retained file paths");
  if (errors.length > 0) return errors;
  const aggregate = readSecureJson(options.report, "aggregate");
  const manifest = readSecureJson(options.artifactManifest, "artifact manifest");
  errors.push(...aggregate.errors);
  errors.push(...manifest.errors);
  if (!aggregate.data || !manifest.data) return errors;
  const manifestHash = hashFile(options.artifactManifest);
  const reportHashes = [hashFile(options.campaignA), hashFile(options.campaignB)];
  const actualArtifactHashes = new Map();
  for (const artifactId of ARTIFACT_IDS) {
    const apk = path.join(path.resolve(options.artifactRoot), `${artifactId}.apk`);
    if (!fs.existsSync(apk) || !fs.statSync(apk).isFile()) errors.push(`required APK missing: ${artifactId}`);
    else actualArtifactHashes.set(artifactId, hashFile(apk));
  }
  if (errors.length > 0) return errors;
  const expected = {
    head: options.expectedHead, runId: options.expectedRunId, jobId: options.expectedJobId,
    runAttempt: expectedAttempt, environment: options.expectedEnvironment, bootHash: options.expectedBootHash,
  };
  errors.push(...validateArtifactManifest(manifest.data, expected, reportHashes, actualArtifactHashes));
  errors.push(...validateAggregateMetadata(aggregate.data, expected, manifestHash, reportHashes));
  errors.push(...validateComparisonRows(aggregate.data));
  if (errors.length > 0) return errors;
  const campaignA = validateCampaignReport(options.campaignA, "campaign A report", expected.environment);
  const campaignB = validateCampaignReport(options.campaignB, "campaign B report", expected.environment);
  errors.push(...campaignA.errors, ...campaignB.errors);
  if (errors.length > 0) return errors;
  for (const row of aggregate.data.comparisons) {
    const key = `${row.fixtureId}|${row.metric}`;
    if (campaignA.summaries.get(key)?.[row.statistic] !== row.campaignA) errors.push(`${key}|${row.statistic}: campaign A source mismatch`);
    if (campaignB.summaries.get(key)?.[row.statistic] !== row.campaignB) errors.push(`${key}|${row.statistic}: campaign B source mismatch`);
  }
  return errors;
}

function requireText(relative, tokens) {
  const text = fs.readFileSync(path.join(repositoryRoot, relative), "utf8");
  return tokens.filter(token => !text.includes(token)).map(token => `${relative}: missing ${token}`);
}

function contractErrors() {
  const errors = [];
  errors.push(...requireText("docs/adr/0015-startup-performance-measurement-stability.md", [
    "exactly two complementary campaigns", "baseline_then_protected", "protected_then_baseline",
    "thirty measured cold starts", "all ninety comparison rows", "A third campaign",
    "--campaign-a", "--expected-head", "--expected-run-id", "--expected-boot-hash", "--artifact-root",
  ]));
  errors.push(...requireText("docs/tasks/M3-08-startup-performance-stability-contract.md", [
    "Issue #64", "warmups=5", "measurements=30", "exactly ninety unique comparison rows",
    "A production optimization is not authorized by this contract.", "--artifact-manifest", "--artifact-root",
  ]));
  errors.push(...requireText("docs/tasks/M3-05-size-startup-memory-benchmarks.md", [
    "  - M3-08", "ADR 0015", "baseline_then_protected", "protected_then_baseline",
    "90 行比较", "不得补样", "不得在本任务修改生产 Runtime", "--campaign-a",
  ]));
  errors.push(...requireText("docs/tasks/INDEX.md", ["| M3-08 |", "M3-07 → M3-08 → M3-09 → M3-11 → M3-10 → M3-05"]));
  errors.push(...requireText("docs/TEST_STRATEGY.md", ["ADR 0015", "恰好两个 campaign", "禁止第三 campaign"]));
  return errors;
}

const ALLOWED_DIFF = new Set([
  ".github/workflows/governance.yml", "HandOff.md", "README.md", "docs/PROJECT_PLAN.md",
  "docs/ROADMAP.md", "docs/TEST_STRATEGY.md", "docs/adr/0015-startup-performance-measurement-stability.md",
  "docs/tasks/INDEX.md", "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
  "docs/tasks/M3-08-startup-performance-stability-contract.md", "tools/governance/validate-project-package.mjs",
  "tools/governance/verify-m3-08-startup-stability-contract.mjs",
]);

function allowedDiffPath(relative) {
  const file = normalize(relative);
  return ALLOWED_DIFF.has(file) || file.startsWith("docs/evidence/M3-08/");
}

function verifyNoImplementationDiff(baseRef) {
  const result = spawnSync("git", ["diff", "--name-only", `${baseRef}..HEAD`], {
    cwd: repositoryRoot, encoding: "utf8", timeout: 30_000,
  });
  if (result.error || result.status !== 0) return [`cannot inspect base diff ${baseRef}: ${(result.stderr || result.error?.message || "git failure").trim()}`];
  return result.stdout.split(/\r?\n/).filter(Boolean).filter(file => !allowedDiffPath(file))
    .map(file => `${normalize(file)}: implementation surface changed in M3-08 diff`);
}

function m307Row(fixtureId, metric, baselineValue, protectedValue) {
  const samples = Array(30).fill(protectedValue);
  const baselineSamples = Array(30).fill(baselineValue);
  const [p50Budget, p95Budget] = BUDGETS.get(metric);
  const delta = protectedValue - baselineValue;
  return {
    fixtureId, environmentId: "api36-x86_64", measurementMode: "observed_cold_start",
    observedRiskLevel: "LOW", observedRiskAction: "ALLOW", riskObservationTiming: "post_start",
    metric, samples, p50: protectedValue, p95: protectedValue, baseline: baselineValue, delta,
    budget: p95Budget, pass: delta <= p50Budget && delta <= p95Budget, baselineSamples,
    baselineP95: baselineValue, deltaP95: delta, p50Budget, claimType: null, freshProcess: null,
    sameHandle: null, lookupCountBeforeUpgrade: null, lookupCountAfterUpgrade: null,
    cleanupPassed: null, nativeJitterMs: null,
  };
}

function highRow(fixtureId) {
  const samples = Array(30).fill(30);
  return {
    fixtureId, environmentId: "api36-x86_64", measurementMode: "isolated_high_upgrade",
    observedRiskLevel: null, observedRiskAction: null, riskObservationTiming: null,
    metric: "highProfileIncrementalMs", samples, p50: 30, p95: 30, baseline: 0, delta: 30,
    budget: 250, pass: true, claimType: "incremental_profile", freshProcess: true,
    sameHandle: true, lookupCountBeforeUpgrade: 0, lookupCountAfterUpgrade: 1,
    cleanupPassed: true, nativeJitterMs: Array(30).fill(25),
  };
}

function campaignReport(baselineValue, protectedValue) {
  const results = FIXTURES.flatMap(fixture => [
    ...METRICS.map(metric => m307Row(fixture, metric, baselineValue, protectedValue)), highRow(fixture),
  ]);
  return {
    schemaVersion: 1, environmentId: "api36-x86_64", policyProfile: "LOW_observed_plus_isolated_HIGH_increment",
    warmups: 5, measurements: 30, results, allBudgetsPass: true, cleanupPassed: true, limitations: [],
  };
}

function summaryValues(baselineValue, protectedValue) {
  return {
    baselineP50: baselineValue, baselineP95: baselineValue,
    protectedP50: protectedValue, protectedP95: protectedValue,
    deltaP50: protectedValue - baselineValue, deltaP95: protectedValue - baselineValue,
  };
}

function makeFixturePackage(root, settings = {}) {
  fs.mkdirSync(root, { recursive: true });
  const reportAPath = path.join(root, "campaign-a.json");
  const reportBPath = path.join(root, "campaign-b.json");
  const aggregatePath = path.join(root, "aggregate.json");
  const manifestPath = path.join(root, "artifact-manifest.json");
  const artifactRoot = path.join(root, "apks");
  fs.mkdirSync(artifactRoot, { recursive: true });
  const a = settings.a ?? { baseline: 100, protected: 110 };
  const b = settings.b ?? { baseline: 105, protected: 115 };
  const reportA = campaignReport(a.baseline, a.protected);
  const reportB = campaignReport(b.baseline, b.protected);
  settings.mutateCampaignA?.(reportA);
  settings.mutateCampaignB?.(reportB);
  fs.writeFileSync(reportAPath, `${JSON.stringify(reportA)}\n`, "utf8");
  fs.writeFileSync(reportBPath, `${JSON.stringify(reportB)}\n`, "utf8");
  const headSha = "1".repeat(40);
  const meta = {
    headSha, environmentId: "api36-x86_64", runId: "123456", jobId: "device-api36",
    runAttempt: 1, bootIdHashPrefix: "1234567890ab",
  };
  const reportHashes = [hashFile(reportAPath), hashFile(reportBPath)];
  const manifest = {
    schemaVersion: 1, headSha, environmentId: meta.environmentId, runId: meta.runId,
    jobId: meta.jobId, runAttempt: meta.runAttempt, bootIdHashPrefix: meta.bootIdHashPrefix,
    campaigns: [
      { id: "A", fixtureOrder: FIXTURES, modeOrder: "baseline_then_protected", reportSha256: reportHashes[0] },
      { id: "B", fixtureOrder: REVERSE_FIXTURES, modeOrder: "protected_then_baseline", reportSha256: reportHashes[1] },
    ],
    artifacts: ARTIFACT_IDS.map(artifactId => {
      const fileName = `${artifactId}.apk`;
      const apk = path.join(artifactRoot, fileName);
      fs.writeFileSync(apk, `synthetic APK bytes for ${artifactId}\n`, "utf8");
      return { artifactId, fileName, sha256: hashFile(apk) };
    }),
  };
  settings.mutateManifest?.(manifest);
  fs.writeFileSync(manifestPath, settings.manifestContent ?? `${JSON.stringify(manifest)}\n`, "utf8");
  meta.artifactManifestSha256 = hashFile(manifestPath);
  const left = summaryValues(a.baseline, a.protected);
  const right = summaryValues(b.baseline, b.protected);
  const comparisons = FIXTURES.flatMap(fixtureId => METRICS.flatMap(metric => STATISTICS.map(statistic => ({
    fixtureId, metric, statistic, campaignA: left[statistic], campaignB: right[statistic],
    variation: expectedVariation(left[statistic], right[statistic]), limit: 0.10,
    pass: expectedVariation(left[statistic], right[statistic]) <= 0.10,
  }))));
  const campaignMetadata = (id, fixtureOrder, modeOrder, reportSha256) => ({
    id, fixtureOrder, modeOrder, headSha, environmentFingerprint: meta.environmentId,
    runId: meta.runId, jobId: meta.jobId, runAttempt: meta.runAttempt,
    bootIdHashPrefix: meta.bootIdHashPrefix, artifactManifestSha256: meta.artifactManifestSha256,
    reportSha256, warmups: 5, measurements: 30, allBudgetsPass: true, cleanupPassed: true,
  });
  const aggregate = {
    schemaVersion: 1, ...meta,
    campaigns: [
      campaignMetadata("A", FIXTURES, "baseline_then_protected", reportHashes[0]),
      campaignMetadata("B", REVERSE_FIXTURES, "protected_then_baseline", reportHashes[1]),
    ],
    comparisons, allBudgetsPass: true, repeatabilityPass: true, cleanupPassed: true,
  };
  settings.mutateAggregate?.(aggregate);
  fs.writeFileSync(aggregatePath, `${JSON.stringify(aggregate)}\n`, "utf8");
  const options = {
    report: aggregatePath, campaignA: reportAPath, campaignB: reportBPath, expectedHead: headSha,
    expectedRunId: meta.runId, expectedJobId: meta.jobId, expectedRunAttempt: String(meta.runAttempt),
    expectedEnvironment: meta.environmentId, expectedBootHash: meta.bootIdHashPrefix,
    artifactManifest: manifestPath, artifactRoot,
  };
  settings.mutateExpected?.(options);
  return options;
}

function runSelfTest() {
  if (allowedDiffPath("runtime/policy/src/main/java/Unsafe.java")) throw new Error("production diff mutation escaped");
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), "aah-m3-08-"));
  try {
    const positive = makeFixturePackage(path.join(temp, "positive"));
    const positiveErrors = validatePackage(positive);
    if (positiveErrors.length > 0) throw new Error(`positive package rejected: ${positiveErrors.join("; ")}`);
    const boundary = makeFixturePackage(path.join(temp, "boundary"), {
      a: { baseline: 100, protected: 110 }, b: { baseline: 110, protected: 121 },
    });
    const boundaryErrors = validatePackage(boundary);
    if (boundaryErrors.length > 0) throw new Error(`10 percent boundary rejected: ${boundaryErrors.join("; ")}`);
    const negativeDelta = makeFixturePackage(path.join(temp, "negative-delta"), {
      a: { baseline: 110, protected: 100 }, b: { baseline: 110, protected: 99 },
    });
    const negativeErrors = validatePackage(negativeDelta);
    if (negativeErrors.length > 0) throw new Error(`valid negative delta rejected: ${negativeErrors.join("; ")}`);

    const mutations = [
      { aggregate: report => report.campaigns.push(structuredClone(report.campaigns[0])) },
      { aggregate: report => { report.campaigns[0].modeOrder = "protected_then_baseline"; } },
      { aggregate: report => { report.campaigns[1].fixtureOrder = FIXTURES; } },
      { aggregate: report => { report.campaigns[1].headSha = "2".repeat(40); } },
      { aggregate: report => { report.campaigns[1].environmentFingerprint = "other"; } },
      { aggregate: report => { report.campaigns[1].runId = "other"; } },
      { aggregate: report => { report.campaigns[1].jobId = "other"; } },
      { aggregate: report => { report.campaigns[1].runAttempt = 2; } },
      { aggregate: report => { report.campaigns[1].bootIdHashPrefix = "abcdefabcdef"; } },
      { aggregate: report => { report.campaigns[1].artifactManifestSha256 = "d".repeat(64); } },
      { aggregate: report => { report.campaigns[0].reportSha256 = "e".repeat(64); } },
      { aggregate: report => { report.campaigns[0].warmups = 4; } },
      { aggregate: report => { report.campaigns[1].warmups = 6; } },
      { aggregate: report => { report.campaigns[0].measurements = 29; } },
      { aggregate: report => { report.campaigns[1].measurements = 31; } },
      { aggregate: report => { report.comparisons.pop(); } },
      { aggregate: report => { report.comparisons[1] = structuredClone(report.comparisons[0]); } },
      { aggregate: report => { report.comparisons[0].statistic = "protectedMedian"; } },
      { aggregate: report => { report.comparisons[0].limit = 0.11; } },
      { aggregate: report => { report.comparisons[0].variation = 0; } },
      { aggregate: report => { report.comparisons[0].pass = false; } },
      { aggregate: report => { report.comparisons.find(row => row.statistic === "deltaP50").campaignA += 1; } },
      { aggregate: report => { report.allBudgetsPass = false; } },
      { aggregate: report => { report.cleanupPassed = false; } },
      { campaignA: report => { report.results.find(row => METRICS.includes(row.metric)).delta += 1; } },
      { campaignA: report => { report.results.find(row => METRICS.includes(row.metric)).baselineSamples.pop(); } },
      { campaignA: report => { report.results.find(row => row.metric === "highProfileIncrementalMs").pass = false; } },
      { expected: options => { options.expectedHead = "2".repeat(40); } },
      { expected: options => { options.expectedRunId = "other"; } },
      { expected: options => { options.expectedJobId = "other"; } },
      { manifest: "tampered-manifest\n", aggregate: report => { report.artifactManifestSha256 = "f".repeat(64); } },
      { manifestObject: manifest => { manifest.runId = "old-run"; } },
      { manifestObject: manifest => { manifest.campaigns[0].modeOrder = "protected_then_baseline"; } },
      { manifestObject: manifest => { manifest.artifacts.pop(); } },
      { manifestObject: manifest => { manifest.artifacts[1] = structuredClone(manifest.artifacts[0]); } },
      { manifestObject: manifest => { manifest.artifacts[0].fileName = "other.apk"; } },
      { manifestObject: manifest => { manifest.artifacts[0].sha256 = "a".repeat(64); } },
      { aggregate: report => { report.jobId = ["C:", "Users", "name", "job"].join("/"); } },
      { aggregate: report => { report.jobId = "D:\\secret\\job"; } },
      { aggregate: report => { report.jobId = "\\\\server\\share"; } },
      { aggregate: report => { report.jobId = ["", "Users", "name", "job"].join("/"); } },
      { aggregate: report => { report.jobId = ["", "home", "name", "job"].join("/"); } },
    ];
    mutations.forEach((mutation, index) => {
      const options = makeFixturePackage(path.join(temp, `negative-${index}`), {
        mutateAggregate: mutation.aggregate, mutateCampaignA: mutation.campaignA,
        mutateCampaignB: mutation.campaignB, mutateExpected: mutation.expected,
        mutateManifest: mutation.manifestObject, manifestContent: mutation.manifest,
      });
      if (validatePackage(options).length === 0) throw new Error(`package mutation ${index} escaped`);
    });
    const samePath = makeFixturePackage(path.join(temp, "same-report-path"));
    samePath.campaignB = samePath.campaignA;
    if (validatePackage(samePath).length === 0) throw new Error("same campaign report path escaped");
    const reused = makeFixturePackage(path.join(temp, "same-report-bytes"));
    fs.copyFileSync(reused.campaignA, reused.campaignB);
    const reusedHash = hashFile(reused.campaignA);
    const reusedManifest = JSON.parse(fs.readFileSync(reused.artifactManifest, "utf8"));
    reusedManifest.campaigns[1].reportSha256 = reusedHash;
    fs.writeFileSync(reused.artifactManifest, `${JSON.stringify(reusedManifest)}\n`, "utf8");
    const reusedAggregate = JSON.parse(fs.readFileSync(reused.report, "utf8"));
    reusedAggregate.artifactManifestSha256 = hashFile(reused.artifactManifest);
    reusedAggregate.campaigns.forEach(campaign => { campaign.artifactManifestSha256 = reusedAggregate.artifactManifestSha256; });
    reusedAggregate.campaigns[1].reportSha256 = reusedHash;
    fs.writeFileSync(reused.report, `${JSON.stringify(reusedAggregate)}\n`, "utf8");
    if (validatePackage(reused).length === 0) throw new Error("different paths with reused campaign bytes escaped");
    const tamperedApk = makeFixturePackage(path.join(temp, "tampered-apk-bytes"));
    fs.appendFileSync(path.join(tamperedApk.artifactRoot, `${ARTIFACT_IDS[0]}.apk`), "tamper", "utf8");
    if (validatePackage(tamperedApk).length === 0) throw new Error("tampered APK bytes escaped manifest binding");
    console.log(`OK: M3-08 mutation self-test (1 diff + ${mutations.length + 3} package negatives + 2 arithmetic positives)`);
  } finally {
    fs.rmSync(temp, { recursive: true, force: true });
  }
}

let options;
try {
  options = parseArguments(process.argv.slice(2));
} catch (failure) {
  console.error(`ERROR: ${failure.message}`);
  process.exit(2);
}
if (options.selfTest) {
  runSelfTest();
  process.exit(0);
}
const errors = contractErrors();
if (options.baseRef !== null) errors.push(...verifyNoImplementationDiff(options.baseRef));
if (options.report !== null) errors.push(...validatePackage(options));
if (errors.length > 0) {
  errors.forEach(error => console.error(`ERROR: ${error}`));
  process.exit(1);
}
console.log(`OK: M3-08 startup performance and measurement-stability contract${options.report ? " and bound reports" : ""}`);
