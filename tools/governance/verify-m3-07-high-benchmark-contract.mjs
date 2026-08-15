#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = process.cwd();
const scriptPath = fileURLToPath(import.meta.url);
const HOST_METRICS = new Set(["hostProcessMs", "hostPeakRssBytes"]);
const OBSERVED_METRICS = new Set([
  "processToApplicationOnCreateMs",
  "processToInteractiveMs",
  "peakPssBytes",
  "nativeHeapPeakBytes",
  "stablePssBytes",
]);
const REQUIRED_ROW_FIELDS = [
  "fixtureId", "environmentId", "measurementMode",
  "observedRiskLevel", "observedRiskAction", "riskObservationTiming",
  "metric", "samples", "p50", "p95", "baseline", "delta", "budget", "pass",
  "claimType", "freshProcess", "sameHandle", "lookupCountBeforeUpgrade",
  "lookupCountAfterUpgrade", "cleanupPassed", "nativeJitterMs",
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

function normalize(relative) {
  return relative.replaceAll("\\", "/");
}

function isProductionSurface(relative) {
  const file = normalize(relative);
  if (/^(runtime|host)\//.test(file)) {
    if (/\/src\/(?:test|androidTest)[^/]*\//.test(file)) return false;
    return /\/src\/main\//.test(file)
      || /\/(?:build\.gradle\.kts|AndroidManifest\.xml|[^/]*proguard[^/]*|[^/]*consumer[^/]*)$/.test(file);
  }
  if (file.startsWith("fixtures/android/")) {
    if (/\/src\/androidTest[^/]*\//.test(file)) return false;
    return file.includes("/src/")
      || /\/(?:build\.gradle\.kts|AndroidManifest\.xml|[^/]*proguard[^/]*)$/.test(file);
  }
  if (file.startsWith("benchmarks/android/")) {
    if (/\/src\/androidTest[^/]*\//.test(file)) return false;
    return /\/src\/main\//.test(file) || file.endsWith("/build.gradle.kts");
  }
  return file.startsWith("distribution/");
}

function relevantExtension(file) {
  return /\.(?:java|kt|kts|groovy|gradle|cpp|cc|c|hpp|h|xml|pro|properties|json|toml|txt)$/i.test(file);
}

const overridePatterns = [
  ["M3-05 task marker", /M3[-_]?0?5/i],
  ["force/override setter", /(?:force|override|inject|set)[A-Za-z0-9_]{0,32}(?:risk|high|profile)|(?:risk|high|profile)[A-Za-z0-9_]{0,32}(?:override|setter)/i],
  ["benchmark HIGH bridge", /benchmark.{0,48}(?:high|risk|profile)|(?:high|risk|profile).{0,48}benchmark/is],
  ["BuildConfig HIGH control", /BuildConfig[^;\n]{0,120}(?:high|risk|profile|m305)/i],
  ["system property HIGH control", /System\.getProperty[^;\n]{0,160}(?:high|risk|profile|m305)|(?:high|risk|profile|m305)[^;\n]{0,160}System\.getProperty/is],
  ["environment HIGH control", /(?:System\.getenv|\bgetenv|environmentVariable)[^;\n]{0,160}(?:high|risk|profile|m305)|(?:high|risk|profile|m305)[^;\n]{0,160}(?:System\.getenv|\bgetenv|environmentVariable)/is],
  ["manifest HIGH control", /<meta-data[^>]{0,240}(?:high|risk|profile|m305)|applicationInfo\.metaData[^;\n]{0,180}(?:high|risk|profile|m305)|(?:high|risk|profile|m305)[^;\n]{0,180}applicationInfo\.metaData/is],
  ["intent HIGH control", /(?:getStringExtra|getBooleanExtra|getIntExtra|Intent)[^;\n]{0,180}(?:high|risk|profile|m305)|(?:high|risk|profile|m305)[^;\n]{0,180}(?:getStringExtra|getBooleanExtra|getIntExtra|Intent)/is],
  ["file/preferences HIGH control", /(?:new\s+File|Files\.|SharedPreferences|getSharedPreferences)[^;\n]{0,180}(?:high|risk|profile|m305)|(?:high|risk|profile|m305)[^;\n]{0,180}(?:new\s+File|Files\.|SharedPreferences|getSharedPreferences)/is],
];

function overrideFindings(text) {
  return overridePatterns.filter(([, pattern]) => pattern.test(text)).map(([name]) => name);
}

function walkFiles(root) {
  const output = [];
  if (!fs.existsSync(root)) return output;
  const pending = [root];
  while (pending.length > 0) {
    const current = pending.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const absolute = path.join(current, entry.name);
      if (entry.isDirectory()) pending.push(absolute);
      else if (entry.isFile()) output.push(absolute);
    }
  }
  return output;
}

function scanProductionTree(root) {
  const errors = [];
  for (const top of ["runtime", "host", "fixtures", "benchmarks", "distribution"]) {
    for (const absolute of walkFiles(path.join(root, top))) {
      const relative = normalize(path.relative(root, absolute));
      if (!isProductionSurface(relative) || !relevantExtension(relative)) continue;
      const text = fs.readFileSync(absolute, "utf8");
      for (const finding of overrideFindings(text)) errors.push(`${relative}: prohibited ${finding}`);
    }
  }
  return errors;
}

function verifyNoProductionDiff(baseRef) {
  const result = spawnSync("git", ["diff", "--name-only", `${baseRef}..HEAD`], {
    cwd: repositoryRoot, encoding: "utf8", timeout: 30_000,
  });
  if (result.error || result.status !== 0) {
    return [`cannot inspect base diff ${baseRef}: ${(result.stderr || result.error?.message || "git failure").trim()}`];
  }
  return result.stdout.split(/\r?\n/).filter(Boolean).filter(isProductionSurface)
    .map(file => `${normalize(file)}: production surface changed in M3-07 diff`);
}

function finiteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function requireNull(row, fields, errors) {
  for (const field of fields) if (row[field] !== null) errors.push(`${field} must be explicit null`);
}

function nearestRank(values, quantile) {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(quantile * sorted.length) - 1)];
}

function validateRow(row, index) {
  const errors = [];
  if (row === null || typeof row !== "object" || Array.isArray(row)) return [`results[${index}] must be an object`];
  for (const field of REQUIRED_ROW_FIELDS) {
    if (!Object.hasOwn(row, field)) errors.push(`missing ${field}`);
  }
  if (typeof row.fixtureId !== "string" || row.fixtureId.length === 0) errors.push("fixtureId must be non-empty string");
  if (typeof row.environmentId !== "string" || row.environmentId.length === 0) errors.push("environmentId must be non-empty string");
  for (const field of ["p50", "p95", "baseline", "delta", "budget"]) {
    if (!finiteNumber(row[field])) errors.push(`${field} must be finite number`);
  }
  if (typeof row.pass !== "boolean") errors.push("pass must be boolean");
  if (!Array.isArray(row.samples) || row.samples.some(value => !finiteNumber(value) || value < 0)) {
    errors.push("samples must be finite non-negative numbers");
  }
  if (Array.isArray(row.samples) && row.samples.length > 0 && row.p50 !== nearestRank(row.samples, 0.50)) {
    errors.push("p50 does not match raw samples");
  }
  if (Array.isArray(row.samples) && row.samples.length > 0 && row.p95 !== nearestRank(row.samples, 0.95)) {
    errors.push("p95 does not match raw samples");
  }

  if (HOST_METRICS.has(row.metric)) {
    if (!Array.isArray(row.samples) || row.samples.length !== 10) errors.push("Host metric requires 10 samples");
    if (row.measurementMode !== null) errors.push("Host measurementMode must be null");
    requireNull(row, ["observedRiskLevel", "observedRiskAction", "riskObservationTiming", "claimType",
      "freshProcess", "sameHandle", "lookupCountBeforeUpgrade", "lookupCountAfterUpgrade",
      "cleanupPassed", "nativeJitterMs"], errors);
  } else if (OBSERVED_METRICS.has(row.metric)) {
    if (!Array.isArray(row.samples) || row.samples.length !== 30) errors.push("observed Android metric requires 30 samples");
    if (row.measurementMode !== "observed_cold_start") errors.push("observed Android metric requires observed_cold_start");
    if (row.observedRiskLevel !== "LOW" || row.observedRiskAction !== "ALLOW") errors.push("fixed observed gate requires LOW/ALLOW");
    if (row.riskObservationTiming !== "post_start") errors.push("observed risk timing must be post_start");
    requireNull(row, ["claimType", "freshProcess", "sameHandle", "lookupCountBeforeUpgrade",
      "lookupCountAfterUpgrade", "cleanupPassed", "nativeJitterMs"], errors);
  } else if (row.metric === "highProfileIncrementalMs") {
    if (!Array.isArray(row.samples) || row.samples.length !== 30
        || row.samples.some(value => !finiteNumber(value) || value < 20 || value > 250)) {
      errors.push("isolated HIGH requires 30 finite 20-250 ms wall samples");
    }
    if (row.measurementMode !== "isolated_high_upgrade") errors.push("isolated HIGH requires isolated_high_upgrade");
    if (row.claimType !== "incremental_profile") errors.push("isolated HIGH claimType must be incremental_profile");
    if (row.freshProcess !== true) errors.push("isolated HIGH requires fresh process");
    if (row.sameHandle !== true) errors.push("isolated HIGH requires same handle");
    if (row.lookupCountBeforeUpgrade !== 0 || row.lookupCountAfterUpgrade !== 1) errors.push("isolated HIGH lookup counts invalid");
    if (row.cleanupPassed !== true) errors.push("isolated HIGH cleanup must pass");
    if (!Array.isArray(row.nativeJitterMs) || row.nativeJitterMs.length !== 30
        || row.nativeJitterMs.some(value => !finiteNumber(value) || value < 20 || value > 50)) {
      errors.push("isolated HIGH requires 30 finite 20-50 ms Native jitter samples");
    }
    requireNull(row, ["observedRiskLevel", "observedRiskAction", "riskObservationTiming"], errors);
  } else {
    errors.push(`unknown metric ${String(row.metric)}`);
  }
  return errors.map(error => `results[${index}]: ${error}`);
}

function validateReportObject(report) {
  if (report === null || typeof report !== "object" || Array.isArray(report)) return ["report must be an object"];
  const errors = [];
  if (report.schemaVersion !== 1) errors.push("schemaVersion must be 1");
  if (!Array.isArray(report.results) || report.results.length === 0) errors.push("results must be non-empty array");
  else report.results.forEach((row, index) => errors.push(...validateRow(row, index)));
  return errors;
}

function validateReportFile(file) {
  try {
    return validateReportObject(JSON.parse(fs.readFileSync(path.resolve(file), "utf8")));
  } catch (failure) {
    return [`cannot parse report: ${failure.message}`];
  }
}

function requireText(file, required) {
  const text = fs.readFileSync(path.join(repositoryRoot, file), "utf8");
  return required.filter(token => !text.includes(token)).map(token => `${file}: missing ${token}`);
}

function contractErrors() {
  const errors = [];
  errors.push(...requireText("docs/adr/0014-test-only-high-benchmark-boundary.md", [
    "observed_cold_start", "isolated_high_upgrade", "must not be synthesized",
    "enumerates all Runtime/Host/production-fixture/distribution main and Release surfaces",
  ]));
  errors.push(...requireText("docs/tasks/M3-07-test-only-high-benchmark-contract.md", [
    "Issue #61", "--report <benchmark-results.json>", "highProfileIncrementalMs",
    "lookupCountBeforeUpgrade", "No product interface changes.",
  ]));
  errors.push(...requireText("docs/tasks/M3-05-size-startup-memory-benchmarks.md", [
    "  - M3-07", "measurementMode", "riskObservationTiming", "observed_cold_start",
    "isolated_high_upgrade", "不得称为真实 HIGH 冷启动", "--report <file>",
  ]));
  errors.push(...requireText("docs/TEST_STRATEGY.md", ["ADR 0014", "observed_cold_start", "isolated_high_upgrade"]));
  return errors;
}

function validReport() {
  const hostSamples = Array.from({ length: 10 }, (_, index) => 100 + index);
  const observedSamples = Array.from({ length: 30 }, (_, index) => 200 + index);
  const isolatedSamples = Array.from({ length: 30 }, (_, index) => 20 + index);
  const common = {
    fixtureId: "java-single-dex", environmentId: "self-test", p50: 0, p95: 0,
    baseline: 0, delta: 0, budget: 500, pass: true,
  };
  const summarize = (row, samples) => ({ ...row, samples, p50: nearestRank(samples, 0.50), p95: nearestRank(samples, 0.95) });
  return {
    schemaVersion: 1,
    results: [
      summarize({ ...common, measurementMode: null, observedRiskLevel: null, observedRiskAction: null,
        riskObservationTiming: null, metric: "hostProcessMs", claimType: null, freshProcess: null,
        sameHandle: null, lookupCountBeforeUpgrade: null, lookupCountAfterUpgrade: null,
        cleanupPassed: null, nativeJitterMs: null }, hostSamples),
      summarize({ ...common, measurementMode: "observed_cold_start", observedRiskLevel: "LOW",
        observedRiskAction: "ALLOW", riskObservationTiming: "post_start", metric: "processToInteractiveMs",
        claimType: null, freshProcess: null, sameHandle: null, lookupCountBeforeUpgrade: null,
        lookupCountAfterUpgrade: null, cleanupPassed: null, nativeJitterMs: null }, observedSamples),
      summarize({ ...common, measurementMode: "isolated_high_upgrade", observedRiskLevel: null,
        observedRiskAction: null, riskObservationTiming: null, metric: "highProfileIncrementalMs",
        claimType: "incremental_profile", freshProcess: true, sameHandle: true,
        lookupCountBeforeUpgrade: 0, lookupCountAfterUpgrade: 1, cleanupPassed: true,
        nativeJitterMs: Array.from({ length: 30 }, (_, index) => 20 + (index % 31)) }, isolatedSamples),
    ],
  };
}

function reportCliExit(report, directory, name) {
  const file = path.join(directory, `${name}.json`);
  fs.writeFileSync(file, `${JSON.stringify(report)}\n`, "utf8");
  return spawnSync(process.execPath, [scriptPath, "--report", file], {
    cwd: repositoryRoot, encoding: "utf8", timeout: 30_000,
  }).status;
}

function runSelfTest() {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), "aah-m3-07-"));
  try {
    const surfaceMutations = [
      ["runtime/policy/src/main/java/x/Override.java", 'System.getenv("AAH_FORCE_HIGH")'],
      ["runtime/native/src/main/AndroidManifest.xml", '<meta-data android:name="force_high"/>'],
      ["host/cli/src/main/java/x/Override.java", 'System.getProperty("m305.riskProfile")'],
      ["fixtures/android/src/m301Common/java/x/Override.java", "BuildConfig.FORCE_HIGH"],
      ["fixtures/android/proguard-rules.pro", "-keep class ah.m305.ForceHigh"],
      ["benchmarks/android/src/main/java/x/Override.kt", 'intent.getBooleanExtra("forceHigh", false)'],
      ["distribution/runtime.properties", "riskOverride=HIGH"],
      ["runtime/policy/build.gradle.kts", 'providers.environmentVariable("M305_HIGH_PROFILE")'],
      ["runtime/policy/src/main/java/x/Override.java", 'getSharedPreferences("highProfile", 0)'],
    ];
    surfaceMutations.forEach(([relative, content], index) => {
      const root = path.join(temp, `surface-${index}`);
      const file = path.join(root, relative);
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, content, "utf8");
      if (scanProductionTree(root).length === 0) throw new Error(`production surface mutation escaped: ${relative}`);
    });

    const positive = validReport();
    if (reportCliExit(positive, temp, "positive") !== 0) throw new Error("positive report CLI failed");
    const mutations = [
      report => { delete report.results[1].measurementMode; },
      report => { report.results[1].observedRiskAction = "DEGRADE"; },
      report => { report.results[1].observedRiskLevel = "HIGH"; },
      report => { report.results[1].samples = report.results[1].samples.slice(0, 29); },
      report => { report.results[1].samples.push(999); },
      report => { report.results[1].samples[0] = "oops"; },
      report => { report.results[2].claimType = "cold_start"; },
      report => { report.results[2].claimType = "startup"; },
      report => { delete report.results[2].claimType; },
      report => { report.results[2].freshProcess = false; },
      report => { report.results[2].sameHandle = null; },
      report => { report.results[2].lookupCountBeforeUpgrade = 1; },
      report => { report.results[2].cleanupPassed = false; },
      report => { report.results[2].nativeJitterMs = report.results[2].nativeJitterMs.slice(0, 29); },
      report => { report.results[2].nativeJitterMs[0] = {}; },
      report => { report.results[2].samples[0] = 251; },
      report => { report.results[0].observedRiskLevel = "LOW"; },
      report => { report.results[0].samples = report.results[0].samples.slice(0, 9); },
      report => { report.results[0].p50 = Number.NaN; },
      report => { report.results[0].p95 += 1; },
    ];
    mutations.forEach((mutate, index) => {
      const candidate = structuredClone(positive);
      mutate(candidate);
      if (reportCliExit(candidate, temp, `report-${index}`) === 0) throw new Error(`report mutation ${index} escaped`);
    });
    console.log(`OK: M3-07 mutation self-test (${surfaceMutations.length} surface + ${mutations.length} report negatives)`);
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

const errors = [...contractErrors(), ...scanProductionTree(repositoryRoot)];
if (options.baseRef !== null) errors.push(...verifyNoProductionDiff(options.baseRef));
if (options.report !== null) errors.push(...validateReportFile(options.report));
if (errors.length > 0) {
  for (const error of errors) console.error(`ERROR: ${error}`);
  process.exit(1);
}
console.log(`OK: M3-07 HIGH benchmark boundary${options.report ? " and report" : ""}`);
