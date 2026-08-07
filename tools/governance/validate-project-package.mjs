#!/usr/bin/env node

// Validate the implementation-ready governance and task package without external dependencies.

import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const errors = [];
const allowedArgs = new Set(["--require-governance-only"]);
const unknownArgs = process.argv.slice(2).filter((arg) => !allowedArgs.has(arg));
if (unknownArgs.length > 0) {
  console.error(`Unknown argument(s): ${unknownArgs.join(", ")}`);
  process.exit(2);
}
const requireGovernanceOnly = process.argv.includes("--require-governance-only");

const expectedTasks = [
  "M0-01-repository-bootstrap.md",
  "M0-02-governance-skills-handoff.md",
  "M0-03-toolchain-gradle-ci.md",
  "M0-04-api29-classloader-poc.md",
  "M0-05-application-factory-provider-jni-poc.md",
  "M1-01-untrusted-apk-inspector.md",
  "M1-02-signer-policy.md",
  "M1-03-binary-axml-transformer.md",
  "M1-04-encrypted-dex-container.md",
  "M1-05-apk-repacker-and-alignment.md",
  "M1-06-cli-and-json-report.md",
  "M2-01-shell-app-component-factory.md",
  "M2-02-native-decrypt-and-inmemory-loader.md",
  "M2-03-runtime-signer-and-integrity.md",
  "M2-04-four-abi-runtime.md",
  "M2-05-environment-risk-engine.md",
  "M2-06-memory-dump-cost-controls.md",
  "M3-01-android-fixtures.md",
  "M3-02-tamper-and-fuzz-tests.md",
  "M3-03-windows-ubuntu-equivalence.md",
  "M3-04-api-and-abi-matrix.md",
  "M3-05-size-startup-memory-benchmarks.md",
  "M4-01-security-and-supply-chain-review.md",
  "M4-02-cross-platform-release-packaging.md",
  "M4-03-release-evidence-and-documentation.md",
  "M0-06-early-startup-config-contract.md",
  "M1-07-chunk-authenticated-container-contract.md",
  "M2-07-native-crypto-backend.md",
];

const taskHeadings = [
  "## Goal",
  "## Background",
  "## Inputs",
  "## Expected Outputs",
  "## In Scope",
  "## Out of Scope",
  "## Implementation Decisions",
  "## Public Interfaces",
  "## Security Constraints",
  "## Compatibility Requirements",
  "## Acceptance Criteria",
  "## Required Tests",
  "## Required Evidence",
  "## Likely Files",
  "## Dependencies and Blockers",
  "## Agent Handoff Requirements",
];

const requiredTaskKeys = [
  "id",
  "title",
  "milestone",
  "status",
  "owner_role",
  "depends_on",
  "required_skills",
  "security_sensitive",
];

const expectedSkills = [
  "plan-apk-hardening-change",
  "implement-apk-postprocessor",
  "implement-runtime-protection",
  "validate-protected-apk",
  "coordinate-project-handoff",
  "audit-third-party-skill",
];

const allowedOwnerRoles = new Set([
  "/root",
  "host-pipeline-agent",
  "runtime-security-agent",
  "qa-governance-agent",
]);

const taskDir = path.join(root, "docs", "tasks");
const actualTasks = fs.existsSync(taskDir)
  ? fs.readdirSync(taskDir).filter((name) => /^M[0-4]-\d{2}-.+\.md$/.test(name)).sort()
  : [];

for (const name of expectedTasks) {
  if (!actualTasks.includes(name)) errors.push(`Missing task card: docs/tasks/${name}`);
}
for (const name of actualTasks) {
  if (!expectedTasks.includes(name)) errors.push(`Unexpected task card: docs/tasks/${name}`);
}

const taskIds = new Map();
const dependencyGraph = new Map();
const taskRequiredSkills = new Map();
const taskOwnerRoles = new Map();
for (const name of actualTasks) {
  const file = path.join(taskDir, name);
  const text = readUtf8(file);
  const yaml = parseFrontmatter(text, file);
  const expectedId = name.slice(0, 5);

  const actualKeys = frontmatterKeys(text);
  if (actualKeys.join("|") !== requiredTaskKeys.join("|")) {
    errors.push(`${relative(file)}: frontmatter keys must appear exactly in the required order`);
  }
  for (const key of requiredTaskKeys) {
    if (!(key in yaml)) errors.push(`${relative(file)}: missing frontmatter key ${key}`);
  }
  if (yaml.id !== expectedId) errors.push(`${relative(file)}: id must be ${expectedId}`);
  if (yaml.milestone !== expectedId.slice(0, 2)) errors.push(`${relative(file)}: milestone does not match ID`);
  if (yaml.status !== "planned") errors.push(`${relative(file)}: initial status must be planned`);
  if (!yaml.title) errors.push(`${relative(file)}: title must not be empty`);
  if (!allowedOwnerRoles.has(yaml.owner_role)) {
    errors.push(`${relative(file)}: unknown owner_role ${yaml.owner_role}`);
  }
  if (!["true", "false"].includes(String(yaml.security_sensitive))) {
    errors.push(`${relative(file)}: security_sensitive must be true or false`);
  }
  if (taskIds.has(yaml.id)) errors.push(`Duplicate task ID: ${yaml.id}`);
  taskIds.set(yaml.id, file);
  dependencyGraph.set(yaml.id, parseList(yaml.depends_on));
  taskRequiredSkills.set(yaml.id, parseList(yaml.required_skills));
  taskOwnerRoles.set(yaml.id, yaml.owner_role);

  checkHeadingOrder(text, taskHeadings, file);
}

for (const [id, deps] of dependencyGraph) {
  for (const dep of deps) {
    if (!taskIds.has(dep)) errors.push(`${id}: unknown dependency ${dep}`);
    if (dep === id) errors.push(`${id}: task cannot depend on itself`);
  }
}
checkCycles(dependencyGraph);

const indexFile = path.join(taskDir, "INDEX.md");
if (!fs.existsSync(indexFile)) {
  errors.push("Missing task index: docs/tasks/INDEX.md");
} else {
  const indexText = readUtf8(indexFile);
  for (const name of expectedTasks) {
    const count = [...indexText.matchAll(new RegExp(escapeRegExp(name), "g"))].length;
    if (count !== 1) errors.push(`docs/tasks/INDEX.md: ${name} must be linked exactly once`);
  }
  const indexedTasks = new Set();
  for (const row of indexText.split(/\r?\n/)) {
    if (!/^\|\s*M[0-4]-\d{2}\s*\|/.test(row)) continue;
    const cells = row.split("|").slice(1, -1).map((cell) => cell.trim());
    if (cells.length !== 5) {
      errors.push(`docs/tasks/INDEX.md: invalid task row: ${row}`);
      continue;
    }
    const [id, issueCell, taskCell, ownerCell, dependencyCell] = cells;
    if (indexedTasks.has(id)) errors.push(`docs/tasks/INDEX.md: duplicate task row ${id}`);
    indexedTasks.add(id);
    if (!taskIds.has(id)) {
      errors.push(`docs/tasks/INDEX.md: unknown task row ${id}`);
      continue;
    }
    const expectedFile = path.basename(taskIds.get(id));
    const issueMatch = issueCell.match(
      /^\[#(\d+)]\(https:\/\/github\.com\/xiaokh31\/androidAppHardening\/issues\/(\d+)\)$/,
    );
    const expectedIssue = id === "M0-06"
      ? "30"
      : id === "M1-07"
        ? "36"
        : id === "M2-07"
          ? "41"
          : String(expectedTasks.indexOf(expectedFile) + 1);
    if (!issueMatch || issueMatch[1] !== issueMatch[2] || issueMatch[1] !== expectedIssue) {
      errors.push(`docs/tasks/INDEX.md: ${id} must link its GitHub Issue`);
    }
    const taskLink = taskCell.match(/^\[[^\]]+]\(([^)]+)\)$/);
    if (!taskLink || taskLink[1] !== expectedFile) {
      errors.push(`docs/tasks/INDEX.md: ${id} must link ${expectedFile}`);
    }
    const indexedOwner = ownerCell.replaceAll("`", "");
    if (indexedOwner !== taskOwnerRoles.get(id)) {
      errors.push(`docs/tasks/INDEX.md: ${id} owner ${indexedOwner} does not match task card ${taskOwnerRoles.get(id)}`);
    }
    const indexedDependencies = parseList(dependencyCell);
    const taskDependencies = dependencyGraph.get(id) ?? [];
    if (indexedDependencies.join("|") !== taskDependencies.join("|")) {
      errors.push(
        `docs/tasks/INDEX.md: ${id} dependencies [${indexedDependencies.join(", ")}] `
        + `do not match task card [${taskDependencies.join(", ")}]`,
      );
    }
  }
  for (const id of taskIds.keys()) {
    if (!indexedTasks.has(id)) errors.push(`docs/tasks/INDEX.md: missing task row ${id}`);
  }
}

const roadmapFile = path.join(root, "docs", "ROADMAP.md");
if (fs.existsSync(roadmapFile)) {
  const roadmapText = readUtf8(roadmapFile);
  const roadmapTasks = new Set();
  for (const row of roadmapText.split(/\r?\n/)) {
    if (!/^\|\s*M[0-4]-\d{2}\s*\|/.test(row)) continue;
    const cells = row.split("|").slice(1, -1).map((cell) => cell.trim());
    if (cells.length !== 3) {
      errors.push(`docs/ROADMAP.md: invalid task row: ${row}`);
      continue;
    }
    const [id, , dependencyCell] = cells;
    if (roadmapTasks.has(id)) errors.push(`docs/ROADMAP.md: duplicate task row ${id}`);
    roadmapTasks.add(id);
    if (!dependencyGraph.has(id)) {
      errors.push(`docs/ROADMAP.md: unknown task row ${id}`);
      continue;
    }
    const roadmapDependencies = parseList(dependencyCell);
    const taskDependencies = dependencyGraph.get(id) ?? [];
    if (roadmapDependencies.join("|") !== taskDependencies.join("|")) {
      errors.push(
        `docs/ROADMAP.md: ${id} dependencies [${roadmapDependencies.join(", ")}] `
        + `do not match task card [${taskDependencies.join(", ")}]`,
      );
    }
  }
  for (const id of taskIds.keys()) {
    if (!roadmapTasks.has(id)) errors.push(`docs/ROADMAP.md: missing task row ${id}`);
  }
}

const skillsRoot = path.join(root, ".agents", "skills");
for (const skill of expectedSkills) {
  const skillDir = path.join(skillsRoot, skill);
  const skillFile = path.join(skillDir, "SKILL.md");
  const openAiFile = path.join(skillDir, "agents", "openai.yaml");
  if (!fs.existsSync(skillFile)) {
    errors.push(`Missing project Skill: .agents/skills/${skill}/SKILL.md`);
  } else {
    const skillYaml = parseFrontmatter(readUtf8(skillFile), skillFile);
    if (skillYaml.name !== skill) {
      errors.push(`.agents/skills/${skill}/SKILL.md: frontmatter name must match directory`);
    }
    if (!skillYaml.description) {
      errors.push(`.agents/skills/${skill}/SKILL.md: description must not be empty`);
    }
  }
  if (!fs.existsSync(openAiFile)) {
    errors.push(`Missing project Skill metadata: .agents/skills/${skill}/agents/openai.yaml`);
  }
}

for (const [taskId, skills] of taskRequiredSkills) {
  if (skills.length === 0) errors.push(`${taskId}: required_skills must not be empty`);
  for (const skill of skills) {
    if (!expectedSkills.includes(skill)) errors.push(`${taskId}: unknown required Skill ${skill}`);
  }
}

const m301Text = readUtf8(path.join(root, "docs", "tasks", "M3-01-android-fixtures.md"));
requireOrderedPhrases(
  m301Text,
  [
    "build unsigned fixture",
    "生成一次性证书",
    "签成产品输入",
    "运行产品",
    "同一证书签名输出副本",
  ],
  "docs/tasks/M3-01-android-fixtures.md",
  "signed fixture flow",
);
for (const phrase of [
  "连续两次 unsigned fixture build",
  "输入与已签名输出的当前证书 SHA-256 完全相同",
]) {
  requirePhrase(m301Text, phrase, "docs/tasks/M3-01-android-fixtures.md");
}

if (!(dependencyGraph.get("M3-03") ?? []).includes("M2-06")) {
  errors.push("M3-03 must depend on M2-06 before comparing final Runtime bytes");
}

const validationSkillText = readUtf8(
  path.join(root, ".agents", "skills", "validate-protected-apk", "SKILL.md"),
);
for (const phrase of [
  "`pre-cli`",
  "`full-flow`",
  "Do not invent, stub, or prematurely expose a product CLI",
  "If the current task provides a synthetic fixture",
  "`fixture_validation: not_applicable`",
  "M0-03 must skip fixture execution",
]) {
  requirePhrase(validationSkillText, phrase, ".agents/skills/validate-protected-apk/SKILL.md");
}

const m305Text = readUtf8(
  path.join(root, "docs", "tasks", "M3-05-size-startup-memory-benchmarks.md"),
);
for (const phrase of [
  "processToApplicationOnCreateMs",
  "processToInteractiveMs",
  "peakPssBytes",
  "nativeHeapPeakBytes",
  "outputExternallySignedApkBytes",
  "bootstrapDexBytes",
  "selectedRuntimeAbiBytes",
  "fourAbiRuntimeBaselineBytes",
  "containerMetadataBytes",
]) {
  requirePhrase(m305Text, phrase, "docs/tasks/M3-05-size-startup-memory-benchmarks.md");
}

const m401Text = readUtf8(
  path.join(root, "docs", "tasks", "M4-01-security-and-supply-chain-review.md"),
);
for (const phrase of ["rc-component-manifest.json", "最终 archive 不属于本任务前置条件"]) {
  requirePhrase(m401Text, phrase, "docs/tasks/M4-01-security-and-supply-chain-review.md");
}

const m403Text = readUtf8(
  path.join(root, "docs", "tasks", "M4-03-release-evidence-and-documentation.md"),
);
for (const phrase of ["work/input/signed-app.apk", "包外挂载"]) {
  requirePhrase(m403Text, phrase, "docs/tasks/M4-03-release-evidence-and-documentation.md");
}
if (m403Text.includes("fixtures/java-single-dex.apk")) {
  errors.push("M4-03 Quickstart must not reference a fixture path absent from release archives");
}

for (const taskName of [
  "M1-02-signer-policy.md",
  "M1-04-encrypted-dex-container.md",
  "M1-05-apk-repacker-and-alignment.md",
]) {
  const rel = `docs/tasks/${taskName}`;
  if (/\bSignerPolicy\b(?!V1)/.test(readUtf8(path.join(root, rel)))) {
    errors.push(`${rel}: use the frozen public type SignerPolicyV1, not SignerPolicy`);
  }
}

for (const extra of [
  ".agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs",
  ".agents/skills/coordinate-project-handoff/references/handoff-schema.md",
  ".agents/skills/coordinate-project-handoff/assets/worker-handoff-template.md",
]) {
  if (!fs.existsSync(path.join(root, extra))) errors.push(`Missing handoff Skill resource: ${extra}`);
}

for (const tool of [
  "tools/governance/validate-project-package.mjs",
  "tools/governance/hash-project-package.mjs",
  "tools/governance/test-handoff-validator.mjs",
]) {
  if (!fs.existsSync(path.join(root, tool))) errors.push(`Missing governance tool: ${tool}`);
}

const expectedCoreDocs = [
  "docs/README_FIRST.md",
  "docs/PROJECT_PLAN.md",
  "docs/PRODUCT_REQUIREMENTS.md",
  "docs/ARCHITECTURE.md",
  "docs/THREAT_MODEL.md",
  "docs/DEVELOPMENT.md",
  "docs/TEST_STRATEGY.md",
  "docs/TOOLCHAIN_AND_PROVENANCE.md",
  "docs/HANDOFF_SPEC.md",
  "docs/ROADMAP.md",
  "docs/COMPATIBILITY_MATRIX.md",
];
for (const file of expectedCoreDocs) {
  if (!fs.existsSync(path.join(root, file))) errors.push(`Missing core document: ${file}`);
}

const expectedAdrs = [
  "docs/adr/0001-apk-postprocessing-only.md",
  "docs/adr/0002-unsigned-output-only.md",
  "docs/adr/0003-api29-public-classloader-hook.md",
  "docs/adr/0004-versioned-encrypted-dex-container.md",
  "docs/adr/0005-runtime-abi-policy.md",
  "docs/adr/0006-offline-key-protection-boundary.md",
  "docs/adr/0007-source-dir-startup-configuration.md",
  "docs/adr/0008-chunk-authenticated-dex-container.md",
  "docs/adr/0009-native-cryptography-backend.md",
];
const adrHeadings = [
  "## Status",
  "## Context",
  "## Decision",
  "## Consequences",
  "## Rejected Alternatives",
  "## Security Impact",
  "## Compatibility Impact",
  "## Verification",
];
for (const name of expectedAdrs) {
  const file = path.join(root, name);
  if (!fs.existsSync(file)) {
    errors.push(`Missing ADR: ${name}`);
  } else {
    checkHeadingOrder(readUtf8(file), adrHeadings, file);
  }
}

for (const file of walk(root)) {
  const rel = relative(file);
  if (rel.startsWith("work/") || rel.startsWith(".git/")) continue;
  if (!/\.(?:md|yml|yaml|json|mjs)$/.test(rel)) continue;
  const text = readUtf8(file);
  if (rel.startsWith("docs/") && /\b(?:TODO|TBD)\b/i.test(text)) {
    errors.push(`${rel}: unresolved placeholder marker`);
  }
  if (/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(text)) {
    errors.push(`${rel}: contains private-key material`);
  }
  if (/(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16})/.test(text)) {
    errors.push(`${rel}: contains a value resembling an access token`);
  }
  if (/(?:[A-Za-z]:[\\/](?:Users|Documents|works)[\\/]|\/(?:Users|home)\/[^/\s]+)/.test(text)) {
    errors.push(`${rel}: contains a user-directory absolute path`);
  }
  if (rel.endsWith(".md")) validateMarkdownLinks(text, file);
}

if (requireGovernanceOnly) {
  for (const forbidden of [
    "cli",
    "host",
    "host-core",
    "runtime",
    "fixtures",
    "test-fixtures",
    "integration-tests",
    "benchmarks",
    "distribution",
  ]) {
    if (fs.existsSync(path.join(root, forbidden))) {
      errors.push(`Business implementation directory must not exist in the governance-only snapshot: ${forbidden}`);
    }
  }

  for (const file of walk(root)) {
    const rel = relative(file);
    if (rel.startsWith("work/") || rel.startsWith(".git/")) continue;
    if (/\.(?:apk|aab|apks|dex|aar|so|class|java|kt|c|cc|cpp|h|hpp)$/i.test(rel)) {
      errors.push(`Business implementation or binary artifact is forbidden in the governance-only snapshot: ${rel}`);
    }
  }
}

if (errors.length > 0) {
  for (const error of errors) console.error(`ERROR: ${error}`);
  process.exit(1);
}
console.log(
  `OK: ${expectedTasks.length} task cards, ${expectedCoreDocs.length} core docs, `
  + `${expectedAdrs.length} ADRs${requireGovernanceOnly ? ", governance-only snapshot" : ""}`,
);

function readUtf8(file) {
  const text = fs.readFileSync(file, "utf8");
  if (text.includes("\uFFFD")) errors.push(`${relative(file)}: contains a Unicode replacement character`);
  return text;
}

function requirePhrase(text, phrase, file) {
  if (!text.includes(phrase)) errors.push(`${file}: missing frozen contract phrase: ${phrase}`);
}

function requireOrderedPhrases(text, phrases, file, contractName) {
  let previous = -1;
  for (const phrase of phrases) {
    const current = text.indexOf(phrase, previous + 1);
    if (current < 0) {
      errors.push(`${file}: ${contractName} is missing ordered phrase: ${phrase}`);
      return;
    }
    previous = current;
  }
}

function parseFrontmatter(text, file) {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
  if (!match) {
    errors.push(`${relative(file)}: missing frontmatter`);
    return {};
  }
  const result = {};
  const lines = match[1].split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    const top = lines[i].match(/^([a-z_]+):\s*(.*)$/);
    if (!top) continue;
    const [, key, raw] = top;
    if (raw.trim()) {
      result[key] = raw.trim().replace(/^["']|["']$/g, "");
      continue;
    }
    const items = [];
    while (i + 1 < lines.length) {
      const item = lines[i + 1].match(/^\s+-\s+(.+?)\s*$/);
      if (!item) break;
      items.push(item[1].replace(/^["']|["']$/g, ""));
      i += 1;
    }
    result[key] = items;
  }
  return result;
}

function frontmatterKeys(text) {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
  if (!match) return [];
  return match[1]
    .split(/\r?\n/)
    .map((line) => line.match(/^([a-z_]+):/))
    .filter(Boolean)
    .map((matchLine) => matchLine[1]);
}

function parseList(value) {
  if (Array.isArray(value)) return value.filter((item) => item !== "None");
  if (value === undefined || value === "" || value === "[]" || value === "None") return [];
  const stripped = value.replace(/^\[|\]$/g, "");
  return stripped.split(",").map((item) => item.trim().replace(/^["']|["']$/g, "")).filter(Boolean);
}

function checkHeadingOrder(text, headings, file) {
  let previous = -1;
  for (let i = 0; i < headings.length; i += 1) {
    const heading = headings[i];
    const matches = [...text.matchAll(new RegExp(`^${escapeRegExp(heading)}$`, "gm"))];
    if (matches.length !== 1) {
      errors.push(`${relative(file)}: heading must occur exactly once: ${heading}`);
      continue;
    }
    if (matches[0].index <= previous) errors.push(`${relative(file)}: heading out of order: ${heading}`);
    const nextHeading = headings[i + 1];
    const sectionStart = matches[0].index + heading.length;
    const sectionEnd = nextHeading ? text.indexOf(`\n${nextHeading}`, sectionStart) : text.length;
    if (sectionEnd >= 0 && text.slice(sectionStart, sectionEnd).trim() === "") {
      errors.push(`${relative(file)}: section must not be empty: ${heading}`);
    }
    previous = matches[0].index;
  }
}

function checkCycles(graph) {
  const visiting = new Set();
  const visited = new Set();
  function visit(id, trail) {
    if (visiting.has(id)) {
      errors.push(`Dependency cycle: ${[...trail, id].join(" -> ")}`);
      return;
    }
    if (visited.has(id)) return;
    visiting.add(id);
    for (const dep of graph.get(id) ?? []) {
      if (graph.has(dep)) visit(dep, [...trail, id]);
    }
    visiting.delete(id);
    visited.add(id);
  }
  for (const id of graph.keys()) visit(id, []);
}

function validateMarkdownLinks(text, file) {
  const regex = /\[[^\]]*]\(([^)]+)\)/g;
  for (const match of text.matchAll(regex)) {
    const target = match[1].trim().replace(/^<|>$/g, "").split("#")[0];
    if (!target || /^(?:https?:|mailto:)/i.test(target)) continue;
    let decoded;
    try {
      decoded = decodeURIComponent(target);
    } catch {
      errors.push(`${relative(file)}: invalid percent-encoding in link ${match[1]}`);
      continue;
    }
    const resolved = path.resolve(path.dirname(file), decoded);
    if (!fs.existsSync(resolved)) errors.push(`${relative(file)}: broken link ${match[1]}`);
  }
}

function walk(dir) {
  const result = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if ([
      ".git",
      ".gradle",
      ".toolchains",
      ".cxx",
      ".externalNativeBuild",
      "artifacts",
      "build",
      "reports",
      "work",
    ].includes(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) result.push(...walk(full));
    else result.push(full);
  }
  return result;
}

function relative(file) {
  return path.relative(root, file).replaceAll("\\", "/");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
