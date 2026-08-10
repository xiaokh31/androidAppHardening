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
const deviceRunner = await readFile(path.join(root, "fixtures", "android", "src",
  "androidTestM201Fixture", "java", "ah", "fixtures", "android", "m201",
  "M201DeviceRunner.java"), "utf8");
const deviceManifest = await readFile(path.join(root, "fixtures", "android", "src",
  "m201Fixture", "AndroidManifest.xml"), "utf8");
const compatibilityManifest = await readFile(path.join(root, "fixtures", "android", "src",
  "compatFixture", "AndroidManifest.xml"), "utf8");
const legacyShell = await readFile(path.join(root, "fixtures", "android", "src",
  "compatFixture", "java", "ah", "runtime", "bootstrap",
  "LegacyShellAppComponentFactory.java"), "utf8");
const kvmWorkflow = await readFile(path.join(root, ".github", "workflows",
  "m0-05-linux-kvm.yml"), "utf8");

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
for (const marker of ["platform_callbacks=6", "main_install=1", "secondary_install=1",
  "custom_application=true", "early_provider=true", "multidex=true", "jni=true",
  "metadata_null=true", "plaintext_dex_files=0"]) {
  requireCondition(deviceRunner.includes(marker), `device acceptance marker absent: ${marker}`);
}
requireCondition(deviceManifest.includes('android:process=":m201secondary"'),
  "independent-process component is absent");
requireCondition(deviceManifest.includes(
  'android:appComponentFactory="ah.runtime.bootstrap.ShellAppComponentFactory"'),
  "production M2-01 fixture does not use the production Shell");
requireCondition(compatibilityManifest.includes(
  'android:appComponentFactory="ah.runtime.bootstrap.LegacyShellAppComponentFactory"'),
  "legacy M0-05 fixture is not isolated from the production Shell");
requireCondition(legacyShell.includes("M0-05 fixture-only compatibility proof")
    && !legacyShell.includes("RuntimeStartupGuard"),
  "legacy M0-05 fixture crossed into the production Guard contract");
requireCondition(!combined.includes("LegacyShellAppComponentFactory"),
  "fixture-only legacy Shell entered production sources");
requireCondition(kvmWorkflow.includes("run-m2-01-device-acceptance.mjs")
    && kvmWorkflow.includes("assembleM201ExtractedRelease")
    && kvmWorkflow.includes("assembleM201DirectRelease"),
  "API 29/36 KVM Release/R8 acceptance is not wired");

console.log(JSON.stringify({
  task: "M2-01",
  result: "PASS",
  guard_entry: "RuntimeStartupGuard.openVerifiedPayload",
  public_factory_entries: 6,
  hidden_api_patterns: 0,
  direct_loader_api_patterns: 0,
  metadata_reads: 0,
  plaintext_dex_outputs: 0,
  real_device_acceptance_wired: true,
  legacy_poc_isolated: true,
  process_modes: 2,
  release_r8_variants: 2,
}, null, 2));
