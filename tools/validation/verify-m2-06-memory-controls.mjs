#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const output = path.resolve(process.argv[2] ?? "build/m2-06/memory-controls.json");
if (!output.startsWith(`${path.resolve("build")}${path.sep}`) &&
    !output.startsWith(`${path.resolve("artifacts")}${path.sep}`)) {
  fail("report must stay under ignored build/ or artifacts/");
}
const files = [
  "runtime/native/src/main/cpp/memory_controls.hpp",
  "runtime/native/src/main/cpp/memory_controls.cpp",
  "runtime/native/src/main/cpp/secure_buffer.hpp",
  "runtime/native/src/main/cpp/secure_buffer.cpp",
  "runtime/native/src/main/cpp/authenticated_payload.cpp",
  "runtime/native/src/main/cpp/payload_memory.hpp",
  "runtime/native/src/main/cpp/payload_memory.cpp",
  "runtime/native/src/main/java/ah/runtime/loader/PayloadRuntime.java",
  "runtime/policy/src/main/java/ah/runtime/MemoryControls.java",
  "runtime/policy/src/main/java/ah/runtime/MemoryProtectionReport.java",
];
const sources = new Map(files.map((file) => [file, readFileSync(file, "utf8")]));
const deviceRunner = readFileSync(
  "fixtures/android/src/androidTestM202Fixture/java/ah/runtime/loader/M202DeviceRunner.java",
  "utf8",
);
const deviceAcceptance = readFileSync("tools/validation/run-m2-02-device-acceptance.mjs", "utf8");

verify(sources);
if (!deviceRunner.includes("expectedDontDumpBytes += roundUp(buffer.capacity(), pageSize)") ||
    !deviceRunner.includes("dontDumpBytesDelta >= expectedDontDumpBytes") ||
    !deviceRunner.includes("smaps_dontdump_bytes_delta=") ||
    !deviceAcceptance.includes('markerNumber(instrumentation.stdout, "smaps_dontdump_bytes_delta") >=') ||
    !deviceAcceptance.includes('markerNumber(instrumentation.stdout, "smaps_dontdump_expected_bytes")')) {
  fail("device smaps byte-coverage gate is missing");
}
const report = {
  task_id: "M2-06",
  validation_mode: "pre-cli",
  controls: {
    secure_buffer_move_only: true,
    deterministic_zero_before_unlock: true,
    sealed_read_only: true,
    dont_dump_best_effort: true,
    lock_budget_bytes: 1024 * 1024,
    dex_edge_bytes: 64 * 1024,
    high_process_dumpable: false,
    high_jitter_ms: { minimum: 20, maximum: 50, cryptographic_source: "getrandom" },
  },
  risk_mapping: {
    "ALLOW/LOW": "BASELINE",
    "DEGRADE/MEDIUM": "ELEVATED",
    "DEGRADE/HIGH": "HIGH",
  },
  unavailable_controls: "reported-best-effort-no-integrity-fallback",
  source_sha256: sha256(Buffer.from(files.map((file) => `${file}\0${sources.get(file)}\0`).join(""))),
  result: "PASS",
};
mkdirSync(path.dirname(output), { recursive: true });
writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);

const mutated = new Map(sources);
mutated.set(
  "runtime/native/src/main/cpp/memory_controls.hpp",
  mutated.get("runtime/native/src/main/cpp/memory_controls.hpp").replace(
    "1024U * 1024U", "2U * 1024U * 1024U"));
let rejected = false;
try {
  verify(mutated);
} catch (error) {
  rejected = String(error).includes("M2-06 memory controls verification failed");
}
if (!rejected) fail("lock-budget mutation was accepted");
process.stdout.write(`M2-06 memory controls verification PASS report=${path.relative(process.cwd(), output)}\n`);

function verify(values) {
  requireText(values, files[0], "kMaximumLockedBytes = 1024U * 1024U");
  requireText(values, files[0], "kDexEdgeBytes = 64U * 1024U");
  requireText(values, files[1], "mlock(rounded_data, rounded_size)");
  requireText(values, files[1], "MADV_DONTDUMP");
  requireText(values, files[1], "PR_SET_DUMPABLE, 0");
  requireText(values, files[1], "getrandom(&random_value");
  requireText(values, files[1], "20U + random_value % 31U");
  requireText(values, files[1], "RandomValueScrubber random_scrubber");
  requireText(values, files[2], "SecureBuffer(const SecureBuffer&) = delete");
  requireText(values, files[2], "SecureBuffer(SecureBuffer&& other) noexcept");
  const release = values.get(files[3]);
  const zero = release.indexOf("crypto::secureZero(data_, allocation_size_)");
  const unlock = release.indexOf("unlockRegion(&locked_)");
  if (zero < 0 || unlock < 0 || zero >= unlock) fail("SecureBuffer does not zero before unlock");
  requireText(values, files[4], "ShareScrubber config_scrubber");
  requireText(values, files[4], "ShareScrubber share_scrubber");
  requireText(values, files[4], "memory::SecureBuffer r_java{32, true}");
  requireText(values, files[4], "memory::SecureBuffer r_native{32, true}");
  requireText(values, files[4], "crypto::secureZero(config_->r_java.data()");
  requireText(values, files[4], "crypto::secureZero(slot_->r_native.data()");
  requireText(values, files[4], "root[index] = r_native[index] ^ value->r_java[index]");
  requireText(values, files[6], "dont_dump = adviseDontDump(data, size)");
  requireText(values, files[6], "lockEdgesBestEffort()");
  requireText(values, files[7], "applyMemoryProfile(");
  requireText(values, files[8], "RiskLevel.LOW && action == RiskAction.ALLOW");
  requireText(values, files[8], "RiskLevel.MEDIUM && action == RiskAction.DEGRADE");
  requireText(values, files[8], "RiskLevel.HIGH && action == RiskAction.DEGRADE");
  requireText(values, files[8], "AAH-RUNTIME-MEMORY-POLICY");
  requireText(values, files[9], "1024L * 1024L");
  for (const [file, text] of values) {
    if (/__arm__|__aarch64__|__i386__|__x86_64__|ANDROID_ABI/u.test(text)) {
      fail(`architecture-specific policy branch in ${file}`);
    }
    if (/Log\.|System\.out|printf\s*\(/u.test(text)) {
      fail(`memory control logging surface in ${file}`);
    }
  }
}

function requireText(values, file, expected) {
  if (!values.get(file)?.includes(expected)) fail(`${file} is missing ${expected}`);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function fail(message) {
  throw new Error(`M2-06 memory controls verification failed: ${message}`);
}
