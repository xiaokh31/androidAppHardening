#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import process from "node:process";

const packageName = "ah.fixtures.android.classloaderpoc";
const component =
  `${packageName}/ah.fixtures.android.payload.PayloadActivity`;
const expectedLabels = new Set(["missing", "corrupt", "empty"]);
const forbiddenProbeEvents = [
  "LOADER_CREATED",
  "APPLICATION_CREATED",
  "ACTIVITY_CREATED",
];

function readOption(name, fallback = "") {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

function readOptions(name) {
  const values = [];
  for (let index = 0; index < process.argv.length; index += 1) {
    if (process.argv[index] === name) {
      values.push(process.argv[index + 1]);
    }
  }
  return values;
}

const adb = readOption("--adb", "adb");
const serial = readOption("--serial");
const expectedApi = Number.parseInt(readOption("--expected-api"), 10);
const expectedAbi = readOption("--expected-abi");
const reportPath = readOption("--report");

function fail(message) {
  throw new Error(`M0-04 tamper-start validation failed: ${message}`);
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

function assertDevice() {
  const api = Number.parseInt(
    run(["shell", "getprop", "ro.build.version.sdk"]).stdout,
    10,
  );
  const abi = run(["shell", "getprop", "ro.product.cpu.abi"]).stdout;
  const fingerprint = run(["shell", "getprop", "ro.build.fingerprint"]).stdout;
  const rootState = run(["shell", "id"]).stdout;
  const uid = /^uid=(\d+)/u.exec(rootState)?.[1];
  if (api !== expectedApi) {
    fail(`expected API ${expectedApi}, got ${api}`);
  }
  if (abi !== expectedAbi) {
    fail(`expected ABI ${expectedAbi}, got ${abi}`);
  }
  if (uid === undefined) {
    fail("cannot parse adb shell identity");
  }
  if (uid === "0") {
    fail("adb shell is root");
  }
  return { api, abi, fingerprint, root_state: rootState };
}

function parseVariants() {
  const variants = new Map();
  for (const argument of readOptions("--variant")) {
    const separator = argument.indexOf("=");
    if (separator <= 0 || separator === argument.length - 1) {
      fail("--variant must use label=apk syntax");
    }
    const label = argument.slice(0, separator);
    const apk = argument.slice(separator + 1);
    if (!expectedLabels.has(label)) {
      fail(`unsupported mutation label ${label}`);
    }
    if (variants.has(label)) {
      fail(`duplicate mutation label ${label}`);
    }
    variants.set(label, apk);
  }
  if (
    variants.size !== expectedLabels.size ||
    [...expectedLabels].some((label) => !variants.has(label))
  ) {
    fail("missing, corrupt, and empty variants are all required");
  }
  return variants;
}

function sha256File(file) {
  return createHash("sha256").update(readFileSync(file)).digest("hex");
}

function hasProbeEvent(log, event) {
  return new RegExp(`AAH-M0-04[^\\r\\n]*:\\s+${event}\\b`, "u").test(log);
}

function validateVariant(label, apk) {
  run(["uninstall", packageName], { allowFailure: true });
  try {
    const install = run(["install", "-r", "-t", apk]);
    if (!/Success/u.test(install.stdout)) {
      fail(`${label} APK did not install successfully`);
    }
    run(["shell", "am", "force-stop", packageName]);
    run(["logcat", "-c"]);
    const launch = run(["shell", "am", "start", "-n", component], {
      allowFailure: true,
    });
    run(["shell", "sleep", "2"]);
    const log = run(["logcat", "-d", "-v", "brief"]).stdout;
    const pid = run(["shell", "pidof", packageName], { allowFailure: true }).stdout;

    if (!log.includes("AAH-P001:")) {
      fail(`${label} APK did not expose AAH-P001`);
    }
    if (!hasProbeEvent(log, "FACTORY_ENTER")) {
      fail(`${label} APK did not enter the public ClassLoader hook`);
    }
    for (const event of forbiddenProbeEvents) {
      if (hasProbeEvent(log, event)) {
        fail(`${label} APK executed forbidden probe event ${event}`);
      }
    }
    if (log.includes("M0-04-IN-MEMORY")) {
      fail(`${label} APK executed the payload-only marker`);
    }
    if (pid) {
      fail(`${label} APK left a live target process`);
    }

    return {
      mutation: label,
      apk_sha256: sha256File(apk),
      install: "PASS",
      am_start_exit_code: launch.status,
      aah_p001: true,
      factory_enter: true,
      loader_created: false,
      application_created: false,
      activity_created: false,
      payload_marker_executed: false,
      live_process_after_failure: false,
    };
  } finally {
    run(["uninstall", packageName], { allowFailure: true });
  }
}

function main() {
  if (!Number.isInteger(expectedApi) || expectedApi < 29) {
    fail("--expected-api must be an integer of at least 29");
  }
  if (!expectedAbi) {
    fail("--expected-abi is required");
  }
  const variants = parseVariants();
  const device = assertDevice();
  const results = [];
  for (const [label, apk] of variants) {
    results.push(validateVariant(label, apk));
  }
  emitReport({
    task_id: "M0-04",
    validation_mode: "pre-cli",
    serial,
    device,
    variants: results,
    result: "PASS",
  });
}

try {
  main();
} catch (error) {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}
