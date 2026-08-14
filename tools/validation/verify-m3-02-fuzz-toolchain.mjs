#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import process from "node:process";

const file = "tools/validation/m3-02-fuzz-toolchain.json";
const lock = JSON.parse(readFileSync(file, "utf8"));
const failures = [];
const check = (condition, message) => { if (!condition) failures.push(message); };
const hex64 = (value) => typeof value === "string" && /^[0-9a-f]{64}$/u.test(value);

check(lock.schema === "m3-02-fuzz-toolchain-v1", "schema");
check(lock.reviewed_at === "2026-08-14", "review date");
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
check(lock.runners?.ubuntu?.reviewed_images?.length === 3, "Ubuntu image count");
check(lock.runners?.windows?.reviewed_images?.length === 3, "Windows image count");
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
check(versions.includes('jazzer = "0.29.1"'), "version catalog lock");
for (const hash of Object.values(lock.jazzer.artifacts)) check(verification.includes(hash), `verification hash ${hash}`);
check(gradleTask.includes("-rss_limit_mb=2048") && gradleTask.includes("-timeout=5") &&
  gradleTask.includes("-max_len=4194304"), "Jazzer limits wiring");
check(nativeRunner.includes('"-rss_limit_mb=2048"') && nativeRunner.includes('"-timeout=5"') &&
  nativeRunner.includes('"-max_len=4194304"'), "libFuzzer limits wiring");
check(cmake.includes("AH_M3_02_LIBFUZZER") && cmake.includes("-fsanitize=fuzzer,address,undefined"), "libFuzzer wiring");
check(workflow.includes("runs-on: ubuntu-24.04") && workflow.includes("os: windows-2025") &&
  !workflow.includes("ubuntu-latest") && !workflow.includes("windows-latest"), "workflow runner lock");
check(workflow.includes("M302_SECONDS: ${{ github.event_name == 'schedule' && '3600' || '600' }}") &&
  workflow.includes("write-m3-02-target-result.mjs") &&
  (workflow.match(/sanitize-m3-02-fuzz-log\.mjs/gu) ?? []).length === 2,
  "workflow duration, sanitized logs and target evidence");
const catalogCheck = spawnSync(process.execPath,
  ["tools/validation/generate-m3-02-tamper-catalog.mjs", "--check"], {encoding: "utf8"});
check(catalogCheck.status === 0, "generated tamper catalog");
const nativeCorpusCheck = spawnSync(process.execPath,
  ["tools/validation/generate-m3-02-native-corpus.mjs", "--check"], {encoding: "utf8"});
check(nativeCorpusCheck.status === 0, "structured Native corpus");

const mutations = [
  (value) => { value.jazzer.version = "latest"; },
  (value) => { value.jazzer.artifacts["jazzer-0.29.1.jar"] = "0".repeat(64); },
  (value) => { value.native.compiler_version = "changed"; },
  (value) => { value.limits.input_timeout_seconds = 0; },
  (value) => { value.runners.ubuntu.label = "ubuntu-latest"; },
  (value) => { value.runners.windows.reviewed_images.push({image_version: "20990101.1.1", manifest_ref: "changed"}); },
];
for (const mutate of mutations) {
  const candidate = structuredClone(lock);
  mutate(candidate);
  const accepted = candidate.jazzer.version === "0.29.1" &&
    candidate.jazzer.artifacts["jazzer-0.29.1.jar"] === lock.jazzer.artifacts["jazzer-0.29.1.jar"] &&
    candidate.native.compiler_version === "18.1.3" && candidate.limits.input_timeout_seconds === 5 &&
    candidate.runners.ubuntu.label === "ubuntu-24.04" && candidate.runners.windows.reviewed_images.length === 3;
  check(!accepted, "lock mutation accepted");
}

if (failures.length !== 0) {
  process.stderr.write(`M3-02 fuzz toolchain verification failed: ${failures.join(", ")}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write("OK: M3-02 Jazzer/Clang/runner lock and negative mutations\n");
}
