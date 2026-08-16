#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(import.meta.url);
const repositoryRoot = path.resolve(path.dirname(scriptPath), "../..");
const FIXTURES = ["java-single-dex", "kotlin-multidex", "jni-four-abi"];
const REVERSE_FIXTURES = [...FIXTURES].reverse();
const METRICS = [
  "processToApplicationOnCreateMs",
  "processToInteractiveMs",
  "peakPssBytes",
  "nativeHeapPeakBytes",
  "stablePssBytes",
];
const STATISTICS = [
  "baselineP50", "baselineP95", "protectedP50", "protectedP95", "deltaP50", "deltaP95",
];
const TOP_FIELDS = [
  "schemaVersion", "headSha", "environmentId", "campaigns", "comparisons",
  "allBudgetsPass", "repeatabilityPass", "cleanupPassed",
];
const CAMPAIGN_FIELDS = [
  "id", "fixtureOrder", "modeOrder", "headSha", "environmentFingerprint",
  "bootIdHashPrefix", "artifactManifestSha256", "reportSha256", "warmups",
  "measurements", "allBudgetsPass", "cleanupPassed",
];
const COMPARISON_FIELDS = [
  "fixtureId", "metric", "statistic", "campaignA", "campaignB", "variation", "limit", "pass",
];

function parseArguments(argv) {
  const options = { selfTest: false, report: null, baseRef: null };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--self-test" && !options.selfTest) {
      options.selfTest = true;
    } else if ((argument === "--report" || argument === "--base-ref") && argv[index + 1]) {
      const key = argument === "--report" ? "report" : "baseRef";
      if (options[key] !== null) throw new Error(`duplicate ${argument}`);
      options[key] = argv[index + 1];
      index += 1;
    } else {
      throw new Error(`unknown or incomplete argument: ${argument}`);
    }
  }
  if (options.selfTest && (options.report !== null || options.baseRef !== null)) {
    throw new Error("--self-test cannot be combined with report or diff inputs");
  }
  return options;
}

function normalize(value) {
  return value.replaceAll("\\", "/");
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

function expectedVariation(left, right) {
  return Math.abs(left - right) / Math.max(1, Math.min(Math.abs(left), Math.abs(right)));
}

function validateCampaign(campaign, expected, headSha) {
  const errors = exactFields(campaign, CAMPAIGN_FIELDS, `campaign ${expected.id}`);
  if (errors.length > 0) return errors;
  if (campaign.id !== expected.id) errors.push(`campaign ${expected.id} id mismatch`);
  if (!sameArray(campaign.fixtureOrder, expected.fixtureOrder)) errors.push(`campaign ${expected.id} fixture order mismatch`);
  if (campaign.modeOrder !== expected.modeOrder) errors.push(`campaign ${expected.id} mode order mismatch`);
  if (campaign.headSha !== headSha) errors.push(`campaign ${expected.id} head mismatch`);
  if (typeof campaign.environmentFingerprint !== "string" || !/^[a-z0-9_.:-]{3,128}$/i.test(campaign.environmentFingerprint)) {
    errors.push(`campaign ${expected.id} environment fingerprint invalid`);
  }
  if (typeof campaign.bootIdHashPrefix !== "string" || !/^[0-9a-f]{12,16}$/i.test(campaign.bootIdHashPrefix)) {
    errors.push(`campaign ${expected.id} boot hash prefix invalid`);
  }
  for (const field of ["artifactManifestSha256", "reportSha256"]) {
    if (typeof campaign[field] !== "string" || !/^[0-9a-f]{64}$/i.test(campaign[field])) {
      errors.push(`campaign ${expected.id} ${field} invalid`);
    }
  }
  if (campaign.warmups !== 5) errors.push(`campaign ${expected.id} warmups must be 5`);
  if (campaign.measurements !== 30) errors.push(`campaign ${expected.id} measurements must be 30`);
  if (campaign.allBudgetsPass !== true) errors.push(`campaign ${expected.id} budgets must pass`);
  if (campaign.cleanupPassed !== true) errors.push(`campaign ${expected.id} cleanup must pass`);
  return errors;
}

function validateReportObject(report) {
  const errors = exactFields(report, TOP_FIELDS, "report");
  if (errors.length > 0) return errors;
  if (report.schemaVersion !== 1) errors.push("schemaVersion must be 1");
  if (typeof report.headSha !== "string" || !/^[0-9a-f]{40}$/i.test(report.headSha)) errors.push("headSha must be 40 hex");
  if (typeof report.environmentId !== "string" || !/^[a-z0-9_.-]{3,128}$/i.test(report.environmentId)) {
    errors.push("environmentId invalid");
  }
  if (!Array.isArray(report.campaigns) || report.campaigns.length !== 2) {
    errors.push("exactly two campaigns are required");
  } else {
    errors.push(...validateCampaign(report.campaigns[0], {
      id: "A", fixtureOrder: FIXTURES, modeOrder: "baseline_then_protected",
    }, report.headSha));
    errors.push(...validateCampaign(report.campaigns[1], {
      id: "B", fixtureOrder: REVERSE_FIXTURES, modeOrder: "protected_then_baseline",
    }, report.headSha));
    const [left, right] = report.campaigns;
    for (const field of ["environmentFingerprint", "bootIdHashPrefix", "artifactManifestSha256"]) {
      if (left[field] !== right[field]) errors.push(`campaign ${field} must match`);
    }
    if (left.reportSha256 === right.reportSha256) errors.push("campaign report hashes must identify two reports");
  }

  const expectedKeys = new Set(FIXTURES.flatMap(fixture => METRICS.flatMap(metric =>
    STATISTICS.map(statistic => `${fixture}|${metric}|${statistic}`))));
  const seen = new Set();
  if (!Array.isArray(report.comparisons) || report.comparisons.length !== expectedKeys.size) {
    errors.push(`comparisons must contain exactly ${expectedKeys.size} rows`);
  } else {
    report.comparisons.forEach((row, index) => {
      const label = `comparisons[${index}]`;
      const rowErrors = exactFields(row, COMPARISON_FIELDS, label);
      errors.push(...rowErrors);
      if (rowErrors.length > 0) return;
      const key = `${row.fixtureId}|${row.metric}|${row.statistic}`;
      if (!expectedKeys.has(key)) errors.push(`${label} identity invalid`);
      if (seen.has(key)) errors.push(`${label} duplicate identity`);
      seen.add(key);
      if (!finite(row.campaignA) || !finite(row.campaignB)) errors.push(`${label} values must be finite`);
      if (!row.statistic.startsWith("delta") && (row.campaignA < 0 || row.campaignB < 0)) {
        errors.push(`${label} absolute summaries must be non-negative`);
      }
      if (row.limit !== 0.10) errors.push(`${label} limit must be 0.10`);
      if (finite(row.campaignA) && finite(row.campaignB)) {
        const expected = expectedVariation(row.campaignA, row.campaignB);
        if (!finite(row.variation) || Math.abs(row.variation - expected) > 1e-12) {
          errors.push(`${label} variation arithmetic mismatch`);
        }
        if (row.pass !== (expected <= 0.10)) errors.push(`${label} pass mismatch`);
        if (row.pass !== true) errors.push(`${label} repeatability must pass`);
      }
    });
    for (const key of expectedKeys) if (!seen.has(key)) errors.push(`missing comparison ${key}`);
  }
  if (report.allBudgetsPass !== true) errors.push("allBudgetsPass must be true");
  if (report.repeatabilityPass !== true) errors.push("repeatabilityPass must be true");
  if (report.cleanupPassed !== true) errors.push("cleanupPassed must be true");
  return errors;
}

function validateReportFile(file) {
  try {
    const text = fs.readFileSync(path.resolve(file), "utf8");
    if (/(?:[A-Za-z]:\\Users\\|\/(?:home|data|sdcard|storage)\/)|-----BEGIN|"(?:serial|deviceSerial)"/i.test(text)) {
      return ["report contains prohibited path, key marker or device serial field"];
    }
    return validateReportObject(JSON.parse(text));
  } catch (failure) {
    return [`cannot parse report: ${failure.message}`];
  }
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
  ]));
  errors.push(...requireText("docs/tasks/M3-08-startup-performance-stability-contract.md", [
    "Issue #64", "warmups=5", "measurements=30", "exactly ninety unique comparison rows",
    "A production optimization is not authorized by this contract.",
  ]));
  errors.push(...requireText("docs/tasks/M3-05-size-startup-memory-benchmarks.md", [
    "  - M3-08", "ADR 0015", "baseline_then_protected", "protected_then_baseline",
    "90 行比较", "不得补样", "不得在本任务修改生产 Runtime",
  ]));
  errors.push(...requireText("docs/tasks/INDEX.md", [
    "| M3-08 |", "M3-07 → M3-08 → M3-05",
  ]));
  errors.push(...requireText("docs/TEST_STRATEGY.md", [
    "ADR 0015", "恰好两个 campaign", "禁止第三 campaign",
  ]));
  return errors;
}

const ALLOWED_DIFF = new Set([
  ".github/workflows/governance.yml",
  "HandOff.md",
  "README.md",
  "docs/TEST_STRATEGY.md",
  "docs/PROJECT_PLAN.md",
  "docs/ROADMAP.md",
  "docs/adr/0015-startup-performance-measurement-stability.md",
  "docs/tasks/INDEX.md",
  "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
  "docs/tasks/M3-08-startup-performance-stability-contract.md",
  "tools/governance/verify-m3-08-startup-stability-contract.mjs",
  "tools/governance/validate-project-package.mjs",
]);

function allowedDiffPath(relative) {
  const file = normalize(relative);
  return ALLOWED_DIFF.has(file) || file.startsWith("docs/evidence/M3-08/");
}

function verifyNoImplementationDiff(baseRef) {
  const result = spawnSync("git", ["diff", "--name-only", `${baseRef}..HEAD`], {
    cwd: repositoryRoot, encoding: "utf8", timeout: 30_000,
  });
  if (result.error || result.status !== 0) {
    return [`cannot inspect base diff ${baseRef}: ${(result.stderr || result.error?.message || "git failure").trim()}`];
  }
  return result.stdout.split(/\r?\n/).filter(Boolean).filter(file => !allowedDiffPath(file))
    .map(file => `${normalize(file)}: implementation surface changed in M3-08 diff`);
}

function validReport() {
  const hash = character => character.repeat(64);
  const headSha = "1".repeat(40);
  const campaigns = [
    {
      id: "A", fixtureOrder: FIXTURES, modeOrder: "baseline_then_protected", headSha,
      environmentFingerprint: "api36-x86_64-kvm", bootIdHashPrefix: "1234567890ab",
      artifactManifestSha256: hash("a"), reportSha256: hash("b"), warmups: 5,
      measurements: 30, allBudgetsPass: true, cleanupPassed: true,
    },
    {
      id: "B", fixtureOrder: REVERSE_FIXTURES, modeOrder: "protected_then_baseline", headSha,
      environmentFingerprint: "api36-x86_64-kvm", bootIdHashPrefix: "1234567890ab",
      artifactManifestSha256: hash("a"), reportSha256: hash("c"), warmups: 5,
      measurements: 30, allBudgetsPass: true, cleanupPassed: true,
    },
  ];
  const comparisons = FIXTURES.flatMap(fixtureId => METRICS.flatMap(metric => STATISTICS.map(statistic => ({
    fixtureId, metric, statistic, campaignA: 100, campaignB: 105,
    variation: expectedVariation(100, 105), limit: 0.10, pass: true,
  }))));
  return {
    schemaVersion: 1, headSha, environmentId: "api36-x86_64", campaigns, comparisons,
    allBudgetsPass: true, repeatabilityPass: true, cleanupPassed: true,
  };
}

function runSelfTest() {
  if (allowedDiffPath("runtime/policy/src/main/java/Unsafe.java")) {
    throw new Error("production diff mutation escaped");
  }
  const mutations = [
    report => report.campaigns.push(structuredClone(report.campaigns[0])),
    report => { report.campaigns[0].modeOrder = "protected_then_baseline"; },
    report => { report.campaigns[1].fixtureOrder = FIXTURES; },
    report => { report.campaigns[1].headSha = "2".repeat(40); },
    report => { report.campaigns[1].environmentFingerprint = "other"; },
    report => { report.campaigns[1].bootIdHashPrefix = "abcdefabcdef"; },
    report => { report.campaigns[1].artifactManifestSha256 = "d".repeat(64); },
    report => { report.campaigns[0].warmups = 4; },
    report => { report.campaigns[1].warmups = 6; },
    report => { report.campaigns[0].measurements = 29; },
    report => { report.campaigns[1].measurements = 31; },
    report => { report.comparisons.pop(); },
    report => { report.comparisons[1] = structuredClone(report.comparisons[0]); },
    report => { report.comparisons[0].statistic = "protectedMedian"; },
    report => { report.comparisons[0].limit = 0.11; },
    report => { report.comparisons[0].variation = 0; },
    report => { report.comparisons[0].pass = false; },
    report => { report.campaigns[0].allBudgetsPass = false; },
    report => { report.allBudgetsPass = false; },
    report => { report.cleanupPassed = false; },
  ];
  const positive = validReport();
  const positiveErrors = validateReportObject(positive);
  if (positiveErrors.length > 0) throw new Error(`positive report rejected: ${positiveErrors.join("; ")}`);
  mutations.forEach((mutate, index) => {
    const candidate = structuredClone(positive);
    mutate(candidate);
    if (validateReportObject(candidate).length === 0) throw new Error(`report mutation ${index} escaped`);
  });
  console.log(`OK: M3-08 mutation self-test (1 diff + ${mutations.length} report negatives)`);
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
if (options.report !== null) errors.push(...validateReportFile(options.report));
if (errors.length > 0) {
  errors.forEach(error => console.error(`ERROR: ${error}`));
  process.exit(1);
}
console.log(`OK: M3-08 startup performance and measurement-stability contract${options.report ? " and report" : ""}`);
