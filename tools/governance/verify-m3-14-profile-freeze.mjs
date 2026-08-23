#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { spawnSync } from "node:child_process";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(?:[A-Za-z]:)/u, (value) => value.slice(1))), "..", "..");
const args = new Set(process.argv.slice(2));
const baseIndex = process.argv.indexOf("--base-ref");
const baseRef = baseIndex >= 0 ? process.argv[baseIndex + 1] : undefined;
const reviewedWorkflowSuccessor = args.has("--allow-reviewed-workflows");
const IMPLEMENTATION_FREEZE_SHA = "50a831b5275ac53846924dbf4d4c9d10d1b25b35";
const PUBLICATION_SHA = "9fe48737d97853d1566cc2e642009d8ff1b8ab52";
const TERMINAL_REQUEST_SHA = "b0771d4853e0a7de7fb9db802cac719e34c67229";
const EXECUTION_IDENTITY_SHA = "96837a115f89e3866d56c315928314c9532b4b635859b6f5860c8d1c442e5357";
const DIAGNOSTIC_WORKFLOW_SHA = "cbe4eca5667415c3712d54c9ceeef56967f7a268c089d1cfa091f122412f4bf6";
const EVIDENCE_WORKFLOW_SHA = "cd91fb59f4905cb13639da43887de53bdbc5ff958e9bd023c3bcc7126493d704";
const TERMINAL_PROOF = "docs/evidence/M3-14/terminal-official-proof.json";
const TERMINAL_PAGE_SPECS = {
  diagnosticRun: ["docs/evidence/M3-14/raw/diagnostic-run.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32611656930", 13584, "95d174a5c369dd89cb31d345620aca06111a879b78b3e4dd29bbd2dbacd54a11"],
  diagnosticJobs: ["docs/evidence/M3-14/raw/diagnostic-jobs-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32611656930/jobs?per_page=100&page=1", 4490, "3e2e36ed673982b61deccfc1ace4f37dd90d1edf2debe9b1204e8dc9dc113219"],
  diagnosticArtifacts: ["docs/evidence/M3-14/raw/diagnostic-artifacts-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32611656930/artifacts?per_page=100&page=1", 33, "d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42"],
  terminalRun: ["docs/evidence/M3-14/raw/terminal-run.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32612414400", 13365, "5862c875de76e180374c5a7279dad28360a06de7530a4e9846e376188fab6343"],
  terminalJobs: ["docs/evidence/M3-14/raw/terminal-jobs-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32612414400/jobs?per_page=100&page=1", 2738, "d7f8bda3c7ca1cef6a504ec7fefab08897b10d4695b81cc1c1cbbef63e18d4b3"],
  terminalArtifacts: ["docs/evidence/M3-14/raw/terminal-artifacts-page-1.json", "/repos/xiaokh31/androidAppHardening/actions/runs/32612414400/artifacts?per_page=100&page=1", 33, "d3ad979d01443a9d7342e7fbe39064b41ebdb340029293f1b099bcfb6c493c42"],
};

function fail(message) {
  throw new Error(`M3-14 profile freeze: ${message}`);
}

function validateReviewedWorkflows(diagnostic, evidence) {
  for (const phrase of [
    "M3-13-SUCCESSOR-DIAGNOSTIC-V1-580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419-883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd",
    "m3-13-startup-attribution", "cancel-in-progress: false", "actions: read", "contents: read",
    "fetch-m3-12-profile-package.mjs", "verify-m3-12-profile-retention.mjs", "9260244215",
    "fetch-depth: 0", "pre-run-ledger.json", "execution-ledger", "Prove first-and-only successor identity before Android setup",
    "GITHUB_RUN_ATTEMPT", "pre-device-runs-page-1.json", "run-m3-14-startup-attribution.mjs",
    "m3-13-startup-attribution-raw",
  ]) if (!diagnostic.includes(phrase)) fail(`diagnostic workflow missing ${phrase}`);
  for (const phrase of [
    "diagnostic-terminal-request.json", "collect-m3-14-github-evidence.mjs",
    "verify-m3-14-startup-attribution.mjs github-evidence", "m3-13-startup-attribution-terminal-evidence",
    "fetch-depth: 0", "git rev-list --parents -n 1 HEAD", "git rev-parse HEAD^",
    "git diff --name-only \"$diagnostic_head\"..HEAD",
    "cancel-in-progress: false", "actions: read", "contents: read",
  ]) if (!evidence.includes(phrase)) fail(`evidence workflow missing ${phrase}`);
  for (const forbidden of ["workflow_dispatch", "pull_request", "schedule:"]) {
    if (diagnostic.includes(forbidden) || evidence.includes(forbidden)) fail(`canonical workflow has forbidden trigger ${forbidden}`);
  }
}

function read(relative) {
  const file = path.join(root, relative);
  if (!fs.statSync(file, { throwIfNoEntry: false })?.isFile()) fail(`missing ${relative}`);
  return fs.readFileSync(file, "utf8");
}

function readBytes(relative) {
  const file = path.join(root, relative);
  if (!fs.statSync(file, { throwIfNoEntry: false })?.isFile()) fail(`missing ${relative}`);
  return fs.readFileSync(file);
}

function sha256Bytes(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function digest(relative) {
  return crypto.createHash("sha256").update(fs.readFileSync(path.join(root, relative))).digest("hex");
}

function gitOk(args, label) {
  const result = spawnSync("git", args, { cwd: root, encoding: "utf8", timeout: 30_000 });
  if (result.status !== 0) fail(`qualification ${label} differs`);
}

function gitOutput(args, label, encoding = "utf8") {
  const result = spawnSync("git", args, { cwd: root, encoding, timeout: 30_000 });
  if (result.status !== 0) fail(`${label} differs`);
  return result.stdout;
}

function validatePublicationTopology(parent, changedFiles) {
  if (parent !== IMPLEMENTATION_FREEZE_SHA) fail("publication parent differs");
  const expected = [
    ".github/workflows/m3-13-startup-attribution-evidence.yml",
    ".github/workflows/m3-13-startup-attribution.yml",
    "docs/evidence/M3-14/pre-run-ledger.json",
  ];
  if (JSON.stringify([...changedFiles].sort()) !== JSON.stringify(expected)) fail("publication changed-file set differs");
}

function validatePublicationBinding(model) {
  const { ledger, diagnosticCandidate, evidenceCandidate, diagnostic, evidence } = model;
  if (ledger.schemaVersion !== 1 || ledger.taskKey !== "M3-13-SUCCESSOR-DIAGNOSTIC-V1" ||
      ledger.executionIdentitySha256 !== EXECUTION_IDENTITY_SHA ||
      sha256Bytes(Buffer.from(JSON.stringify(ledger.executionIdentity))) !== EXECUTION_IDENTITY_SHA ||
      ledger.executionIdentity?.implementationFreezeSha !== IMPLEMENTATION_FREEZE_SHA ||
      ledger.executionIdentity?.diagnosticWorkflowCandidateSha256 !== DIAGNOSTIC_WORKFLOW_SHA ||
      ledger.executionIdentity?.evidenceWorkflowCandidateSha256 !== EVIDENCE_WORKFLOW_SHA) {
    fail("publication ledger identity differs");
  }
  if (sha256Bytes(diagnosticCandidate) !== DIAGNOSTIC_WORKFLOW_SHA ||
      sha256Bytes(evidenceCandidate) !== EVIDENCE_WORKFLOW_SHA ||
      !diagnostic.equals(diagnosticCandidate) || !evidence.equals(evidenceCandidate)) {
    fail("published workflow bytes differ from immutable ledger candidates");
  }
}

function verifyReviewedPublication() {
  const ledgerPath = "docs/evidence/M3-14/pre-run-ledger.json";
  const diagnosticCandidatePath = "tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution.yml";
  const evidenceCandidatePath = "tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution-evidence.yml";
  const diagnosticPath = ".github/workflows/m3-13-startup-attribution.yml";
  const evidencePath = ".github/workflows/m3-13-startup-attribution-evidence.yml";
  const ledgerBytes = readBytes(ledgerPath);
  const model = {
    ledger: JSON.parse(ledgerBytes.toString("utf8")),
    diagnosticCandidate: readBytes(diagnosticCandidatePath),
    evidenceCandidate: readBytes(evidenceCandidatePath),
    diagnostic: readBytes(diagnosticPath),
    evidence: readBytes(evidencePath),
  };
  validatePublicationBinding(model);
  const parent = gitOutput(["rev-parse", `${PUBLICATION_SHA}^`], "publication parent").trim();
  const changed = gitOutput(["diff", "--name-only", `${IMPLEMENTATION_FREEZE_SHA}..${PUBLICATION_SHA}`], "publication diff")
    .trim().split(/\r?\n/u).filter(Boolean).map((value) => value.replaceAll("\\", "/"));
  validatePublicationTopology(parent, changed);
  for (const relative of [ledgerPath, diagnosticPath, evidencePath]) {
    const published = gitOutput(["show", `${PUBLICATION_SHA}:${relative}`], `published ${relative}`, null);
    if (!readBytes(relative).equals(published)) fail(`current ${relative} differs from immutable publication`);
  }
}

function validateRun(run, expected) {
  for (const [key, value] of Object.entries(expected)) {
    if (key === "label") continue;
    if (run?.[key] !== value) fail(`${expected.label} ${key} differs`);
  }
}

function requireStep(job, number, name, conclusion, label) {
  const matches = (job.steps ?? []).filter((step) => step.number === number);
  if (matches.length !== 1 || matches[0].name !== name || matches[0].conclusion !== conclusion) {
    fail(`${label} step ${number} differs`);
  }
}

function validateTerminalSemantics(pages) {
  validateRun(pages.diagnosticRun, {
    label: "diagnostic run", id: 32611656930,
    name: "M3-13-SUCCESSOR-DIAGNOSTIC-V1-580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419-883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd",
    path: ".github/workflows/m3-13-startup-attribution.yml", head_sha: PUBLICATION_SHA,
    event: "push", run_attempt: 1, status: "completed", conclusion: "failure",
  });
  validateRun(pages.terminalRun, {
    label: "terminal run", id: 32612414400, name: "M3-13 successor startup attribution terminal evidence",
    path: ".github/workflows/m3-13-startup-attribution-evidence.yml", head_sha: TERMINAL_REQUEST_SHA,
    event: "push", run_attempt: 1, status: "completed", conclusion: "failure",
  });
  for (const [page, id, name, label] of [
    [pages.diagnosticJobs, 97125597267, "m3-13-startup-attribution", "diagnostic job"],
    [pages.terminalJobs, 97127412040, "m3-13-startup-attribution-terminal-evidence", "terminal job"],
  ]) {
    if (page.total_count !== 1 || page.jobs?.length !== 1 || page.jobs[0].id !== id ||
        page.jobs[0].name !== name || page.jobs[0].status !== "completed" || page.jobs[0].conclusion !== "failure") {
      fail(`${label} identity differs`);
    }
  }
  const diagnosticJob = pages.diagnosticJobs.jobs[0];
  for (const [number, name] of [
    [2, "Check out exact diagnostic head"], [3, "Verify reviewed successor publication and full-history qualification"],
    [4, "Prove first-and-only successor identity before Android setup"], [5, "Verify pinned Ubuntu runner"],
    [9, "Prepare pinned API 36 r2 and Emulator 37.1.11"],
    [10, "Download and verify canonical originals and retained profile package"],
    [11, "Prepare verified M2-07 Native crypto source"], [12, "Build exact Release surfaces"],
  ]) requireStep(diagnosticJob, number, name, "success", "diagnostic");
  requireStep(diagnosticJob, 13, "Execute first-and-only API 36 attribution diagnostic", "failure", "diagnostic");
  requireStep(diagnosticJob, 14, "Upload immutable raw diagnostic package", "skipped", "diagnostic");
  const terminalJob = pages.terminalJobs.jobs[0];
  for (const [number, name] of [
    [2, "Check out terminal evidence head"], [3, "Bind terminal request to reviewed diagnostic bytes"],
    [4, "Verify pinned Ubuntu runner"], [5, "Set up Node.js"],
  ]) requireStep(terminalJob, number, name, "success", "terminal");
  requireStep(terminalJob, 6, "Collect and verify official terminal evidence", "failure", "terminal");
  requireStep(terminalJob, 7, "Upload terminal evidence", "skipped", "terminal");
  for (const [page, label] of [[pages.diagnosticArtifacts, "diagnostic artifacts"], [pages.terminalArtifacts, "terminal artifacts"]]) {
    if (page.total_count !== 0 || !Array.isArray(page.artifacts) || page.artifacts.length !== 0) fail(`${label} differ`);
  }
}

function validateTerminalPageBindings(proof, rawPages) {
  const pages = {};
  for (const [key, [relative, endpoint, bytes, hash]] of Object.entries(TERMINAL_PAGE_SPECS)) {
    const entry = proof.pages?.[key];
    if (entry?.path !== relative || entry?.endpoint !== endpoint || entry?.bytes !== bytes || entry?.sha256 !== hash) {
      fail(`terminal proof page ${key} differs`);
    }
    const raw = rawPages[key];
    if (!Buffer.isBuffer(raw) || raw.length !== bytes || sha256Bytes(raw) !== hash) fail(`terminal raw page ${key} differs`);
    pages[key] = JSON.parse(raw.toString("utf8"));
  }
  return pages;
}

function verifyTerminalEvidence() {
  const proofText = read(TERMINAL_PROOF).replaceAll("\r\n", "\n");
  const proof = JSON.parse(proofText);
  if (proofText !== `${JSON.stringify(proof, null, 2)}\n`) fail("terminal proof is not canonical JSON");
  if (proof.schemaVersion !== 1 || proof.taskId !== "M3-14" || proof.taskKey !== "M3-13-SUCCESSOR-DIAGNOSTIC-V1" ||
      proof.executionIdentitySha256 !== EXECUTION_IDENTITY_SHA ||
      proof.productTupleSha256 !== "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd" ||
      proof.publicationHeadSha !== PUBLICATION_SHA ||
      proof.terminalRequestHeadSha !== TERMINAL_REQUEST_SHA || proof.retryPermitted !== false ||
      proof.replacementPermitted !== false || proof.furtherRenewalPermitted !== false) fail("terminal proof contract differs");
  const rawPages = Object.fromEntries(Object.entries(TERMINAL_PAGE_SPECS)
    .map(([key, [relative]]) => [key, readBytes(relative)]));
  const pages = validateTerminalPageBindings(proof, rawPages);
  validateTerminalSemantics(pages);
  if (proof.diagnostic?.runId !== 32611656930 || proof.diagnostic?.jobId !== 97125597267 ||
      proof.diagnostic?.runAttempt !== 1 || proof.diagnostic?.failedStepNumber !== 13 ||
      proof.diagnostic?.event !== "push" || proof.diagnostic?.status !== "completed" || proof.diagnostic?.conclusion !== "failure" ||
      proof.diagnostic?.artifactCount !== 0 || proof.diagnostic?.retainedSamples !== 0 ||
      proof.terminalEvidence?.runId !== 32612414400 || proof.terminalEvidence?.jobId !== 97127412040 ||
      proof.terminalEvidence?.runAttempt !== 1 || proof.terminalEvidence?.failedStepNumber !== 6 ||
      proof.terminalEvidence?.event !== "push" || proof.terminalEvidence?.status !== "completed" || proof.terminalEvidence?.conclusion !== "failure" ||
      proof.terminalEvidence?.artifactCount !== 0) fail("terminal proof summary differs");
  const parent = gitOutput(["rev-parse", `${TERMINAL_REQUEST_SHA}^`], "terminal request parent").trim();
  const changed = gitOutput(["diff", "--name-only", `${PUBLICATION_SHA}..${TERMINAL_REQUEST_SHA}`], "terminal request diff")
    .trim().split(/\r?\n/u).filter(Boolean).map((value) => value.replaceAll("\\", "/"));
  if (parent !== PUBLICATION_SHA || JSON.stringify(changed) !== JSON.stringify(["docs/evidence/M3-14/diagnostic-terminal-request.json"])) {
    fail("terminal request topology differs");
  }
}

function verifyQualificationEvidence() {
  const value = JSON.parse(read("docs/evidence/M3-14/qualification-evidence.json"));
  const expectedAncestors = [
    "98e652b3017df0255ba8be4869513698c18c9ce6",
    "c1d81fe6c4257efecf8cbb0b23aa724034f6b3a1",
    "621117dc5639bf4c9c9e8696c554bbd2ab821d8c",
  ];
  const expectedHistorical = [
    "86ec37475fd7a96b4baf764530baefc3fe3d4cde",
    "790ae4579ce3562dc93f3c533ffb786a39517600",
    "5fa20550b6f05fec8fa474df6947695f7f5f1937",
  ];
  if (value.schemaVersion !== 1 || value.taskId !== "M3-14" || value.issue !== 82 ||
      value.implementationBaseSha !== "960eb9f406eb1a7b7c9b324598fb59936aa1c5b5" ||
      JSON.stringify(value.requiredAncestors) !== JSON.stringify(expectedAncestors) ||
      JSON.stringify(value.requiredHistoricalObjects) !== JSON.stringify(expectedHistorical) ||
      value.contracts?.contractIdentitySha256 !== "580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419" ||
      value.contracts?.productTupleSha256 !== "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd" ||
      value.contracts?.profileArchiveSha256 !== "21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964" ||
      value.qualification?.fullHistoryRequired !== true || value.qualification?.beforeAndroidRequired !== true ||
      value.qualification?.canonicalWorkflowAbsentAtFreeze !== true || value.qualification?.runLimit !== 1 ||
      value.qualification?.runAttempt !== 1 || value.qualification?.furtherRenewalPermitted !== false) {
    fail("qualification evidence contract differs");
  }
  for (const [name, prefix] of [["m313EligibilityLock", "docs/evidence/M3-13/diagnostic-eligibility-lock.json"],
    ["m313OfficialProof", "docs/evidence/M3-13/predecessor-official-proof.json"],
    ["m312RetentionLock", "docs/evidence/M3-12/profile-package-retention-lock.json"]]) {
    const file = value.contracts[`${name}Path`];
    const stat = fs.statSync(path.join(root, file));
    if (file !== prefix || stat.size !== value.contracts[`${name}Bytes`] || digest(file) !== value.contracts[`${name}Sha256`]) {
      fail(`qualification source differs: ${name}`);
    }
  }
  for (const ancestor of expectedAncestors) gitOk(["merge-base", "--is-ancestor", ancestor, value.implementationBaseSha], ancestor);
  for (const historical of expectedHistorical) gitOk(["cat-file", "-e", `${historical}^{commit}`], historical);
}

function listFiles(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(directory, entry.name);
    return entry.isDirectory() ? listFiles(absolute) : entry.isFile() ? [absolute] : [];
  });
}

function surfaceViolations(relative, value) {
  const text = Buffer.isBuffer(value) ? value.toString("latin1") : value;
  const hits = [];
  const patterns = [
    /M310StartupTimingObserver/u,
    /m3_10_profile/u,
    /Lah\/runtime\/profile\/M310/u,
    /AAH-M3-10/u,
  ];
  for (const pattern of patterns) if (pattern.test(text)) hits.push(`${relative}:${pattern.source}`);
  return hits;
}

function verifyProductionSurface() {
  const roots = ["runtime", "host", "fixtures", "benchmarks", "distribution"];
  const violations = [];
  for (const relativeRoot of roots) {
    for (const file of listFiles(path.join(root, relativeRoot))) {
      const relative = path.relative(root, file).replaceAll("\\", "/");
      if (!/\/src\/(?:main|release)\//u.test(`/${relative}`) &&
          !relative.startsWith("distribution/")) continue;
      violations.push(...surfaceViolations(relative, fs.readFileSync(file)));
    }
  }
  if (violations.length) fail(`production observer surface detected: ${violations.join(", ")}`);
}

function verifyTrackedDesign() {
  const observer = read("tools/validation/m3-10/profile-src/ah/runtime/profile/M310StartupTimingObserver.java");
  for (const phrase of [
    "Process.getStartElapsedRealtime()",
    "SystemClock.elapsedRealtimeNanos()",
    "public static void p15(boolean focused)",
    "private static synchronized void calibrationPoint(int index)",
  ]) if (!observer.includes(phrase)) fail(`observer missing ${phrase}`);
  for (const forbidden of ["System.getenv", "getIntent(", "System.getProperty", "new File(", "SharedPreferences"]) {
    if (observer.includes(forbidden)) fail(`observer contains activation surface ${forbidden}`);
  }

  const verifier = read("host/container/src/test/kotlin/ah/host/container/M310CanonicalProfileVerifier.kt");
  const retainedSupport = read("host/container/src/test/kotlin/ah/host/container/M310RetainedProfileSupport.kt");
  const runner = read("tools/validation/run-m3-14-startup-attribution.mjs");
  const evidenceVerifier = read("tools/validation/verify-m3-14-startup-attribution.mjs");
  const profileLock = read("tools/validation/m3-10/canonical-profile-lock.json");
  const releaseLock = read("tools/validation/m3-10/release-artifact-lock.json");
  const environmentLock = read("tools/validation/m3-10/api36-environment-lock.json");
  const toolchainLock = read("tools/validation/m3-14/toolchain-lock.json");
  const qualification = read("docs/evidence/M3-14/qualification-evidence.json");
  const diagnosticCandidate = read("tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution.yml");
  const evidenceCandidate = read("tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution-evidence.yml");
  const m305 = read("docs/tasks/M3-05-size-startup-memory-benchmarks.md");
  const adr = read("docs/adr/0016-end-to-end-startup-attribution-boundary.md");
  validateContractText(m305, adr);
  for (const forbidden of [
    "host/container/src/test/kotlin/ah/host/container/M310CanonicalProfileDeriver.kt",
    "host/container/src/test/kotlin/ah/host/container/M310DexProfileTool.kt",
    "tools/validation/prepare-m3-14-profile-package.mjs",
  ]) if (fs.existsSync(path.join(root, forbidden))) fail(`profile regeneration surface exists: ${forbidden}`);
  for (const phrase of [
    "internal object M310RetainedProfileSupport",
    "fun decryptPayload(",
    "fun readAllRuntimeSlots(",
    "fun locateSlot(",
    "fun readEntries(",
    "Writes only scratch mutation APKs",
  ]) if (!retainedSupport.includes(phrase)) fail(`retained profile support missing ${phrase}`);
  for (const forbidden of [
    "fun main(",
    "DexContainerBuilder(",
    "SeededContainerRandom",
    "patchRuntimeSlot(",
    "secret seed",
    "apksigner",
    "PrivateKey",
  ]) if (retainedSupport.includes(forbidden)) fail(`retained profile support exposes forbidden generation/signing surface: ${forbidden}`);
  for (const phrase of [
    "manifestBytesEqual",
    "authenticatedContainerVerified",
    "runtimeShareSlotsOnly",
    "requireProbeCalls",
    "requireProbeAdjacencyTokens",
    "opcode:MOVE_RESULT_OBJECT",
    "h2-overload",
    "h7-wrong-owner",
    "h7-wrong-value",
    "VerifiedScheme.V3",
  ]) if (!verifier.includes(phrase)) fail(`verifier missing ${phrase}`);
  for (const phrase of ["validateProfileLock", "runDexdump", "recursiveArchiveContainsAny", "validateGithubEvidence",
    "validateReleaseArtifactLock", "validateProfileVerification", "validateEnvironmentLock", "EXPECTED_EVENTS",
    "requireTrackedLockCopy", "result.productTuple !== PRODUCT_TUPLE", "keys.slice(8)"]) {
    if (!evidenceVerifier.includes(phrase)) fail(`evidence verifier missing ${phrase}`);
  }
  for (const phrase of ["preflight(options, output)", "exactIdentity = await identity(options, adb, output)",
    "sameBoot(adb", "fetchOfficialJobsPage", "current-jobs-page-1.json", "packagePaths(", "requireUninstallSuccess(",
    "requireRemoteAbsence(", "rawCalibrationNs", "TRACKED_LOCK_INPUTS"]) {
    if (!runner.includes(phrase)) fail(`diagnostic runner missing ${phrase}`);
  }
  if (runner.lastIndexOf("preflight(options, output)") > runner.lastIndexOf("exactIdentity = await identity(options, adb, output)")) {
    fail("diagnostic preflight occurs after device identity/install boundary");
  }
  if (runner.includes("nearestRank(")) fail("diagnostic runner must not aggregate raw calibration samples");
  for (const phrase of ["observer", "profileSigner", "signedBaseline", "signedProtected", "regenerationPermitted"]) {
    if (!profileLock.includes(`\"${phrase}\"`)) fail(`profile lock missing ${phrase}`);
  }
  for (const phrase of ["release-bootstrap", "requiredEntries", "apksignerJar", "dexdump", "zipalign", "d8Jar"]) {
    if (!releaseLock.includes(`\"${phrase}\"`)) fail(`release artifact lock missing ${phrase}`);
  }
  for (const phrase of ["system-images;android-36;default;x86_64", "37.1.11", "15917651", "fingerprint"]) {
    if (!environmentLock.includes(phrase)) fail(`environment lock missing ${phrase}`);
  }
  for (const phrase of ["20260816.277.1", "ubuntu24/20260816.277", "37.1.11", "e1b9d9fb665001ef27b16e57d8762a2d54aec6bff617e17506edb8676667b9da"]) {
    if (!toolchainLock.includes(phrase)) fail(`toolchain lock missing ${phrase}`);
  }
  for (const phrase of ["960eb9f406eb1a7b7c9b324598fb59936aa1c5b5", "621117dc5639bf4c9c9e8696c554bbd2ab821d8c", "fullHistoryRequired", "furtherRenewalPermitted"]) {
    if (!qualification.includes(phrase)) fail(`qualification evidence missing ${phrase}`);
  }
  validateReviewedWorkflows(diagnosticCandidate, evidenceCandidate);

  const catalog = read("gradle/libs.versions.toml");
  const lock = read("host/container/gradle.lockfile");
  const metadata = read("gradle/verification-metadata.xml");
  for (const [text, phrase, label] of [
    [catalog, "dexlib2 = \"2.5.2\"", "version catalog"],
    [lock, "org.smali:dexlib2:2.5.2", "dependency lock"],
    [metadata, "org.smali\" name=\"dexlib2\" version=\"2.5.2\"", "verification metadata"],
  ]) if (!text.includes(phrase)) fail(`${label} missing pinned dexlib2`);

  const workflowPaths = [
    ".github/workflows/m3-13-startup-attribution.yml",
    ".github/workflows/m3-13-startup-attribution-evidence.yml",
  ];
  const present = workflowPaths.filter((workflow) => fs.existsSync(path.join(root, workflow)));
  if (present.length !== 0 && !reviewedWorkflowSuccessor) {
    fail(`canonical workflow exists without --allow-reviewed-workflows: ${present.join(", ")}`);
  }
  if (reviewedWorkflowSuccessor) {
    if (present.length !== workflowPaths.length) fail("reviewed canonical workflow pair is incomplete");
    const diagnostic = read(workflowPaths[0]);
    const evidence = read(workflowPaths[1]);
    validateReviewedWorkflows(diagnostic, evidence);
    if (diagnostic !== diagnosticCandidate || evidence !== evidenceCandidate) fail("published workflow bytes differ from reviewed candidates");
  }
}

function validateContractText(m305, adr) {
  if (!m305.includes("M3-10") || !m305.includes("P50 增量均不超过 300 ms") ||
      !m305.includes("PR #63 保持阻塞") || !adr.includes("unchanged 300 ms M3-05") ||
      !adr.includes("M3-05 remains blocked")) fail("M3-05 dependency/budget contract differs");
}

function verifyDiff() {
  if (!baseRef) return;
  const result = spawnSync("git", ["diff", "--name-only", `${baseRef}...HEAD`], {
    cwd: root,
    encoding: "utf8",
    timeout: 30_000,
  });
  if (result.status !== 0) fail(`git diff failed: ${result.stderr.trim()}`);
  const forbidden = result.stdout.split(/\r?\n/u).filter(Boolean).filter((file) =>
    /^(?:runtime|host|fixtures|benchmarks)\/.*\/src\/(?:main|release)\//u.test(file) ||
    /^(?:runtime\/[^/]+|host\/cli|fixtures\/android|benchmarks\/android|distribution)\/build\.gradle(?:\.kts)?$/u.test(file) ||
    (!reviewedWorkflowSuccessor && (file === ".github/workflows/m3-13-startup-attribution.yml" ||
      file === ".github/workflows/m3-13-startup-attribution-evidence.yml")),
  );
  if (forbidden.length) fail(`production/workflow diff detected: ${forbidden.join(", ")}`);
}

function selfTest() {
  const mutations = [
    ["runtime/native/src/main/java/X.java", "M310StartupTimingObserver.p1();"],
    ["runtime/bootstrap/src/release/java/X.java", "m3_10_profile=true"],
    ["host/cli/src/main/kotlin/X.kt", "AAH-M3-10"],
    ["fixtures/android/src/main/java/X.java", "Lah/runtime/profile/M310;"],
    ["benchmarks/android/src/release/java/X.java", "M310StartupTimingObserver"],
    ["distribution/readme.txt", "m3_10_profile"],
    ["runtime/bootstrap/build/outputs/aar/bootstrap-release.aar", Buffer.from("\0M310StartupTimingObserver\0")],
    ["distribution/build/distributions/host-cli.zip", Buffer.from("dex\n039\0AAH-M3-10")],
  ];
  for (const [name, text] of mutations) {
    if (surfaceViolations(name, text).length === 0) fail(`self-test mutation was accepted: ${name}`);
  }
  for (const [name, m305, adr] of [
    ["m3-05-dependency", read("docs/tasks/M3-05-size-startup-memory-benchmarks.md").replaceAll("M3-10", "M3-XX"),
      read("docs/adr/0016-end-to-end-startup-attribution-boundary.md")],
    ["m3-05-budget", read("docs/tasks/M3-05-size-startup-memory-benchmarks.md").replace("300 ms", "301 ms"),
      read("docs/adr/0016-end-to-end-startup-attribution-boundary.md")],
  ]) {
    let rejected = false;
    try { validateContractText(m305, adr); } catch { rejected = true; }
    if (!rejected) fail(`contract mutation was accepted: ${name}`);
  }
  {
    const diagnostic = read("tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution.yml");
    const evidence = read("tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution-evidence.yml");
    for (const [name, mutated] of [
      ["terminal-shallow-checkout", evidence.replace("fetch-depth: 0", "fetch-depth: 1")],
      ["terminal-parent-binding", evidence.replace("git rev-parse HEAD^", "git rev-parse HEAD")],
      ["terminal-diff-binding", evidence.replace("git diff --name-only", "git show --name-only")],
      ["terminal-deletion-filter", evidence.replace("git diff --name-only \"$diagnostic_head\"..HEAD",
        "git diff --name-only --diff-filter=ACMRTUXB \"$diagnostic_head\"..HEAD")],
    ]) {
      let rejected = false;
      try { validateReviewedWorkflows(diagnostic, mutated); } catch { rejected = true; }
      if (!rejected) fail(`workflow self-test mutation was accepted: ${name}`);
    }
  }
  {
    const loadPublicationModel = () => ({
      ledger: JSON.parse(read("docs/evidence/M3-14/pre-run-ledger.json")),
      diagnosticCandidate: readBytes("tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution.yml"),
      evidenceCandidate: readBytes("tools/validation/m3-14/workflow-candidates/m3-13-startup-attribution-evidence.yml"),
      diagnostic: readBytes(".github/workflows/m3-13-startup-attribution.yml"),
      evidence: readBytes(".github/workflows/m3-13-startup-attribution-evidence.yml"),
    });
    for (const [name, mutate] of [
      ["publication-coordinated-workflow-drift", (model) => {
        model.diagnosticCandidate = Buffer.concat([model.diagnosticCandidate, Buffer.from("# drift\n")]);
        model.diagnostic = Buffer.from(model.diagnosticCandidate);
      }],
      ["publication-ledger-drift", (model) => {
        model.ledger.executionIdentity.diagnosticWorkflowCandidateSha256 = "0".repeat(64);
        model.ledger.executionIdentitySha256 = sha256Bytes(Buffer.from(JSON.stringify(model.ledger.executionIdentity)));
      }],
    ]) {
      const model = loadPublicationModel();
      mutate(model);
      let rejected = false;
      try { validatePublicationBinding(model); } catch { rejected = true; }
      if (!rejected) fail(`publication mutation was accepted: ${name}`);
    }
    for (const [name, parent, files] of [
      ["publication-parent", "0".repeat(40), [
        ".github/workflows/m3-13-startup-attribution-evidence.yml",
        ".github/workflows/m3-13-startup-attribution.yml",
        "docs/evidence/M3-14/pre-run-ledger.json",
      ]],
      ["publication-path-set", IMPLEMENTATION_FREEZE_SHA, [
        ".github/workflows/m3-13-startup-attribution-evidence.yml",
        ".github/workflows/m3-13-startup-attribution.yml",
        "docs/evidence/M3-14/pre-run-ledger.json",
        "README.md",
      ]],
    ]) {
      let rejected = false;
      try { validatePublicationTopology(parent, files); } catch { rejected = true; }
      if (!rejected) fail(`publication topology mutation was accepted: ${name}`);
    }
  }
  {
    const proof = JSON.parse(read(TERMINAL_PROOF));
    proof.pages.diagnosticRun.sha256 = "0".repeat(64);
    const rawPages = Object.fromEntries(Object.entries(TERMINAL_PAGE_SPECS)
      .map(([key, [relative]]) => [key, readBytes(relative)]));
    let rejected = false;
    try { validateTerminalPageBindings(proof, rawPages); } catch { rejected = true; }
    if (!rejected) fail("terminal proof hash mutation was accepted");

    const loadPages = () => Object.fromEntries(Object.entries(TERMINAL_PAGE_SPECS)
      .map(([key, [relative]]) => [key, JSON.parse(read(relative))]));
    for (const [name, mutate] of [
      ["terminal-diagnostic-run", (pages) => { pages.diagnosticRun.id += 1; }],
      ["terminal-diagnostic-job", (pages) => { pages.diagnosticJobs.jobs[0].id += 1; }],
      ["terminal-diagnostic-failed-step", (pages) => { pages.diagnosticJobs.jobs[0].steps.find((step) => step.number === 13).conclusion = "success"; }],
      ["terminal-diagnostic-artifact", (pages) => { pages.diagnosticArtifacts.total_count = 1; }],
      ["terminal-evidence-run", (pages) => { pages.terminalRun.id += 1; }],
      ["terminal-evidence-job", (pages) => { pages.terminalJobs.jobs[0].id += 1; }],
      ["terminal-evidence-artifact", (pages) => { pages.terminalArtifacts.total_count = 1; }],
    ]) {
      const pages = loadPages();
      mutate(pages);
      let rejected = false;
      try { validateTerminalSemantics(pages); } catch { rejected = true; }
      if (!rejected) fail(`terminal evidence mutation was accepted: ${name}`);
    }
  }
  console.log(`M3-14 profile freeze self-test PASS mutations=${mutations.length + 18}`);
}

verifyProductionSurface();
verifyQualificationEvidence();
verifyTrackedDesign();
if (reviewedWorkflowSuccessor) verifyReviewedPublication();
verifyTerminalEvidence();
verifyDiff();
if (args.has("--self-test")) selfTest();
console.log(`M3-14 profile freeze PASS workflows=${reviewedWorkflowSuccessor ? "reviewed" : "absent"} productionObserver=absent`);
