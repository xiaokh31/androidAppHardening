#!/usr/bin/env node

import { inflateRawSync } from "node:zlib";
import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import {
  centralRecord,
  isSignatureEntry,
  localRecord,
  normalizedEntry,
  readEntries,
  sha256,
} from "./create-m0-04-tampered-apks.mjs";

const EOCD_SIGNATURE = 0x06054b50;
const MANIFEST_ENTRY = "AndroidManifest.xml";

function fail(message) {
  throw new Error(`M1-03 device APK creation failed: ${message}`);
}

function run(command, args, options = {}) {
  let actualCommand = command;
  let actualArgs = args;
  if (command.toLowerCase().endsWith(".jar")) {
    const javaHome = process.env.JAVA_HOME;
    if (!javaHome) fail("JAVA_HOME is required to run pinned apksigner");
    actualCommand = path.join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java");
    actualArgs = ["-jar", command, ...args];
  }
  const result = spawnSync(actualCommand, actualArgs, {
    cwd: process.cwd(),
    encoding: "utf8",
    timeout: options.timeout ?? 180_000,
    maxBuffer: 8 * 1024 * 1024,
    windowsHide: true,
    env: process.env,
  });
  if (result.error || result.status !== 0) {
    fail(`${path.basename(command)} failed (${result.status}):\n${result.stdout ?? ""}\n${result.stderr ?? ""}`);
  }
  return `${result.stdout ?? ""}${result.stderr ?? ""}`;
}

function materialize(entry) {
  if (entry.method === 0) {
    return Buffer.from(entry.compressedData);
  }
  if (entry.method === 8) {
    const result = inflateRawSync(entry.compressedData);
    if (result.length !== entry.uncompressedSize) {
      fail("linked Manifest length differs from ZIP metadata");
    }
    return result;
  }
  fail(`unsupported linked Manifest compression method ${entry.method}`);
}

function rebuildApk(apk, manifest) {
  const entries = readEntries(apk);
  const manifestEntries = entries.filter((entry) => entry.name === MANIFEST_ENTRY);
  if (manifestEntries.length !== 1) {
    fail(`expected one Manifest, found ${manifestEntries.length}`);
  }
  const localRecords = [];
  const centralRecords = [];
  let localOffset = 0;
  for (const source of entries) {
    if (isSignatureEntry(source.name)) {
      continue;
    }
    const entry = normalizedEntry(source, source.name === MANIFEST_ENTRY ? manifest : undefined);
    const local = localRecord(entry);
    localRecords.push(local);
    centralRecords.push(centralRecord(entry, localOffset));
    localOffset += local.length;
  }
  const centralDirectory = Buffer.concat(centralRecords);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(EOCD_SIGNATURE, 0);
  eocd.writeUInt16LE(localRecords.length, 8);
  eocd.writeUInt16LE(localRecords.length, 10);
  eocd.writeUInt32LE(centralDirectory.length, 12);
  eocd.writeUInt32LE(localOffset, 16);
  return Buffer.concat([...localRecords, centralDirectory, eocd]);
}

function manifestSource(packageName, extracted) {
  return `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}"
    android:versionCode="1"
    android:versionName="1.0">
    <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36" />
    <application
        android:name="ah.fixtures.android.payload.PayloadApplication"
        android:appComponentFactory="ah.fixtures.android.payload.OriginalAppComponentFactory"
        android:extractNativeLibs="${extracted ? "true" : "false"}"
        android:hasCode="true"
        android:label="M1-03 AXML Fixture"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <meta-data android:name="ah.m103.fixture" android:value="preserved" />
        <provider
            android:name="ah.fixtures.android.payload.PayloadProvider"
            android:authorities="${packageName}.startup"
            android:exported="false"
            android:initOrder="100" />
        <activity
            android:name="ah.fixtures.android.payload.PayloadActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service android:name="ah.fixtures.android.payload.PayloadService" android:exported="false" />
        <receiver android:name="ah.fixtures.android.payload.PayloadReceiver" android:exported="false" />
    </application>
</manifest>
`;
}

function assertOutput(repository, output) {
  const roots = [path.join(repository, "build"), path.join(repository, "artifacts")];
  if (!roots.some((root) => output === root || output.startsWith(`${root}${path.sep}`))) {
    fail("output must be under the ignored build/ or artifacts/ tree");
  }
}

function androidTools() {
  const androidHome = process.env.ANDROID_HOME;
  if (!androidHome) {
    fail("ANDROID_HOME is required");
  }
  const buildTools = path.join(androidHome, "build-tools", "36.1.0");
  const executable = (name) => {
    if (process.platform !== "win32") return path.join(buildTools, name);
    return name === "apksigner"
      ? path.join(buildTools, "lib", "apksigner.jar")
      : path.join(buildTools, `${name}.exe`);
  };
  return { androidHome, executable };
}

async function prepareOriginal(variant, outputArgument) {
  const repository = process.cwd();
  const output = path.resolve(outputArgument);
  assertOutput(repository, output);
  const root = path.dirname(output);
  await mkdir(root, { recursive: true });
  const packageName = `ah.fixtures.android.m005.${variant}`;
  const source = path.join(root, `${variant}-original.xml`);
  const linked = path.join(root, `${variant}-original-manifest.apk`);
  await writeFile(source, manifestSource(packageName, variant === "extracted"));
  const { androidHome, executable } = androidTools();
  run(executable("aapt2"), [
    "link", "-o", linked, "--manifest", source,
    "-I", path.join(androidHome, "platforms", "android-36", "android.jar"),
  ]);
  const linkedEntries = readEntries(await readFile(linked));
  const linkedManifest = linkedEntries.filter((entry) => entry.name === MANIFEST_ENTRY);
  if (linkedManifest.length !== 1) fail("aapt2 linked APK does not contain one Manifest");
  const original = materialize(linkedManifest[0]);
  await writeFile(output, original);
  process.stdout.write(`${JSON.stringify({
    task_id: "M1-03",
    mode: "prepare",
    variant,
    package_name: packageName,
    original_manifest_sha256: sha256(original),
    result: "PASS",
  }, null, 2)}\n`);
}

async function packageDevice(inputArgument, variant, transformedArgument, outputArgument) {
  const repository = process.cwd();
  const input = path.resolve(inputArgument);
  const transformedPath = path.resolve(transformedArgument);
  const output = path.resolve(outputArgument);
  assertOutput(repository, output);
  if (input === output || transformedPath === output) fail("input, Manifest and output must differ");
  for (const name of ["M005_TEST_KEYSTORE", "M005_TEST_STORE_PASSWORD", "M005_TEST_KEY_ALIAS", "M005_TEST_KEY_PASSWORD"]) {
    if (!process.env[name]) fail(`${name} is required for the ignored test-only signing step`);
  }
  const packageName = `ah.fixtures.android.m005.${variant}`;
  const root = path.dirname(output);
  await mkdir(root, { recursive: true });
  const unsigned = path.join(root, `${variant}-unsigned.apk`);
  const aligned = path.join(root, `${variant}-aligned.apk`);
  const transformed = await readFile(transformedPath);
  const rebuilt = rebuildApk(await readFile(input), transformed);
  await writeFile(unsigned, rebuilt);
  const { executable } = androidTools();
  run(executable("zipalign"), ["-f", "-P", "16", "4", unsigned, aligned]);
  run(executable("apksigner"), [
    "sign", "--v4-signing-enabled", "false",
    "--ks", process.env.M005_TEST_KEYSTORE,
    "--ks-key-alias", process.env.M005_TEST_KEY_ALIAS,
    "--ks-pass", "env:M005_TEST_STORE_PASSWORD",
    "--key-pass", "env:M005_TEST_KEY_PASSWORD",
    "--out", output,
    aligned,
  ]);
  run(executable("apksigner"), ["verify", "--min-sdk-version", "29", output]);
  const dump = run(executable("aapt2"), ["dump", "xmltree", output, "--file", MANIFEST_ENTRY]);
  for (const expected of [
    "ah.runtime.bootstrap.ShellAppComponentFactory",
    "ah.fixtures.android.payload.PayloadApplication",
    "ah.m103.fixture",
    "preserved",
  ]) {
    if (!dump.includes(expected)) fail(`transformed Manifest is missing ${expected}`);
  }
  const inputBytes = await readFile(input);
  const outputBytes = await readFile(output);
  process.stdout.write(`${JSON.stringify({
    task_id: "M1-03",
    variant,
    package_name: packageName,
    input_sha256: sha256(inputBytes),
    transformed_manifest_sha256: sha256(transformed),
    output_sha256: sha256(outputBytes),
    semantic_gate: "single appComponentFactory change; application and metadata preserved",
    result: "PASS",
  }, null, 2)}\n`);
}

async function main() {
  const [mode, ...args] = process.argv.slice(2);
  if (mode === "prepare") {
    const [variant, output] = args;
    if (!["extracted", "direct"].includes(variant) || !output) {
      fail("usage: create-m1-03-device-apk.mjs prepare <extracted|direct> <original-manifest.bin>");
    }
    await prepareOriginal(variant, output);
    return;
  }
  if (mode === "package") {
    const [input, variant, transformed, output] = args;
    if (!input || !["extracted", "direct"].includes(variant) || !transformed || !output) {
      fail("usage: create-m1-03-device-apk.mjs package <signed-input.apk> <variant> <transformed.bin> <signed-output.apk>");
    }
    await packageDevice(input, variant, transformed, output);
    return;
  }
  fail("first argument must be prepare or package");
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
