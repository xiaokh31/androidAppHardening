#!/usr/bin/env node

import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.argv[2] ?? process.cwd());
const sourceRoot = path.join(root, "runtime", "bootstrap", "src", "main", "java");

async function javaFiles(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await javaFiles(full));
    else if (entry.isFile() && entry.name.endsWith(".java")) files.push(full);
  }
  return files;
}

function requireCondition(condition, message) {
  if (!condition) throw new Error(`M2-01 architecture verification failed: ${message}`);
}

const combined = (await Promise.all((await javaFiles(sourceRoot)).map((file) => readFile(file, "utf8"))))
  .join("\n");
const shell = await readFile(path.join(sourceRoot,
  "ah", "runtime", "bootstrap", "ShellAppComponentFactory.java"), "utf8");
const bootstrap = await readFile(path.join(sourceRoot,
  "ah", "runtime", "bootstrap", "HardeningBootstrap.java"), "utf8");

requireCondition(bootstrap.includes("RuntimeStartupGuard.openVerifiedPayload(applicationInfo, shellLoader)"),
  "frozen Guard call is absent");
requireCondition((bootstrap.match(/RuntimeStartupGuard\.openVerifiedPayload\(/gu) ?? []).length === 1,
  "Guard call count is not exactly one");
for (const method of ["instantiateClassLoader", "instantiateApplication", "instantiateActivity",
  "instantiateService", "instantiateReceiver", "instantiateProvider"]) {
  requireCondition(shell.includes(method + "("), `missing public entry ${method}`);
}
for (const forbidden of [
  /import\s+ah\.runtime\.loader/u,
  /\bPayloadRuntime\b/u,
  /\bInMemoryDexClassLoader\b/u,
  /\bDexClassLoader\b/u,
  /\bApplicationInfo\.metaData\b|\.metaData\b/u,
  /\bPackageManager\b/u,
  /\bActivityThread\b/u,
  /\bLoadedApk\b/u,
  /setAccessible\s*\(/u,
]) {
  requireCondition(!forbidden.test(combined), `forbidden production pattern ${forbidden}`);
}
requireCondition(!combined.includes("dex\n") && !combined.includes("classes.dex"),
  "production source contains a plaintext DEX output path");
requireCondition(bootstrap.includes("State.NEW") || bootstrap.includes("NEW,"), "NEW state absent");
for (const state of ["INSTALLING", "READY", "FAILED"]) {
  requireCondition(bootstrap.includes(state), `${state} state absent`);
}
requireCondition(shell.includes("AAH-RUNTIME-BOOT-") || combined.includes("AAH-RUNTIME-BOOT-"),
  "stable bootstrap error prefix absent");

console.log(JSON.stringify({
  task: "M2-01",
  result: "PASS",
  guard_entry: "RuntimeStartupGuard.openVerifiedPayload",
  public_factory_entries: 6,
  hidden_api_patterns: 0,
  direct_loader_api_patterns: 0,
  metadata_reads: 0,
  plaintext_dex_outputs: 0,
}, null, 2));
