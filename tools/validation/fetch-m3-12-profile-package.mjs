#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const lock = JSON.parse(fs.readFileSync(path.join(root, "docs/evidence/M3-12/profile-package-retention-lock.json"), "utf8"));
const api = "https://api.github.com";

function fail(message) { throw new Error(`M3-12 asset fetch failed: ${message}`); }
function sha256(bytes) { return crypto.createHash("sha256").update(bytes).digest("hex"); }
function contained(parent, child) {
  const relative = path.relative(parent, child);
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative));
}
function headers(accept, token = true) {
  const result = { Accept: accept, "User-Agent": "androidAppHardening-m3-12", "X-GitHub-Api-Version": "2022-11-28" };
  if (token) result.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  return result;
}
async function json(url) {
  const response = await fetch(url, { headers: headers("application/vnd.github+json"), signal: AbortSignal.timeout(60_000) });
  if (!response.ok) fail(`metadata request returned ${response.status}`);
  return response.json();
}
function equal(actual, wanted, label) { if (actual !== wanted) fail(`${label} differs`); }

async function main() {
  const args = process.argv.slice(2); const outputIndex = args.indexOf("--output");
  if (outputIndex < 0 || !args[outputIndex + 1] || args.length !== 2) fail("exactly --output <new-file> is required");
  if (!process.env.GITHUB_TOKEN) fail("GITHUB_TOKEN is required");
  const output = path.resolve(args[outputIndex + 1]); const allowed = path.join(root, "build", "m3-12");
  if (!contained(allowed, output) || output === allowed || fs.existsSync(output)) fail("output must be a new file below build/m3-12");
  const release = await json(`${api}/repos/${lock.source.repository}/releases/${lock.source.releaseId}`);
  equal(release.id, lock.source.releaseId, "release id"); equal(release.tag_name, lock.source.tag, "release tag");
  equal(release.target_commitish, lock.source.targetCommitish, "release target"); equal(release.draft, false, "release draft");
  equal(release.prerelease, true, "release prerelease"); equal(release.immutable, false, "release immutable fact");
  if (!Array.isArray(release.assets) || release.assets.length !== 1 || release.assets[0].id !== lock.source.assetId) fail("release asset set differs");
  const metadata = await json(`${api}${lock.source.apiPath}`);
  for (const [key, wanted] of Object.entries({ id: lock.source.assetId, name: lock.source.assetName,
    label: lock.source.assetLabel, content_type: lock.source.contentType, state: lock.source.assetState,
    size: lock.archive.sizeBytes, digest: lock.archive.githubDigest, created_at: lock.source.createdAt,
    updated_at: lock.source.updatedAt })) equal(metadata[key], wanted, `asset ${key}`);
  const assetUrl = `${api}${lock.source.apiPath}`;
  let response = await fetch(assetUrl, { headers: headers(lock.retention.acceptHeader), redirect: "manual", signal: AbortSignal.timeout(60_000) });
  if ([301, 302, 303, 307, 308].includes(response.status)) {
    const location = response.headers.get("location");
    if (!location) fail("asset redirect is missing Location");
    const target = new URL(location);
    if (target.protocol !== "https:" || !(target.hostname === "github.com" || target.hostname.endsWith(".githubusercontent.com"))) {
      fail("asset redirect host is not GitHub-controlled");
    }
    response = await fetch(target, { headers: headers(lock.retention.acceptHeader, false), redirect: "error", signal: AbortSignal.timeout(60_000) });
  }
  if (!response.ok) fail(`asset download returned ${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  equal(bytes.length, lock.archive.sizeBytes, "downloaded size"); equal(sha256(bytes), lock.archive.sha256, "downloaded SHA-256");
  fs.mkdirSync(path.dirname(output), { recursive: true }); fs.writeFileSync(output, bytes, { flag: "wx" });
  process.stdout.write(`${JSON.stringify({ result: "PASS", releaseId: lock.source.releaseId, assetId: lock.source.assetId,
    sizeBytes: bytes.length, sha256: sha256(bytes) }, null, 2)}\n`);
}

await main();
