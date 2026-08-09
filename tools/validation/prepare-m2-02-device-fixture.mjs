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
const CONFIG_ENTRY = "assets/ah/runtime/config.bin";
const PAYLOAD_ENTRY = "assets/ah/runtime/payload.ahdc";
const RUNTIME = /^lib\/(arm64-v8a|x86_64)\/libah_runtime\.so$/u;

function fail(message) {
  throw new Error(`M2-02 device fixture failed: ${message}`);
}

function assertIgnored(output) {
  const repository = process.cwd();
  const roots = [path.join(repository, "build"), path.join(repository, "artifacts")];
  if (!roots.some((root) => output === root || output.startsWith(`${root}${path.sep}`))) {
    fail("output must be under ignored build/ or artifacts/");
  }
}

function materialize(entry) {
  if (entry.method === 0) return Buffer.from(entry.compressedData);
  if (entry.method === 8) {
    const bytes = inflateRawSync(entry.compressedData);
    if (bytes.length !== entry.uncompressedSize) fail(`length mismatch for ${entry.name}`);
    return bytes;
  }
  fail(`unsupported compression method for ${entry.name}`);
}

function patchRuntime(source, slot, abi) {
  if (slot.length !== 104 || slot.subarray(0, 4).toString("ascii") !== "AHS1") {
    fail(`invalid ${abi} share slot`);
  }
  const expectedAbi = abi === "arm64-v8a" ? 2 : 4;
  if (slot.readUInt16LE(6) !== expectedAbi) fail(`share slot ABI mismatch for ${abi}`);
  const marker = Buffer.from("AHP0", "ascii");
  const offset = source.indexOf(marker);
  if (offset < 0 || source.indexOf(marker, offset + 1) >= 0 || offset > source.length - slot.length) {
    fail(`expected exactly one bounded placeholder in ${abi} runtime`);
  }
  if (source.readUInt16LE(offset + 4) !== 1 ||
      source.readUInt16LE(offset + 6) !== expectedAbi ||
      source.subarray(offset + 8, offset + 104).some((value) => value !== 0)) {
    fail(`runtime placeholder contract mismatch for ${abi}`);
  }
  const result = Buffer.from(source);
  slot.copy(result, offset);
  return result;
}

function rebuild(sourceEntries, replacements) {
  const localRecords = [];
  const centralRecords = [];
  let localOffset = 0;
  const counts = new Map([...replacements.keys()].map((name) => [name, 0]));
  for (const source of sourceEntries) {
    if (isSignatureEntry(source.name)) continue;
    const replacement = replacements.get(source.name);
    if (replacement !== undefined) counts.set(source.name, counts.get(source.name) + 1);
    const entry = normalizedEntry(source, replacement);
    const local = localRecord(entry);
    localRecords.push(local);
    centralRecords.push(centralRecord(entry, localOffset));
    localOffset += local.length;
  }
  for (const [name, count] of counts) {
    if (count !== 1) fail(`expected one replacement target ${name}, found ${count}`);
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

function run(command, args, timeout = 180_000) {
  let executable = command;
  let actualArgs = args;
  if (command.toLowerCase().endsWith(".jar")) {
    if (!process.env.JAVA_HOME) fail("JAVA_HOME is required for apksigner");
    executable = path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java");
    actualArgs = ["-jar", command, ...args];
  }
  const result = spawnSync(executable, actualArgs, {
    encoding: "utf8",
    timeout,
    maxBuffer: 8 * 1024 * 1024,
    windowsHide: true,
    env: process.env,
  });
  if (result.error || result.status !== 0) {
    fail(`${path.basename(command)} failed (${result.status}):\n${result.stdout ?? ""}\n${result.stderr ?? ""}`);
  }
  return `${result.stdout ?? ""}${result.stderr ?? ""}`;
}

function tools() {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  if (!sdk) fail("ANDROID_HOME is required");
  const root = path.join(sdk, "build-tools", "36.1.0");
  return {
    zipalign: path.join(root, process.platform === "win32" ? "zipalign.exe" : "zipalign"),
    apksigner: path.join(root, process.platform === "win32" ? "lib/apksigner.jar" : "apksigner"),
  };
}

async function extract(inputArgument, outputArgument) {
  const input = await readFile(inputArgument);
  const output = path.resolve(outputArgument);
  assertIgnored(output);
  if (input.length < 16 || input.subarray(0, 4).toString("ascii") !== "AHDC" ||
      input[4] !== 1 || input.readUInt16LE(6) !== 2) {
    fail("source must be the synthetic two-DEX M0-05 payload asset");
  }
  const firstLength = input.readUInt32LE(8);
  const secondLength = input.readUInt32LE(12);
  if (16 + firstLength + secondLength !== input.length || firstLength < 112 || secondLength < 112) {
    fail("source payload length table is invalid");
  }
  await mkdir(output, { recursive: true });
  const first = input.subarray(16, 16 + firstLength);
  const second = input.subarray(16 + firstLength);
  await writeFile(path.join(output, "classes.dex"), first);
  await writeFile(path.join(output, "classes2.dex"), second);
  process.stdout.write(`${JSON.stringify({
    task_id: "M2-02",
    mode: "extract-synthetic-dex",
    source_dex_sha256: [sha256(first), sha256(second)],
    result: "PASS",
  }, null, 2)}\n`);
}

async function packageFixture(baselineArgument, vectorArgument, outputArgument) {
  const baselinePath = path.resolve(baselineArgument);
  const vectorRoot = path.resolve(vectorArgument);
  const output = path.resolve(outputArgument);
  assertIgnored(output);
  for (const name of ["M005_TEST_KEYSTORE", "M005_TEST_STORE_PASSWORD", "M005_TEST_KEY_ALIAS", "M005_TEST_KEY_PASSWORD"]) {
    if (!process.env[name]) fail(`${name} is required for test-only signing`);
  }
  await mkdir(path.dirname(output), { recursive: true });
  const baseline = await readFile(baselinePath);
  const entries = readEntries(baseline);
  const replacements = new Map([
    [CONFIG_ENTRY, await readFile(path.join(vectorRoot, "config.bin"))],
    [PAYLOAD_ENTRY, await readFile(path.join(vectorRoot, "payload.ahdc"))],
  ]);
  for (const entry of entries) {
    const match = entry.name.match(RUNTIME);
    if (match) {
      const abi = match[1];
      const slot = await readFile(path.join(vectorRoot, `slot-${abi}.bin`));
      replacements.set(entry.name, patchRuntime(materialize(entry), slot, abi));
    }
  }
  if (![...replacements.keys()].some((name) => name.includes("arm64-v8a")) ||
      ![...replacements.keys()].some((name) => name.includes("x86_64"))) {
    fail("baseline APK is missing one of the two device Runtime ABIs");
  }
  const unsigned = output.replace(/\.apk$/u, "-unsigned.apk");
  const aligned = output.replace(/\.apk$/u, "-aligned.apk");
  await writeFile(unsigned, rebuild(entries, replacements));
  const android = tools();
  // Native opens both fixed STORED assets directly from sourceDir and requires
  // their data offsets to retain the production 4 KiB alignment contract.
  run(android.zipalign, ["-f", "-P", "16", "4096", unsigned, aligned]);
  run(android.apksigner, [
    "sign", "--v4-signing-enabled", "false",
    "--alignment-preserved", "true",
    "--ks", process.env.M005_TEST_KEYSTORE,
    "--ks-key-alias", process.env.M005_TEST_KEY_ALIAS,
    "--ks-pass", "env:M005_TEST_STORE_PASSWORD",
    "--key-pass", "env:M005_TEST_KEY_PASSWORD",
    "--out", output,
    aligned,
  ]);
  run(android.zipalign, ["-c", "-P", "16", "4096", output]);
  run(android.apksigner, ["verify", "--min-sdk-version", "29", output]);
  const outputBytes = await readFile(output);
  process.stdout.write(`${JSON.stringify({
    task_id: "M2-02",
    mode: "package-device-fixture",
    baseline_sha256: sha256(baseline),
    output_sha256: sha256(outputBytes),
    output_bytes: outputBytes.length,
    result: "PASS",
  }, null, 2)}\n`);
}

async function main() {
  const [mode, ...args] = process.argv.slice(2);
  if (mode === "extract" && args.length === 2) return extract(args[0], args[1]);
  if (mode === "package" && args.length === 3) return packageFixture(args[0], args[1], args[2]);
  fail("usage: extract <m0-05-payload.ahdc> <output-root> | package <baseline.apk> <vector-root> <output.apk>");
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
