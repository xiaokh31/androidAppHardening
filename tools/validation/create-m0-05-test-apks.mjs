#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { deflateRawSync } from "node:zlib";
import {
  centralRecord,
  isSignatureEntry,
  localRecord,
  normalizedEntry,
  readEntries,
  sha256,
} from "./create-m0-04-tampered-apks.mjs";

const CONFIG_ENTRY = "assets/ah/runtime/config.bin";
const PAYLOAD_ENTRY = "assets/ah/runtime/payload.ahdc";
const EOCD_SIGNATURE = 0x06054b50;
const DATA_DESCRIPTOR_FLAG = 1 << 3;
const CONFIG_BYTE_MUTATIONS = new Set([
  "no-factory",
  "config-major",
  "config-reserved",
  "config-signer-mismatch",
  "config-factory-flags",
  "config-invalid-utf8",
  "config-nul",
  "config-slot-tail",
]);
const PAYLOAD_V2_ONLY_MUTATIONS = new Set([
  "payload-nonce",
  "payload-tag-first",
  "payload-tag-middle",
  "payload-tag-last",
  "payload-ciphertext-first",
  "payload-ciphertext-middle",
  "payload-ciphertext-last",
]);

function fail(message) {
  throw new Error(`M0-05 test APK creation failed: ${message}`);
}

function mutateConfig(source, mutation) {
  const bytes = Buffer.from(source);
  if (bytes.length !== 768 || bytes.subarray(0, 4).toString("ascii") !== "AHKC") {
    fail("input ConfigV2 is not the canonical 768-byte fixture");
  }
  switch (mutation) {
    case "no-factory":
      bytes.writeUInt16LE(bytes.readUInt16LE(8) & ~1, 8);
      bytes.writeUInt16LE(0, 22);
      bytes.fill(0, 180, 692);
      break;
    case "config-major":
      bytes.writeUInt16LE(3, 4);
      break;
    case "config-reserved":
      bytes[692] = 1;
      break;
    case "config-signer-mismatch":
      bytes[56] ^= 0xff;
      break;
    case "config-factory-flags":
      bytes.writeUInt16LE(bytes.readUInt16LE(8) & ~1, 8);
      break;
    case "config-invalid-utf8":
      bytes[180] = 0xc3;
      bytes[181] = 0x28;
      break;
    case "config-nul":
      bytes[181] = 0;
      break;
    case "config-slot-tail": {
      const length = bytes.readUInt16LE(22);
      bytes[180 + length] = 1;
      break;
    }
    default:
      fail(`unknown config mutation: ${mutation}`);
  }
  return bytes;
}

function mutatePayload(source, mutation) {
  const bytes = Buffer.from(source);
  if (bytes.length < 160 || bytes.subarray(0, 4).toString("ascii") !== "AHDC") {
    fail("input payload is not an AHDC container");
  }
  const signerSize = bytes.readUInt32LE(16);
  const recordTableSize = bytes.readUInt32LE(20);
  const chunkCount = bytes.readUInt32LE(24);
  const chunkTableSize = bytes.readUInt32LE(28);
  const recordOffset = 160 + signerSize;
  const chunkOffset = recordOffset + recordTableSize;
  const payloadOffset = chunkOffset + chunkTableSize;
  if (chunkCount === 0 || chunkTableSize !== chunkCount * 32 || payloadOffset >= bytes.length) {
    fail("input payload topology is invalid");
  }
  if (mutation === "payload-nonce") {
    bytes[recordOffset + 40] ^= 1;
    return bytes;
  }
  const position = mutation.includes("-first")
    ? 0
    : mutation.includes("-middle")
      ? Math.floor(chunkCount / 2)
      : chunkCount - 1;
  const entry = chunkOffset + position * 32;
  const encryptedOffset = Number(bytes.readBigUInt64LE(entry + 16));
  const plaintextLength = bytes.readUInt32LE(entry + 24);
  const target = payloadOffset + encryptedOffset + (mutation.startsWith("payload-tag-") ? plaintextLength : 0);
  if (target < payloadOffset || target >= bytes.length) fail(`payload mutation offset is invalid: ${mutation}`);
  bytes[target] ^= 1;
  return bytes;
}

function isCanonicalPayloadV2(source) {
  return source.length >= 160 && source.subarray(0, 4).toString("ascii") === "AHDC" &&
    source.readUInt16LE(4) === 2 && source.readUInt16LE(8) === 160;
}

function buildApk(sourceEntries, mutation) {
  const localRecords = [];
  const centralRecords = [];
  let localOffset = 0;
  let configCount = 0;
  let payloadCount = 0;
  let duplicatedNativeEntries = 0;

  for (const sourceEntry of sourceEntries) {
    if (isSignatureEntry(sourceEntry.name)) {
      continue;
    }
    let replacement;
    if (sourceEntry.name === CONFIG_ENTRY) {
      configCount += 1;
      replacement = CONFIG_BYTE_MUTATIONS.has(mutation)
        ? mutateConfig(sourceEntry.compressedData, mutation)
        : undefined;
    }
    if (sourceEntry.name === PAYLOAD_ENTRY) {
      payloadCount += 1;
      if (mutation === "payload-corrupt") {
        replacement = Buffer.from(sourceEntry.compressedData);
        replacement[Math.floor(replacement.length / 2)] ^= 0xff;
      } else if (mutation.startsWith("payload-nonce") ||
          mutation.startsWith("payload-tag-") || mutation.startsWith("payload-ciphertext-")) {
        replacement = mutatePayload(sourceEntry.compressedData, mutation);
      }
    }
    const entry = structuralMutation(normalizedEntry(sourceEntry, replacement), mutation);
    if ((entry.flags & DATA_DESCRIPTOR_FLAG) !== 0 && mutation !== "config-descriptor") {
      fail(`data descriptor flag survived normalization for ${entry.name}`);
    }
    const duplicateConfig = sourceEntry.name === CONFIG_ENTRY && mutation === "config-duplicate";
    const duplicateNative = mutation === "native-duplicate"
      && duplicatedNativeEntries === 0
      && /^lib\/[^/]+\/lib[^/]+\.so$/u.test(sourceEntry.name);
    if (duplicateNative) {
      duplicatedNativeEntries += 1;
    }
    const copies = duplicateConfig ? 2 : 1;
    for (let copy = 0; copy < copies; copy += 1) {
      const local = localRecord(entry);
      localRecords.push(local);
      centralRecords.push(centralRecord(entry, localOffset));
      localOffset += local.length;
    }
    if (duplicateNative) {
      const segments = entry.name.split("/");
      segments[1] = segments[1].toUpperCase();
      const alternateName = segments.join("/");
      const alternate = {
        ...entry,
        name: alternateName,
        nameBytes: Buffer.from(alternateName, "utf8"),
      };
      const local = localRecord(alternate);
      localRecords.push(local);
      centralRecords.push(centralRecord(alternate, localOffset));
      localOffset += local.length;
    }
  }
  if (configCount !== 1 || payloadCount !== 1) {
    fail(`expected one config and payload entry, found ${configCount}/${payloadCount}`);
  }
  if (mutation === "native-duplicate" && duplicatedNativeEntries !== 1) {
    fail(`expected one forged duplicate ABI alias, found ${duplicatedNativeEntries}`);
  }

  const centralDirectory = Buffer.concat(centralRecords);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(EOCD_SIGNATURE, 0);
  eocd.writeUInt16LE(localRecords.length, 8);
  eocd.writeUInt16LE(localRecords.length, 10);
  eocd.writeUInt32LE(centralDirectory.length, 12);
  eocd.writeUInt32LE(localOffset, 16);
  const result = Buffer.concat([...localRecords, centralDirectory, eocd]);
  return mutation === "truncated-zip" ? result.subarray(0, result.length - 11) : result;
}

function structuralMutation(entry, mutation) {
  if (entry.name !== CONFIG_ENTRY) {
    return entry;
  }
  if (mutation === "config-deflate") {
    const compressedData = deflateRawSync(entry.compressedData);
    return {
      ...entry,
      method: 8,
      compressedData,
      compressedSize: compressedData.length,
    };
  }
  if (mutation === "config-descriptor") {
    return { ...entry, flags: entry.flags | DATA_DESCRIPTOR_FLAG };
  }
  if (mutation === "config-crc") {
    return { ...entry, crc32: (entry.crc32 ^ 1) >>> 0 };
  }
  if (mutation === "config-length") {
    return { ...entry, uncompressedSize: entry.uncompressedSize + 1 };
  }
  return entry;
}

async function main() {
  const [inputArgument, outputArgument] = process.argv.slice(2);
  if (!inputArgument || !outputArgument) {
    fail("usage: create-m0-05-test-apks.mjs <signed-input.apk> <output-dir>");
  }
  const repositoryRoot = process.cwd();
  const outputDirectory = path.resolve(outputArgument);
  const allowedRoots = [
    path.join(repositoryRoot, "build"),
    path.join(repositoryRoot, "artifacts"),
  ];
  if (!allowedRoots.some((root) => outputDirectory === root || outputDirectory.startsWith(`${root}${path.sep}`))) {
    fail("output directory must be under the ignored build/ or artifacts/ tree");
  }

  const apk = await readFile(inputArgument);
  const entries = readEntries(apk);
  const payloadEntries = entries.filter((entry) => entry.name === PAYLOAD_ENTRY);
  if (payloadEntries.length !== 1) {
    fail(`expected one payload entry, found ${payloadEntries.length}`);
  }
  const supportsPayloadV2Mutations = isCanonicalPayloadV2(payloadEntries[0].compressedData);
  await mkdir(outputDirectory, { recursive: true });
  const outputs = [];
  const mutations = [
    "no-factory",
    "config-major",
    "config-reserved",
    "config-signer-mismatch",
    "config-factory-flags",
    "config-invalid-utf8",
    "config-nul",
    "config-slot-tail",
    "config-duplicate",
    "config-deflate",
    "config-descriptor",
    "config-crc",
    "config-length",
    "payload-corrupt",
    "payload-nonce",
    "payload-tag-first",
    "payload-tag-middle",
    "payload-tag-last",
    "payload-ciphertext-first",
    "payload-ciphertext-middle",
    "payload-ciphertext-last",
    "native-duplicate",
    "truncated-zip",
  ];
  for (const mutation of mutations.filter((name) =>
    supportsPayloadV2Mutations || !PAYLOAD_V2_ONLY_MUTATIONS.has(name))) {
    const bytes = buildApk(entries, mutation);
    const output = path.join(outputDirectory, `m0-05-${mutation}-unsigned.apk`);
    await writeFile(output, bytes);
    outputs.push({
      mutation,
      path: path.relative(repositoryRoot, output).replaceAll("\\", "/"),
      bytes: bytes.length,
      sha256: sha256(bytes),
    });
  }
  process.stdout.write(`${JSON.stringify({
    task_id: "M0-05",
    input_sha256: sha256(apk),
    outputs,
    result: "PASS",
  }, null, 2)}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
