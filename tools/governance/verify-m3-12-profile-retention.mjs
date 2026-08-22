#!/usr/bin/env node

import crypto from "node:crypto";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { deflateRawSync, inflateRawSync } from "node:zlib";
import { scanApkBytes, scanSensitiveBytes } from "../validation/m3-12-security-scan.mjs";

const root = process.cwd();
const lockFile = path.join(root, "docs/evidence/M3-12/profile-package-retention-lock.json");
const metadataFile = path.join(root, "docs/evidence/M3-12/release-metadata.json");
const EXPECTED = Object.freeze({
  releaseId: 374769776,
  assetId: 524507375,
  tag: "m3-10-profile-package-v1",
  target: "9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a",
  assetName: "m3-10-profile-package-v1.zip",
  assetLabel: "M3-10 profile package v1 (test-only)",
  sizeBytes: 2184246,
  sha256: "21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964",
  createdAt: "2026-08-22T01:53:36Z",
  updatedAt: "2026-08-22T01:54:53Z",
});
const EXPECTED_UPSTREAM = Object.freeze({
  implementationCommit: "86ec37475fd7a96b4baf764530baefc3fe3d4cde",
  evidenceCommit: "7a384b321e9afa8df5f683ad1a2b78ba2cb31bd0",
  reviewRecordCommit: "ac2d969392556fd9b338399e6cc2e9c22c90daed",
  reviewRecordPath: "docs/evidence/M3-10/read-only-review-5.md",
  reviewRecordSizeBytes: 2755,
  reviewRecordSha256: "43b9ce026161c60b990c6c56d0932c4a0b931fc0edc8612ebbffde848fc68c10",
  profileLockPath: "tools/validation/m3-10/canonical-profile-lock.json",
  profileLockSizeBytes: 2812,
  profileLockSha256: "a9e130bb4e66e14443d83ea01ef0d60a95adddefa9dc92a9bdc980e5728dab4b",
  acceptedFourApkReportSha256: "1610f895cb1a3003387a2c7f2e2e1474d6fbbfc523da8fc11c88d6cd283c5b93",
  result: "P0=0/P1=0/P2=0",
});
const EXPECTED_ENTRIES = Object.freeze([
  Object.freeze({ name: "derivation-manifest.json", sizeBytes: 1161, sha256: "878d092a3cae6f4aa73cb722ea0bb9aa2f1eb32917a19b8c83220502dbdf4de8" }),
  Object.freeze({ name: "m3-12-manifest.json", sizeBytes: 1620, sha256: "c5f4b45404a6bec5d7915fb6df595d19690022085384592a080c7df454083fd5" }),
  Object.freeze({ name: "observer.dex", sizeBytes: 4748, sha256: "537b1ba424961d3897d574c10ec155e7b01cfffa313d71a0ade1d0c06e26dc88" }),
  Object.freeze({ name: "preparation-report.json", sizeBytes: 782, sha256: "bf174be280410dc98ac532a7aab04e3c1a5890a0f5693a5affe55362bec3a698" }),
  Object.freeze({ name: "profile-baseline-aligned.apk", sizeBytes: 25819, sha256: "8a39bf6e830e18d997ababe767f290bb3ee5489d31cafbb614aa5a625322b7d8" }),
  Object.freeze({ name: "profile-baseline-unsigned.apk", sizeBytes: 23097, sha256: "423461bc1b900230021d2c950f5d5ce1b10f37911a8d63ea4f84a0b46e93fbe4" }),
  Object.freeze({ name: "profile-baseline.apk", sizeBytes: 33971, sha256: "a062e0994482b1db417ff710c554364ec80e9f8d5fa84b5745ff5753308b764b" }),
  Object.freeze({ name: "profile-protected-aligned.apk", sizeBytes: 1279696, sha256: "ffcf606605ed7a13cd9f61aaa11076ff58bbe620308683ac93baa729d0c28c09" }),
  Object.freeze({ name: "profile-protected-unsigned.apk", sizeBytes: 1252546, sha256: "167c44aa4a15071b762fcec18fd4bfcc55087676577750dc0177f8734dad7b25" }),
  Object.freeze({ name: "profile-protected.apk", sizeBytes: 1287848, sha256: "1ce941404d8e6105764d041c449a60016312bc9c9671a8f8eb97c4e8b6820a10" }),
]);
const CRC_TABLE = new Uint32Array(256);
for (let n = 0; n < 256; n += 1) {
  let value = n;
  for (let bit = 0; bit < 8; bit += 1) value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
  CRC_TABLE[n] = value >>> 0;
}

function fail(message) { throw new Error(`M3-12 retention verification failed: ${message}`); }
function sha256(bytes) { return crypto.createHash("sha256").update(bytes).digest("hex"); }
function clone(value) { return structuredClone(value); }
function exactKeys(value, keys, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail(`${label} must be an object`);
  if (Object.keys(value).sort().join("|") !== [...keys].sort().join("|")) fail(`${label} keys differ`);
}
function equal(actual, wanted, label) { if (actual !== wanted) fail(`${label} differs`); }
function u16(bytes, offset, label) {
  if (!Number.isSafeInteger(offset) || offset < 0 || offset + 2 > bytes.length) fail(`${label} u16 bounds`);
  return bytes.readUInt16LE(offset);
}
function u32(bytes, offset, label) {
  if (!Number.isSafeInteger(offset) || offset < 0 || offset + 4 > bytes.length) fail(`${label} u32 bounds`);
  return bytes.readUInt32LE(offset);
}
function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) value = CRC_TABLE[(value ^ byte) & 0xff] ^ (value >>> 8);
  return (value ^ 0xffffffff) >>> 0;
}
function contained(parent, child) {
  const relative = path.relative(parent, child);
  return relative === "" || (relative !== ".." && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative));
}
function samePath(left, right) {
  const a = path.resolve(left); const b = path.resolve(right);
  return process.platform === "win32" ? a.toLowerCase() === b.toLowerCase() : a === b;
}
function zipHeader(name, data, compressed, localOffset, central, flags) {
  const nameBytes = Buffer.from(name, "utf8");
  const header = Buffer.alloc(central ? 46 : 30);
  header.writeUInt32LE(central ? 0x02014b50 : 0x04034b50, 0);
  if (central) { header.writeUInt16LE(20, 4); header.writeUInt16LE(20, 6); }
  else header.writeUInt16LE(20, 4);
  const flagsOffset = central ? 8 : 6; const methodOffset = central ? 10 : 8; const timeOffset = central ? 12 : 10;
  header.writeUInt16LE(flags, flagsOffset); header.writeUInt16LE(8, methodOffset);
  header.writeUInt16LE(0, timeOffset); header.writeUInt16LE(0x21, timeOffset + 2);
  const crcOffset = central ? 16 : 14;
  header.writeUInt32LE(crc32(data), crcOffset); header.writeUInt32LE(compressed.length, crcOffset + 4);
  header.writeUInt32LE(data.length, crcOffset + 8); header.writeUInt16LE(nameBytes.length, central ? 28 : 26);
  if (central) header.writeUInt32LE(localOffset, 42);
  return Buffer.concat([header, nameBytes, central ? Buffer.alloc(0) : compressed]);
}
function makeZip(entries, options = {}) {
  const local = []; const central = []; let offset = 0;
  for (const { name, data } of entries) {
    const compressed = deflateRawSync(data, { level: 9 }); const flags = options.dataDescriptor ? 0x0808 : 0x0800;
    let localRecord = zipHeader(name, data, compressed, offset, false, flags);
    if (options.dataDescriptor) {
      const descriptor = Buffer.alloc(options.descriptorSignature === false ? 12 : 16); let cursor = 0;
      if (options.descriptorSignature !== false) { descriptor.writeUInt32LE(0x08074b50, 0); cursor = 4; }
      descriptor.writeUInt32LE(crc32(data), cursor); descriptor.writeUInt32LE(compressed.length, cursor + 4); descriptor.writeUInt32LE(data.length, cursor + 8);
      localRecord = Buffer.concat([localRecord, descriptor]);
    }
    local.push(localRecord); central.push(zipHeader(name, data, compressed, offset, true, flags)); offset += localRecord.length;
  }
  const centralBytes = Buffer.concat(central); const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralBytes.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, centralBytes, end]);
}
function archiveRecordOffsets(bytes) {
  const eocd = bytes.length - 22; const count = bytes.readUInt16LE(eocd + 10); const records = [];
  let cursor = bytes.readUInt32LE(eocd + 16);
  for (let index = 0; index < count; index += 1) {
    const flags = bytes.readUInt16LE(cursor + 8); const compressedSize = bytes.readUInt32LE(cursor + 20);
    const size = bytes.readUInt32LE(cursor + 24); const nameLength = bytes.readUInt16LE(cursor + 28); const extraLength = bytes.readUInt16LE(cursor + 30);
    const commentLength = bytes.readUInt16LE(cursor + 32); const local = bytes.readUInt32LE(cursor + 42);
    const localNameLength = bytes.readUInt16LE(local + 26); const localExtraLength = bytes.readUInt16LE(local + 28);
    const dataEnd = local + 30 + localNameLength + localExtraLength + compressedSize;
    records.push({ central: cursor, local, flags, compressedSize, size, dataEnd, nameLength,
      name: bytes.subarray(cursor + 46, cursor + 46 + nameLength).toString("utf8") });
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return { eocd, records };
}
function replaceRecordName(bytes, record, name, localToo = true) {
  const encoded = Buffer.from(name, "utf8");
  if (encoded.length !== record.nameLength) fail("self-test replacement name length differs");
  encoded.copy(bytes, record.central + 46);
  if (localToo) encoded.copy(bytes, record.local + 30);
}
function readJson(file, label) {
  try { return JSON.parse(fs.readFileSync(file, "utf8")); } catch { fail(`${label} is missing or invalid JSON`); }
}

function validateLock(lock) {
  exactKeys(lock, ["schemaVersion", "taskId", "packageId", "upstreamReview", "source", "archive", "retention"], "lock");
  equal(lock.schemaVersion, 1, "schemaVersion"); equal(lock.taskId, "M3-12", "taskId");
  equal(lock.packageId, "m3-10-profile-package-v1", "packageId");
  exactKeys(lock.upstreamReview, Object.keys(EXPECTED_UPSTREAM), "upstreamReview");
  for (const [key, wanted] of Object.entries(EXPECTED_UPSTREAM)) equal(lock.upstreamReview[key], wanted, `upstreamReview.${key}`);
  exactKeys(lock.source, ["repository", "releaseId", "tag", "targetCommitish", "draft", "prerelease", "immutable",
    "assetId", "assetName", "assetLabel", "contentType", "assetState", "apiPath", "createdAt", "updatedAt"], "source");
  for (const [key, wanted] of Object.entries({ repository: "xiaokh31/androidAppHardening", releaseId: EXPECTED.releaseId,
    tag: EXPECTED.tag, targetCommitish: EXPECTED.target, draft: false, prerelease: true, immutable: false,
    assetId: EXPECTED.assetId, assetName: EXPECTED.assetName, assetLabel: EXPECTED.assetLabel,
    contentType: "application/zip", assetState: "uploaded",
    apiPath: "/repos/xiaokh31/androidAppHardening/releases/assets/524507375",
    createdAt: EXPECTED.createdAt, updatedAt: EXPECTED.updatedAt })) equal(lock.source[key], wanted, `source.${key}`);
  exactKeys(lock.archive, ["sizeBytes", "sha256", "githubDigest", "entryCount", "entries"], "archive");
  equal(lock.archive.sizeBytes, EXPECTED.sizeBytes, "archive.sizeBytes");
  equal(lock.archive.sha256, EXPECTED.sha256, "archive.sha256");
  equal(lock.archive.githubDigest, `sha256:${EXPECTED.sha256}`, "archive.githubDigest");
  equal(lock.archive.entryCount, 10, "archive.entryCount");
  if (!Array.isArray(lock.archive.entries) || lock.archive.entries.length !== 10) fail("archive.entries count differs");
  const names = new Set();
  for (const [index, entry] of lock.archive.entries.entries()) {
    exactKeys(entry, ["name", "sizeBytes", "sha256"], `archive.entries[${index}]`);
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/u.test(entry.name) || names.has(entry.name)) fail("locked entry name is unsafe or duplicate");
    if (!Number.isSafeInteger(entry.sizeBytes) || entry.sizeBytes < 1 || entry.sizeBytes > 2_000_000) fail("locked entry size is invalid");
    if (!/^[0-9a-f]{64}$/u.test(entry.sha256)) fail("locked entry hash is invalid");
    names.add(entry.name);
  }
  const sorted = [...lock.archive.entries].sort((a, b) => a.name.localeCompare(b.name));
  if (JSON.stringify(sorted) !== JSON.stringify(lock.archive.entries)) fail("locked entries are not sorted");
  if (JSON.stringify(lock.archive.entries) !== JSON.stringify(EXPECTED_ENTRIES)) fail("locked entry values differ");
  exactKeys(lock.retention, ["trackedInGit", "localIgnoredRoot", "consumerPermission", "acceptHeader",
    "regenerationPermitted", "fallbackPermitted", "failClosedIfUnavailable", "replacementChangesAssetId"], "retention");
  for (const [key, wanted] of Object.entries({ trackedInGit: false, localIgnoredRoot: "build/m3-12/",
    consumerPermission: "contents: read", acceptHeader: "application/octet-stream", regenerationPermitted: false,
    fallbackPermitted: false, failClosedIfUnavailable: true, replacementChangesAssetId: true })) {
    equal(lock.retention[key], wanted, `retention.${key}`);
  }
  return lock;
}

function validateMetadata(metadata, lock) {
  exactKeys(metadata, ["repository", "release", "asset"], "metadata");
  equal(metadata.repository, lock.source.repository, "metadata.repository");
  exactKeys(metadata.release, ["id", "tag_name", "target_commitish", "draft", "prerelease", "immutable"], "metadata.release");
  for (const [key, sourceKey] of Object.entries({ id: "releaseId", tag_name: "tag", target_commitish: "targetCommitish",
    draft: "draft", prerelease: "prerelease", immutable: "immutable" })) equal(metadata.release[key], lock.source[sourceKey], `metadata.release.${key}`);
  exactKeys(metadata.asset, ["id", "name", "label", "content_type", "size", "state", "digest", "created_at", "updated_at"], "metadata.asset");
  for (const [key, wanted] of Object.entries({ id: lock.source.assetId, name: lock.source.assetName, label: lock.source.assetLabel,
    content_type: lock.source.contentType, size: lock.archive.sizeBytes, state: lock.source.assetState,
    digest: lock.archive.githubDigest, created_at: lock.source.createdAt, updated_at: lock.source.updatedAt })) {
    equal(metadata.asset[key], wanted, `metadata.asset.${key}`);
  }
}

function gitBytes(commit, relative, label) {
  const result = spawnSync("git", ["show", `${commit}:${relative}`], { cwd: root, encoding: null, timeout: 30_000, maxBuffer: 8 * 1024 * 1024 });
  if (result.status !== 0 || !Buffer.isBuffer(result.stdout)) fail(`${label} is unavailable from Git`);
  return result.stdout;
}
function validateUpstreamObjects(lock) {
  for (const [older, newer, label] of [
    [lock.upstreamReview.implementationCommit, lock.upstreamReview.evidenceCommit, "implementation-to-evidence"],
    [lock.upstreamReview.evidenceCommit, lock.upstreamReview.reviewRecordCommit, "evidence-to-review"],
  ]) {
    const result = spawnSync("git", ["merge-base", "--is-ancestor", older, newer], { cwd: root, encoding: "utf8", timeout: 30_000 });
    if (result.status !== 0) fail(`upstream ancestry differs: ${label}`);
  }
  const reviewBytes = gitBytes(lock.upstreamReview.reviewRecordCommit, lock.upstreamReview.reviewRecordPath, "upstream review record");
  equal(reviewBytes.length, lock.upstreamReview.reviewRecordSizeBytes, "upstream review record size");
  equal(sha256(reviewBytes), lock.upstreamReview.reviewRecordSha256, "upstream review record hash");
  const reviewText = reviewBytes.toString("utf8");
  for (const value of [lock.upstreamReview.implementationCommit, lock.upstreamReview.evidenceCommit,
    lock.upstreamReview.acceptedFourApkReportSha256, "PASS — P0=0/P1=0/P2=0"]) {
    if (!reviewText.includes(value)) fail(`upstream review record missing ${value}`);
  }
  const profileBytes = gitBytes(lock.upstreamReview.reviewRecordCommit, lock.upstreamReview.profileLockPath, "upstream profile lock");
  equal(profileBytes.length, lock.upstreamReview.profileLockSizeBytes, "upstream profile lock size");
  equal(sha256(profileBytes), lock.upstreamReview.profileLockSha256, "upstream profile lock hash");
  let profile;
  try { profile = JSON.parse(profileBytes.toString("utf8")); } catch { fail("upstream profile lock JSON differs"); }
  const byName = Object.fromEntries(lock.archive.entries.map((entry) => [entry.name, entry]));
  for (const [name, value] of [
    ["observer.dex", profile.observer], ["derivation-manifest.json", profile.derivation],
    ["profile-baseline-unsigned.apk", profile.outputs?.unsignedBaseline], ["profile-protected-unsigned.apk", profile.outputs?.unsignedProtected],
    ["profile-baseline-aligned.apk", profile.outputs?.alignedBaseline], ["profile-protected-aligned.apk", profile.outputs?.alignedProtected],
    ["profile-baseline.apk", profile.outputs?.signedBaseline], ["profile-protected.apk", profile.outputs?.signedProtected],
  ]) {
    const expected = byName[name];
    const size = name === "observer.dex" ? value?.dexSizeBytes : name === "derivation-manifest.json" ? value?.manifestSizeBytes : value?.sizeBytes;
    const digest = name === "observer.dex" ? value?.dexSha256 : name === "derivation-manifest.json" ? value?.manifestSha256 : value?.sha256;
    equal(size, expected.sizeBytes, `upstream ${name} size`); equal(digest, expected.sha256, `upstream ${name} hash`);
  }
  equal(profile.retention?.regenerationPermitted, false, "upstream regeneration policy");
}

function validateArchive(bytes, lock) {
  equal(bytes.length, lock.archive.sizeBytes, "archive byte length");
  equal(sha256(bytes), lock.archive.sha256, "archive byte hash");
  if (bytes.length < 22 || u32(bytes, bytes.length - 22, "EOCD") !== 0x06054b50) fail("EOCD must be exactly at archive end");
  const eocd = bytes.length - 22;
  equal(u16(bytes, eocd + 4, "EOCD disk"), 0, "EOCD disk");
  equal(u16(bytes, eocd + 6, "EOCD central disk"), 0, "EOCD central disk");
  equal(u16(bytes, eocd + 8, "EOCD disk entries"), lock.archive.entryCount, "EOCD disk entries");
  equal(u16(bytes, eocd + 10, "EOCD total entries"), lock.archive.entryCount, "EOCD total entries");
  equal(u16(bytes, eocd + 20, "EOCD comment"), 0, "EOCD comment length");
  const centralSize = u32(bytes, eocd + 12, "central size");
  const centralOffset = u32(bytes, eocd + 16, "central offset");
  if (centralOffset + centralSize !== eocd) fail("central directory does not end at EOCD");
  const values = new Map(); const ranges = [];
  let cursor = centralOffset;
  for (let index = 0; index < lock.archive.entryCount; index += 1) {
    if (u32(bytes, cursor, "central signature") !== 0x02014b50 || cursor + 46 > eocd) fail("central record malformed");
    const flags = u16(bytes, cursor + 8, "central flags"); const method = u16(bytes, cursor + 10, "central method");
    const crc = u32(bytes, cursor + 16, "central crc"); const compressedSize = u32(bytes, cursor + 20, "central compressed size");
    const size = u32(bytes, cursor + 24, "central size"); const nameLength = u16(bytes, cursor + 28, "central name");
    const extraLength = u16(bytes, cursor + 30, "central extra"); const commentLength = u16(bytes, cursor + 32, "central comment");
    const disk = u16(bytes, cursor + 34, "central disk"); const external = u32(bytes, cursor + 38, "central attributes");
    const localOffset = u32(bytes, cursor + 42, "local offset");
    if (flags !== 0x0800 || method !== 8 || extraLength !== 0 || commentLength !== 0 || disk !== 0 || external !== 0) fail("central policy differs");
    const centralEnd = cursor + 46 + nameLength;
    if (centralEnd > eocd) fail("central name exceeds bounds");
    const nameBytes = bytes.subarray(cursor + 46, centralEnd); const name = nameBytes.toString("utf8");
    if (!Buffer.from(name, "utf8").equals(nameBytes) || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/u.test(name) || values.has(name)) fail("ZIP name is unsafe or duplicate");
    if (u32(bytes, localOffset, "local signature") !== 0x04034b50 || localOffset + 30 > centralOffset) fail("local record malformed");
    equal(u16(bytes, localOffset + 6, "local flags"), flags, "local flags"); equal(u16(bytes, localOffset + 8, "local method"), method, "local method");
    equal(u32(bytes, localOffset + 14, "local crc"), crc, "local crc"); equal(u32(bytes, localOffset + 18, "local compressed"), compressedSize, "local compressed size");
    equal(u32(bytes, localOffset + 22, "local size"), size, "local size"); equal(u16(bytes, localOffset + 26, "local name"), nameLength, "local name length");
    equal(u16(bytes, localOffset + 28, "local extra"), 0, "local extra length");
    const localNameStart = localOffset + 30; const dataStart = localNameStart + nameLength; const dataEnd = dataStart + compressedSize;
    if (dataEnd > centralOffset || !bytes.subarray(localNameStart, dataStart).equals(nameBytes)) fail("local name/data bounds differ");
    let data; try { data = inflateRawSync(bytes.subarray(dataStart, dataEnd)); } catch { fail(`deflate failed for ${name}`); }
    equal(data.length, size, `${name} uncompressed size`); equal(crc32(data), crc, `${name} CRC-32`);
    scanSensitiveBytes(data, name);
    if (name.endsWith(".apk")) scanApkBytes(data, name);
    values.set(name, data); ranges.push([localOffset, dataEnd]); cursor = centralEnd;
  }
  equal(cursor, eocd, "central directory parsed length");
  ranges.sort((a, b) => a[0] - b[0]);
  let expectedOffset = 0; for (const [start, end] of ranges) { equal(start, expectedOffset, "local record contiguity"); expectedOffset = end; }
  equal(expectedOffset, centralOffset, "local records end");
  const lockedNames = lock.archive.entries.map((entry) => entry.name);
  if (JSON.stringify([...values.keys()].sort()) !== JSON.stringify([...lockedNames].sort())) fail("ZIP member set differs");
  for (const entry of lock.archive.entries) {
    const data = values.get(entry.name); equal(data.length, entry.sizeBytes, `${entry.name} locked size`); equal(sha256(data), entry.sha256, `${entry.name} locked hash`);
  }
  const manifest = JSON.parse(values.get("m3-12-manifest.json").toString("utf8"));
  exactKeys(manifest, ["schemaVersion", "taskId", "packageId", "regenerationPermitted", "entries"], "member manifest");
  equal(manifest.schemaVersion, 1, "member manifest schema"); equal(manifest.taskId, "M3-12", "member manifest task");
  equal(manifest.packageId, "m3-10-profile-package-v1", "member manifest package"); equal(manifest.regenerationPermitted, false, "member manifest regeneration");
  const expectedManifestEntries = lock.archive.entries.filter((entry) => entry.name !== "m3-12-manifest.json");
  if (JSON.stringify(manifest.entries) !== JSON.stringify(expectedManifestEntries)) fail("member manifest entries differ");
  const report = JSON.parse(values.get("preparation-report.json").toString("utf8"));
  equal(report.signingSecretsPublished, false, "preparation signingSecretsPublished"); equal(report.v3Only, true, "preparation v3Only"); equal(report.result, "PASS", "preparation result");
  return { entries: values.size, values };
}

function validateDocuments() {
  const files = [
    ["docs/adr/0017-profile-package-retention-boundary.md", ["374769776", "524507375", EXPECTED.sha256, "immutable=false", "contents: read", EXPECTED_UPSTREAM.reviewRecordCommit]],
    ["docs/adr/0016-end-to-end-startup-attribution-boundary.md", ["M3-12", "524507375", EXPECTED.sha256]],
    ["docs/tasks/M3-12-profile-package-retention.md", ["Issue #75", "P0=0/P1=0/P2=0", "no profile regeneration", EXPECTED_UPSTREAM.reviewRecordCommit]],
    ["docs/tasks/M3-10-startup-attribution-diagnostic.md", ["M3-12", "numeric asset ID"]],
    ["docs/evidence/M3-12/provenance.md", ["byte_equal=true", "immutable=false", "no Android environment ran", EXPECTED_UPSTREAM.profileLockSha256]],
  ];
  for (const [relative, phrases] of files) {
    const text = fs.readFileSync(path.join(root, relative), "utf8");
    for (const phrase of phrases) if (!text.includes(phrase)) fail(`${relative} missing ${phrase}`);
  }
}

function selfTest(lock, metadata, archiveBytes, archiveValues) {
  const mutations = [
    ["upstream_implementation", (x) => { x.upstreamReview.implementationCommit = "0".repeat(40); }],
    ["upstream_evidence", (x) => { x.upstreamReview.evidenceCommit = "0".repeat(40); }],
    ["upstream_review_record", (x) => { x.upstreamReview.reviewRecordCommit = "0".repeat(40); }],
    ["upstream_review_hash", (x) => { x.upstreamReview.reviewRecordSha256 = "0".repeat(64); }],
    ["upstream_profile_lock_hash", (x) => { x.upstreamReview.profileLockSha256 = "0".repeat(64); }],
    ["upstream_report_hash", (x) => { x.upstreamReview.acceptedFourApkReportSha256 = "0".repeat(64); }],
    ["upstream_review_result", (x) => { x.upstreamReview.result = "P0=0/P1=1/P2=0"; }],
    ["release_id", (x) => { x.source.releaseId += 1; }], ["asset_id", (x) => { x.source.assetId += 1; }],
    ["asset_name", (x) => { x.source.assetName += ".new"; }], ["target", (x) => { x.source.targetCommitish = "0".repeat(40); }],
    ["draft", (x) => { x.source.draft = true; }], ["immutable_claim", (x) => { x.source.immutable = true; }],
    ["archive_size", (x) => { x.archive.sizeBytes += 1; }], ["archive_hash", (x) => { x.archive.sha256 = "0".repeat(64); }],
    ["server_digest", (x) => { x.archive.githubDigest = `sha256:${"0".repeat(64)}`; }],
    ["entry_removed", (x) => { x.archive.entries.pop(); }], ["entry_added", (x) => { x.archive.entries.push(clone(x.archive.entries[0])); }],
    ["entry_hash", (x) => { x.archive.entries[0].sha256 = "0".repeat(64); }],
    ["regeneration", (x) => { x.retention.regenerationPermitted = true; }], ["fallback", (x) => { x.retention.fallbackPermitted = true; }],
    ["unavailable", (x) => { x.retention.failClosedIfUnavailable = false; }], ["write_permission", (x) => { x.retention.consumerPermission = "contents: write"; }],
  ];
  for (const [name, mutate] of mutations) {
    const changed = clone(lock); mutate(changed); let rejected = false;
    try { validateLock(changed); } catch { rejected = true; }
    if (!rejected) fail(`lock mutation accepted: ${name}`);
  }
  const metadataMutation = clone(metadata); metadataMutation.asset.id += 1;
  let metadataRejected = false; try { validateMetadata(metadataMutation, lock); } catch { metadataRejected = true; }
  if (!metadataRejected) fail("metadata asset mutation accepted");
  let archiveMutations = 0;
  if (archiveBytes) {
    const layout = archiveRecordOffsets(archiveBytes);
    const record = (name) => {
      const value = layout.records.find((entry) => entry.name === name);
      if (!value) fail(`self-test record missing: ${name}`);
      return value;
    };
    const cases = [
      ["trailing", Buffer.concat([archiveBytes, Buffer.from([0])])],
      ["truncated", archiveBytes.subarray(0, archiveBytes.length - 1)],
      ["byte_flip", (() => { const value = Buffer.from(archiveBytes); value[Math.floor(value.length / 3)] ^= 1; return value; })()],
      ["duplicate_member", (() => { const value = Buffer.from(archiveBytes); replaceRecordName(value, record("profile-protected-aligned.apk"), "profile-baseline-unsigned.apk"); return value; })()],
      ["member_set_substitution", (() => { const value = Buffer.from(archiveBytes); replaceRecordName(value, record("observer.dex"), "missing1.dex"); return value; })()],
      ["traversal_member", (() => { const value = Buffer.from(archiveBytes); replaceRecordName(value, record("derivation-manifest.json"), `../${"x".repeat(21)}`); return value; })()],
      ["unsupported_method", (() => { const value = Buffer.from(archiveBytes); const item = record("observer.dex"); value.writeUInt16LE(99, item.central + 10); value.writeUInt16LE(99, item.local + 8); return value; })()],
      ["unsupported_flags", (() => { const value = Buffer.from(archiveBytes); const item = record("observer.dex"); value.writeUInt16LE(1, item.central + 8); value.writeUInt16LE(1, item.local + 6); return value; })()],
      ["local_offset", (() => { const value = Buffer.from(archiveBytes); const item = record("observer.dex"); value.writeUInt32LE(value.length, item.central + 42); return value; })()],
      ["local_central_name_mismatch", (() => { const value = Buffer.from(archiveBytes); const item = record("observer.dex"); replaceRecordName(value, item, "observer.dfx", false); return value; })()],
      ["missing_entry_count", (() => { const value = Buffer.from(archiveBytes); value.writeUInt16LE(layout.records.length - 1, layout.eocd + 8); value.writeUInt16LE(layout.records.length - 1, layout.eocd + 10); return value; })()],
      ["extra_entry_count", (() => { const value = Buffer.from(archiveBytes); value.writeUInt16LE(layout.records.length + 1, layout.eocd + 8); value.writeUInt16LE(layout.records.length + 1, layout.eocd + 10); return value; })()],
    ];
    for (const [name, bytes] of cases) {
      const changedLock = clone(lock); changedLock.archive.sizeBytes = bytes.length; changedLock.archive.sha256 = sha256(bytes);
      let rejected = false; try { validateArchive(bytes, changedLock); } catch { rejected = true; }
      if (!rejected) fail(`archive mutation accepted: ${name}`);
      archiveMutations += 1;
    }
  }
  const sensitiveVectors = [
    ["pem", ["-----BEGIN", "RSA", "PRIVATE KEY-----"].join(" ")],
    ["github_fine", `github${"_pat_"}${"a".repeat(24)}`],
    ["github_classic", `gh${"p_"}${"b".repeat(24)}`],
    ["bearer", `Bearer ${"c".repeat(24)}`],
    ["keystore", "fixture.keystore"],
    ["windows_path", `C:${"\\"}Users${"\\"}fixture${"\\"}secret`],
    ["unix_path", ["", "home", "fixture", "secret"].join("/")],
  ];
  for (const [name, text] of sensitiveVectors) {
    let rejected = false; try { scanSensitiveBytes(Buffer.from(text), `self-test ${name}`); } catch { rejected = true; }
    if (!rejected) fail(`sensitive mutation accepted: ${name}`);
  }
  const descriptorWithSignature = makeZip([{ name: "assets/value.bin", data: Buffer.from("safe") }], { dataDescriptor: true });
  const descriptorWithoutSignature = makeZip([{ name: "assets/value.bin", data: Buffer.from("safe") }], { dataDescriptor: true, descriptorSignature: false });
  scanApkBytes(descriptorWithSignature, "self-test descriptor signature positive");
  scanApkBytes(descriptorWithoutSignature, "self-test descriptor no-signature positive");
  const nestedCases = [
    ["nested_content", makeZip([{ name: "assets/value.bin", data: Buffer.from(`gh${"p_"}${"d".repeat(24)}`) }])],
    ["nested_name", makeZip([{ name: "assets/release.jks", data: Buffer.from("safe") }])],
    ["nested_traversal", makeZip([{ name: "../escape.bin", data: Buffer.from("safe") }])],
  ];
  if (archiveValues) {
    const baseline = archiveValues.get("profile-baseline.apk"); const protectedApk = archiveValues.get("profile-protected.apk");
    if (!baseline || !protectedApk) fail("real APK mutation inputs missing");
    const baselineLayout = archiveRecordOffsets(baseline); const protectedLayout = archiveRecordOffsets(protectedApk);
    const descriptor = baselineLayout.records.find((entry) => (entry.flags & 0x0008) !== 0);
    const direct = protectedLayout.records.find((entry) => (entry.flags & 0x0008) === 0);
    if (!descriptor || !direct || baseline.readUInt32LE(descriptor.dataEnd) !== 0x08074b50) fail("real APK mutation records differ");
    nestedCases.push(
      ["descriptor_signature", (() => { const value = Buffer.from(baseline); value[descriptor.dataEnd] ^= 1; return value; })()],
      ["descriptor_crc", (() => { const value = Buffer.from(baseline); value[descriptor.dataEnd + 4] ^= 1; return value; })()],
      ["descriptor_compressed_size", (() => { const value = Buffer.from(baseline); value[descriptor.dataEnd + 8] ^= 1; return value; })()],
      ["descriptor_uncompressed_size", (() => { const value = Buffer.from(baseline); value[descriptor.dataEnd + 12] ^= 1; return value; })()],
      ["local_crc", (() => { const value = Buffer.from(protectedApk); value[direct.local + 14] ^= 1; return value; })()],
      ["local_compressed_size", (() => { const value = Buffer.from(protectedApk); value[direct.local + 18] ^= 1; return value; })()],
      ["local_uncompressed_size", (() => { const value = Buffer.from(protectedApk); value[direct.local + 22] ^= 1; return value; })()],
      ["encrypted_flags", (() => { const value = Buffer.from(protectedApk); value.writeUInt16LE(direct.flags | 1, direct.central + 8); value.writeUInt16LE(direct.flags | 1, direct.local + 6); return value; })()],
      ["local_offset_bounds", (() => { const value = Buffer.from(protectedApk); value.writeUInt32LE(value.length, direct.central + 42); return value; })()],
      ["expanded_size", (() => { const value = Buffer.from(protectedApk); value.writeUInt32LE(33 * 1024 * 1024, direct.central + 24); value.writeUInt32LE(33 * 1024 * 1024, direct.local + 22); return value; })()],
      ["symlink_entry", (() => { const value = Buffer.from(protectedApk); value.writeUInt32LE(0xa0000000, direct.central + 38); return value; })()],
      ["duplicate_entry", makeZip([{ name: "assets/same.bin", data: Buffer.from("one") }, { name: "assets/same.bin", data: Buffer.from("two") }])],
      ["overlapping_local", (() => { const value = Buffer.from(protectedApk); const second = protectedLayout.records[1]; value.writeUInt32LE(protectedLayout.records[0].local, second.central + 42); return value; })()],
      ["signing_block_magic", (() => { const value = Buffer.from(protectedApk); const central = protectedLayout.eocd > 0 ? value.readUInt32LE(protectedLayout.eocd + 16) : 0; value[central - 1] ^= 1; return value; })()],
    );
  }
  for (const [name, bytes] of nestedCases) {
    let rejected = false; try { scanApkBytes(bytes, `self-test ${name}`); } catch { rejected = true; }
    if (!rejected) fail(`nested scan mutation accepted: ${name}`);
  }
  return { lockMutations: mutations.length + 1, archiveMutations, sensitiveMutations: sensitiveVectors.length + nestedCases.length };
}

function verifyDiff(baseRef) {
  if (!baseRef) return;
  const diff = spawnSync("git", ["diff", "--name-only", `${baseRef}...HEAD`], { cwd: root, encoding: "utf8", timeout: 30_000 });
  if (diff.status !== 0) fail("git diff failed");
  const forbidden = diff.stdout.split(/\r?\n/u).filter(Boolean).filter((file) =>
    /^(?:runtime|host|fixtures|benchmarks|distribution)\//u.test(file) ||
    file === ".github/workflows/m3-09-startup-attribution.yml" || file === ".github/workflows/m3-09-startup-attribution-evidence.yml");
  if (forbidden.length) fail(`product/workflow diff detected: ${forbidden.join(", ")}`);
}

function main() {
  const args = process.argv.slice(2); let archivePath; let baseRef; let selfTestRequested = false;
  for (let index = 0; index < args.length; index += 1) {
    if (args[index] === "--self-test") selfTestRequested = true;
    else if (args[index] === "--archive" && args[index + 1]) archivePath = args[++index];
    else if (args[index] === "--base-ref" && args[index + 1]) baseRef = args[++index];
    else fail(`unknown or incomplete argument ${args[index]}`);
  }
  const lock = validateLock(readJson(lockFile, "lock")); const metadata = readJson(metadataFile, "metadata");
  validateMetadata(metadata, lock); validateUpstreamObjects(lock); validateDocuments();
  let archiveBytes; let archiveResult;
  if (archivePath) {
    const resolved = path.resolve(archivePath); const buildRoot = path.resolve(root, "build", "m3-12");
    const rootStat = fs.lstatSync(buildRoot, { throwIfNoEntry: false });
    if (!rootStat?.isDirectory() || rootStat.isSymbolicLink()) fail("build/m3-12 must be a real directory");
    const buildReal = fs.realpathSync.native(buildRoot);
    if (!samePath(buildReal, buildRoot)) fail("build/m3-12 must not be a junction");
    const stat = fs.lstatSync(resolved, { throwIfNoEntry: false });
    if (!stat?.isFile() || stat.isSymbolicLink()) fail("archive must be a regular non-symlink file");
    const archiveReal = fs.realpathSync.native(resolved);
    if (!contained(buildReal, archiveReal)) fail("archive realpath must stay below build/m3-12");
    archiveBytes = fs.readFileSync(archiveReal); archiveResult = validateArchive(archiveBytes, lock);
  }
  verifyDiff(baseRef);
  const mutations = selfTestRequested ? selfTest(lock, metadata, archiveBytes, archiveResult?.values) : { lockMutations: 0, archiveMutations: 0 };
  process.stdout.write(`${JSON.stringify({ result: "PASS", releaseId: lock.source.releaseId, assetId: lock.source.assetId,
    archiveSha256: lock.archive.sha256, entryCount: lock.archive.entryCount, ...mutations }, null, 2)}\n`);
}

main();
