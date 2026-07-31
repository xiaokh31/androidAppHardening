#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();
const args = process.argv.slice(2);
const excludedDirectories = new Set([".git", "work"]);
const excludedFiles = new Set(["HandOff.md"]);

let commit = null;
if (args.length > 0) {
  if (args.length !== 2 || args[0] !== "--commit" || !/^[0-9a-f]{40}$/.test(args[1])) {
    console.error("Usage: hash-project-package.mjs [--commit <40-character-lowercase-sha>]");
    process.exit(2);
  }
  commit = args[1];
}

const entries = commit ? entriesFromCommit(commit) : entriesFromWorktree();
const fileManifest = entries.map(({ file, bytes }) => ({
  sha256: sha256(bytes),
  file,
}));
const manifest = fileManifest
  .map(({ file, sha256: fileHash }) => `${fileHash}  ${file}`)
  .join("\n")
  .concat("\n");

const result = {
  schema_version: 1,
  algorithm: "SHA-256",
  source: commit ? "commit" : "worktree",
  ...(commit ? { commit } : {}),
  excludes: [".git/", "work/", "HandOff.md"],
  file_count: entries.length,
  aggregate_sha256: crypto.createHash("sha256").update(manifest, "utf8").digest("hex"),
  files: fileManifest,
};

console.log(JSON.stringify(result, null, 2));

function entriesFromWorktree() {
  return walk(root)
    .map((file) => ({
      file: path.relative(root, file).replaceAll("\\", "/"),
      bytes: fs.readFileSync(file),
    }))
    .filter(({ file }) => !excludedFiles.has(file))
    .sort((left, right) => left.file.localeCompare(right.file, "en"));
}

function entriesFromCommit(sha) {
  const type = git(["cat-file", "-t", sha]);
  if (type.status !== 0 || type.stdout.toString("utf8").trim() !== "commit") {
    failGit(`Commit does not exist: ${sha}`, type);
  }

  const listing = git(["ls-tree", "-r", "-z", "--name-only", sha]);
  if (listing.status !== 0) failGit(`Unable to list commit tree: ${sha}`, listing);
  const files = listing.stdout
    .toString("utf8")
    .split("\0")
    .filter(Boolean)
    .filter((file) => !excludedFiles.has(file) && !file.startsWith("work/"))
    .sort((left, right) => left.localeCompare(right, "en"));

  return files.map((file) => {
    const blob = git(["show", `${sha}:${file}`]);
    if (blob.status !== 0) failGit(`Unable to read ${file} from ${sha}`, blob);
    return { file, bytes: blob.stdout };
  });
}

function walk(directory) {
  const result = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && excludedDirectories.has(entry.name)) continue;
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) result.push(...walk(fullPath));
    else if (entry.isFile()) result.push(fullPath);
  }
  return result;
}

function sha256(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function git(gitArgs) {
  return spawnSync("git", gitArgs, {
    cwd: root,
    encoding: "buffer",
    windowsHide: true,
  });
}

function failGit(message, result) {
  console.error(message);
  const stderr = result.stderr?.toString("utf8").trim();
  if (stderr) console.error(stderr);
  process.exit(1);
}
