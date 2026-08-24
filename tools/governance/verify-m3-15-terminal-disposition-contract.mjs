#!/usr/bin/env node

import childProcess from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { ACTIVE_GOVERNANCE_POLICY_SURFACES } from "./active-governance-policy-surfaces.mjs";

const root = process.cwd();
const V01_TERMINAL_BASELINE_COMMIT = "7c838d7051e8eedb1607e57b6e81e7a6f3db4523";
const V01_PRODUCT_TUPLE = "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd";
const TERMINAL_LOCK_SHA256 = "5a46e8aeaa2ad45b7f58dc60f5557581f1972bbdfc7ea7099b6a5a3bc67bb3a2";
const CURRENT_IMMUTABLE_SHA256 = {
  "docs/evidence/M3-15/terminal-disposition-lock.json": TERMINAL_LOCK_SHA256,
  "docs/adr/0018-successor-diagnostic-execution-identity.md": "8892046f8a2e271515f8cc774ec12527ddc4566a808869b9b76a8374a4779891",
  "docs/adr/0019-terminal-diagnostic-disposition.md": "7c23ca6b1ba0d17e8ac94d607c577feb6ab0cbb49474c66d55e5af7828dd010d",
  "docs/tasks/M3-05-size-startup-memory-benchmarks.md": "b4a8aabad6ba0aa0443bf31ad58b93bfaf0510beafa10c1095e735ebba3e189c",
  "docs/tasks/M3-10-startup-attribution-diagnostic.md": "62b34717bc8d9105c783959e1ba30f7ca7756091f933b5c61d4d4cd898f78328",
  "docs/tasks/M3-15-terminal-diagnostic-disposition.md": "26aaef485cff0a7b7c8f1e68800c8b381d8d452e2ff087761e1cda12fab8a3b5",
  "docs/tasks/INDEX.md": "82ac6f22b2a3d19327253d9b17c485a8ab085dded91fc6b4d85dc095524c7bdf",
  "docs/tasks/M4-01-security-and-supply-chain-review.md": "ade14358d819447539b59eec3d17c42f4e0aacaf732828ba542f712d1c9793c6",
  "docs/tasks/M4-02-cross-platform-release-packaging.md": "9873e3d2e340aae091c792078cd972a8f2e9aeac58c9d21246c86c25ea004c0a",
  "docs/tasks/M4-03-release-evidence-and-documentation.md": "8a20a0e3982f1ebd361d74e05d8a6db3ab6398b85d3eb294cfbc2aaaf87db953",
};
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
  agents: read("AGENTS.md"),
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
  workflow: read(".github/workflows/governance.yml"),
  ...Object.fromEntries(ACTIVE_GOVERNANCE_POLICY_SURFACES.map((relative) => [`policy:${relative}`, read(relative)])),
};
const lock = JSON.parse(read(lockPath));
const errors = validate(lock, documents);
validateHistoricalSnapshot(lock, errors);
validateCurrentImmutableFiles(errors);
validateVersionedHandoff(documents.handoff, errors);

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

const mutationCount = selfTest ? runSelfTest() : 0;
console.log(`OK: M3-15 terminal disposition contract${selfTest ? ` and ${mutationCount} named mutations` : ""}`);

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
    governedFileSha256: {
      ".github/workflows/governance.yml": "6e12ac72f285f35aaa74f6cf70ec685817d2f38d3b1a94f40a606c2dbceecf22",
      "HandOff.md": "2a81467e03af240e7ba378df77c1e11818f5e17c74a10f8bf4fdcd695c7173ed",
      "README.md": "b4f2ecf489a58fd4c21eb9b71331c2513fe7e6af259a1d1e127658ce7b768f1f",
      "docs/PRODUCT_REQUIREMENTS.md": "4873fb18733a66618291056ea683e98101974e221e3d062b6dcbe342db54ce21",
      "docs/PROJECT_PLAN.md": "41c2620fe56bcc7e38b3405b9047cfc4a62966bd49bc7bb97bdf78748afb32c2",
      "docs/ROADMAP.md": "59ae73e72343a8154660da5024086f0417112e3b18d91fb7d4c76726554ea9c2",
      "docs/TEST_STRATEGY.md": "5c894246753ef0cd1ebb56a8f72664c035c80dcc841770bf97f12f9e0044f179",
      "docs/adr/0019-terminal-diagnostic-disposition.md": "7c23ca6b1ba0d17e8ac94d607c577feb6ab0cbb49474c66d55e5af7828dd010d",
      "docs/tasks/INDEX.md": "82ac6f22b2a3d19327253d9b17c485a8ab085dded91fc6b4d85dc095524c7bdf",
      "docs/tasks/M3-05-size-startup-memory-benchmarks.md": "b4a8aabad6ba0aa0443bf31ad58b93bfaf0510beafa10c1095e735ebba3e189c",
      "docs/tasks/M3-15-terminal-diagnostic-disposition.md": "26aaef485cff0a7b7c8f1e68800c8b381d8d452e2ff087761e1cda12fab8a3b5",
      "docs/tasks/M4-01-security-and-supply-chain-review.md": "ade14358d819447539b59eec3d17c42f4e0aacaf732828ba542f712d1c9793c6",
      "tools/governance/validate-project-package.mjs": "521c62b135413250f3cd7602f3aed4a00da55b0d1a14b8e8ccaf65d10dbd1077",
      "tools/governance/verify-m3-08-startup-stability-contract.mjs": "23303430315e6bd97ec0201e5c979df5207d8af9a31084d996ee0230c855dbea",
      "tools/governance/verify-m3-09-startup-attribution-contract.mjs": "3e11ec3adaf6e496aaaf8ddfbe6f129b6238ad3c49419a5f8e76dab09d43574d",
      "tools/governance/verify-m3-13-diagnostic-identity-contract.mjs": "e5a4b6a3a53633615fd08e1ea3c9e8a3b3054afc7174ba2233e4008a9c0093ec",
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
    agents: ["STOP_CURRENT_V0_1_RELEASE_LINE", "M3-05 终态阻塞"],
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
    handoff: [
      "STOP_CURRENT_V0_1_RELEASE_LINE",
      V01_PRODUCT_TUPLE,
      "M3-05",
      "M4",
      "ADR 0019",
    ],
    workflow: [
      "fetch-depth: 0",
      "node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs",
      "node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --self-test",
      "node tools/governance/verify-m3-15-terminal-disposition-contract.mjs",
      "node tools/governance/verify-m3-15-terminal-disposition-contract.mjs --self-test",
    ],
  };
  for (const [key, phrases] of Object.entries(requiredPhrases)) {
    for (const phrase of phrases) {
      if (!docs[key].includes(phrase)) found.push(`${key}: missing phrase ${phrase}`);
    }
  }
  const contradictoryClaims = [
    ["successor retry authorization", /(?:M3-14|successor).{0,48}(?:retry|rerun|renewal|replacement).{0,24}(?:is\s+)?(?:permitted|allowed|authorized)/iu],
    ["task-first modal retry authorization", /(?:M3-05|M3-10|M3-14|successor).{0,64}(?:(?:\bmay\b|\bcan\b)(?:\s+be)?|is\s+(?:authorized|allowed|permitted)(?:\s+to)?|authorized(?:\s+to)?)\s+(?:retry|retried|rerun|resume|unblock|replace|renew|platform[- ]?(?:substitution|replacement))/iu],
    ["successor platform substitution authorization", /(?:M3-10|M3-14|successor).{0,64}(?:platform[- ]?(?:substitution|replacement)).{0,24}(?:is\s+)?(?:permitted|allowed|authorized|may|can)/iu],
    ["generic retry authorization", /\bretry\s+(?:is\s+)?(?:permitted|allowed|authorized)\b/iu],
    ["Chinese retry authorization", /(?:允许|授权|可以).{0,12}(?:重试|重跑|续期).{0,12}M3-14/u],
    ["Chinese retry authorization reversed", /(?:允许|授权|可以).{0,12}M3-14.{0,12}(?:重试|重跑|续期)/u],
    ["Chinese platform substitution authorization", /(?:允许|授权|可以|可).{0,20}(?:换平台|平台替代|平台替换).{0,24}(?:M3-05|M3-10|M3-14|successor)/iu],
    ["Chinese platform substitution authorization reversed", /(?:M3-05|M3-10|M3-14|successor).{0,24}(?:可换平台|允许平台替代|允许平台替换)/iu],
    ["Chinese task-first platform substitution authorization", /(?:允许|授权|可以).{0,20}(?:M3-05|M3-10|M3-14|successor).{0,32}(?:换平台|平台替代|平台替换)/iu],
    ["M3-05 resume authorization", /M3-05.{0,48}(?:resume|unblock).{0,24}(?:is\s+)?(?:permitted|allowed|authorized|may|can)/iu],
    ["Chinese M3-05 resume authorization", /M3-05.{0,32}(?:可恢复|可解除|允许恢复|解除阻塞)/u],
    ["Chinese task-first modal authorization", /(?:M3-05|M3-10|M3-14|successor).{0,32}(?:可以|允许|授权|(?<!不)可)(?:进行|对其)?(?:重试|重跑|替换|续期|恢复|解除阻塞|换平台|平台替代|平台替换)/iu],
    ["M4 start authorization", /(?<!no )M4.{0,32}(?:is\s+startable|may\s+start|can\s+start|start\s+is\s+(?:permitted|allowed))/iu],
    ["Chinese M4 start authorization", /(?:允许|可以|可).{0,20}(?:启动|开始)\s*M4/u],
    ["v0.1 release authorization", /v0\.1.{0,32}release.{0,20}(?:is\s+)?(?:permitted|allowed|authorized)/iu],
    ["PR 83 merge action", /PR\s*#83.{0,32}(?:action\s+is\s+MERGE|may\s+be\s+merged|can\s+be\s+merged)/iu],
    ["PR 63 merge action", /PR\s*#63.{0,32}(?:action\s+is\s+MERGE|may\s+be\s+merged|can\s+be\s+merged)/iu],
  ];
  for (const [key, text] of Object.entries(docs)) {
    for (const [label, pattern] of contradictoryClaims) {
      if (pattern.test(text)) found.push(`${key}: contradictory claim: ${label}`);
    }
  }
  return found;
}

function runSelfTest() {
  let count = 0;
  for (const [parts, original] of leafEntries(lock)) {
    const field = parts.join(".");
    const changed = structuredClone(lock);
    setPath(changed, parts, alternate(original));
    if (validate(changed, documents).length === 0) fail(`self-test mutation accepted: ${field}`);
    count += 1;
  }
  const documentMutations = [
    ["adr", "\nM3-14 retry is permitted.\n"],
    ["task", "\n允许重试 M3-14。\n"],
    ["m305", "\nM3-05 resume is permitted.\n"],
    ["m305", "\nM3-05 may resume.\n"],
    ["task", "\nM3-14 may be retried.\n"],
    ["m305", "\nM3-05 可恢复。\n"],
    ["m305", "\nM3-05 可以恢复。\n"],
    ["task", "\nM3-05 授权恢复。\n"],
    ["task", "\nM3-14 platform substitution is allowed.\n"],
    ["roadmap", "\n允许对 M3-05 进行平台替代。\n"],
    ["handoff", "\nretry allowed\n"],
    ["agents", "\nM3-05 retry is allowed.\n"],
    ["policy:docs/HANDOFF_SPEC.md", "\nM3-05 may resume.\n"],
    ["m401", "\nM4 is startable.\n"],
    ["roadmap", "\n允许启动 M4。\n"],
    ["requirements", "\nv0.1 release is permitted.\n"],
    ["readme", "\nPR #83 action is MERGE.\n"],
    ["handoff", "\nPR #63 may be merged.\n"],
  ];
  for (const [documentKey, addition] of documentMutations) {
    const changedDocuments = { ...documents, [documentKey]: `${documents[documentKey]}${addition}` };
    if (validate(lock, changedDocuments).length === 0) fail(`self-test contradictory document accepted: ${documentKey}`);
    count += 1;
  }

  const nonAncestorErrors = [];
  validateHistoricalSnapshot(lock, nonAncestorErrors, {
    commit: "0".repeat(40),
    isAncestor: () => false,
    readBlob: () => Buffer.alloc(0),
  });
  expectRejectedErrors(nonAncestorErrors, "historical-commit-not-ancestor");
  count += 1;

  const missingBlobErrors = [];
  validateHistoricalSnapshot(lock, missingBlobErrors, {
    isAncestor: () => true,
    readBlob: (_commit, relative) => {
      if (relative === lockPath) throw new Error("synthetic missing blob");
      return gitBlob(V01_TERMINAL_BASELINE_COMMIT, relative);
    },
  });
  expectRejectedErrors(missingBlobErrors, "historical-blob-missing");
  count += 1;

  const changedBlobErrors = [];
  const firstGovernedPath = Object.keys(lock.governedFileSha256)[0];
  validateHistoricalSnapshot(lock, changedBlobErrors, {
    isAncestor: () => true,
    readBlob: (commit, relative) => relative === firstGovernedPath
      ? Buffer.from("MUTATED HISTORICAL BLOB", "utf8")
      : gitBlob(commit, relative),
  });
  expectRejectedErrors(changedBlobErrors, "historical-blob-hash");
  count += 1;

  for (const immutablePath of Object.keys(CURRENT_IMMUTABLE_SHA256)) {
    const immutableErrors = [];
    validateCurrentImmutableFiles(immutableErrors, (relative) => relative === immutablePath
      ? Buffer.from("MUTATED CURRENT IMMUTABLE", "utf8")
      : fs.readFileSync(path.join(root, relative)));
    expectRejectedErrors(immutableErrors, `current-immutable-hash:${immutablePath}`);
    count += 1;
  }

  const oldTupleErrors = [];
  validateVersionedHandoff(syntheticV02Handoff(V01_PRODUCT_TUPLE), oldTupleErrors);
  expectRejectedErrors(oldTupleErrors, "v02-old-tuple-reuse");
  count += 1;

  const restoredRouteDocuments = {
    ...documents,
    handoff: `${documents.handoff}\nThe current v0.1 M4 release is startable and authorized.\n`,
  };
  if (validate(lock, restoredRouteDocuments).length === 0) {
    fail("self-test contradictory document accepted: restored-v01-release-route");
  }
  count += 1;

  const removedTerminalGateDocuments = {
    ...documents,
    workflow: documents.workflow.replace(
      "node tools/governance/verify-m3-15-terminal-disposition-contract.mjs --self-test",
      "node -e \"process.exit(0)\"",
    ),
  };
  if (validate(lock, removedTerminalGateDocuments).length === 0) {
    fail("self-test current governance workflow accepted without the M3-15 self-test gate");
  }
  count += 1;
  return count;
}

function validateHistoricalSnapshot(contract, targetErrors, options = {}) {
  const commit = options.commit ?? V01_TERMINAL_BASELINE_COMMIT;
  const isAncestor = options.isAncestor ?? gitAncestor;
  const readBlob = options.readBlob ?? gitBlob;
  if (!isAncestor(commit)) {
    targetErrors.push(`v0.1 terminal baseline commit must exist and be an ancestor of HEAD: ${commit}`);
    return;
  }

  const expectedHistorical = {
    [lockPath]: TERMINAL_LOCK_SHA256,
    ...contract.governedFileSha256,
  };
  for (const [relative, expectedHash] of Object.entries(expectedHistorical)) {
    let bytes;
    try {
      bytes = readBlob(commit, relative);
    } catch (error) {
      targetErrors.push(`${relative}: cannot read v0.1 terminal baseline blob: ${error.message}`);
      continue;
    }
    const actualHash = sha256(bytes);
    if (actualHash !== expectedHash) {
      targetErrors.push(`${relative}: v0.1 terminal baseline blob hash mismatch`);
    }
  }
}

function validateCurrentImmutableFiles(targetErrors, readCurrent = (relative) => fs.readFileSync(path.join(root, relative))) {
  for (const [relative, expectedHash] of Object.entries(CURRENT_IMMUTABLE_SHA256)) {
    let bytes;
    try {
      bytes = readCurrent(relative);
    } catch (error) {
      targetErrors.push(`${relative}: cannot read current immutable terminal file: ${error.message}`);
      continue;
    }
    if (sha256(bytes) !== expectedHash) {
      targetErrors.push(`${relative}: current immutable terminal file hash mismatch`);
    }
  }
}

function validateVersionedHandoff(text, targetErrors) {
  const schema = frontmatterValue(text, "schema_version");
  if (schema === "1") {
    if (frontmatterValue(text, "state") !== "blocked"
      || frontmatterValue(text, "active_task") !== "NONE") {
      targetErrors.push("legacy schema 1 HandOff must retain blocked state with active_task NONE");
    }
    return;
  }
  if (schema !== "2") {
    targetErrors.push("HandOff schema must be terminal schema 1 or versioned schema 2");
    return;
  }

  const releaseLine = frontmatterValue(text, "release_line");
  const tuple = frontmatterValue(text, "product_tuple_sha256");
  const milestone = frontmatterValue(text, "current_milestone");
  const activeTask = frontmatterValue(text, "active_task");
  if (releaseLine !== "v0.2") targetErrors.push("schema 2 HandOff release_line must be v0.2");
  if (!/^[0-9a-f]{64}$/.test(tuple ?? "")) {
    targetErrors.push("schema 2 HandOff product_tuple_sha256 must be 64 lowercase hex characters");
  } else if (tuple === V01_PRODUCT_TUPLE) {
    targetErrors.push("schema 2 HandOff must not reuse the terminal v0.1 product tuple");
  }
  if (!/^V2-M[0-4]$/.test(milestone ?? "")) {
    targetErrors.push("schema 2 HandOff current_milestone must be V2-M0 through V2-M4");
  }
  if (activeTask !== "NONE" && !/^V2-M[0-4]-\d{2}$/.test(activeTask ?? "")) {
    targetErrors.push("schema 2 HandOff active_task must be NONE or a V2 task ID");
  }
}

function syntheticV02Handoff(tuple) {
  return [
    "---",
    "schema_version: 2",
    "project: androidAppHardening",
    "release_line: v0.2",
    `product_tuple_sha256: ${tuple}`,
    "state: ready",
    "current_milestone: V2-M0",
    "active_task: V2-M0-01",
    "---",
    "STOP_CURRENT_V0_1_RELEASE_LINE",
  ].join("\n");
}

function frontmatterValue(text, key) {
  const frontmatter = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
  if (!frontmatter) return null;
  const match = frontmatter[1].match(new RegExp(`^${key}:\\s*(.*?)\\s*$`, "m"));
  return match ? match[1].replace(/^["']|["']$/g, "") : null;
}

function gitAncestor(commit) {
  const exists = childProcess.spawnSync("git", ["cat-file", "-e", `${commit}^{commit}`], {
    cwd: root,
    stdio: "ignore",
    windowsHide: true,
  });
  if (exists.status !== 0) return false;
  const ancestor = childProcess.spawnSync("git", ["merge-base", "--is-ancestor", commit, "HEAD"], {
    cwd: root,
    stdio: "ignore",
    windowsHide: true,
  });
  return ancestor.status === 0;
}

function gitBlob(commit, relative) {
  return childProcess.execFileSync("git", ["show", `${commit}:${relative}`], {
    cwd: root,
    encoding: null,
    maxBuffer: 32 * 1024 * 1024,
    windowsHide: true,
  });
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function expectRejectedErrors(found, label) {
  if (found.length === 0) fail(`self-test mutation accepted: ${label}`);
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

function setPath(object, pathParts, value) {
  const parts = [...pathParts];
  const leaf = parts.pop();
  let current = object;
  for (const part of parts) current = current[part];
  current[leaf] = value;
}

function leafEntries(value, prefix = []) {
  const result = [];
  for (const [key, child] of Object.entries(value)) {
    const field = [...prefix, key];
    if (child && typeof child === "object" && !Array.isArray(child)) result.push(...leafEntries(child, field));
    else result.push([field, child]);
  }
  return result;
}

function alternate(value) {
  if (typeof value === "boolean") return !value;
  if (typeof value === "number") return value + 1;
  if (typeof value === "string") return `${value}-MUTATED`;
  throw new Error(`unsupported mutation type: ${typeof value}`);
}

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}
