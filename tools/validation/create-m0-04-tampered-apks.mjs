#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { pathToFileURL } from "node:url";

const PAYLOAD_ENTRY = "assets/ah/poc/classes.dex";
const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const LOCAL_SIGNATURE = 0x04034b50;
const DATA_DESCRIPTOR_FLAG = 1 << 3;
const ENCRYPTED_FLAG = 1;

function fail(message) {
  throw new Error(`M0-04 tampered APK creation failed: ${message}`);
}

export function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function makeCrc32Table() {
  const table = new Uint32Array(256);
  for (let index = 0; index < table.length; index += 1) {
    let value = index;
    for (let bit = 0; bit < 8; bit += 1) {
      value = (value & 1) === 0 ? value >>> 1 : 0xedb88320 ^ (value >>> 1);
    }
    table[index] = value >>> 0;
  }
  return table;
}

const crc32Table = makeCrc32Table();

export function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) {
    value = crc32Table[(value ^ byte) & 0xff] ^ (value >>> 8);
  }
  return (value ^ 0xffffffff) >>> 0;
}

function findEocd(apk) {
  const lowerBound = Math.max(0, apk.length - 22 - 65_535);
  for (let offset = apk.length - 22; offset >= lowerBound; offset -= 1) {
    if (apk.readUInt32LE(offset) !== EOCD_SIGNATURE) {
      continue;
    }
    const commentLength = apk.readUInt16LE(offset + 20);
    if (offset + 22 + commentLength !== apk.length) {
      continue;
    }
    const disk = apk.readUInt16LE(offset + 4);
    const centralDisk = apk.readUInt16LE(offset + 6);
    const entriesOnDisk = apk.readUInt16LE(offset + 8);
    const entryCount = apk.readUInt16LE(offset + 10);
    if (
      disk !== 0 ||
      centralDisk !== 0 ||
      entriesOnDisk !== entryCount ||
      entryCount === 0xffff
    ) {
      fail("multi-disk or ZIP64 APK is unsupported");
    }
    return {
      entryCount,
      centralSize: apk.readUInt32LE(offset + 12),
      centralOffset: apk.readUInt32LE(offset + 16),
    };
  }
  fail("EOCD is missing");
}

export function readEntries(apk) {
  const eocd = findEocd(apk);
  const centralEnd = eocd.centralOffset + eocd.centralSize;
  if (centralEnd > apk.length) {
    fail("central directory exceeds APK bounds");
  }

  const entries = [];
  let cursor = eocd.centralOffset;
  for (let index = 0; index < eocd.entryCount; index += 1) {
    if (
      cursor + 46 > centralEnd ||
      apk.readUInt32LE(cursor) !== CENTRAL_SIGNATURE
    ) {
      fail("central directory is malformed");
    }
    const centralHeader = Buffer.from(apk.subarray(cursor, cursor + 46));
    const flags = centralHeader.readUInt16LE(8);
    if ((flags & ENCRYPTED_FLAG) !== 0) {
      fail("encrypted APK entries are unsupported");
    }
    const compressedSize = centralHeader.readUInt32LE(20);
    const uncompressedSize = centralHeader.readUInt32LE(24);
    const nameLength = centralHeader.readUInt16LE(28);
    const centralExtraLength = centralHeader.readUInt16LE(30);
    const commentLength = centralHeader.readUInt16LE(32);
    const localOffset = centralHeader.readUInt32LE(42);
    const next = cursor + 46 + nameLength + centralExtraLength + commentLength;
    if (next > centralEnd || nameLength === 0) {
      fail("central entry exceeds directory bounds");
    }

    const nameBytes = Buffer.from(apk.subarray(cursor + 46, cursor + 46 + nameLength));
    const centralExtra = Buffer.from(
      apk.subarray(
        cursor + 46 + nameLength,
        cursor + 46 + nameLength + centralExtraLength,
      ),
    );
    const comment = Buffer.from(
      apk.subarray(cursor + 46 + nameLength + centralExtraLength, next),
    );
    if (
      localOffset + 30 > apk.length ||
      apk.readUInt32LE(localOffset) !== LOCAL_SIGNATURE
    ) {
      fail("local entry header is malformed");
    }
    const localNameLength = apk.readUInt16LE(localOffset + 26);
    const localExtraLength = apk.readUInt16LE(localOffset + 28);
    const localName = apk.subarray(
      localOffset + 30,
      localOffset + 30 + localNameLength,
    );
    if (!localName.equals(nameBytes)) {
      fail("local and central entry names differ");
    }
    const localExtra = Buffer.from(
      apk.subarray(
        localOffset + 30 + localNameLength,
        localOffset + 30 + localNameLength + localExtraLength,
      ),
    );
    const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
    const dataEnd = dataOffset + compressedSize;
    if (dataEnd > eocd.centralOffset) {
      fail("entry data exceeds APK data region");
    }
    entries.push({
      centralHeader,
      nameBytes,
      name: nameBytes.toString("utf8"),
      centralExtra,
      comment,
      localExtra,
      compressedData: Buffer.from(apk.subarray(dataOffset, dataEnd)),
      flags,
      method: centralHeader.readUInt16LE(10),
      crc32: centralHeader.readUInt32LE(16),
      compressedSize,
      uncompressedSize,
    });
    cursor = next;
  }
  if (cursor !== centralEnd) {
    fail("central directory size does not match entries");
  }
  return entries;
}

export function isSignatureEntry(name) {
  return /^META-INF\/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))$/iu.test(name);
}

export function normalizedEntry(entry, replacement) {
  if (replacement === undefined) {
    return {
      ...entry,
      flags: entry.flags & ~DATA_DESCRIPTOR_FLAG,
    };
  }
  return {
    ...entry,
    flags: entry.flags & ~DATA_DESCRIPTOR_FLAG,
    method: 0,
    crc32: crc32(replacement),
    compressedSize: replacement.length,
    uncompressedSize: replacement.length,
    compressedData: replacement,
    localExtra: Buffer.alloc(0),
    centralExtra: Buffer.alloc(0),
  };
}

export function localRecord(entry) {
  const header = Buffer.alloc(30);
  header.writeUInt32LE(LOCAL_SIGNATURE, 0);
  header.writeUInt16LE(entry.centralHeader.readUInt16LE(6), 4);
  header.writeUInt16LE(entry.flags, 6);
  header.writeUInt16LE(entry.method, 8);
  header.writeUInt16LE(entry.centralHeader.readUInt16LE(12), 10);
  header.writeUInt16LE(entry.centralHeader.readUInt16LE(14), 12);
  header.writeUInt32LE(entry.crc32, 14);
  header.writeUInt32LE(entry.compressedSize, 18);
  header.writeUInt32LE(entry.uncompressedSize, 22);
  header.writeUInt16LE(entry.nameBytes.length, 26);
  header.writeUInt16LE(entry.localExtra.length, 28);
  return Buffer.concat([
    header,
    entry.nameBytes,
    entry.localExtra,
    entry.compressedData,
  ]);
}

export function centralRecord(entry, localOffset) {
  const header = Buffer.from(entry.centralHeader);
  header.writeUInt16LE(entry.flags, 8);
  header.writeUInt16LE(entry.method, 10);
  header.writeUInt32LE(entry.crc32, 16);
  header.writeUInt32LE(entry.compressedSize, 20);
  header.writeUInt32LE(entry.uncompressedSize, 24);
  header.writeUInt16LE(entry.nameBytes.length, 28);
  header.writeUInt16LE(entry.centralExtra.length, 30);
  header.writeUInt16LE(entry.comment.length, 32);
  header.writeUInt32LE(localOffset, 42);
  return Buffer.concat([
    header,
    entry.nameBytes,
    entry.centralExtra,
    entry.comment,
  ]);
}

function buildApk(sourceEntries, mutation) {
  const localRecords = [];
  const centralRecords = [];
  let localOffset = 0;
  let payloadCount = 0;

  for (const sourceEntry of sourceEntries) {
    if (isSignatureEntry(sourceEntry.name)) {
      continue;
    }
    if (sourceEntry.name === PAYLOAD_ENTRY) {
      payloadCount += 1;
      if (mutation === "missing") {
        continue;
      }
    }
    const replacement =
      sourceEntry.name === PAYLOAD_ENTRY
        ? mutation === "empty"
          ? Buffer.alloc(0)
          : Buffer.alloc(112, 0x5a)
        : undefined;
    const entry = normalizedEntry(sourceEntry, replacement);
    const local = localRecord(entry);
    localRecords.push(local);
    centralRecords.push(centralRecord(entry, localOffset));
    localOffset += local.length;
  }
  if (payloadCount !== 1) {
    fail(`expected one payload entry, found ${payloadCount}`);
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

async function main() {
  const [inputArgument, outputArgument] = process.argv.slice(2);
  if (!inputArgument || !outputArgument) {
    fail("usage: create-m0-04-tampered-apks.mjs <input.apk> <output-dir>");
  }
  const repositoryRoot = process.cwd();
  const outputDirectory = path.resolve(outputArgument);
  const allowedRoots = [
    path.join(repositoryRoot, "build"),
    path.join(repositoryRoot, "artifacts"),
  ];
  if (
    !allowedRoots.some(
      (root) =>
        outputDirectory === root ||
        outputDirectory.startsWith(`${root}${path.sep}`),
    )
  ) {
    fail("output directory must be under the ignored build/ or artifacts/ tree");
  }

  const apk = await readFile(inputArgument);
  const entries = readEntries(apk);
  await mkdir(outputDirectory, { recursive: true });
  const outputs = [];
  for (const mutation of ["missing", "corrupt", "empty"]) {
    const bytes = buildApk(entries, mutation);
    const output = path.join(outputDirectory, `m0-04-${mutation}-unsigned.apk`);
    await writeFile(output, bytes, { flag: "wx" });
    outputs.push({
      mutation,
      path: path.relative(repositoryRoot, output).replaceAll("\\", "/"),
      bytes: bytes.length,
      sha256: sha256(bytes),
    });
  }
  process.stdout.write(
    `${JSON.stringify(
      {
        task_id: "M0-04",
        input_sha256: sha256(apk),
        outputs,
        result: "PASS",
      },
      null,
      2,
    )}\n`,
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.stack ?? error}\n`);
    process.exitCode = 1;
  });
}
