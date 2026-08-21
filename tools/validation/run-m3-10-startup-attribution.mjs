#!/usr/bin/env node

import { createHash } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const TASK_KEY = "M3-09-DIAGNOSTIC-V1";
const ENVIRONMENT = "api36-r2-x86_64-emulator-37.1.11";
const PRODUCT_TUPLE = "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd";
const PACKAGE = "ah.fixtures.android.m301.java_single";
const ACTIVITY = "ah.fixtures.android.m301.FixtureActivity";
const EXPECTED_EVENTS = ["provider.ready", "activity.create"];
const COPY_INPUTS = {
  "original-baseline.apk": "original-baseline", "original-protected.apk": "original-protected",
  "observer.dex": "observer-dex", "derivation-manifest.json": "derivation-manifest",
  "profile-lock.json": "profile-lock", "profile-baseline-unsigned.apk": "profile-baseline-unsigned",
  "profile-protected-unsigned.apk": "profile-protected-unsigned", "profile-baseline-aligned.apk": "profile-baseline-aligned",
  "profile-protected-aligned.apk": "profile-protected-aligned", "profile-baseline.apk": "profile-baseline",
  "profile-protected.apk": "profile-protected", "profile-verification.json": "profile-verification",
  "release-bootstrap.aar": "release-bootstrap", "release-policy.aar": "release-policy",
  "release-native.aar": "release-native", "release-fixture.apk": "release-fixture",
  "cli.zip": "cli", "distribution.zip": "distribution",
};

function fail(message) { throw new Error(`M3-10 diagnostic failed: ${message}`); }
function sha256(value) { return createHash("sha256").update(value).digest("hex"); }
function sha256File(file) { return sha256(readFileSync(file)); }
function optionsOf(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) fail("options must be --name value pairs");
    result[values[index].slice(2)] = values[index + 1];
  }
  return result;
}
function required(options, name) { if (!options[name]) fail(`--${name} is required`); return options[name]; }
function run(command, args, timeout = 120_000, allowFailure = false, recordOutput = true) {
  const result = spawnSync(command, args, { encoding: "utf8", windowsHide: true, timeout, maxBuffer: 16 * 1024 * 1024 });
  if (result.error || (!allowFailure && result.status !== 0)) fail(`${path.basename(command)} ${args[0] ?? ""} failed`);
  return { status: result.status, stdout: result.stdout ?? "", stderr: result.stderr ?? "",
    evidence: recordOutput ? `${result.stdout ?? ""}${result.stderr ?? ""}` : "<output-omitted>" };
}
function sleepMs(value) { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, value); }
function nearestRank(values, percentile) { return [...values].sort((a, b) => a - b)[Math.ceil(percentile * values.length) - 1]; }

function identity(options, adb) {
  if (process.env.GITHUB_ACTIONS !== "true" || process.env.GITHUB_EVENT_NAME !== "push") fail("canonical diagnostic requires a GitHub push run");
  const value = {
    headSha: required(options, "head-sha"), runId: required(options, "run-id"), jobId: required(options, "job-id"),
    runAttempt: Number(required(options, "run-attempt")), environmentId: ENVIRONMENT, bootIdHashPrefix: "",
    taskKey: TASK_KEY, productTuple: PRODUCT_TUPLE,
  };
  if (value.headSha !== process.env.GITHUB_SHA || value.runId !== process.env.GITHUB_RUN_ID ||
      value.runAttempt !== 1 || String(process.env.GITHUB_RUN_ATTEMPT) !== "1" || !/^[0-9a-f]{40}$/.test(value.headSha) ||
      !/^[1-9][0-9]*$/.test(value.runId) || !/^[1-9][0-9]*$/.test(value.jobId)) fail("GitHub identity differs");
  const sdk = run(adb, ["shell", "getprop", "ro.build.version.sdk"]).stdout.trim();
  const abi = run(adb, ["shell", "getprop", "ro.product.cpu.abi"]).stdout.trim();
  if (sdk !== "36" || abi !== "x86_64") fail(`device differs: API=${sdk} ABI=${abi}`);
  const boot = run(adb, ["shell", "cat", "/proc/sys/kernel/random/boot_id"]).stdout.trim();
  if (!/^[0-9a-f-]{36}$/.test(boot)) fail("boot ID differs");
  value.bootIdHashPrefix = sha256(Buffer.from(boot)).slice(0, 12);
  return value;
}

function parseProfile(output, protectedPath) {
  const matches = [...output.matchAll(/m3_10_profile=(\{[^\r\n]+\})/g)];
  if (matches.length !== 1) fail(`profile marker count differs: ${matches.length}`);
  const value = JSON.parse(matches[0][1]);
  if (value.schemaVersion !== 1 || value.valid !== true || value.clock !== "CLOCK_BOOTTIME" ||
      value.protected !== protectedPath || !Number.isSafeInteger(value.pid) || value.pid <= 0 ||
      !Array.isArray(value.outerNs) || value.outerNs.length !== 16 ||
      (protectedPath ? !Array.isArray(value.innerNs) || value.innerNs.length !== 9 : value.innerNs !== null) ||
      !Array.isArray(value.calibrationNs) || value.calibrationNs.length !== 15) fail("profile marker differs");
  return value;
}

function parseEvents(output) {
  const events = [...output.matchAll(/(?:^|,\s*)event=([^,\r\n]+)/g)].map((match) => match[1].trim());
  if (JSON.stringify(events) !== JSON.stringify(EXPECTED_EVENTS)) fail(`lifecycle events differ: ${events.join(",")}`);
  return events;
}

function proveAbsent(adb) {
  run(adb, ["shell", "am", "force-stop", PACKAGE], 30_000, true, false);
  run(adb, ["uninstall", PACKAGE], 60_000, true, false);
  const absent = run(adb, ["shell", "pm", "path", PACKAGE], 30_000, true, false);
  if (absent.stdout.includes("package:")) fail("package cleanup failed");
}

function collect(adb, protectedPath, ordinal) {
  run(adb, ["shell", "am", "force-stop", PACKAGE], 30_000, false, false);
  run(adb, ["logcat", "-c"], 30_000, false, false);
  const started = run(adb, ["shell", "am", "start", "-W", "-n", `${PACKAGE}/${ACTIVITY}`], 30_000);
  if (!/Status:\s*ok/.test(started.stdout) || !started.stdout.includes(`${PACKAGE}/${ACTIVITY}`)) fail(`start ${ordinal} differs`);
  sleepMs(200);
  const pid = run(adb, ["shell", "pidof", "-s", PACKAGE]).stdout.trim();
  if (!/^[1-9][0-9]*$/.test(pid)) fail(`start ${ordinal} PID differs`);
  const marker = parseProfile(run(adb, ["logcat", "--pid", pid, "-d", "-s", "AAH-M3-10:I", "*:S"], 30_000, false, false).stdout,
    protectedPath);
  if (String(marker.pid) !== pid) fail(`start ${ordinal} marker PID differs`);
  const events = parseEvents(run(adb, ["shell", "content", "query", "--uri", `content://${PACKAGE}.events`], 30_000).stdout);
  return { observation: { ordinal, outerNs: marker.outerNs, innerNs: marker.innerNs, events },
    calibrationP95Ns: nearestRank(marker.calibrationNs, 0.95) };
}

function installAndCollect(adb, apk, protectedPath) {
  proveAbsent(adb);
  const installed = run(adb, ["install", "-t", apk], 120_000);
  if (!/Success/.test(installed.stdout + installed.stderr)) fail("install did not report Success");
  const warmups = [], samples = [], calibration = [];
  for (let index = 1; index <= 5; index++) warmups.push(collect(adb, protectedPath, index).observation);
  for (let index = 1; index <= 15; index++) {
    const value = collect(adb, protectedPath, index); samples.push(value.observation); calibration.push(value.calibrationP95Ns);
  }
  proveAbsent(adb);
  return { warmups, samples, calibration };
}

function campaign(adb, name, order, inputs, exactIdentity) {
  const collected = {};
  try {
    for (const mode of order) collected[mode] = installAndCollect(adb, inputs[mode], mode === "protected");
  } finally { proveAbsent(adb); }
  return {
    schemaVersion: 1, campaign: name, order,
    warmups: { baseline: collected.baseline.warmups, protected: collected.protected.warmups },
    samples: { baseline: collected.baseline.samples, protected: collected.protected.samples },
    calibrationNs: collected.protected.calibration, maximumProtectedProbeCount: 24, identity: exactIdentity,
  };
}

function main(options) {
  const output = path.resolve(required(options, "output"));
  const allowed = path.resolve("build", "m3-10") + path.sep;
  if (!(output + path.sep).startsWith(allowed) || existsSync(output)) fail("output must be a new build/m3-10 directory");
  mkdirSync(output, { recursive: true });
  let complete = false;
  const adb = path.resolve(required(options, "adb"));
  try {
    for (const [name, option] of Object.entries(COPY_INPUTS)) {
      const source = path.resolve(required(options, option));
      if (!existsSync(source) || !statSync(source).isFile()) fail(`input is missing: ${name}`);
      copyFileSync(source, path.join(output, name));
    }
    const exactIdentity = identity(options, adb);
    const inputs = { baseline: path.join(output, "profile-baseline.apk"), protected: path.join(output, "profile-protected.apk") };
    const campaignA = campaign(adb, "A", ["baseline", "protected"], inputs, exactIdentity);
    const campaignB = campaign(adb, "B", ["protected", "baseline"], inputs, exactIdentity);
    writeFileSync(path.join(output, "campaign-a.json"), `${JSON.stringify(campaignA, null, 2)}\n`);
    writeFileSync(path.join(output, "campaign-b.json"), `${JSON.stringify(campaignB, null, 2)}\n`);
    const verifier = path.resolve("tools/validation/verify-m3-10-startup-attribution.mjs");
    run(process.execPath, [verifier, "summarize", "--campaign-a", path.join(output, "campaign-a.json"),
      "--campaign-b", path.join(output, "campaign-b.json"), "--output", path.join(output, "result.json")]);
    const probe = { schemaVersion: 1, identity: exactIdentity,
      originalBaselineSha256: sha256File(path.join(output, "original-baseline.apk")),
      originalProtectedSha256: sha256File(path.join(output, "original-protected.apk")),
      profileBaselineSha256: sha256File(inputs.baseline), profileProtectedSha256: sha256File(inputs.protected),
      outerPoints: 16, innerPoints: 9, maximumProtectedProbeCount: 24,
      profileVerificationSha256: sha256File(path.join(output, "profile-verification.json")) };
    writeFileSync(path.join(output, "probe-manifest.json"), `${JSON.stringify(probe, null, 2)}\n`);
    proveAbsent(adb);
    writeFileSync(path.join(output, "cleanup.json"), `${JSON.stringify({ schemaVersion: 1, packagesAbsent: true,
      remoteFilesAbsent: true, temporarySigningAbsent: true }, null, 2)}\n`);
    const files = [...Object.keys(COPY_INPUTS), "campaign-a.json", "campaign-b.json", "cleanup.json", "probe-manifest.json", "result.json"].sort();
    const manifest = { schemaVersion: 1, identity: exactIdentity, files: Object.fromEntries(files.map((name) =>
      [name, { sha256: sha256File(path.join(output, name)), size: statSync(path.join(output, name)).size }])) };
    writeFileSync(path.join(output, "artifact-manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
    complete = true;
  } finally {
    let cleanupFailure;
    try { proveAbsent(adb); } catch (error) { cleanupFailure = error; }
    if (!complete || cleanupFailure) rmSync(output, { recursive: true, force: true });
    if (cleanupFailure) throw cleanupFailure;
  }
  process.stdout.write(`${JSON.stringify({ status: "PASS", artifactRoot: path.relative(process.cwd(), output).replaceAll("\\", "/") })}\n`);
}

try { main(optionsOf(process.argv.slice(2))); }
catch (error) { process.stderr.write(`${error.stack ?? error}\n`); process.exitCode = 1; }
