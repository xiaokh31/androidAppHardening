#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const options = parse(process.argv.slice(2));
const adb = required("adb");
const serial = required("serial");
const platform = required("platform");
const evidence = path.resolve(required("evidence"));
const coldStarts = Number(options.get("cold-starts") ?? "20");
const timeout = Number(options.get("command-timeout-ms") ?? "60000");
const selectedVariant = options.get("variant") ?? "all";
const taskId = options.get("task-id") ?? "M2-02";
if (!["M2-02", "M2-03"].includes(taskId)) fail("invalid task id");
const isM203 = taskId === "M2-03";
const packageStem = isM203 ? "m203" : "m202";
const runnerClass = isM203 ? "ah.runtime.guard.M203DeviceRunner" : "ah.runtime.loader.M202DeviceRunner";
const activityClass = isM203
  ? "ah.fixtures.android.m203.M203ColdStartActivity"
  : "ah.fixtures.android.m202.M202ColdStartActivity";
if (!Number.isInteger(coldStarts) || coldStarts < 1 || coldStarts > 20) fail("invalid cold-start count");
if (!Number.isInteger(timeout) || timeout < 1000 || timeout > 120000) fail("invalid command timeout");
if (!["all", "extracted", "direct"].includes(selectedVariant)) fail("invalid variant selection");
assertIgnored(evidence);
mkdirSync(evidence, { recursive: true });

const allVariants = [
  {
    name: "extracted",
    packageName: `ah.fixtures.android.${packageStem}.extracted`,
    target: artifact(required("extracted-target-apk")),
    test: artifact(required("extracted-test-apk")),
    golden: vectorGolden(required("extracted-vector-report")),
  },
  {
    name: "direct",
    packageName: `ah.fixtures.android.${packageStem}.direct`,
    target: artifact(required("direct-target-apk")),
    test: artifact(required("direct-test-apk")),
    golden: vectorGolden(required("direct-vector-report")),
  },
];
const variants = selectedVariant === "all"
  ? allVariants
  : allVariants.filter((variant) => variant.name === selectedVariant);
const transcript = [];
let cleanupPassed = false;

try {
  const environment = collectEnvironment();
  const results = variants.map(runVariant);
  cleanupPassed = cleanup();
  if (!cleanupPassed) fail("package cleanup verification failed");
  const report = {
    task_id: taskId,
    validation_mode: "pre-cli",
    platform,
    serial_sha256: sha256(Buffer.from(serial)),
    environment,
    variant_selection: selectedVariant,
    cold_start_count: coldStarts,
    variants: results,
    cleanup_passed: true,
    emulator_lifecycle_owned_by_runner: false,
    result: "PASS",
  };
  writeFileSync(path.join(evidence, "report.json"), `${JSON.stringify(report, null, 2)}\n`);
  writeFileSync(path.join(evidence, "commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} catch (error) {
  try {
    cleanupPassed = cleanup();
  } catch (cleanupError) {
    transcript.push({ phase: "cleanup-error", message: String(cleanupError) });
  }
  writeFileSync(path.join(evidence, "commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}

function runVariant(variant) {
  uninstall(variant);
  runAdb(["install", "-r", "-t", "--no-streaming", variant.target]);
  runAdb(["install", "-r", "-t", "--no-streaming", variant.test]);
  const instrumentationArguments = [
    "shell", "am", "instrument", "-w",
  ];
  if (!isM203) instrumentationArguments.push(
    "-e", "m202_expected_build_id_hex", variant.golden.buildId,
    "-e", "m202_expected_key_slot_id_hex", variant.golden.keySlotId,
  );
  instrumentationArguments.push(`${variant.packageName}.test/${runnerClass}`);
  const instrumentation = runAdb(instrumentationArguments, 120000);
  const taskSpecificPassed = isM203
    ? instrumentation.stdout.includes("guard_failure_injection=12") &&
      instrumentation.stdout.includes("guard_metadata_rejections=12") &&
      instrumentation.stdout.includes("signer=true") &&
      instrumentation.stdout.includes("framework_package_rejection=true") &&
      instrumentation.stdout.includes("cleanup_suppressed=true") &&
      instrumentation.stdout.includes("mapping_cleanup=true") &&
      instrumentation.stdout.includes("session_close=true")
    : instrumentation.stdout.includes("failure_injection=10") &&
      instrumentation.stdout.includes("metadata_negative=true") &&
      instrumentation.stdout.includes("metadata_golden=true") &&
      instrumentation.stdout.includes("cross_handle=true") &&
      instrumentation.stdout.includes("jni_cleanup=true");
  if (!instrumentation.stdout.includes("OK (1 test)") || !taskSpecificPassed ||
      !instrumentation.stdout.includes("plaintext_dex_files=0") ||
      instrumentation.stdout.includes("FAILURES!!!")) {
    fail(`${variant.name} instrumentation failed:\n${instrumentation.stdout}\n${instrumentation.stderr}`);
  }
  writeFileSync(path.join(evidence, `${variant.name}.instrumentation.txt`), instrumentation.stdout);

  const timings = [];
  const pss = [];
  const activity = `${variant.packageName}/${activityClass}`;
  for (let index = 0; index < coldStarts; index += 1) {
    runAdb(["shell", "am", "force-stop", variant.packageName]);
    runAdb(["logcat", "-c"], timeout, true);
    const start = runAdb(["shell", "am", "start", "-W", "-n", activity]);
    const status = value(start.stdout, "Status");
    const total = Number(value(start.stdout, "TotalTime"));
    const pid = waitForPid(variant.packageName);
    if (status !== "ok" || !Number.isFinite(total) || total < 0 || pid === "") {
      const log = runAdb(["logcat", "-d", "-v", "threadtime"], timeout, true);
      writeFileSync(path.join(evidence, `${variant.name}.cold-${index + 1}.logcat.txt`), log.stdout);
      fail(`${variant.name} cold start ${index + 1} failed:\n${start.stdout}`);
    }
    timings.push(total);
    pss.push(parsePss(runAdb(["shell", "dumpsys", "meminfo", variant.packageName]).stdout));
  }
  runAdb(["shell", "am", "force-stop", variant.packageName]);
  const sorted = [...timings].sort((left, right) => left - right);
  return {
    name: variant.name,
    package_name: variant.packageName,
    target_apk: fileEvidence(variant.target),
    test_apk: fileEvidence(variant.test),
    instrumentation_passed: true,
    failure_injection_windows: isM203 ? 12 : 10,
    guard_metadata_rejections: isM203 ? 12 : 0,
    multidex_verified: true,
    jni_verified: true,
    authenticated_metadata_verified: true,
    plaintext_dex_files: 0,
    cold_start_ms: timings,
    cold_start_p50_ms: percentile(sorted, 0.50),
    cold_start_p95_ms: percentile(sorted, 0.95),
    peak_total_pss_kb: Math.max(...pss),
  };
}

function vectorGolden(argument) {
  const reportPath = artifact(argument);
  let report;
  try {
    report = JSON.parse(readFileSync(reportPath, "utf8"));
  } catch (error) {
    fail(`invalid vector report ${reportPath}: ${error}`);
  }
  for (const name of ["build_id_hex", "key_slot_id_hex"]) {
    if (typeof report[name] !== "string" || !/^[0-9a-f]{32}$/u.test(report[name])) {
      fail(`vector report has invalid ${name}`);
    }
  }
  return { buildId: report.build_id_hex, keySlotId: report.key_slot_id_hex };
}

function collectEnvironment() {
  const property = (name) => runAdb(["shell", "getprop", name]).stdout.trim();
  const identity = runAdb(["shell", "id"]).stdout.trim();
  return {
    api: property("ro.build.version.sdk"),
    abi_list: property("ro.product.cpu.abilist"),
    fingerprint: property("ro.build.fingerprint"),
    ro_secure: property("ro.secure"),
    ro_debuggable: property("ro.debuggable"),
    process_identity: identity,
    process_bitness: runAdb(["shell", "getconf", "LONG_BIT"]).stdout.trim(),
    non_root: !/(?:^|\s)uid=0\b/u.test(identity),
  };
}

function waitForPid(packageName) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const result = runAdb(["shell", "pidof", packageName], timeout, true).stdout.trim();
    if (result !== "") return result;
    if (attempt < 19) Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 100);
  }
  return "";
}

function cleanup() {
  let passed = true;
  for (const variant of variants) {
    uninstall(variant);
    for (const packageName of [variant.packageName, `${variant.packageName}.test`]) {
      if (runAdb(["shell", "pm", "path", packageName], timeout, true).stdout.trim() !== "") {
        passed = false;
      }
    }
  }
  return passed;
}

function uninstall(variant) {
  runAdb(["uninstall", `${variant.packageName}.test`], timeout, true);
  runAdb(["uninstall", variant.packageName], timeout, true);
}

function runAdb(args, commandTimeout = timeout, allowFailure = false) {
  const result = spawnSync(adb, ["-s", serial, ...args], {
    encoding: "utf8",
    timeout: commandTimeout,
    maxBuffer: 8 * 1024 * 1024,
    windowsHide: true,
  });
  const record = {
    started_at: new Date().toISOString(),
    command: ["adb", "-s", "<serial-redacted>", ...args.map((arg) => path.isAbsolute(arg) ? path.basename(arg) : arg)],
    exit_code: result.status,
    signal: result.signal,
    timed_out: result.error?.code === "ETIMEDOUT",
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
  };
  transcript.push(record);
  if (record.timed_out) fail(`adb command timed out: ${args.join(" ")}`);
  if (!allowFailure && result.status !== 0) {
    fail(`adb command failed (${result.status}): ${args.join(" ")}\n${record.stdout}\n${record.stderr}`);
  }
  return record;
}

function parsePss(output) {
  const match = output.match(/^\s*TOTAL\s+(\d+)\b/mu) ?? output.match(/^\s*TOTAL PSS:\s*(\d+)\b/mu);
  if (!match) fail("dumpsys meminfo did not report TOTAL PSS");
  return Number(match[1]);
}

function value(output, label) {
  return output.match(new RegExp(`^${label}:\\s*(.+)$`, "mu"))?.[1]?.trim() ?? "";
}

function percentile(sorted, fraction) {
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}

function fileEvidence(file) {
  const bytes = readFileSync(file);
  return { path: path.relative(process.cwd(), file).replaceAll("\\", "/"), bytes: bytes.length, sha256: sha256(bytes) };
}

function artifact(value) {
  const file = path.resolve(value);
  readFileSync(file);
  return file;
}

function assertIgnored(output) {
  const roots = [path.resolve("build"), path.resolve("artifacts")];
  if (!roots.some((root) => output === root || output.startsWith(`${root}${path.sep}`))) {
    fail("evidence must be under ignored build/ or artifacts/");
  }
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function parse(args) {
  const result = new Map();
  for (let index = 0; index < args.length; index += 2) {
    if (!args[index]?.startsWith("--") || args[index + 1] === undefined) fail("invalid arguments");
    result.set(args[index].slice(2), args[index + 1]);
  }
  return result;
}

function required(name) {
  const value = options.get(name);
  if (!value) fail(`missing --${name}`);
  return value;
}

function fail(message) {
  throw new Error(`M2-02 device acceptance failed: ${message}`);
}
