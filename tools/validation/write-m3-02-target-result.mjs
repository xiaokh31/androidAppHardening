#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

function fail(message) {
  throw new Error(`M3-02 target result failed: ${message}`);
}

function corpusFiles(root) {
  return readdirSync(root).sort().flatMap((name) => {
    const value = path.join(root, name);
    return statSync(value).isDirectory() ? corpusFiles(value) : [value];
  });
}

function corpusDigest(root) {
  const digest = createHash("sha256");
  for (const value of corpusFiles(root)) {
    digest.update(path.relative(root, value).replaceAll("\\", "/"));
    digest.update(readFileSync(value));
  }
  return digest.digest("hex");
}

const [engine, target, platform, secondsValue, logValue, corpusValue, outputValue] = process.argv.slice(2);
const seconds = Number(secondsValue);
const logPath = path.resolve(logValue ?? "");
const corpusPath = path.resolve(corpusValue ?? "");
const outputPath = path.resolve(outputValue ?? "");
if (!['jazzer', 'libfuzzer'].includes(engine)) fail("invalid engine");
if (!/^[A-Za-z0-9_.-]+$/u.test(target ?? "")) fail("invalid target");
if (!['ubuntu-24.04', 'windows-2025'].includes(platform)) fail("invalid platform");
if (!Number.isInteger(seconds) || ![600, 3600].includes(seconds)) fail("invalid duration");
if (!outputPath.startsWith(`${path.resolve("build")}${path.sep}`)) fail("output must be under build/");

const log = readFileSync(logPath, "utf8");
const executions = [...log.matchAll(/#(\d+)\s+DONE\b/gu)].map((match) => Number(match[1]));
if (executions.length === 0 || Math.max(...executions) < 1) fail("missing completed execution count");
if (/ERROR: (?:AddressSanitizer|UndefinedBehaviorSanitizer|libFuzzer)|out[- ]of[- ]memory|timeout after|uncaught exception/iu.test(log)) {
  fail("crash, sanitizer, timeout, OOM or uncaught exception marker in log");
}

const report = {
  schema_version: 1,
  task_id: "M3-02",
  validation_mode: "full-flow",
  engine: engine === "jazzer" ? "Jazzer 0.29.1" : "libFuzzer Clang 18.1.3 ASan UBSan",
  target,
  platform,
  seconds,
  executions: Math.max(...executions),
  memory_limit_mb: 2048,
  input_timeout_seconds: 5,
  corpus_sha256: corpusDigest(corpusPath),
  crash_count: 0,
  sanitizer_findings: 0,
  timeouts: 0,
  oom_failures: 0,
  result: "PASS",
};
mkdirSync(path.dirname(outputPath), {recursive: true});
writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);
process.stdout.write(`${JSON.stringify(report)}\n`);
