import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const args = process.argv.slice(2);
const selfTest = args.includes("--self-test");
const sensitiveOnly = args.includes("--sensitive-only");
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

const RAW_PAGE_SPECS = {
  diagnosticRun: ["docs/evidence/M3-13/raw/diagnostic-run.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32554806537"],
  diagnosticJobsPage1: ["docs/evidence/M3-13/raw/diagnostic-jobs-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32554806537/jobs?per_page=100&page=1"],
  diagnosticArtifactsPage1: ["docs/evidence/M3-13/raw/diagnostic-artifacts-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32554806537/artifacts?per_page=100&page=1"],
  terminalRun: ["docs/evidence/M3-13/raw/terminal-run.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32554917303"],
  terminalJobsPage1: ["docs/evidence/M3-13/raw/terminal-jobs-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32554917303/jobs?per_page=100&page=1"],
  terminalArtifactsPage1: ["docs/evidence/M3-13/raw/terminal-artifacts-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32554917303/artifacts?per_page=100&page=1"],
};

const DIAGNOSTIC_WORKFLOW = ".github/workflows/m3-13-startup-attribution.yml";
const EVIDENCE_WORKFLOW = ".github/workflows/m3-13-startup-attribution-evidence.yml";
const CONTRACT_HASH = "580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419";
const OFFICIAL_PROOF_HASH = "9e06abb32d9e0a933e4254bea6fd781cd2a2a95d2980835fd79956e4b315f117";
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
  "docs/evidence/M3-13/read-only-review-1.md",
  "docs/evidence/M3-13/review-1-remediation-local.md",
  ...Object.values(RAW_PAGE_SPECS).map(([relativePath]) => relativePath),
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

function readBuffer(relativePath) {
  return fs.readFileSync(path.join(root, relativePath));
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

function requireObjectBytes(actual, expected, label, errors) {
  requireEqual(JSON.stringify(actual), JSON.stringify(expected), label, errors);
}

function validateRawOfficialEvidence(state, errors) {
  const { proof, rawPages, historicalBytes } = state;
  for (const [key, [relativePath, endpoint]] of Object.entries(RAW_PAGE_SPECS)) {
    const binding = proof.rawOfficialPages?.[key];
    const page = rawPages[key];
    requireEqual(binding?.path, relativePath, `proof.rawOfficialPages.${key}.path`, errors);
    requireEqual(binding?.endpoint, endpoint, `proof.rawOfficialPages.${key}.endpoint`, errors);
    requireEqual(binding?.bytes, page.bytes.length, `proof.rawOfficialPages.${key}.bytes`, errors);
    requireEqual(binding?.sha256, sha256(page.bytes), `proof.rawOfficialPages.${key}.sha256`, errors);
  }

  const diagnosticRun = rawPages.diagnosticRun.json;
  requireEqual(diagnosticRun.id, proof.diagnosticRun.id, "raw diagnostic run id", errors);
  requireEqual(diagnosticRun.name, proof.diagnosticRun.name, "raw diagnostic run name", errors);
  requireEqual(diagnosticRun.event, proof.diagnosticRun.event, "raw diagnostic run event", errors);
  requireEqual(diagnosticRun.status, proof.diagnosticRun.status, "raw diagnostic run status", errors);
  requireEqual(diagnosticRun.conclusion, proof.diagnosticRun.conclusion, "raw diagnostic run conclusion", errors);
  requireEqual(diagnosticRun.head_sha, proof.diagnosticRun.headSha, "raw diagnostic run head", errors);
  requireEqual(diagnosticRun.run_attempt, proof.diagnosticRun.runAttempt, "raw diagnostic run attempt", errors);
  requireEqual(diagnosticRun.path, proof.diagnosticRun.path, "raw diagnostic run path", errors);
  requireEqual(diagnosticRun.created_at, proof.diagnosticRun.createdAt, "raw diagnostic run created_at", errors);
  requireEqual(diagnosticRun.updated_at, proof.diagnosticRun.updatedAt, "raw diagnostic run updated_at", errors);

  const diagnosticJobs = rawPages.diagnosticJobsPage1.json;
  requireEqual(diagnosticJobs.total_count, 1, "raw diagnostic jobs total_count", errors);
  requireEqual(diagnosticJobs.jobs?.length, 1, "raw diagnostic jobs page completeness", errors);
  const diagnosticJob = diagnosticJobs.jobs?.[0];
  requireEqual(diagnosticJob?.id, proof.diagnosticJob.id, "raw diagnostic job id", errors);
  requireEqual(diagnosticJob?.name, proof.diagnosticJob.name, "raw diagnostic job name", errors);
  requireEqual(diagnosticJob?.status, proof.diagnosticJob.status, "raw diagnostic job status", errors);
  requireEqual(diagnosticJob?.conclusion, proof.diagnosticJob.conclusion, "raw diagnostic job conclusion", errors);
  requireEqual(diagnosticJob?.run_attempt, proof.diagnosticJob.runAttempt, "raw diagnostic job attempt", errors);
  requireEqual(diagnosticJob?.started_at, proof.diagnosticJob.startedAt, "raw diagnostic job started_at", errors);
  requireEqual(diagnosticJob?.completed_at, proof.diagnosticJob.completedAt, "raw diagnostic job completed_at", errors);
  requireObjectBytes(diagnosticJob?.steps?.map(({ number, name, status, conclusion }) => ({ number, name, status, conclusion })), proof.diagnosticJob.steps, "raw diagnostic job steps", errors);

  const diagnosticArtifacts = rawPages.diagnosticArtifactsPage1.json;
  requireEqual(diagnosticArtifacts.total_count, 0, "raw diagnostic artifacts total_count", errors);
  requireEqual(diagnosticArtifacts.artifacts?.length, 0, "raw diagnostic artifacts page completeness", errors);

  const terminalRun = rawPages.terminalRun.json;
  requireEqual(terminalRun.id, proof.terminalRun.id, "raw terminal run id", errors);
  requireEqual(terminalRun.name, proof.terminalRun.name, "raw terminal run name", errors);
  requireEqual(terminalRun.event, proof.terminalRun.event, "raw terminal run event", errors);
  requireEqual(terminalRun.status, proof.terminalRun.status, "raw terminal run status", errors);
  requireEqual(terminalRun.conclusion, proof.terminalRun.conclusion, "raw terminal run conclusion", errors);
  requireEqual(terminalRun.head_sha, proof.terminalRun.headSha, "raw terminal run head", errors);
  requireEqual(terminalRun.run_attempt, proof.terminalRun.runAttempt, "raw terminal run attempt", errors);
  requireEqual(terminalRun.path, proof.terminalRun.path, "raw terminal run path", errors);
  requireEqual(terminalRun.created_at, proof.terminalRun.createdAt, "raw terminal run created_at", errors);
  requireEqual(terminalRun.updated_at, proof.terminalRun.updatedAt, "raw terminal run updated_at", errors);

  const terminalJobs = rawPages.terminalJobsPage1.json;
  requireEqual(terminalJobs.total_count, 1, "raw terminal jobs total_count", errors);
  requireEqual(terminalJobs.jobs?.length, 1, "raw terminal jobs page completeness", errors);
  const terminalJob = terminalJobs.jobs?.[0];
  requireEqual(terminalJob?.id, proof.terminalJob.id, "raw terminal job id", errors);
  requireEqual(terminalJob?.name, proof.terminalJob.name, "raw terminal job name", errors);
  requireEqual(terminalJob?.status, proof.terminalJob.status, "raw terminal job status", errors);
  requireEqual(terminalJob?.conclusion, proof.terminalJob.conclusion, "raw terminal job conclusion", errors);
  requireEqual(terminalJob?.run_attempt, proof.terminalJob.runAttempt, "raw terminal job attempt", errors);
  requireEqual(terminalJob?.started_at, proof.terminalJob.startedAt, "raw terminal job started_at", errors);
  requireEqual(terminalJob?.completed_at, proof.terminalJob.completedAt, "raw terminal job completed_at", errors);
  requireObjectBytes(terminalJob?.steps?.map(({ number, name, status, conclusion }) => ({ number, name, status, conclusion })), proof.terminalJob.steps, "raw terminal job steps", errors);

  const terminalArtifacts = rawPages.terminalArtifactsPage1.json;
  requireEqual(terminalArtifacts.total_count, 0, "raw terminal artifacts total_count", errors);
  requireEqual(terminalArtifacts.artifacts?.length, 0, "raw terminal artifacts page completeness", errors);

  for (const [key, entry] of Object.entries(proof.reviewedBytes)) {
    const historical = historicalBytes[key];
    requireEqual(historical.length, entry.bytes, `reviewed historical ${key} bytes`, errors);
    requireEqual(sha256(historical), entry.sha256, `reviewed historical ${key} SHA-256`, errors);
  }
}

function scanSensitiveFiles(files, errors) {
  for (const relativePath of files) {
    const absolute = path.join(root, relativePath);
    if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) continue;
    scanSensitiveText(fs.readFileSync(absolute, "utf8"), relativePath, errors);
  }
}

function scanSensitiveText(text, label, errors) {
  const patterns = [
    [/\uFFFD/u, "Unicode replacement character"],
    [/-----BEGIN(?: RSA| EC| OPENSSH)? PRIVATE KEY-----/iu, "private key marker"],
    [/(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16})/u, "credential token"],
    [/(?:Authorization\s*:\s*Bearer|Bearer\s+[A-Za-z0-9._~-]{20,})/iu, "authorization token"],
    [/(?:[A-Za-z]:\\Users\\[^\\\s]+|\/(?:home|Users)\/[^/\s]+)/u, "absolute user path"],
  ];
  for (const [pattern, description] of patterns) {
    if (pattern.test(text)) errors.push(`${label}: contains prohibited ${description}`);
  }
}

function validateState(state) {
  const errors = [];
  const { lock, proof, texts, workflowPresence } = state;

  requireEqual(lock.schemaVersion, 1, "lock.schemaVersion", errors);
  requireEqual(lock.task, "M3-13", "lock.task", errors);
  requireEqual(lock.issue, 80, "lock.issue", errors);
  requireEqual(lock.status, "contract_candidate", "lock.status", errors);
  requireEqual(lock.officialProof.path, paths.proof, "lock.officialProof.path", errors);
  requireEqual(lock.officialProof.canonicalBytes, 6871, "lock.officialProof.canonicalBytes", errors);
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
  requireEqual(Buffer.byteLength(proofCanonical, "utf8"), 6871, "official proof canonical utf8 bytes", errors);
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
  validateRawOfficialEvidence(state, errors);

  const phrases = {
    adr: [
      "M3-10 is terminally blocked",
      "zero AVD creation, zero installation attempt, zero retained samples and zero artifacts",
      "No second successor and no further renewal are permitted",
      CONTRACT_HASH,
      "fetch-depth: 0",
      "P0=0/P1=0/P2=0",
      "deliberately not embedded in either workflow candidate or the run name",
      "M3-13-SUCCESSOR-DIAGNOSTIC-V1-580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419-883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd",
    ],
    task: [
      "Issue #80",
      "M3-13-SUCCESSOR-DIAGNOSTIC-V1",
      "The available unlocked ARM device is irrelevant",
      "Independent review returns `P0=0/P1=0/P2=0` before push/PR publication",
    ],
    m310: ["32554806537", "terminally blocked", "must remain draft"],
    m305: ["M3-13", "successor", "remains blocked", "Terminal M3-10 仅是历史输入", "具体 successor implementation 与 remediation 任务 ID 必须在创建后加入本任务依赖"],
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
  const historicalBytes = {};
  for (const [key, entry] of Object.entries(proof.reviewedBytes)) {
    historicalBytes[key] = execFileSync("git", ["show", `${proof.diagnosticRun.headSha}:${entry.path}`], { cwd: root });
  }
  const rawPages = {};
  for (const [key, [relativePath]] of Object.entries(RAW_PAGE_SPECS)) {
    const bytes = readBuffer(relativePath);
    rawPages[key] = { bytes, json: JSON.parse(bytes.toString("utf8")) };
  }
  const texts = {};
  for (const [key, relativePath] of Object.entries(paths)) {
    if (key !== "lock" && key !== "proof") texts[key] = read(relativePath);
  }
  return {
    lock,
    proof,
    rawPages,
    historicalBytes,
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
  scanSensitiveFiles(changed, errors);
  return changed;
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
  const rawCases = [
    ["raw-diagnostic-run-id", (state) => { state.rawPages.diagnosticRun.json.id += 1; }],
    ["raw-diagnostic-job-step", (state) => { state.rawPages.diagnosticJobsPage1.json.jobs[0].steps[10].conclusion = "success"; }],
    ["raw-diagnostic-artifact", (state) => { state.rawPages.diagnosticArtifactsPage1.json.total_count = 1; }],
    ["raw-terminal-run-head", (state) => { state.rawPages.terminalRun.json.head_sha = "0".repeat(40); }],
    ["raw-terminal-job-step", (state) => { state.rawPages.terminalJobsPage1.json.jobs[0].steps[5].conclusion = "success"; }],
    ["raw-terminal-artifact", (state) => { state.rawPages.terminalArtifactsPage1.json.total_count = 1; }],
  ];
  for (const [name, mutate] of rawCases) {
    const mutated = clone(baseState);
    mutate(mutated);
    const rawErrors = validateState(mutated);
    if (rawErrors.length === 0) throw new Error(`raw mutation unexpectedly accepted: ${name}`);
  }
  const sensitiveCases = [
    "\uFFFD",
    ["-----BEGIN", " OPENSSH PRIVATE KEY-----"].join(""),
    `github_pat_${"a".repeat(24)}`,
    ["Authorization:", "Bearer", "abcdefghijklmnopqrstuvwxyz"].join(" "),
    ["C:", "Users", "private-user", "secret.txt"].join("\\"),
    ["", "home", "private-user", "secret.txt"].join("/"),
  ];
  for (const [index, value] of sensitiveCases.entries()) {
    const sensitiveErrors = [];
    scanSensitiveText(value, `sensitive-mutation-${index + 1}`, sensitiveErrors);
    if (sensitiveErrors.length === 0) throw new Error(`sensitive mutation unexpectedly accepted: ${index + 1}`);
  }
  return cases.length + pathCases.length + rawCases.length + sensitiveCases.length;
}

let state;
try {
  state = loadState();
} catch (error) {
  console.error(`M3-13 contract validation failed:\n- ${error.message}`);
  process.exit(1);
}

const errors = validateState(state);
let changedFiles = [];
if (baseRef) {
  try {
    changedFiles = validateBaseDiff(baseRef, errors);
  } catch (error) {
    errors.push(`base diff validation failed: ${error.message}`);
  }
}

if (sensitiveOnly && !baseRef) errors.push("--sensitive-only requires --base-ref");

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

if (sensitiveOnly) {
  console.log(`OK: M3-13 sensitive scan; ${changedFiles.length} changed files inspected`);
} else {
  console.log(`OK: M3-13 successor diagnostic identity contract${selfTest ? `; ${mutationCount} named mutations rejected` : ""}`);
}
