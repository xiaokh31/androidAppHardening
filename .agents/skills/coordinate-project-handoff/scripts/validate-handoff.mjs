#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const args = process.argv.slice(2);
const knownFlags = new Set(["--strict", "--allow-pending-clean", "--allow-pending-branch"]);
const unknownFlags = args.filter((arg) => arg.startsWith("--") && !knownFlags.has(arg));
if (unknownFlags.length > 0) {
  console.error(`Unknown option(s): ${unknownFlags.join(", ")}`);
  process.exit(2);
}
const fileArg = args.find((arg) => !arg.startsWith("--"));
const strict = args.includes("--strict");
const allowPendingClean = args.includes("--allow-pending-clean");
const allowPendingBranch = args.includes("--allow-pending-branch");
const evidenceCommits = new Set();

if (!fileArg) {
  console.error(
    "Usage: validate-handoff.mjs <HandOff.md> "
    + "[--strict] [--allow-pending-clean] [--allow-pending-branch]",
  );
  process.exit(2);
}

const target = path.resolve(fileArg);
const errors = [];

if (path.basename(target) !== "HandOff.md") {
  errors.push("The handoff filename must be exactly HandOff.md.");
}

if (!fs.existsSync(target)) {
  errors.push(`File does not exist: ${target}`);
  finish();
}

const text = fs.readFileSync(target, "utf8");
if (text.includes("\uFFFD")) {
  errors.push("The file contains Unicode replacement characters; verify UTF-8 encoding.");
}

const frontmatterMatch = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
if (!frontmatterMatch) {
  errors.push("Missing YAML frontmatter.");
  finish();
}

const expectedKeys = [
  "schema_version",
  "project",
  "handoff_id",
  "updated_at",
  "updated_by",
  "state",
  "source_branch",
  "base_commit",
  "working_tree",
  "current_milestone",
  "active_task",
  "next_owner",
];

const values = new Map();
const actualKeys = [];
for (const rawLine of frontmatterMatch[1].split(/\r?\n/)) {
  if (!rawLine.trim()) continue;
  const match = rawLine.match(/^([a-z_]+):\s*(.*?)\s*$/);
  if (!match) {
    errors.push(`Invalid frontmatter line: ${rawLine}`);
    continue;
  }
  actualKeys.push(match[1]);
  values.set(match[1], match[2].replace(/^["']|["']$/g, ""));
}

if (actualKeys.join("|") !== expectedKeys.join("|")) {
  errors.push(`Frontmatter keys must appear exactly in this order: ${expectedKeys.join(", ")}`);
}

check("schema_version", (v) => v === "1", "must be 1");
check("project", (v) => v === "androidAppHardening", "must be androidAppHardening");
check("handoff_id", (v) => /^HO-\d{8}-\d{6}$/.test(v), "must match HO-YYYYMMDD-HHMMSS");
check(
  "updated_at",
  (v) => /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:Z|[+-]\d{2}:\d{2})$/.test(v) && !Number.isNaN(Date.parse(v)),
  "must be an ISO-8601 timestamp with timezone",
);
check("updated_by", (v) => v === "/root", "must be /root");
check("state", (v) => ["active", "ready", "blocked"].includes(v), "must be active, ready, or blocked");
check("source_branch", (v) => /^[A-Za-z0-9._/-]+$/.test(v), "contains invalid branch characters");
check("base_commit", (v) => v === "UNBORN" || /^[0-9a-f]{40}$/.test(v), "must be UNBORN or a full lowercase SHA");
check("working_tree", (v) => ["clean", "dirty"].includes(v), "must be clean or dirty");
check("current_milestone", (v) => /^M[0-4]$/.test(v), "must be M0 through M4");
check("active_task", (v) => v === "NONE" || /^M[0-4]-\d{2}$/.test(v), "must be NONE or a task ID");
check("next_owner", (v) => v.length > 0, "must not be empty");

const requiredHeadings = [
  "# Project HandOff",
  "## Objective",
  "## Current State",
  "## Active Workstreams",
  "## Decisions and Invariants",
  "## Changes Since Previous Handoff",
  "## Verification Evidence",
  "## Blockers and Required Approvals",
  "## Ordered Next Actions",
  "## Relevant Files and Artifacts",
  "## Resume Checklist",
  "## Handoff Sign-off",
];

let previousIndex = -1;
const headingPositions = new Map();
for (const heading of requiredHeadings) {
  const matches = [...text.matchAll(new RegExp(`^${escapeRegExp(heading)}$`, "gm"))];
  if (matches.length !== 1) {
    errors.push(`Heading must occur exactly once: ${heading}`);
    continue;
  }
  if (matches[0].index <= previousIndex) {
    errors.push(`Heading is out of order: ${heading}`);
  }
  headingPositions.set(heading, matches[0].index);
  previousIndex = matches[0].index;
}

for (let index = 1; index < requiredHeadings.length; index += 1) {
  const heading = requiredHeadings[index];
  const start = headingPositions.get(heading);
  if (start === undefined) continue;
  const next = requiredHeadings[index + 1];
  const end = next && headingPositions.has(next) ? headingPositions.get(next) : text.length;
  if (text.slice(start + heading.length, end).trim() === "") {
    errors.push(`Section must not be empty: ${heading}`);
  }
}

if (/\b(?:TODO|TBD)\b/i.test(text)) {
  errors.push("Placeholders TODO and TBD are forbidden.");
}
if (/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(text)) {
  errors.push("Private-key material is forbidden.");
}
if (/(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16})/.test(text)) {
  errors.push("A value resembling an access token is forbidden.");
}
if (/(?:password|passwd|token|secret|keystore|alias)\s*[:=]\s*[`"']?[^\s`"']+/i.test(text)) {
  errors.push("A value resembling a credential or signing secret is forbidden.");
}
if (/(?:customer|client)[^\s`"']*\.apk\b/i.test(text) || /dex\\n0(?:35|37|38|39|40)\u0000/i.test(text)) {
  errors.push("Customer APK references and plaintext DEX content are forbidden.");
}
if (/(?:[A-Za-z]:[\\/](?:Users|Documents|works)[\\/]|\/(?:Users|home)\/[^/\s]+)/.test(text)) {
  errors.push("User-directory absolute paths are forbidden; use repository-relative paths.");
}

const activeTask = values.get("active_task");
if (activeTask && activeTask !== "NONE") {
  const tasksDir = path.join(path.dirname(target), "docs", "tasks");
  const exists = fs.existsSync(tasksDir)
    && fs.readdirSync(tasksDir).some((name) => name.startsWith(`${activeTask}-`) && name.endsWith(".md"));
  if (!exists) {
    errors.push(`No task card exists for active_task ${activeTask}.`);
  }
  if (!text.includes(`| ${activeTask} |`)) {
    errors.push(`Active workstreams must contain a row for ${activeTask}.`);
  }
}

validateEvidence();
validateWorkstreams();
validateStateSemantics();
validateRelevantFiles();

if (strict) {
  validateGit();
}

function section(heading, nextHeading) {
  const start = headingPositions.get(heading);
  if (start === undefined) return "";
  const end = nextHeading && headingPositions.has(nextHeading)
    ? headingPositions.get(nextHeading)
    : text.length;
  return text.slice(start + heading.length, end).trim();
}

function validateEvidence() {
  const evidence = section("## Verification Evidence", "## Blockers and Required Approvals");
  const blocks = evidence.split(/^###\s+/m).slice(1);
  const requiredFields = [
    "task_id",
    "git_commit",
    "command",
    "exit_code",
    "environment",
    "timestamp",
    "artifact",
    "sha256",
    "result",
  ];
  if (blocks.length === 0) {
    errors.push("Verification Evidence must contain at least one task evidence block.");
    return;
  }

  const evidencedTasks = new Set();
  for (const block of blocks) {
    const valuesByField = new Map();
    for (const field of requiredFields) {
      const match = block.match(new RegExp(`^- ${field}:\\s*(.+?)\\s*$`, "m"));
      if (!match) {
        errors.push(`Verification evidence block is missing field: ${field}`);
      } else {
        valuesByField.set(field, match[1].replaceAll("`", "").trim());
      }
    }
    const taskId = valuesByField.get("task_id");
    if (taskId && !/^M[0-4]-\d{2}$/.test(taskId)) {
      errors.push(`Verification evidence has invalid task_id: ${taskId}`);
    } else if (taskId) {
      evidencedTasks.add(taskId);
    }
    const commit = valuesByField.get("git_commit");
    if (commit && !/^[0-9a-f]{40}$/.test(commit)) {
      errors.push(`Verification evidence has invalid git_commit: ${commit}`);
    } else if (commit) {
      evidenceCommits.add(commit);
    }
    const exitCode = valuesByField.get("exit_code");
    if (exitCode && !/^-?\d+$/.test(exitCode)) {
      errors.push(`Verification evidence has invalid exit_code: ${exitCode}`);
    }
    const hash = valuesByField.get("sha256");
    if (hash && hash !== "not_applicable" && !/^[0-9a-f]{64}$/.test(hash)) {
      errors.push(`Verification evidence has invalid sha256: ${hash}`);
    }
    const timestamp = valuesByField.get("timestamp");
    if (timestamp
      && (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:Z|[+-]\d{2}:\d{2})$/.test(timestamp)
        || Number.isNaN(Date.parse(timestamp)))) {
      errors.push(`Verification evidence has invalid timestamp: ${timestamp}`);
    }
  }

  const activeWorkstreams = section("## Active Workstreams", "## Decisions and Invariants");
  for (const row of activeWorkstreams.split(/\r?\n/)) {
    const match = row.match(/^\|\s*(M[0-4]-\d{2})\s*\|.*\|\s*(?:done|completed)\s*\|/i);
    if (match && !evidencedTasks.has(match[1])) {
      errors.push(`Completed task ${match[1]} has no Verification Evidence block.`);
    }
  }
}

function validateWorkstreams() {
  const activeWorkstreams = section("## Active Workstreams", "## Decisions and Invariants");
  const allowedStatuses = new Set(["planned", "in_progress", "blocked", "review", "done"]);
  for (const row of activeWorkstreams.split(/\r?\n/)) {
    if (!/^\|\s*M[0-4]-\d{2}\s*\|/.test(row)) continue;
    const cells = row.split("|").slice(1, -1).map((cell) => cell.trim().replaceAll("`", ""));
    if (cells.length < 6) {
      errors.push(`Invalid Active Workstreams row: ${row}`);
      continue;
    }
    const [taskId, owner, , status, , nextAction] = cells;
    if (!allowedStatuses.has(status)) {
      errors.push(`Active workstream ${taskId} has invalid status: ${status}`);
    }
    if (status === "blocked" && (!owner || owner === "unassigned" || !nextAction || nextAction === "None")) {
      errors.push(`Blocked workstream ${taskId} requires an unblock owner and exact next action.`);
    }
  }
}

function validateStateSemantics() {
  const state = values.get("state");
  const task = values.get("active_task");
  const blockers = section("## Blockers and Required Approvals", "## Ordered Next Actions").trim();
  if (state === "active" && task === "NONE") {
    errors.push("state active requires a concrete active_task.");
  }
  if (state === "blocked" && (blockers === "" || /^None$/i.test(blockers))) {
    errors.push("state blocked requires a non-empty blocker section.");
  }
  if (state === "ready" && !/^None$/i.test(blockers)) {
    errors.push("state ready requires the blocker section to be exactly None.");
  }
}

function validateRelevantFiles() {
  const relevantFiles = section("## Relevant Files and Artifacts", "## Resume Checklist");
  for (const match of relevantFiles.matchAll(/`([^`\r\n]+)`/g)) {
    const candidate = match[1];
    if (!/^(?:\.agents|\.github|docs|tools)\//.test(candidate)
      && !/^(?:AGENTS|CONTRIBUTING|HandOff|README|SECURITY|THIRD_PARTY_NOTICES)\.md$/.test(candidate)) {
      continue;
    }
    const resolved = path.resolve(path.dirname(target), candidate);
    if (!fs.existsSync(resolved)) {
      errors.push(`Relevant file does not exist: ${candidate}`);
    }
  }
}

finish();

function check(key, predicate, message) {
  const value = values.get(key);
  if (value === undefined) {
    errors.push(`Missing frontmatter key: ${key}`);
  } else if (!predicate(value)) {
    errors.push(`${key} ${message}.`);
  }
}

function validateGit() {
  const repoRoot = path.dirname(target);
  const inside = git(["rev-parse", "--is-inside-work-tree"], repoRoot);
  if (inside.status !== 0 || inside.stdout.trim() !== "true") {
    errors.push("Strict validation requires a Git working tree.");
    return;
  }

  const base = values.get("base_commit");
  if (base === "UNBORN") {
    const head = git(["rev-parse", "--verify", "HEAD"], repoRoot);
    if (head.status === 0) errors.push("UNBORN is invalid after the repository has a commit.");
  } else if (base) {
    if (git(["cat-file", "-e", `${base}^{commit}`], repoRoot).status !== 0) {
      errors.push(`base_commit does not exist: ${base}`);
    } else if (git(["merge-base", "--is-ancestor", base, "HEAD"], repoRoot).status !== 0) {
      errors.push("base_commit must be an ancestor of HEAD.");
    }
  }

  const branch = git(["rev-parse", "--abbrev-ref", "HEAD"], repoRoot);
  const actualBranch = branch.stdout.trim() === "HEAD"
    ? (process.env.GITHUB_HEAD_REF || process.env.GITHUB_REF_NAME || "HEAD")
    : branch.stdout.trim();
  if (branch.status !== 0) {
    errors.push("Unable to determine the current Git branch.");
  } else if (values.get("source_branch") !== actualBranch) {
    if (!allowPendingBranch) {
      errors.push(`source_branch declares ${values.get("source_branch")} but Git reports ${actualBranch}.`);
    } else {
      const expectedTarget = process.env.GITHUB_BASE_REF || "main";
      if (values.get("source_branch") !== expectedTarget) {
        errors.push(
          `source_branch pending target must be ${expectedTarget}, got ${values.get("source_branch")}.`,
        );
      }
    }
  }

  for (const commit of evidenceCommits) {
    if (git(["cat-file", "-e", `${commit}^{commit}`], repoRoot).status !== 0) {
      errors.push(`Verification evidence commit does not exist: ${commit}`);
    } else if (git(["merge-base", "--is-ancestor", commit, "HEAD"], repoRoot).status !== 0) {
      errors.push(`Verification evidence commit must be an ancestor of HEAD: ${commit}`);
    }
  }

  const status = git(["status", "--porcelain"], repoRoot);
  const isClean = status.status === 0 && status.stdout.trim() === "";
  const declared = values.get("working_tree");
  if (declared === "clean" && !isClean && !allowPendingClean) {
    errors.push("working_tree declares clean but Git reports changes.");
  }
  if (declared === "dirty" && isClean) {
    errors.push("working_tree declares dirty but Git is clean.");
  }
}

function git(gitArgs, cwd) {
  return spawnSync("git", gitArgs, { cwd, encoding: "utf8", windowsHide: true });
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function finish() {
  if (errors.length > 0) {
    for (const error of errors) console.error(`ERROR: ${error}`);
    process.exit(1);
  }
  console.log(`OK: ${target}`);
  process.exit(0);
}
