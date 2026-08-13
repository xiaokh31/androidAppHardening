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
    for (const entry of readdirSync(absolute, { withFileTypes: true })) {
      const child = path.join(absolute, entry.name);
      if (entry.isDirectory()) visit(child);
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

const runtimeProduction = ["bootstrap", "native", "policy"]
  .flatMap((module) => filesBelow(`runtime/${module}/src/main/java`))
  .filter((file) => file.endsWith(".java"));
const callers = runtimeProduction
  .filter((file) => !file.includes(`${path.sep}runtime${path.sep}native${path.sep}`))
  .filter((file) => readFileSync(file, "utf8").includes("PayloadRuntime."))
  .map((file) => path.relative(root, file).replaceAll("\\", "/"))
  .sort();
const guard = read("runtime/policy/src/main/java/ah/runtime/guard/RuntimeStartupGuard.java");
const memoryControls = read("runtime/policy/src/main/java/ah/runtime/MemoryControls.java");
const guardRuntimeCalls = [...guard.matchAll(/PayloadRuntime\.(\w+)\s*\(/gu)].map((match) => match[1]).sort();
const memoryControlRuntimeCalls = [...memoryControls.matchAll(/PayloadRuntime\.(\w+)\s*\(/gu)]
  .map((match) => match[1])
  .sort();
check(
  JSON.stringify(callers) ===
    JSON.stringify([
      "runtime/policy/src/main/java/ah/runtime/MemoryControls.java",
      "runtime/policy/src/main/java/ah/runtime/guard/RuntimeStartupGuard.java",
    ]) &&
    JSON.stringify(guardRuntimeCalls) === JSON.stringify(["inspectBinding", "openVerified"]) &&
    JSON.stringify(memoryControlRuntimeCalls) === JSON.stringify(["applyMemoryProfile"]),
  "Guard open and MemoryControls profile are the fixed PayloadRuntime caller boundary",
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
const connectedRunner = read("runtime/policy/src/androidTest/java/ah/runtime/guard/PolicyConnectedRunner.java");
const kvmWorkflow = read(".github/workflows/m0-05-linux-kvm.yml");
check(
  connectedRunner.includes("policy_connected=true cases=15") &&
    connectedRunner.includes("RuntimeSignerVerifier.verify(self)") &&
    connectedRunner.includes("verifyAcrossProcesses(self)") &&
    connectedRunner.includes("m2-03-secondary-ready") &&
    kvmWorkflow.includes("ah.runtime.policy.test/ah.runtime.guard.PolicyConnectedRunner") &&
    kvmWorkflow.includes("grep -F 'policy_connected=true cases=15'") &&
    kvmWorkflow.includes("primary_cache_hit=true secondary_cache_hit=true") &&
    kvmWorkflow.includes('test "$policy_pid_one" != "$policy_pid_two"'),
  "non-empty connected policy runner",
);
const guardRunner = read("fixtures/android/src/androidTestM203Fixture/java/ah/runtime/guard/M203DeviceRunner.java");
const fixtureProguard = read("fixtures/android/proguard-rules.pro");
check(
  guardRunner.includes("guard_failure_injection=") &&
    guardRunner.includes("captureNativeHandle") &&
    guardRunner.includes("requireNativeHandleClosed") &&
    guardRunner.includes("captureLoadedPayload") &&
    fixtureProguard.includes("-keep class ah.runtime.loader.PayloadMemoryHandle { *; }") &&
    fixtureProguard.includes("-keep class ah.runtime.loader.UntrustedPayloadBinding { *; }") &&
    fixtureProguard.includes("-keep class ah.runtime.guard.RuntimeSignerVerifier { *; }") &&
    fixtureProguard.includes("-keep class ah.runtime.guard.IntegrityChecks { *; }"),
  "non-empty Guard device runner with R8-safe Native ownership observer",
);
const signerMatrix = read("tools/validation/run-m2-03-signer-matrix.mjs");
check(
  signerMatrix.includes('verifyFixture("valid-rotation"') &&
    signerMatrix.includes('verifyFixture("multiple-current"') &&
    signerMatrix.includes('verifyStartup("historical-only"') &&
    signerMatrix.includes("lookup_count=0 session_published=false") &&
    signerMatrix.includes("run_token=${runToken}") &&
    signerMatrix.includes("matchingMarkers.length !== 1") &&
    signerMatrix.includes("unexpected install failure") &&
    signerMatrix.includes("assertNoSensitiveEvidence") &&
    kvmWorkflow.includes("run-m2-03-signer-matrix.mjs"),
  "device signer, tamper, publication and evidence-safety matrix",
);

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
