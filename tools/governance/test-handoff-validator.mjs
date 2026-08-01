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

const source = fs.readFileSync(sourceFile, "utf8")
  .replace(/^state:.*$/m, "state: ready")
  .replace(/^active_task:.*$/m, "active_task: NONE")
  .replace(/^working_tree:.*$/m, `working_tree: ${currentTreeState}`)
  .replace(/^source_branch:.*$/m, `source_branch: ${currentBranch}`)
  .replace(
    /## Relevant Files and Artifacts[\s\S]*?## Resume Checklist/,
    "## Relevant Files and Artifacts\n\nNone\n\n## Resume Checklist",
  )
  .replace(
    /## Blockers and Required Approvals[\s\S]*?## Ordered Next Actions/,
    "## Blockers and Required Approvals\n\nNone\n\n## Ordered Next Actions",
  );

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
      /^- sha256: `?[0-9a-f]{64}`?$/m,
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

  for (const testCase of cases) {
    const directory = path.join(fixtureRoot, testCase.name);
    fs.mkdirSync(directory, { recursive: true });
    const fixture = path.join(directory, "HandOff.md");
    const fixtureText = testCase.mutate(source);
    if (fixtureText === source) {
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
