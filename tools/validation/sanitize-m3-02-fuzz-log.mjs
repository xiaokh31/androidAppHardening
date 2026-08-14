#!/usr/bin/env node

import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const target = path.resolve(process.argv[2] ?? "");
if (!target.startsWith(`${path.resolve("build")}${path.sep}`)) {
  throw new Error("M3-02 fuzz log must be under build/");
}
let content = readFileSync(target, "utf8");
const roots = [process.cwd(), process.env.GITHUB_WORKSPACE, process.env.RUNNER_TEMP,
  process.env.USERPROFILE, process.env.HOME].filter((value) => typeof value === "string" && value.length > 2);
for (const root of roots) {
  for (const variant of new Set([root, root.replaceAll("\\", "/"), root.replaceAll("/", "\\")])) {
    content = content.replaceAll(variant, "<redacted-root>");
  }
}
content = content
  .replace(/[A-Za-z]:[\\/](?:Users|a|actions-runner)[^\r\n\t ]*/gu, "<redacted-path>")
  .replace(/\/(?:home|Users|tmp|var\/folders)\/[^\r\n\t ]*/gu, "<redacted-path>");
writeFileSync(target, content);
if (/[A-Za-z]:[\\/](?:Users|a|actions-runner)|\/(?:home|Users|tmp|var\/folders)\//u.test(content)) {
  throw new Error("M3-02 fuzz log still contains a user or runner path");
}
process.stdout.write("OK: M3-02 fuzz log path sanitization\n");
