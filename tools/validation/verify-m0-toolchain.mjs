#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const EXPECTED_MODULES = [
  ":host:cli",
  ":host:apk-inspector",
  ":host:axml",
  ":host:container",
  ":host:repacker",
  ":runtime:bootstrap",
  ":runtime:native",
  ":runtime:policy",
  ":fixtures:android",
  ":integration-tests",
  ":benchmarks:host",
  ":benchmarks:android",
  ":tools:validation",
  ":distribution",
];

const EXPECTED_VERSIONS = new Map([
  ["agp", "9.3.0"],
  ["android-build-tools", "36.1.0"],
  ["android-cmake", "4.1.2"],
  ["android-compile-sdk", "36"],
  ["android-min-sdk", "29"],
  ["android-ndk", "29.0.14206865"],
  ["android-target-sdk", "36"],
  ["gradle", "9.5.0"],
  ["jdk", "17.0.19+10"],
  ["kotlin", "2.4.10"],
  ["node", "24.12.0"],
]);

export function validate(root, { requireEmptySkeleton = false } = {}) {
  const errors = [];
  const read = (relativePath) => {
    const file = path.join(root, relativePath);
    if (!fs.existsSync(file)) {
      errors.push(`Missing required file: ${relativePath}`);
      return "";
    }
    const text = fs.readFileSync(file, "utf8");
    if (text.includes("\uFFFD")) errors.push(`${relativePath}: contains a Unicode replacement character`);
    return text;
  };

  const settings = read("settings.gradle.kts");
  const actualModules = [...settings.matchAll(/"(:[a-z0-9-]+(?::[a-z0-9-]+)*)"/g)]
    .map((match) => match[1]);
  if (actualModules.join("|") !== EXPECTED_MODULES.join("|")) {
    errors.push(`Module graph mismatch: expected ${EXPECTED_MODULES.join(", ")}, got ${actualModules.join(", ")}`);
  }
  for (const forbiddenRepository of ["mavenLocal()", "gradlePluginPortal()", "jcenter()"]) {
    if (settings.includes(forbiddenRepository)) {
      errors.push(`Forbidden repository declaration: ${forbiddenRepository}`);
    }
  }
  if (!settings.includes("RepositoriesMode.FAIL_ON_PROJECT_REPOS")) {
    errors.push("settings.gradle.kts must fail on project-level repositories");
  }

  const catalog = read("gradle/libs.versions.toml");
  for (const [key, expected] of EXPECTED_VERSIONS) {
    const match = catalog.match(new RegExp(`^${escapeRegExp(key)}\\s*=\\s*"([^"]+)"$`, "m"));
    if (!match || match[1] !== expected) {
      errors.push(`gradle/libs.versions.toml: ${key} must be ${expected}`);
    }
  }

  const properties = read("gradle.properties");
  for (const required of [
    "org.gradle.caching=true",
    "org.gradle.configuration-cache=true",
    "org.gradle.dependency.verification=strict",
  ]) {
    if (!properties.includes(required)) errors.push(`gradle.properties: missing ${required}`);
  }

  const wrapperProperties = read("gradle/wrapper/gradle-wrapper.properties");
  if (!wrapperProperties.includes("gradle-9.5.0-bin.zip")) {
    errors.push("Gradle Wrapper distribution must be gradle-9.5.0-bin.zip");
  }
  if (!wrapperProperties.includes(
    "distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746",
  )) {
    errors.push("Gradle Wrapper distribution SHA-256 is missing or incorrect");
  }

  const wrapperJar = path.join(root, "gradle", "wrapper", "gradle-wrapper.jar");
  const wrapperChecksum = read("gradle/wrapper/gradle-wrapper.jar.sha256").trim();
  if (fs.existsSync(wrapperJar) && /^[0-9a-f]{64}$/.test(wrapperChecksum)) {
    const actual = crypto.createHash("sha256").update(fs.readFileSync(wrapperJar)).digest("hex");
    if (actual !== wrapperChecksum) errors.push("Gradle Wrapper JAR SHA-256 mismatch");
  } else if (fs.existsSync(wrapperJar)) {
    errors.push("Gradle Wrapper JAR checksum must be a lowercase SHA-256");
  }

  const verificationMetadata = read("gradle/verification-metadata.xml");
  if (!/<verification-metadata\b/.test(verificationMetadata) || !/<sha256\b/.test(verificationMetadata)) {
    errors.push("Gradle verification metadata must contain SHA-256 artifact checksums");
  }
  for (const lockfile of [
    "settings-gradle.lockfile",
    "host/cli/gradle.lockfile",
    "host/apk-inspector/gradle.lockfile",
    "host/axml/gradle.lockfile",
    "host/container/gradle.lockfile",
    "host/repacker/gradle.lockfile",
    "runtime/bootstrap/gradle.lockfile",
    "runtime/native/gradle.lockfile",
    "runtime/policy/gradle.lockfile",
    "fixtures/android/gradle.lockfile",
    "integration-tests/gradle.lockfile",
    "benchmarks/host/gradle.lockfile",
    "benchmarks/android/gradle.lockfile",
    "tools/validation/gradle.lockfile",
    "distribution/gradle.lockfile",
  ]) {
    const lockText = read(lockfile);
    if (!lockText.includes("This is a Gradle generated file")) {
      errors.push(`${lockfile}: missing generated dependency lock state`);
    }
  }

  const buildFiles = walk(root)
    .filter((file) => /(?:build|settings)\.gradle\.kts$/.test(file) || file.endsWith("libs.versions.toml"));
  for (const file of buildFiles) {
    const relative = path.relative(root, file).replaceAll("\\", "/");
    const text = fs.readFileSync(file, "utf8");
    if (/mavenLocal\s*\(|jcenter\s*\(|SNAPSHOT/i.test(text)) {
      errors.push(`${relative}: contains a forbidden repository or snapshot`);
    }
    if (/(?:version|implementation|api|classpath)\s*(?:=|\()\s*["'][^"']*\+[^"']*["']/i.test(text)) {
      errors.push(`${relative}: contains a dynamic dependency version`);
    }
  }

  for (const workflow of walk(path.join(root, ".github", "workflows")).filter((file) => /\.ya?ml$/.test(file))) {
    const relative = path.relative(root, workflow).replaceAll("\\", "/");
    const text = fs.readFileSync(workflow, "utf8");
    for (const match of text.matchAll(/^\s*uses:\s*([^@\s]+)@([^\s#]+).*$/gm)) {
      if (!/^[0-9a-f]{40}$/.test(match[2])) {
        errors.push(`${relative}: Action ${match[1]} must use a full commit SHA`);
      }
    }
    if (!/permissions:\s*\r?\n\s+contents:\s+read/m.test(text)) {
      errors.push(`${relative}: workflow must declare contents: read`);
    }
    if (relative === ".github/workflows/build.yml") {
      for (const cacheInput of [
        "**/*gradle.lockfile",
        "gradle/verification-metadata.xml",
        "gradle/wrapper/gradle-wrapper.properties",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/libs.versions.toml",
      ]) {
        if (!text.includes(cacheInput)) errors.push(`${relative}: cache key must include ${cacheInput}`);
      }
    }
  }

  if (requireEmptySkeleton) {
    const sourceFiles = walk(root).filter((file) =>
      /\/src\//.test(file.replaceAll("\\", "/"))
      && /\.(?:java|kt|kts|c|cc|cpp|h|hpp)$/i.test(file));
    const allowedSource = "runtime/native/src/main/cpp/empty.cpp";
    for (const file of sourceFiles) {
      const relative = path.relative(root, file).replaceAll("\\", "/");
      if (relative !== allowedSource) errors.push(`${relative}: M0-03 must not contain business source`);
    }
    const emptySource = read(allowedSource).trim();
    if (emptySource !== 'extern "C" void ah_runtime_empty_anchor() {}') {
      errors.push(`${allowedSource}: native skeleton must contain only the empty anchor`);
    }
  }

  return errors;
}

function walk(directory) {
  if (!fs.existsSync(directory)) return [];
  const result = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if ([".git", ".gradle", ".toolchains", "build", "artifacts", "reports"].includes(entry.name)) continue;
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) result.push(...walk(full));
    else result.push(full);
  }
  return result;
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const requireEmptySkeleton = process.argv.includes("--require-empty-skeleton");
  const errors = validate(process.cwd(), { requireEmptySkeleton });
  if (errors.length > 0) {
    for (const error of errors) console.error(`ERROR: ${error}`);
    process.exit(1);
  }
  const sourceScope = requireEmptySkeleton ? " and M0-03 empty-source boundary" : "";
  console.log(`OK: pinned toolchain and ${EXPECTED_MODULES.length} module graph${sourceScope}`);
}
