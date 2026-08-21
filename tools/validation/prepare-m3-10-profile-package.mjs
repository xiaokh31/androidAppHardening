#!/usr/bin/env node

import { createHash, randomBytes } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const CANONICAL_BASELINE = { size: 29962, sha256: "4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf" };
const CANONICAL_PROTECTED = { size: 1287876, sha256: "1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9" };
const SIGNER_CONTEXT = Buffer.from("M3-10-PROFILE-SIGNER-V1\0", "utf8");

function fail(message) { throw new Error(`M3-10 profile preparation failed: ${message}`); }
function sha256(value) { return createHash("sha256").update(value).digest("hex"); }
function sha256File(file) { return sha256(readFileSync(file)); }
function optionsOf(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) fail("options must be --name value pairs");
    result[values[index].slice(2)] = values[index + 1];
  }
  return result;
}
function required(options, name) { if (!options[name]) fail(`--${name} is required`); return options[name]; }

function run(command, args, env, label, timeout = 300_000) {
  let executable = command;
  let commandArgs = args;
  if (process.platform === "win32" && /\.(?:bat|cmd)$/i.test(command)) {
    executable = process.env.ComSpec ?? "C:\\Windows\\System32\\cmd.exe";
    commandArgs = ["/d", "/c", command, ...args];
  }
  const result = spawnSync(executable, commandArgs, {
    cwd: process.cwd(), env, encoding: "utf8", windowsHide: true, timeout, maxBuffer: 32 * 1024 * 1024,
  });
  if (result.error || result.status !== 0) {
    fail(`${label} exited ${result.status ?? "START"}: ${(result.stderr || result.stdout || result.error?.message || "").slice(0, 2000)}`);
  }
  return `${result.stdout ?? ""}${result.stderr ?? ""}`;
}

function tools() {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  const javaHome = process.env.JAVA_HOME;
  if (!sdk || !javaHome) fail("ANDROID_HOME/ANDROID_SDK_ROOT and JAVA_HOME are required");
  const buildTools = path.join(sdk, "build-tools", "36.1.0");
  const java = path.join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java");
  const javac = path.join(javaHome, "bin", process.platform === "win32" ? "javac.exe" : "javac");
  const keytool = path.join(javaHome, "bin", process.platform === "win32" ? "keytool.exe" : "keytool");
  const gradle = path.resolve(process.platform === "win32" ? "gradlew.bat" : "gradlew");
  return {
    java, javac, keytool, gradle,
    androidJar: path.join(sdk, "platforms", "android-36", "android.jar"),
    d8Jar: path.join(buildTools, "lib", "d8.jar"),
    apksignerJar: path.join(buildTools, "lib", "apksigner.jar"),
    zipalign: path.join(buildTools, process.platform === "win32" ? "zipalign.exe" : "zipalign"),
  };
}

function regularLocked(file, expected, label) {
  if (!existsSync(file) || !statSync(file).isFile() || statSync(file).size !== expected.size || sha256File(file) !== expected.sha256) {
    fail(`${label} does not match the M3-11 canonical lock`);
  }
}

function sign(toolchain, input, output, keystore, alias, env) {
  run(toolchain.java, ["-jar", toolchain.apksignerJar, "sign", "--v1-signing-enabled", "false",
    "--v2-signing-enabled", "false", "--v3-signing-enabled", "true", "--v4-signing-enabled", "false",
    "--alignment-preserved", "true", "--ks", keystore, "--ks-key-alias", alias,
    "--ks-pass", "env:M310_PROFILE_PASS", "--key-pass", "env:M310_PROFILE_PASS", "--out", output, input], env, "apksigner sign");
  run(toolchain.java, ["-jar", toolchain.apksignerJar, "verify", "--verbose", "--min-sdk-version", "29", output], env,
    "apksigner verify");
}

function deriveOnce(toolchain, root, label, baseline, protectedApk, observerDex, seedFile, signerDigest, env) {
  const directory = path.join(root, `derive-${label}`);
  run(toolchain.gradle, [":host:container:m310CanonicalProfiles", "--offline", "--no-daemon",
    `-Pm310BaselineApk=${baseline}`, `-Pm310ProtectedApk=${protectedApk}`,
    `-Pm310ObserverDex=${observerDex}`, `-Pm310OutputDirectory=${directory}`],
  { ...env, M310_SECRET_SEED: seedFile, M310_SIGNER_SHA256: signerDigest }, `derive ${label}`, 600_000);
  return directory;
}

function equalFiles(left, right, label) {
  if (statSync(left).size !== statSync(right).size || sha256File(left) !== sha256File(right)) fail(`${label} is not deterministic`);
}

function filesBelow(root, suffix) {
  const result = [];
  const stack = [root];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      const file = path.join(current, entry.name);
      if (entry.isDirectory()) stack.push(file);
      else if (entry.isFile() && entry.name.endsWith(suffix)) result.push(file);
    }
  }
  return result.sort();
}

function main(options) {
  const repository = process.cwd();
  const output = path.resolve(required(options, "output"));
  const buildRoot = path.resolve(repository, "build", "m3-10");
  if (!(output + path.sep).startsWith(buildRoot + path.sep) || existsSync(output)) {
    fail("output must be a new directory below build/m3-10");
  }
  const baseline = path.resolve(required(options, "original-baseline"));
  const protectedApk = path.resolve(required(options, "original-protected"));
  regularLocked(baseline, CANONICAL_BASELINE, "baseline");
  regularLocked(protectedApk, CANONICAL_PROTECTED, "protected");
  const toolchain = tools();
  const requiredTools = Object.values(toolchain);
  if (requiredTools.some((file) => !existsSync(file))) fail("a pinned Java/Android/Gradle tool is missing");
  mkdirSync(output, { recursive: true });
  const secretRoot = path.join(output, ".signing-secret");
  mkdirSync(secretRoot);
  let complete = false;
  try {
    const observerSource = path.resolve("tools/validation/m3-10/profile-src/ah/runtime/profile/M310StartupTimingObserver.java");
    const observerClasses = path.join(output, "observer-classes");
    const observerDexRoot = path.join(output, "observer-dex");
    mkdirSync(observerClasses); mkdirSync(observerDexRoot);
    run(toolchain.javac, ["-source", "17", "-target", "17", "-classpath", toolchain.androidJar,
      "-d", observerClasses, observerSource], process.env, "javac observer");
    const observerClassFiles = filesBelow(observerClasses, ".class");
    if (observerClassFiles.length !== 1) fail("observer javac output differs");
    run(toolchain.java, ["-cp", toolchain.d8Jar, "com.android.tools.r8.D8", "--min-api", "29", "--lib",
      toolchain.androidJar, "--output", observerDexRoot, ...observerClassFiles], process.env, "d8 observer");
    const observerDex = path.join(observerDexRoot, "classes.dex");
    const password = randomBytes(24).toString("base64url");
    const alias = "m310-profile";
    const keystore = path.join(secretRoot, "profile.p12");
    const cert = path.join(secretRoot, "profile.der");
    const env = { ...process.env, M310_PROFILE_PASS: password };
    run(toolchain.keytool, ["-genkeypair", "-storetype", "PKCS12", "-keystore", keystore, "-alias", alias,
      "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=M3-10 test-only",
      "-storepass:env", "M310_PROFILE_PASS", "-keypass:env", "M310_PROFILE_PASS"], env, "keytool generate");
    run(toolchain.keytool, ["-exportcert", "-keystore", keystore, "-alias", alias, "-file", cert,
      "-storepass:env", "M310_PROFILE_PASS"], env, "keytool export");
    const signerDigest = sha256File(cert);
    const signerCommitment = sha256(Buffer.concat([SIGNER_CONTEXT, Buffer.from(signerDigest, "hex")]));
    const seedFile = path.join(secretRoot, "container-seed.bin");
    writeFileSync(seedFile, randomBytes(32), { flag: "wx" });
    const first = deriveOnce(toolchain, output, "a", baseline, protectedApk, observerDex, seedFile, signerDigest, env);
    const second = deriveOnce(toolchain, output, "b", baseline, protectedApk, observerDex, seedFile, signerDigest, env);
    for (const name of ["profile-baseline-unsigned.apk", "profile-protected-unsigned.apk", "derivation-manifest.json"]) {
      equalFiles(path.join(first, name), path.join(second, name), name);
    }
    for (const [role, name] of [["baseline", "profile-baseline-unsigned.apk"], ["protected", "profile-protected-unsigned.apk"]]) {
      for (const directory of [first, second]) {
        const aligned = path.join(directory, `profile-${role}-aligned.apk`);
        const signed = path.join(directory, `profile-${role}.apk`);
        run(toolchain.zipalign, ["-f", "-P", "16", "4096", path.join(directory, name), aligned], env, `zipalign ${role}`);
        run(toolchain.zipalign, ["-c", "-P", "16", "4096", aligned], env, `zipalign verify ${role}`);
        sign(toolchain, aligned, signed, keystore, alias, env);
      }
      equalFiles(path.join(first, `profile-${role}-aligned.apk`), path.join(second, `profile-${role}-aligned.apk`), `${role} aligned APK`);
      equalFiles(path.join(first, `profile-${role}.apk`), path.join(second, `profile-${role}.apk`), `${role} signed APK`);
    }
    const finalRoot = path.join(output, "package");
    mkdirSync(finalRoot);
    copyFileSync(observerDex, path.join(finalRoot, "observer.dex"));
    for (const name of ["profile-baseline-unsigned.apk", "profile-protected-unsigned.apk", "derivation-manifest.json",
      "profile-baseline-aligned.apk", "profile-protected-aligned.apk", "profile-baseline.apk", "profile-protected.apk"]) {
      copyFileSync(path.join(first, name), path.join(finalRoot, name));
    }
    writeFileSync(path.join(finalRoot, "preparation-report.json"), `${JSON.stringify({
      schemaVersion: 1, taskId: "M3-10", canonical: { baseline: CANONICAL_BASELINE, protected: CANONICAL_PROTECTED },
      observer: { sizeBytes: statSync(observerDex).size, sha256: sha256File(observerDex) },
      signer: { certificateSha256Prefix: signerDigest.slice(0, 12), commitment: signerCommitment },
      deterministicDerivations: 2, buildTools: "36.1.0", minSdk: 29, zipalignPageBytes: 4096,
      v3Only: true, signingSecretsPublished: false, result: "PASS",
    }, null, 2)}\n`);
    complete = true;
  } finally {
    rmSync(secretRoot, { recursive: true, force: true });
    if (existsSync(secretRoot)) fail("temporary signing root cleanup failed");
    if (!complete) rmSync(output, { recursive: true, force: true });
  }
  process.stdout.write(`${JSON.stringify({ status: "PASS", output: path.relative(repository, output).replaceAll("\\", "/"), temporarySigningAbsent: true })}\n`);
}

try { main(optionsOf(process.argv.slice(2))); }
catch (error) { process.stderr.write(`${error.stack ?? error}\n`); process.exitCode = 1; }
