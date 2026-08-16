#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

if (process.argv.length === 3 && process.argv[2] === "--self-test") {
  requireSuccessfulInstrumentation({ stdout: "OK (1 test)\n", stderr: "" }, "success-without-final-code");
  requireSuccessfulInstrumentation({ stdout: "OK (1 test)\nINSTRUMENTATION_CODE: -1\n", stderr: "" }, "legacy-success");
  for (const stdout of ["", "FAILURES!!!\n", "Error in startupAndMemory\n", "INSTRUMENTATION_CODE: -1\n"]) {
    let rejected = false;
    try {
      requireSuccessfulInstrumentation({ stdout, stderr: "" }, "negative");
    } catch {
      rejected = true;
    }
    if (!rejected) throw new Error("instrumentation marker self-test accepted an invalid result");
  }
  console.log("OK: M3-05 instrumentation success markers");
  process.exit(0);
}

const args = new Map();
for (let index = 2; index < process.argv.length; index += 2) args.set(process.argv[index], process.argv[index + 1]);
for (const key of ["--adb", "--benchmark-apk", "--test-apk", "--targets", "--output", "--campaign"]) {
  if (!args.has(key)) throw new Error(`missing ${key}`);
}

const adb = path.resolve(args.get("--adb"));
const output = path.resolve(args.get("--output"));
mkdirSync(output, { recursive: true });
const fixtures = [
  ["java-single-dex", "ah.fixtures.android.m301.java_single"],
  ["kotlin-multidex", "ah.fixtures.android.m301.kotlin_multidex"],
  ["jni-four-abi", "ah.fixtures.android.m301.jni_four"],
];
const campaignId = args.get("--campaign");
if (campaignId !== "A" && campaignId !== "B") throw new Error("--campaign must be A or B");
const deferBudgetFailure = args.get("--defer-budget-failure") === "true";
if (args.has("--defer-budget-failure")
    && args.get("--defer-budget-failure") !== "true"
    && args.get("--defer-budget-failure") !== "false") {
  throw new Error("--defer-budget-failure must be true or false");
}
const campaignFixtures = campaignId === "A" ? fixtures : [...fixtures].reverse();
const campaignModes = campaignId === "A" ? ["baseline", "protected"] : ["protected", "baseline"];
const modeOrder = campaignId === "A" ? "baseline_then_protected" : "protected_then_baseline";
const benchmarkPackage = "ah.benchmarks.android";
const instrumentationComponent = `${benchmarkPackage}/androidx.test.runner.AndroidJUnitRunner`;
const installed = new Set([benchmarkPackage]);
const commands = [];

function run(commandArgs, { timeout = 60_000, allowFailure = false, recordOutput = true } = {}) {
  const result = spawnSync(adb, commandArgs, { encoding: "utf8", timeout, maxBuffer: 16 * 1024 * 1024 });
  const row = {
    command: ["adb", ...commandArgs.map(redact)],
    exitCode: result.status,
    stdout: recordOutput ? sanitize(result.stdout ?? "") : "<output-omitted>",
    stderr: recordOutput ? sanitize(result.stderr ?? "") : "<output-omitted>",
  };
  commands.push(row);
  if (result.error || (!allowFailure && result.status !== 0)) {
    throw new Error(`adb command failed: ${row.command.join(" ")} ${row.stderr.slice(-300)}`);
  }
  return result;
}

function install(apk) {
  const result = run(["install", "-r", "-t", "--no-incremental", apk], { timeout: 120_000 });
  if (!result.stdout.includes("Success")) throw new Error("adb install did not report Success");
}

function uninstall(packageName) {
  run(["uninstall", packageName], { allowFailure: true, timeout: 30_000 });
  installed.delete(packageName);
}

function removePrivateResult(fileName) {
  run(["shell", "run-as", benchmarkPackage, "rm", "-f", `files/${fileName}`]);
}

function requireSuccessfulInstrumentation(result, label) {
  if (!result.stdout.includes("OK (1 test)") ||
      result.stdout.includes("FAILURES!!!") ||
      result.stdout.includes("Error in ")) {
    throw new Error(`${label} instrumentation failed: ${sanitize(result.stdout)} ${sanitize(result.stderr)}`);
  }
}

function percentile(values, quantile) {
  if (!Array.isArray(values) || values.length !== 30 || values.some(value => !Number.isFinite(value) || value < 0)) {
    throw new Error("invalid Android benchmark samples");
  }
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(quantile * sorted.length) - 1)];
}

function resultRow(fixtureId, environmentId, metric, baseline, protectedValues, p50Budget, p95Budget) {
  const baselineP50 = percentile(baseline, 0.50);
  const baselineP95 = percentile(baseline, 0.95);
  const p50 = percentile(protectedValues, 0.50);
  const p95 = percentile(protectedValues, 0.95);
  const deltaP50 = p50 - baselineP50;
  const deltaP95 = p95 - baselineP95;
  return {
    fixtureId,
    environmentId,
    measurementMode: "observed_cold_start",
    observedRiskLevel: "LOW",
    observedRiskAction: "ALLOW",
    riskObservationTiming: "post_start",
    metric,
    samples: protectedValues,
    p50,
    p95,
    baseline: baselineP50,
    delta: deltaP50,
    budget: p95Budget,
    pass: deltaP50 <= p50Budget && deltaP95 <= p95Budget,
    baselineSamples: baseline,
    baselineP95,
    deltaP95,
    p50Budget,
    claimType: null,
    freshProcess: null,
    sameHandle: null,
    lookupCountBeforeUpgrade: null,
    lookupCountAfterUpgrade: null,
    cleanupPassed: null,
    nativeJitterMs: null,
  };
}

function highResultRow(fixtureId, environmentId, samples) {
  if (!Array.isArray(samples) || samples.length !== 30) throw new Error("wrong isolated HIGH sample count");
  const wall = samples.map(sample => sample.wallMillis);
  const jitter = samples.map(sample => sample.nativeJitterMillis);
  for (const sample of samples) {
    if (sample.fixtureId !== fixtureId || sample.sameHandle !== true
        || sample.lookupCountBeforeUpgrade !== 0 || sample.lookupCountAfterUpgrade !== 1
        || sample.cleanupPassed !== true || sample.wallMillis < 20 || sample.wallMillis > 250
        || sample.nativeJitterMillis < 20 || sample.nativeJitterMillis > 50) {
      throw new Error(`${fixtureId} isolated HIGH ownership or timing proof failed`);
    }
  }
  const p50 = percentile(wall, 0.50);
  const p95 = percentile(wall, 0.95);
  return {
    fixtureId,
    environmentId,
    measurementMode: "isolated_high_upgrade",
    observedRiskLevel: null,
    observedRiskAction: null,
    riskObservationTiming: null,
    metric: "highProfileIncrementalMs",
    samples: wall,
    p50,
    p95,
    baseline: 0,
    delta: p50,
    budget: 250,
    pass: p95 <= 250,
    claimType: "incremental_profile",
    freshProcess: true,
    sameHandle: true,
    lookupCountBeforeUpgrade: 0,
    lookupCountAfterUpgrade: 1,
    cleanupPassed: true,
    nativeJitterMs: jitter,
  };
}

function sanitize(text) {
  return text
    .replace(/\b[0-9a-f]{64}\b/gi, match => `${match.slice(0, 12)}<redacted>`)
    .replace(/(?:[A-Za-z]:\\|\/(?:home|data|sdcard|storage|system|vendor|product|apex|mnt)\/)[^\s"']+/g, "<path>")
    .slice(-8_000);
}

function redact(value) {
  if (/\.apk$/i.test(value)) return `<apk:${path.basename(value)}>`;
  return value;
}

function hash(file) {
  return createHash("sha256").update(readFileSync(file)).digest("hex");
}

let report;
try {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
  const targetApks = fixtures.flatMap(([fixtureId]) => ["signed-input.apk", "protected-signed.apk"]
    .map(name => path.resolve(args.get("--targets"), fixtureId, name)));
  const artifactBoundary = spawnSync(process.execPath, [
    path.resolve(repositoryRoot, "tools/validation/verify-m3-05-test-bridge-artifacts.mjs"),
    path.resolve(args.get("--test-apk")),
    path.resolve(args.get("--benchmark-apk")),
    path.resolve(repositoryRoot, "runtime/native/build/outputs/aar/native-release.aar"),
    path.resolve(repositoryRoot, "runtime/policy/build/outputs/aar/policy-release.aar"),
    path.resolve(repositoryRoot, "host/cli/build/libs/cli-0.1.0-dev.jar"),
    ...targetApks,
  ], { encoding: "utf8", timeout: 60_000 });
  if (artifactBoundary.error || artifactBoundary.status !== 0) {
    throw new Error(`test-only bridge artifact boundary failed: ${(artifactBoundary.stderr || artifactBoundary.error?.message || "unknown").slice(-500)}`);
  }
  install(path.resolve(args.get("--test-apk")));
  const raw = {};
  const high = {};
  for (const [fixtureId, packageName] of campaignFixtures) {
    raw[fixtureId] = {};
    high[fixtureId] = [];
    for (const mode of campaignModes) {
      const apkName = mode === "baseline" ? "signed-input.apk" : "protected-signed.apk";
      const apk = path.resolve(args.get("--targets"), fixtureId, apkName);
      uninstall(packageName);
      install(apk);
      installed.add(packageName);
      removePrivateResult("m3-05-result.json");
      const instrumentation = run([
        "shell", "am", "instrument", "-w",
        "-e", "androidx.benchmark.suppressErrors", "EMULATOR,NOT-PROFILEABLE",
        "-e", "class", "ah.benchmarks.android.M305StartupBenchmark#startupAndMemory",
        "-e", "fixtureId", fixtureId,
        "-e", "targetPackage", packageName,
        "-e", "mode", mode,
        instrumentationComponent,
      ], { timeout: 360_000, recordOutput: false });
      requireSuccessfulInstrumentation(instrumentation, `${fixtureId}/${mode}`);
      const pulled = run(["exec-out", "run-as", "ah.benchmarks.android", "cat", "files/m3-05-result.json"]);
      const value = JSON.parse(pulled.stdout);
      if (value.fixtureId !== fixtureId || value.mode !== mode || value.sampleCount !== 30) throw new Error("benchmark result identity mismatch");
      if (mode === "protected" && (value.observedRiskLevel !== "LOW" || value.observedRiskAction !== "ALLOW")) {
        throw new Error(`${fixtureId} fixed reference profile is not LOW/ALLOW`);
      }
      raw[fixtureId][mode] = value;
      writeFileSync(path.join(output, `${fixtureId}-${mode}.json`), `${JSON.stringify(value)}\n`, "utf8");
      if (mode === "protected") {
        for (let sampleIndex = 0; sampleIndex < 30; sampleIndex++) {
          run(["shell", "am", "force-stop", benchmarkPackage]);
          removePrivateResult("m3-05-high-result.json");
          const highInstrumentation = run([
            "shell", "am", "instrument", "-w",
            "-e", "class", "ah.benchmarks.android.M305HighProfileBridge#isolatedHighUpgrade",
            "-e", "fixtureId", fixtureId,
            "-e", "targetPackage", packageName,
            instrumentationComponent,
          ], { timeout: 120_000, recordOutput: false });
          requireSuccessfulInstrumentation(highInstrumentation, `${fixtureId} isolated HIGH sample ${sampleIndex}`);
          const highPulled = run(["exec-out", "run-as", "ah.benchmarks.android", "cat", "files/m3-05-high-result.json"]);
          high[fixtureId].push(JSON.parse(highPulled.stdout));
        }
        writeFileSync(path.join(output, `${fixtureId}-isolated-high.json`), `${JSON.stringify(high[fixtureId])}\n`, "utf8");
      }
      uninstall(packageName);
    }
  }
  const sdk = run(["shell", "getprop", "ro.build.version.sdk"]).stdout.trim();
  const abi = run(["shell", "getprop", "ro.product.cpu.abi"]).stdout.trim();
  const environmentId = `api${sdk}-${abi}`.replace(/[^a-zA-Z0-9_.-]/g, "_").toLowerCase();
  const mib = 1024 * 1024;
  const budgets = {
    processToApplicationOnCreateMs: [300, 500],
    processToInteractiveMs: [300, 500],
    peakPssBytes: [48 * mib, 64 * mib],
    nativeHeapPeakBytes: [24 * mib, 32 * mib],
    stablePssBytes: [32 * mib, 48 * mib],
  };
  const results = [];
  for (const [fixtureId] of fixtures) {
    for (const [metric, budget] of Object.entries(budgets)) {
      results.push(resultRow(fixtureId, environmentId, metric,
        raw[fixtureId].baseline[metric], raw[fixtureId].protected[metric], budget[0], budget[1]));
    }
    results.push(highResultRow(fixtureId, environmentId, high[fixtureId]));
  }
  report = {
    schemaVersion: 1,
    campaignId,
    fixtureOrder: campaignFixtures.map(([fixtureId]) => fixtureId),
    modeOrder,
    environmentId,
    policyProfile: "LOW_observed_plus_isolated_HIGH_increment",
    warmups: 5,
    measurements: 30,
    results,
    allBudgetsPass: results.every(row => row.pass),
    cleanupPassed: false,
    limitations: [
      "isolated_high_upgrade_is_incremental_only_and_is_not_a_HIGH_cold_start_claim",
      "macrobenchmark_explicitly_suppresses_only_EMULATOR_and_NOT_PROFILEABLE_for_release_reference_fixtures",
    ],
  };
} finally {
  for (const [_, packageName] of fixtures) uninstall(packageName);
  uninstall("ah.benchmarks.android.test");
  uninstall("ah.benchmarks.android");
  let cleanup = true;
  for (const packageName of [...fixtures.map(row => row[1]), "ah.benchmarks.android", "ah.benchmarks.android.test"]) {
    const check = run(["shell", "pm", "path", packageName], { allowFailure: true });
    if (check.status === 0 || check.stdout.includes("package:")) cleanup = false;
  }
  if (report) report.cleanupPassed = cleanup;
}

if (!report) throw new Error("M3-05 Android benchmark did not produce a report");
const reportPath = path.join(output, "benchmark-results.json");
writeFileSync(reportPath, `${JSON.stringify(report)}\n`, "utf8");
const contract = spawnSync(process.execPath, [
  path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../governance/verify-m3-07-high-benchmark-contract.mjs"),
  "--report", reportPath,
], { encoding: "utf8", timeout: 30_000 });
if (contract.error || contract.status !== 0) {
  throw new Error(`M3-07 report contract failed: ${(contract.stderr || contract.error?.message || "unknown").slice(-500)}`);
}
const commandsPath = path.join(output, "commands.json");
writeFileSync(commandsPath, `${JSON.stringify(commands)}\n`, "utf8");
const manifestPath = path.join(output, "sha256-manifest.txt");
writeFileSync(manifestPath, `${hash(reportPath)}  benchmark-results.json\n${hash(commandsPath)}  commands.json\n`, "utf8");

const retained = readFileSync(reportPath, "utf8") + readFileSync(commandsPath, "utf8");
if (/(?:[A-Za-z]:\\Users\\|\/(?:home|data|sdcard|storage)\/)|-----BEGIN|dex\n0|[0-9a-f]{64}/i.test(retained)) {
  throw new Error("retained M3-05 evidence contains sensitive material");
}
if (!report.cleanupPassed) throw new Error("M3-05 Android cleanup failed");
if (!report.allBudgetsPass && !deferBudgetFailure) throw new Error("M3-05 Android budget failed");
console.log(`M3-05 Android campaign ${campaignId} COMPLETE ${report.environmentId} budgets=${report.allBudgetsPass}`);
