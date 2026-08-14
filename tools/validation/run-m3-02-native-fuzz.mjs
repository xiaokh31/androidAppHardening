#!/usr/bin/env node

import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync } from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawnSync } from "node:child_process";

function fail(message) {
  throw new Error(`M3-02 Native fuzz failed: ${message}`);
}

function files(root) {
  return readdirSync(root).sort().flatMap((name) => {
    const value = path.join(root, name);
    return statSync(value).isDirectory() ? files(value) : [value];
  });
}

function digest(root) {
  const hash = createHash("sha256");
  for (const value of files(root)) {
    hash.update(path.relative(root, value).replaceAll("\\", "/"));
    hash.update(readFileSync(value));
  }
  return hash.digest("hex");
}

function run(executable, args, timeout) {
  const result = spawnSync(executable, args, {
    stdio: "inherit",
    timeout,
    windowsHide: true,
    env: {
      ...process.env,
      ASAN_OPTIONS: "detect_leaks=1:halt_on_error=1:strict_string_checks=1",
      UBSAN_OPTIONS: "halt_on_error=1:print_stacktrace=1",
    },
  });
  if (result.error || result.status !== 0) fail(`process failed status=${result.status} error=${result.error ?? "none"}`);
}

const [executableValue, secondsValue, workValue] = process.argv.slice(2);
const executable = path.resolve(executableValue ?? "");
const seconds = Number(secondsValue);
const work = path.resolve(workValue ?? "");
if (!existsSync(executable) || !statSync(executable).isFile()) fail("missing libFuzzer executable");
if (!Number.isInteger(seconds) || seconds < 1 || seconds > 3600) fail("invalid fuzz duration");
const allowedWorkRoots = [path.resolve("build"), path.resolve("tools/validation/build")];
if (!allowedWorkRoots.some((root) => work.startsWith(`${root}${path.sep}`))) {
  fail("work must be under an approved ignored build directory");
}
const corpusSource = path.resolve("tools/validation/src/fuzz/resources/corpus/native");
const regressionSource = path.resolve("tools/validation/src/fuzz/resources/regressions/native");
const sourceHashes = [digest(corpusSource), digest(regressionSource)];
rmSync(work, {recursive: true, force: true});
const corpus = path.join(work, "corpus");
const crashes = path.join(work, "crashes");
mkdirSync(corpus, {recursive: true});
mkdirSync(crashes, {recursive: true});
cpSync(corpusSource, corpus, {recursive: true});
cpSync(regressionSource, corpus, {recursive: true});
for (let pass = 0; pass < 2; pass += 1) {
  for (const input of files(corpus)) {
    run(executable, ["-runs=1", "-timeout=5", "-rss_limit_mb=2048", input], 30_000);
  }
}
run(executable, [
  `-max_total_time=${seconds}`,
  "-timeout=5",
  "-rss_limit_mb=2048",
  "-max_len=4194304",
  `-artifact_prefix=${crashes}${path.sep}`,
  corpus,
], (seconds + 30) * 1000);
if (files(crashes).length !== 0) fail("crash artifact was produced");
if (sourceHashes[0] !== digest(corpusSource) || sourceHashes[1] !== digest(regressionSource)) {
  fail("tracked Native corpus changed");
}
process.stdout.write(`OK: M3-02 Native regressions=2 duration=${seconds}s corpus_sha256=${sourceHashes.join(":")}\n`);
