#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

function readOption(name, fallback = "") {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

const adb = readOption("--adb", "adb");
const serial = readOption("--serial");
const iterations = Number.parseInt(readOption("--iterations", "20"), 10);
const expectedApi = Number.parseInt(readOption("--expected-api"), 10);
const expectedAbi = readOption("--expected-abi");
const payloadSha256 = readOption("--payload-sha256").toLowerCase();
const reportPath = readOption("--report");
const packageName = "ah.fixtures.android.classloaderpoc";
const component =
  `${packageName}/ah.fixtures.android.payload.PayloadActivity`;
const forbiddenFileExtension = /\.(?:dex|jar|odex)$/iu;
const requiredProbeEvents = [
  "FACTORY_ENTER",
  "LOADER_CREATED",
  "APPLICATION_CREATED",
  "ACTIVITY_CREATED",
];

function fail(message) {
  throw new Error(`M0-04 cold-start validation failed: ${message}`);
}

function emitReport(report) {
  const text = `${JSON.stringify(report, null, 2)}\n`;
  if (reportPath) {
    const repositoryRoot = process.cwd();
    const output = path.resolve(reportPath);
    const allowedRoots = [
      path.join(repositoryRoot, "build"),
      path.join(repositoryRoot, "artifacts"),
    ];
  if (
    !allowedRoots.some((root) => output.startsWith(`${root}${path.sep}`))
  ) {
      fail("--report must be under the ignored build/ or artifacts/ tree");
    }
    mkdirSync(path.dirname(output), { recursive: true });
    writeFileSync(output, text, { flag: "wx" });
  }
  process.stdout.write(text);
}

function run(arguments_, { allowFailure = false } = {}) {
  const result = spawnSync(adb, serial ? ["-s", serial, ...arguments_] : arguments_, {
    encoding: "utf8",
    windowsHide: true,
  });
  if (result.error) {
    throw result.error;
  }
  if (!allowFailure && result.status !== 0) {
    fail(
      `${arguments_[0]} exited ${result.status}: ${result.stderr || result.stdout}`,
    );
  }
  return {
    status: result.status,
    stdout: result.stdout.trim(),
    stderr: result.stderr.trim(),
  };
}

function parseSnapshot(kind, output, target) {
  for (const line of output.split(/\r?\n/u).filter(Boolean)) {
    const match = /^([0-9a-f]{64})\s+\*?(.+)$/iu.exec(line.trim());
    if (!match) {
      fail(`cannot parse ${kind} file snapshot`);
    }
    const relative = match[2].replaceAll("\\", "/").replace(/^\.\//u, "");
    if (relative.startsWith("/") || relative.includes("../")) {
      fail(`${kind} snapshot returned an unsafe path`);
    }
    target.set(`${kind}:${relative}`, match[1].toLowerCase());
  }
}

function snapshotAppFiles() {
  const snapshot = new Map();
  const privateFiles = run([
    "shell",
    `run-as ${packageName} sh -c 'find . -type f -exec sha256sum {} \\;'`,
  ]);
  const externalRoot = `/sdcard/Android/data/${packageName}`;
  const externalFiles = run(
    [
      "shell",
      `if [ -d ${externalRoot} ]; then cd ${externalRoot} && find . -type f -exec sha256sum {} \\;; fi`,
    ],
  );
  parseSnapshot("private", privateFiles.stdout, snapshot);
  parseSnapshot("external", externalFiles.stdout, snapshot);
  return {
    files: snapshot,
    private_success: privateFiles.status === 0,
    external_success: externalFiles.status === 0,
  };
}

function compareSnapshots(before, after, expectedPayloadHash) {
  const added = [];
  const changed = [];
  const deleted = [];

  for (const [file, hash] of after) {
    if (!before.has(file)) {
      added.push(file);
    } else if (before.get(file) !== hash) {
      changed.push(file);
    }
  }
  for (const file of before.keys()) {
    if (!after.has(file)) {
      deleted.push(file);
    }
  }

  const forbiddenFiles = [...after.keys()].filter((file) =>
    forbiddenFileExtension.test(file),
  );
  const payloadMatches = [...after.entries()]
    .filter(([, hash]) => hash === expectedPayloadHash)
    .map(([file]) => file);
  return {
    added: added.sort(),
    changed: changed.sort(),
    deleted: deleted.sort(),
    forbiddenFiles: forbiddenFiles.sort(),
    payloadMatches: payloadMatches.sort(),
  };
}

function validateDevice(actual, expected) {
  if (actual.api !== expected.api) {
    fail(`expected API ${expected.api}, got ${actual.api}`);
  }
  if (actual.abi !== expected.abi) {
    fail(`expected ABI ${expected.abi}, got ${actual.abi}`);
  }
  const uid = /^uid=(\d+)/u.exec(actual.rootState)?.[1];
  if (uid === undefined) {
    fail("cannot parse adb shell identity");
  }
  if (uid === "0") {
    fail("adb shell is root");
  }
}

function hasProbeEvent(log, event) {
  return new RegExp(`AAH-M0-04[^\\r\\n]*:\\s+${event}\\b`, "u").test(log);
}

function runSelfTest() {
  const hash = "a".repeat(64);
  const before = new Map([["private:files/existing", "b".repeat(64)]]);
  const after = new Map(before);
  after.set("private:files/payload-without-extension", hash);
  const delta = compareSnapshots(before, after, hash);
  if (
    delta.added.length !== 1 ||
    delta.payloadMatches.length !== 1 ||
    delta.forbiddenFiles.length !== 0
  ) {
    fail("extensionless payload detector self-test failed");
  }
  let rootRejected = false;
  try {
    validateDevice(
      { api: 29, abi: "x86_64", rootState: "uid=0(root)" },
      { api: 29, abi: "x86_64" },
    );
  } catch {
    rootRejected = true;
  }
  if (!rootRejected) {
    fail("root-device detector self-test failed");
  }
  emitReport({
    task_id: "M0-04",
    extensionless_payload_detection: "PASS",
    root_device_rejection: "PASS",
    result: "PASS",
  });
}

function main() {
  if (process.argv.includes("--self-test")) {
    runSelfTest();
    return;
  }
  if (!Number.isInteger(iterations) || iterations < 1 || iterations > 100) {
    fail("--iterations must be an integer from 1 through 100");
  }
  if (!Number.isInteger(expectedApi) || expectedApi < 29) {
    fail("--expected-api must be an integer of at least 29");
  }
  if (!expectedAbi) {
    fail("--expected-abi is required");
  }
  if (!/^[0-9a-f]{64}$/u.test(payloadSha256)) {
    fail("--payload-sha256 must be a lowercase or uppercase SHA-256");
  }

  const deviceApi = run(["shell", "getprop", "ro.build.version.sdk"]).stdout;
  const deviceAbi = run(["shell", "getprop", "ro.product.cpu.abi"]).stdout;
  const fingerprint = run(["shell", "getprop", "ro.build.fingerprint"]).stdout;
  const rootState = run(["shell", "id"]).stdout;
  const device = {
    api: Number.parseInt(deviceApi, 10),
    abi: deviceAbi,
    fingerprint,
    rootState,
  };
  validateDevice(device, { api: expectedApi, abi: expectedAbi });

  const baselineSnapshot = snapshotAppFiles();
  const baselineFiles = baselineSnapshot.files;
  const starts = [];
  const forbiddenLogs = [];

  for (let iteration = 1; iteration <= iterations; iteration += 1) {
    run(["shell", "am", "force-stop", packageName]);
    run(["logcat", "-c"]);
    const launch = run(["shell", "am", "start", "-W", "-n", component]);
    if (!/^Status:\s+ok$/mu.test(launch.stdout)) {
      fail(`iteration ${iteration} did not report Status: ok`);
    }
    const pid = run(["shell", "pidof", packageName]).stdout;
    if (!/^\d+$/u.test(pid)) {
      fail(`iteration ${iteration} has no live target process`);
    }
    const log = run(["logcat", "-d", "--pid", pid, "-v", "brief"]).stdout;
    const violations = log
      .split(/\r?\n/u)
      .filter((line) =>
        /FATAL EXCEPTION|AAH-P001|hiddenapi|Accessing hidden/iu.test(line),
      );
    if (violations.length > 0) {
      forbiddenLogs.push({ iteration, violations });
    }
    for (const event of requiredProbeEvents) {
      if (!hasProbeEvent(log, event)) {
        fail(`iteration ${iteration} is missing probe event ${event}`);
      }
    }
    const totalTime = /^TotalTime:\s+(\d+)$/mu.exec(launch.stdout)?.[1] ?? null;
    starts.push({
      iteration,
      pid: Number.parseInt(pid, 10),
      total_time_ms: totalTime === null ? null : Number.parseInt(totalTime, 10),
    });
  }

  if (forbiddenLogs.length > 0) {
    fail(`forbidden process logs found: ${JSON.stringify(forbiddenLogs)}`);
  }

  const finalSnapshot = snapshotAppFiles();
  const finalFiles = finalSnapshot.files;
  const delta = compareSnapshots(baselineFiles, finalFiles, payloadSha256);
  if (delta.forbiddenFiles.length > 0) {
    fail(`plaintext-like files found: ${JSON.stringify(delta.forbiddenFiles)}`);
  }
  if (delta.payloadMatches.length > 0) {
    fail(`payload hash found on disk: ${JSON.stringify(delta.payloadMatches)}`);
  }

  emitReport({
    task_id: "M0-04",
    validation_mode: "pre-cli",
    serial,
    device: {
      api: device.api,
      abi: device.abi,
      fingerprint: device.fingerprint,
      root_state: device.rootState,
    },
    expected_device: {
      api: expectedApi,
      abi: expectedAbi,
      adb_shell_non_root: true,
    },
    iterations,
    starts,
    file_delta: {
      before_count: baselineFiles.size,
      after_count: finalFiles.size,
      added: delta.added,
      changed: delta.changed,
      deleted: delta.deleted,
    },
    snapshot_status: {
      private_before: baselineSnapshot.private_success,
      external_before: baselineSnapshot.external_success,
      private_after: finalSnapshot.private_success,
      external_after: finalSnapshot.external_success,
    },
    payload_sha256: payloadSha256,
    payload_hash_matches_on_disk: 0,
    forbidden_process_logs: 0,
    forbidden_private_or_external_files: 0,
    result: "PASS",
  });
}

try {
  main();
} catch (error) {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}
