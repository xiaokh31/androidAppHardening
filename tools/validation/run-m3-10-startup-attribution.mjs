#!/usr/bin/env node

import { createHash } from "node:crypto";
import { request } from "node:https";
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const TASK_KEY = "M3-09-DIAGNOSTIC-V1";
const ENVIRONMENT = "api36-r2-x86_64-emulator-37.1.11";
const PRODUCT_TUPLE = "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd";
const PACKAGE = "ah.fixtures.android.m301.java_single";
const ACTIVITY = "ah.fixtures.android.m301.FixtureActivity";
const EXPECTED_EVENTS = ["provider.ready", "activity.create"];
const RELEASE_ROLES = ["release-bootstrap", "release-policy", "release-native", "release-fixture", "cli", "distribution"];
const REPOSITORY = "xiaokh31/androidAppHardening";
const DIAGNOSTIC_BRANCH = "feat/m3-10-startup-attribution-diagnostic";
const MAX_GITHUB_PAGE_BYTES = 4 * 1024 * 1024;
const TRACKED_LOCK_INPUTS = {
  "profile-lock.json": "tools/validation/m3-10/canonical-profile-lock.json",
  "release-artifact-lock.json": "tools/validation/m3-10/release-artifact-lock.json",
  "api36-environment-lock.json": "tools/validation/m3-10/api36-environment-lock.json",
};
const COPY_INPUTS = {
  "original-baseline.apk": "original-baseline", "original-protected.apk": "original-protected",
  "observer.dex": "observer-dex", "derivation-manifest.json": "derivation-manifest",
  "profile-baseline-unsigned.apk": "profile-baseline-unsigned",
  "profile-protected-unsigned.apk": "profile-protected-unsigned",
  "profile-baseline-aligned.apk": "profile-baseline-aligned",
  "profile-protected-aligned.apk": "profile-protected-aligned",
  "profile-baseline.apk": "profile-baseline", "profile-protected.apk": "profile-protected",
  "release-bootstrap.aar": "release-bootstrap", "release-policy.aar": "release-policy",
  "release-native.aar": "release-native", "release-fixture.apk": "release-fixture",
  "cli.zip": "cli", "distribution.jar": "distribution",
};

function fail(message) { throw new Error(`M3-10 diagnostic failed: ${message}`); }
function sha256(value) { return createHash("sha256").update(value).digest("hex"); }
function sha256File(file) { return sha256(readFileSync(file)); }
function json(file) { try { return JSON.parse(readFileSync(file, "utf8")); } catch (error) { fail(`${path.basename(file)} invalid JSON: ${error.message}`); } }
function optionsOf(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) fail("options must be --name value pairs");
    result[values[index].slice(2)] = values[index + 1];
  }
  return result;
}
function required(options, name) { if (!options[name]) fail(`--${name} is required`); return options[name]; }
function run(command, args, { timeout = 120_000, allowFailure = false, recordOutput = true, env = process.env } = {}) {
  let executable = command;
  let commandArgs = args;
  if (process.platform === "win32" && /\.(?:bat|cmd)$/i.test(command)) {
    executable = process.env.ComSpec ?? "C:\\Windows\\System32\\cmd.exe";
    commandArgs = ["/d", "/c", command, ...args];
  }
  const result = spawnSync(executable, commandArgs, {
    cwd: process.cwd(), env, encoding: "utf8", windowsHide: true, timeout, maxBuffer: 32 * 1024 * 1024,
  });
  if (result.error || (!allowFailure && result.status !== 0)) {
    fail(`${path.basename(command)} ${args[0] ?? ""} failed (${result.status ?? "START"})`);
  }
  return { status: result.status, stdout: result.stdout ?? "", stderr: result.stderr ?? "",
    evidence: recordOutput ? `${result.stdout ?? ""}${result.stderr ?? ""}` : "<output-omitted>" };
}
function sleepMs(value) { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, value); }
function sameBoot(adb, expectedPrefix) {
  const boot = run(adb, ["shell", "cat", "/proc/sys/kernel/random/boot_id"], { timeout: 30_000 }).stdout.trim();
  if (!/^[0-9a-f-]{36}$/.test(boot) || sha256(Buffer.from(boot)).slice(0, 12) !== expectedPrefix) fail("device boot identity changed");
}
function lockedFile(file, expected, label) {
  if (!existsSync(file) || !statSync(file).isFile() || statSync(file).size !== expected.sizeBytes || sha256File(file) !== expected.sha256) {
    fail(`${label} identity differs`);
  }
}

function fetchOfficialPage(url, label) {
  const token = process.env.GITHUB_TOKEN;
  if (!token || process.env.GITHUB_REPOSITORY !== REPOSITORY) fail("official GitHub API identity is unavailable");
  return new Promise((resolve, reject) => {
    const call = request(url, { method: "GET", headers: {
      Accept: "application/vnd.github+json", Authorization: `Bearer ${token}`,
      "User-Agent": "androidAppHardening-M3-10", "X-GitHub-Api-Version": "2022-11-28",
    } }, (response) => {
      const chunks = [];
      let total = 0;
      let bounded = true;
      const declared = response.headers["content-length"];
      if (declared !== undefined && (!/^[0-9]+$/u.test(declared) || Number(declared) > MAX_GITHUB_PAGE_BYTES)) {
        bounded = false;
        response.destroy();
        reject(new Error(`${label} API response length differs`));
        return;
      }
      response.on("data", (chunk) => {
        total += chunk.length;
        if (total > MAX_GITHUB_PAGE_BYTES) {
          bounded = false;
          response.destroy();
          reject(new Error(`${label} API response exceeds fixed bound`));
          return;
        }
        chunks.push(chunk);
      });
      response.on("end", () => {
        if (!bounded) return;
        if (response.statusCode !== 200) return reject(new Error(`official ${label} API returned ${response.statusCode}`));
        resolve(Buffer.concat(chunks, total));
      });
    });
    call.setTimeout(30_000, () => call.destroy(new Error(`official ${label} API timed out`)));
    call.on("error", reject);
    call.end();
  });
}

function fetchOfficialJobsPage(runId) {
  const url = new URL(`https://api.github.com/repos/${REPOSITORY}/actions/runs/${runId}/jobs?per_page=100&page=1`);
  return fetchOfficialPage(url, "jobs");
}

function fetchOfficialRunsPage() {
  const url = new URL(`https://api.github.com/repos/${REPOSITORY}/actions/runs?branch=${encodeURIComponent(DIAGNOSTIC_BRANCH)}&event=push&per_page=100&page=1`);
  return fetchOfficialPage(url, "runs");
}

function releaseArgs(output) {
  return RELEASE_ROLES.flatMap((name) => [`--${name}`, path.join(output,
    name === "cli" ? "cli.zip" : name === "distribution" ? "distribution.jar" :
      name === "release-fixture" ? "release-fixture.apk" : `${name}.aar`)]);
}

function preflight(options, output) {
  const verifier = path.resolve("tools/validation/verify-m3-10-startup-attribution.mjs");
  const common = ["--release-lock", path.join(output, "release-artifact-lock.json"),
    "--build-tools-source", path.resolve(required(options, "build-tools-source")), ...releaseArgs(output)];
  run(process.execPath, [path.resolve("tools/governance/verify-m3-11-canonical-artifact-contract.mjs"),
    "--artifact-root", path.resolve(required(options, "m3-11-artifact-root"))]);
  run(process.execPath, [verifier, "profile-lock", "--lock", path.join(output, "profile-lock.json"),
    "--original-baseline", path.join(output, "original-baseline.apk"), "--original-protected", path.join(output, "original-protected.apk"),
    "--observer-source", path.resolve("tools/validation/m3-10/profile-src/ah/runtime/profile/M310StartupTimingObserver.java"),
    "--observer-dex", path.join(output, "observer.dex"), "--derivation-manifest", path.join(output, "derivation-manifest.json"),
    "--unsigned-baseline", path.join(output, "profile-baseline-unsigned.apk"), "--unsigned-protected", path.join(output, "profile-protected-unsigned.apk"),
    "--aligned-baseline", path.join(output, "profile-baseline-aligned.apk"), "--aligned-protected", path.join(output, "profile-protected-aligned.apk"),
    "--signed-baseline", path.join(output, "profile-baseline.apk"), "--signed-protected", path.join(output, "profile-protected.apk"),
    "--apksigner", path.resolve(required(options, "apksigner")), ...common], { timeout: 180_000 });
  for (const role of ["baseline", "protected"]) {
    run(process.execPath, [verifier, "apk-pair", "--original", path.join(output, `original-${role}.apk`),
      "--profile", path.join(output, `profile-${role}.apk`), "--role", role,
      "--dexdump", path.resolve(required(options, "dexdump")), "--scratch", path.join(output, `preflight-${role}`),
      ...common], { timeout: 180_000 });
  }
  run(process.execPath, [verifier, "surface", "--dexdump", path.resolve(required(options, "dexdump")),
    "--apksigner", path.resolve(required(options, "apksigner")), ...common], { timeout: 180_000 });
  const report = path.join(output, "profile-verification.json");
  const gradleEnv = { ...process.env, M310_ORIGINAL_BASELINE: path.join(output, "original-baseline.apk"),
    M310_ORIGINAL_PROTECTED: path.join(output, "original-protected.apk"), M310_PROFILE_BASELINE: path.join(output, "profile-baseline.apk"),
    M310_PROFILE_PROTECTED: path.join(output, "profile-protected.apk"), M310_OBSERVER_DEX: path.join(output, "observer.dex"),
    M310_DERIVATION_MANIFEST: path.join(output, "derivation-manifest.json"), M310_PROFILE_LOCK: path.join(output, "profile-lock.json"),
    M310_VERIFICATION_REPORT: report };
  run(path.resolve(required(options, "gradle")), [":host:container:m310VerifyProfiles", "--offline", "--no-daemon"],
    { timeout: 600_000, env: gradleEnv });
  run(process.execPath, [verifier, "profile-report", "--report", report,
    "--original-baseline", path.join(output, "original-baseline.apk"), "--original-protected", path.join(output, "original-protected.apk"),
    "--profile-baseline", path.join(output, "profile-baseline.apk"), "--profile-protected", path.join(output, "profile-protected.apk"),
    "--profile-lock", path.join(output, "profile-lock.json")]);
}

async function identity(options, adb, output) {
  if (process.env.GITHUB_ACTIONS !== "true" || process.env.GITHUB_EVENT_NAME !== "push") fail("canonical diagnostic requires a GitHub push run");
  const value = { headSha: required(options, "head-sha"), runId: required(options, "run-id"), jobId: "",
    runAttempt: Number(required(options, "run-attempt")), environmentId: ENVIRONMENT, bootIdHashPrefix: "",
    taskKey: TASK_KEY, productTuple: PRODUCT_TUPLE };
  if (value.headSha !== process.env.GITHUB_SHA || value.runId !== process.env.GITHUB_RUN_ID || value.runAttempt !== 1 ||
      String(process.env.GITHUB_RUN_ATTEMPT) !== "1" || !/^[0-9a-f]{40}$/.test(value.headSha) ||
      !/^[1-9][0-9]*$/.test(value.runId)) fail("GitHub identity differs");
  const runsPageBytes = await fetchOfficialRunsPage();
  writeFileSync(path.join(output, "current-runs-page-1.json"), runsPageBytes);
  const runsPage = json(path.join(output, "current-runs-page-1.json"));
  if (!Number.isSafeInteger(runsPage.total_count) || runsPage.total_count < 1 || runsPage.total_count >= 100 ||
      !Array.isArray(runsPage.workflow_runs) || runsPage.workflow_runs.length !== runsPage.total_count) {
    fail("official branch runs page is incomplete");
  }
  const runMatches = runsPage.workflow_runs.filter((run) =>
    run.path === ".github/workflows/m3-09-startup-attribution.yml" &&
    run.name === `${TASK_KEY}-${PRODUCT_TUPLE}` && run.event === "push");
  if (runMatches.length !== 1) fail("first-and-only workflow run history differs");
  const currentRun = runMatches[0];
  if (String(currentRun.id) !== value.runId || currentRun.path !== ".github/workflows/m3-09-startup-attribution.yml" ||
      currentRun.head_sha !== value.headSha || currentRun.run_attempt !== 1 || currentRun.event !== "push" ||
      currentRun.name !== `${TASK_KEY}-${PRODUCT_TUPLE}` || currentRun.status !== "in_progress" ||
      currentRun.conclusion !== null) {
    fail("first-and-only current workflow run differs");
  }
  const pageBytes = await fetchOfficialJobsPage(value.runId);
  writeFileSync(path.join(output, "current-jobs-page-1.json"), pageBytes);
  const page = json(path.join(output, "current-jobs-page-1.json"));
  if (!Number.isSafeInteger(page.total_count) || page.total_count < 1 || page.total_count >= 100 ||
      !Array.isArray(page.jobs) || page.jobs.length !== page.total_count) fail("official jobs API page is incomplete");
  const matches = page.jobs.filter((candidate) => String(candidate.run_id) === value.runId &&
    candidate.name === "m3-09-startup-attribution");
  if (matches.length !== 1) fail("official current job selection differs");
  const official = matches[0];
  value.jobId = String(official.id);
  if (!/^[1-9][0-9]*$/.test(value.jobId)) fail("official current job ID differs");
  const job = Object.fromEntries(["id", "run_id", "name", "status", "conclusion", "runner_name", "labels"]
    .map((key) => [key, official[key]]));
  writeFileSync(path.join(output, "current-job.json"), `${JSON.stringify(job, null, 2)}\n`);
  if (Object.keys(job).sort().join(",") !== ["conclusion", "id", "labels", "name", "run_id", "runner_name", "status"].sort().join(",") ||
      String(job.id) !== value.jobId || String(job.run_id) !== value.runId || job.name !== "m3-09-startup-attribution" ||
      job.status !== "in_progress" || job.conclusion !== null || !Array.isArray(job.labels) || !job.labels.includes("ubuntu-24.04")) {
    fail("official current job identity differs");
  }
  const lock = json(path.join(output, "api36-environment-lock.json"));
  if (lock.environmentId !== ENVIRONMENT) fail("environment lock identity differs");
  lockedFile(path.resolve(required(options, "system-image-source")), { sizeBytes: 319, sha256: lock.systemImage.sourcePropertiesSha256 }, "system image source.properties");
  lockedFile(path.resolve(required(options, "system-image-build-prop")), { sizeBytes: 5281, sha256: lock.systemImage.buildPropSha256 }, "system image build.prop");
  lockedFile(path.resolve(required(options, "emulator-source")), { sizeBytes: 103, sha256: lock.emulator.sourcePropertiesSha256 }, "emulator source.properties");
  const sdk = run(adb, ["shell", "getprop", "ro.build.version.sdk"]).stdout.trim();
  const abi = run(adb, ["shell", "getprop", "ro.product.cpu.abi"]).stdout.trim();
  const fingerprint = run(adb, ["shell", "getprop", "ro.build.fingerprint"]).stdout.trim();
  if (sdk !== lock.systemImage.sdk || abi !== lock.systemImage.abi || fingerprint !== lock.systemImage.fingerprint) {
    fail("running device differs from the pinned environment");
  }
  const boot = run(adb, ["shell", "cat", "/proc/sys/kernel/random/boot_id"]).stdout.trim();
  if (!/^[0-9a-f-]{36}$/.test(boot)) fail("boot ID differs");
  value.bootIdHashPrefix = sha256(Buffer.from(boot)).slice(0, 12);
  return value;
}

function parseProfile(output, protectedPath) {
  const matches = [...output.matchAll(/m3_10_profile=(\{[^\r\n]+\})/g)];
  if (matches.length !== 1) fail(`profile marker count differs: ${matches.length}`);
  const value = JSON.parse(matches[0][1]);
  if (value.schemaVersion !== 1 || value.valid !== true || value.clock !== "CLOCK_BOOTTIME" || value.protected !== protectedPath ||
      !Number.isSafeInteger(value.pid) || value.pid <= 0 || !Array.isArray(value.outerNs) || value.outerNs.length !== 16 ||
      (protectedPath ? !Array.isArray(value.innerNs) || value.innerNs.length !== 9 : value.innerNs !== null) ||
      !Array.isArray(value.calibrationNs) || value.calibrationNs.length !== 15 ||
      value.calibrationNs.some((item) => !Number.isSafeInteger(item) || item < 0)) fail("profile marker differs");
  return value;
}
function parseEvents(output, completedStarts) {
  const events = [...output.matchAll(/(?:^|,\s*)event=([^,\r\n]+)/g)].map((match) => match[1].trim());
  const expected = Array.from({ length: completedStarts }, () => EXPECTED_EVENTS).flat();
  if (JSON.stringify(events) !== JSON.stringify(expected)) fail(`lifecycle event history differs at start ${completedStarts}`);
  return events.slice(-EXPECTED_EVENTS.length);
}

function packagePaths(result, label) {
  if (result.status !== 0 || result.stderr.trim() !== "") fail(`${label} package query failed`);
  const value = result.stdout.trim();
  if (value !== "" && !value.split(/\r?\n/).every((line) => /^package:\S+$/.test(line))) {
    fail(`${label} package state differs`);
  }
  return value;
}

function requireUninstallSuccess(result) {
  if (result.status !== 0 || `${result.stdout}${result.stderr}`.trim() !== "Success") fail("package uninstall failed");
}

function requireRemoteAbsence(result) {
  if (result.status !== 0 || result.stderr.trim() !== "" ||
      result.stdout.split(/\r?\n/).some((name) => name.startsWith("m3-10-"))) {
    fail("remote temporary file absence proof failed");
  }
}

function cleanupSelfTest() {
  const rejected = [];
  const expectRejected = (name, action) => {
    try { action(); } catch { rejected.push(name); return; }
    fail(`cleanup mutation was accepted: ${name}`);
  };
  packagePaths({ status: 0, stdout: "", stderr: "" }, "canonical");
  requireUninstallSuccess({ status: 0, stdout: "Success\n", stderr: "" });
  requireRemoteAbsence({ status: 0, stdout: "other-file\n", stderr: "" });
  expectRejected("pm-path-nonzero", () => packagePaths({ status: 1, stdout: "", stderr: "" }, "mutation"));
  expectRejected("pm-path-stderr", () => packagePaths({ status: 0, stdout: "", stderr: "error" }, "mutation"));
  expectRejected("pm-path-malformed", () => packagePaths({ status: 0, stdout: "unexpected", stderr: "" }, "mutation"));
  expectRejected("uninstall-nonzero", () => requireUninstallSuccess({ status: 1, stdout: "Failure", stderr: "" }));
  expectRejected("uninstall-inexact", () => requireUninstallSuccess({ status: 0, stdout: "Success extra", stderr: "" }));
  expectRejected("remote-ls-nonzero", () => requireRemoteAbsence({ status: 1, stdout: "", stderr: "" }));
  expectRejected("remote-ls-stderr", () => requireRemoteAbsence({ status: 0, stdout: "", stderr: "error" }));
  expectRejected("remote-residual", () => requireRemoteAbsence({ status: 0, stdout: "m3-10-leftover.apk", stderr: "" }));
  return { cleanupSelfTest: "PASS", rejectedMutations: rejected };
}

function proveAbsent(adb, identityValue) {
  if (identityValue) sameBoot(adb, identityValue.bootIdHashPrefix);
  const before = run(adb, ["shell", "pm", "path", PACKAGE], { timeout: 30_000, allowFailure: true, recordOutput: false });
  const installedPaths = packagePaths(before, "pre-cleanup");
  if (installedPaths !== "") {
    run(adb, ["shell", "am", "force-stop", PACKAGE], { timeout: 30_000, recordOutput: false });
    const uninstall = run(adb, ["uninstall", PACKAGE], { timeout: 60_000, allowFailure: true, recordOutput: false });
    requireUninstallSuccess(uninstall);
  }
  const absent = run(adb, ["shell", "pm", "path", PACKAGE], { timeout: 30_000, allowFailure: true, recordOutput: false });
  if (packagePaths(absent, "absence-proof") !== "") fail("package absence proof failed");
  const temporary = run(adb, ["shell", "ls", "/data/local/tmp"], { timeout: 30_000, allowFailure: true, recordOutput: false });
  requireRemoteAbsence(temporary);
  if (identityValue) sameBoot(adb, identityValue.bootIdHashPrefix);
}

function collect(adb, protectedPath, ordinal, completedStarts) {
  run(adb, ["shell", "am", "force-stop", PACKAGE], { timeout: 30_000, recordOutput: false });
  run(adb, ["logcat", "-c"], { timeout: 30_000, recordOutput: false });
  const started = run(adb, ["shell", "am", "start", "-W", "-n", `${PACKAGE}/${ACTIVITY}`], { timeout: 30_000 });
  if (!/Status:\s*ok/.test(started.stdout) || !started.stdout.includes(`${PACKAGE}/${ACTIVITY}`)) fail(`start ${ordinal} differs`);
  sleepMs(200);
  const pid = run(adb, ["shell", "pidof", "-s", PACKAGE]).stdout.trim();
  if (!/^[1-9][0-9]*$/.test(pid)) fail(`start ${ordinal} PID differs`);
  const marker = parseProfile(run(adb, ["logcat", "--pid", pid, "-d", "-s", "AAH-M3-10:I", "*:S"],
    { timeout: 30_000, recordOutput: false }).stdout, protectedPath);
  if (String(marker.pid) !== pid) fail(`start ${ordinal} marker PID differs`);
  const events = parseEvents(run(adb, ["shell", "content", "query", "--uri", `content://${PACKAGE}.events`],
    { timeout: 30_000 }).stdout, completedStarts);
  return { observation: { ordinal, outerNs: marker.outerNs, innerNs: marker.innerNs, events }, rawCalibrationNs: marker.calibrationNs };
}

function installAndCollect(adb, apk, protectedPath, identityValue) {
  proveAbsent(adb, identityValue);
  const installed = run(adb, ["install", "-t", apk], { timeout: 120_000 });
  if (!/Success/.test(installed.stdout + installed.stderr)) fail("install did not report Success");
  const warmups = [], samples = [];
  for (let index = 1; index <= 5; index++) warmups.push(collect(adb, protectedPath, index, index).observation);
  let calibrationNs = null;
  for (let index = 1; index <= 15; index++) {
    const value = collect(adb, protectedPath, index, index + 5);
    samples.push(value.observation);
    if (protectedPath && index === 1) calibrationNs = value.rawCalibrationNs;
  }
  proveAbsent(adb, identityValue);
  return { warmups, samples, calibrationNs };
}

function campaign(adb, name, order, inputs, exactIdentity) {
  sameBoot(adb, exactIdentity.bootIdHashPrefix);
  const collected = {};
  try {
    for (const mode of order) {
      collected[mode] = installAndCollect(adb, inputs[mode], mode === "protected", exactIdentity);
      sameBoot(adb, exactIdentity.bootIdHashPrefix);
    }
  } finally { proveAbsent(adb, exactIdentity); }
  if (!Array.isArray(collected.protected.calibrationNs) || collected.protected.calibrationNs.length !== 15) {
    fail("protected retained ordinal 1 calibration differs");
  }
  return { schemaVersion: 1, campaign: name, order,
    warmups: { baseline: collected.baseline.warmups, protected: collected.protected.warmups },
    samples: { baseline: collected.baseline.samples, protected: collected.protected.samples },
    calibrationNs: collected.protected.calibrationNs, maximumProtectedProbeCount: 24, identity: exactIdentity };
}

async function main(options) {
  const output = path.resolve(required(options, "output"));
  const allowed = path.resolve("build", "m3-10") + path.sep;
  if (!(output + path.sep).startsWith(allowed) || existsSync(output)) fail("output must be a new build/m3-10 directory");
  mkdirSync(output, { recursive: true });
  let complete = false;
  let exactIdentity = null;
  let deviceTouched = false;
  const adb = path.resolve(required(options, "adb"));
  try {
    for (const [name, option] of Object.entries(COPY_INPUTS)) {
      const source = path.resolve(required(options, option));
      if (!existsSync(source) || !statSync(source).isFile()) fail(`input is missing: ${name}`);
      copyFileSync(source, path.join(output, name));
    }
    for (const [name, source] of Object.entries(TRACKED_LOCK_INPUTS)) {
      copyFileSync(path.resolve(source), path.join(output, name));
    }
    preflight(options, output);
    exactIdentity = await identity(options, adb, output);
    writeFileSync(path.join(output, "ledger.json"), `${JSON.stringify({
      taskKey: TASK_KEY,
      productTuple: PRODUCT_TUPLE,
      headSha: exactIdentity.headSha,
      workflowPath: ".github/workflows/m3-09-startup-attribution.yml",
      workflowName: `${TASK_KEY}-${PRODUCT_TUPLE}`,
      jobName: "m3-09-startup-attribution",
      artifactName: "m3-09-startup-attribution-raw",
    }, null, 2)}\n`);
    deviceTouched = true;
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
      profileVerificationSha256: sha256File(path.join(output, "profile-verification.json")),
      environmentLockSha256: sha256File(path.join(output, "api36-environment-lock.json")),
      currentJobSha256: sha256File(path.join(output, "current-job.json")),
      currentJobsPageSha256: sha256File(path.join(output, "current-jobs-page-1.json")),
      currentRunsPageSha256: sha256File(path.join(output, "current-runs-page-1.json")),
      systemImageSourceSha256: sha256File(path.resolve(required(options, "system-image-source"))),
      systemImageBuildPropSha256: sha256File(path.resolve(required(options, "system-image-build-prop"))),
      emulatorSourceSha256: sha256File(path.resolve(required(options, "emulator-source"))) };
    writeFileSync(path.join(output, "probe-manifest.json"), `${JSON.stringify(probe, null, 2)}\n`);
    proveAbsent(adb, exactIdentity);
    writeFileSync(path.join(output, "cleanup.json"), `${JSON.stringify({ schemaVersion: 1, packagesAbsent: true,
      remoteFilesAbsent: true, temporarySigningAbsent: true }, null, 2)}\n`);
    const files = [...Object.keys(COPY_INPUTS), ...Object.keys(TRACKED_LOCK_INPUTS), "current-job.json", "current-jobs-page-1.json",
      "current-runs-page-1.json", "ledger.json",
      "profile-verification.json", "campaign-a.json", "campaign-b.json",
      "cleanup.json", "probe-manifest.json", "result.json"].sort();
    const manifest = { schemaVersion: 1, identity: exactIdentity, files: Object.fromEntries(files.map((name) =>
      [name, { sha256: sha256File(path.join(output, name)), size: statSync(path.join(output, name)).size }])) };
    writeFileSync(path.join(output, "artifact-manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
    complete = true;
  } finally {
    let cleanupFailure;
    try { if (deviceTouched) proveAbsent(adb, exactIdentity); } catch (error) { cleanupFailure = error; }
    if (!complete || cleanupFailure) rmSync(output, { recursive: true, force: true });
    if (cleanupFailure) throw cleanupFailure;
  }
  process.stdout.write(`${JSON.stringify({ status: "PASS", artifactRoot: path.relative(process.cwd(), output).replaceAll("\\", "/") })}\n`);
}

if (process.argv.length === 3 && process.argv[2] === "--cleanup-self-test") {
  try { process.stdout.write(`${JSON.stringify(cleanupSelfTest())}\n`); }
  catch (error) { process.stderr.write(`${error.stack ?? error}\n`); process.exitCode = 1; }
} else {
  main(optionsOf(process.argv.slice(2))).catch((error) => {
    process.stderr.write(`${error.stack ?? error}\n`);
    process.exitCode = 1;
  });
}
