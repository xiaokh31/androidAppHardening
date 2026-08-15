#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const allowed = new Set(["--self-test"]);
const unknown = process.argv.slice(2).filter(argument => !allowed.has(argument));
if (unknown.length > 0) {
  console.error(`Unknown argument(s): ${unknown.join(", ")}`);
  process.exit(2);
}

const prohibitedProductionPatterns = [
  ["task marker", /M3[-_]?0?5/i],
  ["benchmark HIGH override", /benchmark.{0,40}(?:high|risk|profile)/is],
  ["risk/profile setter", /(?:force|override|set|inject)(?:Risk|High|Profile)/i],
  ["BuildConfig override", /BuildConfig\s*\./],
  ["system-property override", /System\.getProperty\s*\(/],
  ["manifest-metadata override", /applicationInfo\.metaData/],
  ["intent override", /get(?:String|Boolean|Int)Extra\s*\(/],
  ["filesystem marker", /(?:new\s+File|Files\.)[^;]{0,120}(?:exists|isFile|read)/is],
];

function productionOverrideFindings(text) {
  return prohibitedProductionPatterns
    .filter(([, pattern]) => pattern.test(text))
    .map(([name]) => name);
}

function validateResultShape(row) {
  const errors = [];
  if (row.measurementMode === "observed_cold_start") {
    if (!/^(LOW|MEDIUM|HIGH)$/.test(row.observedRiskLevel ?? "")) {
      errors.push("observed mode requires an observed risk level");
    }
    if (!/^(ALLOW|DEGRADE)$/.test(row.observedRiskAction ?? "")) {
      errors.push("observed mode requires an observed risk action");
    }
    if (row.riskObservationTiming !== "post_start") {
      errors.push("observed mode requires post-start risk observation");
    }
    if (row.observedRiskLevel !== "LOW") errors.push("fixed observed gate requires LOW");
    if (row.forced === true) errors.push("observed cold start cannot be forced");
    if (row.metric === "highProfileIncrementalMs") {
      errors.push("observed cold start cannot carry isolated HIGH metric");
    }
  } else if (row.measurementMode === "isolated_high_upgrade") {
    if (row.metric !== "highProfileIncrementalMs") {
      errors.push("isolated HIGH requires its fixed metric");
    }
    if (row.freshProcess !== true) errors.push("isolated HIGH requires a fresh process");
    if (row.claimType === "cold_start") {
      errors.push("isolated HIGH cannot be labeled as cold start");
    }
    if (row.sameHandle !== true) errors.push("isolated HIGH requires same handle");
    if (row.lookupCountBeforeUpgrade !== 0) errors.push("isolated HIGH requires zero pre-upgrade lookup");
    if (row.lookupCountAfterUpgrade !== 1) errors.push("isolated HIGH requires one post-upgrade lookup");
    if (row.cleanupPassed !== true) errors.push("isolated HIGH requires cleanup");
    if (!Array.isArray(row.nativeJitterMs)
        || row.nativeJitterMs.length === 0
        || row.nativeJitterMs.some(value => value < 20 || value > 50)) {
      errors.push("isolated HIGH requires 20-50 ms Native jitter samples");
    }
    if (!Array.isArray(row.samples)
        || row.samples.length === 0
        || row.samples.some(value => value < 20 || value > 250)) {
      errors.push("isolated HIGH requires bounded wall samples");
    }
  } else {
    errors.push("unknown measurement mode");
  }
  return errors;
}

function requireText(file, required) {
  const text = fs.readFileSync(path.join(root, file), "utf8");
  const missing = required.filter(token => !text.includes(token));
  return { text, missing: missing.map(token => `${file}: missing ${token}`) };
}

function runSelfTest() {
  const productionMutations = [
    'System.getProperty("aah.m305.forceHigh")',
    'applicationInfo.metaData.getBoolean("forceHigh")',
    "BuildConfig.FORCE_HIGH",
    'new File("m305-high").exists()',
    "static void setRiskOverride(boolean enabled) {}",
    "static boolean benchmarkHighProfile() { return true; }",
  ];
  for (const mutation of productionMutations) {
    if (productionOverrideFindings(mutation).length === 0) {
      throw new Error(`production mutation escaped: ${mutation}`);
    }
  }

  const validObserved = {
    measurementMode: "observed_cold_start",
    observedRiskLevel: "LOW",
    observedRiskAction: "ALLOW",
    metric: "processToInteractiveMs",
    riskObservationTiming: "post_start",
    forced: false,
  };
  const validIsolated = {
    measurementMode: "isolated_high_upgrade",
    metric: "highProfileIncrementalMs",
    claimType: "incremental_profile",
    freshProcess: true,
    sameHandle: true,
    lookupCountBeforeUpgrade: 0,
    lookupCountAfterUpgrade: 1,
    cleanupPassed: true,
    nativeJitterMs: [20, 35, 50],
    samples: [20, 42, 250],
  };
  if (validateResultShape(validObserved).length !== 0
      || validateResultShape(validIsolated).length !== 0) {
    throw new Error("positive result-shape self-test failed");
  }
  const resultMutations = [
    { ...validObserved, forced: true },
    { ...validObserved, riskObservationTiming: "guard_private" },
    { ...validObserved, observedRiskLevel: "HIGH", observedRiskAction: "DEGRADE" },
    { ...validObserved, metric: "highProfileIncrementalMs" },
    { ...validIsolated, claimType: "cold_start" },
    { ...validIsolated, freshProcess: false },
    { ...validIsolated, sameHandle: false },
    { ...validIsolated, lookupCountBeforeUpgrade: 1 },
    { ...validIsolated, lookupCountAfterUpgrade: 0 },
    { ...validIsolated, cleanupPassed: false },
    { ...validIsolated, nativeJitterMs: [19] },
    { ...validIsolated, nativeJitterMs: [51] },
    { ...validIsolated, samples: [251] },
    { ...validIsolated, measurementMode: "forced_high_cold_start" },
  ];
  for (const mutation of resultMutations) {
    if (validateResultShape(mutation).length === 0) {
      throw new Error(`result mutation escaped: ${JSON.stringify(mutation)}`);
    }
  }
  console.log(`OK: M3-07 contract mutation self-test (${productionMutations.length + resultMutations.length} negatives)`);
}

if (process.argv.includes("--self-test")) {
  runSelfTest();
  process.exit(0);
}

const errors = [];
const adr = requireText("docs/adr/0014-test-only-high-benchmark-boundary.md", [
  "observed_cold_start",
  "isolated_high_upgrade",
  "No production source, public method, manifest flag, system property, persistent marker or risk-policy override is added.",
  "must not be synthesized",
]);
errors.push(...adr.missing);
const task = requireText("docs/tasks/M3-07-test-only-high-benchmark-contract.md", [
  "Issue #61",
  "highProfileIncrementalMs",
  "lookupCountBeforeUpgrade",
  "riskObservationTiming",
  "No product interface changes.",
]);
errors.push(...task.missing);
const m305 = requireText("docs/tasks/M3-05-size-startup-memory-benchmarks.md", [
  "  - M3-07",
  "measurementMode",
  "riskObservationTiming",
  "observed_cold_start",
  "isolated_high_upgrade",
  "不得称为真实 HIGH 冷启动",
]);
errors.push(...m305.missing);
const strategy = requireText("docs/TEST_STRATEGY.md", [
  "ADR 0014",
  "observed_cold_start",
  "isolated_high_upgrade",
]);
errors.push(...strategy.missing);

const productionFiles = [
  "runtime/policy/src/main/java/ah/runtime/risk/EnvironmentRiskEngine.java",
  "runtime/policy/src/main/java/ah/runtime/guard/RuntimeStartupGuard.java",
];
for (const file of productionFiles) {
  const text = fs.readFileSync(path.join(root, file), "utf8");
  for (const finding of productionOverrideFindings(text)) {
    errors.push(`${file}: prohibited ${finding}`);
  }
}

if (errors.length > 0) {
  for (const error of errors) console.error(`ERROR: ${error}`);
  process.exit(1);
}
console.log("OK: M3-07 test-only HIGH benchmark boundary");
