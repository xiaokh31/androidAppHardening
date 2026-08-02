#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const args = parseArguments(process.argv.slice(2));
const adb = required("adb");
const serial = required("serial");
const evidenceRoot = path.resolve(required("evidence"));
const packageName = args.get("package") ?? "ah.fixtures.android.m005.extracted";
const commandTimeoutMs = Number(args.get("command-timeout-ms") ?? "60000");
const allowedRoots = [path.resolve("build"), path.resolve("artifacts")];
if (!allowedRoots.some((root) => evidenceRoot === root || evidenceRoot.startsWith(`${root}${path.sep}`))) {
  fail("--evidence must be under build/ or artifacts/");
}
mkdirSync(evidenceRoot, { recursive: true });

const cases = loadCases();
if (!Array.isArray(cases) || cases.length === 0) {
  fail("case manifest must be a non-empty JSON array");
}
const transcript = [];
const results = [];

try {
  for (const testCase of cases) {
    const apk = path.resolve(testCase.apk);
    const apkBytes = readFileSync(apk);
    runAdb(["uninstall", packageName], true);
    runAdb(["logcat", "-c"], true);
    const installed = runAdb(["install", "-r", "-t", "--no-streaming", apk], true);
    if (installed.status !== 0) {
      if (testCase.install_rejection !== true && testCase.allow_install_rejection !== true) {
        fail(`${testCase.name} was rejected during install unexpectedly: ${installed.stderr}`);
      }
      results.push({
        name: testCase.name,
        apk_sha256: sha256(apkBytes),
        expected_code: testCase.expected_code,
        install_rejected: true,
        loader_created: false,
        result: "PASS",
      });
      continue;
    }
    if (testCase.install_rejection === true) {
      fail(`${testCase.name} unexpectedly installed`);
    }
    runAdb([
      "shell", "am", "start", "-n",
      `${packageName}/ah.fixtures.android.payload.PayloadActivity`,
    ], true);
    runAdb(["shell", "sleep", "1"]);
    const logs = runAdb(["logcat", "-d", "-v", "threadtime"]);
    writeFileSync(path.join(evidenceRoot, `${testCase.name}.logcat.txt`), logs.stdout);
    const failureLogs = currentFailureProcessLogs(logs.stdout);
    if (!failureLogs.includes(`${testCase.expected_code}:`)) {
      fail(`${testCase.name} did not emit ${testCase.expected_code}`);
    }
    if (/AAH-M0-04:\s+LOADER_CREATED/u.test(failureLogs)) {
      fail(`${testCase.name} created a loader after the expected startup failure`);
    }
    results.push({
      name: testCase.name,
      apk_sha256: sha256(apkBytes),
      expected_code: testCase.expected_code,
      install_rejected: false,
      loader_created: false,
      result: "PASS",
    });
  }
  cleanup();
  const report = {
    task_id: "M0-05",
    package_name: packageName,
    serial_sha256: sha256(Buffer.from(serial, "utf8")),
    cases: results,
    result: "PASS",
  };
  writeFileSync(path.join(evidenceRoot, "startup-negative-report.json"), `${JSON.stringify(report, null, 2)}\n`);
  writeFileSync(path.join(evidenceRoot, "startup-negative-junit.xml"), startupNegativeJUnit(report));
  writeFileSync(path.join(evidenceRoot, "startup-negative-commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} catch (error) {
  cleanup();
  writeFileSync(path.join(evidenceRoot, "startup-negative-commands.json"), `${JSON.stringify(transcript, null, 2)}\n`);
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}

function currentFailureProcessLogs(output) {
  const processPattern = new RegExp(
    `AndroidRuntime:\\s+Process: ${escapeRegExp(packageName)}, PID:\\s+(\\d+)`,
    "gu",
  );
  const crashes = [...output.matchAll(processPattern)];
  const pid = crashes.at(-1)?.[1];
  if (!pid) {
    fail(`startup failure did not expose a FATAL process for ${packageName}`);
  }
  const pidPattern = new RegExp(
    `^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+${escapeRegExp(pid)}\\s+`,
    "u",
  );
  return output.split(/\r?\n/u).filter((line) => pidPattern.test(line)).join("\n");
}

function startupNegativeJUnit(report) {
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<testsuite name="M0-05 startup negatives" tests="${report.cases.length}" failures="0" errors="0">`,
    `  <property name="package" value="${xml(report.package_name)}"/>`,
    ...report.cases.map((testCase) =>
      `  <testcase classname="M0-05.${xml(report.package_name)}" name="${xml(testCase.name)}"/>`),
    "</testsuite>",
    "",
  ].join("\n");
}

function xml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll('"', "&quot;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, "\\$&");
}

function cleanup() {
  runAdb(["uninstall", `${packageName}.test`], true);
  runAdb(["uninstall", packageName], true);
}

function loadCases() {
  if (args.has("cases")) {
    return JSON.parse(readFileSync(path.resolve(args.get("cases")), "utf8"));
  }
  const signed = args.get("signed-dir");
  const unsigned = args.get("unsigned-dir");
  if (!signed || !unsigned) {
    fail("provide --cases or both --signed-dir and --unsigned-dir");
  }
  const signedPath = (name) => path.resolve(signed, `m0-05-${name}.apk`);
  const unsignedPath = (name) => path.resolve(unsigned, `m0-05-${name}-unsigned.apk`);
  return [
    { name: "config-major", apk: signedPath("config-major"), expected_code: "AAH-P009" },
    { name: "config-reserved", apk: signedPath("config-reserved"), expected_code: "AAH-P009" },
    { name: "config-signer-mismatch", apk: signedPath("config-signer-mismatch"), expected_code: "AAH-P010" },
    { name: "config-factory-flags", apk: signedPath("config-factory-flags"), expected_code: "AAH-P009" },
    { name: "config-invalid-utf8", apk: signedPath("config-invalid-utf8"), expected_code: "AAH-P009" },
    { name: "config-nul", apk: signedPath("config-nul"), expected_code: "AAH-P009" },
    { name: "config-slot-tail", apk: signedPath("config-slot-tail"), expected_code: "AAH-P009" },
    { name: "config-deflate", apk: signedPath("config-deflate"), expected_code: "AAH-P009", allow_install_rejection: true },
    { name: "config-descriptor", apk: signedPath("config-descriptor"), expected_code: "AAH-P009", allow_install_rejection: true },
    { name: "config-crc", apk: signedPath("config-crc"), expected_code: "AAH-P009", allow_install_rejection: true },
    { name: "config-length", apk: signedPath("config-length"), expected_code: "AAH-P009", allow_install_rejection: true },
    { name: "payload-corrupt", apk: signedPath("payload-corrupt"), expected_code: "AAH-P001" },
    { name: "native-duplicate", apk: signedPath("native-duplicate"), expected_code: "AAH-P004", allow_install_rejection: true },
    { name: "wrong-signer", apk: signedPath("wrong-signer"), expected_code: "AAH-P008" },
    { name: "multi-signer", apk: signedPath("multi-signer"), expected_code: "AAH-P007" },
    { name: "duplicate-config", apk: unsignedPath("config-duplicate"), expected_code: "PLATFORM_ZIP_REJECTION", install_rejection: true },
    { name: "truncated-zip", apk: unsignedPath("truncated-zip"), expected_code: "PLATFORM_ZIP_REJECTION", install_rejection: true },
    { name: "unsigned", apk: unsignedPath("no-factory"), expected_code: "PLATFORM_SIGNATURE_REJECTION", install_rejection: true },
  ];
}

function runAdb(command, allowFailure = false) {
  const result = spawnSync(adb, ["-s", serial, ...command], {
    encoding: "utf8",
    timeout: commandTimeoutMs,
    maxBuffer: 16 * 1024 * 1024,
    windowsHide: true,
  });
  const record = {
    started_at: new Date().toISOString(),
    command: ["adb", "-s", "<serial-redacted>", ...command],
    exit_code: result.status,
    timed_out: result.error?.code === "ETIMEDOUT",
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
  };
  transcript.push(record);
  if (record.timed_out) {
    fail(`adb command timed out: ${command.join(" ")}`);
  }
  if (!allowFailure && result.status !== 0) {
    fail(`adb command failed (${result.status}): ${command.join(" ")}\n${record.stderr}`);
  }
  return { status: result.status, stdout: record.stdout, stderr: record.stderr };
}

function parseArguments(values) {
  const result = new Map();
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) {
      fail(`invalid argument sequence near ${values[index] ?? "<end>"}`);
    }
    result.set(values[index].slice(2), values[index + 1]);
  }
  return result;
}

function required(name) {
  const value = args.get(name);
  if (!value) {
    fail(`missing --${name}`);
  }
  return value;
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function fail(message) {
  throw new Error(`M0-05 startup negative acceptance failed: ${message}`);
}
