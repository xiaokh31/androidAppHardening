#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const failures = [];
const checks = [];

function read(relative) {
  return readFileSync(path.join(root, relative), "utf8");
}

function check(condition, name) {
  checks.push({ name, passed: Boolean(condition) });
  if (!condition) failures.push(name);
}

function filesBelow(relative) {
  const result = [];
  const visit = (absolute) => {
    for (const name of readdirSync(absolute)) {
      const child = path.join(absolute, name);
      if (statSync(child).isDirectory()) visit(child);
      else result.push(child);
    }
  };
  visit(path.join(root, relative));
  return result;
}

const catalog = read("gradle/libs.versions.toml");
const policyLock = read("runtime/policy/gradle.lockfile");
check(
  /^agp\s*=\s*"9\.3\.0"$/mu.test(catalog) &&
    /android-apksig\s*=\s*\{[^\n]*version\.ref\s*=\s*"agp"/u.test(catalog),
  "apksig catalog pin 9.3.0",
);
check(policyLock.includes("com.android.tools.build:apksig:9.3.0="), "policy apksig lock");

const bootstrapBuild = read("runtime/bootstrap/build.gradle.kts");
check(!bootstrapBuild.includes('project(":runtime:native")'), "bootstrap has no native compile dependency");
const bootstrapSources = filesBelow("runtime/bootstrap/src/main/java")
  .filter((file) => file.endsWith(".java"))
  .map((file) => readFileSync(file, "utf8"));
check(bootstrapSources.every((source) => !source.includes("ah.runtime.loader")), "bootstrap has no loader reference");

const runtimeProduction = filesBelow("runtime")
  .filter((file) => file.includes(`${path.sep}src${path.sep}main${path.sep}`) && file.endsWith(".java"));
const callers = runtimeProduction
  .filter((file) => !file.includes(`${path.sep}runtime${path.sep}native${path.sep}`))
  .filter((file) => readFileSync(file, "utf8").includes("PayloadRuntime."))
  .map((file) => path.relative(root, file).replaceAll("\\", "/"));
check(
  callers.length === 1 && callers[0] === "runtime/policy/src/main/java/ah/runtime/guard/RuntimeStartupGuard.java",
  "RuntimeStartupGuard is sole production PayloadRuntime caller",
);

const policyProduction = filesBelow("runtime/policy/src/main/java")
  .filter((file) => file.endsWith(".java"))
  .map((file) => readFileSync(file, "utf8"))
  .join("\n");
const forbiddenCapabilities = [
  /\bPrivateKey\b/u,
  /\bKeyStore\b/u,
  /\bApkSigner\b/u,
  /\bapksigner\b/u,
  /\bjarsigner\b/u,
  /\.sign\s*\(/u,
];
check(forbiddenCapabilities.every((pattern) => !pattern.test(policyProduction)), "no product signing or private-key capability");
check(!/android\.content\.Context|PackageManager|SigningInfo/u.test(policyProduction), "no Context or PackageManager startup dependency");

const guard = read("runtime/policy/src/main/java/ah/runtime/guard/RuntimeStartupGuard.java");
const sequence = [
  "RuntimeSignerVerifier.verify",
  "PayloadRuntime.inspectBinding",
  "verifyPreReadSigner",
  "PayloadRuntime.openVerified",
  "verifyAuthenticatedMetadata",
  "new VerifiedSignerIdentity",
  "new VerifiedStartupConfiguration",
  "new VerifiedPayloadSession",
  "committed = true",
];
let cursor = -1;
let ordered = true;
for (const token of sequence) {
  const next = guard.indexOf(token, cursor + 1);
  if (next < 0) ordered = false;
  cursor = next;
}
check(ordered, "frozen Guard verification order");
check(guard.includes("if (!committed && loadedPayload != null)"), "Guard rollback owner");
check(guard.includes("primary.addSuppressed(cleanupFailure)"), "cleanup suppression preserves primary");

check(statSync(path.join(root, "runtime/policy/src/test/java/ah/runtime/guard/PolicySelfTest.java")).size > 0, "non-empty JVM policy matrix");
check(statSync(path.join(root, "runtime/policy/src/androidTest/java/ah/runtime/guard/PolicyConnectedRunner.java")).size > 0, "non-empty connected policy runner");
check(statSync(path.join(root, "fixtures/android/src/androidTestM203Fixture/java/ah/runtime/guard/M203DeviceRunner.java")).size > 0, "non-empty Guard device runner");

const report = {
  task_id: "M2-03",
  validation_mode: "pre-cli",
  checks,
  production_source_sha256: createHash("sha256").update(policyProduction).digest("hex"),
  payload_runtime_callers: callers,
  result: failures.length === 0 ? "PASS" : "FAIL",
};
const reportDir = path.join(root, "build/reports/m2-03");
mkdirSync(reportDir, { recursive: true });
writeFileSync(path.join(reportDir, "architecture-and-capability-scan.json"), `${JSON.stringify(report, null, 2)}\n`);
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
if (failures.length !== 0) {
  process.stderr.write(`M2-03 verification failed: ${failures.join(", ")}\n`);
  process.exitCode = 1;
}
