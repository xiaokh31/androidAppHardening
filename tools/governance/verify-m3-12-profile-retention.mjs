#!/usr/bin/env node

import crypto from "node:crypto";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { inflateRawSync } from "node:zlib";

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
function readJson(file, label) {
  try { return JSON.parse(fs.readFileSync(file, "utf8")); } catch { fail(`${label} is missing or invalid JSON`); }
}

function validateLock(lock) {
  exactKeys(lock, ["schemaVersion", "taskId", "packageId", "source", "archive", "retention"], "lock");
  equal(lock.schemaVersion, 1, "schemaVersion"); equal(lock.taskId, "M3-12", "taskId");
  equal(lock.packageId, "m3-10-profile-package-v1", "packageId");
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
    for (const marker of SENSITIVE) if (data.toString("latin1").includes(marker)) fail(`sensitive marker found in ${name}`);
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
  return { entries: values.size };
}

function validateDocuments() {
  const files = [
    ["docs/adr/0017-profile-package-retention-boundary.md", ["374769776", "524507375", EXPECTED.sha256, "immutable=false", "contents: read"]],
    ["docs/adr/0016-end-to-end-startup-attribution-boundary.md", ["M3-12", "524507375", EXPECTED.sha256]],
    ["docs/tasks/M3-12-profile-package-retention.md", ["Issue #75", "P0=0/P1=0/P2=0", "no profile regeneration"]],
    ["docs/tasks/M3-10-startup-attribution-diagnostic.md", ["M3-12", "numeric asset ID"]],
    ["docs/evidence/M3-12/provenance.md", ["byte_equal=true", "immutable=false", "no Android environment ran"]],
  ];
  for (const [relative, phrases] of files) {
    const text = fs.readFileSync(path.join(root, relative), "utf8");
    for (const phrase of phrases) if (!text.includes(phrase)) fail(`${relative} missing ${phrase}`);
  }
}

function selfTest(lock, metadata, archiveBytes) {
  const mutations = [
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
    const cases = [
      ["trailing", Buffer.concat([archiveBytes, Buffer.from([0])])],
      ["truncated", archiveBytes.subarray(0, archiveBytes.length - 1)],
      ["byte_flip", (() => { const value = Buffer.from(archiveBytes); value[Math.floor(value.length / 3)] ^= 1; return value; })()],
    ];
    for (const [name, bytes] of cases) {
      const changedLock = clone(lock); changedLock.archive.sizeBytes = bytes.length; changedLock.archive.sha256 = sha256(bytes);
      let rejected = false; try { validateArchive(bytes, changedLock); } catch { rejected = true; }
      if (!rejected) fail(`archive mutation accepted: ${name}`);
      archiveMutations += 1;
    }
  }
  return { lockMutations: mutations.length + 1, archiveMutations };
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
  validateMetadata(metadata, lock); validateDocuments();
  let archiveBytes;
  if (archivePath) {
    const resolved = path.resolve(archivePath); const buildRoot = path.join(root, "build", "m3-12");
    const relative = path.relative(buildRoot, resolved);
    if (relative.startsWith(`..${path.sep}`) || relative === ".." || path.isAbsolute(relative)) fail("archive must be below build/m3-12");
    const stat = fs.lstatSync(resolved, { throwIfNoEntry: false });
    if (!stat?.isFile() || stat.isSymbolicLink()) fail("archive must be a regular non-symlink file");
    archiveBytes = fs.readFileSync(resolved); validateArchive(archiveBytes, lock);
  }
  verifyDiff(baseRef);
  const mutations = selfTestRequested ? selfTest(lock, metadata, archiveBytes) : { lockMutations: 0, archiveMutations: 0 };
  process.stdout.write(`${JSON.stringify({ result: "PASS", releaseId: lock.source.releaseId, assetId: lock.source.assetId,
    archiveSha256: lock.archive.sha256, entryCount: lock.archive.entryCount, ...mutations }, null, 2)}\n`);
}

main();
