#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  statSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";

const STAGES = [
  "signer_source",
  "binding_precheck",
  "payload_open",
  "metadata_policy",
  "session_commit",
  "bootstrap_factory",
];
const THRESHOLD_NS = 30_000_000;

function fail(message) {
  throw new Error(`M2-10 diagnostic failed: ${message}`);
}

function parseOptions(values) {
  const options = {};
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) {
      fail("options must be --name value pairs");
    }
    options[values[index].slice(2)] = values[index + 1];
  }
  return options;
}

function required(options, name) {
  const value = options[name];
  if (!value) fail(`--${name} is required`);
  return value;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function sha256File(file) {
  return sha256(readFileSync(file));
}

function run(command, args, timeout = 120_000, allowFailure = false) {
  const result = spawnSync(command, args, {
    encoding: "utf8",
    timeout,
    windowsHide: true,
    maxBuffer: 4 * 1024 * 1024,
  });
  if (result.error) fail(`${path.basename(command)} could not start: ${result.error.message}`);
  if (!allowFailure && result.status !== 0) {
    fail(`${path.basename(command)} ${args[0] ?? ""} exited ${result.status}`);
  }
  return result;
}

function exactGithubIdentity(options) {
  if (process.env.GITHUB_ACTIONS !== "true") fail("the first-and-only diagnostic must run in GitHub Actions");
  const identity = {
    headSha: required(options, "head-sha"),
    runId: required(options, "run-id"),
    jobId: required(options, "job-id"),
    runAttempt: Number(required(options, "run-attempt")),
    environmentId: required(options, "environment-id"),
  };
  if (!/^[0-9a-f]{40}$/.test(identity.headSha)) fail("head SHA is invalid");
  if (!/^[1-9][0-9]*$/.test(identity.runId) || !/^[1-9][0-9]*$/.test(identity.jobId)) {
    fail("runId/jobId must be numeric GitHub identities");
  }
  if (identity.runAttempt !== 1) fail("runAttempt must equal 1");
  if (process.env.GITHUB_SHA !== identity.headSha || process.env.GITHUB_RUN_ID !== identity.runId ||
      Number(process.env.GITHUB_RUN_ATTEMPT) !== identity.runAttempt) {
    fail("GitHub environment identity differs from command identity");
  }
  return identity;
}

function adbExecutable(options) {
  if (options.adb) return path.resolve(options.adb);
  const sdk = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME;
  if (!sdk) fail("ANDROID_SDK_ROOT/ANDROID_HOME is missing");
  return path.join(sdk, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
}

function ensureOneDevice(adb) {
  const output = run(adb, ["devices"]).stdout.replace(/\r/g, "").split("\n").slice(1);
  const devices = output.filter((line) => /\tdevice$/.test(line));
  if (devices.length !== 1) fail(`exactly one authorized emulator is required, found ${devices.length}`);
}

function parseMarker(output) {
  if (!/INSTRUMENTATION_CODE:\s*-1(?:\r?$)/m.test(output)) {
    fail("instrumentation did not finish with Android RESULT_OK");
  }
  const matches = [...output.matchAll(/^m2_10_profile=(\{[^\r\n]+\})\r?$/gm)];
  if (matches.length !== 1) fail(`instrumentation emitted ${matches.length} profile markers`);
  let marker;
  try {
    marker = JSON.parse(matches[0][1]);
  } catch {
    fail("instrumentation profile marker is not JSON");
  }
  if (!Number.isInteger(marker.pid) || marker.pid <= 0 || !Array.isArray(marker.points_ns) ||
      marker.points_ns.length !== 7 || !Number.isSafeInteger(marker.runtime_ns)) {
    fail("instrumentation profile marker shape differs");
  }
  return marker;
}

function observation(marker, kind, id, sequence) {
  const pointsNs = marker.points_ns;
  for (let index = 0; index < pointsNs.length; index++) {
    if (!Number.isSafeInteger(pointsNs[index]) || pointsNs[index] <= 0 ||
        (index > 0 && pointsNs[index] < pointsNs[index - 1])) {
      fail(`observation ${sequence} is non-monotonic`);
    }
  }
  const stageDurationsNs = Object.fromEntries(
    STAGES.map((stage, index) => [stage, pointsNs[index + 1] - pointsNs[index]]),
  );
  const runtimeNs = pointsNs[6] - pointsNs[0];
  if (marker.runtime_ns !== runtimeNs ||
      Object.values(stageDurationsNs).reduce((sum, value) => sum + value, 0) !== runtimeNs) {
    fail(`observation ${sequence} does not reconcile`);
  }
  return {
    kind,
    id,
    sequence,
    pid: marker.pid,
    source: "FIRST_APPCOMPONENTFACTORY_STARTUP",
    pointsNs,
    stageDurationsNs,
    runtimeNs,
  };
}

function nearestRankP50(values) {
  const ordered = [...values].sort((left, right) => left - right);
  return ordered[Math.ceil(0.5 * ordered.length) - 1];
}

function p50s(samples, ids) {
  return Object.fromEntries(
    STAGES.map((stage) => [stage, nearestRankP50(ids.map((id) => samples[id - 1].stageDurationsNs[stage]))]),
  );
}

function selectedStage(eligible, early, late) {
  return [...eligible].sort((left, right) => {
    const score = Math.min(early[right], late[right]) - Math.min(early[left], late[left]);
    return score || STAGES.indexOf(left) - STAGES.indexOf(right);
  })[0] ?? null;
}

function writeEvidence(root, identity, bootIdHashPrefix, raw, cleanupPassed, baseline, profiling) {
  const rawDocument = { schemaVersion: 1, ...identity, bootIdHashPrefix, ...raw };
  const rawText = `${JSON.stringify(rawDocument, null, 2)}\n`;
  const rawFile = path.join(root, "runtime-startup-raw.json");
  writeFileSync(rawFile, rawText);
  const earlyIds = [1, 2, 3, 4, 5, 6, 7];
  const lateIds = [8, 9, 10, 11, 12, 13, 14, 15];
  const early = p50s(raw.samples, earlyIds);
  const late = p50s(raw.samples, lateIds);
  const eligibleStages = STAGES.filter(
    (stage) => early[stage] >= THRESHOLD_NS && late[stage] >= THRESHOLD_NS,
  );
  const chosen = selectedStage(eligibleStages, early, late);
  const report = {
    schemaVersion: 1,
    ...identity,
    bootIdHashPrefix,
    firstAndOnly: true,
    clock: "SystemClock.elapsedRealtimeNanos",
    startupPath: "REAL_FIRST_APPCOMPONENTFACTORY",
    warmupCount: 5,
    measurementCount: 15,
    eligibilityThresholdNs: THRESHOLD_NS,
    cleanupPassed,
    reportCount: 1,
    rawSampleSha256: sha256(Buffer.from(rawText)),
    baselineApkSha256: sha256File(baseline),
    profilingApkSha256: sha256File(profiling),
    partitions: {
      early: { sampleIds: earlyIds, nearestRankOneBased: 4, p50Ns: early },
      late: { sampleIds: lateIds, nearestRankOneBased: 4, p50Ns: late },
    },
    eligibleStages,
    selectedStage: chosen,
    selectionRule: "MAX_MIN_PARTITION_P50_THEN_STAGE_ORDER",
    status: chosen === null ? "BLOCKED" : "ELIGIBLE",
  };
  const reportFile = path.join(root, "runtime-startup-stages.json");
  writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);
  const files = {
    report: ["runtime-startup-stages.json", reportFile],
    raw: ["runtime-startup-raw.json", rawFile],
    baselineApk: ["baseline.apk", path.join(root, "baseline.apk")],
    profilingApk: ["profiling.apk", path.join(root, "profiling.apk")],
  };
  const manifest = {
    schemaVersion: 1,
    ...identity,
    bootIdHashPrefix,
    reportCount: 1,
    cleanupPassed,
    files: Object.fromEntries(Object.entries(files).map(([key, [name, file]]) => [
      key,
      { name, sha256: sha256File(file), size: statSync(file).size },
    ])),
  };
  writeFileSync(path.join(root, "artifact-manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
  return report;
}

function main() {
  const options = parseOptions(process.argv.slice(2));
  const identity = exactGithubIdentity(options);
  const adb = adbExecutable(options);
  const root = path.resolve(required(options, "artifact-root"));
  const baselineInput = path.resolve(required(options, "baseline-apk"));
  const profilingInput = path.resolve(required(options, "profiling-apk"));
  const testApk = path.resolve(required(options, "test-apk"));
  const targetPackage = required(options, "target-package");
  const testPackage = required(options, "test-package");
  const runner = required(options, "runner");
  for (const file of [baselineInput, profilingInput, testApk]) {
    if (!existsSync(file) || !statSync(file).isFile()) fail(`input APK is missing: ${path.basename(file)}`);
  }
  if (existsSync(root) && readdirSync(root).length !== 0) {
    fail("artifact root is not empty; refusing a replacement diagnostic");
  }
  mkdirSync(root, { recursive: true });
  const baseline = path.join(root, "baseline.apk");
  const profiling = path.join(root, "profiling.apk");
  copyFileSync(baselineInput, baseline);
  copyFileSync(profilingInput, profiling);

  ensureOneDevice(adb);
  const bootId = run(adb, ["shell", "cat", "/proc/sys/kernel/random/boot_id"]).stdout.trim();
  if (!/^[0-9a-f-]{36}$/.test(bootId)) fail("emulator boot ID is unavailable");
  const bootIdHashPrefix = sha256(Buffer.from(bootId, "utf8")).slice(0, 12);
  const component = `${testPackage}/${runner}`;
  const warmups = [];
  const samples = [];
  let cleanupPassed = false;
  try {
    run(adb, ["install", "-r", "-t", profilingInput], 120_000);
    run(adb, ["install", "-r", "-t", testApk], 120_000);
    for (let sequence = 1; sequence <= 20; sequence++) {
      run(adb, ["shell", "am", "force-stop", targetPackage], 30_000);
      const result = run(adb, ["shell", "am", "instrument", "-w", "-r", component], 120_000);
      const marker = parseMarker(result.stdout);
      if (sequence <= 5) {
        warmups.push(observation(marker, "warmup", `warmup-${sequence}`, sequence));
      } else {
        const id = sequence - 5;
        samples.push(observation(marker, "retained", id, sequence));
      }
    }
    const observationKeys = new Set([...warmups, ...samples].map((item) => `${item.pid}:${item.pointsNs[0]}`));
    if (observationKeys.size !== 20) fail("a startup transaction was reused");
    const endingBoot = run(adb, ["shell", "cat", "/proc/sys/kernel/random/boot_id"]).stdout.trim();
    if (endingBoot !== bootId) fail("emulator boot changed during the diagnostic");
  } finally {
    run(adb, ["shell", "am", "force-stop", targetPackage], 30_000, true);
    run(adb, ["uninstall", testPackage], 60_000, true);
    run(adb, ["uninstall", targetPackage], 60_000, true);
    const targetPath = run(adb, ["shell", "pm", "path", targetPackage], 30_000, true).stdout;
    const testPath = run(adb, ["shell", "pm", "path", testPackage], 30_000, true).stdout;
    cleanupPassed = !targetPath.includes("package:") && !testPath.includes("package:");
  }
  if (warmups.length !== 5 || samples.length !== 15 || !cleanupPassed) {
    fail("diagnostic samples or cleanup are incomplete");
  }
  const report = writeEvidence(
    root,
    identity,
    bootIdHashPrefix,
    { warmups, samples },
    cleanupPassed,
    baseline,
    profiling,
  );
  const validator = path.resolve("tools/validation/verify-m2-10-runtime-startup-performance.mjs");
  const validation = run(process.execPath, [
    validator,
    "validate",
    "--artifact-root", root,
    "--expected-head", identity.headSha,
    "--expected-run-id", identity.runId,
    "--expected-job-id", identity.jobId,
    "--expected-run-attempt", "1",
    "--expected-environment", identity.environmentId,
    "--expected-boot-prefix", bootIdHashPrefix,
  ]);
  process.stdout.write(validation.stdout);
  if (report.selectedStage === null) fail("no stage met the fixed two-partition eligibility threshold");
}

try {
  main();
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
