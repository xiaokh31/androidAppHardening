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
const timeout = Number(options.get("command-timeout-ms") ?? "60000");
if (!Number.isInteger(timeout) || timeout < 1000 || timeout > 120000) fail("invalid timeout");
assertIgnored(evidence);
mkdirSync(evidence, { recursive: true });

const variants = [
  variant("extracted", "ah.fixtures.android.m201.extracted", true),
  variant("direct", "ah.fixtures.android.m201.direct", true),
  variant("no-factory", "ah.fixtures.android.m201.extracted", false),
];
const transcript = [];

try {
  const environment = collectEnvironment();
  const results = variants.map(runVariant);
  if (!cleanup()) fail("package cleanup verification failed");
  const report = {
    task_id: "M2-01",
    validation_mode: "pre-cli",
    platform,
    serial_sha256: sha256(Buffer.from(serial)),
    environment,
    variants: results,
    cleanup_passed: true,
    emulator_lifecycle_owned_by_runner: false,
    result: "PASS",
  };
  writeFileSync(path.join(evidence, "report.json"), `${JSON.stringify(report, null, 2)}\n`);
  writeFileSync(path.join(evidence, "commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} catch (error) {
  try { cleanup(); } catch { /* best-effort before failure is reported */ }
  writeFileSync(path.join(evidence, "commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}

function runVariant(variant) {
  uninstall(variant);
  runAdb(["install", "-r", "-t", "--no-streaming", variant.target]);
  runAdb(["install", "-r", "-t", "--no-streaming", variant.test]);
  const output = runAdb([
    "shell", "am", "instrument", "-w",
    "-e", "original_factory", String(variant.originalFactoryExpected),
    `${variant.packageName}.test/ah.fixtures.android.m201.M201DeviceRunner`,
  ], 120000);
  const requiredMarkers = [
    "m2_01_device=true", "platform_callbacks=6", "main_install=1",
    "secondary_install=1", "custom_application=true", "early_provider=true",
    "multidex=true", "jni=true", "metadata_null=true", "plaintext_dex_files=0",
    `original_factory=${variant.originalFactoryExpected}`,
    `factory_callbacks=${variant.originalFactoryExpected ? 1 : 0}`,
    "OK (1 test)", "INSTRUMENTATION_CODE: -1",
  ];
  if (requiredMarkers.some((marker) => !output.stdout.includes(marker))
      || output.stdout.includes("FAILURES!!!")) {
    fail(`${variant.name} instrumentation failed:\n${output.stdout}\n${output.stderr}`);
  }
  const instrumentation = path.join(evidence, `${variant.name}.instrumentation.txt`);
  writeFileSync(instrumentation, output.stdout);
  const vector = JSON.parse(readFileSync(variant.vector, "utf8"));
  const expectedFactory = variant.originalFactoryExpected
    ? "ah.fixtures.android.payload.OriginalAppComponentFactory"
    : null;
  if (vector.original_factory !== expectedFactory
      || vector.result !== "PASS") fail(`${variant.name} vector lacks authenticated factory`);
  return {
    name: variant.name,
    package_name: variant.packageName,
    target_apk: fileEvidence(variant.target),
    test_apk: fileEvidence(variant.test),
    vector_report: fileEvidence(variant.vector),
    instrumentation: fileEvidence(instrumentation),
    platform_callbacks: 6,
    original_factory_present: variant.originalFactoryExpected,
    original_factory_callback_count: variant.originalFactoryExpected ? 1 : 0,
    main_process_install_count: 1,
    secondary_process_install_count: 1,
    custom_application_verified: true,
    early_provider_verified: true,
    multidex_verified: true,
    jni_verified: true,
    metadata_null_verified: true,
    plaintext_dex_files: 0,
    result: "PASS",
  };
}

function variant(name, packageName, originalFactoryExpected) {
  return {
    name,
    packageName,
    originalFactoryExpected,
    target: artifact(required(`${name}-target-apk`)),
    test: artifact(required(`${name}-test-apk`)),
    vector: artifact(required(`${name}-vector-report`)),
  };
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
    non_root: !/(?:^|\s)uid=0\b/u.test(identity),
  };
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
    encoding: "utf8", timeout: commandTimeout, maxBuffer: 8 * 1024 * 1024, windowsHide: true,
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
  if (!allowFailure && result.status !== 0) fail(`adb command failed: ${args.join(" ")}\n${record.stderr}`);
  return record;
}

function artifact(value) {
  const file = path.resolve(value);
  readFileSync(file);
  return file;
}

function fileEvidence(file) {
  const bytes = readFileSync(file);
  return { path: path.relative(process.cwd(), file).replaceAll("\\", "/"), bytes: bytes.length, sha256: sha256(bytes) };
}

function sha256(bytes) { return createHash("sha256").update(bytes).digest("hex"); }
function fail(message) { throw new Error(`M2-01 device acceptance failed: ${message}`); }
function required(name) { const value = options.get(name); if (!value) fail(`missing --${name}`); return value; }
function assertIgnored(output) {
  const roots = [path.resolve("build"), path.resolve("artifacts")];
  if (!roots.some((root) => output === root || output.startsWith(`${root}${path.sep}`))) {
    fail("evidence must be under ignored build/ or artifacts/");
  }
}
function parse(args) {
  const parsed = new Map();
  for (let index = 0; index < args.length; index += 2) {
    if (!args[index]?.startsWith("--") || args[index + 1] === undefined) fail("arguments must be --name value pairs");
    parsed.set(args[index].slice(2), args[index + 1]);
  }
  return parsed;
}
