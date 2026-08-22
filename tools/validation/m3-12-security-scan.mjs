import path from "node:path";
import fs from "node:fs";
import { inflateRawSync } from "node:zlib";

const MAX_ENTRY_BYTES = 32 * 1024 * 1024;
const MAX_TOTAL_BYTES = 128 * 1024 * 1024;
const MAX_SIGNING_BLOCK_BYTES = 16 * 1024 * 1024;
const APK_SIG_MAGIC = Buffer.from("APK Sig Block 42", "ascii");
const TEXT_PATTERNS = Object.freeze([
  ["PEM private key", /-----BEGIN (?:ENCRYPTED |RSA |EC |OPENSSH )?PRIVATE KEY-----/iu],
  ["GitHub fine-grained token", /github_pat_[A-Za-z0-9_]{20,}/u],
  ["GitHub token", /gh[pousr]_[A-Za-z0-9]{20,}/u],
  ["Bearer credential", /Bearer\s+[A-Za-z0-9._~+\/-]{16,}={0,2}/u],
  ["profile password variable", /M310_PROFILE_(?:PASS|PASSWORD|SECRET|TOKEN)/iu],
  ["container seed", /container-seed\.bin/iu],
  ["keystore filename", /(?:^|[\\/\s"'])[^\\/\s"']*\.(?:jks|keystore|p12|pfx)(?:$|[\\/\s"'])/imu],
  ["Windows user path", /[A-Za-z]:\\(?:Users|Documents and Settings)\\/iu],
  ["UNC user path", /\\\\[^\\\r\n]+\\(?:Users|Documents and Settings)\\/iu],
  ["Unix user path", /\/(?:home|Users|root)\//u],
]);

function fail(label, detail) {
  throw new Error(`M3-12 sensitive scan failed: ${detail} in ${label}`);
}
function u16(bytes, offset, label) {
  if (!Number.isSafeInteger(offset) || offset < 0 || offset + 2 > bytes.length) fail(label, "u16 bounds");
  return bytes.readUInt16LE(offset);
}
function u32(bytes, offset, label) {
  if (!Number.isSafeInteger(offset) || offset < 0 || offset + 4 > bytes.length) fail(label, "u32 bounds");
  return bytes.readUInt32LE(offset);
}
function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) {
    value ^= byte;
    for (let bit = 0; bit < 8; bit += 1) value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
  }
  return (value ^ 0xffffffff) >>> 0;
}
function findEocd(bytes, label) {
  const first = Math.max(0, bytes.length - 22 - 0xffff);
  for (let offset = bytes.length - 22; offset >= first; offset -= 1) {
    if (u32(bytes, offset, label) === 0x06054b50 && offset + 22 + u16(bytes, offset + 20, label) === bytes.length) return offset;
  }
  fail(label, "valid EOCD not found");
}

export function scanSensitiveText(text, label) {
  for (const [name, pattern] of TEXT_PATTERNS) if (pattern.test(text)) fail(label, name);
}

export function scanSensitiveBytes(bytes, label) {
  scanSensitiveText(Buffer.from(bytes).toString("latin1"), label);
}

function validateTailGap(bytes, localEnd, centralOffset, label) {
  if (localEnd === centralOffset) return;
  if (localEnd > centralOffset || centralOffset < 24 || !bytes.subarray(centralOffset - 16, centralOffset).equals(APK_SIG_MAGIC)) {
    fail(label, "unexplained local-to-central gap");
  }
  const sizeValue = bytes.readBigUInt64LE(centralOffset - 24);
  if (sizeValue > BigInt(MAX_SIGNING_BLOCK_BYTES) || sizeValue > BigInt(Number.MAX_SAFE_INTEGER)) fail(label, "APK Signing Block size");
  const totalSize = Number(sizeValue) + 8;
  const blockStart = centralOffset - totalSize;
  if (totalSize < 32 || blockStart < localEnd || blockStart % 4096 !== 0 ||
      bytes.readBigUInt64LE(blockStart) !== sizeValue) fail(label, "APK Signing Block bounds");
  const padding = bytes.subarray(localEnd, blockStart);
  if (padding.length > 4095 || padding.some((byte) => byte !== 0)) fail(label, "APK alignment padding");
}

export function scanApkBytes(input, label) {
  const bytes = Buffer.from(input);
  scanSensitiveBytes(bytes, `${label} raw bytes`);
  const eocd = findEocd(bytes, label);
  if (u16(bytes, eocd + 4, label) !== 0 || u16(bytes, eocd + 6, label) !== 0) fail(label, "multi-disk ZIP");
  const count = u16(bytes, eocd + 10, label);
  if (count < 1 || count !== u16(bytes, eocd + 8, label)) fail(label, "entry count");
  const centralSize = u32(bytes, eocd + 12, label);
  const centralOffset = u32(bytes, eocd + 16, label);
  if (centralOffset + centralSize !== eocd) fail(label, "central directory bounds");
  const names = new Set(); const ranges = [];
  let cursor = centralOffset;
  let totalBytes = 0;
  for (let index = 0; index < count; index += 1) {
    if (cursor + 46 > eocd || u32(bytes, cursor, label) !== 0x02014b50) fail(label, "central record");
    const flags = u16(bytes, cursor + 8, label);
    const method = u16(bytes, cursor + 10, label);
    const crc = u32(bytes, cursor + 16, label);
    const compressedSize = u32(bytes, cursor + 20, label);
    const size = u32(bytes, cursor + 24, label);
    const nameLength = u16(bytes, cursor + 28, label);
    const extraLength = u16(bytes, cursor + 30, label);
    const commentLength = u16(bytes, cursor + 32, label);
    const disk = u16(bytes, cursor + 34, label);
    const external = u32(bytes, cursor + 38, label);
    const localOffset = u32(bytes, cursor + 42, label);
    if ((flags & ~(0x0800 | 0x0008)) !== 0 || (method !== 0 && method !== 8) || disk !== 0) fail(label, "unsupported ZIP policy");
    if ((((external >>> 16) & 0xf000) === 0xa000)) fail(label, "symlink entry");
    if (size > MAX_ENTRY_BYTES || totalBytes + size > MAX_TOTAL_BYTES) fail(label, "expanded size limit");
    const centralEnd = cursor + 46 + nameLength + extraLength + commentLength;
    if (nameLength < 1 || centralEnd > eocd) fail(label, "central name bounds");
    const nameBytes = bytes.subarray(cursor + 46, cursor + 46 + nameLength);
    const name = nameBytes.toString("utf8");
    const segments = name.split("/");
    if (!Buffer.from(name, "utf8").equals(nameBytes) || name.includes("\0") || name.includes("\\") || name.startsWith("/") ||
        /^[A-Za-z]:/u.test(name) || segments.includes("..") || names.has(name)) fail(label, "unsafe or duplicate entry name");
    scanSensitiveText(name, `${label} entry name`);
    names.add(name);
    if (localOffset + 30 > centralOffset || u32(bytes, localOffset, label) !== 0x04034b50) fail(label, "local record");
    if (u16(bytes, localOffset + 6, label) !== flags || u16(bytes, localOffset + 8, label) !== method) fail(label, "local policy mismatch");
    const localNameLength = u16(bytes, localOffset + 26, label);
    const localExtraLength = u16(bytes, localOffset + 28, label);
    const localCrc = u32(bytes, localOffset + 14, label);
    const localCompressedSize = u32(bytes, localOffset + 18, label);
    const localSize = u32(bytes, localOffset + 22, label);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const dataEnd = dataStart + compressedSize;
    if (dataEnd > centralOffset || localNameLength !== nameLength ||
        !bytes.subarray(localOffset + 30, localOffset + 30 + localNameLength).equals(nameBytes)) fail(label, "local name/data bounds");
    let recordEnd = dataEnd;
    if ((flags & 0x0008) === 0) {
      if (localCrc !== crc || localCompressedSize !== compressedSize || localSize !== size) fail(label, `local integrity differs: ${name}`);
    } else {
      const localValuesValid = (localCrc === 0 && localCompressedSize === 0 && localSize === 0) ||
        (localCrc === crc && localCompressedSize === compressedSize && localSize === size);
      if (!localValuesValid) fail(label, `local descriptor values differ: ${name}`);
      let descriptor = dataEnd;
      if (u32(bytes, descriptor, label) === 0x08074b50) descriptor += 4;
      if (descriptor + 12 > centralOffset || u32(bytes, descriptor, label) !== crc ||
          u32(bytes, descriptor + 4, label) !== compressedSize || u32(bytes, descriptor + 8, label) !== size) {
        fail(label, `data descriptor differs: ${name}`);
      }
      recordEnd = descriptor + 12;
    }
    let data;
    try {
      data = method === 0 ? Buffer.from(bytes.subarray(dataStart, dataEnd)) :
        inflateRawSync(bytes.subarray(dataStart, dataEnd), { maxOutputLength: Math.min(MAX_ENTRY_BYTES, size + 1) });
    } catch {
      fail(label, `entry inflate failed: ${name}`);
    }
    if (data.length !== size || crc32(data) !== crc) fail(label, `entry integrity differs: ${name}`);
    scanSensitiveBytes(data, `${label}!${name}`);
    totalBytes += data.length;
    ranges.push([localOffset, recordEnd]);
    cursor = centralEnd;
  }
  if (cursor !== eocd) fail(label, "central directory parsed length");
  ranges.sort((left, right) => left[0] - right[0]);
  let localEnd = 0;
  for (const [start, end] of ranges) {
    if (start !== localEnd || end <= start || end > centralOffset) fail(label, "local record overlap or gap");
    localEnd = end;
  }
  validateTailGap(bytes, localEnd, centralOffset, label);
  return { entryCount: names.size, expandedBytes: totalBytes };
}

export function assertContainedNewOutput(repositoryRoot, allowedRoot, requestedOutput, label) {
  const repositoryReal = path.resolve(repositoryRoot);
  const allowedLexical = path.resolve(allowedRoot);
  const outputLexical = path.resolve(requestedOutput);
  const relative = path.relative(allowedLexical, outputLexical);
  if (relative === "" || relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    fail(label, "output escapes allowed root");
  }
  const rootRelative = path.relative(repositoryReal, allowedLexical);
  if (rootRelative === ".." || rootRelative.startsWith(`..${path.sep}`) || path.isAbsolute(rootRelative)) fail(label, "allowed root escapes repository");
  let current = repositoryReal;
  for (const segment of rootRelative.split(path.sep).filter(Boolean)) {
    current = path.join(current, segment);
    const stat = fs.lstatSync(current, { throwIfNoEntry: false });
    if (!stat) fs.mkdirSync(current);
    const after = fs.lstatSync(current);
    if (!after.isDirectory() || after.isSymbolicLink()) fail(label, "allowed root contains link or non-directory");
    if (!samePath(fs.realpathSync.native(current), current)) fail(label, "allowed root contains junction");
  }
  const allowedReal = fs.realpathSync.native(allowedLexical);
  const parentRelative = path.relative(allowedLexical, path.dirname(outputLexical));
  current = allowedReal;
  for (const segment of parentRelative.split(path.sep).filter(Boolean)) {
    current = path.join(current, segment);
    const stat = fs.lstatSync(current, { throwIfNoEntry: false });
    if (!stat) fs.mkdirSync(current);
    const after = fs.lstatSync(current);
    if (!after.isDirectory() || after.isSymbolicLink() || !samePath(fs.realpathSync.native(current), current)) fail(label, "output parent contains link or junction");
  }
  const output = path.join(fs.realpathSync.native(current), path.basename(outputLexical));
  const existing = fs.lstatSync(output, { throwIfNoEntry: false });
  if (existing) fail(label, "output already exists");
  return output;
}
function samePath(left, right) {
  const a = path.resolve(left); const b = path.resolve(right);
  return process.platform === "win32" ? a.toLowerCase() === b.toLowerCase() : a === b;
}
