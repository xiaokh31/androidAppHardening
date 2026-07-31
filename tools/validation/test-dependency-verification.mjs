#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const root = process.cwd();
const temporaryParent = os.tmpdir();
const temporaryRoot = fs.mkdtempSync(path.join(temporaryParent, "aah-dependency-verification-"));

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
        "artifacts",
        "build",
        "reports",
        "tmp",
      ].some((excluded) => parts.includes(excluded));
    },
  });

  const metadataFile = path.join(temporaryRoot, "gradle", "verification-metadata.xml");
  const originalMetadata = fs.readFileSync(metadataFile, "utf8");
  const componentPattern = /(<component group="com\.android\.tools\.build" name="gradle"[\s\S]*?<sha256 value=")([0-9a-f]{64})(")/;
  if (!componentPattern.test(originalMetadata)) {
    throw new Error("Pinned AGP checksum was not found in verification metadata");
  }
  const tamperedMetadata = originalMetadata.replace(componentPattern, `$1${"0".repeat(64)}$3`);
  fs.writeFileSync(metadataFile, tamperedMetadata, "utf8");

  const failing = runGradle(temporaryRoot, ["--no-daemon", "--refresh-dependencies", "help"]);
  if (failing.status === 0 || !`${failing.stdout}\n${failing.stderr}`.includes("Dependency verification failed")) {
    throw new Error(`Tampered checksum did not fail closed; exit_code=${failing.status}`);
  }

  fs.writeFileSync(metadataFile, originalMetadata, "utf8");
  const restored = runGradle(temporaryRoot, ["--no-daemon", "help"]);
  if (restored.status !== 0) {
    throw new Error(`Restored verification metadata failed; exit_code=${restored.status}`);
  }

  console.log(`OK: dependency verification tamper_exit=${failing.status} restored_exit=${restored.status}`);
} finally {
  const resolvedTemporary = path.resolve(temporaryRoot);
  const resolvedParent = path.resolve(temporaryParent);
  if (resolvedTemporary.startsWith(`${resolvedParent}${path.sep}`)) {
    fs.rmSync(resolvedTemporary, { recursive: true, force: true });
  }
}

function runGradle(directory, args) {
  const javaHome = process.env.JAVA_HOME;
  const javaExecutable = javaHome
    ? path.join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java")
    : "java";
  const wrapperJar = path.join(directory, "gradle", "wrapper", "gradle-wrapper.jar");
  return spawnSync(javaExecutable, [
    "-classpath",
    wrapperJar,
    "org.gradle.wrapper.GradleWrapperMain",
    ...args,
  ], {
    cwd: directory,
    encoding: "utf8",
    env: process.env,
    timeout: 300_000,
  });
}
