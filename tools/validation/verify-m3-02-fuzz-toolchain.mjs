#!/usr/bin/env node

import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import process from "node:process";

const file = "tools/validation/m3-02-fuzz-toolchain.json";
const lock = JSON.parse(readFileSync(file, "utf8"));
const failures = [];
const check = (condition, message) => { if (!condition) failures.push(message); };
const hex64 = (value) => typeof value === "string" && /^[0-9a-f]{64}$/u.test(value);

check(lock.schema === "m3-02-fuzz-toolchain-v1", "schema");
check(lock.reviewed_at === "2026-08-22", "review date");
check(lock.jazzer?.version === "0.29.1", "Jazzer version");
check(lock.jazzer?.repository === "https://repo.maven.apache.org/maven2", "Maven repository");
check(lock.jazzer?.source === "https://github.com/CodeIntelligenceTesting/jazzer", "Jazzer source");
check(lock.jazzer?.license === "Apache-2.0", "Jazzer license");
check(Object.keys(lock.jazzer?.artifacts ?? {}).join(",") === [
  "jazzer-0.29.1.jar", "jazzer-0.29.1.pom", "jazzer-api-0.29.1.jar", "jazzer-api-0.29.1.pom",
].join(","), "Jazzer artifact set/order");
check(Object.values(lock.jazzer?.artifacts ?? {}).every(hex64), "Jazzer artifact SHA-256");
check(lock.native?.compiler === "clang++-18" && lock.native?.compiler_version === "18.1.3", "Native Clang");
check(JSON.stringify(lock.native?.sanitizers) === JSON.stringify(["fuzzer", "address", "undefined"]), "sanitizers");
check(JSON.stringify(lock.limits) === JSON.stringify({
  memory_mb: 2048,
  input_timeout_seconds: 5,
  pr_seconds_per_target: 600,
  nightly_seconds_per_target: 3600,
}), "resource limits");
check(lock.runners?.ubuntu?.label === "ubuntu-24.04" && lock.runners?.ubuntu?.image_os === "ubuntu24", "Ubuntu runner");
check(lock.runners?.windows?.label === "windows-2025" && lock.runners?.windows?.image_os === "win25-vs2026", "Windows runner");
const expectedUbuntuImages = [
  {image_version: "20260720.247.2", manifest_ref: "ubuntu24/20260720.247"},
  {image_version: "20260804.265.1", manifest_ref: "ubuntu24/20260804.265"},
  {image_version: "20260810.271.1", manifest_ref: "ubuntu24/20260810.271"},
  {image_version: "20260816.277.1", manifest_ref: "ubuntu24/20260816.277"},
];
const expectedWindowsImages = [
  {image_version: "20260728.188.1", manifest_ref: "win25-vs2026/20260728.188"},
  {image_version: "20260803.193.1", manifest_ref: "win25-vs2026/20260803.193"},
  {image_version: "20260810.198.2", manifest_ref: "win25-vs2026/20260810.198"},
  {image_version: "20260818.207.1", manifest_ref: "win25-vs2026/20260818.207"},
];
check(JSON.stringify(lock.runners?.ubuntu?.reviewed_images) === JSON.stringify(expectedUbuntuImages),
  "Ubuntu image lock/order");
check(JSON.stringify(lock.runners?.windows?.reviewed_images) === JSON.stringify(expectedWindowsImages),
  "Windows image lock/order");
for (const family of ["ubuntu", "windows"]) {
  const images = lock.runners[family].reviewed_images;
  check(new Set(images.map((entry) => entry.image_version)).size === images.length, `${family} duplicate image`);
  check(images.every((entry) => /^[0-9]{8}\.[0-9]+\.[0-9]+$/u.test(entry.image_version) &&
    entry.manifest_ref.includes("/")), `${family} image format`);
}

const versions = readFileSync("gradle/libs.versions.toml", "utf8");
const verification = readFileSync("gradle/verification-metadata.xml", "utf8");
const gradleTask = readFileSync("tools/validation/build.gradle.kts", "utf8");
const cmake = readFileSync("runtime/native/src/main/cpp/CMakeLists.txt", "utf8");
const nativeRunner = readFileSync("tools/validation/run-m3-02-native-fuzz.mjs", "utf8");
const workflow = readFileSync(".github/workflows/m3-02-fuzz.yml", "utf8");
const buildWorkflow = readFileSync(".github/workflows/build.yml", "utf8");
const kvmWorkflow = readFileSync(".github/workflows/m0-05-linux-kvm.yml", "utf8");
const deviceRunner = readFileSync("tools/validation/run-m2-02-device-acceptance.mjs", "utf8");
const deviceSummary = readFileSync("tools/validation/summarize-m3-02-device-tamper.mjs", "utf8");
const signerMatrix = readFileSync("tools/validation/run-m2-03-signer-matrix.mjs", "utf8");
const jvmCorpus = readFileSync("tools/validation/generate-m3-02-jvm-corpus.mjs", "utf8");
const testApkCreator = readFileSync("tools/validation/create-m0-05-test-apks.mjs", "utf8");
const tamperRunner = readFileSync(
  "tools/validation/src/main/java/ah/tools/validation/tamper/TamperCatalogRunner.java", "utf8");
check(versions.includes('jazzer = "0.29.1"'), "version catalog lock");
for (const hash of Object.values(lock.jazzer.artifacts)) check(verification.includes(hash), `verification hash ${hash}`);
check(gradleTask.includes('maxHeapSize = "128m"') &&
  gradleTask.includes('"-XX:MaxMetaspaceSize=192m"') &&
  gradleTask.includes('"-XX:ReservedCodeCacheSize=64m"') &&
  gradleTask.includes('"-XX:MaxDirectMemorySize=64m"') &&
  gradleTask.includes('"-Xss256k"') && gradleTask.includes('"-XX:+UseSerialGC"') &&
  gradleTask.includes("-rss_limit_mb=2048") &&
  gradleTask.includes('"--instrumentation_includes=ah.host.**${File.pathSeparator}ah.tools.validation.fuzz.**"') &&
  gradleTask.includes('"--custom_hook_includes=ah.host.**${File.pathSeparator}ah.tools.validation.fuzz.**"') &&
  gradleTask.includes("-timeout=5") && gradleTask.includes("-max_len=4194304"),
"Jazzer owned-surface instrumentation, JVM pools and total RSS limits wiring");
check(gradleTask.includes('from(m302RegressionRoot.dir(corpusName))') &&
  gradleTask.includes('dependsOn(tasks.named("classes"), prepareCorpus, "regressionFuzz")'),
"JVM regression preflight wiring");
check(nativeRunner.includes('"-rss_limit_mb=2048"') && nativeRunner.includes('"-timeout=5"') &&
  nativeRunner.includes('"-max_len=4194304"') &&
  nativeRunner.includes('path.resolve("tools/validation/build")'), "libFuzzer limits and ignored work root wiring");
check(cmake.includes("AH_M3_02_LIBFUZZER") && cmake.includes("-fsanitize=fuzzer,address,undefined"), "libFuzzer wiring");
check(workflow.includes("runs-on: ubuntu-24.04") && workflow.includes("os: windows-2025") &&
  !workflow.includes("ubuntu-latest") && !workflow.includes("windows-latest"), "workflow runner lock");
check(workflow.includes("M302_SECONDS: ${{ github.event_name == 'schedule' && '3600' || '600' }}") &&
  workflow.includes("write-m3-02-target-result.mjs") &&
  workflow.includes("actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093") &&
  workflow.includes("build/m3-02/targets") &&
  (workflow.match(/sanitize-m3-02-fuzz-log\.mjs/gu) ?? []).length === 2,
  "workflow duration, sanitized logs and target evidence");
check(kvmWorkflow.includes("REVISION: ${{ matrix.revision }}") &&
  kvmWorkflow.includes('avd_name="m0_05_api${API}_r${REVISION}_x86_64"') &&
  !kvmWorkflow.includes('avd_name="m0_05_api${API}_r${{ matrix.revision }}_x86_64"'),
"bounded KVM run block avoids GitHub expression length limit");
check((workflow.match(/actions\/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093/gu) ?? []).length === 5,
  "five exact target artifact downloads");
check(deviceRunner.includes("parseM302Cases") && deviceRunner.includes("m302_cases: m302Cases") &&
  signerMatrix.includes("m302_cases: m302Cases") &&
  deviceSummary.includes("signer.m302_cases.length !== startupCatalog.length") &&
  deviceSummary.includes("duplicate startup case") &&
  deviceSummary.includes("for (const field of fields)") && deviceSummary.includes("variantCases"),
"named per-case device evidence");
check(jvmCorpus.includes("hasApkSigningBlock") &&
  jvmCorpus.includes('assertUnsigned(apk, parsed, "source APK")'),
  "v1 and v2/v3 unsigned corpus boundary");
check(testApkCreator.includes("PAYLOAD_V2_ONLY_MUTATIONS") &&
  testApkCreator.includes("supportsPayloadV2Mutations || !PAYLOAD_V2_ONLY_MUTATIONS.has(name)"),
  "legacy M0-05 payload excludes M3-02-only authenticated-container mutations");
check((buildWorkflow.match(/b287183d1c2af46cfb9ce4b027e7993ec9721e039f91c3125176a962a2ddd641/gu) ?? []).length === 2,
  "dual-platform Build locks the extended M1-03 error matrix");
check(kvmWorkflow.includes('"build/m2-06/device-api${API}-x86_64/report.json"') &&
  deviceSummary.includes('["M2-02", "M2-06"].includes(loader.task_id)'),
  "M3-02 consumes the executed M202/M2-06 loader report");
check(tamperRunner.includes('stage = "INSPECT"') && tamperRunner.includes('stage = "MANIFEST"') &&
  tamperRunner.includes("stage mismatch"), "fixed Host stage evidence");
const catalogCheck = spawnSync(process.execPath,
  ["tools/validation/generate-m3-02-tamper-catalog.mjs", "--check"], {encoding: "utf8"});
check(catalogCheck.status === 0, "generated tamper catalog");
const nativeCorpusCheck = spawnSync(process.execPath,
  ["tools/validation/generate-m3-02-native-corpus.mjs", "--check"], {encoding: "utf8"});
check(nativeCorpusCheck.status === 0, "structured Native corpus");
const jvmCorpusCheck = spawnSync(process.execPath,
  ["tools/validation/generate-m3-02-jvm-corpus.mjs", "--check"], {encoding: "utf8"});
check(jvmCorpusCheck.status === 0, "valid structured JVM corpus");

const summaryRoot = "build/m3-02/summary-self-test";
const summaryOutput = "build/m3-02/summary-self-test.json";
const summaryCommit = "1".repeat(40);
rmSync(summaryRoot, {recursive: true, force: true});
const targetSpecs = [
  ["Jazzer 0.29.1", "ApkInspectorFuzzTarget", "ubuntu-24.04"],
  ["Jazzer 0.29.1", "BinaryAxmlFuzzTarget", "ubuntu-24.04"],
  ["Jazzer 0.29.1", "ApkInspectorFuzzTarget", "windows-2025"],
  ["Jazzer 0.29.1", "BinaryAxmlFuzzTarget", "windows-2025"],
  ["libFuzzer Clang 18.1.3 ASan UBSan", "AHDCNativeFuzzTarget", "ubuntu-24.04"],
];
targetSpecs.forEach(([engine, target, platform], index) => {
  const directory = `${summaryRoot}/${index}`;
  mkdirSync(directory, {recursive: true});
  writeFileSync(`${directory}/target-result.json`, `${JSON.stringify({
    schema_version: 1, task_id: "M3-02", validation_mode: "full-flow", commit: summaryCommit,
    mode: "pr", engine, target, platform, seconds: 600, executions: index + 1,
    memory_limit_mb: 2048, input_timeout_seconds: 5, corpus_sha256: String(index).repeat(64),
    crash_count: 0, sanitizer_findings: 0, timeouts: 0, oom_failures: 0, result: "PASS",
  })}\n`);
});
const summaryEnvironment = {...process.env, GITHUB_SHA: summaryCommit};
const summaryPass = spawnSync(process.execPath,
  ["tools/validation/write-m3-02-ci-summary.mjs", "pr", "600", summaryRoot, summaryOutput],
  {encoding: "utf8", env: summaryEnvironment});
check(summaryPass.status === 0, "real target summary aggregation");
const failedTarget = `${summaryRoot}/0/target-result.json`;
const failedValue = JSON.parse(readFileSync(failedTarget, "utf8"));
failedValue.result = "FAIL";
writeFileSync(failedTarget, `${JSON.stringify(failedValue)}\n`);
const summaryReject = spawnSync(process.execPath,
  ["tools/validation/write-m3-02-ci-summary.mjs", "pr", "600", summaryRoot, summaryOutput],
  {encoding: "utf8", env: summaryEnvironment});
check(summaryReject.status !== 0, "non-PASS target summary accepted");
rmSync(summaryRoot, {recursive: true, force: true});
rmSync(summaryOutput, {force: true});

const mutations = [
  (value) => { value.jazzer.version = "latest"; },
  (value) => { value.jazzer.artifacts["jazzer-0.29.1.jar"] = "0".repeat(64); },
  (value) => { value.native.compiler_version = "changed"; },
  (value) => { value.limits.input_timeout_seconds = 0; },
  (value) => { value.runners.ubuntu.label = "ubuntu-latest"; },
  (value) => { value.runners.ubuntu.reviewed_images[3].manifest_ref = "ubuntu24/changed"; },
  (value) => { value.runners.windows.reviewed_images.push({image_version: "20990101.1.1", manifest_ref: "changed"}); },
];
for (const mutate of mutations) {
  const candidate = structuredClone(lock);
  mutate(candidate);
  const accepted = candidate.jazzer.version === "0.29.1" &&
    candidate.jazzer.artifacts["jazzer-0.29.1.jar"] === lock.jazzer.artifacts["jazzer-0.29.1.jar"] &&
    candidate.native.compiler_version === "18.1.3" && candidate.limits.input_timeout_seconds === 5 &&
    candidate.runners.ubuntu.label === "ubuntu-24.04" &&
    JSON.stringify(candidate.runners.ubuntu.reviewed_images) === JSON.stringify(expectedUbuntuImages) &&
    JSON.stringify(candidate.runners.windows.reviewed_images) === JSON.stringify(expectedWindowsImages);
  check(!accepted, "lock mutation accepted");
}

if (failures.length !== 0) {
  process.stderr.write(`M3-02 fuzz toolchain verification failed: ${failures.join(", ")}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write("OK: M3-02 Jazzer/Clang/runner lock and negative mutations\n");
}
