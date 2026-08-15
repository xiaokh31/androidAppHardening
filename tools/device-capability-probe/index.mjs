#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const ROOT = path.resolve(import.meta.dirname, "../..");
const ABIS = ["armeabi-v7a", "arm64-v8a", "x86", "x86_64"];
const STATUSES = new Set(["VERIFIED", "FAILED", "UNVERIFIED"]);
const MANDATORY = new Set(["29/armeabi-v7a", "29/arm64-v8a", "29/x86_64", "36/x86_64"]);
const UNAVAILABLE = "NO_AUTHORIZED_PROVENANCE_LOCKED_ENVIRONMENT";
const command = process.argv[2] ?? "";
const options = parseOptions(process.argv.slice(3));

try {
  if (command === "self-test") selfTest();
  else if (command === "cell") createCell();
  else if (command === "generate") generate();
  else if (command === "validate") validateFiles();
  else fail("usage: index.mjs <self-test|cell|generate|validate> [options]");
} catch (error) {
  process.stderr.write(`M3-04 matrix failed: ${error.message}\n`);
  process.exitCode = 1;
}

function versions() {
  const text = readFileSync(path.join(ROOT, "gradle/libs.versions.toml"), "utf8");
  const value = (name) => Number(text.match(new RegExp(`^${name}\\s*=\\s*"(\\d+)"$`, "mu"))?.[1]);
  const minApi = value("android-min-sdk");
  const maxApi = value("android-compile-sdk");
  if (!Number.isInteger(minApi) || !Number.isInteger(maxApi) || minApi !== 29 || maxApi !== 36) {
    fail("locked Android API range is not 29..36");
  }
  return { minApi, maxApi };
}

function createCell() {
  const apiLevel = integerOption("api");
  const processAbi = required("abi");
  const platform = stableToken(required("platform"), "platform");
  const sourceCommit = hex(required("source-commit"), 40, "source commit");
  const fixturePath = fileOption("fixture-report");
  const runtimePath = fileOption("runtime-report");
  const signerPath = options.has("signer-report") ? fileOption("signer-report") : null;
  const fixture = json(fixturePath);
  const runtime = json(runtimePath);
  const signer = signerPath === null ? null : json(signerPath);
  const output = outputOption("output");

  if (fixture.status !== "pass" || fixture.fixture_count !== 9 || !Array.isArray(fixture.fixtures)) {
    fail("M3-01 fixture report is not a complete pass");
  }
  const processFacts = fixture.device?.process;
  if (Number(fixture.device?.sdk) !== apiLevel || processFacts?.sdk !== apiLevel ||
      processFacts?.process_abi !== processAbi || !Array.isArray(processFacts?.supported_abis)) {
    fail("Android-reported fixture API/process ABI facts do not match the requested cell");
  }
  const byId = new Map(fixture.fixtures.map((row) => [row.id, row]));
  const requiredFixtures = ["java-single-dex", "kotlin-multidex", "jni-four-abi"];
  if (processAbi.startsWith("x86")) requiredFixtures.push("custom-factory");
  for (const id of requiredFixtures) assertFixture(byId.get(id), id, true, apiLevel);
  const armOnly = byId.get("jni-arm-only");
  if (processAbi.startsWith("arm" ) || processAbi.startsWith("armeabi")) assertFixture(armOnly, "jni-arm-only", true, apiLevel);
  else if (!armOnly || armOnly.status !== "pass" || armOnly.installed !== false || armOnly.package_cleanup !== true) {
    fail("ARM-only fixture was not explicitly classified on x86");
  }
  if (fixture.negative_matrix?.includes("different_output_signer") !== true || fixture.test_signing_cleanup !== true) {
    fail("M3-01 signer rejection or ephemeral signing cleanup evidence is missing");
  }

  if (runtime.result !== "PASS" || runtime.cleanup_passed !== true || !Array.isArray(runtime.variants) ||
      runtime.variants.length !== 2 || runtime.environment?.api !== String(apiLevel) ||
      runtime.variants.some((row) => row.runtime_abi !== processAbi || row.instrumentation_passed !== true ||
        row.plaintext_dex_files !== 0 || row.multidex_verified !== true || row.jni_verified !== true)) {
    fail("M2-04 runtime evidence does not prove the requested cell");
  }
  const runtimeCases = runtime.variants.flatMap((row) => row.m302_cases ?? []);
  const tagCase = runtimeCases.find((row) => row.id === "m302-runtime-tag-first") ??
    signer?.m302_cases?.find((row) => row.id === "m302-runtime-tag-first");
  const directTagEvidence = preloadFailure(tagCase);
  const fixtureTagEvidence = fixture.runtime_tag_negative?.status === "pass" &&
    fixture.runtime_tag_negative?.business_events_reached === false &&
    fixture.runtime_tag_negative?.package_cleanup === true &&
    /^[0-9a-f]{64}$/u.test(fixture.runtime_tag_negative?.tampered_apk_sha256 ?? "");
  if (!directTagEvidence && !fixtureTagEvidence) fail("authenticated tag failure-before-load evidence is missing");

  const differentSigner = signer?.startup_rejection_matrix?.find((row) => row.name === "different-signer");
  const strongSignerEvidence = signer !== null && signer.result === "PASS" && signer.cleanup_passed === true &&
    differentSigner?.result === "PASS" && differentSigner.lookup_count === 0 && differentSigner.session_published === false;
  const fixtureSignerEvidence = fixture.runtime_signer_negative?.status === "pass" &&
    fixture.runtime_signer_negative?.business_events_reached === false &&
    fixture.runtime_signer_negative?.package_cleanup === true;
  if (!strongSignerEvidence && !fixtureSignerEvidence) fail("runtime signer rejection evidence is missing");
  const serialHashes = new Set([runtime.serial_sha256, signer?.serial_sha256].filter(Boolean));
  if (serialHashes.size !== 1 || !/^[0-9a-f]{64}$/u.test(runtime.serial_sha256 ?? "")) {
    fail("device identity hashes do not bind to one device");
  }

  const fixtureResults = fixture.fixtures.map((row) => ({
    id: row.id,
    status: row.status === "pass" ? "PASS" : "FAIL",
    installed: row.installed,
    expectedEvents: row.expected_events,
    observedEvents: row.observed_events,
    catalogExpectedEvents: row.catalog_expected_events,
    configurationRelaunch: row.configuration_relaunch,
    packageCleanup: row.package_cleanup,
    artifactSha256: {
      input: row.input_sha256,
      unsignedOutput: row.unsigned_output_sha256,
      signedOutput: row.signed_output_sha256,
      report: row.product_report_sha256,
    },
  }));
  const cell = {
    apiLevel,
    processAbi,
    status: "VERIFIED",
    reasonCode: null,
    deviceFacts: {
      sdkInt: apiLevel,
      processAbi,
      supportedAbis: processFacts.supported_abis,
      is64Bit: processFacts.is_64_bit,
      buildType: runtime.environment?.fingerprint?.includes("/userdebug/") ? "userdebug" : "user",
      nonRoot: runtime.environment?.non_root === true,
      serialSha256: runtime.serial_sha256,
      platform,
    },
    fixtureResults,
    retryCount: 0,
    artifactSha256: {
      fixtureReport: sha256File(fixturePath),
      runtimeReport: sha256File(runtimePath),
      ...(signerPath === null ? {} : { signerReport: sha256File(signerPath) }),
    },
    evidence: {
      sourceCommit,
      positiveFixturesPassed: true,
      signerMismatchBeforeLoad: true,
      signerEvidence: strongSignerEvidence ? "DIRECT_LOOKUP_AND_SESSION_MARKERS" : "BOOTSTRAP_REJECTION_BEFORE_BUSINESS_EVENTS",
      authenticatedTagTamperBeforeLoad: true,
      tagEvidence: directTagEvidence ? "DIRECT_LOOKUP_AND_SESSION_MARKERS" : "BOOTSTRAP_REJECTION_BEFORE_BUSINESS_EVENTS",
      payloadLookupCount: 0,
      sessionPublished: false,
      armOnlyClassification: processAbi.startsWith("x86") ? "LIMITED" : "EXECUTED",
      abiRiskContribution: processAbi.startsWith("x86") ? 0 : null,
      cleanupPassed: true,
    },
  };
  validateCell(cell, true);
  writeJson(output, cell);
  process.stdout.write(`OK: M3-04 VERIFIED cell ${apiLevel}/${processAbi}\n`);
}

function generate() {
  const evidenceDir = path.resolve(required("evidence-dir"));
  const jsonOutput = outputOption("json-output");
  const markdownOutput = outputOption("markdown-output");
  const allowIncomplete = options.has("allow-incomplete");
  const supplied = new Map();
  for (const name of readdirSync(evidenceDir).filter((value) => value.endsWith(".json")).sort()) {
    const cell = json(path.join(evidenceDir, name));
    validateCell(cell, true);
    const key = cellKey(cell);
    if (supplied.has(key)) fail(`duplicate cell evidence ${key}`);
    supplied.set(key, cell);
  }
  const { minApi, maxApi } = versions();
  const cells = [];
  for (let apiLevel = minApi; apiLevel <= maxApi; apiLevel += 1) {
    for (const processAbi of ABIS) {
      const key = `${apiLevel}/${processAbi}`;
      cells.push(supplied.get(key) ?? unverified(apiLevel, processAbi));
    }
  }
  const commits = new Set([...supplied.values()].map((cell) => cell.evidence.sourceCommit));
  if (commits.size > 1) fail("VERIFIED cells are not bound to one source commit");
  const matrix = {
    schemaVersion: 1,
    generatedFromCommit: commits.size === 1 ? [...commits][0] : null,
    minApi,
    maxApi,
    abis: ABIS,
    cells,
    summary: summary(cells),
    compatibilityClaim: "Only exact VERIFIED cells are release-validated; UNVERIFIED cells make no compatibility claim.",
  };
  validateMatrix(matrix, !allowIncomplete);
  writeJson(jsonOutput, matrix);
  writeFile(markdownOutput, render(matrix));
  process.stdout.write(`OK: M3-04 matrix cells=${cells.length} verified=${matrix.summary.verified} unverified=${matrix.summary.unverified}\n`);
}

function validateFiles() {
  const matrix = json(fileOption("json"));
  validateMatrix(matrix, !options.has("allow-incomplete"));
  const expected = render(matrix);
  const actual = readFileSync(fileOption("markdown"), "utf8");
  if (actual !== expected) fail("Markdown does not exactly render the JSON matrix");
  process.stdout.write("OK: M3-04 JSON/Markdown semantic equivalence\n");
}

function validateMatrix(matrix, requireMandatory) {
  const { minApi, maxApi } = versions();
  if (matrix.schemaVersion !== 1 || matrix.minApi !== minApi || matrix.maxApi !== maxApi ||
      JSON.stringify(matrix.abis) !== JSON.stringify(ABIS) || !Array.isArray(matrix.cells) || matrix.cells.length !== 32) {
    fail("matrix header or 32-cell inventory is invalid");
  }
  const keys = new Set();
  for (const cell of matrix.cells) {
    validateCell(cell, false);
    const key = cellKey(cell);
    if (keys.has(key)) fail(`duplicate matrix cell ${key}`);
    keys.add(key);
  }
  for (let api = minApi; api <= maxApi; api += 1) for (const abi of ABIS) {
    if (!keys.has(`${api}/${abi}`)) fail(`missing matrix cell ${api}/${abi}`);
  }
  if (matrix.cells.some((cell) => cell.status === "FAILED")) fail("FAILED cell blocks M3-04");
  if (requireMandatory) for (const key of MANDATORY) {
    if (matrix.cells.find((cell) => cellKey(cell) === key)?.status !== "VERIFIED") fail(`mandatory cell is not VERIFIED: ${key}`);
  }
  const expectedSummary = summary(matrix.cells);
  if (JSON.stringify(matrix.summary) !== JSON.stringify(expectedSummary)) fail("matrix summary mismatch");
}

function validateCell(cell, standalone) {
  const { minApi, maxApi } = versions();
  if (!Number.isInteger(cell.apiLevel) || cell.apiLevel < minApi || cell.apiLevel > maxApi || !ABIS.includes(cell.processAbi)) {
    fail("cell API/ABI is outside the locked inventory");
  }
  if (!STATUSES.has(cell.status)) fail(`unknown cell status ${cell.status}`);
  if (!Number.isInteger(cell.retryCount) || cell.retryCount < 0 || cell.retryCount > 1) fail("invalid retry count");
  if (cell.status === "UNVERIFIED") {
    if (cell.reasonCode !== UNAVAILABLE || cell.deviceFacts !== null || !Array.isArray(cell.fixtureResults) ||
        cell.fixtureResults.length !== 0 || Object.keys(cell.artifactSha256 ?? {}).length !== 0 ||
        cell.evidence?.compatibilityClaim !== false) fail("UNVERIFIED cell carries contradictory evidence or claim");
    return;
  }
  if (cell.reasonCode !== null || !cell.deviceFacts || cell.deviceFacts.sdkInt !== cell.apiLevel ||
      cell.deviceFacts.processAbi !== cell.processAbi || !Array.isArray(cell.deviceFacts.supportedAbis) ||
      cell.fixtureResults?.length < 4 || !/^[0-9a-f]{40}$/u.test(cell.evidence?.sourceCommit ?? "")) {
    fail("executed cell evidence is incomplete or inferred");
  }
  if (cell.status === "VERIFIED" && (cell.evidence.positiveFixturesPassed !== true ||
      cell.evidence.signerMismatchBeforeLoad !== true || cell.evidence.authenticatedTagTamperBeforeLoad !== true ||
      cell.evidence.payloadLookupCount !== 0 || cell.evidence.sessionPublished !== false ||
      cell.evidence.cleanupPassed !== true)) fail("VERIFIED cell lacks mandatory positive/negative/cleanup proof");
  if (!standalone && !Object.values(cell.artifactSha256 ?? {}).every((value) => /^[0-9a-f]{64}$/u.test(value))) {
    fail("cell artifact SHA-256 is invalid");
  }
}

function render(matrix) {
  const lines = [
    "# Android API/ABI validation results", "",
    "> Only exact `VERIFIED` cells are release-validated. `UNVERIFIED` means no authorized provenance-locked environment was available and makes no compatibility claim.", "",
    `Source commit: \`${matrix.generatedFromCommit ?? "none"}\``, "",
    "| API | Process ABI | Status | Claim | Evidence |", "|---:|---|---|---|---|",
  ];
  for (const cell of matrix.cells) {
    const claim = cell.status === "VERIFIED" ? "Validated on this exact combination" :
      cell.status === "FAILED" ? "Executed regression failed" : "Not validated; no compatibility claim";
    const evidence = cell.status === "UNVERIFIED" ? cell.reasonCode :
      `${cell.deviceFacts.platform}; reports ${Object.values(cell.artifactSha256).map((value) => value.slice(0, 12)).join(", ")}`;
    lines.push(`| ${cell.apiLevel} | \`${cell.processAbi}\` | **${cell.status}** | ${claim} | ${evidence} |`);
  }
  lines.push("", `Summary: VERIFIED ${matrix.summary.verified}; FAILED ${matrix.summary.failed}; UNVERIFIED ${matrix.summary.unverified}.`);
  return `${lines.join("\n")}\n`;
}

function selfTest() {
  const { minApi, maxApi } = versions();
  const schema = json(path.join(ROOT, "integration-tests/schemas/compatibility-matrix.schema.json"));
  if (schema.properties?.minApi?.const !== minApi || schema.properties?.maxApi?.const !== maxApi ||
      JSON.stringify(schema.properties?.abis?.const) !== JSON.stringify(ABIS) ||
      schema.properties?.cells?.minItems !== 32 || schema.properties?.cells?.maxItems !== 32) {
    fail("versioned JSON Schema differs from the executable inventory contract");
  }
  if (maxApi - minApi + 1 !== 8 || ABIS.length !== 4) fail("inventory dimensions changed");
  const cells = [];
  for (let api = minApi; api <= maxApi; api += 1) for (const abi of ABIS) cells.push(unverified(api, abi));
  const matrix = { schemaVersion: 1, generatedFromCommit: null, minApi, maxApi, abis: ABIS, cells, summary: summary(cells), compatibilityClaim: "test" };
  validateMatrix(matrix, false);
  expectFailure(() => validateMatrix({ ...matrix, cells: cells.slice(1), summary: summary(cells.slice(1)) }, false), "missing cell");
  expectFailure(() => validateMatrix({ ...matrix, cells: [...cells, cells[0]], summary: summary([...cells, cells[0]]) }, false), "duplicate cell");
  expectFailure(() => validateCell({ ...cells[0], status: "SUPPORTED" }, false), "unknown status");
  expectFailure(() => validateCell({ ...cells[0], deviceFacts: {} }, false), "fake device facts");
  const markdown = render(matrix);
  if (!markdown.includes("Not validated; no compatibility claim") || markdown.includes("UNVERIFIED** | Validated")) {
    fail("UNVERIFIED rendering is a positive claim");
  }
  if (markdown.endsWith("\n\n")) fail("Markdown renderer emitted a trailing blank line");
  process.stdout.write("OK: M3-04 inventory/status/render mutation self-tests\n");
}

function unverified(apiLevel, processAbi) {
  return { apiLevel, processAbi, status: "UNVERIFIED", reasonCode: UNAVAILABLE, deviceFacts: null,
    fixtureResults: [], retryCount: 0, artifactSha256: {}, evidence: { compatibilityClaim: false } };
}
function summary(cells) { return { verified: cells.filter((c) => c.status === "VERIFIED").length,
  failed: cells.filter((c) => c.status === "FAILED").length,
  unverified: cells.filter((c) => c.status === "UNVERIFIED").length }; }
function assertFixture(row, id, installed, apiLevel) {
  const catalog = row?.catalog_expected_events;
  const expected = row?.expected_events;
  const observed = row?.observed_events;
  const exact = JSON.stringify(expected) === JSON.stringify(observed);
  const canonical = JSON.stringify(expected) === JSON.stringify(catalog) && row?.configuration_relaunch === false;
  const relaunch = apiLevel === 29 && row?.configuration_relaunch === true &&
    JSON.stringify(expected) === JSON.stringify([...(catalog ?? []), ...activityRelaunchEvents(id)]);
  if (!row || row.status !== "pass" || row.installed !== installed || row.package_cleanup !== true ||
      !Array.isArray(catalog) || !exact || (!canonical && !relaunch)) fail(`fixture ${id} did not pass exactly`);
}
function activityRelaunchEvents(id) {
  if (id === "kotlin-single-dex") return ["activity.create", "kotlin.marker"];
  if (id === "kotlin-multidex") return ["activity.create", "kotlin.marker", "multidex.class"];
  if (id === "jni-four-abi" || id === "jni-arm-only") return ["activity.create", "jni.marker"];
  if (["java-single-dex", "custom-application", "custom-factory", "startup-provider", "multi-process"].includes(id)) {
    return ["activity.create"];
  }
  fail(`unknown fixture for configuration relaunch: ${id}`);
}
function preloadFailure(row) { return row && row.payloadLoaded === "false" && row.payloadClassLookupAttempted === "false" &&
  row.verifiedPayloadSessionPublished === "false"; }
function cellKey(cell) { return `${cell.apiLevel}/${cell.processAbi}`; }
function sha256File(file) { return createHash("sha256").update(readFileSync(file)).digest("hex"); }
function json(file) { return JSON.parse(readFileSync(file, "utf8")); }
function writeJson(file, value) { writeFile(file, `${JSON.stringify(value, null, 2)}\n`); }
function writeFile(file, value) { mkdirSync(path.dirname(file), { recursive: true }); writeFileSync(file, value, "utf8"); }
function outputOption(name) { const value = path.resolve(required(name)); assertOwned(value); return value; }
function fileOption(name) { return path.resolve(required(name)); }
function required(name) { const value = options.get(name); if (!value) fail(`missing --${name}`); return value; }
function integerOption(name) { const value = Number(required(name)); if (!Number.isInteger(value)) fail(`invalid --${name}`); return value; }
function stableToken(value, name) { if (!/^[a-z0-9][a-z0-9._-]{2,80}$/u.test(value)) fail(`invalid ${name}`); return value; }
function hex(value, length, name) { if (!new RegExp(`^[0-9a-f]{${length}}$`, "u").test(value)) fail(`invalid ${name}`); return value; }
function assertOwned(file) { const relative = path.relative(ROOT, file); if (relative.startsWith("..") || path.isAbsolute(relative)) fail("output must remain in the repository"); }
function expectFailure(action, name) { try { action(); } catch { return; } fail(`mutation self-test did not reject ${name}`); }
function parseOptions(args) { const result = new Map(); for (let i = 0; i < args.length; i += 1) {
  const token = args[i]; if (!token.startsWith("--")) fail(`unexpected argument ${token}`); const key = token.slice(2);
  if (i + 1 < args.length && !args[i + 1].startsWith("--")) result.set(key, args[++i]); else result.set(key, "true");
} return result; }
function fail(message) { throw new Error(message); }
