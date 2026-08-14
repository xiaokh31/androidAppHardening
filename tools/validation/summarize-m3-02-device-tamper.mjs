#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

function fail(message) {
  throw new Error(`M3-02 device summary failed: ${message}`);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function readJson(value, label) {
  try {
    return JSON.parse(readFileSync(value, "utf8"));
  } catch (error) {
    fail(`invalid ${label}: ${error}`);
  }
}

function runtimeCatalog(value) {
  const cases = [];
  let current = null;
  for (const raw of readFileSync(value, "utf8").split(/\r?\n/u)) {
    const line = raw.trimEnd();
    if (line.trim() === "" || line.trimStart().startsWith("#") || line === "cases:") continue;
    if (line.startsWith("  - ")) {
      current = {};
      cases.push(current);
    }
    if (!current) fail("catalog field before case");
    const content = line.startsWith("  - ") ? line.slice(4) : line.trimStart();
    const split = content.indexOf(":");
    if (split <= 0) fail("invalid catalog line");
    current[content.slice(0, split)] = content.slice(split + 1).trim().replace(/^"|"$/gu, "");
  }
  return cases.filter((entry) => ["runtime-prehandle", "runtime-posthandle", "guard"].includes(entry.target));
}

const [catalogPath, signerPath, loaderPath, guardPath, platform, outputPath] = process.argv.slice(2);
if (!catalogPath || !signerPath || !loaderPath || !guardPath || !platform || !outputPath) {
  fail("usage: <catalog> <signer-report> <loader-report> <guard-report> <platform> <output>");
}
const catalog = runtimeCatalog(catalogPath);
const signer = readJson(signerPath, "signer report");
const loader = readJson(loaderPath, "loader report");
const guard = readJson(guardPath, "Guard report");
const expectedStartup = new Map([
  ["m302-runtime-different-signer", "different-signer"],
  ["m302-runtime-config-version", "config-version-tamper"],
  ["m302-runtime-factory-flags", "factory-slot-tamper"],
  ["m302-runtime-binding-slot", "binding-slot-tamper"],
  ["m302-runtime-container", "container-ciphertext-tamper"],
  ...["nonce", "tag-first", "tag-middle", "tag-last", "ciphertext-first", "ciphertext-middle", "ciphertext-last"]
    .map((name) => [`m302-runtime-${name}`, `m302-${name}`]),
]);
if (signer.result !== "PASS" || signer.cleanup_passed !== true ||
    !Array.isArray(signer.startup_rejection_matrix)) {
  fail("M3-02 startup matrix did not pass completely");
}
for (const [catalogId, resultName] of expectedStartup) {
  const result = signer.startup_rejection_matrix.find((entry) => entry.name === resultName);
  if (!result || result.result !== "PASS" ||
      result.lookup_count !== 0 || result.session_published !== false || result.install_rejected !== false) {
    fail(`invalid startup result: ${catalogId}`);
  }
}
if (loader.task_id !== "M2-02" || loader.result !== "PASS" || loader.cleanup_passed !== true ||
    !Array.isArray(loader.variants) || loader.variants.length !== 2) {
  fail("loader acceptance did not pass both variants");
}
for (const variant of loader.variants) {
  const contract = variant.failure_publication_contract;
  if (variant.instrumentation_passed !== true || variant.failure_injection_windows !== 20 ||
      !contract || contract.payload_loaded !== false ||
      contract.payload_class_lookup_attempted !== false || contract.byte_buffers_published !== false ||
      contract.loaded_payload_published !== false ||
      contract.verified_payload_session_published !== false ||
      contract.native_close_count_exactly_once !== true ||
      contract.partial_java_references_cleared !== true ||
      contract.partial_guard_references_cleared !== "not_applicable" ||
      contract.completed_mappings_zeroized_unmapped !== true ||
      contract.partial_mapping_zeroized_unmapped !== true ||
      contract.primary_code_preserved !== true || contract.cleanup_failure_suppressed !== true) {
    fail(`loader publication contract failed for ${variant.name}`);
  }
}
if (guard.task_id !== "M2-03" || guard.result !== "PASS" || guard.cleanup_passed !== true ||
    !Array.isArray(guard.variants) || guard.variants.length !== 2) {
  fail("Guard acceptance did not pass both variants");
}
for (const variant of guard.variants) {
  const contract = variant.failure_publication_contract;
  if (variant.instrumentation_passed !== true || variant.failure_injection_windows !== 12 ||
      variant.guard_metadata_rejections !== 12 || !contract || contract.payload_loaded !== false ||
      contract.payload_class_lookup_attempted !== false || contract.byte_buffers_published !== false ||
      contract.loaded_payload_published !== true ||
      contract.verified_payload_session_published !== false ||
      contract.native_close_count_exactly_once !== true ||
      contract.partial_java_references_cleared !== true ||
      contract.partial_guard_references_cleared !== true ||
      contract.completed_mappings_zeroized_unmapped !== true ||
      contract.partial_mapping_zeroized_unmapped !== true ||
      contract.primary_code_preserved !== true || contract.cleanup_failure_suppressed !== true) {
    fail(`Guard publication contract failed for ${variant.name}`);
  }
}
const report = {
  task_id: "M3-02",
  validation_mode: "full-flow",
  platform,
  catalog_sha256: sha256(readFileSync(catalogPath)),
  signer_report_sha256: sha256(readFileSync(signerPath)),
  loader_report_sha256: sha256(readFileSync(loaderPath)),
  guard_report_sha256: sha256(readFileSync(guardPath)),
  cases: catalog.map((entry) => ({
    ...entry,
    evidence: entry.target === "runtime-prehandle"
      ? "tokenized-startup-rejection"
      : entry.target === "runtime-posthandle" ? "M202DeviceRunner" : "M203DeviceRunner",
    result: "PASS",
  })),
  cleanup_passed: true,
  result: "PASS",
};
const output = path.resolve(outputPath);
if (!output.startsWith(`${path.resolve("build")}${path.sep}`) &&
    !output.startsWith(`${path.resolve("artifacts")}${path.sep}`)) {
  fail("output must be under ignored build/ or artifacts/");
}
mkdirSync(path.dirname(output), { recursive: true });
writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
