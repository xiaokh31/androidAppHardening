#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import process from "node:process";

function readOption(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

const adb = readOption("--adb", "adb");
const serial = readOption("--serial", "");
const iterations = Number.parseInt(readOption("--iterations", "20"), 10);
const packageName = "ah.fixtures.android.classloaderpoc";
const component =
  `${packageName}/ah.fixtures.android.payload.PayloadActivity`;

function fail(message) {
  throw new Error(`M0-04 cold-start validation failed: ${message}`);
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
      `${arguments_.join(" ")} exited ${result.status}: ${result.stderr || result.stdout}`,
    );
  }
  return {
    status: result.status,
    stdout: result.stdout.trim(),
    stderr: result.stderr.trim(),
  };
}

function listAppFiles() {
  const privateFiles = run(
    ["shell", "run-as", packageName, "find", ".", "-type", "f"],
    { allowFailure: true },
  );
  const externalFiles = run(
    [
      "shell",
      "find",
      `/sdcard/Android/data/${packageName}`,
      "-type",
      "f",
    ],
    { allowFailure: true },
  );
  return [privateFiles.stdout, externalFiles.stdout]
    .join("\n")
    .split(/\r?\n/u)
    .filter(Boolean)
    .sort();
}

function main() {
  if (!Number.isInteger(iterations) || iterations < 1 || iterations > 100) {
    fail("--iterations must be an integer from 1 through 100");
  }

  const deviceApi = run(["shell", "getprop", "ro.build.version.sdk"]).stdout;
  const deviceAbi = run(["shell", "getprop", "ro.product.cpu.abi"]).stdout;
  const fingerprint = run(["shell", "getprop", "ro.build.fingerprint"]).stdout;
  const rootState = run(["shell", "id"]).stdout;
  const starts = [];
  const forbiddenLogs = [];

  for (let iteration = 1; iteration <= iterations; iteration += 1) {
    run(["shell", "am", "force-stop", packageName]);
    run(["logcat", "-c"]);
    const launch = run(["shell", "am", "start", "-W", "-n", component]);
    if (!/^Status:\s+ok$/mu.test(launch.stdout)) {
      fail(`iteration ${iteration} did not report Status: ok\n${launch.stdout}`);
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
    const totalTime = /^TotalTime:\s+(\d+)$/mu.exec(launch.stdout)?.[1] ?? null;
    starts.push({ iteration, pid: Number.parseInt(pid, 10), total_time_ms: totalTime });
  }

  if (forbiddenLogs.length > 0) {
    fail(`forbidden process logs found: ${JSON.stringify(forbiddenLogs)}`);
  }
  const files = listAppFiles();
  const forbiddenFiles = files.filter((file) => /\.(?:dex|jar|odex)$/iu.test(file));
  if (forbiddenFiles.length > 0) {
    fail(`plaintext-like files found: ${JSON.stringify(forbiddenFiles)}`);
  }

  process.stdout.write(
    `${JSON.stringify(
      {
        task_id: "M0-04",
        validation_mode: "pre-cli",
        serial,
        device: {
          api: Number.parseInt(deviceApi, 10),
          abi: deviceAbi,
          fingerprint,
          root_state: rootState,
        },
        iterations,
        starts,
        forbidden_process_logs: 0,
        forbidden_private_or_external_files: 0,
        result: "PASS",
      },
      null,
      2,
    )}\n`,
  );
}

try {
  main();
} catch (error) {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}
