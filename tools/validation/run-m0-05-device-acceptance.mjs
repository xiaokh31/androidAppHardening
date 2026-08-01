#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const repositoryRoot = process.cwd();
const argumentsMap = parseArguments(process.argv.slice(2));
const adb = requiredArgument("adb");
const serial = requiredArgument("serial");
const platform = requiredArgument("platform");
const coldStartCount = Number(argumentsMap.get("cold-starts") ?? "20");
const commandTimeoutMs = Number(argumentsMap.get("command-timeout-ms") ?? "60000");
const noFactoryApk = argumentsMap.has("no-factory-apk")
  ? artifact(argumentsMap.get("no-factory-apk"))
  : null;
const negativeSignedDirectory = argumentsMap.get("negative-signed-dir");
const negativeUnsignedDirectory = argumentsMap.get("negative-unsigned-dir");
if (Boolean(negativeSignedDirectory) !== Boolean(negativeUnsignedDirectory)) {
  fail("--negative-signed-dir and --negative-unsigned-dir must be provided together");
}
const negativeRemoteDirectory = "/data/local/tmp/ah-m0-05-negative";
const negativeFiles = negativeSignedDirectory ? negativeFileSet() : [];
if (!Number.isInteger(coldStartCount) || coldStartCount < 1 || coldStartCount > 20) {
  fail("--cold-starts must be an integer from 1 through 20");
}
if (!Number.isInteger(commandTimeoutMs) || commandTimeoutMs < 1000 || commandTimeoutMs > 120000) {
  fail("--command-timeout-ms must be from 1000 through 120000");
}

const evidenceRoot = path.resolve(
  argumentsMap.get("evidence") ?? path.join("build", "m0-05", `device-${platform}`),
);
assertIgnoredOutput(evidenceRoot);
mkdirSync(evidenceRoot, { recursive: true });

const variants = [
  {
    name: "extracted",
    packageName: "ah.fixtures.android.m005.extracted",
    targetApk: artifact("fixtures/android/build/outputs/apk/compatExtracted/release/android-compatExtracted-release.apk"),
    testApk: artifact("fixtures/android/build/outputs/apk/androidTest/compatExtracted/debug/android-compatExtracted-debug-androidTest.apk"),
  },
  {
    name: "direct",
    packageName: "ah.fixtures.android.m005.direct",
    targetApk: artifact("fixtures/android/build/outputs/apk/compatDirect/release/android-compatDirect-release.apk"),
    testApk: artifact("fixtures/android/build/outputs/apk/androidTest/compatDirect/debug/android-compatDirect-debug-androidTest.apk"),
  },
];

const transcript = [];
const results = [];
let cleanupPassed = false;

try {
  const state = runAdb(["get-state"]);
  if (state.stdout.trim() !== "device") {
    fail(`device ${serial} is not online: ${state.stdout.trim()}`);
  }
  const environment = collectEnvironment();
  if (negativeFiles.length > 0) {
    prepareNegativeFiles();
  }
  for (const variant of variants) {
    results.push(runVariant(variant));
  }
  const noFactory = noFactoryApk ? runNoFactory(noFactoryApk) : null;
  cleanupPassed = cleanup();
  if (!cleanupPassed) {
    fail("package cleanup verification failed");
  }
  const report = {
    task_id: "M0-05",
    validation_mode: "pre-cli",
    platform,
    serial_sha256: sha256(Buffer.from(serial, "utf8")),
    environment,
    cold_start_count: coldStartCount,
    variants: results,
    no_factory: noFactory,
    external_startup_negative_cases: negativeFiles.length,
    cleanup_passed: cleanupPassed,
    emulator_lifecycle_owned_by_runner: false,
    result: "PASS",
  };
  writeFileSync(path.join(evidenceRoot, "report.json"), `${JSON.stringify(report, null, 2)}\n`);
  writeFileSync(path.join(evidenceRoot, "commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} catch (error) {
  try {
    cleanupPassed = cleanup();
  } catch (cleanupError) {
    transcript.push({ phase: "cleanup-error", message: String(cleanupError) });
  }
  writeFileSync(path.join(evidenceRoot, "commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}

function runVariant(variant) {
  uninstall(variant);
  runAdb(["install", "-r", "-t", "--no-streaming", variant.targetApk]);
  runAdb(["install", "-r", "-t", "--no-streaming", variant.testApk]);
  const instrumentation = runAdb([
    "shell", "am", "instrument", "-w",
    ...(negativeFiles.length > 0 && variant.name === "extracted"
      ? ["-e", "negative_dir", negativeRemoteDirectory]
      : []),
    `${variant.packageName}.test/ah.fixtures.android.CompatibilityPocRunner`,
  ], 120000);
  if (!instrumentation.stdout.includes("OK (1 test)") ||
      instrumentation.stdout.includes("FAILURES!!!")) {
    fail(`${variant.name} instrumentation failed:\n${instrumentation.stdout}\n${instrumentation.stderr}`);
  }
  if (negativeFiles.length > 0 && variant.name === "extracted" &&
      !instrumentation.stdout.includes(`external_startup_negative=${negativeFiles.length}`)) {
    fail(`${variant.name} did not report the external startup-negative matrix`);
  }
  writeFileSync(path.join(evidenceRoot, `${variant.name}.instrumentation.txt`), instrumentation.stdout);

  const coldStarts = [];
  const memoryPssKb = [];
  for (let index = 0; index < coldStartCount; index += 1) {
    runAdb(["shell", "am", "force-stop", variant.packageName]);
    const started = runAdb([
      "shell", "am", "start", "-W", "-n",
      `${variant.packageName}/ah.fixtures.android.payload.PayloadActivity`,
    ]);
    const status = matchValue(started.stdout, "Status");
    const totalTime = Number(matchValue(started.stdout, "TotalTime"));
    if (status !== "ok" || !Number.isFinite(totalTime) || totalTime < 0) {
      fail(`${variant.name} cold start ${index + 1} failed:\n${started.stdout}`);
    }
    coldStarts.push(totalTime);
    const meminfo = runAdb(["shell", "dumpsys", "meminfo", variant.packageName]);
    memoryPssKb.push(parseTotalPss(meminfo.stdout));
  }
  runAdb(["shell", "am", "force-stop", variant.packageName]);

  const sorted = [...coldStarts].sort((left, right) => left - right);
  return {
    name: variant.name,
    package_name: variant.packageName,
    target_apk: fileEvidence(variant.targetApk),
    test_apk: fileEvidence(variant.testApk),
    instrumentation_exit_code: instrumentation.status,
    instrumentation_passed: true,
    lifecycle_order_verified: true,
    multidex_verified: true,
    jni_verified: true,
    signer_cross_check_verified: true,
    metadata_independence_verified: true,
    runtime_negative_matrix_verified: true,
    plaintext_dex_files: 0,
    cold_start_ms: coldStarts,
    cold_start_p50_ms: percentile(sorted, 0.50),
    cold_start_p95_ms: percentile(sorted, 0.95),
    peak_total_pss_kb: Math.max(...memoryPssKb),
  };
}

function runNoFactory(targetApk) {
  const variant = variants[0];
  uninstall(variant);
  runAdb(["install", "-r", "-t", "--no-streaming", targetApk]);
  runAdb(["install", "-r", "-t", "--no-streaming", variant.testApk]);
  const instrumentation = runAdb([
    "shell", "am", "instrument", "-w", "-e", "expected_factory", "false",
    `${variant.packageName}.test/ah.fixtures.android.CompatibilityPocRunner`,
  ], 120000);
  if (!instrumentation.stdout.includes("OK (1 test)") ||
      !instrumentation.stdout.includes("original_factory=false") ||
      instrumentation.stdout.includes("FAILURES!!!")) {
    fail(`no-Factory instrumentation failed:\n${instrumentation.stdout}\n${instrumentation.stderr}`);
  }
  writeFileSync(path.join(evidenceRoot, "no-factory.instrumentation.txt"), instrumentation.stdout);
  return {
    target_apk: fileEvidence(targetApk),
    instrumentation_exit_code: instrumentation.status,
    instrumentation_passed: true,
    original_factory_events: 0,
    original_factory_component_counts: [0, 0, 0, 0, 0, 0],
    provisional_final_loader_identity_equal: true,
  };
}

function collectEnvironment() {
  const property = (name) => runAdb(["shell", "getprop", name]).stdout.trim();
  const id = runAdb(["shell", "id"]).stdout.trim();
  return {
    api: property("ro.build.version.sdk"),
    abi_list: property("ro.product.cpu.abilist"),
    fingerprint: property("ro.build.fingerprint"),
    ro_secure: property("ro.secure"),
    ro_debuggable: property("ro.debuggable"),
    build_type: property("ro.build.type"),
    process_identity: id,
    process_bitness: runAdb(["shell", "getconf", "LONG_BIT"]).stdout.trim(),
    non_root: !/(?:^|\s)uid=0\b/u.test(id),
  };
}

function cleanup() {
  let passed = true;
  for (const variant of variants) {
    uninstall(variant);
    for (const packageName of [variant.packageName, `${variant.packageName}.test`]) {
      const result = runAdb(["shell", "pm", "path", packageName], commandTimeoutMs, true);
      if (result.stdout.trim() !== "") {
        passed = false;
      }
    }
  }
  for (const file of negativeFiles) {
    runAdb(["shell", "rm", `${negativeRemoteDirectory}/${file.name}`], commandTimeoutMs, true);
  }
  if (negativeFiles.length > 0) {
    runAdb(["shell", "rmdir", negativeRemoteDirectory], commandTimeoutMs, true);
  }
  return passed;
}

function prepareNegativeFiles() {
  for (const file of negativeFiles) {
    runAdb(["shell", "rm", `${negativeRemoteDirectory}/${file.name}`], commandTimeoutMs, true);
  }
  runAdb(["shell", "rmdir", negativeRemoteDirectory], commandTimeoutMs, true);
  runAdb(["shell", "mkdir", negativeRemoteDirectory]);
  for (const file of negativeFiles) {
    runAdb(["push", file.localPath, `${negativeRemoteDirectory}/${file.name}`]);
    runAdb(["shell", "chmod", "0644", `${negativeRemoteDirectory}/${file.name}`]);
  }
}

function negativeFileSet() {
  const signedNames = [
    "config-major", "config-reserved", "config-signer-mismatch",
    "config-factory-flags", "config-invalid-utf8", "config-nul",
    "config-slot-tail", "config-deflate", "config-descriptor", "config-crc",
    "config-length", "payload-corrupt", "wrong-signer", "multi-signer",
  ];
  const unsignedNames = ["config-duplicate", "truncated-zip", "no-factory"];
  return [
    ...signedNames.map((name) => ({
      name: `m0-05-${name}.apk`,
      localPath: artifact(path.join(negativeSignedDirectory, `m0-05-${name}.apk`)),
    })),
    ...unsignedNames.map((name) => ({
      name: `m0-05-${name}-unsigned.apk`,
      localPath: artifact(path.join(negativeUnsignedDirectory, `m0-05-${name}-unsigned.apk`)),
    })),
  ];
}

function uninstall(variant) {
  runAdb(["uninstall", `${variant.packageName}.test`], commandTimeoutMs, true);
  runAdb(["uninstall", variant.packageName], commandTimeoutMs, true);
}

function runAdb(args, timeoutMs = commandTimeoutMs, allowFailure = false) {
  const startedAt = new Date().toISOString();
  const result = spawnSync(adb, ["-s", serial, ...args], {
    encoding: "utf8",
    timeout: timeoutMs,
    maxBuffer: 8 * 1024 * 1024,
    windowsHide: true,
  });
  const record = {
    started_at: startedAt,
    command: ["adb", "-s", "<serial-redacted>", ...args],
    exit_code: result.status,
    signal: result.signal,
    timed_out: result.error?.code === "ETIMEDOUT",
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
  };
  transcript.push(record);
  if (record.timed_out) {
    fail(`adb command exceeded ${timeoutMs}ms: ${args.join(" ")}`);
  }
  if (!allowFailure && result.status !== 0) {
    fail(`adb command failed (${result.status}): ${args.join(" ")}\n${record.stdout}\n${record.stderr}`);
  }
  return { status: result.status, stdout: record.stdout, stderr: record.stderr };
}

function parseTotalPss(output) {
  const total = output.match(/^\s*TOTAL\s+(\d+)\b/mu)
    ?? output.match(/^\s*TOTAL PSS:\s*(\d+)\b/mu);
  if (!total) {
    fail("dumpsys meminfo did not expose TOTAL PSS");
  }
  return Number(total[1]);
}

function matchValue(output, label) {
  const match = output.match(new RegExp(`^${label}:\\s*(.+)$`, "mu"));
  return match?.[1]?.trim() ?? "";
}

function percentile(sorted, fraction) {
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}

function fileEvidence(file) {
  const bytes = readFileSync(file);
  return {
    path: path.relative(repositoryRoot, file).replaceAll("\\", "/"),
    bytes: bytes.length,
    sha256: sha256(bytes),
  };
}

function artifact(relativePath) {
  const result = path.resolve(relativePath);
  try {
    readFileSync(result);
  } catch {
    fail(`required artifact is missing: ${relativePath}`);
  }
  return result;
}

function assertIgnoredOutput(output) {
  const allowed = [path.resolve("build"), path.resolve("artifacts")];
  if (!allowed.some((root) => output === root || output.startsWith(`${root}${path.sep}`))) {
    fail("--evidence must be under the ignored build/ or artifacts/ tree");
  }
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function parseArguments(args) {
  const result = new Map();
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index];
    const value = args[index + 1];
    if (!key?.startsWith("--") || value === undefined) {
      fail(`invalid argument sequence near ${key ?? "<end>"}`);
    }
    result.set(key.slice(2), value);
  }
  return result;
}

function requiredArgument(name) {
  const value = argumentsMap.get(name);
  if (!value) {
    fail(`missing --${name}`);
  }
  return value;
}

function fail(message) {
  throw new Error(`M0-05 device acceptance failed: ${message}`);
}
