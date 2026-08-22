#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { inflateRawSync } from "node:zlib";

const REPOSITORY = "xiaokh31/androidAppHardening";
const TASK_KEY = "M3-09-DIAGNOSTIC-V1";
const PRODUCT_TUPLE = "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd";
const WORKFLOW_PATH = ".github/workflows/m3-09-startup-attribution.yml";
const ARTIFACT_NAME = "m3-09-startup-attribution-raw";
const DIAGNOSTIC_BRANCH = "feat/m3-10-startup-attribution-diagnostic";
const MAX_PAGE_BYTES = 4 * 1024 * 1024;
const MAX_ARTIFACT_BYTES = 64 * 1024 * 1024;
const MAX_ENTRY_BYTES = 16 * 1024 * 1024;

function fail(message) { throw new Error(`M3-10 evidence collection failed: ${message}`); }
function sha256(bytes) { return crypto.createHash("sha256").update(bytes).digest("hex"); }
function optionsOf(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) fail("options must be --name value pairs");
    result[values[index].slice(2)] = values[index + 1];
  }
  return result;
}
function required(options, name) { if (!options[name]) fail(`--${name} is required`); return options[name]; }
function exactKeys(value, keys, label) {
  if (!value || typeof value !== "object" || Array.isArray(value) ||
      JSON.stringify(Object.keys(value).sort()) !== JSON.stringify([...keys].sort())) fail(`${label} keys differ`);
}
function safeNewPath(value, label) {
  const root = fs.realpathSync.native(process.cwd());
  const build = path.join(root, "build", "m3-10");
  fs.mkdirSync(build, { recursive: true });
  const resolved = path.resolve(value);
  if (!(resolved + path.sep).startsWith(build + path.sep) || fs.existsSync(resolved)) fail(`${label} must be new under build/m3-10`);
  let cursor = path.dirname(resolved);
  while (cursor.startsWith(build)) {
    if (fs.existsSync(cursor) && fs.lstatSync(cursor).isSymbolicLink()) fail(`${label} parent is a symbolic link`);
    if (cursor === build) break;
    cursor = path.dirname(cursor);
  }
  return resolved;
}
function allowedArtifactRedirect(value) {
  const target = new URL(value);
  return target.protocol === "https:" && (target.hostname === "github.com" ||
    target.hostname.endsWith(".githubusercontent.com") ||
    /^productionresultssa[0-9]+\.blob\.core\.windows\.net$/u.test(target.hostname));
}
function archiveSize(total, compressedSize, size, label) {
  if (!Number.isSafeInteger(compressedSize) || !Number.isSafeInteger(size) || compressedSize < 0 || size < 0 ||
      compressedSize > MAX_ENTRY_BYTES || size > MAX_ENTRY_BYTES || total + size > MAX_ARTIFACT_BYTES) {
    fail(`${label} exceeds fixed archive bounds`);
  }
  return total + size;
}
async function boundedBody(response, maximum, label) {
  const declared = response.headers.get("content-length");
  if (declared !== null && (!/^[0-9]+$/u.test(declared) || Number(declared) > maximum)) fail(`${label} length differs`);
  if (!response.body) fail(`${label} body is missing`);
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    if (total > maximum) {
      await reader.cancel();
      fail(`${label} exceeds fixed download bound`);
    }
    chunks.push(Buffer.from(value));
  }
  return Buffer.concat(chunks, total);
}
function findEocd(bytes) {
  for (let offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 65_557); offset--) {
    if (bytes.readUInt32LE(offset) === 0x06054b50 && offset + 22 + bytes.readUInt16LE(offset + 20) === bytes.length) return offset;
  }
  fail("artifact ZIP EOCD is missing");
}
function readZip(bytes) {
  const eocd = findEocd(bytes);
  const count = bytes.readUInt16LE(eocd + 10);
  const centralSize = bytes.readUInt32LE(eocd + 12);
  const centralOffset = bytes.readUInt32LE(eocd + 16);
  if (count === 0xffff || centralSize === 0xffffffff || centralOffset === 0xffffffff || centralOffset + centralSize > eocd) {
    fail("artifact ZIP directory differs");
  }
  const entries = new Map();
  let cursor = centralOffset;
  let totalSize = 0;
  for (let index = 0; index < count; index++) {
    if (cursor + 46 > eocd || bytes.readUInt32LE(cursor) !== 0x02014b50) fail("artifact ZIP central entry differs");
    const flags = bytes.readUInt16LE(cursor + 8);
    const method = bytes.readUInt16LE(cursor + 10);
    const compressedSize = bytes.readUInt32LE(cursor + 20);
    const size = bytes.readUInt32LE(cursor + 24);
    totalSize = archiveSize(totalSize, compressedSize, size, `artifact ZIP entry ${index}`);
    const nameLength = bytes.readUInt16LE(cursor + 28);
    const extraLength = bytes.readUInt16LE(cursor + 30);
    const commentLength = bytes.readUInt16LE(cursor + 32);
    const localOffset = bytes.readUInt32LE(cursor + 42);
    const name = bytes.subarray(cursor + 46, cursor + 46 + nameLength).toString("utf8");
    if ((flags & 1) !== 0 || ![0, 8].includes(method) || !name || name.includes("/") || name.includes("\\") ||
        entries.has(name) || localOffset + 30 > centralOffset || bytes.readUInt32LE(localOffset) !== 0x04034b50) {
      fail(`artifact ZIP entry differs: ${name}`);
    }
    const localNameLength = bytes.readUInt16LE(localOffset + 26);
    const localExtraLength = bytes.readUInt16LE(localOffset + 28);
    const start = localOffset + 30 + localNameLength + localExtraLength;
    const end = start + compressedSize;
    if (end > centralOffset) fail(`artifact ZIP entry escapes payload: ${name}`);
    const compressed = bytes.subarray(start, end);
    const content = method === 0 ? Buffer.from(compressed) : inflateRawSync(compressed, { maxOutputLength: MAX_ENTRY_BYTES });
    if (content.length !== size) fail(`artifact ZIP entry size differs: ${name}`);
    entries.set(name, content);
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  if (cursor !== centralOffset + centralSize) fail("artifact ZIP central size differs");
  return entries;
}
function headers(accept, token = true) {
  const value = { Accept: accept, "User-Agent": "androidAppHardening-M3-10-evidence", "X-GitHub-Api-Version": "2022-11-28" };
  if (token) value.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  return value;
}
async function fetchPage(url) {
  const response = await fetch(url, { headers: headers("application/vnd.github+json"), signal: AbortSignal.timeout(60_000) });
  if (!response.ok) fail(`GitHub API returned ${response.status}`);
  const link = response.headers.get("link") ?? "";
  if (/rel="next"/.test(link)) fail("GitHub API pagination has a next page");
  return boundedBody(response, MAX_PAGE_BYTES, "GitHub API page");
}
async function fetchArtifact(assetId) {
  const url = `https://api.github.com/repos/${REPOSITORY}/actions/artifacts/${assetId}/zip`;
  let response = await fetch(url, { headers: headers("application/vnd.github+json"), redirect: "manual", signal: AbortSignal.timeout(60_000) });
  if ([301, 302, 303, 307, 308].includes(response.status)) {
    const location = response.headers.get("location");
    if (!location) fail("artifact redirect is missing");
    if (!allowedArtifactRedirect(location)) fail("artifact redirect is not GitHub-controlled");
    const target = new URL(location);
    response = await fetch(target, { headers: headers("application/octet-stream", false), redirect: "error", signal: AbortSignal.timeout(60_000) });
  }
  if (!response.ok) fail(`artifact download returned ${response.status}`);
  return boundedBody(response, MAX_ARTIFACT_BYTES, "artifact download");
}

function selfTest() {
  if (!allowedArtifactRedirect("https://productionresultssa5.blob.core.windows.net/actions-results/example.zip") ||
      !allowedArtifactRedirect("https://objects.githubusercontent.com/example.zip")) fail("artifact redirect positive case differs");
  for (const value of ["http://productionresultssa5.blob.core.windows.net/example.zip",
    "https://productionresultssa5.blob.core.windows.net.evil.invalid/example.zip",
    "https://other.blob.core.windows.net/example.zip"]) {
    if (allowedArtifactRedirect(value)) fail("artifact redirect negative case was accepted");
  }
  try { archiveSize(0, 1, MAX_ENTRY_BYTES + 1, "self-test"); } catch { return { redirects: 5, archiveBounds: 1 }; }
  fail("oversized archive entry was accepted");
}

async function main() {
  if (!process.env.GITHUB_TOKEN || process.env.GITHUB_REPOSITORY !== REPOSITORY) fail("official GitHub identity is unavailable");
  const options = optionsOf(process.argv.slice(2));
  const requestPath = path.resolve(required(options, "request"));
  if (requestPath !== path.resolve("docs/evidence/M3-10/diagnostic-terminal-request.json")) fail("request path differs");
  const request = JSON.parse(fs.readFileSync(requestPath, "utf8"));
  exactKeys(request, ["schemaVersion", "taskKey", "productTuple", "diagnosticHeadSha", "diagnosticRunId"], "request");
  if (request.schemaVersion !== 1 || request.taskKey !== TASK_KEY || request.productTuple !== PRODUCT_TUPLE ||
      !/^[0-9a-f]{40}$/.test(request.diagnosticHeadSha) || !/^[1-9][0-9]*$/.test(String(request.diagnosticRunId))) {
    fail("request identity differs");
  }
  const evidenceRoot = safeNewPath(required(options, "output"), "evidence output");
  const resultOutput = safeNewPath(required(options, "result-output"), "result output");
  fs.mkdirSync(evidenceRoot);
  try {
    const api = `https://api.github.com/repos/${REPOSITORY}`;
    const runs = await fetchPage(`${api}/actions/runs?branch=${encodeURIComponent(DIAGNOSTIC_BRANCH)}&event=push&per_page=100&page=1`);
    const jobs = await fetchPage(`${api}/actions/runs/${request.diagnosticRunId}/jobs?per_page=100&page=1`);
    const artifacts = await fetchPage(`${api}/actions/runs/${request.diagnosticRunId}/artifacts?per_page=100&page=1`);
    const artifactsJson = JSON.parse(artifacts.toString("utf8"));
    const matches = Array.isArray(artifactsJson.artifacts) ? artifactsJson.artifacts.filter((item) =>
      item.name === ARTIFACT_NAME && String(item.workflow_run?.id) === String(request.diagnosticRunId)) : [];
    if (matches.length !== 1 || matches[0].expired !== false || !Number.isSafeInteger(matches[0].id) ||
        !Number.isSafeInteger(matches[0].size_in_bytes) || matches[0].size_in_bytes < 1 ||
        matches[0].size_in_bytes > MAX_ARTIFACT_BYTES) fail("terminal artifact selection differs");
    const artifact = await fetchArtifact(matches[0].id);
    const entries = readZip(artifact);
    if (!entries.has("ledger.json") || !entries.has("result.json")) fail("artifact ledger/result is missing");
    const ledger = JSON.parse(entries.get("ledger.json").toString("utf8"));
    if (ledger.headSha !== request.diagnosticHeadSha || ledger.productTuple !== request.productTuple ||
        ledger.taskKey !== request.taskKey) fail("artifact ledger differs from the committed request");
    const pageFiles = {
      "runs-page-1.json": runs,
      "jobs-page-1.json": jobs,
      "artifacts-page-1.json": artifacts,
    };
    for (const [name, bytes] of Object.entries(pageFiles)) fs.writeFileSync(path.join(evidenceRoot, name), bytes, { flag: "wx" });
    fs.writeFileSync(path.join(evidenceRoot, "artifact.zip"), artifact, { flag: "wx" });
    fs.writeFileSync(path.join(evidenceRoot, "ledger.json"), entries.get("ledger.json"), { flag: "wx" });
    fs.writeFileSync(resultOutput, entries.get("result.json"), { flag: "wx" });
    const manifest = {
      schemaVersion: 1,
      pages: Object.fromEntries(Object.entries(pageFiles).map(([name, bytes]) => [name, {
        sha256: sha256(bytes), sizeBytes: bytes.length, page: 1, perPage: 100, nextPageAbsent: true,
      }])),
      artifact: { sha256: sha256(artifact), sizeBytes: artifact.length },
    };
    fs.writeFileSync(path.join(evidenceRoot, "page-manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`, { flag: "wx" });
    process.stdout.write(`${JSON.stringify({ status: "PASS", runId: String(request.diagnosticRunId), artifactId: String(matches[0].id), artifactSha256: sha256(artifact) })}\n`);
  } catch (error) {
    fs.rmSync(evidenceRoot, { recursive: true, force: true });
    fs.rmSync(resultOutput, { force: true });
    throw error;
  }
}

if (process.argv.includes("--self-test")) process.stdout.write(`${JSON.stringify({ status: "PASS", ...selfTest() })}\n`);
else await main();
