#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(?:[A-Za-z]:)/u, (value) => value.slice(1))), "..", "..");
const args = new Set(process.argv.slice(2));
const baseIndex = process.argv.indexOf("--base-ref");
const baseRef = baseIndex >= 0 ? process.argv[baseIndex + 1] : undefined;
const reviewedWorkflowSuccessor = args.has("--allow-reviewed-workflows");

function fail(message) {
  throw new Error(`M3-10 profile freeze: ${message}`);
}

function validateReviewedWorkflows(diagnostic, evidence) {
  for (const phrase of [
    "M3-09-DIAGNOSTIC-V1-883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd",
    "m3-09-startup-attribution", "cancel-in-progress: false", "actions: read", "contents: read",
    "fetch-m3-12-profile-package.mjs", "verify-m3-12-profile-retention.mjs", "9260244215",
    "run-m3-10-startup-attribution.mjs", "m3-09-startup-attribution-raw",
  ]) if (!diagnostic.includes(phrase)) fail(`diagnostic workflow missing ${phrase}`);
  for (const phrase of [
    "diagnostic-terminal-request.json", "collect-m3-10-github-evidence.mjs",
    "verify-m3-10-startup-attribution.mjs github-evidence", "m3-09-startup-attribution-terminal-evidence",
    "fetch-depth: 0", "git rev-list --parents -n 1 HEAD", "git rev-parse HEAD^", "git diff --name-only",
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

  const deriver = read("host/container/src/test/kotlin/ah/host/container/M310CanonicalProfileDeriver.kt");
  const verifier = read("host/container/src/test/kotlin/ah/host/container/M310CanonicalProfileVerifier.kt");
  const transformer = read("host/container/src/test/kotlin/ah/host/container/M310DexProfileTool.kt");
  const preparation = read("tools/validation/prepare-m3-10-profile-package.mjs");
  const runner = read("tools/validation/run-m3-10-startup-attribution.mjs");
  const evidenceVerifier = read("tools/validation/verify-m3-10-startup-attribution.mjs");
  const profileLock = read("tools/validation/m3-10/canonical-profile-lock.json");
  const releaseLock = read("tools/validation/m3-10/release-artifact-lock.json");
  const environmentLock = read("tools/validation/m3-10/api36-environment-lock.json");
  const m305 = read("docs/tasks/M3-05-size-startup-memory-benchmarks.md");
  const adr = read("docs/adr/0016-end-to-end-startup-attribution-boundary.md");
  validateContractText(m305, adr);
  for (const phrase of [
    "requireExactOriginal(baseline, BASELINE_SIZE, BASELINE_SHA256",
    "SeededContainerRandom(seed)",
    "DexContainerBuilder(",
    "patchRuntimeSlot(",
    "profileSignerSha256Prefix",
  ]) if (!deriver.includes(phrase)) fail(`deriver missing ${phrase}`);
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
  for (const phrase of ["payload-baseline", "payload-protected", "shell", "h0", "h8", "p15"]) {
    if (!transformer.includes(phrase)) fail(`transformer missing ${phrase}`);
  }
  for (const phrase of ["36.1.0", "--v3-signing-enabled", "M310_PROFILE_PASS", "finally", "temporarySigningAbsent",
    "release-lock", "toolLocked(toolchain.d8Jar", "toolLocked(toolchain.zipalign"]) {
    if (!preparation.includes(phrase)) fail(`profile preparation missing ${phrase}`);
  }
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

  const catalog = read("gradle/libs.versions.toml");
  const lock = read("host/container/gradle.lockfile");
  const metadata = read("gradle/verification-metadata.xml");
  for (const [text, phrase, label] of [
    [catalog, "dexlib2 = \"2.5.2\"", "version catalog"],
    [lock, "org.smali:dexlib2:2.5.2", "dependency lock"],
    [metadata, "org.smali\" name=\"dexlib2\" version=\"2.5.2\"", "verification metadata"],
  ]) if (!text.includes(phrase)) fail(`${label} missing pinned dexlib2`);

  const workflowPaths = [
    ".github/workflows/m3-09-startup-attribution.yml",
    ".github/workflows/m3-09-startup-attribution-evidence.yml",
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
    (!reviewedWorkflowSuccessor && (file === ".github/workflows/m3-09-startup-attribution.yml" ||
      file === ".github/workflows/m3-09-startup-attribution-evidence.yml")),
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
  if (reviewedWorkflowSuccessor) {
    const diagnostic = read(".github/workflows/m3-09-startup-attribution.yml");
    const evidence = read(".github/workflows/m3-09-startup-attribution-evidence.yml");
    for (const [name, mutated] of [
      ["terminal-shallow-checkout", evidence.replace("fetch-depth: 0", "fetch-depth: 1")],
      ["terminal-parent-binding", evidence.replace("git rev-parse HEAD^", "git rev-parse HEAD")],
      ["terminal-diff-binding", evidence.replace("git diff --name-only", "git show --name-only")],
    ]) {
      let rejected = false;
      try { validateReviewedWorkflows(diagnostic, mutated); } catch { rejected = true; }
      if (!rejected) fail(`workflow self-test mutation was accepted: ${name}`);
    }
  }
  console.log(`M3-10 profile freeze self-test PASS mutations=${mutations.length + 2 + (reviewedWorkflowSuccessor ? 3 : 0)}`);
}

verifyProductionSurface();
verifyTrackedDesign();
verifyDiff();
if (args.has("--self-test")) selfTest();
console.log(`M3-10 profile freeze PASS workflows=${reviewedWorkflowSuccessor ? "reviewed" : "absent"} productionObserver=absent`);
