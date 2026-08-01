#!/usr/bin/env node

import { createHash } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { inflateRawSync } from "node:zlib";
import { crc32 } from "./create-m0-04-tampered-apks.mjs";

const PAYLOAD_ENTRY = "assets/ah/runtime/payload.ahdc";
const CONFIG_ENTRY = "assets/ah/runtime/config.bin";
const EOCD = 0x06054b50;
const CENTRAL = 0x02014b50;
const LOCAL = 0x04034b50;
const DATA_DESCRIPTOR = 1 << 3;
const ENCRYPTED = 1;
const EXPECTED_ABIS = ["arm64-v8a", "x86_64"];

function fail(message) {
  throw new Error(`M0-05 APK verification failed: ${message}`);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function readEntries(apk) {
  const lowerBound = Math.max(0, apk.length - 22 - 65_535);
  let eocdOffset = -1;
  for (let offset = apk.length - 22; offset >= lowerBound; offset -= 1) {
    if (
      apk.readUInt32LE(offset) === EOCD &&
      offset + 22 + apk.readUInt16LE(offset + 20) === apk.length
    ) {
      eocdOffset = offset;
      break;
    }
  }
  if (eocdOffset < 0) {
    fail("EOCD is missing");
  }
  const entryCount = apk.readUInt16LE(eocdOffset + 10);
  const centralSize = apk.readUInt32LE(eocdOffset + 12);
  const centralOffset = apk.readUInt32LE(eocdOffset + 16);
  const centralEnd = centralOffset + centralSize;
  if (entryCount === 0 || entryCount > 4096 || centralEnd > apk.length) {
    fail("central directory bounds are invalid");
  }

  const entries = [];
  const names = new Set();
  let cursor = centralOffset;
  for (let index = 0; index < entryCount; index += 1) {
    if (cursor + 46 > centralEnd || apk.readUInt32LE(cursor) !== CENTRAL) {
      fail("central directory is malformed");
    }
    const flags = apk.readUInt16LE(cursor + 8);
    const method = apk.readUInt16LE(cursor + 10);
    const crc32 = apk.readUInt32LE(cursor + 16);
    const compressedSize = apk.readUInt32LE(cursor + 20);
    const uncompressedSize = apk.readUInt32LE(cursor + 24);
    const nameLength = apk.readUInt16LE(cursor + 28);
    const extraLength = apk.readUInt16LE(cursor + 30);
    const commentLength = apk.readUInt16LE(cursor + 32);
    const localOffset = apk.readUInt32LE(cursor + 42);
    const name = apk.subarray(cursor + 46, cursor + 46 + nameLength).toString("utf8");
    if (!names.add(name)) {
      fail(`duplicate ZIP entry ${name}`);
    }
    entries.push({
      name,
      flags,
      method,
      crc32,
      compressedSize,
      uncompressedSize,
      localOffset,
    });
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  if (cursor !== centralEnd) {
    fail("central directory size does not match entries");
  }
  return entries;
}

function storedEntryBytes(apk, entry) {
  const offset = entry.localOffset;
  if (offset + 30 > apk.length || apk.readUInt32LE(offset) !== LOCAL) {
    fail(`local header is invalid for ${entry.name}`);
  }
  const flags = apk.readUInt16LE(offset + 6);
  const method = apk.readUInt16LE(offset + 8);
  const localCrc32 = apk.readUInt32LE(offset + 14);
  const compressedSize = apk.readUInt32LE(offset + 18);
  const uncompressedSize = apk.readUInt32LE(offset + 22);
  const nameLength = apk.readUInt16LE(offset + 26);
  const extraLength = apk.readUInt16LE(offset + 28);
  const name = apk.subarray(offset + 30, offset + 30 + nameLength).toString("utf8");
  if (
    flags !== entry.flags ||
    method !== entry.method ||
    localCrc32 !== entry.crc32 ||
    compressedSize !== entry.compressedSize ||
    uncompressedSize !== entry.uncompressedSize ||
    name !== entry.name
  ) {
    fail(`local and central headers differ for ${entry.name}`);
  }
  if (method !== 0) {
    fail(`${entry.name} is not STORED`);
  }
  const dataOffset = offset + 30 + nameLength + extraLength;
  const dataEnd = dataOffset + compressedSize;
  if (dataEnd > apk.length) {
    fail(`entry data exceeds APK bounds for ${entry.name}`);
  }
  const bytes = apk.subarray(dataOffset, dataEnd);
  if (crc32(bytes) !== entry.crc32) {
    fail(`CRC-32 differs for ${entry.name}`);
  }
  return bytes;
}

function zipEntryBytes(apk, entry) {
  const offset = entry.localOffset;
  if (offset + 30 > apk.length || apk.readUInt32LE(offset) !== LOCAL) {
    fail(`local header is invalid for ${entry.name}`);
  }
  const flags = apk.readUInt16LE(offset + 6);
  const method = apk.readUInt16LE(offset + 8);
  const localCrc32 = apk.readUInt32LE(offset + 14);
  const compressedSize = apk.readUInt32LE(offset + 18);
  const uncompressedSize = apk.readUInt32LE(offset + 22);
  const nameLength = apk.readUInt16LE(offset + 26);
  const extraLength = apk.readUInt16LE(offset + 28);
  const name = apk.subarray(offset + 30, offset + 30 + nameLength).toString("utf8");
  if (
    flags !== entry.flags ||
    method !== entry.method ||
    localCrc32 !== entry.crc32 ||
    compressedSize !== entry.compressedSize ||
    uncompressedSize !== entry.uncompressedSize ||
    name !== entry.name
  ) {
    fail(`local and central headers differ for ${entry.name}`);
  }
  if (method !== 0 && method !== 8) {
    fail(`${entry.name} uses unsupported ZIP method ${method}`);
  }
  const dataOffset = offset + 30 + nameLength + extraLength;
  const dataEnd = dataOffset + compressedSize;
  if (dataEnd > apk.length) {
    fail(`entry data exceeds APK bounds for ${entry.name}`);
  }
  const compressed = apk.subarray(dataOffset, dataEnd);
  const bytes = method === 0 ? compressed : inflateRawSync(compressed);
  if (bytes.length !== uncompressedSize || crc32(bytes) !== entry.crc32) {
    fail(`uncompressed bytes differ for ${entry.name}`);
  }
  return bytes;
}

function parseContainer(bytes) {
  if (
    bytes.length < 16 ||
    bytes.subarray(0, 4).toString("ascii") !== "AHDC" ||
    bytes[4] !== 1 ||
    bytes[5] !== 0 ||
    bytes.readUInt16LE(6) !== 2
  ) {
    fail("payload container header is invalid");
  }
  const lengths = [bytes.readUInt32LE(8), bytes.readUInt32LE(12)];
  const dexFiles = [];
  let cursor = 16;
  for (const length of lengths) {
    if (length < 112 || length > 16 * 1024 * 1024 || cursor + length > bytes.length) {
      fail("payload DEX bounds are invalid");
    }
    const dex = bytes.subarray(cursor, cursor + length);
    if (dex.subarray(0, 4).toString("binary") !== "dex\n") {
      fail("payload DEX magic is invalid");
    }
    dexFiles.push(dex);
    cursor += length;
  }
  if (cursor !== bytes.length) {
    fail("payload container has trailing bytes");
  }
  return dexFiles;
}

function dexDefinedClasses(dex) {
  const stringCount = dex.readUInt32LE(56);
  const stringOffset = dex.readUInt32LE(60);
  const typeCount = dex.readUInt32LE(64);
  const typeOffset = dex.readUInt32LE(68);
  const classCount = dex.readUInt32LE(96);
  const classOffset = dex.readUInt32LE(100);
  if (
    stringOffset + stringCount * 4 > dex.length ||
    typeOffset + typeCount * 4 > dex.length ||
    classOffset + classCount * 32 > dex.length
  ) {
    fail("DEX identifier tables exceed bounds");
  }
  const readString = (index) => {
    if (index >= stringCount) {
      fail("DEX string index exceeds bounds");
    }
    let cursor = dex.readUInt32LE(stringOffset + index * 4);
    for (let shift = 0; shift < 35; shift += 7) {
      if (cursor >= dex.length) {
        fail("DEX string length exceeds bounds");
      }
      const value = dex[cursor++];
      if ((value & 0x80) === 0) {
        const end = dex.indexOf(0, cursor);
        if (end < 0) {
          fail("DEX string is unterminated");
        }
        return dex.subarray(cursor, end).toString("utf8");
      }
    }
    fail("DEX string length is malformed");
  };
  const result = new Set();
  for (let index = 0; index < classCount; index += 1) {
    const typeIndex = dex.readUInt32LE(classOffset + index * 32);
    if (typeIndex >= typeCount) {
      fail("DEX class type index exceeds bounds");
    }
    const stringIndex = dex.readUInt32LE(typeOffset + typeIndex * 4);
    result.add(readString(stringIndex));
  }
  return result;
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
  const runtimeRoot = path.join(repositoryRoot, "runtime", "bootstrap", "src", "main", "java");
  const forbidden = [
    /com\.android\.apksig\.internal/u,
    /android\.content\.Context/u,
    /android\.content\.pm\.PackageManager/u,
    /\bActivityThread\b/u,
    /\bLoadedApk\b/u,
    /\bDexClassLoader\b/u,
    /\bpathList\b/u,
    /setAccessible\s*\(/u,
    /\.metaData\b/u,
  ];
  for (const file of await sourceFiles(runtimeRoot)) {
    const text = await readFile(file, "utf8");
    for (const expression of forbidden) {
      if (expression.test(text)) {
        fail(`${path.relative(repositoryRoot, file)} contains ${expression}`);
      }
    }
  }

  const shell = await readFile(
    path.join(runtimeRoot, "ah", "runtime", "bootstrap", "ShellAppComponentFactory.java"),
    "utf8",
  );
  const signer = shell.indexOf("EarlySignerProbe.verify(applicationInfo)");
  const config = shell.indexOf("EarlyConfigProbe.open(applicationInfo, signer)");
  const payload = shell.indexOf("StoredDexReader.readContainer(applicationInfo.sourceDir)");
  if (!(signer >= 0 && signer < config && config < payload)) {
    fail("source ordering does not prove signer then ConfigV2 then payload");
  }
}

function parseConfigV2(bytes, expectedSignerHex) {
  if (bytes.length !== 768 || bytes.subarray(0, 4).toString("ascii") !== "AHKC") {
    fail("ConfigV2 size or magic is invalid");
  }
  const fields = {
    major: bytes.readUInt16LE(4),
    minor: bytes.readUInt16LE(6),
    flags: bytes.readUInt16LE(8),
    reserved: bytes.readUInt16LE(10),
    totalSize: bytes.readUInt32LE(12),
    containerMajor: bytes.readUInt16LE(16),
    signerPolicy: bytes.readUInt16LE(18),
    riskPolicy: bytes.readUInt16LE(20),
    factoryLength: bytes.readUInt16LE(22),
  };
  if (
    fields.major !== 2 ||
    fields.minor !== 0 ||
    fields.flags !== 1 ||
    fields.reserved !== 0 ||
    fields.totalSize !== 768 ||
    fields.containerMajor !== 1 ||
    fields.signerPolicy !== 1 ||
    fields.riskPolicy !== 1 ||
    fields.factoryLength < 1 ||
    fields.factoryLength > 512
  ) {
    fail(`ConfigV2 fields are invalid: ${JSON.stringify(fields)}`);
  }
  const factorySlot = bytes.subarray(180, 692);
  const factory = factorySlot.subarray(0, fields.factoryLength).toString("utf8");
  if (
    factory !== "ah.fixtures.android.payload.OriginalAppComponentFactory" ||
    factorySlot.subarray(fields.factoryLength).some((value) => value !== 0) ||
    bytes.subarray(692).some((value) => value !== 0)
  ) {
    fail("ConfigV2 Factory or zero-fill contract is invalid");
  }
  const signerHex = bytes.subarray(56, 88).toString("hex");
  if (signerHex !== expectedSignerHex.toLowerCase()) {
    fail(`ConfigV2 signer binding differs from expected signer ${signerHex}`);
  }
  return { bytes: bytes.length, sha256: sha256(bytes), signer_sha256: signerHex, factory };
}

function verifyR8Mapping(mapping) {
  for (const className of ["ApkSigner", "ApkSignerEngine"]) {
    const kept = new RegExp(
      `^com\\.android\\.apksig\\.${className} -> (?!R8\\$\\$REMOVED\\$\\$CLASS)`,
      "mu",
    );
    if (kept.test(mapping)) {
      fail(`R8 retained signing execution class ${className}`);
    }
  }
  if (!/^com\.android\.apksig\.ApkVerifier -> /mu.test(mapping)) {
    fail("R8 mapping does not contain the verifier path");
  }
}

function verifyR8Usage(usage) {
  if (/^com\.android\.apksig\.ApkVerifier$/mu.test(usage)) {
    fail("R8 usage reports the verifier entry point as removed");
  }
  for (const className of ["ApkSigner", "ApkSignerEngine"]) {
    if (!new RegExp(`^com\\.android\\.apksig\\.${className}$`, "mu").test(usage)) {
      fail(`R8 usage does not prove removal of ${className}`);
    }
  }
}

async function inspectVariant(
  apkPath,
  testApkPath,
  mappingPath,
  usagePath,
  expectExtracted,
  expectedSignerHex,
) {
  const [apk, testApk, mappingBytes, usageBytes] = await Promise.all([
    readFile(apkPath),
    readFile(testApkPath),
    readFile(mappingPath),
    readFile(usagePath),
  ]);
  const mapping = mappingBytes.toString("utf8");
  const usage = usageBytes.toString("utf8");
  const entries = readEntries(apk);
  for (const key of [
    "ah.runtime.original_application",
    "ah.runtime.original_app_component_factory",
    "ah.runtime.has_original_app_component_factory",
    "ah.runtime.container_asset",
    "ah.runtime.container_major",
    "ah.runtime.signer_policy_version",
    "ah.runtime.risk_policy_version",
  ]) {
    if (apk.includes(Buffer.from(key, "utf8"))) {
      fail(`deprecated Manifest metadata key remains in ${apkPath}: ${key}`);
    }
  }
  const configEntries = entries.filter((entry) => entry.name === CONFIG_ENTRY);
  if (configEntries.length !== 1) {
    fail(`expected one ${CONFIG_ENTRY}, found ${configEntries.length}`);
  }
  const configEntry = configEntries[0];
  if (
    configEntry.method !== 0 ||
    (configEntry.flags & (ENCRYPTED | DATA_DESCRIPTOR)) !== 0 ||
    configEntry.compressedSize !== configEntry.uncompressedSize
  ) {
    fail("ConfigV2 entry violates the STORED/no-descriptor contract");
  }
  const config = parseConfigV2(storedEntryBytes(apk, configEntry), expectedSignerHex);
  const payloadEntries = entries.filter((entry) => entry.name === PAYLOAD_ENTRY);
  if (payloadEntries.length !== 1) {
    fail(`expected one ${PAYLOAD_ENTRY}, found ${payloadEntries.length}`);
  }
  const payloadEntry = payloadEntries[0];
  if (
    payloadEntry.method !== 0 ||
    (payloadEntry.flags & (ENCRYPTED | DATA_DESCRIPTOR)) !== 0 ||
    payloadEntry.compressedSize !== payloadEntry.uncompressedSize
  ) {
    fail("payload entry violates the STORED/no-descriptor contract");
  }
  const container = storedEntryBytes(apk, payloadEntry);
  const dexFiles = parseContainer(container);
  const primaryClasses = dexDefinedClasses(dexFiles[0]);
  const secondaryClasses = dexDefinedClasses(dexFiles[1]);
  for (const descriptor of [
    "Lah/fixtures/android/payload/PayloadApplication;",
    "Lah/fixtures/android/payload/OriginalAppComponentFactory;",
    "Lah/fixtures/android/payload/PayloadProvider;",
    "Lah/fixtures/android/payload/PayloadJni;",
  ]) {
    if (!primaryClasses.has(descriptor)) {
      fail(`primary payload DEX is missing ${descriptor}`);
    }
  }
  const secondaryDescriptor = Buffer.from("Lah/fixtures/android/payload/SecondaryApi;");
  if (
    primaryClasses.has(secondaryDescriptor.toString("utf8")) ||
    !secondaryClasses.has(secondaryDescriptor.toString("utf8"))
  ) {
    fail("SecondaryApi is not exclusive to classes2 payload DEX");
  }

  const rootDexEntries = entries.filter((entry) => /^classes\d*\.dex$/u.test(entry.name));
  if (rootDexEntries.length === 0) {
    fail("root bootstrap DEX is missing");
  }
  for (const entry of rootDexEntries) {
    const rootDex = storedEntryBytes(apk, entry);
    const payloadDefinitions = [...dexDefinedClasses(rootDex)].filter((name) =>
      name.startsWith("Lah/fixtures/android/payload/"),
    );
    if (payloadDefinitions.length > 0) {
      fail(`payload implementation leaked into ${entry.name}`);
    }
  }

  const nativeEntries = entries.filter((entry) => /^lib\/[^/]+\/libfixture_jni\.so$/u.test(entry.name));
  const nativeAbis = nativeEntries.map((entry) => entry.name.split("/")[1]).sort();
  if (JSON.stringify(nativeAbis) !== JSON.stringify(EXPECTED_ABIS)) {
    fail(`native ABI set is ${JSON.stringify(nativeAbis)}`);
  }
  for (const entry of nativeEntries) {
    if (expectExtracted ? entry.method === 0 : entry.method !== 0) {
      fail(`${entry.name} packaging does not match extractNativeLibs=${expectExtracted}`);
    }
  }
  verifyR8Mapping(mapping);
  verifyR8Usage(usage);
  return {
    apk: { path: apkPath, bytes: apk.length, sha256: sha256(apk) },
    test_apk: { path: testApkPath, bytes: testApk.length, sha256: sha256(testApk) },
    payload: {
      bytes: container.length,
      sha256: sha256(container),
      dex: dexFiles.map((dex) => ({ bytes: dex.length, sha256: sha256(dex) })),
    },
    config,
    native_abis: nativeAbis,
    native_libraries: nativeEntries
      .map((entry) => {
        const bytes = zipEntryBytes(apk, entry);
        return {
          abi: entry.name.split("/")[1],
          path: entry.name,
          bytes: bytes.length,
          sha256: sha256(bytes),
        };
      })
      .sort((left, right) => left.abi.localeCompare(right.abi)),
    r8: {
      mapping: { path: mappingPath, bytes: mappingBytes.length, sha256: sha256(mappingBytes) },
      usage: { path: usagePath, bytes: usageBytes.length, sha256: sha256(usageBytes) },
      signing_execution_classes: "REMOVED",
      verifier_entry_point: "RETAINED",
    },
    extract_native_libs: expectExtracted,
    root_dex_count: rootDexEntries.length,
    root_dex_bytes: rootDexEntries.reduce((total, entry) => total + entry.uncompressedSize, 0),
  };
}

async function baselineRootDexBytes(apkPath) {
  const apk = await readFile(apkPath);
  const entries = readEntries(apk).filter((entry) => /^classes\d*\.dex$/u.test(entry.name));
  if (entries.length === 0) {
    fail("baseline bootstrap APK has no root DEX");
  }
  return entries.reduce((total, entry) => total + entry.uncompressedSize, 0);
}

async function main() {
  const [
    extractedApk,
    directApk,
    extractedTestApk,
    directTestApk,
    extractedMapping,
    extractedUsage,
    directMapping,
    directUsage,
    baselineApk,
    expectedSignerHex,
  ] = process.argv.slice(2);
  if (!expectedSignerHex || !/^[0-9a-fA-F]{64}$/u.test(expectedSignerHex)) {
    fail(
      "usage: verify-m0-05-apks.mjs <extracted.apk> <direct.apk> <extracted-test.apk> <direct-test.apk> <extracted-mapping.txt> <extracted-usage.txt> <direct-mapping.txt> <direct-usage.txt> <baseline.apk> <signer-sha256>",
    );
  }
  await verifySourcePolicy(process.cwd());
  const [extracted, direct, baselineBytes] = await Promise.all([
    inspectVariant(
      extractedApk,
      extractedTestApk,
      extractedMapping,
      extractedUsage,
      true,
      expectedSignerHex,
    ),
    inspectVariant(
      directApk,
      directTestApk,
      directMapping,
      directUsage,
      false,
      expectedSignerHex,
    ),
    baselineRootDexBytes(baselineApk),
  ]);
  if (extracted.config.sha256 !== direct.config.sha256) {
    fail("extracted/direct ConfigV2 bytes differ");
  }
  process.stdout.write(
    `${JSON.stringify(
      {
        task_id: "M0-05",
        validation_mode: "pre-cli",
        variants: { extracted, direct },
        source_policy: "PASS",
        r8_signing_execution_classes: "REMOVED",
        verifier_peak_memory_kb: process.resourceUsage().maxRSS,
        verifier_root_dex_delta: {
          baseline_bytes: baselineBytes,
          extracted_bytes: extracted.root_dex_bytes,
          direct_bytes: direct.root_dex_bytes,
          extracted_delta: extracted.root_dex_bytes - baselineBytes,
          direct_delta: direct.root_dex_bytes - baselineBytes,
        },
        result: "PASS",
      },
      null,
      2,
    )}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
