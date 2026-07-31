#!/usr/bin/env node

import { createHash } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const ENTRY_NAME = "assets/ah/poc/classes.dex";
const EOCD = 0x06054b50;
const CENTRAL = 0x02014b50;
const LOCAL = 0x04034b50;
const DATA_DESCRIPTOR = 1 << 3;
const ENCRYPTED = 1;

function fail(message) {
  throw new Error(`M0-04 APK verification failed: ${message}`);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function findEocd(apk) {
  const lowerBound = Math.max(0, apk.length - 22 - 65_535);
  for (let offset = apk.length - 22; offset >= lowerBound; offset -= 1) {
    if (apk.readUInt32LE(offset) !== EOCD) {
      continue;
    }
    const commentLength = apk.readUInt16LE(offset + 20);
    if (offset + 22 + commentLength === apk.length) {
      return {
        entryCount: apk.readUInt16LE(offset + 10),
        centralSize: apk.readUInt32LE(offset + 12),
        centralOffset: apk.readUInt32LE(offset + 16),
      };
    }
  }
  fail("EOCD is missing");
}

function readEntries(apk) {
  const eocd = findEocd(apk);
  const centralEnd = eocd.centralOffset + eocd.centralSize;
  if (centralEnd > apk.length) {
    fail("central directory exceeds APK bounds");
  }

  const entries = [];
  let cursor = eocd.centralOffset;
  for (let index = 0; index < eocd.entryCount; index += 1) {
    if (cursor + 46 > centralEnd || apk.readUInt32LE(cursor) !== CENTRAL) {
      fail("central directory is malformed");
    }
    const flags = apk.readUInt16LE(cursor + 8);
    const method = apk.readUInt16LE(cursor + 10);
    const compressedSize = apk.readUInt32LE(cursor + 20);
    const uncompressedSize = apk.readUInt32LE(cursor + 24);
    const nameLength = apk.readUInt16LE(cursor + 28);
    const extraLength = apk.readUInt16LE(cursor + 30);
    const commentLength = apk.readUInt16LE(cursor + 32);
    const localOffset = apk.readUInt32LE(cursor + 42);
    const name = apk
      .subarray(cursor + 46, cursor + 46 + nameLength)
      .toString("utf8");
    entries.push({
      name,
      flags,
      method,
      compressedSize,
      uncompressedSize,
      localOffset,
    });
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  if (cursor !== centralEnd) {
    fail("central directory size does not match parsed entries");
  }
  return entries;
}

function entryBytes(apk, entry) {
  const offset = entry.localOffset;
  if (offset + 30 > apk.length || apk.readUInt32LE(offset) !== LOCAL) {
    fail(`local header is invalid for ${entry.name}`);
  }
  const localFlags = apk.readUInt16LE(offset + 6);
  const localMethod = apk.readUInt16LE(offset + 8);
  const nameLength = apk.readUInt16LE(offset + 26);
  const extraLength = apk.readUInt16LE(offset + 28);
  const localName = apk
    .subarray(offset + 30, offset + 30 + nameLength)
    .toString("utf8");
  if (
    localFlags !== entry.flags ||
    localMethod !== entry.method ||
    localName !== entry.name
  ) {
    fail(`local and central headers differ for ${entry.name}`);
  }
  const dataOffset = offset + 30 + nameLength + extraLength;
  const dataEnd = dataOffset + entry.compressedSize;
  if (dataEnd > apk.length) {
    fail(`entry data exceeds APK bounds for ${entry.name}`);
  }
  return apk.subarray(dataOffset, dataEnd);
}

async function sourceFiles(root) {
  const result = [];
  for (const item of await readdir(root, { withFileTypes: true })) {
    const fullPath = path.join(root, item.name);
    if (item.isDirectory()) {
      result.push(...(await sourceFiles(fullPath)));
    } else if (item.isFile() && item.name.endsWith(".java")) {
      result.push(fullPath);
    }
  }
  return result;
}

async function verifySourcePolicy(repositoryRoot) {
  const root = path.join(
    repositoryRoot,
    "runtime",
    "bootstrap",
    "src",
    "main",
    "java",
  );
  const forbidden = [
    /\bDexClassLoader\b/u,
    /\bpathList\b/u,
    /setAccessible\s*\(/u,
    /\bAssetManager\b/u,
    /\bandroid\.content\.Context\b/u,
    /\bVMRuntime\b/u,
  ];
  for (const file of await sourceFiles(root)) {
    const text = await readFile(file, "utf8");
    for (const expression of forbidden) {
      if (expression.test(text)) {
        fail(`${path.relative(repositoryRoot, file)} contains ${expression}`);
      }
    }
  }
}

async function main() {
  const [apkArgument, generatedDexArgument, testApkArgument] = process.argv.slice(2);
  if (!apkArgument || !generatedDexArgument || !testApkArgument) {
    fail(
      "usage: verify-m0-04-apk.mjs <fixture.apk> <generated.dex> <test.apk>",
    );
  }

  const repositoryRoot = process.cwd();
  const [apk, generatedDex, testApk] = await Promise.all([
    readFile(apkArgument),
    readFile(generatedDexArgument),
    readFile(testApkArgument),
  ]);
  const entries = readEntries(apk);
  const payloadEntries = entries.filter((entry) => entry.name === ENTRY_NAME);
  if (payloadEntries.length !== 1) {
    fail(`expected one ${ENTRY_NAME}, found ${payloadEntries.length}`);
  }

  const payloadEntry = payloadEntries[0];
  if (
    payloadEntry.method !== 0 ||
    (payloadEntry.flags & (ENCRYPTED | DATA_DESCRIPTOR)) !== 0 ||
    payloadEntry.compressedSize !== payloadEntry.uncompressedSize ||
    payloadEntry.uncompressedSize < 112
  ) {
    fail("payload must be non-empty STORED data without encryption or descriptor");
  }
  const packagedDex = entryBytes(apk, payloadEntry);
  if (!packagedDex.equals(generatedDex)) {
    fail("packaged payload differs from generated payload");
  }

  const rootDexEntries = entries.filter((entry) => /^classes\d*\.dex$/u.test(entry.name));
  if (rootDexEntries.length === 0) {
    fail("APK root DEX is missing");
  }
  const payloadDescriptor = Buffer.from("Lah/fixtures/android/payload/", "utf8");
  for (const entry of rootDexEntries) {
    if (entry.method !== 0) {
      fail(`root DEX ${entry.name} is unexpectedly compressed`);
    }
    if (entryBytes(apk, entry).includes(payloadDescriptor)) {
      fail(`payload class leaked into root ${entry.name}`);
    }
  }

  for (const className of [
    "PayloadApplication",
    "PayloadActivity",
    "PayloadOnlyApi",
  ]) {
    const descriptor = Buffer.from(
      `Lah/fixtures/android/payload/${className};`,
      "utf8",
    );
    if (!packagedDex.includes(descriptor)) {
      fail(`payload DEX is missing ${className}`);
    }
  }

  await verifySourcePolicy(repositoryRoot);

  const result = {
    task_id: "M0-04",
    validation_mode: "pre-cli",
    apk: {
      bytes: apk.length,
      sha256: sha256(apk),
      root_dex_entries: rootDexEntries.map((entry) => entry.name),
    },
    payload: {
      entry: ENTRY_NAME,
      bytes: packagedDex.length,
      sha256: sha256(packagedDex),
      zip_method: "STORED",
      encrypted: false,
      data_descriptor: false,
    },
    test_apk: {
      bytes: testApk.length,
      sha256: sha256(testApk),
    },
    source_policy: "PASS",
    result: "PASS",
  };
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
