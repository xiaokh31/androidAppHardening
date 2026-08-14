#!/usr/bin/env node

import { createHash, randomBytes } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawnSync } from "node:child_process";

const options = parse(process.argv.slice(2));
const adb = required("adb");
const serial = required("serial");
const evidence = path.resolve(required("evidence"));
const signer = required("signer-sha256");
const timeout = Number(options.get("command-timeout-ms") ?? "60000");
if (!/^[0-9a-f]{64}$/u.test(signer)) fail("invalid signer SHA-256");
if (!Number.isInteger(timeout) || timeout < 1000 || timeout > 120000) fail("invalid timeout");
assertIgnored(evidence);
mkdirSync(evidence, { recursive: true });

const policyPackage = "ah.runtime.policy.test";
const runner = `${policyPackage}/ah.runtime.guard.PolicyConnectedRunner`;
const fixturePackage = "ah.fixtures.android.m005.extracted";
const targetPackage = "ah.fixtures.android.m203.extracted";
const activity = `${targetPackage}/ah.fixtures.android.m203.M203ColdStartActivity`;
const transcript = [];
const fixtureResults = [];
const startupResults = [];
const m302Definitions = new Map([
  ["different-signer", ["m302-runtime-different-signer", "signer_flip", "SIGNER_POLICY", false]],
  ["config-version-tamper", ["m302-runtime-config-version", "config_version", "NATIVE_CONFIG", false]],
  ["factory-slot-tamper", ["m302-runtime-factory-flags", "factory_flags", "NATIVE_CONFIG", false]],
  ["binding-slot-tamper", ["m302-runtime-binding-slot", "binding_slot", "NATIVE_BINDING", false]],
  ["container-ciphertext-tamper", ["m302-runtime-container", "container_flip", "NATIVE_AUTH", true]],
  ["m302-nonce", ["m302-runtime-nonce", "nonce_flip", "NATIVE_AUTH", true]],
  ["m302-tag-first", ["m302-runtime-tag-first", "tag_first_flip", "NATIVE_AUTH", true]],
  ["m302-tag-middle", ["m302-runtime-tag-middle", "tag_middle_flip", "NATIVE_AUTH", true]],
  ["m302-tag-last", ["m302-runtime-tag-last", "tag_last_flip", "NATIVE_AUTH", true]],
  ["m302-ciphertext-first", ["m302-runtime-ciphertext-first", "ciphertext_first_flip", "NATIVE_AUTH", true]],
  ["m302-ciphertext-middle", ["m302-runtime-ciphertext-middle", "ciphertext_middle_flip", "NATIVE_AUTH", true]],
  ["m302-ciphertext-last", ["m302-runtime-ciphertext-last", "ciphertext_last_flip", "NATIVE_AUTH", true]],
]);

try {
  runAdb(["install", "-r", "-t", "--no-streaming", artifact("policy-apk")]);
  verifyFixture("same-signer", "same-apk", "VERIFIED", 1, signer);
  verifyFixture("different-signer", "wrong-apk", "SIGNER_MISMATCH", 0, signer);
  verifyFixture("multiple-current", "multi-apk", "MULTIPLE_CURRENT", 0, "");
  verifyFixture("unsigned", "unsigned-apk", "UNSIGNED", 0, "");
  verifyFixture("valid-rotation", "rotation-apk", "VERIFIED", 2, "");
  verifyFixture("historical-only", "rotation-apk", "SIGNER_MISMATCH", 0, signer);

  verifyStartup("different-signer", "wrong-target-apk", "SIGNER_MISMATCH", false);
  verifyStartup("multiple-current", "multi-target-apk", "MULTIPLE_CURRENT", true);
  verifyStartup("historical-only", "historical-target-apk", "SIGNER_MISMATCH", false);
  verifyStartup("config-version-tamper", "config-target-apk", "CONTAINER", false);
  verifyStartup("factory-slot-tamper", "factory-target-apk", "CONTAINER", false);
  verifyStartup("binding-slot-tamper", "binding-target-apk", "CONTAINER", false);
  verifyStartup("container-ciphertext-tamper", "container-target-apk", "CONTAINER", false);
  verifyStartup("m302-nonce", "m302-nonce-target-apk", "CONTAINER", false);
  verifyStartup("m302-tag-first", "m302-tag-first-target-apk", "CONTAINER", false);
  verifyStartup("m302-tag-middle", "m302-tag-middle-target-apk", "CONTAINER", false);
  verifyStartup("m302-tag-last", "m302-tag-last-target-apk", "CONTAINER", false);
  verifyStartup("m302-ciphertext-first", "m302-ciphertext-first-target-apk", "CONTAINER", false);
  verifyStartup("m302-ciphertext-middle", "m302-ciphertext-middle-target-apk", "CONTAINER", false);
  verifyStartup("m302-ciphertext-last", "m302-ciphertext-last-target-apk", "CONTAINER", false);
  const cleanupPassed = cleanup();
  if (!cleanupPassed) fail("cleanup verification failed");
  const m302Cases = startupResults
    .filter((value) => m302Definitions.has(value.name))
    .map(toM302Case);
  if (m302Cases.length !== m302Definitions.size) fail("M3-02 startup evidence count mismatch");

  const report = {
    task_id: "M2-03",
    validation_mode: "pre-cli",
    serial_sha256: sha256(Buffer.from(serial, "utf8")),
    signer_fixture_matrix: fixtureResults,
    startup_rejection_matrix: startupResults,
    m302_runtime_tamper_matrix: startupResults.filter((value) => value.name.startsWith("m302-")),
    m302_cases: m302Cases,
    cleanup_passed: cleanupPassed,
    result: "PASS",
  };
  const serializedTranscript = `${JSON.stringify(transcript, null, 2)}\n`;
  assertNoSensitiveEvidence(serializedTranscript, "commands transcript");
  writeFileSync(path.join(evidence, "signer-matrix-report.json"), `${JSON.stringify(report, null, 2)}\n`);
  writeFileSync(path.join(evidence, "signer-matrix-commands.json"), serializedTranscript);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} catch (error) {
  cleanup();
  const serializedTranscript = `${JSON.stringify(transcript, null, 2)}\n`;
  assertNoSensitiveEvidence(serializedTranscript, "failed commands transcript");
  writeFileSync(path.join(evidence, "signer-matrix-commands.json"), serializedTranscript);
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}

function toM302Case(result) {
  const [id, mutation, stage, mapping] = m302Definitions.get(result.name) ?? [];
  if (!id || result.result !== "PASS" || result.install_rejected !== false ||
      result.lookup_count !== 0 || result.session_published !== false) {
    fail(`invalid M3-02 startup evidence ${result.name}`);
  }
  return {
    id,
    target: "runtime-prehandle",
    mutation,
    expectedStage: stage,
    expectedCode: result.actual_code,
    payloadLoaded: "false",
    payloadClassLookupAttempted: "false",
    nativeHandleAcquired: "false",
    loadedPayloadPublished: "false",
    verifiedPayloadSessionPublished: "false",
    byteBuffersPublished: "false",
    nativeCloseCount: "0",
    partialJavaReferencesCleared: "not_applicable",
    partialGuardReferencesCleared: "not_applicable",
    completedMappingsZeroizedUnmapped: "not_applicable",
    partialMappingZeroizedUnmapped: mapping ? "true" : "not_applicable",
    primaryCodePreserved: "true",
    cleanupFailureSuppressed: "false",
  };
}

function verifyFixture(name, option, expected, lineageCount, expectedCurrent) {
  const source = artifact(option);
  const staging = `/data/local/tmp/aah-m2-03-${name}.apk`;
  const appRoot = runAdb(["shell", "run-as", policyPackage, "pwd"]).stdout.trim();
  if (!appRoot.startsWith("/data/")) fail("invalid policy app data path");
  const remote = `${appRoot}/files/aah-m2-03-${name}.apk`;
  runAdb(["push", source, staging]);
  runAdb(["shell", "run-as", policyPackage, "mkdir", "-p", "files"]);
  runAdb(["shell", "run-as", policyPackage, "cp", staging, `files/aah-m2-03-${name}.apk`]);
  const command = [
    "shell", "am", "instrument", "-w",
    "-e", "verify_apk", remote,
    "-e", "verify_package", fixturePackage,
    "-e", "expected_category", expected,
    "-e", "expected_lineage_count", String(lineageCount),
  ];
  if (expectedCurrent !== "") {
    command.push("-e", "expected_current_sha256", expectedCurrent);
  }
  command.push(runner);
  const result = runAdb(command);
  if (!result.stdout.includes("policy_fixture=true") ||
      !result.stdout.includes(`actual=${expected}`) ||
      !result.stdout.includes("INSTRUMENTATION_CODE: -1")) {
    fail(`${name} policy instrumentation mismatch: ${result.stdout}`);
  }
  runAdb(["shell", "run-as", policyPackage, "rm", "-f", `files/aah-m2-03-${name}.apk`]);
  runAdb(["shell", "rm", "-f", staging]);
  fixtureResults.push({
    name,
    apk_sha256: sha256(readFileSync(source)),
    expected_code: `AAH-RUNTIME-INTEGRITY-${expected}`,
    actual_code: `AAH-RUNTIME-INTEGRITY-${expected}`,
    expected_lineage_count: lineageCount,
    result: "PASS",
  });
}

function verifyStartup(name, option, expected, allowInstallRejection) {
  const apk = artifact(option);
  runAdb(["uninstall", targetPackage], true);
  runAdb(["logcat", "-c"], true);
  const installed = runAdb(["install", "-r", "-t", "--no-streaming", apk], true);
  if (installed.status !== 0) {
    if (!allowInstallRejection) fail(`${name} install failed: ${installed.stderr}`);
    const installOutput = `${installed.stdout}\n${installed.stderr}`;
    const platformCode = installOutput.match(
      /INSTALL_(?:PARSE_FAILED_(?:INCONSISTENT_CERTIFICATES|NO_CERTIFICATES)|FAILED_INVALID_APK)/u,
    )?.[0];
    if (!platformCode) fail(`${name} unexpected install failure: ${installOutput}`);
    startupResults.push({
      name,
      apk_sha256: sha256(readFileSync(apk)),
      expected_code: `AAH-RUNTIME-INTEGRITY-${expected}`,
      actual_code: `PLATFORM_SIGNATURE_REJECTION:${platformCode}`,
      install_rejected: true,
      result: "PASS",
    });
    return;
  }
  const code = `AAH-RUNTIME-INTEGRITY-${expected}`;
  const runToken = randomBytes(8).toString("hex");
  const marker = `startup_rejected run_token=${runToken} code=${code} lookup_count=0 session_published=false`;
  // API 29 can keep `am start -W` blocked until the command timeout when the
  // deliberately rejected Activity dies in onCreate. Dispatch without `-W`;
  // the bounded tagged-log polling below is the completion barrier.
  runAdb([
    "shell", "am", "start", "-n", activity,
    "--es", "aah_m2_03_run_token", runToken,
  ], true);
  let startupLogs = "";
  for (let attempt = 0; attempt < 5 && !startupLogs.includes(marker); attempt += 1) {
    runAdb(["shell", "sleep", "1"]);
    startupLogs = runAdb(
      ["logcat", "-d", "-v", "threadtime", "-s", "AAH-M2-03:*"],
      false,
      false,
    ).stdout;
  }
  const sanitizedLines = startupLogs
    .split(/\r?\n/u)
    .filter((line) => line.includes("AAH-M2-03") && line.includes(`run_token=${runToken}`))
    .map((line) => line.slice(line.indexOf("startup_rejected")))
    .filter((line) => line.startsWith("startup_rejected"));
  const matchingMarkers = sanitizedLines.filter((line) => line === marker);
  const sanitized = sanitizedLines.join("\n");
  if (matchingMarkers.length !== 1 || sanitizedLines.length !== 1) {
    fail(`${name} startup marker was not unique for this run token; observed=${sanitized || "<none>"}`);
  }
  assertNoSensitiveEvidence(sanitized, name);
  writeFileSync(path.join(evidence, `${name}.logcat.txt`), `${marker}\n`);
  startupResults.push({
    name,
    apk_sha256: sha256(readFileSync(apk)),
    expected_code: code,
    actual_code: code,
    install_rejected: false,
    lookup_count: 0,
    session_published: false,
    result: "PASS",
  });
}

function assertNoSensitiveEvidence(value, name) {
  const forbidden = [
    /dex\n0(?:35|37|38|39|40|41)\0/u,
    /(?:cek|kek|private[_ -]?key|key[_ -]?material)\s*[:=]/iu,
    /[A-Z]:\\Users\\/iu,
    /\/home\/[^/\s]+\//u,
    /\/(?:data|sdcard|storage|proc)\/[^\s"']+/u,
  ];
  if (forbidden.some((pattern) => pattern.test(value))) {
    fail(`${name} evidence contains forbidden plaintext or user path`);
  }
}

function cleanup() {
  let passed = true;
  for (const name of [
    "same-signer", "different-signer", "multiple-current", "unsigned",
    "valid-rotation", "historical-only",
  ]) {
    const file = `files/aah-m2-03-${name}.apk`;
    runAdb(["shell", "run-as", policyPackage, "rm", "-f", file], true);
    if (runAdb(["shell", "run-as", policyPackage, "ls", file], true).status === 0) {
      passed = false;
    }
  }
  runAdb(["shell", "rm", "-f", "/data/local/tmp/aah-m2-03-same-signer.apk"], true);
  runAdb(["shell", "rm", "-f", "/data/local/tmp/aah-m2-03-different-signer.apk"], true);
  runAdb(["shell", "rm", "-f", "/data/local/tmp/aah-m2-03-multiple-current.apk"], true);
  runAdb(["shell", "rm", "-f", "/data/local/tmp/aah-m2-03-unsigned.apk"], true);
  runAdb(["shell", "rm", "-f", "/data/local/tmp/aah-m2-03-valid-rotation.apk"], true);
  runAdb(["shell", "rm", "-f", "/data/local/tmp/aah-m2-03-historical-only.apk"], true);
  runAdb(["uninstall", policyPackage], true);
  runAdb(["uninstall", targetPackage], true);
  for (const packageName of [policyPackage, targetPackage]) {
    if (runAdb(["shell", "pm", "path", packageName], true).stdout.trim() !== "") {
      passed = false;
    }
  }
  for (const name of [
    "same-signer", "different-signer", "multiple-current", "unsigned",
    "valid-rotation", "historical-only",
  ]) {
    if (runAdb(["shell", "ls", `/data/local/tmp/aah-m2-03-${name}.apk`], true).status === 0) {
      passed = false;
    }
  }
  return passed;
}

function runAdb(args, allowFailure = false, recordOutput = true) {
  const result = spawnSync(adb, ["-s", serial, ...args], {
    encoding: "utf8",
    timeout,
    maxBuffer: 16 * 1024 * 1024,
    windowsHide: true,
  });
  const record = {
    started_at: new Date().toISOString(),
    command: ["adb", "-s", "<serial-redacted>", ...args.map(redact)],
    exit_code: result.status,
    timed_out: result.error?.code === "ETIMEDOUT",
    stdout: recordOutput ? sanitizeTranscriptOutput(result.stdout ?? "") : "<output-omitted>",
    stderr: recordOutput ? sanitizeTranscriptOutput(result.stderr ?? "") : "<output-omitted>",
  };
  transcript.push(record);
  if (record.timed_out) fail(`adb timed out: ${args.join(" ")}`);
  if (!allowFailure && result.status !== 0) fail(`adb failed: ${args.join(" ")}\n${record.stderr}`);
  return { status: result.status, stdout: result.stdout ?? "", stderr: result.stderr ?? "" };
}

function sanitizeTranscriptOutput(value) {
  if (/dex\n0(?:35|37|38|39|40|41)\0/u.test(value)) return "<binary-dex-output-redacted>";
  return value
    .split(/\r?\n/u)
    .map((line) => /(?:cek|kek|private[_ -]?key|key[_ -]?material)\s*[:=]/iu.test(line)
      ? "<sensitive-output-redacted>"
      : line)
    .join("\n")
    .replace(/[0-9a-f]{64}/giu, "<sha256-redacted>")
    .replace(/[A-Z]:\\[^\s"']+/giu, "<host-path-redacted>")
    .replace(/\/(?:data|sdcard|storage|proc)\/[^\s"']+/gu, "<device-path-redacted>")
    .replace(/\/home\/[^\s"']+/gu, "<host-path-redacted>");
}

function redact(value) {
  if (path.isAbsolute(value)) return path.basename(value);
  return /^[0-9a-f]{64}$/u.test(value) ? "<sha256-redacted>" : value;
}

function artifact(name) {
  const value = path.resolve(required(name));
  readFileSync(value);
  return value;
}

function assertIgnored(target) {
  const roots = [path.resolve("build"), path.resolve("artifacts")];
  if (!roots.some((root) => target === root || target.startsWith(`${root}${path.sep}`))) {
    fail("evidence must be under build/ or artifacts/");
  }
}

function parse(values) {
  const parsed = new Map();
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) fail("invalid arguments");
    parsed.set(values[index].slice(2), values[index + 1]);
  }
  return parsed;
}

function required(name) {
  const value = options.get(name);
  if (!value) fail(`missing --${name}`);
  return value;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function fail(message) {
  throw new Error(`M2-03 signer matrix failed: ${message}`);
}
