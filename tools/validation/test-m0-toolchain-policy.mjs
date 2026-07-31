#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { validate } from "./verify-m0-toolchain.mjs";

const root = process.cwd();
const baseline = validate(root);
if (baseline.length > 0) {
  throw new Error(`Baseline validation failed:\n${baseline.join("\n")}`);
}
const frozenSkeletonErrors = validate(root, { requireEmptySkeleton: true });
if (!frozenSkeletonErrors.some((error) => error.includes("M0-03 must not contain business source"))) {
  throw new Error("M0-03 empty-source mode did not reject post-M0-03 business source");
}

runTamperCase(
  "project repository injection",
  "settings.gradle.kts",
  (text) => text.replace("mavenCentral()", "mavenCentral()\n        mavenLocal()"),
  "Forbidden repository declaration",
);
runTamperCase(
  "floating Action tag",
  ".github/workflows/build.yml",
  (text) => text.replace(
    "actions/cache@caa296126883cff596d87d8935842f9db880ef25",
    "actions/cache@v5",
  ),
  "must use a full commit SHA",
);
runTamperCase(
  "toolchain version drift",
  "gradle/libs.versions.toml",
  (text) => text.replace('kotlin = "2.4.10"', 'kotlin = "2.4.11"'),
  "kotlin must be 2.4.10",
);

console.log("OK: toolchain policy positive, stage-aware source, and tamper cases");

function runTamperCase(name, relativeFile, mutate, expectedError) {
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "aah-m0-03-policy-"));
  try {
    fs.cpSync(root, temporaryRoot, {
      recursive: true,
      filter: (source) => {
        const relative = path.relative(root, source).replaceAll("\\", "/");
        const parts = relative.split("/");
        return ![
          ".git",
          ".gradle",
          ".toolchains",
          ".cxx",
          ".externalNativeBuild",
          "build",
        ].some((excluded) => parts.includes(excluded));
      },
    });
    const target = path.join(temporaryRoot, relativeFile);
    fs.writeFileSync(target, mutate(fs.readFileSync(target, "utf8")), "utf8");
    const errors = validate(temporaryRoot);
    if (!errors.some((error) => error.includes(expectedError))) {
      throw new Error(`${name}: expected error containing "${expectedError}", got ${errors.join("; ")}`);
    }
  } finally {
    if (temporaryRoot.startsWith(os.tmpdir())) {
      fs.rmSync(temporaryRoot, { recursive: true, force: true });
    }
  }
}
