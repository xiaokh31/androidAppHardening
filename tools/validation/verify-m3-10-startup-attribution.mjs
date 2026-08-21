#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import {
  existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, readdirSync,
  rmSync, statSync, writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { inflateRawSync } from "node:zlib";
import {
  centralRecord, isSignatureEntry, localRecord, normalizedEntry, readEntries as readMutableEntries,
} from "./create-m0-04-tampered-apks.mjs";

const TASK_KEY = "M3-09-DIAGNOSTIC-V1";
const ENVIRONMENT = "api36-r2-x86_64-emulator-37.1.11";
const OWNERS = [
  "RUNTIME_BOOTSTRAP",
  "PRE_APPLICATION_RESIDUAL",
  "P1_P2", "P2_P3", "P3_P4", "P4_P5", "P5_P6", "P6_P7", "P7_P8",
];
const PACKAGE_FILES = [
  "artifact-manifest.json", "campaign-a.json", "campaign-b.json", "cleanup.json",
  "cli.zip", "derivation-manifest.json", "distribution.zip", "original-baseline.apk",
  "original-protected.apk", "observer.dex", "probe-manifest.json", "profile-baseline.apk",
  "profile-baseline-aligned.apk", "profile-baseline-unsigned.apk", "profile-lock.json",
  "profile-protected.apk", "profile-protected-aligned.apk", "profile-protected-unsigned.apk",
  "profile-verification.json", "release-bootstrap.aar",
  "release-fixture.apk", "release-native.aar", "release-policy.aar", "result.json",
].sort();
const MANIFESTED_FILES = PACKAGE_FILES.filter((name) => name !== "artifact-manifest.json");
const OBSERVER = "M310StartupTimingObserver";
const RECEIVER = "M310SnapshotReceiver";
const MAX_PROTECTED_PROBES = 24;
const MAX_OVERHEAD_NS = 5_000_000;
const APP_BUDGET_NS = 300_000_000;
// These are the exact common lifecycle events emitted by the canonical java-single-dex
// fixture. Signer/Guard/container semantics are independently recomputed from the APKs;
// they are deliberately not fabricated as device events.
const EXPECTED_EVENTS = ["provider.ready", "activity.create"];

function fail(message) {
  throw new Error(`M3-10 validation failed: ${message}`);
}

function optionsOf(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 2) {
    if (!values[index]?.startsWith("--") || values[index + 1] === undefined) {
      fail("options must be --name value pairs");
    }
    result[values[index].slice(2)] = values[index + 1];
  }
  return result;
}

function required(options, name) {
  const value = options[name];
  if (!value) fail(`--${name} is required`);
  return value;
}

function json(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    fail(`${path.basename(file)} is not canonical JSON: ${error.message}`);
  }
}

function sha256Bytes(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function sha256File(file) {
  return sha256Bytes(readFileSync(file));
}

function exactKeys(value, keys, label) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) fail(`${label} must be an object`);
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    fail(`${label} keys differ: ${actual.join(",")}`);
  }
}

function integer(value, label, minimum = Number.MIN_SAFE_INTEGER) {
  if (!Number.isSafeInteger(value) || value < minimum) fail(`${label} is not a safe integer`);
  return value;
}

function nearestRank(values, percentile) {
  if (!Array.isArray(values) || values.length === 0) fail("nearest-rank input is empty");
  const ordered = values.map((value, index) => integer(value, `rank[${index}]`)).sort((a, b) => a - b);
  return ordered[Math.ceil(percentile * ordered.length) - 1];
}

function variation(left, right) {
  return Math.abs(left - right) / Math.max(1, Math.min(Math.abs(left), Math.abs(right)));
}

function sensitiveScan(value, label) {
  const text = typeof value === "string" ? value : JSON.stringify(value);
  const patterns = [
    /[A-Za-z]:\\(?:Users|works)\\/i,
    /\/(?:home|Users|data|sdcard|storage|proc|apex|system|vendor|product|mnt)\//,
    /-----BEGIN (?:PRIVATE KEY|CERTIFICATE)-----/,
    /(?:storePassword|keyPassword|keystore|deviceSerial|authorization: bearer)/i,
    /dex\\n03[5-9]\\0/,
    /(?:cek|r_java|r_native)[=:][0-9a-f]{16,}/i,
  ];
  if (patterns.some((pattern) => pattern.test(text))) fail(`${label} contains sensitive material or a host/device path`);
}

function findEocd(bytes) {
  const lower = Math.max(0, bytes.length - 65_557);
  for (let offset = bytes.length - 22; offset >= lower; offset--) {
    if (bytes.readUInt32LE(offset) === 0x06054b50) return offset;
  }
  fail("ZIP EOCD is missing");
}

export function readZip(file) {
  const bytes = readFileSync(file);
  const eocd = findEocd(bytes);
  const entryCount = bytes.readUInt16LE(eocd + 10);
  const centralSize = bytes.readUInt32LE(eocd + 12);
  const centralOffset = bytes.readUInt32LE(eocd + 16);
  if (entryCount === 0xffff || centralOffset === 0xffffffff || centralSize === 0xffffffff) {
    fail(`${path.basename(file)} uses unsupported ZIP64`);
  }
  if (centralOffset + centralSize > eocd) fail(`${path.basename(file)} central directory escapes file`);
  const entries = new Map();
  let cursor = centralOffset;
  for (let index = 0; index < entryCount; index++) {
    if (bytes.readUInt32LE(cursor) !== 0x02014b50) fail(`${path.basename(file)} central entry ${index} is malformed`);
    const flags = bytes.readUInt16LE(cursor + 8);
    const method = bytes.readUInt16LE(cursor + 10);
    const compressedSize = bytes.readUInt32LE(cursor + 20);
    const size = bytes.readUInt32LE(cursor + 24);
    const nameLength = bytes.readUInt16LE(cursor + 28);
    const extraLength = bytes.readUInt16LE(cursor + 30);
    const commentLength = bytes.readUInt16LE(cursor + 32);
    const localOffset = bytes.readUInt32LE(cursor + 42);
    if ((flags & 1) !== 0 || ![0, 8].includes(method)) fail(`${path.basename(file)} has encrypted/unsupported ZIP entry`);
    const name = bytes.subarray(cursor + 46, cursor + 46 + nameLength).toString("utf8");
    if (!name || name.includes("\\") || name.startsWith("/") || name.split("/").includes("..") || entries.has(name)) {
      fail(`${path.basename(file)} has unsafe or duplicate ZIP entry ${name}`);
    }
    if (bytes.readUInt32LE(localOffset) !== 0x04034b50) fail(`${path.basename(file)} local header differs`);
    const localName = bytes.readUInt16LE(localOffset + 26);
    const localExtra = bytes.readUInt16LE(localOffset + 28);
    const start = localOffset + 30 + localName + localExtra;
    const end = start + compressedSize;
    if (end > centralOffset) fail(`${path.basename(file)} entry ${name} escapes payload`);
    const compressed = bytes.subarray(start, end);
    const content = method === 0 ? Buffer.from(compressed) : inflateRawSync(compressed);
    if (content.length !== size) fail(`${path.basename(file)} entry ${name} size differs`);
    entries.set(name, content);
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  if (cursor !== centralOffset + centralSize) fail(`${path.basename(file)} central directory size differs`);
  return entries;
}

function recursiveArchiveContains(file, needle) {
  const bytes = readFileSync(file);
  if (bytes.includes(Buffer.from(needle, "utf8"))) return true;
  let entries;
  try {
    entries = readZip(file);
  } catch {
    return false;
  }
  for (const [name, content] of entries) {
    if (content.includes(Buffer.from(needle, "utf8"))) return true;
    if (/\.(?:jar|aar|apk|zip)$/i.test(name)) {
      const scratch = mkdtempSync(path.join(tmpdir(), "m310-nested-"));
      const nested = path.join(scratch, path.basename(name));
      try {
        writeFileSync(nested, content);
        if (recursiveArchiveContains(nested, needle)) return true;
      } finally {
        rmSync(scratch, { recursive: true, force: true });
      }
    }
  }
  return false;
}

function recursiveArchiveContainsAny(file, needles) {
  return needles.some((needle) => recursiveArchiveContains(file, needle));
}

function replaceZipEntry(source, target, mutate) {
  const entries = readMutableEntries(readFileSync(source));
  if (entries.filter((entry) => entry.name === target).length !== 1) fail(`mutation target differs: ${target}`);
  const local = [];
  const central = [];
  let offset = 0;
  for (const sourceEntry of entries) {
    const replacement = sourceEntry.name === target ? mutate(Buffer.from(readZip(source).get(target))) : undefined;
    const entry = normalizedEntry(sourceEntry, replacement);
    const localBytes = localRecord(entry);
    local.push(localBytes);
    central.push(centralRecord(entry, offset));
    offset += localBytes.length;
  }
  const directory = Buffer.concat(central);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(local.length, 8);
  eocd.writeUInt16LE(local.length, 10);
  eocd.writeUInt32LE(directory.length, 12);
  eocd.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, directory, eocd]);
}

function requireRegularRoot(root, names) {
  if (!existsSync(root) || !statSync(root).isDirectory()) fail("artifact root is missing");
  const actual = readdirSync(root).sort();
  if (JSON.stringify(actual) !== JSON.stringify([...names].sort())) {
    fail(`artifact root differs: ${actual.join(",")}`);
  }
  for (const name of actual) {
    const info = lstatSync(path.join(root, name));
    if (!info.isFile() || info.isSymbolicLink()) fail(`${name} is not a regular file`);
  }
}

function identityOf(value, label) {
  const keys = ["headSha", "runId", "jobId", "runAttempt", "environmentId", "bootIdHashPrefix", "taskKey", "productTuple"];
  const result = Object.fromEntries(keys.map((key) => [key, value[key]]));
  if (!/^[0-9a-f]{40}$/.test(result.headSha ?? "")) fail(`${label}.headSha differs`);
  if (!/^[1-9][0-9]*$/.test(String(result.runId)) || !/^[1-9][0-9]*$/.test(String(result.jobId))) {
    fail(`${label} run/job identity differs`);
  }
  if (result.runAttempt !== 1 || result.environmentId !== ENVIRONMENT || result.taskKey !== TASK_KEY) {
    fail(`${label} fixed identity differs`);
  }
  if (!/^[0-9a-f]{12}$/.test(result.bootIdHashPrefix ?? "") ||
      !/^[0-9a-f]{64}$/.test(result.productTuple ?? "")) fail(`${label} boot/product identity differs`);
  return result;
}

function sameIdentity(left, right, label) {
  if (JSON.stringify(left) !== JSON.stringify(right)) fail(`${label} identity differs`);
}

function validateObservation(value, protectedPath, ordinal, label) {
  exactKeys(value, ["ordinal", "outerNs", "innerNs", "events"], label);
  if (value.ordinal !== ordinal) fail(`${label}.ordinal differs`);
  if (!Array.isArray(value.outerNs) || value.outerNs.length !== 16) fail(`${label}.outerNs differs`);
  value.outerNs.forEach((point, index) => {
    integer(point, `${label}.outerNs[${index}]`, 1);
    if (index > 0 && point < value.outerNs[index - 1]) fail(`${label}.outerNs is non-monotonic`);
  });
  if (!Array.isArray(value.events) || JSON.stringify(value.events) !== JSON.stringify(EXPECTED_EVENTS)) {
    fail(`${label}.events differs`);
  }
  if (protectedPath) {
    if (!Array.isArray(value.innerNs) || value.innerNs.length !== 9) fail(`${label}.innerNs differs`);
    value.innerNs.forEach((point, index) => {
      integer(point, `${label}.innerNs[${index}]`, 1);
      if (index > 0 && point < value.innerNs[index - 1]) fail(`${label}.innerNs is non-monotonic`);
    });
    if (value.outerNs[0] > value.innerNs[0] || value.innerNs[8] > value.outerNs[1]) {
      fail(`${label} inner chain escapes p0..p1`);
    }
  } else if (value.innerNs !== null) {
    fail(`${label}.innerNs must be null`);
  }
}

function ownerVector(baseline, protectedSample, label) {
  const b = baseline.outerNs;
  const p = protectedSample.outerNs;
  const h = protectedSample.innerNs;
  const vector = {
    RUNTIME_BOOTSTRAP: h[8] - h[0],
    PRE_APPLICATION_RESIDUAL: (h[0] - p[0]) + (p[1] - h[8]) - (b[1] - b[0]),
  };
  for (let index = 1; index <= 7; index++) {
    vector[`P${index}_P${index + 1}`] = (p[index + 1] - p[index]) - (b[index + 1] - b[index]);
  }
  const total = (p[8] - p[0]) - (b[8] - b[0]);
  const sum = Object.values(vector).reduce((value, part) => value + part, 0);
  if (sum !== total) fail(`${label} owner vector does not reconcile: ${sum} != ${total}`);
  return { total, vector };
}

function validateCampaign(value, expectedName, expectedOrder, identity) {
  exactKeys(value, [
    "schemaVersion", "campaign", "order", "warmups", "samples", "calibrationNs",
    "maximumProtectedProbeCount", "identity",
  ], `campaign-${expectedName}`);
  if (value.schemaVersion !== 1 || value.campaign !== expectedName ||
      JSON.stringify(value.order) !== JSON.stringify(expectedOrder)) fail(`campaign ${expectedName} order differs`);
  sameIdentity(identityOf(value.identity, `campaign-${expectedName}.identity`), identity, `campaign-${expectedName}`);
  for (const kind of ["warmups", "samples"]) {
    exactKeys(value[kind], ["baseline", "protected"], `campaign-${expectedName}.${kind}`);
    const count = kind === "warmups" ? 5 : 15;
    for (const mode of ["baseline", "protected"]) {
      if (!Array.isArray(value[kind][mode]) || value[kind][mode].length !== count) {
        fail(`campaign ${expectedName} ${kind} ${mode} count differs`);
      }
      value[kind][mode].forEach((sample, index) =>
        validateObservation(sample, mode === "protected", index + 1, `${expectedName}.${kind}.${mode}[${index}]`));
    }
  }
  if (!Array.isArray(value.calibrationNs) || value.calibrationNs.length !== 15) {
    fail(`campaign ${expectedName} calibration count differs`);
  }
  value.calibrationNs.forEach((sample, index) => integer(sample, `${expectedName}.calibrationNs[${index}]`, 0));
  if (value.maximumProtectedProbeCount !== MAX_PROTECTED_PROBES) fail(`campaign ${expectedName} probe count differs`);
  const calibrationP95 = nearestRank(value.calibrationNs, 0.95);
  if (calibrationP95 * MAX_PROTECTED_PROBES > MAX_OVERHEAD_NS) {
    fail(`campaign ${expectedName} calibrated overhead exceeds 5 ms`);
  }
  const totals = [];
  const byOwner = Object.fromEntries(OWNERS.map((owner) => [owner, []]));
  for (let index = 0; index < 15; index++) {
    const baseline = value.samples.baseline[index];
    const protectedSample = value.samples.protected[index];
    if (JSON.stringify(baseline.events) !== JSON.stringify(protectedSample.events)) {
      fail(`campaign ${expectedName} ordinal ${index + 1} security/lifecycle events differ`);
    }
    const computed = ownerVector(baseline, protectedSample, `${expectedName} ordinal ${index + 1}`);
    totals.push(computed.total);
    for (const owner of OWNERS) byOwner[owner].push(computed.vector[owner]);
  }
  return {
    calibrationP95Ns: calibrationP95,
    totalP50Ns: nearestRank(totals, 0.5),
    ownerP50Ns: Object.fromEntries(OWNERS.map((owner) => [owner, nearestRank(byOwner[owner], 0.5)])),
  };
}

function computeResult(campaignA, campaignB) {
  const eligibleOwners = OWNERS.filter((owner) => {
    const a = campaignA.ownerP50Ns[owner];
    const b = campaignB.ownerP50Ns[owner];
    return campaignA.totalP50Ns > APP_BUDGET_NS && campaignB.totalP50Ns > APP_BUDGET_NS &&
      a >= 30_000_000 && b >= 30_000_000 && a > 0 && b > 0 &&
      variation(a, b) <= 0.10 &&
      a / campaignA.totalP50Ns >= 0.50 && b / campaignB.totalP50Ns >= 0.50;
  });
  return {
    status: eligibleOwners.length === 1 ? "ELIGIBLE" : "UNATTRIBUTED",
    selectedOwner: eligibleOwners.length === 1 ? eligibleOwners[0] : null,
    eligibleOwners,
    campaigns: { A: campaignA, B: campaignB },
  };
}

function runDexdump(executable, apk, scratch, label) {
  const entries = readZip(apk);
  const dex = [...entries].filter(([name]) => /^classes(?:[2-9][0-9]*)?\.dex$/.test(name));
  if (dex.length === 0) fail(`${label} contains no DEX`);
  const methods = new Map();
  for (const [name, bytes] of dex) {
    const file = path.join(scratch, `${label}-${name}`);
    writeFileSync(file, bytes);
    const result = spawnSync(executable, ["-d", file], {
      encoding: "utf8", windowsHide: true, timeout: 60_000, maxBuffer: 64 * 1024 * 1024,
    });
    if (result.error || result.status !== 0) fail(`dexdump failed for ${label}/${name}`);
    let current = null;
    let pending = null;
    for (const raw of result.stdout.replace(/\r/g, "").split("\n")) {
      const header = raw.match(/^\s*[0-9a-f]+:\s+\|\[[0-9a-f]+\]\s+([^:]+):(.+)$/i);
      if (header) {
        if (current !== null && pending !== null) methods.get(current).push(pending);
        current = `${header[1]}:${header[2]}`;
        if (!methods.has(current)) methods.set(current, []);
        pending = null;
        continue;
      }
      const instruction = raw.match(/^\s*[0-9a-f]+:\s+[0-9a-f ]+\|([0-9a-f]+):\s+(.+)$/i);
      if (!instruction || current === null) continue;
      const address = Number.parseInt(instruction[1], 16);
      let text = instruction[2]
        .replace(/\s+\/\/\s+(?:method|string|type|field)@[0-9a-f]+/gi, "")
        .replace(/,\s*[0-9a-f]{4}(?=\s+\/\/)/gi, ", @")
        .replace(/\s+\/\/\s+[+-][0-9a-f]+$/i, "")
        .trim();
      const observerCall = text.includes(`/${OBSERVER};.`) && /\.(?:p(?:[1-9]|1[0-5])|h[0-8])(?::|\()/u.test(text);
      if (observerCall) {
        continue;
      }
      if (/^const(?:\/\w+)?\s/.test(text)) {
        if (pending !== null) methods.get(current).push(pending);
        pending = { address, text };
        continue;
      }
      if (pending !== null) {
        methods.get(current).push(pending);
        pending = null;
      }
      methods.get(current).push({ address, text });
    }
    if (current !== null && pending !== null) methods.get(current).push(pending);
  }
  const ignoredClasses = [
    `ah.runtime.profile.${OBSERVER}`,
  ];
  for (const key of [...methods.keys()]) {
    if (ignoredClasses.some((owner) => key.startsWith(owner + ".")) ||
        key.startsWith("ah.fixtures.android.m301.BenchmarkFixtureApplication.attachBaseContext:") ||
        key.startsWith("ah.fixtures.android.m301.FixtureActivity.onResume:")) {
      methods.delete(key);
    }
  }
  return Object.fromEntries([...methods].sort(([a], [b]) => a.localeCompare(b)).map(([key, instructions]) => {
    const addressToIndex = new Map(instructions.map((instruction, index) => [instruction.address, index]));
    const registers = new Map();
    const canonical = instructions.map((instruction) => {
      let text = instruction.text;
      if (/^(?:goto(?:\/\d+)?|if-\S+|packed-switch|sparse-switch|fill-array-data)\s/.test(text)) {
        text = text.replace(/([,\s])([0-9a-f]{4})(?=\s*$)/i, (match, separator, target) => {
          const targetAddress = Number.parseInt(target, 16);
          const targetIndex = addressToIndex.get(targetAddress) ??
            instructions.findIndex((candidate) => candidate.address > targetAddress);
          if (targetIndex < 0) fail(`${key} branch target does not resolve after probe removal`);
          return `${separator}@${targetIndex}`;
        });
      }
      return text.replace(/\b([vp]\d+)\b/g, (register) => {
        if (!registers.has(register)) registers.set(register, `r${registers.size}`);
        return registers.get(register);
      });
    });
    return [key, canonical];
  }));
}

function comparePair(original, profile, role, dexdump, scratch) {
  const left = readZip(original);
  const right = readZip(profile);
  const allNames = new Set([...left.keys(), ...right.keys()].filter((name) => !isSignatureEntry(name)));
  const allowed = new Set();
  for (const name of allNames) {
    if (/^classes(?:[2-9][0-9]*)?\.dex$/.test(name)) allowed.add(name);
    if (role === "protected" && /^assets\/ah\/runtime\/(?:config\.bin|payload\.ahdc)$/.test(name)) allowed.add(name);
  }
  for (const name of allNames) {
    if (!left.has(name) || !right.has(name)) fail(`${role} APK entry set differs at ${name}`);
    if (!allowed.has(name) && !left.get(name).equals(right.get(name))) {
      if (role === "protected" && /^lib\/(?:armeabi-v7a|arm64-v8a|x86|x86_64)\/libah_runtime\.so$/.test(name)) {
        const normalizedLeft = normalizedRuntimeShare(left.get(name), `${role}-original/${name}`);
        const normalizedRight = normalizedRuntimeShare(right.get(name), `${role}-profile/${name}`);
        if (normalizedLeft.equals(normalizedRight)) continue;
      }
      fail(`${role} non-probe APK entry differs: ${name}`);
    }
  }
  const leftSurface = runDexdump(dexdump, original, scratch, `${role}-original`);
  const rightSurface = runDexdump(dexdump, profile, scratch, `${role}-profile`);
  if (JSON.stringify(leftSurface) !== JSON.stringify(rightSurface)) {
    const leftKeys = Object.keys(leftSurface);
    const rightKeys = Object.keys(rightSurface);
    const first = [...new Set([...leftKeys, ...rightKeys])].find(
      (key) => JSON.stringify(leftSurface[key]) !== JSON.stringify(rightSurface[key]));
    const leftInstructions = leftSurface[first] ?? [];
    const rightInstructions = rightSurface[first] ?? [];
    const instructionIndex = Array.from({ length: Math.max(leftInstructions.length, rightInstructions.length) }, (_, index) => index)
      .find((index) => leftInstructions[index] !== rightInstructions[index]);
    fail(`${role} non-probe DEX instruction surface differs at ${first ?? "unknown"}[${instructionIndex}]: ` +
      `${JSON.stringify(leftInstructions[instructionIndex])} != ${JSON.stringify(rightInstructions[instructionIndex])}`);
  }
}

function normalizedRuntimeShare(bytes, label) {
  const copy = Buffer.from(bytes);
  if (copy.length < 64 || !copy.subarray(0, 4).equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46])) ||
      ![1, 2].includes(copy[4]) || copy[5] !== 1 || copy[6] !== 1) {
    fail(`${label} has an unsupported ELF header`);
  }
  const is64 = copy[4] === 2;
  const safe64 = (offset) => {
    const value = copy.readBigUInt64LE(offset);
    if (value > BigInt(Number.MAX_SAFE_INTEGER)) fail(`${label} ELF offset exceeds the safe range`);
    return Number(value);
  };
  const shoff = is64 ? safe64(40) : copy.readUInt32LE(32);
  const shentsize = copy.readUInt16LE(is64 ? 58 : 46);
  const shnum = copy.readUInt16LE(is64 ? 60 : 48);
  const shstrndx = copy.readUInt16LE(is64 ? 62 : 50);
  if (shnum === 0 || shstrndx >= shnum || shoff + shentsize * shnum > copy.length) {
    fail(`${label} ELF section table is invalid`);
  }
  const section = (index) => {
    const header = shoff + shentsize * index;
    return {
      nameOffset: copy.readUInt32LE(header),
      type: copy.readUInt32LE(header + 4),
      offset: is64 ? safe64(header + 24) : copy.readUInt32LE(header + 16),
      size: is64 ? safe64(header + 32) : copy.readUInt32LE(header + 20),
    };
  };
  const names = section(shstrndx);
  if (names.offset + names.size > copy.length) fail(`${label} ELF name table is invalid`);
  const shares = [];
  for (let index = 0; index < shnum; index++) {
    const value = section(index);
    if (value.type !== 8 && value.offset + value.size > copy.length) {
      fail(`${label} ELF section exceeds file bounds`);
    }
    const nameStart = names.offset + value.nameOffset;
    if (nameStart < names.offset || nameStart >= names.offset + names.size) {
      fail(`${label} ELF section name is out of bounds`);
    }
    const nameEnd = copy.indexOf(0, nameStart);
    if (nameEnd < 0 || nameEnd >= names.offset + names.size) fail(`${label} ELF section name is unterminated`);
    if (copy.subarray(nameStart, nameEnd).toString("ascii") === ".ah_share_v1") shares.push(value);
  }
  if (shares.length !== 1 || shares[0].size !== 104) {
    fail(`${label} must contain one 104-byte Runtime share section`);
  }
  const share = shares[0];
  if (copy.subarray(share.offset, share.offset + 4).toString("ascii") !== "AHS1") {
    fail(`${label} Runtime share section is not materialized`);
  }
  copy.fill(0, share.offset, share.offset + share.size);
  return copy;
}

function validateSurface(options) {
  const root = path.resolve(options.root ?? process.cwd());
  const profileSources = [
    "tools/validation/m3-10/profile-src/ah/runtime/profile/M310StartupTimingObserver.java",
  ];
  profileSources.forEach((name) => {
    if (!existsSync(path.join(root, name))) fail(`profile source is missing: ${name}`);
  });
  for (const module of ["runtime", "host", "distribution"]) {
    const moduleRoot = path.join(root, module);
    if (!existsSync(moduleRoot)) continue;
    const stack = [moduleRoot];
    while (stack.length > 0) {
      const current = stack.pop();
      for (const entry of readdirSync(current, { withFileTypes: true })) {
        const file = path.join(current, entry.name);
        if (entry.isDirectory()) stack.push(file);
        else if (/[/\\]src[/\\](?:main|release)[/\\]/.test(file) &&
          /\.(?:java|kt|cpp|h|xml|pro)$/.test(file) &&
          readFileSync(file, "utf8").includes("M310")) {
          fail(`production source contains M3-10 material: ${path.relative(root, file)}`);
        }
      }
    }
  }
  const forbiddenSurface = [
    "M310StartupTimingObserver", "m3_10_profile", "AAH-M3-10",
    "Lah/runtime/profile/M310StartupTimingObserver;", "M3-10-PROFILE-SIGNER-V1",
  ];
  const releaseNames = ["release-bootstrap", "release-policy", "release-native", "release-fixture", "cli", "distribution"];
  for (const name of releaseNames) {
    if (!options[name]) fail(`--${name} is required`);
    if (recursiveArchiveContainsAny(path.resolve(options[name]), forbiddenSurface)) {
      fail(`${name} contains an M3-10 profile surface`);
    }
  }
  return { profileSources: profileSources.length, releaseArtifacts: releaseNames.length, releaseSurface: "CLEAN" };
}

function validateApkPair(options) {
  const original = path.resolve(required(options, "original"));
  const profile = path.resolve(required(options, "profile"));
  const role = required(options, "role");
  if (!new Set(["baseline", "protected"]).has(role)) fail("--role must be baseline or protected");
  const dexdump = path.resolve(required(options, "dexdump"));
  const scratch = path.resolve(required(options, "scratch"));
  rmSync(scratch, { recursive: true, force: true });
  mkdirSync(scratch, { recursive: true });
  try {
    comparePair(original, profile, role, dexdump, scratch);
  } finally {
    rmSync(scratch, { recursive: true, force: true });
  }
  return { role, originalSha256: sha256File(original), profileSha256: sha256File(profile) };
}

function validateSignedCopy(options) {
  const unsigned = path.resolve(required(options, "unsigned"));
  const signed = path.resolve(required(options, "signed"));
  const left = readZip(unsigned);
  const right = readZip(signed);
  const signatureEntry = (name) => /^META-INF\/(?:MANIFEST\.MF|[A-Z0-9_-]{1,32}\.(?:SF|RSA|DSA|EC))$/i.test(name);
  if ([...left.keys()].some(signatureEntry)) fail("unsigned product contains a JAR signature entry");
  const productEntries = new Map([...right].filter(([name]) => !signatureEntry(name)));
  if (left.size !== productEntries.size) fail("signed APK product entry count differs from its unsigned product");
  for (const [name, bytes] of left) {
    if (!productEntries.has(name) || !bytes.equals(productEntries.get(name))) {
      fail(`signed APK product entry differs: ${name}`);
    }
  }
  return { unsignedSha256: sha256File(unsigned), signedSha256: sha256File(signed), entries: left.size };
}

function lockedFile(lockValue, file, label) {
  exactKeys(lockValue, ["sizeBytes", "sha256"], `profile-lock.${label}`);
  if (!existsSync(file) || !statSync(file).isFile() || lstatSync(file).isSymbolicLink()) {
    fail(`${label} is not a regular file`);
  }
  if (statSync(file).size !== lockValue.sizeBytes || sha256File(file) !== lockValue.sha256) {
    fail(`${label} does not match the immutable profile lock`);
  }
}

function profileSigner(apksigner, apk) {
  let executable = apksigner;
  let commandArgs = ["verify", "--verbose", "--print-certs", "--min-sdk-version", "29", apk];
  if (apksigner.toLowerCase().endsWith(".jar")) {
    const javaHome = process.env.JAVA_HOME;
    if (!javaHome) fail("JAVA_HOME is required for pinned apksigner.jar");
    executable = path.join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java");
    commandArgs = ["-jar", apksigner, ...commandArgs];
  }
  const result = spawnSync(executable, commandArgs, {
    encoding: "utf8", windowsHide: true, timeout: 60_000, maxBuffer: 4 * 1024 * 1024,
  });
  if (result.error || result.status !== 0) fail(`apksigner rejected ${path.basename(apk)}`);
  const output = `${result.stdout}\n${result.stderr}`;
  const scheme = (number) => new RegExp(`Verified using v${number} scheme[^:]*:\\s*(true|false)`, "i")
    .exec(output)?.[1]?.toLowerCase() === "true";
  const digests = [...output.matchAll(/Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-f]{64})/gi)]
    .map((match) => match[1].toLowerCase());
  if (digests.length !== 1 || scheme(1) || scheme(2) || !scheme(3) || scheme(4)) {
    fail(`${path.basename(apk)} must have exactly one v3-only signer`);
  }
  const commitment = sha256Bytes(Buffer.concat([
    Buffer.from("M3-10-PROFILE-SIGNER-V1\0", "utf8"), Buffer.from(digests[0], "hex"),
  ]));
  return { commitment, prefix: digests[0].slice(0, 12) };
}

function validateProfileLock(options) {
  const lockFile = path.resolve(required(options, "lock"));
  const lock = json(lockFile);
  exactKeys(lock, [
    "schemaVersion", "taskId", "fixtureId", "productTupleSha256", "canonical", "observer",
    "derivation", "profileSigner", "outputs", "toolchain", "retention",
  ], "profile-lock");
  if (lock.schemaVersion !== 1 || lock.taskId !== "M3-10" || lock.fixtureId !== "java-single-dex" ||
      lock.productTupleSha256 !== "883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd") {
    fail("profile lock identity differs");
  }
  exactKeys(lock.canonical, ["baseline", "protected"], "profile-lock.canonical");
  exactKeys(lock.observer, ["sourceSha256", "dexSizeBytes", "dexSha256", "classDescriptor"], "profile-lock.observer");
  exactKeys(lock.derivation, ["manifestSizeBytes", "manifestSha256", "profileBaselineDexSha256",
    "profileProtectedPayloadDexSha256", "profileProtectedShellDexSha256"], "profile-lock.derivation");
  exactKeys(lock.profileSigner, ["commitmentAlgorithm", "commitment", "certificateSha256Prefix", "signerCount",
    "requiredScheme", "v1Allowed", "v2Allowed", "v4Allowed"], "profile-lock.profileSigner");
  exactKeys(lock.outputs, ["unsignedBaseline", "unsignedProtected", "alignedBaseline", "alignedProtected",
    "signedBaseline", "signedProtected"], "profile-lock.outputs");
  exactKeys(lock.toolchain, ["java", "buildTools", "dexlib2", "minSdk", "zipalignPageBytes"], "profile-lock.toolchain");
  exactKeys(lock.retention, ["trackedApks", "ignoredRoot", "privateKeyRetained", "seedRetained",
    "regenerationPermitted"], "profile-lock.retention");
  if (lock.profileSigner.commitmentAlgorithm !== "SHA-256(UTF8('M3-10-PROFILE-SIGNER-V1\\0') || certificateSha256Bytes)" ||
      lock.profileSigner.signerCount !== 1 || lock.profileSigner.requiredScheme !== "v3" ||
      lock.profileSigner.v1Allowed !== false || lock.profileSigner.v2Allowed !== false || lock.profileSigner.v4Allowed !== false ||
      lock.toolchain.java !== "17.0.19" || lock.toolchain.buildTools !== "36.1.0" || lock.toolchain.dexlib2 !== "2.5.2" ||
      lock.toolchain.minSdk !== 29 || lock.toolchain.zipalignPageBytes !== 4096) fail("profile lock policy/toolchain differs");
  lockedFile(lock.canonical.baseline, path.resolve(required(options, "original-baseline")), "canonical.baseline");
  lockedFile(lock.canonical.protected, path.resolve(required(options, "original-protected")), "canonical.protected");
  const observerSource = path.resolve(required(options, "observer-source"));
  const observerDex = path.resolve(required(options, "observer-dex"));
  if (sha256File(observerSource) !== lock.observer.sourceSha256 || statSync(observerDex).size !== lock.observer.dexSizeBytes ||
      sha256File(observerDex) !== lock.observer.dexSha256 ||
      lock.observer.classDescriptor !== "Lah/runtime/profile/M310StartupTimingObserver;") {
    fail("observer source/DEX identity differs");
  }
  const derivationManifest = path.resolve(required(options, "derivation-manifest"));
  if (statSync(derivationManifest).size !== lock.derivation.manifestSizeBytes ||
      sha256File(derivationManifest) !== lock.derivation.manifestSha256) fail("derivation manifest identity differs");
  const derivation = json(derivationManifest);
  for (const key of ["profileBaselineDexSha256", "profileProtectedPayloadDexSha256", "profileProtectedShellDexSha256"]) {
    if (derivation[key] !== lock.derivation[key]) fail(`derivation lock differs: ${key}`);
  }
  const outputMap = {
    unsignedBaseline: "unsigned-baseline", unsignedProtected: "unsigned-protected",
    alignedBaseline: "aligned-baseline", alignedProtected: "aligned-protected",
    signedBaseline: "signed-baseline", signedProtected: "signed-protected",
  };
  for (const [name, option] of Object.entries(outputMap)) {
    lockedFile(lock.outputs[name], path.resolve(required(options, option)), `outputs.${name}`);
  }
  for (const role of ["baseline", "protected"]) {
    validateSignedCopy({ unsigned: path.resolve(required(options, `unsigned-${role}`)),
      signed: path.resolve(required(options, `aligned-${role}`)) });
    validateSignedCopy({ unsigned: path.resolve(required(options, `aligned-${role}`)),
      signed: path.resolve(required(options, `signed-${role}`)) });
  }
  const apksigner = path.resolve(required(options, "apksigner"));
  const baselineSigner = profileSigner(apksigner, path.resolve(required(options, "signed-baseline")));
  const protectedSigner = profileSigner(apksigner, path.resolve(required(options, "signed-protected")));
  if (baselineSigner.commitment !== protectedSigner.commitment ||
      baselineSigner.commitment !== lock.profileSigner.commitment ||
      baselineSigner.prefix !== lock.profileSigner.certificateSha256Prefix) fail("profile signer commitment differs");
  if (lock.retention.trackedApks !== false || lock.retention.privateKeyRetained !== false ||
      lock.retention.seedRetained !== false || lock.retention.regenerationPermitted !== false) {
    fail("profile lock retention policy differs");
  }
  sensitiveScan(lock, "profile lock");
  return {
    lockSha256: sha256File(lockFile),
    observerDexSha256: lock.observer.dexSha256,
    signerCommitment: lock.profileSigner.commitment,
  };
}

export function validatePackage(options) {
  const root = path.resolve(required(options, "artifact-root"));
  requireRegularRoot(root, PACKAGE_FILES);
  const documents = Object.fromEntries(
    ["artifact-manifest", "campaign-a", "campaign-b", "cleanup", "probe-manifest", "result"]
      .map((name) => [name, json(path.join(root, `${name}.json`))]),
  );
  validateProfileLock({
    lock: path.join(root, "profile-lock.json"),
    "original-baseline": path.join(root, "original-baseline.apk"),
    "original-protected": path.join(root, "original-protected.apk"),
    "observer-source": path.join(options.root ?? process.cwd(), "tools/validation/m3-10/profile-src/ah/runtime/profile/M310StartupTimingObserver.java"),
    "observer-dex": path.join(root, "observer.dex"),
    "derivation-manifest": path.join(root, "derivation-manifest.json"),
    "unsigned-baseline": path.join(root, "profile-baseline-unsigned.apk"),
    "unsigned-protected": path.join(root, "profile-protected-unsigned.apk"),
    "aligned-baseline": path.join(root, "profile-baseline-aligned.apk"),
    "aligned-protected": path.join(root, "profile-protected-aligned.apk"),
    "signed-baseline": path.join(root, "profile-baseline.apk"),
    "signed-protected": path.join(root, "profile-protected.apk"),
    apksigner: required(options, "apksigner"),
  });
  Object.entries(documents).forEach(([name, value]) => sensitiveScan(value, name));
  const identity = identityOf(documents.result.identity, "result.identity");
  for (const key of ["campaign-a", "campaign-b", "artifact-manifest", "probe-manifest"]) {
    sameIdentity(identityOf(documents[key].identity, `${key}.identity`), identity, key);
  }
  const manifest = documents["artifact-manifest"];
  exactKeys(manifest, ["schemaVersion", "identity", "files"], "artifact-manifest");
  if (manifest.schemaVersion !== 1) fail("artifact-manifest schema differs");
  exactKeys(manifest.files, MANIFESTED_FILES, "artifact-manifest.files");
  for (const name of MANIFESTED_FILES) {
    exactKeys(manifest.files[name], ["sha256", "size"], `manifest.files.${name}`);
    const file = path.join(root, name);
    if (manifest.files[name].sha256 !== sha256File(file) || manifest.files[name].size !== statSync(file).size) {
      fail(`manifest hash/size differs for ${name}`);
    }
  }
  const probe = documents["probe-manifest"];
  exactKeys(probe, ["schemaVersion", "identity", "originalBaselineSha256", "originalProtectedSha256",
    "profileBaselineSha256", "profileProtectedSha256", "outerPoints", "innerPoints",
    "maximumProtectedProbeCount", "profileVerificationSha256"], "probe-manifest");
  if (probe.schemaVersion !== 1 || probe.outerPoints !== 16 || probe.innerPoints !== 9 ||
      probe.maximumProtectedProbeCount !== MAX_PROTECTED_PROBES) fail("probe manifest fixed boundary differs");
  const apkHashes = {
    originalBaselineSha256: sha256File(path.join(root, "original-baseline.apk")),
    originalProtectedSha256: sha256File(path.join(root, "original-protected.apk")),
    profileBaselineSha256: sha256File(path.join(root, "profile-baseline.apk")),
    profileProtectedSha256: sha256File(path.join(root, "profile-protected.apk")),
  };
  for (const [key, value] of Object.entries(apkHashes)) if (probe[key] !== value) fail(`probe-manifest.${key} differs`);
  const profileVerificationFile = path.join(root, "profile-verification.json");
  if (probe.profileVerificationSha256 !== sha256File(profileVerificationFile)) fail("profile verification hash differs");
  const profileVerification = json(profileVerificationFile);
  for (const key of ["profileV3Verified", "sameProfileSigner", "manifestBytesEqual", "baselineFactoryAbsent",
    "authenticatedContainerVerified", "runtimeShareSlotsOnly", "exactProbeDexTransforms"]) {
    if (profileVerification[key] !== true) fail(`profile verification is incomplete: ${key}`);
  }
  const dexdump = path.resolve(required(options, "dexdump"));
  if (!existsSync(dexdump) || !statSync(dexdump).isFile()) fail("pinned dexdump is missing");
  const scratch = path.resolve(options.scratch ?? path.join(root, "..", "scratch"));
  const allowedScratch = path.resolve(root, "..");
  if (!scratch.startsWith(allowedScratch + path.sep)) fail("scratch must remain beside the artifact root");
  rmSync(scratch, { recursive: true, force: true });
  mkdirSync(scratch, { recursive: true });
  try {
    comparePair(path.join(root, "original-baseline.apk"), path.join(root, "profile-baseline.apk"), "baseline", dexdump, scratch);
    comparePair(path.join(root, "original-protected.apk"), path.join(root, "profile-protected.apk"), "protected", dexdump, scratch);
  } finally {
    rmSync(scratch, { recursive: true, force: true });
  }
  const releaseMap = {
    "release-bootstrap": "release-bootstrap.aar",
    "release-policy": "release-policy.aar",
    "release-native": "release-native.aar",
    "release-fixture": "release-fixture.apk",
    cli: "cli.zip",
    distribution: "distribution.zip",
  };
  validateSurface({ root: options.root ?? process.cwd(), ...Object.fromEntries(
    Object.entries(releaseMap).map(([key, name]) => [key, path.join(root, name)]),
  ) });
  const campaignA = validateCampaign(documents["campaign-a"], "A", ["baseline", "protected"], identity);
  const campaignB = validateCampaign(documents["campaign-b"], "B", ["protected", "baseline"], identity);
  const computed = computeResult(campaignA, campaignB);
  exactKeys(documents.result, ["schemaVersion", "identity", "status", "selectedOwner", "eligibleOwners", "campaigns"], "result");
  if (documents.result.schemaVersion !== 1 ||
      JSON.stringify({ status: documents.result.status, selectedOwner: documents.result.selectedOwner,
        eligibleOwners: documents.result.eligibleOwners, campaigns: documents.result.campaigns }) !== JSON.stringify(computed)) {
    fail("result does not equal independently recomputed attribution");
  }
  validateCleanup(documents.cleanup);
  return { status: computed.status, selectedOwner: computed.selectedOwner, manifestSha256: sha256File(path.join(root, "artifact-manifest.json")) };
}

function validateCleanup(value) {
  exactKeys(value, ["schemaVersion", "packagesAbsent", "remoteFilesAbsent", "temporarySigningAbsent"], "cleanup");
  if (value.schemaVersion !== 1 || value.packagesAbsent !== true || value.remoteFilesAbsent !== true ||
      value.temporarySigningAbsent !== true) fail("cleanup is not complete");
}

function validateGithubModel(ledger, runs, jobs, artifacts, identity, artifactSize) {
  exactKeys(ledger, ["taskKey", "productTuple", "headSha", "workflowPath", "workflowName", "jobName", "artifactName"], "ledger");
  if (ledger.taskKey !== TASK_KEY || ledger.productTuple !== identity.productTuple ||
      ledger.headSha !== identity.headSha ||
      ledger.workflowPath !== ".github/workflows/m3-09-startup-attribution.yml" ||
      ledger.workflowName !== `${TASK_KEY}-${identity.productTuple}` ||
      ledger.jobName !== "m3-09-startup-attribution" ||
      ledger.artifactName !== "m3-09-startup-attribution-raw") fail("ledger differs");
  if (!Number.isSafeInteger(runs.total_count) || !Array.isArray(runs.workflow_runs) ||
      runs.total_count !== runs.workflow_runs.length || runs.total_count >= 100) fail("runs pagination is incomplete");
  const matching = runs.workflow_runs.filter((run) =>
    run.path === ledger.workflowPath && run.head_sha === identity.headSha &&
    run.run_attempt === 1 && run.event === "push" &&
    run.name === ledger.workflowName);
  if (matching.length !== 1 || String(matching[0].id) !== String(identity.runId) ||
      matching[0].status !== "completed" || matching[0].conclusion !== "success") fail("unique terminal diagnostic run differs");
  if (!Number.isSafeInteger(jobs.total_count) || !Array.isArray(jobs.jobs) ||
      jobs.total_count !== jobs.jobs.length || jobs.total_count >= 100) fail("jobs pagination is incomplete");
  const matchingJobs = jobs.jobs.filter((job) => job.name === ledger.jobName && String(job.run_id) === String(identity.runId));
  if (matchingJobs.length !== 1 || String(matchingJobs[0].id) !== String(identity.jobId) ||
      matchingJobs[0].status !== "completed" || matchingJobs[0].conclusion !== "success") fail("diagnostic job differs");
  if (!Number.isSafeInteger(artifacts.total_count) || !Array.isArray(artifacts.artifacts) ||
      artifacts.total_count !== artifacts.artifacts.length || artifacts.total_count >= 100) fail("artifact pagination is incomplete");
  const matchingArtifacts = artifacts.artifacts.filter((artifact) =>
    artifact.name === ledger.artifactName && String(artifact.workflow_run?.id) === String(identity.runId));
  if (matchingArtifacts.length !== 1 || matchingArtifacts[0].expired !== false ||
      matchingArtifacts[0].size_in_bytes !== artifactSize) fail("diagnostic artifact differs");
  return matchingArtifacts[0];
}

export function validateGithubEvidence(options) {
  const root = path.resolve(required(options, "evidence-root"));
  requireRegularRoot(root, [
    "artifact.zip", "artifacts-page-1.json", "jobs-page-1.json", "ledger.json",
    "page-manifest.json", "runs-page-1.json",
  ]);
  const ledger = json(path.join(root, "ledger.json"));
  const pages = json(path.join(root, "page-manifest.json"));
  const runsFile = path.join(root, "runs-page-1.json");
  const jobsFile = path.join(root, "jobs-page-1.json");
  const artifactsFile = path.join(root, "artifacts-page-1.json");
  const artifactFile = path.join(root, "artifact.zip");
  const runs = json(runsFile);
  const jobs = json(jobsFile);
  const artifacts = json(artifactsFile);
  const report = json(path.resolve(required(options, "result")));
  const identity = identityOf(report.identity, "result.identity");
  exactKeys(pages, ["schemaVersion", "pages", "artifact"], "page-manifest");
  if (pages.schemaVersion !== 1) fail("page-manifest schema differs");
  exactKeys(pages.pages, ["runs-page-1.json", "jobs-page-1.json", "artifacts-page-1.json"], "page-manifest.pages");
  for (const [name, file] of [["runs-page-1.json", runsFile], ["jobs-page-1.json", jobsFile], ["artifacts-page-1.json", artifactsFile]]) {
    exactKeys(pages.pages[name], ["sha256", "sizeBytes", "page", "perPage", "nextPageAbsent"], `page-manifest.pages.${name}`);
    if (pages.pages[name].sha256 !== sha256File(file) || pages.pages[name].sizeBytes !== statSync(file).size ||
        pages.pages[name].page !== 1 || pages.pages[name].perPage !== 100 || pages.pages[name].nextPageAbsent !== true) {
      fail(`${name} raw-page binding differs`);
    }
  }
  exactKeys(pages.artifact, ["sha256", "sizeBytes"], "page-manifest.artifact");
  if (pages.artifact.sha256 !== sha256File(artifactFile) || pages.artifact.sizeBytes !== statSync(artifactFile).size) {
    fail("downloaded artifact binding differs");
  }
  const matchingArtifact = validateGithubModel(ledger, runs, jobs, artifacts, identity, statSync(artifactFile).size);
  const packageEntries = readZip(artifactFile);
  if (!packageEntries.has("artifact-manifest.json") || !packageEntries.has("result.json") ||
      sha256Bytes(packageEntries.get("result.json")) !== sha256File(path.resolve(required(options, "result")))) {
    fail("downloaded artifact does not bind the validated result");
  }
  sensitiveScan({ ledger, runs, jobs, artifacts }, "GitHub evidence");
  return { runId: String(identity.runId), jobId: String(identity.jobId), artifactId: String(matchingArtifact.id) };
}

function sample(ordinal, totalDelta = 360_000_000, runtime = 300_000_000) {
  const baseStart = 1_000_000_000 + ordinal * 1_000_000_000;
  const baselineDurations = [10, 5, 10, 5, 10, 5, 10, 5].map((value) => value * 1_000_000);
  const baselineOuter = [baseStart];
  baselineDurations.forEach((value) => baselineOuter.push(baselineOuter.at(-1) + value));
  while (baselineOuter.length < 16) baselineOuter.push(baselineOuter.at(-1) + 1_000_000);
  const protectedOuter = [...baselineOuter];
  protectedOuter[1] += runtime + (totalDelta - runtime);
  for (let index = 2; index < protectedOuter.length; index++) protectedOuter[index] += totalDelta;
  const h0 = baseStart + 2_000_000;
  const inner = [h0];
  for (let index = 1; index < 9; index++) inner.push(h0 + Math.floor(runtime * index / 8));
  const events = [...EXPECTED_EVENTS];
  return {
    baseline: { ordinal, outerNs: baselineOuter, innerNs: null, events: [...events] },
    protected: { ordinal, outerNs: protectedOuter, innerNs: inner, events: [...events] },
  };
}

function campaign(name, identity, runtime = 300_000_000) {
  const retained = Array.from({ length: 15 }, (_, index) => sample(index + 1, 360_000_000, runtime));
  const warmups = Array.from({ length: 5 }, (_, index) => sample(index + 1, 360_000_000, runtime));
  return {
    schemaVersion: 1, campaign: name,
    order: name === "A" ? ["baseline", "protected"] : ["protected", "baseline"],
    warmups: { baseline: warmups.map((value) => value.baseline), protected: warmups.map((value) => value.protected) },
    samples: { baseline: retained.map((value) => value.baseline), protected: retained.map((value) => value.protected) },
    calibrationNs: Array(15).fill(100_000), maximumProtectedProbeCount: MAX_PROTECTED_PROBES,
    identity,
  };
}

function selfTest() {
  const identity = {
    headSha: "a".repeat(40), runId: "1001", jobId: "2002", runAttempt: 1,
    environmentId: ENVIRONMENT, bootIdHashPrefix: "b".repeat(12), taskKey: TASK_KEY,
    productTuple: "c".repeat(64),
  };
  const a = campaign("A", identity);
  const b = campaign("B", identity);
  const aComputed = validateCampaign(a, "A", ["baseline", "protected"], identity);
  const bComputed = validateCampaign(b, "B", ["protected", "baseline"], identity);
  const expected = computeResult(aComputed, bComputed);
  if (expected.status !== "ELIGIBLE" || expected.selectedOwner !== "RUNTIME_BOOTSTRAP") {
    fail("canonical attribution does not select Runtime");
  }
  const mutations = [
    ["attempt", (value) => { value.identity.runAttempt = 2; }],
    ["order", (value) => { value.order.reverse(); }],
    ["warmups", (value) => { value.warmups.baseline.pop(); }],
    ["samples", (value) => { value.samples.protected.pop(); }],
    ["ordinal", (value) => { value.samples.baseline[3].ordinal = 3; }],
    ["outer-count", (value) => { value.samples.baseline[0].outerNs.pop(); }],
    ["outer-order", (value) => { value.samples.baseline[0].outerNs[2] = value.samples.baseline[0].outerNs[1] - 1; }],
    ["inner-count", (value) => { value.samples.protected[0].innerNs.pop(); }],
    ["inner-order", (value) => { value.samples.protected[0].innerNs[3] = value.samples.protected[0].innerNs[2] - 1; }],
    ["events", (value) => { value.samples.protected[0].events.push("unexpected"); }],
    ["calibration-count", (value) => { value.calibrationNs.pop(); }],
    ["calibration-overhead", (value) => { value.calibrationNs[14] = 300_000; }],
    ["probe-count", (value) => { value.maximumProtectedProbeCount = 23; }],
  ];
  let rejected = 0;
  const accepted = [];
  for (const [name, mutate] of mutations) {
    const value = structuredClone(a);
    mutate(value);
    try {
      validateCampaign(value, "A", ["baseline", "protected"], identity);
    } catch {
      rejected++;
      continue;
    }
    accepted.push(name);
  }
  const multiA = validateCampaign(campaign("A", identity, 180_000_000), "A", ["baseline", "protected"], identity);
  const multiB = validateCampaign(campaign("B", identity, 180_000_000), "B", ["protected", "baseline"], identity);
  if (computeResult(multiA, multiB).status !== "UNATTRIBUTED") fail("multiple/residual owner case must be unattributed");
  if (rejected !== mutations.length) {
    fail(`self-test rejected ${rejected}/${mutations.length} mutations; accepted=${accepted.join(",")}`);
  }
  validateCleanup({ schemaVersion: 1, packagesAbsent: true, remoteFilesAbsent: true, temporarySigningAbsent: true });
  const cleanupMutations = ["packagesAbsent", "remoteFilesAbsent", "temporarySigningAbsent"].map((key) =>
    expectRejected(`cleanup-${key}`, () => validateCleanup({ schemaVersion: 1, packagesAbsent: true,
      remoteFilesAbsent: true, temporarySigningAbsent: true, [key]: false })));

  const ledger = { taskKey: TASK_KEY, productTuple: identity.productTuple, headSha: identity.headSha,
    workflowPath: ".github/workflows/m3-09-startup-attribution.yml",
    workflowName: `${TASK_KEY}-${identity.productTuple}`, jobName: "m3-09-startup-attribution",
    artifactName: "m3-09-startup-attribution-raw" };
  const runs = { total_count: 1, workflow_runs: [{ id: 1001, path: ledger.workflowPath, head_sha: identity.headSha,
    run_attempt: 1, event: "push", name: ledger.workflowName, status: "completed", conclusion: "success" }] };
  const jobs = { total_count: 1, jobs: [{ id: 2002, run_id: 1001, name: ledger.jobName,
    status: "completed", conclusion: "success" }] };
  const artifacts = { total_count: 1, artifacts: [{ id: 3003, name: ledger.artifactName, expired: false,
    size_in_bytes: 4004, workflow_run: { id: 1001 } }] };
  validateGithubModel(ledger, runs, jobs, artifacts, identity, 4004);
  const githubMutations = [
    ["github-ledger-tuple", (l) => { l.productTuple = "d".repeat(64); }],
    ["github-run-pagination", (_l, r) => { r.total_count = 100; }],
    ["github-run-duplicate", (_l, r) => { r.workflow_runs.push(structuredClone(r.workflow_runs[0])); r.total_count = 2; }],
    ["github-run-name", (_l, r) => { r.workflow_runs[0].name += "-suffix"; }],
    ["github-run-attempt", (_l, r) => { r.workflow_runs[0].run_attempt = 2; }],
    ["github-job-pagination", (_l, _r, j) => { j.total_count = 100; }],
    ["github-job-id", (_l, _r, j) => { j.jobs[0].id = 9999; }],
    ["github-artifact-expired", (_l, _r, _j, ar) => { ar.artifacts[0].expired = true; }],
    ["github-artifact-size", (_l, _r, _j, ar) => { ar.artifacts[0].size_in_bytes = 1; }],
  ].map(([name, mutate]) => {
    const values = [structuredClone(ledger), structuredClone(runs), structuredClone(jobs), structuredClone(artifacts)];
    mutate(...values);
    return expectRejected(name, () => validateGithubModel(...values, identity, 4004));
  });
  return { canonical: 1, rejectedMutations: rejected + cleanupMutations.length + githubMutations.length,
    reportMutations: rejected, cleanupMutations, githubMutations, owner: expected.selectedOwner };
}

function expectRejected(name, action) {
  try { action(); } catch { return name; }
  fail(`named mutation was accepted: ${name}`);
}

function profileSelfTest(options) {
  validateProfileLock(options);
  const scratch = path.resolve(required(options, "scratch"));
  const allowed = path.resolve(process.cwd(), "build", "m3-10") + path.sep;
  if (!(scratch + path.sep).startsWith(allowed)) fail("profile self-test scratch must remain under build/m3-10");
  rmSync(scratch, { recursive: true, force: true });
  mkdirSync(scratch, { recursive: true });
  const mutations = [];
  const mutateFile = (name, option, mutate = (bytes) => { bytes[bytes.length - 1] ^= 1; return bytes; }) => {
    const output = path.join(scratch, `${name}${path.extname(options[option]) || ".bin"}`);
    writeFileSync(output, mutate(Buffer.from(readFileSync(options[option]))));
    mutations.push(expectRejected(name, () => validateProfileLock({ ...options, [option]: output })));
  };
  try {
    mutateFile("canonical-apk-hash", "original-baseline");
    mutateFile("observer-source", "observer-source");
    mutateFile("observer-dex", "observer-dex");
    mutateFile("derivation-manifest", "derivation-manifest");
    mutateFile("unsigned-apk", "unsigned-baseline");
    mutateFile("aligned-apk", "aligned-baseline");
    mutateFile("signed-apk", "signed-baseline");
    mutations.push(expectRejected("signer-pair", () => validateProfileLock({ ...options,
      "signed-protected": options["signed-baseline"] })));

    const dexdump = path.resolve(required(options, "dexdump"));
    const pairScratch = path.join(scratch, "dexdump");
    const profileBaseline = path.resolve(options["signed-baseline"]);
    const profileProtected = path.resolve(options["signed-protected"]);
    const structural = [
      ["manifest", profileBaseline, "AndroidManifest.xml", "baseline", options["original-baseline"], 8],
      ["resource", profileBaseline, "resources.arsc", "baseline", options["original-baseline"], 8],
      ["dex", profileBaseline, "classes.dex", "baseline", options["original-baseline"], 120],
      ["native", profileProtected, "lib/x86_64/libah_runtime.so", "protected", options["original-protected"], 256],
    ];
    for (const [name, source, entry, role, original, offset] of structural) {
      const mutated = path.join(scratch, `${name}.apk`);
      writeFileSync(mutated, replaceZipEntry(source, entry, (bytes) => { bytes[Math.min(offset, bytes.length - 1)] ^= 1; return bytes; }));
      mutations.push(expectRejected(`${name}-surface`, () => {
        rmSync(pairScratch, { recursive: true, force: true }); mkdirSync(pairScratch);
        comparePair(path.resolve(original), mutated, role, dexdump, pairScratch);
      }));
    }
    const surfaceOptions = { root: options.root ?? process.cwd() };
    for (const name of ["release-bootstrap", "release-policy", "release-native", "release-fixture", "cli", "distribution"]) {
      surfaceOptions[name] = path.resolve(required(options, name));
    }
    const polluted = path.join(scratch, "polluted-release.aar");
    writeFileSync(polluted, Buffer.concat([readFileSync(surfaceOptions["release-bootstrap"]), Buffer.from("\0M310StartupTimingObserver\0")]));
    mutations.push(expectRejected("binary-release-surface", () => validateSurface({ ...surfaceOptions, "release-bootstrap": polluted })));
    mutations.push(expectRejected("sensitive-host-path", () => sensitiveScan("C:\\Users\\redacted\\secret", "mutation")));
  } finally {
    rmSync(scratch, { recursive: true, force: true });
  }
  return { canonicalProfiles: 1, rejectedRealMutations: mutations.length, mutations };
}

function summarize(options) {
  const campaignAFile = path.resolve(required(options, "campaign-a"));
  const campaignBFile = path.resolve(required(options, "campaign-b"));
  const output = path.resolve(required(options, "output"));
  const campaignAValue = json(campaignAFile);
  const campaignBValue = json(campaignBFile);
  const identity = identityOf(campaignAValue.identity, "campaign-a.identity");
  sameIdentity(identityOf(campaignBValue.identity, "campaign-b.identity"), identity, "campaign-b");
  const campaignA = validateCampaign(campaignAValue, "A", ["baseline", "protected"], identity);
  const campaignB = validateCampaign(campaignBValue, "B", ["protected", "baseline"], identity);
  const computed = computeResult(campaignA, campaignB);
  const report = { schemaVersion: 1, identity, ...computed };
  writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
  return { output, status: computed.status, selectedOwner: computed.selectedOwner };
}

const [command = "", ...rest] = process.argv.slice(2);
try {
  const options = optionsOf(rest);
  let result;
  if (command === "self-test") result = selfTest();
  else if (command === "profile-self-test") result = profileSelfTest(options);
  else if (command === "surface") result = validateSurface(options);
  else if (command === "profile-lock") result = validateProfileLock(options);
  else if (command === "apk-pair") result = validateApkPair(options);
  else if (command === "signed-copy") result = validateSignedCopy(options);
  else if (command === "summarize") result = summarize(options);
  else if (command === "package") result = validatePackage(options);
  else if (command === "github-evidence") result = validateGithubEvidence(options);
  else fail("usage: self-test | profile-self-test | profile-lock | surface | apk-pair | signed-copy | summarize | package | github-evidence");
  process.stdout.write(`${JSON.stringify({ status: "PASS", ...result })}\n`);
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
