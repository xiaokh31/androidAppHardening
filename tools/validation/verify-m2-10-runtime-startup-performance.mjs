#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import {
  cpSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const STAGES = [
  "signer_source",
  "binding_precheck",
  "payload_open",
  "metadata_policy",
  "session_commit",
  "bootstrap_factory",
];
const IDENTITY_KEYS = [
  "headSha",
  "runId",
  "jobId",
  "runAttempt",
  "environmentId",
  "bootIdHashPrefix",
];
const REQUIRED_ARTIFACTS = [
  "artifact-manifest.json",
  "baseline.apk",
  "profiling.apk",
  "runtime-startup-raw.json",
  "runtime-startup-stages.json",
];
const THRESHOLD_NS = 30_000_000;
const OBSERVER = "ah/runtime/profile/M210StartupTimingObserver";

function fail(message) {
  throw new Error(`M2-10 validation failed: ${message}`);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function sha256File(file) {
  return sha256(readFileSync(file));
}

function parseJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    fail(`${path.basename(file)} is not strict JSON: ${error.message}`);
  }
}

function parseOptions(values) {
  const options = {};
  for (let index = 0; index < values.length; index += 2) {
    const key = values[index];
    const value = values[index + 1];
    if (!key?.startsWith("--") || value === undefined) fail("invalid command options");
    options[key.slice(2)] = value;
  }
  return options;
}

function requireExactKeys(value, keys, label) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    fail(`${label} must be an object`);
  }
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    fail(`${label} keys differ: ${actual.join(",")}`);
  }
}

function requireSafeInteger(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail(`${label} must be a non-negative safe integer`);
}

function identityOf(value, label) {
  const identity = {};
  for (const key of IDENTITY_KEYS) {
    if (!(key in value)) fail(`${label}.${key} is missing`);
    identity[key] = value[key];
  }
  if (!/^[0-9a-f]{40}$/.test(identity.headSha)) fail(`${label}.headSha must be lowercase SHA-1`);
  if (!/^[1-9][0-9]*$/.test(String(identity.runId))) fail(`${label}.runId is invalid`);
  if (!/^[1-9][0-9]*$/.test(String(identity.jobId))) fail(`${label}.jobId is invalid`);
  if (identity.runAttempt !== 1) fail(`${label}.runAttempt must equal 1`);
  if (!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(identity.environmentId)) {
    fail(`${label}.environmentId is invalid`);
  }
  if (!/^[0-9a-f]{12}$/.test(identity.bootIdHashPrefix)) {
    fail(`${label}.bootIdHashPrefix must be 12 lowercase hex characters`);
  }
  return identity;
}

function sameIdentity(left, right, label) {
  for (const key of IDENTITY_KEYS) {
    if (String(left[key]) !== String(right[key])) fail(`${label}.${key} differs`);
  }
}

function durationMap(points, label) {
  if (!Array.isArray(points) || points.length !== 7) fail(`${label}.pointsNs must have t0..t6`);
  for (let index = 0; index < points.length; index++) {
    requireSafeInteger(points[index], `${label}.pointsNs[${index}]`);
    if (points[index] === 0 || (index > 0 && points[index] < points[index - 1])) {
      fail(`${label}.pointsNs is not monotonic`);
    }
  }
  return Object.fromEntries(STAGES.map((stage, index) => [stage, points[index + 1] - points[index]]));
}

function validateObservation(value, label, expectedKind, expectedId, expectedSequence) {
  requireExactKeys(
    value,
    ["kind", "id", "sequence", "pid", "source", "pointsNs", "stageDurationsNs", "runtimeNs"],
    label,
  );
  if (value.kind !== expectedKind || value.id !== expectedId || value.sequence !== expectedSequence) {
    fail(`${label} acquisition identity differs`);
  }
  requireSafeInteger(value.pid, `${label}.pid`);
  if (value.pid === 0) fail(`${label}.pid must be positive`);
  if (value.source !== "FIRST_APPCOMPONENTFACTORY_STARTUP") fail(`${label}.source is not the real first startup`);
  const computed = durationMap(value.pointsNs, label);
  requireExactKeys(value.stageDurationsNs, STAGES, `${label}.stageDurationsNs`);
  for (const stage of STAGES) {
    requireSafeInteger(value.stageDurationsNs[stage], `${label}.${stage}`);
    if (value.stageDurationsNs[stage] !== computed[stage]) fail(`${label}.${stage} is not adjacent`);
  }
  const sum = STAGES.reduce((total, stage) => total + value.stageDurationsNs[stage], 0);
  const interval = value.pointsNs[6] - value.pointsNs[0];
  if (value.runtimeNs !== interval || sum !== interval) fail(`${label} does not reconcile to t6-t0`);
}

function nearestRankP50(values) {
  const ordered = [...values].sort((left, right) => left - right);
  return ordered[Math.ceil(0.5 * ordered.length) - 1];
}

function expectedP50(samples, ids) {
  return Object.fromEntries(
    STAGES.map((stage) => [
      stage,
      nearestRankP50(ids.map((id) => samples[id - 1].stageDurationsNs[stage])),
    ]),
  );
}

function validatePartition(value, label, expectedIds, expectedP50s) {
  requireExactKeys(value, ["sampleIds", "nearestRankOneBased", "p50Ns"], label);
  if (JSON.stringify(value.sampleIds) !== JSON.stringify(expectedIds)) fail(`${label}.sampleIds differs`);
  if (value.nearestRankOneBased !== 4) fail(`${label}.nearestRankOneBased must equal 4`);
  requireExactKeys(value.p50Ns, STAGES, `${label}.p50Ns`);
  for (const stage of STAGES) {
    if (value.p50Ns[stage] !== expectedP50s[stage]) fail(`${label}.p50Ns.${stage} differs`);
  }
}

function selectedStage(eligible, early, late) {
  return [...eligible].sort((left, right) => {
    const score = Math.min(early[right], late[right]) - Math.min(early[left], late[left]);
    return score || STAGES.indexOf(left) - STAGES.indexOf(right);
  })[0] ?? null;
}

function scanSensitive(value, label) {
  const text = JSON.stringify(value);
  const forbidden = [
    /[A-Za-z]:\\/,
    /\/(?:home|Users|data|sdcard|storage|proc|apex|system|vendor|product|mnt)\//,
    /-----BEGIN (?:PRIVATE KEY|CERTIFICATE)-----/,
    /(?:keystore|storePassword|keyPassword|deviceSerial)/i,
    /dex\\n03[5-9]\\0/,
  ];
  if (forbidden.some((pattern) => pattern.test(text))) fail(`${label} contains sensitive material or a user/device path`);
}

export function validateEvidence(options) {
  const root = path.resolve(options["artifact-root"] ?? fail("--artifact-root is required"));
  const reportFile = path.resolve(options.report ?? path.join(root, "runtime-startup-stages.json"));
  const rawFile = path.resolve(options.raw ?? path.join(root, "runtime-startup-raw.json"));
  const manifestFile = path.resolve(options.manifest ?? path.join(root, "artifact-manifest.json"));
  const baselineFile = path.resolve(options.baseline ?? path.join(root, "baseline.apk"));
  const profilingFile = path.resolve(options.profiling ?? path.join(root, "profiling.apk"));
  for (const file of [reportFile, rawFile, manifestFile, baselineFile, profilingFile]) {
    if (!existsSync(file) || !statSync(file).isFile()) fail(`${path.basename(file)} is missing`);
  }
  const actualNames = readdirSync(root).filter((name) => statSync(path.join(root, name)).isFile()).sort();
  if (JSON.stringify(actualNames) !== JSON.stringify(REQUIRED_ARTIFACTS)) {
    fail(`artifact set differs: ${actualNames.join(",")}`);
  }

  const report = parseJson(reportFile);
  const raw = parseJson(rawFile);
  const manifest = parseJson(manifestFile);
  scanSensitive(report, "report");
  scanSensitive(raw, "raw");
  scanSensitive(manifest, "manifest");
  const reportIdentity = identityOf(report, "report");
  const rawIdentity = identityOf(raw, "raw");
  const manifestIdentity = identityOf(manifest, "manifest");
  sameIdentity(reportIdentity, rawIdentity, "raw");
  sameIdentity(reportIdentity, manifestIdentity, "manifest");

  const expected = {
    headSha: options["expected-head"],
    runId: options["expected-run-id"],
    jobId: options["expected-job-id"],
    runAttempt: options["expected-run-attempt"] === undefined
      ? 1
      : Number(options["expected-run-attempt"]),
    environmentId: options["expected-environment"],
    bootIdHashPrefix: options["expected-boot-prefix"],
  };
  if (Object.values(expected).some((value) => value === undefined)) fail("all expected identity options are required");
  sameIdentity(reportIdentity, expected, "expectedIdentity");

  requireExactKeys(
    manifest.files,
    ["report", "raw", "baselineApk", "profilingApk"],
    "manifest.files",
  );
  const actualHashes = {
    report: sha256File(reportFile),
    raw: sha256File(rawFile),
    baselineApk: sha256File(baselineFile),
    profilingApk: sha256File(profilingFile),
  };
  const expectedNames = {
    report: "runtime-startup-stages.json",
    raw: "runtime-startup-raw.json",
    baselineApk: "baseline.apk",
    profilingApk: "profiling.apk",
  };
  for (const [name, hash] of Object.entries(actualHashes)) {
    requireExactKeys(manifest.files[name], ["name", "sha256", "size"], `manifest.files.${name}`);
    if (manifest.files[name].sha256 !== hash) fail(`manifest.files.${name}.sha256 differs`);
    const file = { report: reportFile, raw: rawFile, baselineApk: baselineFile, profilingApk: profilingFile }[name];
    if (manifest.files[name].name !== expectedNames[name]) fail(`manifest.files.${name}.name differs`);
    if (manifest.files[name].size !== statSync(file).size) fail(`manifest.files.${name}.size differs`);
  }
  if (report.rawSampleSha256 !== actualHashes.raw ||
      report.baselineApkSha256 !== actualHashes.baselineApk ||
      report.profilingApkSha256 !== actualHashes.profilingApk) {
    fail("report artifact hashes differ");
  }

  if (report.schemaVersion !== 1 || raw.schemaVersion !== 1 || manifest.schemaVersion !== 1) {
    fail("schemaVersion must equal 1");
  }
  if (report.firstAndOnly !== true || report.clock !== "SystemClock.elapsedRealtimeNanos" ||
      report.startupPath !== "REAL_FIRST_APPCOMPONENTFACTORY") {
    fail("report does not identify the first-and-only in-process startup path");
  }
  if (report.warmupCount !== 5 || report.measurementCount !== 15 ||
      report.eligibilityThresholdNs !== THRESHOLD_NS || report.cleanupPassed !== true) {
    fail("report fixed counts, threshold, or cleanup differ");
  }
  if (!Array.isArray(raw.warmups) || raw.warmups.length !== 5) fail("raw warmups must contain exactly five observations");
  if (!Array.isArray(raw.samples) || raw.samples.length !== 15) fail("raw samples must contain exactly fifteen observations");
  raw.warmups.forEach((sample, index) =>
    validateObservation(sample, `warmups[${index}]`, "warmup", `warmup-${index + 1}`, index + 1));
  raw.samples.forEach((sample, index) =>
    validateObservation(sample, `samples[${index}]`, "retained", index + 1, index + 6));
  const observationKeys = new Set(
    [...raw.warmups, ...raw.samples].map((sample) => `${sample.pid}:${sample.pointsNs[0]}`),
  );
  if (observationKeys.size !== 20) fail("a startup observation was duplicated or reused");

  const earlyIds = [1, 2, 3, 4, 5, 6, 7];
  const lateIds = [8, 9, 10, 11, 12, 13, 14, 15];
  const early = expectedP50(raw.samples, earlyIds);
  const late = expectedP50(raw.samples, lateIds);
  requireExactKeys(report.partitions, ["early", "late"], "report.partitions");
  validatePartition(report.partitions.early, "report.partitions.early", earlyIds, early);
  validatePartition(report.partitions.late, "report.partitions.late", lateIds, late);
  const eligible = STAGES.filter((stage) => early[stage] >= THRESHOLD_NS && late[stage] >= THRESHOLD_NS);
  if (JSON.stringify(report.eligibleStages) !== JSON.stringify(eligible)) fail("report.eligibleStages differs");
  const chosen = selectedStage(eligible, early, late);
  if (report.selectedStage !== chosen || report.selectionRule !== "MAX_MIN_PARTITION_P50_THEN_STAGE_ORDER") {
    fail("report selected-stage rule differs");
  }
  if (eligible.length === 0 || report.status !== "ELIGIBLE") fail("diagnostic has no eligible stage");
  if (report.reportCount !== 1 || manifest.reportCount !== 1) fail("exactly one report is required");
  if (manifest.cleanupPassed !== true) fail("manifest cleanup is not proven");
  return { selectedStage: chosen, reportSha256: actualHashes.report, rawSha256: actualHashes.raw };
}

function commandOutput(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, encoding: "utf8", windowsHide: true });
  if (result.status !== 0) fail(`${path.basename(command)} ${args.join(" ")} failed`);
  return result.stdout;
}

function extractClasses(aar, directory, jar) {
  mkdirSync(directory, { recursive: true });
  commandOutput(jar, ["xf", path.resolve(aar), "classes.jar"], directory);
  return path.join(directory, "classes.jar");
}

function validateSurface(options) {
  const root = path.resolve(options.root ?? process.cwd());
  const policyBuild = readFileSync(path.join(root, "runtime/policy/build.gradle.kts"), "utf8");
  const bootstrapBuild = readFileSync(path.join(root, "runtime/bootstrap/build.gradle.kts"), "utf8");
  const observerFile = path.join(
    root,
    "runtime/policy/src/m210Profile/java/ah/runtime/profile/M210StartupTimingObserver.java",
  );
  if (!existsSync(observerFile)) fail("profile observer source is missing");
  for (const [label, text] of [["policy", policyBuild], ["bootstrap", bootstrapBuild]]) {
    if (!text.includes('create("m210Profile")') || !text.includes("InstrumentationScope.PROJECT")) {
      fail(`${label} profile variant is not isolated`);
    }
  }
  const mainRoots = ["runtime/native/src/main", "runtime/policy/src/main", "runtime/bootstrap/src/main"];
  for (const relative of mainRoots) {
    const directory = path.join(root, relative);
    const stack = [directory];
    while (stack.length > 0) {
      const current = stack.pop();
      for (const entry of readdirSync(current, { withFileTypes: true })) {
        const file = path.join(current, entry.name);
        if (entry.isDirectory()) stack.push(file);
        else if (/\.(java|kt|cpp|h|xml)$/.test(entry.name)) {
          const text = readFileSync(file, "utf8");
          if (/M210StartupTimingObserver|m210Profile|M210StartupProfileRunner/.test(text)) {
            fail(`production source contains M2-10 observer material: ${path.relative(root, file)}`);
          }
        }
      }
    }
  }
  if (options["profile-policy-aar"]) {
    const scratch = path.resolve(options.scratch ?? path.join(root, "build/m2-10/surface"));
    const allowedScratch = `${path.resolve(root, "build")}${path.sep}`;
    if (!`${scratch}${path.sep}`.startsWith(allowedScratch)) {
      fail("surface scratch must remain under the repository build directory");
    }
    rmSync(scratch, { recursive: true, force: true });
    mkdirSync(scratch, { recursive: true });
    const javaHome = process.env.JAVA_HOME;
    if (!javaHome) fail("JAVA_HOME is required for artifact surface validation");
    const jar = path.join(javaHome, "bin", process.platform === "win32" ? "jar.exe" : "jar");
    const javap = path.join(javaHome, "bin", process.platform === "win32" ? "javap.exe" : "javap");
    const profilePolicy = extractClasses(options["profile-policy-aar"], path.join(scratch, "profile-policy"), jar);
    const profileBootstrap = extractClasses(options["profile-bootstrap-aar"], path.join(scratch, "profile-bootstrap"), jar);
    const releasePolicy = extractClasses(options["release-policy-aar"], path.join(scratch, "release-policy"), jar);
    const releaseBootstrap = extractClasses(options["release-bootstrap-aar"], path.join(scratch, "release-bootstrap"), jar);
    const profileEntries = commandOutput(jar, ["tf", profilePolicy], scratch);
    const releaseEntries = commandOutput(jar, ["tf", releasePolicy], scratch);
    if (!profileEntries.includes(`${OBSERVER}.class`) || releaseEntries.includes(`${OBSERVER}.class`)) {
      fail("observer presence differs from the profile-only contract");
    }
    const profileGuard = commandOutput(javap, ["-classpath", profilePolicy, "-c", "-p", "ah.runtime.guard.RuntimeStartupGuard"], scratch);
    const releaseGuard = commandOutput(javap, ["-classpath", releasePolicy, "-c", "-p", "ah.runtime.guard.RuntimeStartupGuard"], scratch);
    const profileCoordinator = commandOutput(javap, ["-classpath", profileBootstrap, "-c", "-p", "ah.runtime.bootstrap.HardeningBootstrap$Coordinator"], scratch);
    const releaseCoordinator = commandOutput(javap, ["-classpath", releaseBootstrap, "-c", "-p", "ah.runtime.bootstrap.HardeningBootstrap$Coordinator"], scratch);
    const guardMarks = (profileGuard.match(/M210StartupTimingObserver\.mark/g) ?? []).length;
    const bootstrapMarks = (profileCoordinator.match(/M210StartupTimingObserver\.mark/g) ?? []).length;
    if (guardMarks !== 6 || bootstrapMarks !== 1) {
      fail(`profile bytecode mark distribution differs: Guard=${guardMarks}, bootstrap=${bootstrapMarks}`);
    }
    const guardBoundaries = [
      /M210StartupTimingObserver\.mark[\s\S]{0,240}RuntimeSignerVerifier\.verify/,
      /Method sha256:[\s\S]{0,240}M210StartupTimingObserver\.mark/,
      /IntegrityChecks\.verifyPreReadSigner[\s\S]{0,240}M210StartupTimingObserver\.mark/,
      /PayloadRuntime\.openVerified[\s\S]{0,240}M210StartupTimingObserver\.mark/,
      /MemoryControls\.apply[\s\S]{0,240}M210StartupTimingObserver\.mark/,
      /M210StartupTimingObserver\.mark[\s\S]{0,120}areturn/,
    ];
    if (guardBoundaries.some((pattern) => !pattern.test(profileGuard))) {
      fail("profile Guard marks do not bind the exact t0..t5 call/return boundaries");
    }
    if (!/BootstrapResult\.ready[\s\S]{0,500}putfield[^\n]*terminalResult[\s\S]{0,300}HardeningBootstrap\$State\.READY[\s\S]{0,300}putfield[^\n]*state[\s\S]{0,200}M210StartupTimingObserver\.mark/.test(profileCoordinator)) {
      fail("profile t6 is not immediately after the committed READY state write");
    }
    if (/M210StartupTimingObserver/.test(releaseGuard + releaseCoordinator)) {
      fail("Release bytecode contains the M2-10 observer");
    }
  }
  return { sourceSurface: "PASS" };
}

function observation(kind, id, sequence, pid, base, payloadNs = 40_000_000) {
  const durations = [5_000_000, 6_000_000, payloadNs, 7_000_000, 2_000_000, 3_000_000];
  const pointsNs = [base];
  for (const duration of durations) pointsNs.push(pointsNs.at(-1) + duration);
  return {
    kind,
    id,
    sequence,
    pid,
    source: "FIRST_APPCOMPONENTFACTORY_STARTUP",
    pointsNs,
    stageDurationsNs: Object.fromEntries(STAGES.map((stage, index) => [stage, durations[index]])),
    runtimeNs: durations.reduce((sum, value) => sum + value, 0),
  };
}

function writeCanonical(directory, mutate = () => {}) {
  mkdirSync(directory, { recursive: true });
  writeFileSync(path.join(directory, "baseline.apk"), "m210-baseline");
  writeFileSync(path.join(directory, "profiling.apk"), "m210-profiling");
  const identity = {
    headSha: "a".repeat(40), runId: "1001", jobId: "2002", runAttempt: 1,
    environmentId: "api36-x86_64-r2-emulator-37.1.11", bootIdHashPrefix: "b".repeat(12),
  };
  const raw = {
    schemaVersion: 1,
    ...identity,
    warmups: Array.from({ length: 5 }, (_, index) =>
      observation("warmup", `warmup-${index + 1}`, index + 1, 3000 + index, 1_000_000_000 + index * 100_000_000)),
    samples: Array.from({ length: 15 }, (_, index) =>
      observation("retained", index + 1, index + 6, 4000 + index, 2_000_000_000 + index * 100_000_000)),
  };
  const earlyP50 = expectedP50(raw.samples, [1, 2, 3, 4, 5, 6, 7]);
  const lateP50 = expectedP50(raw.samples, [8, 9, 10, 11, 12, 13, 14, 15]);
  const report = {
    schemaVersion: 1,
    ...identity,
    firstAndOnly: true,
    clock: "SystemClock.elapsedRealtimeNanos",
    startupPath: "REAL_FIRST_APPCOMPONENTFACTORY",
    warmupCount: 5,
    measurementCount: 15,
    eligibilityThresholdNs: THRESHOLD_NS,
    cleanupPassed: true,
    reportCount: 1,
    rawSampleSha256: "",
    baselineApkSha256: sha256File(path.join(directory, "baseline.apk")),
    profilingApkSha256: sha256File(path.join(directory, "profiling.apk")),
    partitions: {
      early: { sampleIds: [1, 2, 3, 4, 5, 6, 7], nearestRankOneBased: 4, p50Ns: earlyP50 },
      late: { sampleIds: [8, 9, 10, 11, 12, 13, 14, 15], nearestRankOneBased: 4, p50Ns: lateP50 },
    },
    eligibleStages: ["payload_open"],
    selectedStage: "payload_open",
    selectionRule: "MAX_MIN_PARTITION_P50_THEN_STAGE_ORDER",
    status: "ELIGIBLE",
  };
  const state = { identity, raw, report };
  mutate(state);
  Object.assign(raw, identity);
  Object.assign(report, identity);
  const rawText = `${JSON.stringify(raw, null, 2)}\n`;
  writeFileSync(path.join(directory, "runtime-startup-raw.json"), rawText);
  report.rawSampleSha256 = sha256(Buffer.from(rawText));
  const reportText = `${JSON.stringify(report, null, 2)}\n`;
  writeFileSync(path.join(directory, "runtime-startup-stages.json"), reportText);
  const manifest = {
    schemaVersion: 1,
    ...identity,
    reportCount: 1,
    cleanupPassed: true,
    files: Object.fromEntries([
      ["report", "runtime-startup-stages.json"],
      ["raw", "runtime-startup-raw.json"],
      ["baselineApk", "baseline.apk"],
      ["profilingApk", "profiling.apk"],
    ].map(([key, name]) => [key, { name, sha256: sha256File(path.join(directory, name)), size: statSync(path.join(directory, name)).size }])),
  };
  writeFileSync(path.join(directory, "artifact-manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
  return { identity };
}

function selfTest() {
  const base = mkdtempSync(path.join(tmpdir(), "m210-validator-"));
  const canonical = path.join(base, "canonical");
  const { identity } = writeCanonical(canonical);
  const options = {
    "artifact-root": canonical,
    "expected-head": identity.headSha,
    "expected-run-id": identity.runId,
    "expected-job-id": identity.jobId,
    "expected-run-attempt": "1",
    "expected-environment": identity.environmentId,
    "expected-boot-prefix": identity.bootIdHashPrefix,
  };
  validateEvidence(options);
  const cases = [
    ["head", ({ identity: value }) => { value.headSha = "c".repeat(40); }],
    ["run", ({ identity: value }) => { value.runId = "1002"; }],
    ["job", ({ identity: value }) => { value.jobId = "2003"; }],
    ["attempt", ({ identity: value }) => { value.runAttempt = 2; }],
    ["boot", ({ identity: value }) => { value.bootIdHashPrefix = "d".repeat(12); }],
    ["environment", ({ identity: value }) => { value.environmentId = "api36-other"; }],
    ["warmups", ({ report }) => { report.warmupCount = 4; }],
    ["measurements", ({ raw }) => { raw.samples.pop(); }],
    ["sample-id", ({ raw }) => { raw.samples[5].id = 5; }],
    ["sample-order", ({ raw }) => { [raw.samples[0], raw.samples[1]] = [raw.samples[1], raw.samples[0]]; }],
    ["sample-source", ({ raw }) => { raw.samples[0].source = "MANUAL_SECOND_OPEN"; }],
    ["non-monotonic", ({ raw }) => { raw.samples[0].pointsNs[3] = raw.samples[0].pointsNs[2] - 1; }],
    ["stage-gap", ({ raw }) => { raw.samples[0].stageDurationsNs.payload_open += 1; }],
    ["runtime-sum", ({ raw }) => { raw.samples[0].runtimeNs += 1; }],
    ["partition", ({ report }) => { report.partitions.early.sampleIds[6] = 8; }],
    ["p50", ({ report }) => { report.partitions.late.p50Ns.payload_open += 1; }],
    ["rank", ({ report }) => { report.partitions.late.nearestRankOneBased = 5; }],
    ["eligibility", ({ report }) => { report.eligibleStages = []; report.selectedStage = null; }],
    ["selected", ({ report }) => { report.selectedStage = "signer_source"; }],
    ["cleanup", ({ report }) => { report.cleanupPassed = false; }],
    ["report-count", ({ report }) => { report.reportCount = 2; }],
    ["clock", ({ report }) => { report.clock = "System.nanoTime"; }],
    ["startup-path", ({ report }) => { report.startupPath = "SECOND_OPEN"; }],
    ["sensitive", ({ report }) => { report.note = "C:\\Users\\example\\secret.apk"; }],
  ];
  let rejected = 0;
  for (const [name, mutation] of cases) {
    const directory = path.join(base, name);
    writeCanonical(directory, mutation);
    try {
      validateEvidence({ ...options, "artifact-root": directory });
    } catch {
      rejected++;
      continue;
    }
    fail(`self-test mutation unexpectedly passed: ${name}`);
  }
  const hashDirectory = path.join(base, "hash");
  cpSync(canonical, hashDirectory, { recursive: true });
  writeFileSync(path.join(hashDirectory, "runtime-startup-stages.json"), "{}\n");
  try {
    validateEvidence({ ...options, "artifact-root": hashDirectory });
  } catch {
    rejected++;
  }
  const duplicateDirectory = path.join(base, "duplicate");
  cpSync(canonical, duplicateDirectory, { recursive: true });
  writeFileSync(path.join(duplicateDirectory, "second-report.json"), "{}\n");
  try {
    validateEvidence({ ...options, "artifact-root": duplicateDirectory });
  } catch {
    rejected++;
  }
  rmSync(base, { recursive: true, force: true });
  if (rejected !== cases.length + 2) fail(`self-test rejected ${rejected} mutations`);
  return { canonical: 1, rejectedMutations: rejected };
}

const [command = "", ...values] = process.argv.slice(2);
try {
  let result;
  if (command === "self-test") result = selfTest();
  else if (command === "validate") result = validateEvidence(parseOptions(values));
  else if (command === "surface") result = validateSurface(parseOptions(values));
  else fail("usage: self-test | validate --artifact-root ... | surface --root ...");
  process.stdout.write(`${JSON.stringify({ status: "PASS", ...result })}\n`);
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
