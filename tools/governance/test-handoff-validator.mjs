#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();
const sourceFile = path.join(root, "HandOff.md");
const validator = path.join(
  root,
  ".agents",
  "skills",
  "coordinate-project-handoff",
  "scripts",
  "validate-handoff.mjs",
);

if (!fs.existsSync(sourceFile) || !fs.existsSync(validator)) {
  console.error("Root HandOff.md and its validator must exist before running this test.");
  process.exit(2);
}

const workRoot = path.join(root, "work");
fs.mkdirSync(workRoot, { recursive: true });
const fixtureRoot = fs.mkdtempSync(path.join(workRoot, "handoff-validator-"));
const branchResult = spawnSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
  cwd: root,
  encoding: "utf8",
  windowsHide: true,
});
const currentBranch = branchResult.stdout.trim() === "HEAD"
  ? (process.env.GITHUB_HEAD_REF || process.env.GITHUB_REF_NAME || "HEAD")
  : branchResult.stdout.trim();
const statusResult = spawnSync("git", ["status", "--porcelain"], {
  cwd: root,
  encoding: "utf8",
  windowsHide: true,
});
const currentTreeState = statusResult.stdout.trim() === "" ? "clean" : "dirty";
const headResult = spawnSync("git", ["rev-parse", "HEAD"], {
  cwd: root,
  encoding: "utf8",
  windowsHide: true,
});
if (headResult.status !== 0 || !/^[0-9a-f]{40}$/.test(headResult.stdout.trim())) {
  console.error("A committed Git HEAD is required for HandOff validator fixtures.");
  process.exit(2);
}
const headCommit = headResult.stdout.trim();

const source = fs.readFileSync(sourceFile, "utf8")
  .replace(/^schema_version:.*$/m, "schema_version: 1")
  .replace(/^release_line:.*\r?\n/m, "")
  .replace(/^product_tuple_sha256:.*\r?\n/m, "")
  .replace(/^state:.*$/m, "state: ready")
  .replace(/^active_task:.*$/m, "active_task: NONE")
  .replace(/^current_milestone:.*$/m, "current_milestone: M0")
  .replace(/^base_commit:.*$/m, `base_commit: ${headCommit}`)
  .replace(/^working_tree:.*$/m, `working_tree: ${currentTreeState}`)
  .replace(/^source_branch:.*$/m, `source_branch: ${currentBranch}`)
  .replace(
    /## Active Workstreams[\s\S]*?## Decisions and Invariants/,
    "## Active Workstreams\n\nNone\n\n## Decisions and Invariants",
  )
  .replace(
    /## Verification Evidence[\s\S]*?## Blockers and Required Approvals/,
    `## Verification Evidence

### Validator fixture

- task_id: M0-01
- git_commit: ${headCommit}
- command: \`node --version\`
- exit_code: 0
- environment: \`validator fixture; local Git worktree\`
- timestamp: 2026-08-24T00:00:00+08:00
- artifact: \`not_applicable\`
- sha256: not_applicable
- result: validator fixture baseline

## Blockers and Required Approvals`,
  )
  .replace(
    /## Relevant Files and Artifacts[\s\S]*?## Resume Checklist/,
    "## Relevant Files and Artifacts\n\nNone\n\n## Resume Checklist",
  )
  .replace(
    /## Blockers and Required Approvals[\s\S]*?## Ordered Next Actions/,
    "## Blockers and Required Approvals\n\nNone\n\n## Ordered Next Actions",
  );
const v2ProductTuple = "5fb0205d9fc0c2523cd33734145bf23a901303f4f563eef866e5883ca81fd4c2";
const oldV01ProductTuple = "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd";
const selfCertifiedCandidate = makeSelfCertifiedCandidateLock();
const sourceV2 = source
  .replace(/^schema_version: 1$/m, "schema_version: 2")
  .replace(
    /^project: androidAppHardening$/m,
    `project: androidAppHardening\nrelease_line: v0.2\nproduct_tuple_sha256: ${v2ProductTuple}`,
  )
  .replace(/^current_milestone: M([0-4])$/m, "current_milestone: V2-M$1")
  .replace(/^- task_id: `?M0-01`?$/m, "- task_id: V2-M0-01");

const cases = [
  {
    name: "missing-required-field",
    mutate: (text) => text.replace(/^next_owner:.*\r?\n/m, ""),
    expected: "Frontmatter keys must appear exactly in this order",
    expectedErrorCount: 2,
    strict: false,
  },
  {
    name: "heading-order",
    mutate: (text) => text
      .replace("## Objective", "## HEADING-SWAP")
      .replace("## Current State", "## Objective")
      .replace("## HEADING-SWAP", "## Current State"),
    expected: "Heading is out of order",
    expectedErrorCount: 2,
    strict: false,
  },
  {
    name: "forbidden-placeholder",
    mutate: (text) => text.replace("## Objective", "## Objective\n\nTODO"),
    expected: "Placeholders TODO and TBD are forbidden",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "absolute-user-path",
    mutate: (text) => text.replace(
      "## Current State",
      "## Current State\n\nC:\\Users\\fixture\\sample.txt",
    ),
    expected: "User-directory absolute paths are forbidden",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "invalid-evidence-hash",
    mutate: (text) => text.replace(
      /^- sha256:.*$/m,
      "- sha256: invalid",
    ),
    expected: "Verification evidence has invalid sha256",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "invalid-evidence-timestamp",
    mutate: (text) => text.replace(
      /^- timestamp:.*$/m,
      "- timestamp: `not-a-timestamp`",
    ),
    expected: "Verification evidence has invalid timestamp",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "active-state-without-task",
    mutate: (text) => text.replace(/^state: ready$/m, "state: active"),
    expected: "state active requires a concrete active_task",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "blocked-state-without-blocker",
    mutate: (text) => text.replace(/^state: ready$/m, "state: blocked"),
    expected: "state blocked requires a non-empty blocker section",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "nonexistent-base-commit",
    mutate: (text) => text.replace(
      /^base_commit: [0-9a-f]{40}$/m,
      `base_commit: ${"0".repeat(40)}`,
    ),
    expected: "base_commit does not exist",
    expectedErrorCount: 1,
    strict: true,
  },
  {
    name: "source-branch-mismatch",
    mutate: (text) => text.replace(
      /^source_branch:.*$/m,
      "source_branch: invalid/branch-for-negative-test",
    ),
    expected: "source_branch declares invalid/branch-for-negative-test",
    expectedErrorCount: 1,
    strict: true,
  },
  {
    name: "nonexistent-evidence-commit",
    mutate: (text) => text.replace(
      /^- git_commit: `?[0-9a-f]{40}`?$/m,
      `- git_commit: \`${"f".repeat(40)}\``,
    ),
    expected: "Verification evidence commit does not exist",
    expectedErrorCount: 1,
    strict: true,
  },
  {
    name: "schema2-missing-release-line",
    schema: 2,
    mutate: (text) => text.replace(/^release_line:.*\r?\n/m, ""),
    expected: "Missing frontmatter key: release_line",
    expectedErrorCount: 2,
    strict: false,
  },
  {
    name: "schema2-field-order",
    schema: 2,
    mutate: (text) => text.replace(
      `release_line: v0.2\nproduct_tuple_sha256: ${v2ProductTuple}`,
      `product_tuple_sha256: ${v2ProductTuple}\nrelease_line: v0.2`,
    ),
    expected: "Frontmatter keys must appear exactly in this order",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-old-product-tuple",
    schema: 2,
    mutate: (text) => text.replace(
      /^product_tuple_sha256: [0-9a-f]{64}$/m,
      `product_tuple_sha256: ${oldV01ProductTuple}`,
    ),
    expected: "product_tuple_sha256 must not reuse the terminal v0.1 product tuple",
    expectedErrorCount: 2,
    strict: false,
  },
  {
    name: "schema2-arbitrary-third-tuple-without-lock",
    schema: 2,
    mutate: (text) => text.replace(
      /^product_tuple_sha256: [0-9a-f]{64}$/m,
      `product_tuple_sha256: ${"a".repeat(64)}`,
    ),
    expected: "must equal the exact development tuple",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-candidate-without-lock",
    schema: 2,
    mutate: (text) => text.replace(
      /^product_tuple_sha256: [0-9a-f]{64}$/m,
      `product_tuple_sha256: ${"b".repeat(64)}`,
    ),
    expected: "product-tuple-lock.json is absent",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-self-certified-verified-candidate-lock",
    schema: 2,
    mutate: (text) => text.replace(
      /^product_tuple_sha256: [0-9a-f]{64}$/m,
      `product_tuple_sha256: ${selfCertifiedCandidate.productTupleSha256}`,
    ),
    setup: (directory) => {
      const init = spawnSync("git", ["init", "--quiet"], {
        cwd: directory,
        encoding: "utf8",
        windowsHide: true,
      });
      if (init.status !== 0) throw new Error(`cannot initialize candidate-lock fixture: ${init.stderr}`);
      const lockPath = path.join(directory, "docs", "v0.2", "evidence", "V2-M3-02", "product-tuple-lock.json");
      fs.mkdirSync(path.dirname(lockPath), { recursive: true });
      fs.writeFileSync(lockPath, `${JSON.stringify(selfCertifiedCandidate.lock, null, 2)}\n`, "utf8");
    },
    expected: "self-declared VERIFIED lock is not trusted",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-old-milestone",
    schema: 2,
    mutate: (text) => text.replace(/^current_milestone: V2-M([0-4])$/m, "current_milestone: M$1"),
    expected: "current_milestone must be V2-M0 through V2-M4 for v0.2",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-old-task",
    schema: 2,
    mutate: (text) => text
      .replace(/^active_task: NONE$/m, "active_task: M0-05")
      .replace(
        "## Active Workstreams",
        "## Active Workstreams\n\n| M0-05 | qa-governance-agent | test/m0-05 | planned | None | reject legacy namespace |",
      ),
    setup: (directory) => createTaskCard(directory, "M0-05"),
    expected: "active_task must be NONE or a V2-Mx-nn task ID for v0.2",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-active-task-milestone-mismatch",
    schema: 2,
    mutate: (text) => text
      .replace(/^active_task: NONE$/m, "active_task: V2-M2-01")
      .replace(
        "## Active Workstreams",
        "## Active Workstreams\n\n| V2-M2-01 | qa-governance-agent | test/v2-m2-01 | planned | None | validate mismatch |",
      ),
    setup: (directory) => createTaskCard(directory, "V2-M2-01"),
    expected: "active_task V2-M2-01 must match current_milestone V2-M0",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-active-task-missing-workstream",
    schema: 2,
    mutate: (text) => text
      .replace(/^active_task: NONE$/m, "active_task: V2-M0-02")
      .replace(
        "## Ordered Next Actions",
        "## Ordered Next Actions\n\nV2-M0-02 appears outside Active Workstreams and must not satisfy the row requirement.",
      ),
    setup: (directory) => createTaskCard(directory, "V2-M0-02"),
    expected: "Active workstreams must contain a row for V2-M0-02",
    expectedErrorCount: 1,
    strict: false,
  },
  {
    name: "schema2-nonexistent-task",
    schema: 2,
    mutate: (text) => text
      .replace(/^active_task: NONE$/m, "active_task: V2-M0-99")
      .replace(
        "## Active Workstreams",
        "## Active Workstreams\n\n| V2-M0-99 | qa-governance-agent | test/v2-m0-99 | planned | None | create task card |",
      ),
    expected: "No task card exists for active_task V2-M0-99",
    expectedErrorCount: 1,
    strict: false,
  },
];

let failures = 0;
const evidenceRows = [];
try {
  const baselineDirectory = path.join(fixtureRoot, "valid-baseline");
  fs.mkdirSync(baselineDirectory, { recursive: true });
  const baselineFixture = path.join(baselineDirectory, "HandOff.md");
  fs.writeFileSync(baselineFixture, source, "utf8");
  const baseline = spawnSync(process.execPath, [validator, baselineFixture, "--strict"], {
    cwd: baselineDirectory,
    encoding: "utf8",
    windowsHide: true,
  });
  if (baseline.status !== 0) {
    console.error("FAIL: valid-baseline");
    console.error(`${baseline.stdout ?? ""}\n${baseline.stderr ?? ""}`.trim());
    process.exitCode = 1;
    failures += 1;
    evidenceRows.push({
      fixture: "valid-baseline",
      sha256: sha256(source),
      expected: "valid",
      actual_exit_code: baseline.status,
      actual_error_count: countErrors(`${baseline.stdout ?? ""}\n${baseline.stderr ?? ""}`),
      result: "FAIL",
    });
  } else {
    console.log("PASS: valid-baseline");
    evidenceRows.push({
      fixture: "valid-baseline",
      sha256: sha256(source),
      expected: "valid",
      actual_exit_code: baseline.status,
      actual_error_count: 0,
      result: "PASS",
    });
  }

  const schema2BaselineDirectory = path.join(fixtureRoot, "valid-schema2-baseline");
  fs.mkdirSync(schema2BaselineDirectory, { recursive: true });
  const schema2BaselineFixture = path.join(schema2BaselineDirectory, "HandOff.md");
  fs.writeFileSync(schema2BaselineFixture, sourceV2, "utf8");
  const schema2Baseline = spawnSync(
    process.execPath,
    [validator, schema2BaselineFixture, "--strict"],
    {
      cwd: schema2BaselineDirectory,
      encoding: "utf8",
      windowsHide: true,
    },
  );
  if (schema2Baseline.status !== 0) {
    console.error("FAIL: valid-schema2-baseline");
    console.error(`${schema2Baseline.stdout ?? ""}\n${schema2Baseline.stderr ?? ""}`.trim());
    process.exitCode = 1;
    failures += 1;
    evidenceRows.push({
      fixture: "valid-schema2-baseline",
      sha256: sha256(sourceV2),
      expected: "valid",
      actual_exit_code: schema2Baseline.status,
      actual_error_count: countErrors(`${schema2Baseline.stdout ?? ""}\n${schema2Baseline.stderr ?? ""}`),
      result: "FAIL",
    });
  } else {
    console.log("PASS: valid-schema2-baseline");
    evidenceRows.push({
      fixture: "valid-schema2-baseline",
      sha256: sha256(sourceV2),
      expected: "valid",
      actual_exit_code: schema2Baseline.status,
      actual_error_count: 0,
      result: "PASS",
    });
  }

  const schema2ActiveDirectory = path.join(fixtureRoot, "valid-schema2-active-task");
  fs.mkdirSync(schema2ActiveDirectory, { recursive: true });
  createTaskCard(schema2ActiveDirectory, "V2-M0-01");
  const schema2ActiveSource = sourceV2
    .replace(/^state: ready$/m, "state: active")
    .replace(/^active_task: NONE$/m, "active_task: V2-M0-01")
    .replace(
      "## Active Workstreams",
      "## Active Workstreams\n\n| V2-M0-01 | qa-governance-agent | test/v2-m0-01 | in_progress | None | validate schema |",
    );
  const schema2ActiveFixture = path.join(schema2ActiveDirectory, "HandOff.md");
  fs.writeFileSync(schema2ActiveFixture, schema2ActiveSource, "utf8");
  const schema2Active = spawnSync(
    process.execPath,
    [validator, schema2ActiveFixture, "--strict"],
    {
      cwd: schema2ActiveDirectory,
      encoding: "utf8",
      windowsHide: true,
    },
  );
  if (schema2Active.status !== 0) {
    console.error("FAIL: valid-schema2-active-task");
    console.error(`${schema2Active.stdout ?? ""}\n${schema2Active.stderr ?? ""}`.trim());
    failures += 1;
    evidenceRows.push({
      fixture: "valid-schema2-active-task",
      sha256: sha256(schema2ActiveSource),
      expected: "valid",
      actual_exit_code: schema2Active.status,
      actual_error_count: countErrors(`${schema2Active.stdout ?? ""}\n${schema2Active.stderr ?? ""}`),
      result: "FAIL",
    });
  } else {
    console.log("PASS: valid-schema2-active-task");
    evidenceRows.push({
      fixture: "valid-schema2-active-task",
      sha256: sha256(schema2ActiveSource),
      expected: "valid",
      actual_exit_code: schema2Active.status,
      actual_error_count: 0,
      result: "PASS",
    });
  }

  for (const testCase of cases) {
    const directory = path.join(fixtureRoot, testCase.name);
    fs.mkdirSync(directory, { recursive: true });
    if (testCase.setup) testCase.setup(directory);
    const fixture = path.join(directory, "HandOff.md");
    const caseSource = testCase.schema === 2 ? sourceV2 : source;
    const fixtureText = testCase.mutate(caseSource);
    if (fixtureText === caseSource) {
      console.log(`FAIL: ${testCase.name}`);
      console.error(`Mutation did not change HandOff.md for ${testCase.name}`);
      failures += 1;
      evidenceRows.push({
        fixture: testCase.name,
        sha256: sha256(fixtureText),
        expected_error: testCase.expected,
        expected_exit_code: 1,
        expected_error_count: testCase.expectedErrorCount,
        actual_exit_code: 0,
        actual_error_count: 0,
        result: "FAIL",
      });
      continue;
    }
    fs.writeFileSync(fixture, fixtureText, "utf8");

    const validatorArgs = [validator, fixture];
    if (testCase.strict) validatorArgs.push("--strict");
    const result = spawnSync(process.execPath, validatorArgs, {
      cwd: directory,
      encoding: "utf8",
      windowsHide: true,
    });
    const output = `${result.stdout ?? ""}\n${result.stderr ?? ""}`;
    const errorCount = countErrors(output);
    const passed = result.status === 1
      && output.includes(testCase.expected)
      && errorCount === testCase.expectedErrorCount;
    console.log(`${passed ? "PASS" : "FAIL"}: ${testCase.name}`);
    if (!passed) {
      failures += 1;
      console.error(output.trim());
    }
    evidenceRows.push({
      fixture: testCase.name,
      sha256: sha256(fixtureText),
      expected_error: testCase.expected,
      expected_exit_code: 1,
      expected_error_count: testCase.expectedErrorCount,
      actual_exit_code: result.status,
      actual_error_count: errorCount,
      result: passed ? "PASS" : "FAIL",
    });
  }
} finally {
  fs.rmSync(fixtureRoot, { recursive: true, force: true });
}

console.log(JSON.stringify({ schema_version: 1, fixtures: evidenceRows }, null, 2));
if (failures > 0) process.exit(1);
console.log(`OK: ${cases.length} negative HandOff validator cases`);

function sha256(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function countErrors(output) {
  return [...output.matchAll(/^ERROR:/gm)].length;
}

function createTaskCard(directory, taskId) {
  const tasksDirectory = taskId.startsWith("V2-")
    ? path.join(directory, "docs", "v0.2", "tasks")
    : path.join(directory, "docs", "tasks");
  fs.mkdirSync(tasksDirectory, { recursive: true });
  fs.writeFileSync(path.join(tasksDirectory, `${taskId}-validator-fixture.md`), `# ${taskId}\n`, "utf8");
}

function makeSelfCertifiedCandidateLock() {
  const tuple = {
    schemaVersion: 2,
    releaseLine: "v0.2",
    releaseVersion: "0.2.0",
    candidateId: "v0.2.0-rc.1",
    terminalV01MainSha: oldV01ProductTuple.slice(0, 40),
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
  return {
    productTupleSha256,
    lock: {
      schemaVersion: 1,
      taskId: "V2-M3-02",
      verificationStatus: "VERIFIED",
      tuple,
      tuplePreimage,
      productTupleSha256,
    },
  };
}
