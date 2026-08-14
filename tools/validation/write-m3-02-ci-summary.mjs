#!/usr/bin/env node

import {mkdirSync, readFileSync, readdirSync, statSync, writeFileSync} from "node:fs";
import path from "node:path";
import process from "node:process";

function fail(message) {
  throw new Error(`M3-02 CI summary failed: ${message}`);
}

function files(root) {
  return readdirSync(root).sort().flatMap((name) => {
    const value = path.join(root, name);
    return statSync(value).isDirectory() ? files(value) : [value];
  });
}

const [mode, secondsValue, inputValue, outputValue] = process.argv.slice(2);
const seconds = Number(secondsValue);
if (!["pr", "nightly"].includes(mode) || !Number.isInteger(seconds) ||
    (mode === "pr" ? seconds !== 600 : seconds !== 3600)) {
  fail("requires pr/600 or nightly/3600");
}
const input = path.resolve(inputValue ?? "");
const output = path.resolve(outputValue ?? "");
const buildRoot = path.resolve("build");
if (!input.startsWith(`${buildRoot}${path.sep}`) || !output.startsWith(`${buildRoot}${path.sep}`)) {
  fail("inputs and output must remain under build/");
}
const sha = process.env.GITHUB_SHA ?? "local";
if (sha !== "local" && !/^[0-9a-f]{40}$/u.test(sha)) fail("invalid GITHUB_SHA");
const expected = new Set([
  "Jazzer 0.29.1|ApkInspectorFuzzTarget|ubuntu-24.04",
  "Jazzer 0.29.1|BinaryAxmlFuzzTarget|ubuntu-24.04",
  "Jazzer 0.29.1|ApkInspectorFuzzTarget|windows-2025",
  "Jazzer 0.29.1|BinaryAxmlFuzzTarget|windows-2025",
  "libFuzzer Clang 18.1.3 ASan UBSan|AHDCNativeFuzzTarget|ubuntu-24.04",
]);
const candidates = files(input).filter((value) => value.endsWith("target-result.json"));
if (candidates.length !== expected.size) fail(`expected five target reports, observed ${candidates.length}`);
const targets = candidates.map((value) => {
  const report = JSON.parse(readFileSync(value, "utf8"));
  const key = `${report.engine}|${report.target}|${report.platform}`;
  if (!expected.delete(key)) fail(`unexpected or duplicate target ${key}`);
  if (report.schema_version !== 1 || report.task_id !== "M3-02" ||
      report.validation_mode !== "full-flow" || report.commit !== sha || report.mode !== mode ||
      report.seconds !== seconds || report.result !== "PASS" ||
      !Number.isInteger(report.executions) || report.executions < 1 ||
      report.memory_limit_mb !== 2048 || report.input_timeout_seconds !== 5 ||
      !/^[0-9a-f]{64}$/u.test(report.corpus_sha256) || report.crash_count !== 0 ||
      report.sanitizer_findings !== 0 || report.timeouts !== 0 || report.oom_failures !== 0) {
    fail(`invalid target report ${key}`);
  }
  return report;
}).sort((left, right) => `${left.platform}:${left.target}`.localeCompare(`${right.platform}:${right.target}`));
if (expected.size !== 0) fail(`missing target reports: ${[...expected].join(",")}`);
const report = {
  schema_version: 1,
  task_id: "M3-02",
  validation_mode: "full-flow",
  mode,
  commit: sha,
  seconds_per_target: seconds,
  memory_limit_mb: 2048,
  input_timeout_seconds: 5,
  targets,
  total_executions: targets.reduce((sum, target) => sum + target.executions, 0),
  crash_count: 0,
  sanitizer_findings: 0,
  timeouts: 0,
  oom_failures: 0,
  runtime_device_matrix: "SEPARATE_API29_API36_KVM_GATE",
  result: "PASS",
};
mkdirSync(path.dirname(output), {recursive: true});
writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
