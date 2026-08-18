#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const args = process.argv.slice(2);
const selfTest = args.includes("--self-test");
const baseIndex = args.indexOf("--base-ref");
const allowed = new Set(["--self-test", "--base-ref"]);
for (let index = 0; index < args.length; index += 1) {
  if (!allowed.has(args[index]) && index !== baseIndex + 1) fail(`unknown argument: ${args[index]}`);
}
if (baseIndex >= 0 && (!args[baseIndex + 1] || args[baseIndex + 1].startsWith("--"))) {
  fail("--base-ref requires a Git revision");
}

const contract = Object.freeze({
  outer: Object.freeze(Array.from({ length: 16 }, (_, index) => `p${index}`)),
  inner: Object.freeze(["p0", "h0", "h1", "h2", "h3", "h4", "h5", "h6", "h7", "h8", "p1"]),
  runtimeOwner: "h0..h8",
  fixture: "kotlin-multidex",
  api: 36,
  imageRevision: 2,
  emulator: "37.1.11",
  campaignA: "baseline_then_protected",
  campaignB: "protected_then_baseline",
  warmups: 5,
  retainedSamples: 15,
  nearestRankP50: 8,
  minimumContributionMs: 30,
  maximumVariation: 0.10,
  minimumDeltaShare: 0.50,
  clock: "CLOCK_BOOTTIME",
  oldRunId: "32099991400",
  allowOldRunRetry: false,
  allowSyntheticBaselineFactory: false,
  allowProductTimingSurface: false,
  governanceRunsDynamicTests: false,
  requiredIdentity: Object.freeze([
    "workflowPath", "headSha", "runId", "jobId", "runAttempt", "bootIdHashPrefix",
    "traceSha256", "rawSamplesSha256", "baselineApkSha256", "protectedApkSha256",
  ]),
});

validateModel(contract);
validateDocuments();
if (baseIndex >= 0) validateDiff(args[baseIndex + 1]);
if (selfTest) runMutations();

console.log(`OK: M3-09 startup attribution contract${selfTest ? " and 27 mutations" : ""}`);

function validateModel(value) {
  requireExact(value.outer, Array.from({ length: 16 }, (_, index) => `p${index}`), "outer checkpoints");
  requireExact(value.inner, ["p0", "h0", "h1", "h2", "h3", "h4", "h5", "h6", "h7", "h8", "p1"], "inner checkpoints");
  if (value.runtimeOwner !== "h0..h8") fail("Runtime owner must be h0..h8");
  if (value.fixture !== "kotlin-multidex" || value.api !== 36 || value.imageRevision !== 2
      || value.emulator !== "37.1.11") fail("reference environment or fixture drift");
  if (value.campaignA !== "baseline_then_protected" || value.campaignB !== "protected_then_baseline") {
    fail("campaign order drift");
  }
  if (value.warmups !== 5 || value.retainedSamples !== 15 || value.nearestRankP50 !== 8) {
    fail("sample or percentile contract drift");
  }
  if (value.minimumContributionMs !== 30 || value.maximumVariation !== 0.10
      || value.minimumDeltaShare !== 0.50) fail("eligibility threshold drift");
  if (value.clock !== "CLOCK_BOOTTIME") fail("cross-clock attribution is forbidden");
  if (value.oldRunId !== "32099991400" || value.allowOldRunRetry !== false) fail("M2-10 retry forbidden");
  if (value.allowSyntheticBaselineFactory !== false || value.allowProductTimingSurface !== false
      || value.governanceRunsDynamicTests !== false) fail("scope boundary drift");
  requireExact(value.requiredIdentity, [
    "workflowPath", "headSha", "runId", "jobId", "runAttempt", "bootIdHashPrefix",
    "traceSha256", "rawSamplesSha256", "baselineApkSha256", "protectedApkSha256",
  ], "diagnostic identity");
}

function validateDocuments() {
  const adr = read("docs/adr/0016-end-to-end-startup-attribution-boundary.md");
  const task = read("docs/tasks/M3-09-startup-attribution-boundary-contract.md");
  const strategy = read("docs/TEST_STRATEGY.md");
  const m305 = read("docs/tasks/M3-05-size-startup-memory-benchmarks.md");
  const index = read("docs/tasks/INDEX.md");
  const roadmap = read("docs/ROADMAP.md");
  const plan = read("docs/PROJECT_PLAN.md");

  requirePhrases(adr, [
    "p0..p15", "p0,h0,h1,h2,h3,h4,h5,h6,h7,h8,p1", "h0..h8",
    "platform_pre_shell", "platform_post_loader", "kotlin-multidex", "CLOCK_BOOTTIME",
    "five warmups", "fifteen retained cold starts", "30 ms", "10%", "half",
    "UNATTRIBUTED", "32099991400", "cannot be replaced", "M3-09 is governance-only",
  ], "ADR 0016");
  requirePhrases(task, [
    "p0..p15", "p0,h0,h1..h7,h8,p1", "5", "15", "30 ms", "10%", "50%",
    "Issue #68", "no KVM, emulator, ARM, benchmark, M2-10 retry",
  ], "M3-09 task");
  requirePhrases(strategy, ["ADR 0016", "p0..p15", "h0..h8", "UNATTRIBUTED", "32099991400"], "TEST_STRATEGY");
  requirePhrases(m305, ["M3-09", "ADR 0016", "PR #63 保持阻塞"], "M3-05 task");
  requirePhrases(index, ["| M3-09 | [#68]", "M3-08 → M3-09 → M3-05"], "task index");
  requirePhrases(roadmap, ["| M3-09 |", "M3-08, M3-09"], "roadmap");
  requirePhrases(plan, ["M3-09：端到端启动性能归因边界合同"], "project plan");
}

function validateDiff(baseRef) {
  const result = spawnSync("git", ["diff", "--name-only", `${baseRef}...HEAD`], {
    cwd: root, encoding: "utf8", timeout: 30_000,
  });
  if (result.error || result.status !== 0) fail(`git diff failed: ${result.stderr || result.error?.message}`);
  const files = result.stdout.split(/\r?\n/).filter(Boolean).map(normalize);
  for (const file of files) {
    if (!isAllowedGovernanceFile(file)) fail(`M3-09 contains implementation change: ${file}`);
  }
}

function isAllowedGovernanceFile(file) {
  return file === "HandOff.md"
    || file === "README.md"
    || file === ".github/workflows/governance.yml"
    || file === "tools/governance/validate-project-package.mjs"
    || file === "tools/governance/verify-m3-09-startup-attribution-contract.mjs"
    || file.startsWith("docs/");
}

function runMutations() {
  const mutations = [
    value => { value.outer.pop(); },
    value => { [value.outer[2], value.outer[3]] = [value.outer[3], value.outer[2]]; },
    value => { value.outer[4] = value.outer[3]; },
    value => { value.inner.pop(); },
    value => { [value.inner[2], value.inner[3]] = [value.inner[3], value.inner[2]]; },
    value => { value.inner[5] = value.inner[4]; },
    value => { value.runtimeOwner = "p0..p1"; },
    value => { value.fixture = "java-single-dex"; },
    value => { value.api = 29; },
    value => { value.imageRevision = 1; },
    value => { value.emulator = "latest"; },
    value => { value.campaignA = "protected_then_baseline"; },
    value => { value.campaignB = "baseline_then_protected"; },
    value => { value.warmups = 4; },
    value => { value.warmups = 6; },
    value => { value.retainedSamples = 14; },
    value => { value.retainedSamples = 16; },
    value => { value.nearestRankP50 = 7; },
    value => { value.minimumContributionMs = 29; },
    value => { value.maximumVariation = 0.11; },
    value => { value.minimumDeltaShare = 0.49; },
    value => { value.clock = "wall_clock"; },
    value => { value.oldRunId = "replacement"; },
    value => { value.allowOldRunRetry = true; },
    value => { value.allowSyntheticBaselineFactory = true; },
    value => { value.allowProductTimingSurface = true; },
    value => { value.requiredIdentity.splice(4, 1); },
  ];
  for (const mutate of mutations) {
    const candidate = structuredClone(contract);
    mutate(candidate);
    let rejected = false;
    try { validateModel(candidate); } catch { rejected = true; }
    if (!rejected) fail("mutation was accepted");
  }
  if (isAllowedGovernanceFile("runtime/bootstrap/src/main/java/Probe.java")) {
    fail("production-diff mutation was accepted");
  }
}

function read(relative) {
  const text = fs.readFileSync(path.join(root, relative), "utf8");
  if (text.includes("\uFFFD")) fail(`${relative} contains a replacement character`);
  return text;
}

function requirePhrases(text, phrases, label) {
  for (const phrase of phrases) if (!text.includes(phrase)) fail(`${label} missing phrase: ${phrase}`);
}

function requireExact(actual, expected, label) {
  if (!Array.isArray(actual) || actual.length !== expected.length
      || actual.some((value, index) => value !== expected[index])) fail(`${label} drift`);
}

function normalize(file) { return file.replaceAll("\\", "/"); }
function fail(message) { throw new Error(message); }
