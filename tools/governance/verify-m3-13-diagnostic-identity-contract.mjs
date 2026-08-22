import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const args = process.argv.slice(2);
const selfTest = args.includes("--self-test");
const baseRefIndex = args.indexOf("--base-ref");
const baseRef = baseRefIndex >= 0 ? args[baseRefIndex + 1] : null;

if (baseRefIndex >= 0 && (!baseRef || baseRef.startsWith("--"))) {
  console.error("M3-13 contract validation failed:\n- --base-ref requires a commit");
  process.exit(1);
}

const paths = {
  adr: "docs/adr/0018-successor-diagnostic-execution-identity.md",
  task: "docs/tasks/M3-13-successor-diagnostic-identity-contract.md",
  m310: "docs/tasks/M3-10-startup-attribution-diagnostic.md",
  m305: "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
  lock: "docs/evidence/M3-13/diagnostic-eligibility-lock.json",
  proof: "docs/evidence/M3-13/predecessor-official-proof.json",
  index: "docs/tasks/INDEX.md",
  roadmap: "docs/ROADMAP.md",
  plan: "docs/PROJECT_PLAN.md",
  strategy: "docs/TEST_STRATEGY.md",
  readme: "README.md",
  handoff: "HandOff.md",
};

const DIAGNOSTIC_WORKFLOW = ".github/workflows/m3-13-startup-attribution.yml";
const EVIDENCE_WORKFLOW = ".github/workflows/m3-13-startup-attribution-evidence.yml";
const CONTRACT_HASH = "4104670bbe53aaa193740e4e34128051332657bb8dc8c65b57dd133443387faf";
const OFFICIAL_PROOF_HASH = "b3faa34fcee76adb5223c99ccc854fc3000133244cce5a23c8ff2d9432d0d643";
const PRODUCT_TUPLE = "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd";
const ALLOWED_CHANGED_FILES = new Set([
  "HandOff.md",
  "README.md",
  ".github/workflows/governance.yml",
  "docs/PROJECT_PLAN.md",
  "docs/ROADMAP.md",
  "docs/TEST_STRATEGY.md",
  "docs/adr/0016-end-to-end-startup-attribution-boundary.md",
  "docs/adr/0018-successor-diagnostic-execution-identity.md",
  "docs/evidence/M3-13/diagnostic-eligibility-lock.json",
  "docs/evidence/M3-13/predecessor-official-proof.json",
  "docs/evidence/M3-13/local-validation.md",
  "docs/tasks/INDEX.md",
  "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
  "docs/tasks/M3-10-startup-attribution-diagnostic.md",
  "docs/tasks/M3-13-successor-diagnostic-identity-contract.md",
  "tools/governance/validate-project-package.mjs",
  "tools/governance/verify-m3-08-startup-stability-contract.mjs",
  "tools/governance/verify-m3-09-startup-attribution-contract.mjs",
  "tools/governance/verify-m3-13-diagnostic-identity-contract.mjs",
]);

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function expectedPreimage(lock) {
  return JSON.stringify({
    schemaVersion: 1,
    contractTask: "M3-13",
    predecessorTask: lock.predecessor.task,
    predecessorProductTupleSha256: lock.predecessor.productTupleSha256,
    predecessorOfficialProofSha256: lock.officialProof.canonicalSha256,
    predecessorWorkflowPath: lock.predecessor.workflowPath,
    predecessorTaskKey: lock.predecessor.taskKey,
    predecessorHeadSha: lock.predecessor.headSha,
    predecessorRunId: lock.predecessor.runId,
    predecessorJobId: lock.predecessor.jobId,
    predecessorRunAttempt: lock.predecessor.runAttempt,
    predecessorArtifactCount: lock.predecessor.artifactCount,
    predecessorAvdCreated: lock.predecessor.avdCreated,
    predecessorApkInstallAttempted: lock.predecessor.apkInstallAttempted,
    predecessorRetainedSamples: lock.predecessor.retainedSamples,
    terminalEvidenceRunId: lock.predecessor.terminalEvidenceRunId,
    terminalEvidenceJobId: lock.predecessor.terminalEvidenceJobId,
    successorTaskKey: lock.successor.taskKey,
    successorWorkflowPath: lock.successor.workflowPath,
    successorEvidenceWorkflowPath: lock.successor.evidenceWorkflowPath,
    successorRunLimit: lock.successor.runLimit,
    successorRunAttempt: lock.successor.requiredRunAttempt,
    furtherRenewalPermitted: lock.successor.furtherRenewalPermitted,
  });
}

function requireEqual(actual, expected, label, errors) {
  if (actual !== expected) errors.push(`${label}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}

function requirePhrase(text, phrase, label, errors) {
  if (!text.includes(phrase)) errors.push(`${label}: missing contract phrase: ${phrase}`);
}

function validateState(state) {
  const errors = [];
  const { lock, proof, texts, workflowPresence } = state;

  requireEqual(lock.schemaVersion, 1, "lock.schemaVersion", errors);
  requireEqual(lock.task, "M3-13", "lock.task", errors);
  requireEqual(lock.issue, 80, "lock.issue", errors);
  requireEqual(lock.status, "contract_candidate", "lock.status", errors);
  requireEqual(lock.officialProof.path, paths.proof, "lock.officialProof.path", errors);
  requireEqual(lock.officialProof.canonicalBytes, 5274, "lock.officialProof.canonicalBytes", errors);
  requireEqual(lock.officialProof.canonicalSha256, OFFICIAL_PROOF_HASH, "lock.officialProof.canonicalSha256", errors);
  requireEqual(lock.predecessor.task, "M3-10", "lock.predecessor.task", errors);
  requireEqual(lock.predecessor.productTupleSha256, PRODUCT_TUPLE, "lock.predecessor.productTupleSha256", errors);
  requireEqual(lock.predecessor.workflowPath, ".github/workflows/m3-09-startup-attribution.yml", "lock.predecessor.workflowPath", errors);
  requireEqual(lock.predecessor.taskKey, "M3-09-DIAGNOSTIC-V1", "lock.predecessor.taskKey", errors);
  requireEqual(lock.predecessor.headSha, "790ae4579ce3562dc93f3c533ffb786a39517600", "lock.predecessor.headSha", errors);
  requireEqual(lock.predecessor.runId, 32554806537, "lock.predecessor.runId", errors);
  requireEqual(lock.predecessor.jobId, 96987186584, "lock.predecessor.jobId", errors);
  requireEqual(lock.predecessor.runAttempt, 1, "lock.predecessor.runAttempt", errors);
  requireEqual(lock.predecessor.artifactCount, 0, "lock.predecessor.artifactCount", errors);
  requireEqual(lock.predecessor.avdCreated, false, "lock.predecessor.avdCreated", errors);
  requireEqual(lock.predecessor.apkInstallAttempted, false, "lock.predecessor.apkInstallAttempted", errors);
  requireEqual(lock.predecessor.retainedSamples, 0, "lock.predecessor.retainedSamples", errors);
  requireEqual(lock.predecessor.terminalEvidenceRunId, 32554917303, "lock.predecessor.terminalEvidenceRunId", errors);
  requireEqual(lock.predecessor.terminalEvidenceJobId, 96987454333, "lock.predecessor.terminalEvidenceJobId", errors);

  requireEqual(lock.successor.taskKey, "M3-13-SUCCESSOR-DIAGNOSTIC-V1", "lock.successor.taskKey", errors);
  requireEqual(lock.successor.workflowPath, DIAGNOSTIC_WORKFLOW, "lock.successor.workflowPath", errors);
  requireEqual(lock.successor.evidenceWorkflowPath, EVIDENCE_WORKFLOW, "lock.successor.evidenceWorkflowPath", errors);
  requireEqual(lock.successor.runLimit, 1, "lock.successor.runLimit", errors);
  requireEqual(lock.successor.requiredRunAttempt, 1, "lock.successor.requiredRunAttempt", errors);
  requireEqual(lock.successor.fullHistoryCheckoutRequired, true, "lock.successor.fullHistoryCheckoutRequired", errors);
  requireEqual(lock.successor.qualificationBeforeAndroid, true, "lock.successor.qualificationBeforeAndroid", errors);
  requireEqual(lock.successor.independentReviewRequired, true, "lock.successor.independentReviewRequired", errors);
  requireEqual(lock.successor.furtherRenewalPermitted, false, "lock.successor.furtherRenewalPermitted", errors);

  const preimage = expectedPreimage(lock);
  requireEqual(lock.identityPreimage, preimage, "lock.identityPreimage", errors);
  requireEqual(Buffer.byteLength(lock.identityPreimage, "utf8"), 1033, "lock.identityPreimage utf8 bytes", errors);
  requireEqual(lock.identityPreimageBytes, 1033, "lock.identityPreimageBytes", errors);
  requireEqual(sha256(lock.identityPreimage), CONTRACT_HASH, "lock identity SHA-256", errors);
  requireEqual(lock.identityPreimageSha256, CONTRACT_HASH, "lock.identityPreimageSha256", errors);

  const proofCanonical = JSON.stringify(proof);
  requireEqual(Buffer.byteLength(proofCanonical, "utf8"), 5274, "official proof canonical utf8 bytes", errors);
  requireEqual(sha256(proofCanonical), OFFICIAL_PROOF_HASH, "official proof canonical SHA-256", errors);
  requireEqual(proof.schemaVersion, 1, "proof.schemaVersion", errors);
  requireEqual(proof.task, "M3-13", "proof.task", errors);
  requireEqual(proof.source, "official GitHub Actions API via gh api", "proof.source", errors);
  requireEqual(proof.diagnosticRun.id, 32554806537, "proof.diagnosticRun.id", errors);
  requireEqual(proof.diagnosticRun.headSha, "790ae4579ce3562dc93f3c533ffb786a39517600", "proof.diagnosticRun.headSha", errors);
  requireEqual(proof.diagnosticRun.runAttempt, 1, "proof.diagnosticRun.runAttempt", errors);
  requireEqual(proof.diagnosticRun.status, "completed", "proof.diagnosticRun.status", errors);
  requireEqual(proof.diagnosticRun.conclusion, "failure", "proof.diagnosticRun.conclusion", errors);
  requireEqual(proof.diagnosticJob.totalCount, 1, "proof.diagnosticJob.totalCount", errors);
  requireEqual(proof.diagnosticJob.id, 96987186584, "proof.diagnosticJob.id", errors);
  requireEqual(proof.diagnosticJob.runAttempt, 1, "proof.diagnosticJob.runAttempt", errors);
  requireEqual(proof.diagnosticJob.steps.length, 17, "proof.diagnosticJob.steps.length", errors);
  requireEqual(proof.diagnosticArtifacts.totalCount, 0, "proof.diagnosticArtifacts.totalCount", errors);
  requireEqual(proof.diagnosticArtifacts.artifacts.length, 0, "proof.diagnosticArtifacts.artifacts.length", errors);
  requireEqual(proof.terminalRun.id, 32554917303, "proof.terminalRun.id", errors);
  requireEqual(proof.terminalRun.headSha, "415420223441578aa028a1687cb94ef79dfd1924", "proof.terminalRun.headSha", errors);
  requireEqual(proof.terminalRun.runAttempt, 1, "proof.terminalRun.runAttempt", errors);
  requireEqual(proof.terminalJob.totalCount, 1, "proof.terminalJob.totalCount", errors);
  requireEqual(proof.terminalJob.id, 96987454333, "proof.terminalJob.id", errors);
  requireEqual(proof.terminalJob.steps.length, 10, "proof.terminalJob.steps.length", errors);
  requireEqual(proof.terminalArtifacts.totalCount, 0, "proof.terminalArtifacts.totalCount", errors);
  requireEqual(proof.terminalArtifacts.artifacts.length, 0, "proof.terminalArtifacts.artifacts.length", errors);
  const diagnosticStep = proof.diagnosticJob.steps.find((step) => step.name === "Execute first-and-only API 36 attribution diagnostic");
  requireEqual(diagnosticStep?.number, 11, "proof diagnostic step number", errors);
  requireEqual(diagnosticStep?.conclusion, "skipped", "proof diagnostic step conclusion", errors);
  const failedStep = proof.diagnosticJob.steps.find((step) => step.name === "Download and verify canonical originals and retained profile package");
  requireEqual(failedStep?.number, 8, "proof failed preflight step number", errors);
  requireEqual(failedStep?.conclusion, "failure", "proof failed preflight step conclusion", errors);
  requireEqual(proof.reviewedBytes.diagnosticWorkflow.sha256, "a09145d499d06a769cce38e4229019fa3360bfbff26be6ed18ca1552ab1d5559", "proof workflow SHA-256", errors);
  requireEqual(proof.reviewedBytes.runner.sha256, "640c40d502a410f7609c0b1113a7b096a56730f5039aa18c6dffd1ddb741d228", "proof runner SHA-256", errors);
  requireEqual(proof.reviewedBytes.verifier.sha256, "d4781da1888bbacc0e52b0851d3ab1e61bcf315a55b2a068ae9d0d320985486a", "proof verifier SHA-256", errors);
  requireEqual(proof.reviewedBytes.environmentLock.sha256, "6e8fe036b3eadc7dad0fd1eed90178d96feae569ab8cbbac5f94717e21f34a1f", "proof environment lock SHA-256", errors);
  requireEqual(proof.inferenceBoundary.classification, "ZERO_DEVICE_OBSERVATION", "proof classification", errors);
  requireEqual(proof.inferenceBoundary.androidPackagesPrepared, true, "proof androidPackagesPrepared", errors);
  requireEqual(proof.inferenceBoundary.nativePreparationReached, false, "proof nativePreparationReached", errors);
  requireEqual(proof.inferenceBoundary.releaseBuildReached, false, "proof releaseBuildReached", errors);
  requireEqual(proof.inferenceBoundary.diagnosticExecutionStepReached, false, "proof diagnosticExecutionStepReached", errors);
  requireEqual(proof.inferenceBoundary.avdCreated, false, "proof avdCreated", errors);
  requireEqual(proof.inferenceBoundary.apkInstallAttempted, false, "proof apkInstallAttempted", errors);
  requireEqual(proof.inferenceBoundary.retainedSamples, 0, "proof retainedSamples", errors);
  requireEqual(proof.inferenceBoundary.artifactCount, 0, "proof artifactCount", errors);

  const phrases = {
    adr: [
      "M3-10 is terminally blocked",
      "zero AVD creation, zero installation attempt, zero retained samples and zero artifacts",
      "No second successor and no further renewal are permitted",
      CONTRACT_HASH,
      "fetch-depth: 0",
      "P0=0/P1=0/P2=0",
    ],
    task: [
      "Issue #80",
      "M3-13-SUCCESSOR-DIAGNOSTIC-V1",
      "The available unlocked ARM device is irrelevant",
      "Independent review returns `P0=0/P1=0/P2=0` before push/PR publication",
    ],
    m310: ["32554806537", "terminally blocked", "must remain draft"],
    m305: ["M3-13", "successor", "remains blocked"],
    index: ["M3-13-successor-diagnostic-identity-contract.md", "#80", "M3-10 → M3-13"],
    roadmap: ["M3-13", "successor diagnostic identity"],
    plan: ["M3-13", "successor diagnostic identity"],
    strategy: ["ADR 0018", "zero device observation", "no further renewal"],
    readme: ["M3-13", "ADR 0018", "32554806537"],
    handoff: ["active_task: M3-13", "Issue #80", "No device, KVM, emulator, ARM, API 29 or benchmark"],
  };
  for (const [key, required] of Object.entries(phrases)) {
    for (const phrase of required) requirePhrase(texts[key], phrase, paths[key] ?? key, errors);
  }

  if (workflowPresence.diagnostic) errors.push(`${DIAGNOSTIC_WORKFLOW}: contract task must not add executable diagnostic workflow`);
  if (workflowPresence.evidence) errors.push(`${EVIDENCE_WORKFLOW}: contract task must not add executable evidence workflow`);
  return errors;
}

function loadState() {
  const lockText = read(paths.lock).replaceAll("\r\n", "\n");
  const lock = JSON.parse(lockText);
  const canonical = `${JSON.stringify(lock, null, 2)}\n`;
  if (lockText !== canonical) throw new Error(`${paths.lock}: JSON must use canonical two-space formatting and one trailing LF`);
  const proof = JSON.parse(read(paths.proof));
  const texts = {};
  for (const [key, relativePath] of Object.entries(paths)) {
    if (key !== "lock" && key !== "proof") texts[key] = read(relativePath);
  }
  return {
    lock,
    proof,
    texts,
    workflowPresence: {
      diagnostic: fs.existsSync(path.join(root, DIAGNOSTIC_WORKFLOW)),
      evidence: fs.existsSync(path.join(root, EVIDENCE_WORKFLOW)),
    },
  };
}

function validateBaseDiff(reference, errors) {
  if (!/^[0-9a-fA-F]{7,40}$/.test(reference)) {
    errors.push(`--base-ref must be a 7-40 character hex commit: ${reference}`);
    return;
  }
  const changed = execFileSync("git", ["diff", "--name-only", `${reference}...HEAD`], {
    cwd: root,
    encoding: "utf8",
  }).trim().split(/\r?\n/).filter(Boolean).map((item) => item.replaceAll("\\", "/"));
  validateChangedFiles(changed, errors);
}

function validateChangedFiles(changed, errors) {
  for (const file of changed) {
    if (!ALLOWED_CHANGED_FILES.has(file)) errors.push(`base diff contains out-of-scope file: ${file}`);
  }
}

function clone(value) {
  return structuredClone(value);
}

function runSelfTest(baseState) {
  const cases = [];
  const addLockCase = (name, mutate) => cases.push({ name, mutate: (state) => mutate(state.lock) });
  addLockCase("predecessor-task", (lock) => { lock.predecessor.task = "M3-10-RETRY"; });
  addLockCase("official-proof-path", (lock) => { lock.officialProof.path += ".other"; });
  addLockCase("official-proof-size", (lock) => { lock.officialProof.canonicalBytes += 1; });
  addLockCase("official-proof-hash", (lock) => { lock.officialProof.canonicalSha256 = "0".repeat(64); });
  addLockCase("product-tuple", (lock) => { lock.predecessor.productTupleSha256 = "0".repeat(64); });
  addLockCase("predecessor-workflow", (lock) => { lock.predecessor.workflowPath += ".retry"; });
  addLockCase("predecessor-key", (lock) => { lock.predecessor.taskKey += "-RETRY"; });
  addLockCase("predecessor-head", (lock) => { lock.predecessor.headSha = "0".repeat(40); });
  addLockCase("predecessor-run", (lock) => { lock.predecessor.runId += 1; });
  addLockCase("predecessor-job", (lock) => { lock.predecessor.jobId += 1; });
  addLockCase("predecessor-attempt", (lock) => { lock.predecessor.runAttempt = 2; });
  addLockCase("artifact-count", (lock) => { lock.predecessor.artifactCount = 1; });
  addLockCase("avd-created", (lock) => { lock.predecessor.avdCreated = true; });
  addLockCase("install-attempted", (lock) => { lock.predecessor.apkInstallAttempted = true; });
  addLockCase("retained-sample", (lock) => { lock.predecessor.retainedSamples = 1; });
  addLockCase("terminal-run", (lock) => { lock.predecessor.terminalEvidenceRunId += 1; });
  addLockCase("terminal-job", (lock) => { lock.predecessor.terminalEvidenceJobId += 1; });
  addLockCase("successor-key", (lock) => { lock.successor.taskKey += "-2"; });
  addLockCase("successor-workflow", (lock) => { lock.successor.workflowPath += ".retry"; });
  addLockCase("successor-evidence-workflow", (lock) => { lock.successor.evidenceWorkflowPath += ".retry"; });
  addLockCase("successor-run-limit", (lock) => { lock.successor.runLimit = 2; });
  addLockCase("successor-attempt", (lock) => { lock.successor.requiredRunAttempt = 2; });
  addLockCase("shallow-history", (lock) => { lock.successor.fullHistoryCheckoutRequired = false; });
  addLockCase("late-qualification", (lock) => { lock.successor.qualificationBeforeAndroid = false; });
  addLockCase("missing-review", (lock) => { lock.successor.independentReviewRequired = false; });
  addLockCase("further-renewal", (lock) => { lock.successor.furtherRenewalPermitted = true; });
  addLockCase("preimage-byte-count", (lock) => { lock.identityPreimageBytes = 932; });
  addLockCase("preimage-content", (lock) => { lock.identityPreimage += " "; });
  addLockCase("preimage-hash", (lock) => { lock.identityPreimageSha256 = "0".repeat(64); });
  cases.push({ name: "proof-diagnostic-run", mutate: (state) => { state.proof.diagnosticRun.id += 1; } });
  cases.push({ name: "proof-diagnostic-step-count", mutate: (state) => { state.proof.diagnosticJob.steps.pop(); } });
  cases.push({ name: "proof-diagnostic-step-executed", mutate: (state) => { state.proof.diagnosticJob.steps.find((step) => step.number === 11).conclusion = "success"; } });
  cases.push({ name: "proof-artifact", mutate: (state) => { state.proof.diagnosticArtifacts.totalCount = 1; } });
  cases.push({ name: "proof-terminal-run", mutate: (state) => { state.proof.terminalRun.id += 1; } });
  cases.push({ name: "proof-terminal-step-count", mutate: (state) => { state.proof.terminalJob.steps.pop(); } });
  cases.push({ name: "proof-workflow-hash", mutate: (state) => { state.proof.reviewedBytes.diagnosticWorkflow.sha256 = "0".repeat(64); } });
  cases.push({ name: "proof-android-package-fact", mutate: (state) => { state.proof.inferenceBoundary.androidPackagesPrepared = false; } });
  cases.push({ name: "proof-avd-created", mutate: (state) => { state.proof.inferenceBoundary.avdCreated = true; } });
  cases.push({ name: "missing-no-renewal-text", mutate: (state) => { state.texts.adr = state.texts.adr.replace("No second successor and no further renewal are permitted", "A later successor may be permitted"); } });
  cases.push({ name: "missing-arm-boundary", mutate: (state) => { state.texts.task = state.texts.task.replace("The available unlocked ARM device is irrelevant", "The ARM device may be used"); } });
  cases.push({ name: "missing-m310-terminal", mutate: (state) => { state.texts.m310 = state.texts.m310.replace("terminally blocked", "retryable"); } });
  cases.push({ name: "diagnostic-workflow-present", mutate: (state) => { state.workflowPresence.diagnostic = true; } });
  cases.push({ name: "evidence-workflow-present", mutate: (state) => { state.workflowPresence.evidence = true; } });

  for (const testCase of cases) {
    const mutated = clone(baseState);
    testCase.mutate(mutated);
    const errors = validateState(mutated);
    if (errors.length === 0) throw new Error(`mutation unexpectedly accepted: ${testCase.name}`);
  }
  const pathCases = [
    "runtime/policy/src/main/java/ah/runtime/Policy.java",
    "host/container/src/main/java/ah/host/Container.java",
    "fixtures/android/src/main/AndroidManifest.xml",
    "benchmarks/android/src/main/java/ah/benchmark/Benchmark.java",
    "build/m3-13/profile.apk",
    "build/m3-13/classes.dex",
    "build/m3-13/private-key.pem",
    "distribution/m3-13.zip",
    DIAGNOSTIC_WORKFLOW,
    EVIDENCE_WORKFLOW,
  ];
  for (const file of pathCases) {
    const pathErrors = [];
    validateChangedFiles([...ALLOWED_CHANGED_FILES, file], pathErrors);
    if (!pathErrors.includes(`base diff contains out-of-scope file: ${file}`)) {
      throw new Error(`base-diff mutation unexpectedly accepted: ${file}`);
    }
  }
  const positiveErrors = [];
  validateChangedFiles([...ALLOWED_CHANGED_FILES], positiveErrors);
  if (positiveErrors.length !== 0) throw new Error(`allowed base-diff paths unexpectedly rejected: ${positiveErrors.join(", ")}`);
  return cases.length + pathCases.length;
}

let state;
try {
  state = loadState();
} catch (error) {
  console.error(`M3-13 contract validation failed:\n- ${error.message}`);
  process.exit(1);
}

const errors = validateState(state);
if (baseRef) {
  try {
    validateBaseDiff(baseRef, errors);
  } catch (error) {
    errors.push(`base diff validation failed: ${error.message}`);
  }
}

let mutationCount = 0;
if (selfTest && errors.length === 0) {
  try {
    mutationCount = runSelfTest(state);
  } catch (error) {
    errors.push(error.message);
  }
}

if (errors.length > 0) {
  console.error(`M3-13 contract validation failed:\n${errors.map((error) => `- ${error}`).join("\n")}`);
  process.exit(1);
}

console.log(`OK: M3-13 successor diagnostic identity contract${selfTest ? `; ${mutationCount} named mutations rejected` : ""}`);
