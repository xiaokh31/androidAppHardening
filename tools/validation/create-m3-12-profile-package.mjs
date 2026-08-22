#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { deflateRawSync } from "node:zlib";

const SOURCE_ENTRIES = Object.freeze([
  ["derivation-manifest.json", 1161, "878d092a3cae6f4aa73cb722ea0bb9aa2f1eb32917a19b8c83220502dbdf4de8"],
  ["observer.dex", 4748, "537b1ba424961d3897d574c10ec155e7b01cfffa313d71a0ade1d0c06e26dc88"],
  ["preparation-report.json", 782, "bf174be280410dc98ac532a7aab04e3c1a5890a0f5693a5affe55362bec3a698"],
  ["profile-baseline-aligned.apk", 25819, "8a39bf6e830e18d997ababe767f290bb3ee5489d31cafbb614aa5a625322b7d8"],
  ["profile-baseline-unsigned.apk", 23097, "423461bc1b900230021d2c950f5d5ce1b10f37911a8d63ea4f84a0b46e93fbe4"],
  ["profile-baseline.apk", 33971, "a062e0994482b1db417ff710c554364ec80e9f8d5fa84b5745ff5753308b764b"],
  ["profile-protected-aligned.apk", 1279696, "ffcf606605ed7a13cd9f61aaa11076ff58bbe620308683ac93baa729d0c28c09"],
  ["profile-protected-unsigned.apk", 1252546, "167c44aa4a15071b762fcec18fd4bfcc55087676577750dc0177f8734dad7b25"],
  ["profile-protected.apk", 1287848, "1ce941404d8e6105764d041c449a60016312bc9c9671a8f8eb97c4e8b6820a10"],
]);
const SENSITIVE = Object.freeze([
  `-----BEGIN ${"PRIVATE"} KEY-----`, `-----BEGIN ENCRYPTED ${"PRIVATE"} KEY-----`, "M310_PROFILE_PASS",
  "container-seed.bin", "profile.p12", "C:\\Users\\", "D:\\works\\",
]);
const CRC_TABLE = new Uint32Array(256);
for (let n = 0; n < 256; n += 1) {
  let value = n;
  for (let bit = 0; bit < 8; bit += 1) value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
  CRC_TABLE[n] = value >>> 0;
}

function fail(message) { throw new Error(`M3-12 package creation failed: ${message}`); }
function sha256(bytes) { return crypto.createHash("sha256").update(bytes).digest("hex"); }
function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) value = CRC_TABLE[(value ^ byte) & 0xff] ^ (value >>> 8);
  return (value ^ 0xffffffff) >>> 0;
}
function contained(parent, child) {
  const relative = path.relative(parent, child);
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative));
}
function regularFileBelow(root, name) {
  const candidate = path.resolve(root, name);
  if (!contained(root, candidate) || candidate === root) fail(`source entry escapes root: ${name}`);
  const stat = fs.lstatSync(candidate, { throwIfNoEntry: false });
  if (!stat?.isFile() || stat.isSymbolicLink()) fail(`source entry is not a regular file: ${name}`);
  if (!contained(fs.realpathSync.native(root), fs.realpathSync.native(candidate))) fail(`source entry realpath escapes root: ${name}`);
  return { candidate, stat };
}
function localHeader(name, data, compressed) {
  const nameBytes = Buffer.from(name, "utf8");
  const header = Buffer.alloc(30);
  header.writeUInt32LE(0x04034b50, 0); header.writeUInt16LE(20, 4); header.writeUInt16LE(0x0800, 6);
  header.writeUInt16LE(8, 8); header.writeUInt16LE(0, 10); header.writeUInt16LE(0x21, 12);
  header.writeUInt32LE(crc32(data), 14); header.writeUInt32LE(compressed.length, 18);
  header.writeUInt32LE(data.length, 22); header.writeUInt16LE(nameBytes.length, 26); header.writeUInt16LE(0, 28);
  return Buffer.concat([header, nameBytes, compressed]);
}
function centralHeader(name, data, compressed, offset) {
  const nameBytes = Buffer.from(name, "utf8");
  const header = Buffer.alloc(46);
  header.writeUInt32LE(0x02014b50, 0); header.writeUInt16LE(20, 4); header.writeUInt16LE(20, 6);
  header.writeUInt16LE(0x0800, 8); header.writeUInt16LE(8, 10); header.writeUInt16LE(0, 12);
  header.writeUInt16LE(0x21, 14); header.writeUInt32LE(crc32(data), 16);
  header.writeUInt32LE(compressed.length, 20); header.writeUInt32LE(data.length, 24);
  header.writeUInt16LE(nameBytes.length, 28); header.writeUInt16LE(0, 30); header.writeUInt16LE(0, 32);
  header.writeUInt16LE(0, 34); header.writeUInt16LE(0, 36); header.writeUInt32LE(0, 38);
  header.writeUInt32LE(offset, 42);
  return Buffer.concat([header, nameBytes]);
}
function zip(entries) {
  const local = [];
  const central = [];
  let offset = 0;
  for (const entry of entries) {
    const compressed = deflateRawSync(entry.data, { level: 9 });
    const localRecord = localHeader(entry.name, entry.data, compressed);
    local.push(localRecord);
    central.push(centralHeader(entry.name, entry.data, compressed, offset));
    offset += localRecord.length;
  }
  const centralBytes = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(0, 4); end.writeUInt16LE(0, 6);
  end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralBytes.length, 12); end.writeUInt32LE(offset, 16); end.writeUInt16LE(0, 20);
  return Buffer.concat([...local, centralBytes, end]);
}

function main() {
  const options = Object.fromEntries(process.argv.slice(2).reduce((pairs, value, index, values) => {
    if (index % 2 === 0) {
      if (!value.startsWith("--") || values[index + 1] === undefined) fail("options must be --name value pairs");
      pairs.push([value.slice(2), values[index + 1]]);
    }
    return pairs;
  }, []));
  if (!options.source || !options.output) fail("--source and --output are required");
  const repository = fs.realpathSync.native(process.cwd());
  const source = path.resolve(options.source);
  const output = path.resolve(options.output);
  const allowedOutput = path.join(repository, "build", "m3-12");
  if (!contained(repository, source) || !contained(allowedOutput, output) || output === allowedOutput || fs.existsSync(output)) {
    fail("source must stay in the repository and output must be a new file below build/m3-12");
  }
  const actualNames = fs.readdirSync(source, { withFileTypes: true }).map((entry) => {
    if (!entry.isFile()) fail(`source contains a non-file entry: ${entry.name}`);
    return entry.name;
  }).sort();
  const expectedNames = SOURCE_ENTRIES.map(([name]) => name).sort();
  if (JSON.stringify(actualNames) !== JSON.stringify(expectedNames)) fail("source entry set differs from the reviewed package");
  const entries = SOURCE_ENTRIES.map(([name, sizeBytes, expectedHash]) => {
    const { candidate, stat } = regularFileBelow(source, name);
    const data = fs.readFileSync(candidate);
    if (stat.size !== sizeBytes || sha256(data) !== expectedHash) fail(`reviewed bytes differ: ${name}`);
    const latin = data.toString("latin1");
    for (const pattern of SENSITIVE) if (latin.includes(pattern)) fail(`sensitive material marker found in ${name}`);
    return { name, data, sizeBytes, sha256: expectedHash };
  });
  const manifest = Buffer.from(`${JSON.stringify({
    schemaVersion: 1,
    taskId: "M3-12",
    packageId: "m3-10-profile-package-v1",
    regenerationPermitted: false,
    entries: entries.map(({ name, sizeBytes, sha256: digest }) => ({ name, sizeBytes, sha256: digest })),
  }, null, 2)}\n`, "utf8");
  const allEntries = [...entries, { name: "m3-12-manifest.json", data: manifest }].sort((a, b) => a.name.localeCompare(b.name));
  const archive = zip(allEntries);
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, archive, { flag: "wx" });
  process.stdout.write(`${JSON.stringify({
    archive: path.relative(repository, output).replaceAll("\\", "/"),
    sizeBytes: archive.length,
    sha256: sha256(archive),
    manifestSizeBytes: manifest.length,
    manifestSha256: sha256(manifest),
    entryCount: allEntries.length,
  }, null, 2)}\n`);
}

main();
