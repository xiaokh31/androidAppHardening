#!/usr/bin/env node

import childProcess from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const args = process.argv.slice(2);
const selfTest = args.includes("--self-test");
const baseIndex = args.indexOf("--base-ref");
const baseRef = baseIndex >= 0 ? args[baseIndex + 1] : null;
const knownArgs = new Set(["--self-test", "--base-ref"]);
for (let index = 0; index < args.length; index += 1) {
  const arg = args[index];
  if (arg === "--base-ref") {
    index += 1;
    if (!args[index]) fail("--base-ref requires a Git revision");
  } else if (!knownArgs.has(arg)) {
    fail(`unknown argument: ${arg}`);
  }
}

const lockPath = "docs/evidence/M3-15/terminal-disposition-lock.json";
const documents = {
  adr: read("docs/adr/0019-terminal-diagnostic-disposition.md"),
  task: read("docs/tasks/M3-15-terminal-diagnostic-disposition.md"),
  m305: read("docs/tasks/M3-05-size-startup-memory-benchmarks.md"),
  m401: read("docs/tasks/M4-01-security-and-supply-chain-review.md"),
  index: read("docs/tasks/INDEX.md"),
  plan: read("docs/PROJECT_PLAN.md"),
  roadmap: read("docs/ROADMAP.md"),
  requirements: read("docs/PRODUCT_REQUIREMENTS.md"),
  strategy: read("docs/TEST_STRATEGY.md"),
  readme: read("README.md"),
  handoff: read("HandOff.md"),
};
const lock = JSON.parse(read(lockPath));
const errors = validate(lock, documents);

for (const forbidden of [
  ".github/workflows/m3-13-startup-attribution.yml",
  ".github/workflows/m3-13-startup-attribution-evidence.yml",
]) {
  if (fs.existsSync(path.join(root, forbidden))) {
    errors.push(`${forbidden} must remain absent from the terminal disposition branch`);
  }
}

if (baseRef) validateDiff(baseRef, errors);
if (errors.length > 0) {
  for (const error of errors) console.error(`ERROR: ${error}`);
  process.exit(1);
}

if (selfTest) runSelfTest();
console.log(`OK: M3-15 terminal disposition contract${selfTest ? " and 31 named mutations" : ""}`);

function validate(candidate, docs) {
  const found = [];
  const expected = {
    schemaVersion: 1,
    taskId: "M3-15",
    issueNumber: 84,
    decision: "STOP_CURRENT_V0_1_RELEASE_LINE",
    baseCommit: "960eb9f406eb1a7b7c9b324598fb59936aa1c5b5",
    source: {
      issueNumber: 82,
      draftPrNumber: 83,
      reviewedHeadSha: "a112e4469699125a32d80e3c652cda7d4b6b7cf1",
      terminalReviewSha: "75e89f4e192820b8cd7f28b1c7264ea0155f16f0",
      publicationHeadSha: "9fe48737d97853d1566cc2e642009d8ff1b8ab52",
      terminalRequestHeadSha: "b0771d4853e0a7de7fb9db802cac719e34c67229",
      executionIdentitySha256: "96837a115f89e3866d56c315928314c9532b4b635859b6f5860c8d1c442e5357",
      productTupleSha256: "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd",
    },
    diagnostic: {
      runId: 32611656930,
      jobId: 97125597267,
      runAttempt: 1,
      event: "push",
      status: "completed",
      conclusion: "failure",
      failedStepNumber: 13,
      skippedUploadStepNumber: 14,
      artifactCount: 0,
    },
    terminalEvidence: {
      runId: 32612414400,
      jobId: 97127412040,
      runAttempt: 1,
      event: "push",
      status: "completed",
      conclusion: "failure",
      failedStepNumber: 6,
      skippedUploadStepNumber: 7,
      artifactCount: 0,
    },
    officialPageSha256: {
      diagnosticRun: "95d174a5c369dd89cb31d345620aca06111a879b78b3e4dd29bbd2dbacd54a11",
      diagnosticJobs: "3e2e36ed673982b61deccfc1ace4f37dd90d1edf2debe9b1204e8dc9dc113219",
      diagnosticArtifacts: "d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42",
      terminalRun: "5862c875de76e180374c5a7279dad28360a06de7530a4e9846e376188fab6343",
      terminalJobs: "d7f8bda3c7ca1cef6a504ec7fefab08897b10d4695b81cc1c1cbbef63e18d4b3",
      terminalArtifacts: "d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42",
    },
    permissions: {
      retryPermitted: false,
      replacementPermitted: false,
      furtherRenewalPermitted: false,
      platformSubstitutionPermitted: false,
      m305ResumePermitted: false,
      m4StartPermitted: false,
      v01ReleasePermitted: false,
    },
    postMergeActions: {
      pr83: "CLOSE_UNMERGED",
      issue82: "CLOSE_TERMINAL_BLOCKED",
      pr63: "CLOSE_UNMERGED",
      issue22: "CLOSE_TERMINAL_BLOCKED",
    },
    futureWork: {
      currentTupleReusable: false,
      requiresNewVersionedProductTuple: true,
      requiresNewAdr: true,
      requiresNewTaskGraph: true,
    },
  };
  if (stable(candidate) !== stable(expected)) found.push(`${lockPath}: exact contract mismatch`);

  const requiredPhrases = {
    adr: [
      "STOP_CURRENT_V0_1_RELEASE_LINE",
      "Draft PR #83 and Issue #82 are closed as terminally blocked without merging the branch",
      "Draft PR #63 and Issue #22 are closed without merging",
      "M4-01, M4-02 and M4-03 are not startable",
      "A restart requires explicit user authorization, a new versioned product baseline and product tuple, a new ADR and task graph",
    ],
    task: [
      "Issue #84",
      "No release-ready, complete-v0.1 or M4-startable claim is permitted",
      "No Android, device, KVM, benchmark, diagnostic or terminal evidence workflow runs during the task",
    ],
    m305: ["STOP_CURRENT_V0_1_RELEASE_LINE", "PR #63"],
    m401: ["ADR 0019", "not startable"],
    index: ["M3-15-terminal-diagnostic-disposition.md", "STOP_CURRENT_V0_1_RELEASE_LINE"],
    plan: ["M3-15", "STOP_CURRENT_V0_1_RELEASE_LINE"],
    roadmap: ["M3-15", "当前 v0.1 release line"],
    requirements: ["STOP_CURRENT_V0_1_RELEASE_LINE", "不得生成 v0.1 Release Candidate"],
    strategy: ["32611656930", "32612414400", "artifact count 为零"],
    readme: ["M3-15", "STOP_CURRENT_V0_1_RELEASE_LINE"],
    handoff: ["active_task: M3-15", "Issue #84", "PR #83", "PR #63"],
  };
  for (const [key, phrases] of Object.entries(requiredPhrases)) {
    for (const phrase of phrases) {
      if (!docs[key].includes(phrase)) found.push(`${key}: missing phrase ${phrase}`);
    }
  }
  return found;
}

function runSelfTest() {
  const mutations = [
    ["schemaVersion", 2], ["taskId", "M3-14"], ["issueNumber", 85],
    ["decision", "CONTINUE"], ["baseCommit", "0".repeat(40)],
    ["source.issueNumber", 83], ["source.draftPrNumber", 84],
    ["source.reviewedHeadSha", "0".repeat(40)], ["source.terminalReviewSha", "0".repeat(40)],
    ["source.publicationHeadSha", "0".repeat(40)], ["source.terminalRequestHeadSha", "0".repeat(40)],
    ["source.executionIdentitySha256", "0".repeat(64)], ["source.productTupleSha256", "0".repeat(64)],
    ["diagnostic.runId", 1], ["diagnostic.jobId", 1], ["diagnostic.runAttempt", 2],
    ["diagnostic.failedStepNumber", 12], ["diagnostic.artifactCount", 1],
    ["terminalEvidence.runId", 1], ["terminalEvidence.jobId", 1], ["terminalEvidence.runAttempt", 2],
    ["terminalEvidence.failedStepNumber", 5], ["terminalEvidence.artifactCount", 1],
    ["officialPageSha256.diagnosticRun", "0".repeat(64)],
    ["officialPageSha256.terminalRun", "0".repeat(64)],
    ["permissions.retryPermitted", true], ["permissions.m305ResumePermitted", true],
    ["permissions.m4StartPermitted", true], ["permissions.v01ReleasePermitted", true],
    ["postMergeActions.pr83", "MERGE"], ["futureWork.currentTupleReusable", true],
  ];
  for (const [field, value] of mutations) {
    const changed = structuredClone(lock);
    setPath(changed, field, value);
    if (validate(changed, documents).length === 0) fail(`self-test mutation accepted: ${field}`);
  }
}

function validateDiff(revision, targetErrors) {
  let output;
  try {
    output = childProcess.execFileSync("git", ["diff", "--name-only", `${revision}...HEAD`], {
      cwd: root,
      encoding: "utf8",
    });
  } catch (error) {
    targetErrors.push(`cannot inspect base diff: ${error.message}`);
    return;
  }
  const allowedExact = new Set([
    ".github/workflows/governance.yml",
    "HandOff.md",
    "README.md",
    "docs/PRODUCT_REQUIREMENTS.md",
    "docs/PROJECT_PLAN.md",
    "docs/ROADMAP.md",
    "docs/TEST_STRATEGY.md",
    "docs/adr/0019-terminal-diagnostic-disposition.md",
    "docs/evidence/M3-15/terminal-disposition-lock.json",
    "docs/tasks/INDEX.md",
    "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
    "docs/tasks/M3-15-terminal-diagnostic-disposition.md",
    "docs/tasks/M4-01-security-and-supply-chain-review.md",
    "tools/governance/validate-project-package.mjs",
    "tools/governance/verify-m3-08-startup-stability-contract.mjs",
    "tools/governance/verify-m3-09-startup-attribution-contract.mjs",
    "tools/governance/verify-m3-13-diagnostic-identity-contract.mjs",
    "tools/governance/verify-m3-15-terminal-disposition-contract.mjs",
  ]);
  for (const file of output.split(/\r?\n/).filter(Boolean)) {
    if (!allowedExact.has(file)) targetErrors.push(`base diff contains out-of-scope path: ${file}`);
  }
}

function read(relative) {
  const file = path.join(root, relative);
  if (!fs.existsSync(file)) fail(`missing required file: ${relative}`);
  const text = fs.readFileSync(file, "utf8");
  if (text.includes("\uFFFD")) fail(`${relative}: invalid UTF-8 replacement character`);
  return text;
}

function stable(value) {
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stable(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

function setPath(object, dotted, value) {
  const parts = dotted.split(".");
  const leaf = parts.pop();
  let current = object;
  for (const part of parts) current = current[part];
  current[leaf] = value;
}

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}
