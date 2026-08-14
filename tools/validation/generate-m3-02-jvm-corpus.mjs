#!/usr/bin/env node

import {inflateRawSync} from "node:zlib";
import {mkdirSync, readFileSync, rmSync, writeFileSync} from "node:fs";
import path from "node:path";
import process from "node:process";

const corpus = "tools/validation/src/fuzz/resources/corpus";
const regressions = "tools/validation/src/fuzz/resources/regressions";
const apkSeed = path.join(corpus, "apk", "valid-m301.apk");
const axmlSeed = path.join(corpus, "axml", "valid-manifest.axml");
const apkRegression = path.join(regressions, "apk", "truncated-local-header.regression");
const axmlRegression = path.join(regressions, "axml", "oversized-chunk.regression");

function fail(message) {
  throw new Error(`M3-02 JVM corpus failed: ${message}`);
}

function entries(bytes) {
  let eocd = -1;
  for (let offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 65_557); offset -= 1) {
    if (bytes.readUInt32LE(offset) === 0x06054b50) { eocd = offset; break; }
  }
  if (eocd < 0) fail("missing EOCD");
  const count = bytes.readUInt16LE(eocd + 10);
  let offset = bytes.readUInt32LE(eocd + 16);
  const output = new Map();
  for (let index = 0; index < count; index += 1) {
    if (bytes.readUInt32LE(offset) !== 0x02014b50) fail("invalid central directory");
    const method = bytes.readUInt16LE(offset + 10);
    const compressedSize = bytes.readUInt32LE(offset + 20);
    const uncompressedSize = bytes.readUInt32LE(offset + 24);
    const nameLength = bytes.readUInt16LE(offset + 28);
    const extraLength = bytes.readUInt16LE(offset + 30);
    const commentLength = bytes.readUInt16LE(offset + 32);
    const localOffset = bytes.readUInt32LE(offset + 42);
    const name = bytes.subarray(offset + 46, offset + 46 + nameLength).toString("utf8");
    if (bytes.readUInt32LE(localOffset) !== 0x04034b50) fail("invalid local header");
    const localNameLength = bytes.readUInt16LE(localOffset + 26);
    const localExtraLength = bytes.readUInt16LE(localOffset + 28);
    const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = bytes.subarray(dataOffset, dataOffset + compressedSize);
    const value = method === 0 ? Buffer.from(compressed) : method === 8 ? inflateRawSync(compressed) : null;
    if (value && value.length !== uncompressedSize) fail(`size mismatch for ${name}`);
    output.set(name, value);
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return {eocd, values: output};
}

function validate() {
  const apk = readFileSync(apkSeed);
  const parsed = entries(apk);
  if (!parsed.values.has("AndroidManifest.xml") || !parsed.values.has("classes.dex")) {
    fail("valid APK seed lacks manifest or classes.dex");
  }
  if ([...parsed.values.keys()].some((name) => /^META-INF\/.*\.(?:RSA|DSA|EC|SF)$/iu.test(name))) {
    fail("valid APK seed must remain unsigned");
  }
  const axml = readFileSync(axmlSeed);
  if (axml.length < 8 || axml.readUInt16LE(0) !== 0x0003 || axml.readUInt16LE(2) !== 8 ||
      axml.readUInt32LE(4) !== axml.length || !parsed.values.get("AndroidManifest.xml").equals(axml)) {
    fail("valid Binary AXML seed is not the APK manifest");
  }
  const truncated = readFileSync(apkRegression);
  if (truncated.length !== 29 || !apk.subarray(0, truncated.length).equals(truncated)) {
    fail("APK regression is not a minimized truncated local header");
  }
  const oversized = readFileSync(axmlRegression);
  if (oversized.length !== axml.length || oversized.readUInt32LE(4) !== 0x7fffffff ||
      !oversized.subarray(8).equals(axml.subarray(8))) {
    fail("AXML regression is not an oversized binary root chunk");
  }
}

if (process.argv[2] === "--write") {
  const source = process.argv[3];
  if (!source) fail("--write requires a generated unsigned synthetic APK");
  const apk = readFileSync(source);
  const parsed = entries(apk);
  const manifest = parsed.values.get("AndroidManifest.xml");
  if (!manifest) fail("source APK lacks AndroidManifest.xml");
  mkdirSync(path.dirname(apkSeed), {recursive: true});
  mkdirSync(path.dirname(axmlSeed), {recursive: true});
  mkdirSync(path.dirname(apkRegression), {recursive: true});
  mkdirSync(path.dirname(axmlRegression), {recursive: true});
  writeFileSync(apkSeed, apk);
  writeFileSync(axmlSeed, manifest);
  writeFileSync(apkRegression, apk.subarray(0, 29));
  const oversized = Buffer.from(manifest);
  oversized.writeUInt32LE(0x7fffffff, 4);
  writeFileSync(axmlRegression, oversized);
  rmSync(path.join(corpus, "apk", "zip-header.seed"), {force: true});
  rmSync(path.join(corpus, "axml", "xml-header.seed"), {force: true});
  rmSync(path.join(regressions, "apk", "truncated-eocd.regression"), {force: true});
} else if (process.argv[2] !== "--check") {
  fail("usage: --write <unsigned-synthetic.apk>|--check");
}
validate();
process.stdout.write("OK: M3-02 valid APK/AXML corpus and binary regressions\n");
