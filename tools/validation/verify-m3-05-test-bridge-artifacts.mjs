#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { inflateRawSync } from "node:zlib";

if (process.argv.length < 4) {
  throw new Error("usage: verify-m3-05-test-bridge-artifacts.mjs required.zip forbidden.zip...");
}

const markers = ["M305HighProfileBridge", "M305HighProfileWorker"]
  .map(value => Buffer.from(value, "utf8"));

function entries(file) {
  const bytes = readFileSync(file);
  let eocd = -1;
  for (let at = bytes.length - 22; at >= Math.max(0, bytes.length - 65_557); at--) {
    if (bytes.readUInt32LE(at) === 0x06054b50) { eocd = at; break; }
  }
  if (eocd < 0) throw new Error(`${file}: missing ZIP end record`);
  const count = bytes.readUInt16LE(eocd + 10);
  let at = bytes.readUInt32LE(eocd + 16);
  const values = [];
  for (let index = 0; index < count; index++) {
    if (bytes.readUInt32LE(at) !== 0x02014b50) throw new Error(`${file}: invalid central directory`);
    const method = bytes.readUInt16LE(at + 10);
    const compressedSize = bytes.readUInt32LE(at + 20);
    const nameLength = bytes.readUInt16LE(at + 28);
    const extraLength = bytes.readUInt16LE(at + 30);
    const commentLength = bytes.readUInt16LE(at + 32);
    const local = bytes.readUInt32LE(at + 42);
    if (bytes.readUInt32LE(local) !== 0x04034b50) throw new Error(`${file}: invalid local header`);
    const localName = bytes.readUInt16LE(local + 26);
    const localExtra = bytes.readUInt16LE(local + 28);
    const start = local + 30 + localName + localExtra;
    const compressed = bytes.subarray(start, start + compressedSize);
    if (method === 0) values.push(Buffer.from(compressed));
    else if (method === 8) values.push(inflateRawSync(compressed));
    at += 46 + nameLength + extraLength + commentLength;
  }
  return values;
}

function markerPresence(file) {
  const values = entries(file);
  return markers.map(marker => values.some(value => value.indexOf(marker) >= 0));
}

const required = process.argv[2];
if (markerPresence(required).some(present => !present)) {
  throw new Error(`${required}: test-only HIGH bridge/worker missing`);
}
for (const forbidden of process.argv.slice(3)) {
  if (markerPresence(forbidden).some(Boolean)) {
    throw new Error(`${forbidden}: test-only HIGH bridge/worker escaped`);
  }
}
console.log(`M3-05 test-only bridge artifact boundary PASS forbidden=${process.argv.length - 3}`);
