#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import {
  ACTIVE_GOVERNANCE_POLICY_SURFACES,
  isActiveGovernancePolicySurface,
} from "./active-governance-policy-surfaces.mjs";

const root = process.cwd();
const args = process.argv.slice(2);
const selfTest = args.includes("--self-test");
const baseRefIndex = args.indexOf("--base-ref");
const baseRef = baseRefIndex >= 0 ? args[baseRefIndex + 1] : null;
const allowedArgs = new Set(["--self-test", "--base-ref"]);
for (let index = 0; index < args.length; index += 1) {
  const arg = args[index];
  if (arg === "--base-ref") {
    index += 1;
    if (!args[index] || args[index].startsWith("--")) fail("--base-ref requires a Git revision");
  } else if (!allowedArgs.has(arg)) {
    fail(`unknown argument: ${arg}`);
  }
}

const terminalV01Commit = "7c838d7051e8eedb1607e57b6e81e7a6f3db4523";
const oldV01Tuple = "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd";
const candidateLockPath = "docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json";
const identityPolicyPath = "docs/v0.2/identity-path-policy-v1.json";
const postFreezePolicyPath = "docs/v0.2/post-freeze-path-policy-v1.json";
const machinePolicyHashes = new Map([
  [identityPolicyPath, "874d788d45aaa051ca5695aeac8d5f693bebde0cba4a4e026312d1ee5cc0e58b"],
  [postFreezePolicyPath, "8cc5fae5da1de7c8be07deaea27817e469887035f23a8ff8130881a277b14329"],
]);
const expectedTuple = {
  schema_version: 1,
  tuple_kind: "development_baseline",
  release_line: "v0.2",
  target_version: "0.2.0",
  source_version: "0.1.0-dev",
  releasable: false,
  terminal_v0_1_commit: terminalV01Commit,
  predecessor_product_tuple_sha256: oldV01Tuple,
  source_trees: {
    root: "1f8a4c387b4715443df31457fa498c7463b1f23f",
    host: "0785d697f3e406219462c1cd3e14fbcd77d46cca",
    runtime: "38c07c0954127fb60f990e03974b2f457ab646bf",
    fixtures: "8d23ea087ce17f0dd935acc5d0516ea1270e69fd",
    integration_tests: "cf54d499a239fc79727120286cde84b2eb1e6288",
    tools_validation: "c130a3f03ec4eaafdbc421524433570f73e2deb7",
    benchmarks: "c8bdf317566f151c942da932a4e2bb514044354e",
    distribution: "9330b45cdd46cccf803e80f24e3dcf62411887c8",
  },
  contracts: {
    ahdc: 2,
    config_v2: 2,
    spv: 1,
    report: 1,
    min_sdk: 29,
    abis: ["armeabi-v7a", "arm64-v8a", "x86", "x86_64"],
  },
  evidence_policy: "fresh_release_evidence_required",
};

const tasks = [
  task("V2-M0-01", "V2-M0-01-product-baseline-version-isolation.md", 86, "V2-M0", "/root", [], ["plan-apk-hardening-change", "coordinate-project-handoff"], true),
  task("V2-M0-02", "V2-M0-02-versioned-candidate-baseline.md", 87, "V2-M0", "host-pipeline-agent", ["V2-M0-01"], ["plan-apk-hardening-change", "implement-apk-postprocessor", "validate-protected-apk"], true),
  task("V2-M3-01", "V2-M3-01-fresh-performance-harness.md", 88, "V2-M3", "qa-governance-agent", ["V2-M0-01"], ["plan-apk-hardening-change", "validate-protected-apk"], true),
  task("V2-M3-02", "V2-M3-02-product-tuple-freeze.md", 89, "V2-M3", "/root", ["V2-M0-02", "V2-M3-01"], ["plan-apk-hardening-change", "coordinate-project-handoff", "validate-protected-apk"], true),
  task("V2-M3-03", "V2-M3-03-size-startup-memory-gate.md", 90, "V2-M3", "qa-governance-agent", ["V2-M3-02"], ["validate-protected-apk", "coordinate-project-handoff"], true),
  task("V2-M3-04", "V2-M3-04-exact-tuple-release-validation.md", 91, "V2-M3", "qa-governance-agent", ["V2-M3-03"], ["validate-protected-apk", "plan-apk-hardening-change"], true),
  task("V2-M4-01", "V2-M4-01-security-supply-chain-review.md", 92, "V2-M4", "security-review-agent", ["V2-M3-03", "V2-M3-04"], ["validate-protected-apk", "plan-apk-hardening-change"], true),
  task("V2-M4-02", "V2-M4-02-reproducible-release-packaging.md", 93, "V2-M4", "host-pipeline-agent", ["V2-M4-01"], ["implement-apk-postprocessor", "validate-protected-apk", "plan-apk-hardening-change"], true),
  task("V2-M4-03", "V2-M4-03-release-evidence-decision.md", 94, "V2-M4", "qa-governance-agent", ["V2-M4-02"], ["coordinate-project-handoff", "validate-protected-apk"], true),
];

const expectedDocs = [
  "docs/adr/0020-v0-2-product-baseline-and-version-isolation.md",
  "docs/adr/0021-v0-2-maven-published-artifact-source-profiles.md",
  "docs/v0.2/README_FIRST.md",
  "docs/v0.2/PRODUCT_BASELINE.md",
  "docs/v0.2/PRODUCT_REQUIREMENTS.md",
  "docs/v0.2/ARCHITECTURE.md",
  "docs/v0.2/THREAT_MODEL.md",
  "docs/v0.2/EVIDENCE_REUSE_POLICY.md",
  "docs/v0.2/IDENTITY_MANIFESTS.md",
  identityPolicyPath,
  postFreezePolicyPath,
  "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md",
  "docs/v0.2/TEST_STRATEGY.md",
  "docs/v0.2/ROADMAP.md",
  "docs/v0.2/COMPATIBILITY_MATRIX.md",
  "docs/v0.2/development-product-tuple.json",
  "docs/v0.2/tasks/INDEX.md",
  ...tasks.map((entry) => `docs/v0.2/tasks/${entry.file}`),
];

const state = loadState();
const errors = validate(state);
if (baseRef) validateDiff(baseRef, errors);
if (errors.length > 0) {
  for (const error of errors) console.error(`ERROR: ${error}`);
  process.exit(1);
}

const mutationCount = selfTest ? runSelfTest(state) : 0;
console.log(`OK: v0.2 package (${tasks.length} tasks${selfTest ? `; ${mutationCount} mutations rejected` : ""})`);

function task(id, file, issue, milestone, owner, depends, skills, sensitive) {
  return { id, file, issue, milestone, owner, depends, skills, sensitive };
}

function loadState() {
  const files = new Map();
  for (const relative of [
    ...expectedDocs,
    ...ACTIVE_GOVERNANCE_POLICY_SURFACES,
    ".github/workflows/governance.yml",
    ".agents/skills/plan-apk-hardening-change/SKILL.md",
    ".agents/skills/implement-apk-postprocessor/SKILL.md",
    ".agents/skills/validate-protected-apk/SKILL.md",
    "docs/evidence/M3-15/terminal-disposition-lock.json",
    "docs/adr/0019-terminal-diagnostic-disposition.md",
    "docs/tasks/INDEX.md",
    "docs/tasks/M3-05-size-startup-memory-benchmarks.md",
    "docs/tasks/M3-15-terminal-diagnostic-disposition.md",
    "docs/tasks/M4-01-security-and-supply-chain-review.md",
    "docs/tasks/M4-02-cross-platform-release-packaging.md",
    "docs/tasks/M4-03-release-evidence-and-documentation.md",
    candidateLockPath,
  ]) {
    const absolute = path.join(root, relative);
    if (fs.existsSync(absolute)) files.set(relative, fs.readFileSync(absolute, "utf8"));
  }
  return { files };
}

function validate(candidate) {
  const found = [];
  for (const relative of expectedDocs) {
    if (!candidate.files.has(relative)) found.push(`missing required v0.2 file: ${relative}`);
  }
  if (found.length > 0) return found;

  const tupleText = candidate.files.get("docs/v0.2/development-product-tuple.json");
  let tuple;
  try {
    tuple = JSON.parse(tupleText);
  } catch (error) {
    found.push(`development product tuple is invalid JSON: ${error.message}`);
    return found;
  }
  const tupleHash = tuple.tuple_sha256;
  const tuplePayload = structuredClone(tuple);
  delete tuplePayload.tuple_sha256;
  requireEqual(stable(tuplePayload), stable(expectedTuple), "development product tuple payload", found);
  requireMatch(tupleHash, /^[0-9a-f]{64}$/, "development product tuple hash", found);
  requireEqual(tupleHash, sha256(stable(tuplePayload)), "development product tuple canonical SHA-256", found);
  if (tupleHash === oldV01Tuple) found.push("development product tuple reuses the terminal v0.1 tuple");

  validateGitBaseline(tuplePayload, found);
  validateMachinePolicySpecs(candidate, found);
  validateTasks(candidate, found);
  validateHandoffTupleState(candidate, tupleHash, found);
  validateRequiredPhrases(candidate, tupleHash, found);
  validateDocuments(candidate, found);
  validateContradictions(candidate, found);
  validateLinks(candidate, found);
  return found;
}

function validateGitBaseline(tuple, found) {
  const commit = git(["cat-file", "-e", `${terminalV01Commit}^{commit}`]);
  if (commit.status !== 0) {
    found.push(`terminal v0.1 commit is unavailable: ${terminalV01Commit}`);
    return;
  }
  if (git(["merge-base", "--is-ancestor", terminalV01Commit, "HEAD"]).status !== 0) {
    found.push("terminal v0.1 commit must be an ancestor of HEAD");
  }
  const treeSpecs = {
    root: `${terminalV01Commit}^{tree}`,
    host: `${terminalV01Commit}:host`,
    runtime: `${terminalV01Commit}:runtime`,
    fixtures: `${terminalV01Commit}:fixtures`,
    integration_tests: `${terminalV01Commit}:integration-tests`,
    tools_validation: `${terminalV01Commit}:tools/validation`,
    benchmarks: `${terminalV01Commit}:benchmarks`,
    distribution: `${terminalV01Commit}:distribution`,
  };
  for (const [name, spec] of Object.entries(treeSpecs)) {
    const result = git(["rev-parse", spec]);
    if (result.status !== 0) found.push(`cannot resolve baseline tree: ${name}`);
    else requireEqual(result.stdout.trim(), tuple.source_trees[name], `baseline tree ${name}`, found);
  }
}

function validateMachinePolicySpecs(candidate, found) {
  const parsed = new Map();
  for (const [relative, expectedHash] of machinePolicyHashes) {
    const raw = candidate.files.get(relative);
    if (!raw) {
      found.push(`missing machine policy: ${relative}`);
      continue;
    }
    let value;
    try {
      value = JSON.parse(raw);
    } catch (error) {
      found.push(`${relative}: invalid JSON: ${error.message}`);
      continue;
    }
    requireEqual(raw, `${JSON.stringify(value, null, 2)}\n`, `${relative} canonical JSON bytes`, found);
    requireEqual(sha256(raw), expectedHash, `${relative} frozen SHA-256`, found);
    parsed.set(relative, value);
  }

  const identity = parsed.get(identityPolicyPath);
  if (identity) {
    requireEqual(
      Object.keys(identity).join("|"),
      "schemaVersion|releaseLine|pathEncoding|sortOrder|allowedModes|roleEnum|canonicalPaths|manifestPolicies|resolutionRules",
      `${identityPolicyPath} field order`,
      found,
    );
    requireEqual(identity.schemaVersion, 1, `${identityPolicyPath} schemaVersion`, found);
    requireEqual(identity.releaseLine, "v0.2", `${identityPolicyPath} releaseLine`, found);
    requireEqual(identity.pathEncoding, "repository-relative-posix-utf8-nfc", `${identityPolicyPath} pathEncoding`, found);
    requireEqual(identity.sortOrder, "unsigned-utf8-bytes", `${identityPolicyPath} sortOrder`, found);
    requireEqual(identity.allowedModes?.join("|"), "100644|100755", `${identityPolicyPath} allowedModes`, found);
    requireEqual(
      identity.manifestPolicies?.map((entry) => entry.manifestKind).join("|"),
      "implementation|toolchain|product-contract|fixture-source|validation|performance-contract|release-gate-contract",
      `${identityPolicyPath} manifest order`,
      found,
    );
    requireEqual(
      Object.values(identity.canonicalPaths?.workflowCandidates || {}).join("|"),
      ["v02-performance.yml", "v02-release-validation.yml", "v02-security-review.yml", "v02-release-packaging.yml", "v02-release-evidence.yml"]
        .map((name) => `tools/validation/workflows/${name}`).join("|"),
      `${identityPolicyPath} workflow candidate paths`,
      found,
    );
    requireEqual(
      Object.values(identity.canonicalPaths?.liveWorkflows || {}).join("|"),
      ["v02-performance.yml", "v02-release-validation.yml", "v02-security-review.yml", "v02-release-packaging.yml", "v02-release-evidence.yml"]
        .map((name) => `.github/workflows/${name}`).join("|"),
      `${identityPolicyPath} live workflow paths`,
      found,
    );
    requireEqual(identity.canonicalPaths?.candidateVerifier, "tools/governance/verify-v02-product-tuple.mjs", `${identityPolicyPath} candidate verifier`, found);
    requireEqual(identity.canonicalPaths?.postFreezeVerifier, "tools/governance/verify-v02-post-freeze-head.mjs", `${identityPolicyPath} post-freeze verifier`, found);
    requireEqual(identity.canonicalPaths?.componentBaseline, "docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json", `${identityPolicyPath} component baseline`, found);
    requireEqual(
      Object.entries(identity.canonicalPaths?.artifactManifestSchemas || {}).map(([key, value]) => `${key}=${value}`).join("|"),
      [
        "performance=tools/validation/schemas/v02-performance-artifact-manifest-v1.schema.json",
        "release-validation=tools/validation/schemas/v02-release-validation-artifact-manifest-v1.schema.json",
        "security=tools/security-review-v02/schemas/security-artifact-manifest-v1.schema.json",
        "release-packaging=tools/release-evidence-v02/schemas/release-packaging-artifact-manifest-v1.schema.json",
        "release-evidence=tools/release-evidence-v02/schemas/release-evidence-artifact-manifest-v1.schema.json",
      ].join("|"),
      `${identityPolicyPath} artifact manifest schema paths`,
      found,
    );
    requireEqual(
      Object.entries(identity.canonicalPaths?.artifactManifestValidators || {}).map(([key, value]) => `${key}=${value}`).join("|"),
      [
        "release-packaging=tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs",
        "release-evidence=tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs",
      ].join("|"),
      `${identityPolicyPath} artifact manifest validator paths`,
      found,
    );
    const rawManifestReleasePaths = [
      "tools/release-evidence-v02/schemas/release-evidence-artifact-manifest-v1.schema.json",
      "tools/release-evidence-v02/schemas/release-packaging-artifact-manifest-v1.schema.json",
      "tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs",
      "tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs",
    ];
    const validationPolicy = identity.manifestPolicies?.find((entry) => entry.manifestKind === "validation");
    const releaseGatePolicy = identity.manifestPolicies?.find((entry) => entry.manifestKind === "release-gate-contract");
    for (const relative of rawManifestReleasePaths) {
      const validationSelected = (validationPolicy?.exactPaths || []).includes(relative)
        || (validationPolicy?.recursivePrefixes || []).some((prefix) => relative.startsWith(prefix));
      if (!validationSelected) found.push(`${identityPolicyPath}: validation manifest does not select ${relative}`);
      if (!(releaseGatePolicy?.exactPaths || []).includes(relative)) {
        found.push(`${identityPolicyPath}: release-gate-contract manifest does not select ${relative}`);
      }
    }
    requireEqual(new Set(identity.roleEnum || []).size, identity.roleEnum?.length, `${identityPolicyPath} unique roles`, found);
    for (const policy of identity.manifestPolicies || []) {
      for (const key of ["recursivePrefixes", "exactPaths"]) {
        validateSortedUniquePaths(policy[key], `${identityPolicyPath} ${policy.manifestKind}.${key}`, found);
      }
      if (!Array.isArray(policy.roleRules) || policy.roleRules.length === 0) {
        found.push(`${identityPolicyPath} ${policy.manifestKind}: roleRules must be non-empty`);
      }
    }
  }

  const postFreeze = parsed.get(postFreezePolicyPath);
  if (postFreeze) {
    requireEqual(
      Object.keys(postFreeze).join("|"),
      "schemaVersion|releaseLine|identityPolicyPath|validationFreezeLockPath|candidateLockPath|stageOrder|phaseEnum|phaseRules|frozenChecks|stages|evaluationRules",
      `${postFreezePolicyPath} field order`,
      found,
    );
    requireEqual(postFreeze.schemaVersion, 1, `${postFreezePolicyPath} schemaVersion`, found);
    requireEqual(postFreeze.releaseLine, "v0.2", `${postFreezePolicyPath} releaseLine`, found);
    requireEqual(postFreeze.identityPolicyPath, identityPolicyPath, `${postFreezePolicyPath} identityPolicyPath`, found);
    const expectedStages = ["V2-M3-02", "V2-M3-03", "V2-M3-04", "V2-M4-01", "V2-M4-02", "V2-M4-03"];
    requireEqual(postFreeze.stageOrder?.join("|"), expectedStages.join("|"), `${postFreezePolicyPath} stage order`, found);
    requireEqual(postFreeze.phaseEnum?.join("|"), "pre-run|evidence-pr|post-merge", `${postFreezePolicyPath} phase enum`, found);
    requireEqual(postFreeze.stages?.map((entry) => entry.taskId).join("|"), expectedStages.join("|"), `${postFreezePolicyPath} stage records`, found);
    const expectedRequiredTrackedOutputs = new Map([
      ["V2-M3-02", [
        "docs/v0.2/evidence/V2-M0-02/implementation-freeze-ancestry-report.json",
        "docs/v0.2/evidence/V2-M0-02/implementation-freeze-changed-path-report.json",
        "docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json",
        "docs/v0.2/evidence/V2-M3-01/validation-freeze-ancestry-report.json",
        "docs/v0.2/evidence/V2-M3-01/validation-freeze-changed-path-report.json",
        "docs/v0.2/evidence/V2-M3-01/validation-freeze-lock.json",
        "docs/v0.2/evidence/V2-M3-02/pre-run-ledger.json",
        "docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json",
      ]],
      ["V2-M3-03", [
        "docs/v0.2/evidence/V2-M3-03/performance-run-lock.json",
        "docs/v0.2/evidence/V2-M3-03/v02-environment.json",
        "docs/v0.2/evidence/V2-M3-03/v02-performance-artifact-manifest.json",
        "docs/v0.2/evidence/V2-M3-03/v02-performance-gate.json",
        "docs/v0.2/evidence/V2-M3-03/v02-performance-results.json",
      ]],
      ["V2-M3-04", [
        "docs/v0.2/evidence/V2-M3-04/v02-compatibility-matrix.json",
        "docs/v0.2/evidence/V2-M3-04/v02-release-validation-artifact-manifest.json",
        "docs/v0.2/evidence/V2-M3-04/v02-release-validation-gate.json",
        "docs/v0.2/evidence/V2-M3-04/validation-run-lock.json",
      ]],
      ["V2-M4-01", [
        "docs/v0.2/evidence/V2-M4-01/bom-v0.2.0.cdx.json",
        "docs/v0.2/evidence/V2-M4-01/independent-review.md",
        "docs/v0.2/evidence/V2-M4-01/local-validation.md",
        "docs/v0.2/evidence/V2-M4-01/release-gate-v0.2.0.json",
        "docs/v0.2/evidence/V2-M4-01/security-run-lock.json",
        "docs/v0.2/evidence/V2-M4-01/v02-rc-component-manifest.json",
        "docs/v0.2/evidence/V2-M4-01/v02-security-artifact-manifest.json",
      ]],
      ["V2-M4-02", [
        "docs/v0.2/evidence/V2-M4-02/release-artifact-lock.json",
        "docs/v0.2/evidence/V2-M4-02/v02-release-artifact-manifest.json",
        "docs/v0.2/evidence/V2-M4-02/v02-release-packaging-gate.json",
      ]],
      ["V2-M4-03", [
        "docs/v0.2/evidence/V2-M4-03/v02-release-evidence-artifact-manifest.json",
        "docs/v0.2/releases/QUICKSTART.md",
        "docs/v0.2/releases/RELEASE_NOTES_v0.2.0.md",
        "docs/v0.2/releases/release-decision-v0.2.0.json",
        "docs/v0.2/releases/release-evidence-v0.2.0.json",
      ]],
    ]);
    for (const stage of postFreeze.stages || []) {
      validateSortedUniquePaths(stage.requiredTrackedOutputs, `${postFreezePolicyPath} ${stage.taskId}.requiredTrackedOutputs`, found);
      validateSortedUniquePaths(stage.allowedChangedPaths, `${postFreezePolicyPath} ${stage.taskId}.allowedChangedPaths`, found);
      requireEqual(
        (stage.requiredTrackedOutputs || []).join("|"),
        (expectedRequiredTrackedOutputs.get(stage.taskId) || []).join("|"),
        `${postFreezePolicyPath} ${stage.taskId} exact required tracked outputs`,
        found,
      );
      const missing = (stage.requiredTrackedOutputs || []).filter((entry) => !(stage.allowedChangedPaths || []).includes(entry));
      if (missing.length > 0) found.push(`${postFreezePolicyPath} ${stage.taskId}: required outputs absent from allowlist: ${missing.join(", ")}`);
    }
  }
}

function validateSortedUniquePaths(values, label, found) {
  if (!Array.isArray(values)) {
    found.push(`${label}: must be an array`);
    return;
  }
  const sorted = [...values].sort((left, right) => Buffer.compare(Buffer.from(left, "utf8"), Buffer.from(right, "utf8")));
  requireEqual(values.join("|"), sorted.join("|"), `${label} UTF-8 sort`, found);
  requireEqual(new Set(values).size, values.length, `${label} uniqueness`, found);
  for (const value of values) {
    if (typeof value !== "string" || value.length === 0 || value.includes("\\") || value.startsWith("/") || value.split("/").some((part) => part === "." || part === "..")) {
      found.push(`${label}: invalid repository-relative POSIX path: ${JSON.stringify(value)}`);
    }
  }
}

function validateTasks(candidate, found) {
  const taskDir = path.join(root, "docs", "v0.2", "tasks");
  const actual = fs.existsSync(taskDir)
    ? fs.readdirSync(taskDir).filter((name) => /^V2-M[0-4]-\d{2}-.+\.md$/.test(name)).sort()
    : [];
  const expected = tasks.map((entry) => entry.file).sort();
  requireEqual(actual.join("|"), expected.join("|"), "v0.2 task-card filename set", found);

  const index = candidate.files.get("docs/v0.2/tasks/INDEX.md");
  const graph = new Map();
  for (const entry of tasks) {
    const relative = `docs/v0.2/tasks/${entry.file}`;
    const text = candidate.files.get(relative);
    const frontmatter = parseTaskFrontmatter(text, relative, found);
    if (!frontmatter) continue;
    requireEqual(frontmatter.scalar.get("id"), entry.id, `${entry.id} id`, found);
    requireEqual(frontmatter.scalar.get("milestone"), entry.milestone, `${entry.id} milestone`, found);
    requireEqual(frontmatter.scalar.get("status"), "planned", `${entry.id} status`, found);
    requireEqual(frontmatter.scalar.get("owner_role"), entry.owner, `${entry.id} owner_role`, found);
    requireEqual(frontmatter.scalar.get("security_sensitive"), String(entry.sensitive), `${entry.id} security_sensitive`, found);
    requireEqual(frontmatter.list.get("depends_on")?.join("|") || "", entry.depends.join("|"), `${entry.id} depends_on`, found);
    requireEqual(frontmatter.list.get("baseline_inputs")?.join("|") || "", `main@${terminalV01Commit}`, `${entry.id} baseline_inputs`, found);
    requireEqual(frontmatter.list.get("required_skills")?.join("|") || "", entry.skills.join("|"), `${entry.id} required_skills`, found);
    graph.set(entry.id, frontmatter.list.get("depends_on") || []);
    for (const dependency of graph.get(entry.id)) {
      if (!/^V2-M[0-4]-\d{2}$/.test(dependency)) found.push(`${entry.id} has non-V2 dependency: ${dependency}`);
    }
    validateTaskHeadings(text, relative, found);
    const rowCount = [...index.matchAll(new RegExp(`^\\|\\s*${escapeRegExp(entry.id)}\\s*\\|`, "gm"))].length;
    requireEqual(rowCount, 1, `task index row ${entry.id}`, found);
    requireCount(index, `issues/${entry.issue})`, 1, `task index Issue #${entry.issue}`, found);
    requireCount(index, `](${entry.file})`, 1, `task index link ${entry.file}`, found);
  }
  for (const id of graph.keys()) visit(id, graph, new Set(), new Set(), found);
  requirePhrase(index, "V2-M0-01", "task index", found);
  requirePhrase(index, "V2-M3-03", "task index", found);
  requirePhrase(index, "V2-M4-01", "task index", found);
  requirePhrase(index, "不得重跑同 tuple", "task index", found);
}

function parseTaskFrontmatter(text, relative, found) {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
  if (!match) {
    found.push(`${relative}: missing YAML frontmatter`);
    return null;
  }
  const expectedKeys = ["id", "title", "milestone", "status", "owner_role", "depends_on", "baseline_inputs", "required_skills", "security_sensitive"];
  const scalar = new Map();
  const list = new Map();
  const keys = [];
  let activeList = null;
  for (const line of match[1].split(/\r?\n/)) {
    const key = line.match(/^([a-z_]+):(?:\s*(.*))?$/);
    if (key) {
      keys.push(key[1]);
      activeList = null;
      if (["depends_on", "baseline_inputs", "required_skills"].includes(key[1])) {
        activeList = key[1];
        list.set(activeList, key[2]?.trim() === "[]" ? [] : []);
      } else {
        scalar.set(key[1], (key[2] || "").trim().replace(/^["']|["']$/g, ""));
      }
      continue;
    }
    const item = line.match(/^\s{2}-\s+(.+?)\s*$/);
    if (item && activeList) list.get(activeList).push(item[1].replace(/^["']|["']$/g, ""));
    else if (line.trim()) found.push(`${relative}: invalid frontmatter line: ${line}`);
  }
  requireEqual(keys.join("|"), expectedKeys.join("|"), `${relative} frontmatter key order`, found);
  return { scalar, list };
}

function validateTaskHeadings(text, relative, found) {
  const headings = [
    "## Goal", "## Background", "## Inputs", "## Expected Outputs", "## In Scope", "## Out of Scope",
    "## Implementation Decisions", "## Public Interfaces", "## Security Constraints", "## Compatibility Requirements",
    "## Acceptance Criteria", "## Required Tests", "## Required Evidence", "## Likely Files",
    "## Dependencies and Blockers", "## Agent Handoff Requirements",
  ];
  let previous = -1;
  for (const heading of headings) {
    const matches = [...text.matchAll(new RegExp(`^${escapeRegExp(heading)}$`, "gm"))];
    if (matches.length !== 1) found.push(`${relative}: heading must occur once: ${heading}`);
    else if (matches[0].index <= previous) found.push(`${relative}: heading out of order: ${heading}`);
    else previous = matches[0].index;
  }
}

function validateRequiredPhrases(candidate, tupleHash, found) {
  const adr = candidate.files.get("docs/adr/0020-v0-2-product-baseline-and-version-isolation.md");
  for (const heading of ["## Status", "## Context", "## Decision", "## Consequences", "## Rejected Alternatives", "## Security Impact", "## Compatibility Impact", "## Verification"]) {
    requirePhrase(adr, heading, "ADR 0020", found);
  }
  for (const phrase of [terminalV01Commit, oldV01Tuple, "STOP_CURRENT_V0_1_RELEASE_LINE", "fresh", "V2-M3-03", "V2-M4-01"]) {
    requirePhrase(adr, phrase, "ADR 0020", found);
  }
  const baseline = candidate.files.get("docs/v0.2/PRODUCT_BASELINE.md");
  for (const phrase of [terminalV01Commit, tupleHash, "development_baseline", "releasable", "0.2.0"]) {
    requirePhrase(baseline, phrase, "v0.2 product baseline", found);
  }
  const requirements = candidate.files.get("docs/v0.2/PRODUCT_REQUIREMENTS.md");
  for (const phrase of ["独立 APK", "未签名", "minSdk >= 29", "armeabi-v7a", "x86_64", "不能绝对", "不保证输出小于输入"]) {
    requirePhrase(requirements, phrase, "v0.2 product requirements", found);
  }
  const policy = candidate.files.get("docs/v0.2/EVIDENCE_REUSE_POLICY.md");
  for (const phrase of ["源码/测试资产", "发布 PASS", "PR #63", "PR #83", "M3-05", "runAttempt=1", "不补样", "不得成为 v0.2 PASS"]) {
    requirePhrase(policy, phrase, "v0.2 evidence policy", found);
  }
  const manifests = candidate.files.get("docs/v0.2/IDENTITY_MANIFESTS.md");
  for (const phrase of [
    "implementationManifestSha256", "validationManifestSha256", "toolchainManifestSha256",
    "productContractManifestSha256", "fixtureSourceManifestSha256", "performanceContractSha256",
    "releaseGateContractSha256", "唯一 preimage", "100644", "100755", "symlink", "self-reference",
    "docs/v0.2/evidence/V2-M0-02/implementation-manifest.json",
    "docs/v0.2/evidence/V2-M3-01/validation-manifest.json",
    "UTF-8 无符号字节序严格递增", "archive-byte-contract-v1.json", "Temurin `17.0.19+10`", "V2-M3-02 只能读取",
  ]) requirePhrase(manifests, phrase, "v0.2 identity manifests", found);
  const supplyChain = candidate.files.get("docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md");
  for (const phrase of [
    "org.cyclonedx.bom` `3.4.1", "CycloneDX CLI `0.33.1`", "OSV-Scanner `2.5.1`",
    "61666c361da1164737c7a2f3faad88ba879c5090c3b3bdd4cd878eda153bd1b2",
    "9b360974c41a5d612eb026fb5e3bb1fa28e08865f7a4f915665b8bc94bd3bc31",
    "f9f25499a2c8cc367b3af45df2ea7eeca7fbccceab9c35079968f4b3652194be",
    "specVersion=1.6", "--input-version v1_6", "single offline DB snapshot", "databaseTreeSha256",
    "--offline --no-resolve --all-packages", "scan 阶段禁止 `--download-offline-databases`",
    "0..86400", "UNKNOWN", "BLOCKED", "禁止在线 fallback",
    "bom-v0.2.0.raw.cdx.json", "/metadata/timestamp", "semantic diff path 恰好是 `/metadata/timestamp`", "RFC 8785", "raw bytes 不得被称为可重现 SBOM",
    "osv-package", "native-vendor-advisory", "first-party-source", "tool-binary",
    "offline mode 不支持 commit-level scanning", "Mbed-TLS/mbedtls-docs", "TF-PSA-Crypto", "false-zero",
  ]) requirePhrase(supplyChain, phrase, "v0.2 supply-chain tool contract", found);
  const roadmap = candidate.files.get("docs/v0.2/ROADMAP.md");
  for (const phrase of ["V2-M0-01", "V2-M3-03", "V2-M4-03", "V2-M0-02 + V2-M3-01", "V2-M0-02 merge/post-merge PASS", "V2-M3-01 refresh on that main", "STOP_CURRENT_V0_1_RELEASE_LINE"]) {
    requirePhrase(roadmap, phrase, "v0.2 roadmap", found);
  }
  const m301 = candidate.files.get("docs/v0.2/tasks/V2-M3-01-fresh-performance-harness.md");
  for (const phrase of ["outputUnsignedApkBytes = inputApkBytes", "removedOriginalDexDataDeltaBytes < 0", "输入含 v1 signature entries", "输入含 v2/v3 APK Signing Block", "输出 v1 entries 与 signing block 都必须为 0", "fourAbiRuntimeBaselineBytes` 只作信息性基准", "zipStructureDeltaBytes` 只能由已解析结构 interval", "validationManifestSha256", "releaseGateContractSha256", "performance-run-lock.json", "validation-run-lock.json", "securityReviewV02", "v02-release-validation.yml", "release-packaging-artifact-manifest-v1.schema.json", "release-evidence-artifact-manifest-v1.schema.json", "verify-release-packaging-artifact-manifest.mjs", "verify-release-evidence-artifact-manifest.mjs", "win32-job-getprocessmemoryinfo-v1", "cgroup v2", "dumpsys meminfo --checkin", "abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10", "独立的 100 MiB 合成 Host 输入 case"]) {
    requirePhrase(m301, phrase, "V2-M3-01", found);
  }
  const m002 = candidate.files.get("docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md");
  for (const phrase of ["distribution packager", "archive-internal Quickstart", "新 distribution source 的唯一 allowlist", "implementation-manifest.json", "toolchain-manifest.json", "product-contract-manifest.json", "symlink", "self-reference", "V2-M4-01 后不得再新增", "archive-byte-contract-v1.json", "ZIP32（ZIP64 拒绝）", "version-made-by `0x0314`", "version-needed `20`", "general-purpose flags `0x0800`", "method `8`", "Deflater level `9`", "data descriptor absent", "extra/comment/archive-comment empty", "UTC DOS timestamp 向下取偶数秒", "POSIX `ustar`", "uid/gid `0`", "uname/gname empty", "无 PAX/GNU/sparse/hardlink/symlink", "`CM=8`、`FLG=0`", "`MTIME=freeze epoch`", "`XFL=2`、`OS=3`", "无 extra/name/comment/header-CRC", "Eclipse Temurin `17.0.19+10`", "canonicalize-cyclonedx-v02.mjs"]) {
    requirePhrase(m002, phrase, "V2-M0-02", found);
  }
  const m302 = candidate.files.get("docs/v0.2/tasks/V2-M3-02-product-tuple-freeze.md");
  for (const phrase of ["V2-M0-02 先 merge/post-merge PASS", "validationManifestSha256", "七个 manifest", "v02-release-validation.yml", "v02-security-review.yml", "v02-release-packaging.yml", "v02-release-evidence.yml", "canonical workflow 的唯一事件是无 `inputs` 的 `workflow_dispatch`", "refs/heads/main", "concurrency.group=v02-performance-v0.2.0-rc.1", "cancel-in-progress=false", "V2-M3-02 只发布并验证 workflow，不 dispatch"]) {
    requirePhrase(m302, phrase, "V2-M3-02", found);
  }
  const m303 = candidate.files.get("docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md");
  for (const phrase of ["只有 `/root` 可以在确认 V2-M3-02 exact-head 与 post-merge PASS 后执行一次 `gh workflow run v02-performance.yml --ref main`", "不得提供 `-f` input、caller artifact 或替代 ref", "工作 Agent 不自行 dispatch", "outputUnsignedApkBytes = inputApkBytes", "输入有 v1 signature entries", "输入有 v2/v3 APK Signing Block", "输出 v1 entries 与 signing block 都为 0", "zipStructureDeltaBytes` 是", "不能是 remainder", "fourAbiRuntimeBaselineBytes` 仅单独报告", "abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10", "100 MiB Host case", "performance-run-lock.json", "docs/v0.2/evidence/V2-M3-03/v02-performance-gate.json", "build-only", "Windows Job aggregate", "Android `dumpsys meminfo --checkin`"]) {
    requirePhrase(m303, phrase, "V2-M3-03", found);
  }
  requireCount(m303, "gh workflow run v02-performance.yml --ref main", 1, "V2-M3-03 canonical dispatch command", found);
  validateCanonicalWorkflowTaskSemantics(m302, m303, found);
  validateFrozenDownstreamSemantics(candidate, found);
  const compatibility = candidate.files.get("docs/v0.2/COMPATIBILITY_MATRIX.md");
  for (const phrase of ["API 29", "API 36", "armeabi-v7a", "arm64-v8a", "x86_64", "UNVERIFIED", "ARM-only"]) {
    requirePhrase(compatibility, phrase, "v0.2 compatibility matrix", found);
  }
  const workflow = candidate.files.get(".github/workflows/governance.yml");
  for (const phrase of [
    "node tools/governance/verify-m3-15-terminal-disposition-contract.mjs",
    "node tools/governance/verify-m3-15-terminal-disposition-contract.mjs --self-test",
    "node tools/governance/validate-v0-2-package.mjs",
    "node tools/governance/validate-v0-2-package.mjs --self-test",
    "node --check tools/governance/active-governance-policy-surfaces.mjs",
    "fetch-depth: 0",
    "introduces_v02=false",
    "active_task: V2-M0-01",
    "if [ \"$V2_M0_01_HEAD_REF\" != \"docs/v2-m0-01-versioned-baseline\" ]",
    "V2-M0-01 must use docs/v2-m0-01-versioned-baseline",
    "node tools/governance/validate-v0-2-package.mjs --base-ref",
  ]) requirePhrase(workflow, phrase, "Governance workflow", found);
  const handoff = candidate.files.get("HandOff.md");
  for (const phrase of ["schema_version: 2", "release_line: v0.2", "STOP_CURRENT_V0_1_RELEASE_LINE", oldV01Tuple, "M3-05 PR #63 remains blocked", "ADR 0019 has since closed it unmerged", "Schema 2 HandOff uses the exact development tuple until V2-M3-02 atomically commits a verified RC lock"]) {
    requirePhrase(handoff, phrase, "HandOff", found);
  }
  const handoffSpec = candidate.files.get("docs/HANDOFF_SPEC.md");
  for (const phrase of ["Schema 2 tuple 状态机只有两个状态", "development", "candidate", candidateLockPath, "任意第三 tuple", "lock/hash 不匹配"]) {
    requirePhrase(handoffSpec, phrase, "HandOff spec", found);
  }
  const readme = candidate.files.get("README.md");
  for (const phrase of ["v0.2 规划线已经启动", "V2-M0-01", tupleHash, "tuple_kind=development_baseline", "releasable=false", "docs/v0.2/README_FIRST.md", "docs/v0.2/tasks/INDEX.md", "ADR 0020"]) {
    requirePhrase(readme, phrase, "README", found);
  }
  const planSkill = candidate.files.get(".agents/skills/plan-apk-hardening-change/SKILL.md");
  for (const phrase of ["HandOff.md", "release_line", "docs/v0.2/README_FIRST.md", "v0.1 and v0.2"]) {
    requirePhrase(planSkill, phrase, "plan-apk-hardening-change Skill", found);
  }
  const hostSkill = candidate.files.get(".agents/skills/implement-apk-postprocessor/SKILL.md");
  for (const phrase of ["active release line", "V2 task", "required_skills", "docs/v0.2/tasks/"]) {
    requirePhrase(hostSkill, phrase, "implement-apk-postprocessor Skill", found);
  }
  const validationSkill = candidate.files.get(".agents/skills/validate-protected-apk/SKILL.md");
  for (const phrase of ["release_line", "verify the exact `baseline_inputs` Git commit instead of adding M1-06 to `depends_on`", "full-flow", "docs/v0.2/tasks/"]) {
    requirePhrase(validationSkill, phrase, "validate-protected-apk Skill", found);
  }
}

function validateHandoffTupleState(candidate, developmentTuple, found) {
  const handoff = candidate.files.get("HandOff.md");
  const handoffTuple = handoff?.match(/^product_tuple_sha256:\s*([0-9a-f]{64})\s*$/m)?.[1];
  if (!handoffTuple) {
    found.push("HandOff: missing or invalid schema 2 product_tuple_sha256");
    return;
  }
  const lockText = candidate.files.get(candidateLockPath);
  if (!lockText) {
    requireEqual(handoffTuple, developmentTuple, "HandOff pre-candidate development tuple", found);
    return;
  }
  found.push(
    `${candidateLockPath}: V2-M0-01 is a pre-candidate gate and rejects every candidate lock. `
    + "V2-M3-01 must first freeze the exact aggregate verifier and candidate-state adapters; "
    + "a self-declared VERIFIED lock is never sufficient.",
  );
}

function validateDocuments(candidate, found) {
  for (const [relative, text] of candidate.files) {
    if (!isV02PolicySurface(relative)) continue;
    if (text.includes("\uFFFD")) found.push(`${relative}: Unicode replacement character is forbidden`);
    if (/\b(?:TODO|TBD)\b/i.test(text)) found.push(`${relative}: TODO/TBD placeholders are forbidden`);
    if (/(?:[A-Za-z]:[\\/](?:Users|Documents|works)[\\/]|\/(?:Users|home)\/[^/\s]+)/.test(text)) found.push(`${relative}: user absolute path is forbidden`);
    if (/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(text)) found.push(`${relative}: private key material is forbidden`);
    if (/(?:github_pat_|gh[pousr]_)[A-Za-z0-9_]{20,}/.test(text)) found.push(`${relative}: token-like content is forbidden`);
  }
}

function validateContradictions(candidate, found) {
  const texts = [...candidate.files]
    .filter(([relative]) => isV02PolicySurface(relative))
    .map(([relative, text]) => [relative, text]);
  const forbidden = [
    ["old task route authorization", /(?:M3-05|M3-10|M3-14|PR\s*#63|PR\s*#83).{0,100}(?:retry|rerun|replacement|renewal|resume|unblock|platform[- ]?(?:substitution|replacement)).{0,40}(?:is\s+)?(?:allowed|permitted|authorized|may|can)/isu],
    ["old task modal route authorization", /(?:M3-05|M3-10|M3-14|PR\s*#63|PR\s*#83).{0,64}(?:(?:\bmay\b|\bcan\b)(?:\s+be)?|is\s+(?:authorized|allowed|permitted)(?:\s+to)?|authorized(?:\s+to)?)\s+(?:retry|retried|rerun|resume|unblock|replace|renew|platform[- ]?(?:substitution|replacement))/isu],
    ["old task route authorization in Chinese", /(?:M3-05|M3-10|M3-14|PR\s*#63|PR\s*#83).{0,80}(?:可重试|可重跑|可替换|可续期|可恢复|可解除阻塞|允许恢复|允许解除阻塞|可换平台|允许平台替代|允许平台替换)/su],
    ["old task modal route authorization in Chinese", /(?:M3-05|M3-10|M3-14|PR\s*#63|PR\s*#83).{0,32}(?:可以|允许|授权|(?<!不)可)(?:进行|对其)?(?:重试|重跑|替换|续期|恢复|解除阻塞|换平台|平台替代|平台替换)/su],
    ["old task route authorization in Chinese reversed", /(?:允许|授权|可以)(?:(?!不|未|禁止|不得|不可|不能).){0,24}(?:(?:重试|重跑|替换|续期|恢复|解除阻塞|换平台|平台替代|平台替换)(?:(?!不|未|禁止|不得|不可|不能).){0,60}(?:M3-05|M3-10|M3-14|PR\s*#63|PR\s*#83)|(?:M3-05|M3-10|M3-14|PR\s*#63|PR\s*#83)(?:(?!不|未|禁止|不得|不可|不能).){0,60}(?:重试|重跑|替换|续期|恢复|解除阻塞|换平台|平台替代|平台替换))/su],
    ["old M4 start authorization", /(?:v0\.1|旧).{0,40}M4.{0,60}(?:is\s+startable|may\s+start|can\s+start|允许启动|可以启动)/isu],
    ["historical evidence promoted to PASS", /(?:v0\.1|(?<!no )(?<!not )old|(?<!no )(?<!not )historical|(?<!no )(?<!not )predecessor|历史|旧).{0,80}(?:evidence|tuple|run|artifact|证据|运行|产物).{0,60}(?:may\s+satisfy|can\s+satisfy|reusable\s+as|可作为|可满足).{0,30}(?:v0\.2\s+)?PASS/isu],
    ["AAB support expansion", /(?:AAB|APKS|split APK).{0,40}(?:is\s+supported|are\s+supported|受支持|支持输入)/isu],
    ["lower minSdk support expansion", /minSdk.{0,20}(?:<|below|低于)\s*29.{0,40}(?:supported|accepted|受支持|接受)/isu],
    ["product signing expansion", /(?:(?:product|tool)\s+(?:signs?\s+(?:the\s+)?APK|accepts?\s+(?:a\s+)?keystore)|(?:产品|工具)(?:(?!不|未|禁止|不得).){0,20}(?:签名\s*APK|接受\s*keystore))/isu],
    ["four-ABI informational baseline double count", /fourAbiRuntimeBaselineBytes.{0,40}(?:(?<!不)(?<!未)(?<!得)(?<!重复)计入(?:实际输出|单 ABI)|(?<!not )(?:included in actual output|added to actual output))/isu],
    ["A\/B wrong denominator", /max\(1,\s*max\(abs\(A\),\s*abs\(B\)\)\)/u],
    ["absolute-defense claim", /(?:(?:provides?|guarantees?|achieves?)\s+(?:unbreakable|cannot\s+be\s+(?:dumped|cracked|hooked))|(?:提供|实现|保证|达到).{0,8}(?:绝对防护|无法破解|无法截取))/isu],
  ];
  for (const [relative, text] of texts) {
    for (const [label, pattern] of forbidden) {
      if (pattern.test(text)) found.push(`${relative}: contradictory v0.2 claim: ${label}`);
    }
  }
}

function validateCanonicalWorkflowTaskSemantics(m302, m303, found) {
  const m302Forbidden = [
    ["alternate canonical trigger authorization", /canonical workflow.{0,100}(?:also\s+accepts|accepts|allows|uses|may\s+use|can\s+use|也接受|允许|可以|使用).{0,40}(?:push|pull_request|schedule|repository_dispatch|workflow_call|inputs?)/isu],
    ["alternate canonical trigger authorization reversed", /(?:push|pull_request|schedule|repository_dispatch|workflow_call|inputs?).{0,40}(?:is|are)?\s*(?:allowed|permitted|authorized)|(?:允许|授权|可以).{0,24}(?:push|pull_request|schedule|repository_dispatch|workflow_call|inputs?)/isu],
    ["freeze task dispatch authorization", /V2-M3-02.{0,60}(?:may|can|is\s+(?:allowed|authorized|permitted)\s+to|允许|可以).{0,16}dispatch/isu],
    ["cancel-in-progress true authorization", /canonical workflow.{0,80}(?:uses|sets|requires|使用|设置).{0,24}cancel-in-progress=true/isu],
    ["alternate canonical ref authorization", /canonical workflow.{0,80}(?:allows|accepts|may\s+use|can\s+use|允许|接受|可以使用).{0,30}(?:alternate|release|non-main|替代|非 main).{0,12}ref/isu],
  ];
  const m303Forbidden = [
    ["worker dispatch authorization", /(?:worker|working agent|work agent|工作 Agent).{0,40}(?:may|can|is\s+(?:allowed|authorized|permitted)\s+to|允许|可以).{0,16}dispatch/isu],
    ["second dispatch authorization", /(?:second|additional|another|第二次|额外|再次).{0,24}(?:dispatch|run|运行).{0,24}(?:is\s+)?(?:allowed|permitted|authorized|may|can|允许|可以)/isu],
    ["second dispatch authorization reversed", /(?:allowed|permitted|authorized|may|can|允许|可以).{0,24}(?:second|additional|another|第二次|额外|再次).{0,24}(?:dispatch|run|运行)/isu],
    ["canonical input authorization", /(?:-f|caller\s+input|workflow\s+input|外部输入|调用参数).{0,30}(?:is|are)?\s*(?:allowed|permitted|authorized)|(?:允许|授权|可以).{0,24}(?:-f|caller\s+input|workflow\s+input|外部输入|调用参数)/isu],
    ["alternate ref authorization", /(?:alternate|release|non-main|替代|非 main).{0,16}ref.{0,24}(?:is\s+)?(?:allowed|permitted|authorized|may|can|允许|可以)/isu],
    ["canonical rerun authorization", /V2-M3-03.{0,64}(?:rerun|retry|second\s+run).{0,24}(?:is\s+)?(?:allowed|permitted|authorized|may|can)/isu],
  ];
  for (const [label, pattern] of m302Forbidden) {
    if (pattern.test(m302)) found.push(`V2-M3-02 contradictory canonical workflow claim: ${label}`);
  }
  for (const [label, pattern] of m303Forbidden) {
    if (pattern.test(m303)) found.push(`V2-M3-03 contradictory canonical workflow claim: ${label}`);
  }
}

function validateFrozenDownstreamSemantics(candidate, found) {
  const m304 = candidate.files.get("docs/v0.2/tasks/V2-M3-04-exact-tuple-release-validation.md");
  const m401 = candidate.files.get("docs/v0.2/tasks/V2-M4-01-security-supply-chain-review.md");
  const m402 = candidate.files.get("docs/v0.2/tasks/V2-M4-02-reproducible-release-packaging.md");
  const m403 = candidate.files.get("docs/v0.2/tasks/V2-M4-03-release-evidence-decision.md");
  const manifests = candidate.files.get("docs/v0.2/IDENTITY_MANIFESTS.md");
  const supplyChain = candidate.files.get("docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md");

  for (const phrase of ["本任务只执行", "本任务无权编辑", "validationManifestSha256", "runAttempt=1", "失败不得 rerun", "docs/v0.2/evidence/V2-M3-03/{v02-performance-gate.json,v02-performance-artifact-manifest.json,performance-run-lock.json}", "validation-run-lock.json", "docs/v0.2/evidence/V2-M3-04/v02-release-validation-gate.json", "v02-release-validation-artifact-manifest.json", "v02-compatibility-matrix.json", "build-only"]) {
    requirePhrase(m304, phrase, "V2-M3-04 frozen execution", found);
  }
  for (const phrase of ["只执行候选前冻结的", "CycloneDX JSON 1.6", "single offline DB snapshot", "--offline --no-resolve --all-packages", "Critical/High/UNKNOWN", "security PASS 后再新增的发布 byte", "v02-sbom-canonicalization.json", "osv-package|native-vendor-advisory|first-party-source|tool-binary", "false-zero", "V2-M3-03/{v02-performance-gate.json,v02-performance-artifact-manifest.json,performance-run-lock.json}", "V2-M3-04/{v02-release-validation-gate.json,v02-compatibility-matrix.json,v02-release-validation-artifact-manifest.json,validation-run-lock.json}"]) {
    requirePhrase(m401, phrase, "V2-M4-01 frozen security review", found);
  }
  for (const phrase of ["只执行 exact reviewed bytes", "本任务只执行", "本任务无权编辑", "archive-internal Quickstart", "release-artifact-lock.json", "numeric ID", "runAttempt=1", "不得新增/修改 distribution", "archive-byte-contract-v1.json", "Temurin `17.0.19+10`", "ZIP32", "POSIX `ustar`", "CM=8,FLG=0,MTIME=freeze,XFL=2,OS=3", "docs/v0.2/evidence/V2-M4-01/{bom-v0.2.0.cdx.json,v02-rc-component-manifest.json,v02-security-artifact-manifest.json,release-gate-v0.2.0.json,security-run-lock.json}", "四个 `path,mode,blob,sizeBytes,sha256` 绑定", "tools/release-evidence-v02/schemas/release-packaging-artifact-manifest-v1.schema.json", "tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs", "artifactManifestSchemas.release-packaging", "artifactManifestValidators.release-packaging"]) {
    requirePhrase(m402, phrase, "V2-M4-02 frozen packaging", found);
  }
  for (const phrase of ["Archive 内 `distribution/docs/QUICKSTART.md`", "不可变 bytes", "位于 archive 外", "release-artifact-lock.json", "numeric run/artifact ID", "missing、expired、deleted", "本任务只执行，不编辑 gate", "Archive-internal Quickstart drift", "五个 required tracked outputs", "RELEASE_NOTES_v0.2.0.md", "docs/v0.2/releases/QUICKSTART.md", "tools/release-evidence-v02/schemas/release-evidence-artifact-manifest-v1.schema.json", "tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs", "artifactManifestSchemas.release-evidence", "artifactManifestValidators.release-evidence"]) {
    requirePhrase(m403, phrase, "V2-M4-03 Quickstart ownership", found);
  }

  const checks = [
    ["V2-M3-04", m304, /V2-M3-04.{0,80}(?:may|can|is allowed to|允许|可以).{0,32}(?:implement|modify|add|实现|修改|新增).{0,40}(?:validator|schema|workflow|验证器)/isu],
    ["V2-M4-01", m401, /V2-M4-01.{0,80}(?:may|can|is allowed to|允许|可以).{0,32}(?:change|upgrade|modify|修改|升级).{0,40}(?:tool|plugin|dependency|schema|validator|工具|依赖)/isu],
    ["V2-M4-02", m402, /V2-M4-02.{0,80}(?:may|can|is allowed to|允许|可以).{0,32}(?:implement|modify|add|实现|修改|新增).{0,40}(?:distribution|launcher|Quickstart|packager)/isu],
    ["V2-M4-03", m403, /V2-M4-03.{0,80}(?:may|can|is allowed to|允许|可以).{0,32}(?:modify|add|修改|新增).{0,40}(?:distribution|archive-internal|归档内)/isu],
    ["identity manifests", manifests, /(?:empty|arbitrary|alternate|ignored|空|任意|替代).{0,40}(?:manifest|preimage).{0,32}(?:is|are)?\s*(?:allowed|accepted|permitted|可接受|允许)/isu],
    ["identity manifests", manifests, /(?:symlink|gitlink|self-reference).{0,32}(?:is|are)?\s*(?:allowed|accepted|permitted)/isu],
    ["supply chain dynamic/fallback", supplyChain, /(?:dynamic|latest|floating|在线 fallback|online fallback).{0,40}(?:is|are)?\s*(?:allowed|accepted|permitted|允许|可接受)/isu],
    ["supply chain stale snapshot", supplyChain, /(?:stale|过期).{0,40}(?:database|snapshot|数据库|快照).{0,32}(?:is|are)?\s*(?:allowed|accepted|permitted|可接受|允许)/isu],
    ["supply chain unknown severity", supplyChain, /(?:^|\n)(?![^\n]*(?:不得|不能|禁止))[^\n]*(?:UNKNOWN|unknown severity|未知严重度)[^\n]*(?:treated as|downgraded to|视为|降为)[^\n]*(?:Low|None|低|无)/iu],
    ["supply chain per-platform snapshot", supplyChain, /(?:Windows|Ubuntu).{0,50}(?:separate|different|各自).{0,24}(?:database|snapshot|数据库|快照).{0,24}(?:allowed|permitted|允许)/isu],
    ["supply chain unsupported mapping", supplyChain, /(?:unsupported|unknown).{0,32}(?:ecosystem|mapping).{0,32}(?:may be|can be|is)?\s*(?:skipped|ignored|跳过|忽略)/isu],
    ["supply chain missing tool", supplyChain, /(?:missing|unavailable|缺失|不可用).{0,32}(?:tool|scanner|validator|工具).{0,32}(?:warning only|仅警告|continue|继续)/isu],
    ["supply chain CycloneDX signing", supplyChain, /CycloneDX CLI.{0,32}(?:sign|keygen).{0,32}(?:is|are)?\s*(?:allowed|permitted|允许)/isu],
    ["supply chain ownership", supplyChain, /V2-M3-01.{0,48}(?:may|can|is allowed to|允许|可以).{0,24}(?:modify|change|修改|变更).{0,24}(?:tool lock|plugin|verification metadata)/isu],
    ["SBOM canonicalization", supplyChain, /(?:canonicalizer|normalization|规范化).{0,72}(?:may|can|is allowed to|允许|可以).{0,32}(?:change|remove|reorder|修改|删除|重排).{0,40}(?:component|dependency|license|array|组件|依赖|数组)/isu],
    ["SBOM raw reproducibility", supplyChain, /(?:^|\n)(?![^\n]*(?:不得|not\s+reproducible))[^\n]*raw (?:SBOM|bytes)[^\n]*(?:are reproducible|is reproducible|可重现|字节一致)/iu],
    ["global OSV coverage", supplyChain, /(?:^|\n)(?![^\n]*(?:不能|不得|只证明))[^\n]*(?:OSV|scanner)[^\n]*(?:zero|0|零)[^\n]*(?:prove|proves|covers|means|证明|覆盖|表示)[^\n]*(?:all|every|全部|所有) component/iu],
    ["Native OSV coverage", supplyChain, /(?:commit-only|generic purl).{0,40}(?:is|are)?\s*(?:accepted|supported|covered|接受|支持|覆盖).{0,32}(?:OSV|scanner)/isu],
    ["first-party coverage escape", supplyChain, /(?:external|third-party|预编译第三方).{0,32}(?:may|can|允许|可以).{0,32}first-party-source/isu],
    ["size accounting", candidate.files.get("docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md"), /zipStructureDeltaBytes.{0,32}(?:may|can|is allowed to|允许|可以).{0,16}(?:be\s+)?(?:set as|use|设置为|使用).{0,16}remainder/isu],
    ["signature accounting", candidate.files.get("docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md"), /removedSignatureDataDeltaBytes.{0,40}(?:always|必须).{0,16}(?:negative|小于\s*0|为负)/isu],
    ["memory fallback", candidate.files.get("docs/v0.2/TEST_STRATEGY.md"), /(?:cgroup|Job Object|dumpsys).{0,56}(?:unavailable|missing|不可用|缺失).{0,24}(?:may|can|allowed|permitted|允许|可以).{0,16}(?:fallback|continue|降级|继续)/isu],
    ["parent-only memory accepted", candidate.files.get("docs/v0.2/TEST_STRATEGY.md"), /(?:parent-process-only|only\s+(?:the\s+)?parent|只测父进程).{0,40}(?:memory|RSS|内存).{0,24}(?:is\s+)?(?:accepted|allowed|permitted|接受|允许)/isu],
    ["sparse memory accepted", candidate.files.get("docs/v0.2/TEST_STRATEGY.md"), /(?:sparse|稀疏|end-only|结束时).{0,40}(?:sampl(?:e|ing)|采样).{0,24}(?:is\s+)?(?:accepted|allowed|permitted|接受|允许)/isu],
    ["release artifact flow", m402, /(?:second|alternate|第二|替代).{0,24}artifact.{0,24}(?:is|are)?\s*(?:allowed|permitted|允许)/isu],
    ["release artifact flow", m403, /(?:caller|URL|cache|alternate run|调用方|缓存|替代运行).{0,32}(?:artifact|archive|产物|归档).{0,24}(?:is|are)?\s*(?:allowed|permitted|允许)/isu],
    ["performance lock build-only", candidate.files.get("docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md"), /build-only.{0,32}(?:output|artifact|evidence).{0,24}(?:is\s+)?(?:accepted|allowed|permitted)/isu],
    ["validation lock build-only", m304, /build-only.{0,32}(?:output|artifact|evidence).{0,24}(?:is\s+)?(?:accepted|allowed|permitted)/isu],
    ["frozen notices", m401, /V2-M4-01.{0,48}(?:may|can|is allowed to|允许|可以).{0,24}(?:modify|edit|修改).{0,24}THIRD_PARTY_NOTICES/isu],
  ];
  for (const [label, text, pattern] of checks) {
    if (pattern.test(text)) found.push(`${label}: contradictory post-freeze or supply-chain authorization`);
  }
}

function isV02PolicySurface(relative) {
  return relative.startsWith("docs/v0.2/")
    || relative === "docs/adr/0020-v0-2-product-baseline-and-version-isolation.md"
    || isActiveGovernancePolicySurface(relative);
}

function validateLinks(candidate, found) {
  for (const [relative, text] of candidate.files) {
    if (!relative.startsWith("docs/v0.2/") && relative !== "docs/adr/0020-v0-2-product-baseline-and-version-isolation.md") continue;
    for (const match of text.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
      const target = match[1].split("#")[0];
      if (!target || /^(?:https?:|mailto:)/.test(target)) continue;
      const resolved = path.resolve(root, path.dirname(relative), decodeURIComponent(target));
      if (!fs.existsSync(resolved)) found.push(`${relative}: broken Markdown link: ${match[1]}`);
    }
  }
}

function validateDiff(revision, found) {
  const result = git(["diff", "--name-only", `${revision}...HEAD`]);
  if (result.status !== 0) {
    found.push(`cannot inspect base diff: ${result.stderr.trim()}`);
    return;
  }
  const exact = new Set([
    ".github/workflows/governance.yml", "README.md", "AGENTS.md", "HandOff.md", "docs/HANDOFF_SPEC.md",
    "docs/agents/COORDINATOR_AGENT.md", "docs/agents/WORKER_AGENT.md",
    "tools/governance/test-handoff-validator.mjs",
    "tools/governance/active-governance-policy-surfaces.mjs",
    "tools/governance/validate-v0-2-package.mjs",
    "tools/governance/verify-m3-13-diagnostic-identity-contract.mjs",
    "tools/governance/verify-m3-15-terminal-disposition-contract.mjs",
    ".agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs",
    ".agents/skills/coordinate-project-handoff/SKILL.md",
    ".agents/skills/coordinate-project-handoff/references/handoff-schema.md",
    ".agents/skills/coordinate-project-handoff/assets/worker-handoff-template.md",
    ".agents/skills/plan-apk-hardening-change/SKILL.md",
    ".agents/skills/implement-apk-postprocessor/SKILL.md",
    ".agents/skills/validate-protected-apk/SKILL.md",
  ]);
  for (const relative of result.stdout.split(/\r?\n/).filter(Boolean).map((value) => value.replaceAll("\\", "/"))) {
    if (!exact.has(relative) && relative !== "docs/adr/0020-v0-2-product-baseline-and-version-isolation.md" && !relative.startsWith("docs/v0.2/")) {
      found.push(`V2-M0-01 contains out-of-scope path: ${relative}`);
    }
    if (/^(?:host|runtime|fixtures|integration-tests|benchmarks|distribution|tools\/validation)\//.test(relative) || ["build.gradle.kts", "settings.gradle.kts", "gradle.properties"].includes(relative)) {
      found.push(`V2-M0-01 must not modify product or dynamic-validation path: ${relative}`);
    }
  }
}

function runSelfTest(original) {
  let count = 0;
  const tupleRelative = "docs/v0.2/development-product-tuple.json";
  const originalTuple = JSON.parse(original.files.get(tupleRelative));
  const originalPayload = structuredClone(originalTuple);
  delete originalPayload.tuple_sha256;
  for (const [pathParts, value] of leafEntries(originalPayload)) {
    const changed = cloneState(original);
    const payload = structuredClone(originalPayload);
    setPath(payload, pathParts, alternate(value));
    writeTuple(changed, tupleRelative, payload);
    if (validate(changed).length === 0) fail(`self-test tuple mutation accepted: ${pathParts.join(".")}`);
    count += 1;
  }

  const extraField = cloneState(original);
  const extraPayload = structuredClone(originalPayload);
  extraPayload.unexpected = "forbidden";
  writeTuple(extraField, tupleRelative, extraPayload);
  if (validate(extraField).length === 0) fail("self-test tuple mutation accepted: extra-field");
  count += 1;

  const reorderedTuple = cloneState(original);
  const reverseTopLevel = Object.fromEntries(Object.entries(originalPayload).reverse());
  reorderedTuple.files.set(
    tupleRelative,
    `${JSON.stringify({ ...reverseTopLevel, tuple_sha256: sha256(stable(reverseTopLevel)) }, null, 2)}\n`,
  );
  const reorderErrors = validate(reorderedTuple);
  if (reorderErrors.length > 0) fail(`canonical object-key reordering rejected: ${reorderErrors.join("; ")}`);

  const mutations = [
    ["reuse-old-tuple", (state) => mutateJson(state, "docs/v0.2/development-product-tuple.json", (value) => { value.tuple_sha256 = oldV01Tuple; })],
    ["wrong-baseline-tree", (state) => mutateJson(state, "docs/v0.2/development-product-tuple.json", (value) => { value.source_trees.host = "0".repeat(40); })],
    ["releasable-development-baseline", (state) => mutateJson(state, "docs/v0.2/development-product-tuple.json", (value) => { value.releasable = true; })],
    ["legacy-task-dependency", (state) => replace(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "  - V2-M3-02", "  - M3-05")],
    ["release-bypasses-performance", (state) => replace(state, "docs/v0.2/tasks/V2-M4-01-security-supply-chain-review.md", "  - V2-M3-03\n", "")],
    ["missing-task-index-issue", (state) => replace(state, "docs/v0.2/tasks/INDEX.md", "issues/94", "issues/999")],
    ["missing-adr-stop-decision", (state) => replace(state, "docs/adr/0020-v0-2-product-baseline-and-version-isolation.md", "STOP_CURRENT_V0_1_RELEASE_LINE", "REMOVED_DECISION")],
    ["old-pr-as-pass", (state) => replace(state, "docs/v0.2/EVIDENCE_REUSE_POLICY.md", "不得成为 v0.2 PASS", "可成为 v0.2 PASS")],
    ["explicit-old-run-pass", (state) => append(state, "docs/v0.2/EVIDENCE_REUSE_POLICY.md", "\nHistorical v0.1 evidence may satisfy v0.2 PASS.\n")],
    ["explicit-old-retry", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-05 retry is allowed.\n")],
    ["explicit-old-resume", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-05 unblock is authorized.\n")],
    ["task-first-modal-resume", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-05 may resume.\n")],
    ["task-first-modal-retry", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-14 may be retried.\n")],
    ["explicit-old-resume-chinese", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-05 可解除阻塞。\n")],
    ["task-first-modal-resume-chinese", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-05 可以恢复。\n")],
    ["task-first-authorized-resume-chinese", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-05 授权恢复。\n")],
    ["explicit-platform-substitution", (state) => append(state, "docs/v0.2/ROADMAP.md", "\nM3-05 platform substitution is allowed.\n")],
    ["explicit-platform-substitution-chinese", (state) => append(state, "docs/v0.2/ROADMAP.md", "\n允许对 M3-05 进行平台替代。\n")],
    ["handoff-spec-restores-v01", (state) => append(state, "docs/HANDOFF_SPEC.md", "\nM3-05 may resume.\n")],
    ["aab-scope-expansion", (state) => append(state, "docs/v0.2/PRODUCT_REQUIREMENTS.md", "\nAAB is supported input.\n")],
    ["absolute-defense-claim", (state) => append(state, "docs/v0.2/PRODUCT_REQUIREMENTS.md", "\n本产品提供绝对防护。\n")],
    ["missing-m3-15-workflow", (state) => replace(state, ".github/workflows/governance.yml", "node tools/governance/verify-m3-15-terminal-disposition-contract.mjs --self-test", "node --version")],
    ["missing-v02-self-test", (state) => replace(state, ".github/workflows/governance.yml", "node tools/governance/validate-v0-2-package.mjs --self-test", "node --version")],
    ["wrong-v02-branch-guard", (state) => replace(state, ".github/workflows/governance.yml", "docs/v2-m0-01-versioned-baseline", "docs/wrong-v2-branch")],
    ["stale-root-readme", (state) => replace(state, "README.md", "v0.2 规划线已经启动", "v0.2 尚未启动")],
    ["highest-rule-restores-v01", (state) => append(state, "AGENTS.md", "\nM3-05 retry is allowed.\n")],
    ["candidate-without-lock", (state) => replace(state, "HandOff.md", originalTuple.tuple_sha256, "b".repeat(64))],
    ["development-tuple-after-lock", (state) => { installSyntheticCandidateLock(state); }],
    ["self-certified-verified-candidate-lock", (state) => { const hash = installSyntheticCandidateLock(state); replace(state, "HandOff.md", originalTuple.tuple_sha256, hash); }],
    ["arbitrary-third-tuple-with-lock", (state) => { installSyntheticCandidateLock(state); replace(state, "HandOff.md", originalTuple.tuple_sha256, "c".repeat(64)); }],
    ["old-tuple-with-lock", (state) => { installSyntheticCandidateLock(state); replace(state, "HandOff.md", originalTuple.tuple_sha256, oldV01Tuple); }],
    ["unverified-candidate-lock", (state) => { installSyntheticCandidateLock(state); mutateJson(state, candidateLockPath, (value) => { value.verificationStatus = "UNVERIFIED"; }); }],
    ["candidate-lock-hash-mismatch", (state) => { const hash = installSyntheticCandidateLock(state); replace(state, "HandOff.md", originalTuple.tuple_sha256, hash); mutateJson(state, candidateLockPath, (value) => { value.productTupleSha256 = "d".repeat(64); }); }],
    ["malformed-candidate-lock", (state) => { state.files.set(candidateLockPath, "{not-json}\n"); }],
    ["unordered-freeze-merge", (state) => replace(state, "docs/v0.2/ROADMAP.md", "V2-M0-02 merge/post-merge PASS", "either freeze may merge first")],
    ["canonical-workflow-push-trigger", (state) => replace(state, "docs/v0.2/tasks/V2-M3-02-product-tuple-freeze.md", "workflow_dispatch", "push")],
    ["canonical-workflow-coexisting-triggers", (state) => append(state, "docs/v0.2/tasks/V2-M3-02-product-tuple-freeze.md", "\nThe canonical workflow also accepts push and workflow_call with inputs.\n")],
    ["freeze-task-dispatches", (state) => append(state, "docs/v0.2/tasks/V2-M3-02-product-tuple-freeze.md", "\nV2-M3-02 may dispatch the workflow.\n")],
    ["canonical-cancel-in-progress", (state) => append(state, "docs/v0.2/tasks/V2-M3-02-product-tuple-freeze.md", "\nThe canonical workflow uses cancel-in-progress=true.\n")],
    ["worker-replaces-root-dispatch", (state) => replace(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "只有 `/root` 可以在确认 V2-M3-02 exact-head 与 post-merge PASS 后执行一次", "只有 worker 可以在确认 V2-M3-02 exact-head 与 post-merge PASS 后执行一次")],
    ["worker-second-dispatch", (state) => append(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "\nA worker may dispatch a second run with -f on an alternate ref.\n")],
    ["v2-performance-rerun", (state) => append(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "\nV2-M3-03 rerun is allowed.\n")],
    ["size-double-count-contract", (state) => replace(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "fourAbiRuntimeBaselineBytes` 仅单独报告", "fourAbiRuntimeBaselineBytes` 计入实际输出")],
    ["ab-wrong-denominator", (state) => replace(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "max(1, min(abs(A), abs(B)))", "max(1, max(abs(A), abs(B)))")],
    ["missing-100mib-case", (state) => replace(state, "docs/v0.2/tasks/V2-M3-01-fresh-performance-harness.md", "独立的 100 MiB 合成 Host 输入 case", "optional large Host case")],
    ["manifest-alternate-preimage", (state) => replace(state, "docs/v0.2/IDENTITY_MANIFESTS.md", "docs/v0.2/evidence/V2-M3-01/validation-manifest.json", "build/alternate-validation-manifest.json")],
    ["manifest-empty-accepted", (state) => append(state, "docs/v0.2/IDENTITY_MANIFESTS.md", "\nAn empty manifest preimage is accepted.\n")],
    ["manifest-symlink-accepted", (state) => append(state, "docs/v0.2/IDENTITY_MANIFESTS.md", "\nA symlink is allowed in entries.\n")],
    ["identity-policy-role-drift", (state) => mutateJson(state, identityPolicyPath, (value) => { value.roleEnum.push("other"); })],
    ["identity-policy-workflow-path-drift", (state) => mutateJson(state, identityPolicyPath, (value) => { value.canonicalPaths.workflowCandidates.performance = "tools/validation/workflows/alternate.yml"; })],
    ["identity-policy-release-packaging-manifest-schema-drift", (state) => mutateJson(state, identityPolicyPath, (value) => { value.canonicalPaths.artifactManifestSchemas["release-packaging"] = "build/alternate-packaging-schema.json"; })],
    ["identity-policy-release-evidence-manifest-validator-drift", (state) => mutateJson(state, identityPolicyPath, (value) => { value.canonicalPaths.artifactManifestValidators["release-evidence"] = "build/alternate-evidence-validator.mjs"; })],
    ["identity-policy-release-gate-omits-packaging-manifest-schema", (state) => mutateJson(state, identityPolicyPath, (value) => {
      const policy = value.manifestPolicies.find((entry) => entry.manifestKind === "release-gate-contract");
      policy.exactPaths = policy.exactPaths.filter((entry) => entry !== "tools/release-evidence-v02/schemas/release-packaging-artifact-manifest-v1.schema.json");
    })],
    ["identity-policy-validation-omits-release-evidence-tools", (state) => mutateJson(state, identityPolicyPath, (value) => {
      const policy = value.manifestPolicies.find((entry) => entry.manifestKind === "validation");
      policy.recursivePrefixes = policy.recursivePrefixes.filter((entry) => entry !== "tools/release-evidence-v02/");
    })],
    ["post-freeze-stage-skip", (state) => mutateJson(state, postFreezePolicyPath, (value) => { value.stageOrder.splice(1, 1); })],
    ["post-freeze-unknown-path", (state) => mutateJson(state, postFreezePolicyPath, (value) => { value.stages[1].allowedChangedPaths.push("host/forbidden.kt"); })],
    ["post-freeze-m304-undefined-summary", (state) => mutateJson(state, postFreezePolicyPath, (value) => { value.stages[2].requiredTrackedOutputs.push("docs/v0.2/evidence/V2-M3-04/v02-release-validation-summary.json"); value.stages[2].requiredTrackedOutputs.sort(); })],
    ["post-freeze-m401-missing-independent-review", (state) => mutateJson(state, postFreezePolicyPath, (value) => { value.stages[3].requiredTrackedOutputs = value.stages[3].requiredTrackedOutputs.filter((entry) => !entry.endsWith("/independent-review.md")); })],
    ["post-freeze-m403-missing-quickstart", (state) => mutateJson(state, postFreezePolicyPath, (value) => { value.stages[5].requiredTrackedOutputs = value.stages[5].requiredTrackedOutputs.filter((entry) => !entry.endsWith("/QUICKSTART.md")); })],
    ["post-freeze-m403-missing-release-notes", (state) => mutateJson(state, postFreezePolicyPath, (value) => { value.stages[5].requiredTrackedOutputs = value.stages[5].requiredTrackedOutputs.filter((entry) => !entry.includes("RELEASE_NOTES")); })],
    ["m402-missing-security-artifact-input", (state) => replace(state, "docs/v0.2/tasks/V2-M4-02-reproducible-release-packaging.md", ",v02-security-artifact-manifest.json", "")],
    ["m402-four-blob-binding-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M4-02-reproducible-release-packaging.md", "四个 `path,mode,blob,sizeBytes,sha256` 绑定", "三个 `path,mode,blob,sizeBytes,sha256` 绑定")],
    ["m402-packaging-artifact-manifest-schema-drift", (state) => {
      const key = "docs/v0.2/tasks/V2-M4-02-reproducible-release-packaging.md";
      state.files.set(key, state.files.get(key).replaceAll("tools/release-evidence-v02/schemas/release-packaging-artifact-manifest-v1.schema.json", "build/alternate-release-packaging.schema.json"));
    }],
    ["m403-release-evidence-artifact-manifest-validator-drift", (state) => {
      const key = "docs/v0.2/tasks/V2-M4-03-release-evidence-decision.md";
      state.files.set(key, state.files.get(key).replaceAll("tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs", "build/alternate-release-evidence-validator.mjs"));
    }],
    ["distribution-allowlist-removed", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "新 distribution source 的唯一 allowlist", "distribution files chosen during implementation")],
    ["post-freeze-release-validator-implementation", (state) => append(state, "docs/v0.2/tasks/V2-M3-04-exact-tuple-release-validation.md", "\nV2-M3-04 may implement a new validator and workflow.\n")],
    ["post-freeze-security-tool-upgrade", (state) => append(state, "docs/v0.2/tasks/V2-M4-01-security-supply-chain-review.md", "\nV2-M4-01 may upgrade the scanner tool and validator.\n")],
    ["post-security-distribution-change", (state) => append(state, "docs/v0.2/tasks/V2-M4-02-reproducible-release-packaging.md", "\nV2-M4-02 may modify the distribution packager and Quickstart.\n")],
    ["post-package-quickstart-change", (state) => append(state, "docs/v0.2/tasks/V2-M4-03-release-evidence-decision.md", "\nV2-M4-03 may modify archive-internal distribution Quickstart.\n")],
    ["dynamic-supply-chain-tool", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nDynamic/latest tool versions are allowed.\n")],
    ["wrong-osv-asset-hash", (state) => replace(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "f9f25499a2c8cc367b3af45df2ea7eeca7fbccceab9c35079968f4b3652194be", "0".repeat(64))],
    ["sbom-schema-1-7", (state) => replace(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "specVersion=1.6", "specVersion=1.7")],
    ["online-osv-fallback", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nOnline fallback is allowed when the database is unavailable.\n")],
    ["scan-time-db-download", (state) => replace(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "scan 阶段禁止 `--download-offline-databases`", "scan 阶段允许 `--download-offline-databases`")],
    ["stale-db-accepted", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nA stale database snapshot is accepted.\n")],
    ["per-os-db-snapshots", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nWindows and Ubuntu separate database snapshots are allowed.\n")],
    ["unknown-severity-downgraded", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nUNKNOWN severity is treated as Low.\n")],
    ["unsupported-ecosystem-skipped", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nAn unsupported ecosystem mapping may be skipped.\n")],
    ["missing-scanner-warning-only", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nA missing scanner tool is warning only; continue.\n")],
    ["offline-scan-omits-coverage", (state) => replace(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "--offline --no-resolve --all-packages", "--offline --no-resolve")],
    ["cyclonedx-sign-enabled", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nCycloneDX CLI sign is allowed.\n")],
    ["supply-lock-owner-drift", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nV2-M3-01 may modify the tool lock and verification metadata.\n")],
    ["raw-sbom-called-reproducible", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nRaw SBOM bytes are reproducible across both platforms.\n")],
    ["canonicalizer-changes-components", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nThe canonicalizer may change component arrays.\n")],
    ["canonicalizer-missing-timestamp-rule", (state) => replace(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "semantic diff path 恰好是 `/metadata/timestamp`", "semantic diff may use arbitrary paths")],
    ["osv-zero-covers-all", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nOSV zero findings prove all components are covered.\n")],
    ["native-commit-osv-covered", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nA commit-only component is accepted as covered by OSV scanner.\n")],
    ["external-first-party-escape", (state) => append(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "\nAn external binary may use first-party-source coverage.\n")],
    ["false-zero-removed", (state) => replace(state, "docs/v0.2/SUPPLY_CHAIN_TOOLCHAIN.md", "必须拒绝 false-zero", "false zero is accepted")],
    ["size-remainder-authorized", (state) => append(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "\nzipStructureDeltaBytes may be set as remainder.\n")],
    ["signature-delta-always-negative", (state) => append(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "\nremovedSignatureDataDeltaBytes must always be negative.\n")],
    ["signature-output-residual", (state) => replace(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "输出 v1 entries 与 signing block 都为 0", "output signature state is not checked")],
    ["host-memory-parent-only", (state) => append(state, "docs/v0.2/TEST_STRATEGY.md", "\nParent-process-only memory sampling is accepted.\n")],
    ["host-memory-fallback", (state) => append(state, "docs/v0.2/TEST_STRATEGY.md", "\nWhen cgroup is unavailable it may fallback to sparse sampling.\n")],
    ["android-memory-sparse", (state) => append(state, "docs/v0.2/TEST_STRATEGY.md", "\nEnd-only sparse sampling is accepted.\n")],
    ["performance-run-lock-build-only", (state) => replace(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "只接受上述三个 tracked path；build-only", "build-only output is accepted;")],
    ["performance-run-lock-alternate", (state) => replace(state, "docs/v0.2/tasks/V2-M3-03-size-startup-memory-gate.md", "docs/v0.2/evidence/V2-M3-03/v02-performance-gate.json", "build/alternate/v02-performance-gate.json")],
    ["validation-run-lock-build-only", (state) => replace(state, "docs/v0.2/tasks/V2-M3-04-exact-tuple-release-validation.md", "build-only、alternate path", "build-only output is accepted; alternate path")],
    ["validation-run-lock-alternate", (state) => replace(state, "docs/v0.2/tasks/V2-M3-04-exact-tuple-release-validation.md", "docs/v0.2/evidence/V2-M3-04/v02-release-validation-gate.json", "build/alternate/v02-release-validation-gate.json")],
    ["archive-jre-vendor-drift", (state) => {
      const key = "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md";
      state.files.set(key, state.files.get(key).replaceAll("Eclipse Temurin `17.0.19+10`", "any JRE 17"));
    }],
    ["archive-zip64-enabled", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "ZIP32（ZIP64 拒绝）", "ZIP64 allowed")],
    ["archive-zip-method-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "method `8`", "method `0`")],
    ["archive-zip-level-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "Deflater level `9`", "Deflater level `6`")],
    ["archive-zip-flags-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "general-purpose flags `0x0800`", "general-purpose flags `0x0808`")],
    ["archive-zip-descriptor-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "data descriptor absent", "data descriptor present")],
    ["archive-zip-extra-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "extra/comment/archive-comment empty", "platform extra fields allowed")],
    ["archive-zip-rounding-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "UTC DOS timestamp 向下取偶数秒", "local time with millisecond precision")],
    ["archive-tar-format-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "POSIX `ustar`", "GNU tar")],
    ["archive-tar-owner-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "uid/gid `0`", "host uid/gid")],
    ["archive-tar-name-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "uname/gname empty", "uname/gname from host")],
    ["archive-tar-pax-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "无 PAX/GNU/sparse/hardlink/symlink", "PAX metadata allowed")],
    ["archive-gzip-flags-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "`CM=8`、`FLG=0`", "`CM=8`、`FLG=8`")],
    ["archive-gzip-mtime-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "`MTIME=freeze epoch`", "`MTIME=build time`")],
    ["archive-gzip-os-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "`XFL=2`、`OS=3`", "`XFL=0`、`OS=255`")],
    ["archive-gzip-name-drift", (state) => replace(state, "docs/v0.2/tasks/V2-M0-02-versioned-candidate-baseline.md", "无 extra/name/comment/header-CRC", "original name/comment allowed")],
    ["second-release-artifact", (state) => append(state, "docs/v0.2/tasks/V2-M4-02-reproducible-release-packaging.md", "\nA second artifact is allowed.\n")],
    ["caller-release-artifact", (state) => append(state, "docs/v0.2/tasks/V2-M4-03-release-evidence-decision.md", "\nA caller URL artifact is allowed.\n")],
    ["missing-release-artifact-accepted", (state) => replace(state, "docs/v0.2/tasks/V2-M4-03-release-evidence-decision.md", "missing、expired、deleted", "missing/expired/deleted is accepted")],
    ["m401-modifies-frozen-notice", (state) => append(state, "docs/v0.2/tasks/V2-M4-01-security-supply-chain-review.md", "\nV2-M4-01 may modify THIRD_PARTY_NOTICES.\n")],
    ["stale-plan-skill-route", (state) => replace(state, ".agents/skills/plan-apk-hardening-change/SKILL.md", "docs/v0.2/README_FIRST.md", "docs/README_FIRST.md")],
    ["stale-host-skill-route", (state) => replace(state, ".agents/skills/implement-apk-postprocessor/SKILL.md", "docs/v0.2/tasks/", "docs/tasks/")],
    ["stale-validation-skill-route", (state) => replace(state, ".agents/skills/validate-protected-apk/SKILL.md", "baseline_inputs", "depends_on")],
  ];
  for (const [name, mutate] of mutations) {
    const changed = cloneState(original);
    mutate(changed);
    if (validate(changed).length === 0) fail(`self-test mutation accepted: ${name}`);
    count += 1;
  }
  return count;
}

function installSyntheticCandidateLock(state) {
  const tuple = {
    schemaVersion: 2,
    releaseLine: "v0.2",
    releaseVersion: "0.2.0",
    candidateId: "v0.2.0-rc.1",
    terminalV01MainSha: terminalV01Commit,
    implementationFreezeSha: "1".repeat(40),
    implementationManifestSha256: "2".repeat(64),
    validationFreezeSha: "3".repeat(40),
    validationManifestSha256: "4".repeat(64),
    toolchainManifestSha256: "5".repeat(64),
    productContractManifestSha256: "6".repeat(64),
    fixtureSourceManifestSha256: "7".repeat(64),
    performanceContractSha256: "8".repeat(64),
    releaseGateContractSha256: "9".repeat(64),
  };
  const tuplePreimage = JSON.stringify(tuple);
  const productTupleSha256 = sha256(tuplePreimage);
  const lock = {
    schemaVersion: 1,
    taskId: "V2-M3-02",
    verificationStatus: "VERIFIED",
    tuple,
    tuplePreimage,
    productTupleSha256,
  };
  state.files.set(candidateLockPath, `${JSON.stringify(lock, null, 2)}\n`);
  return productTupleSha256;
}

function cloneState(state) {
  return { files: new Map([...state.files].map(([key, value]) => [key, value])) };
}

function replace(state, relative, before, after) {
  const text = state.files.get(relative);
  if (!text?.includes(before)) fail(`self-test preimage missing: ${relative}: ${before}`);
  state.files.set(relative, text.replace(before, after));
}

function append(state, relative, addition) {
  state.files.set(relative, `${state.files.get(relative)}${addition}`);
}

function mutateJson(state, relative, mutation) {
  const value = JSON.parse(state.files.get(relative));
  mutation(value);
  state.files.set(relative, `${JSON.stringify(value, null, 2)}\n`);
}

function writeTuple(state, relative, payload) {
  state.files.set(
    relative,
    `${JSON.stringify({ ...payload, tuple_sha256: sha256(stable(payload)) }, null, 2)}\n`,
  );
}

function leafEntries(value, prefix = []) {
  const result = [];
  for (const [key, child] of Object.entries(value)) {
    const childPath = [...prefix, key];
    if (child && typeof child === "object" && !Array.isArray(child)) result.push(...leafEntries(child, childPath));
    else result.push([childPath, child]);
  }
  return result;
}

function setPath(object, pathParts, value) {
  const parts = [...pathParts];
  const leaf = parts.pop();
  let current = object;
  for (const part of parts) current = current[part];
  current[leaf] = value;
}

function alternate(value) {
  if (typeof value === "boolean") return !value;
  if (typeof value === "number") return value + 1;
  if (typeof value === "string") return `${value}-MUTATED`;
  if (Array.isArray(value)) return [...value].reverse();
  throw new Error(`unsupported tuple field type: ${typeof value}`);
}

function visit(id, graph, visiting, visited, found) {
  if (visited.has(id)) return;
  if (visiting.has(id)) {
    found.push(`v0.2 dependency cycle detected at ${id}`);
    return;
  }
  visiting.add(id);
  for (const dependency of graph.get(id) || []) {
    if (!graph.has(dependency)) found.push(`${id} depends on unknown task ${dependency}`);
    else visit(dependency, graph, visiting, visited, found);
  }
  visiting.delete(id);
  visited.add(id);
}

function requirePhrase(text, phrase, label, found) {
  if (!text?.includes(phrase)) found.push(`${label}: missing phrase: ${phrase}`);
}

function requireCount(text, phrase, expected, label, found) {
  const actual = text?.split(phrase).length - 1;
  if (actual !== expected) found.push(`${label}: expected ${expected}, got ${actual}`);
}

function requireEqual(actual, expected, label, found) {
  if (actual !== expected) found.push(`${label}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}

function requireMatch(actual, pattern, label, found) {
  if (typeof actual !== "string" || !pattern.test(actual)) found.push(`${label}: invalid value ${JSON.stringify(actual)}`);
}

function stable(value) {
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  if (value && typeof value === "object") return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stable(value[key])}`).join(",")}}`;
  return JSON.stringify(value);
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function git(gitArgs) {
  return spawnSync("git", gitArgs, { cwd: root, encoding: "utf8", windowsHide: true });
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}
