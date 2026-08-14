#!/usr/bin/env node

import {createHash} from "node:crypto";
import {mkdirSync, readFileSync, writeFileSync} from "node:fs";
import path from "node:path";
import process from "node:process";

const fields = [
  "id", "target", "mutation", "expectedStage", "expectedCode", "payloadLoaded",
  "payloadClassLookupAttempted", "nativeHandleAcquired", "loadedPayloadPublished",
  "verifiedPayloadSessionPublished", "byteBuffersPublished", "nativeCloseCount",
  "partialJavaReferencesCleared", "partialGuardReferencesCleared",
  "completedMappingsZeroizedUnmapped", "partialMappingZeroizedUnmapped",
  "primaryCodePreserved", "cleanupFailureSuppressed",
];

function fail(message) {
  throw new Error(`M3-02 device summary failed: ${message}`);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function readJson(value, label) {
  try { return JSON.parse(readFileSync(value, "utf8")); } catch (error) { fail(`invalid ${label}: ${error}`); }
}

function runtimeCatalog(value) {
  const cases = [];
  let current = null;
  for (const raw of readFileSync(value, "utf8").split(/\r?\n/u)) {
    const line = raw.trimEnd();
    if (line.trim() === "" || line.trimStart().startsWith("#") || line === "cases:") continue;
    if (line.startsWith("  - ")) { current = {}; cases.push(current); }
    if (!current) fail("catalog field before case");
    const content = line.startsWith("  - ") ? line.slice(4) : line.trimStart();
    const split = content.indexOf(":");
    if (split <= 0) fail("invalid catalog line");
    current[content.slice(0, split)] = content.slice(split + 1).trim().replace(/^"|"$/gu, "");
  }
  return cases.filter((entry) => ["runtime-prehandle", "runtime-posthandle", "guard"].includes(entry.target));
}

function exact(entry, observed, source) {
  for (const field of fields) {
    if (String(observed[field]) !== entry[field]) {
      fail(`${entry.id} ${source} field ${field}: ${observed[field]} != ${entry[field]}`);
    }
  }
  return {...observed, evidence: source, result: "PASS"};
}

function variantCases(report, target, expectedCount) {
  if (!Array.isArray(report.variants) || report.variants.length !== 2) fail(`${target} variants missing`);
  const maps = report.variants.map((variant) => {
    if (!Array.isArray(variant.m302_cases) || variant.m302_cases.length !== expectedCount) {
      fail(`${target}/${variant.name} named case count`);
    }
    const values = new Map();
    for (const entry of variant.m302_cases) {
      if (values.has(entry.id)) fail(`${target}/${variant.name} duplicate ${entry.id}`);
      values.set(entry.id, entry);
    }
    return values;
  });
  if (JSON.stringify([...maps[0]]) !== JSON.stringify([...maps[1]])) fail(`${target} variants disagree`);
  return maps[0];
}

const [catalogPath, signerPath, loaderPath, guardPath, platform, outputPath] = process.argv.slice(2);
if (!catalogPath || !signerPath || !loaderPath || !guardPath || !platform || !outputPath) {
  fail("usage: <catalog> <signer-report> <loader-report> <guard-report> <platform> <output>");
}
const catalog = runtimeCatalog(catalogPath);
const byId = new Map(catalog.map((entry) => [entry.id, entry]));
if (byId.size !== catalog.length) fail("duplicate runtime catalog ID");
const signer = readJson(signerPath, "signer report");
const loader = readJson(loaderPath, "loader report");
const guard = readJson(guardPath, "Guard report");
if (signer.result !== "PASS" || signer.cleanup_passed !== true || !Array.isArray(signer.m302_cases)) {
  fail("startup matrix did not pass");
}
if (loader.task_id !== "M2-02" || loader.result !== "PASS" || loader.cleanup_passed !== true) fail("loader report");
if (guard.task_id !== "M2-03" || guard.result !== "PASS" || guard.cleanup_passed !== true) fail("Guard report");

const observed = new Map();
const startupCatalog = catalog.filter((entry) => entry.target === "runtime-prehandle");
if (signer.m302_cases.length !== startupCatalog.length) fail("startup named case count");
const startupCases = new Map();
for (const entry of signer.m302_cases) {
  if (startupCases.has(entry.id)) fail(`duplicate startup case ${entry.id}`);
  startupCases.set(entry.id, entry);
}
for (const expected of startupCatalog) {
  const actual = startupCases.get(expected.id);
  if (!actual) fail(`missing startup case ${expected.id}`);
  observed.set(expected.id, exact(expected, actual, "tokenized-startup+transaction-cleanup"));
}

const loaderCases = variantCases(loader, "M202DeviceRunner", 21);
const guardCases = variantCases(guard, "M203DeviceRunner", 24);
for (const entry of catalog.filter((value) => value.target === "runtime-posthandle")) {
  const actual = loaderCases.get(entry.id);
  if (!actual) fail(`missing M202 case ${entry.id}`);
  observed.set(entry.id, exact(entry, actual, "M202DeviceRunner-both-variants"));
}
for (const entry of catalog.filter((value) => value.target === "guard")) {
  const actual = guardCases.get(entry.id);
  if (!actual) fail(`missing M203 case ${entry.id}`);
  observed.set(entry.id, exact(entry, actual, "M203DeviceRunner-both-variants"));
}
if (observed.size !== catalog.length) {
  fail(`runtime evidence mismatch observed=${observed.size} expected=${catalog.length}`);
}
for (const variant of loader.variants) {
  const contract = variant.failure_publication_contract;
  if (variant.instrumentation_passed !== true || variant.failure_injection_windows !== 20 ||
      !contract || contract.completed_mappings_zeroized_unmapped !== true ||
      contract.partial_mapping_zeroized_unmapped !== true || contract.native_close_count_exactly_once !== true) {
    fail(`loader transaction cleanup failed for ${variant.name}`);
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
  cases: catalog.map((entry) => observed.get(entry.id)),
  cleanup_passed: true,
  result: "PASS",
};
const output = path.resolve(outputPath);
if (!output.startsWith(`${path.resolve("build")}${path.sep}`) &&
    !output.startsWith(`${path.resolve("artifacts")}${path.sep}`)) fail("output path");
mkdirSync(path.dirname(output), {recursive: true});
writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
