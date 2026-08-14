#!/usr/bin/env node

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const root = "tools/validation/src/fuzz/resources/corpus/native";
const u16 = (buffer, offset, value) => buffer.writeUInt16LE(value, offset);
const u32 = (buffer, offset, value) => buffer.writeUInt32LE(value, offset);
const u64 = (buffer, offset, value) => buffer.writeBigUInt64LE(BigInt(value), offset);
const selected = (selector, payload) => Buffer.concat([Buffer.of(selector), payload]);

const header = Buffer.alloc(160);
header.write("AHDC", 0, "ascii");
u16(header, 4, 2); u16(header, 8, 160); u32(header, 12, 1); u32(header, 16, 76);
u32(header, 20, 128); u32(header, 24, 1); u32(header, 28, 32); u64(header, 32, 17);
u32(header, 136, 65_536);
const signer = Buffer.alloc(76);
signer.write("SPV1", 0, "ascii"); u16(signer, 4, 1); u16(signer, 8, 1);
signer.fill(0x31, 12, 44); signer.fill(0x31, 44, 76);
const record = Buffer.alloc(128);
u16(record, 4, 11); u64(record, 8, 1); u64(record, 16, 1); u32(record, 24, 1);
record.fill(0x41, 40, 48); record.write("classes.dex", 48, "ascii");
const chunk = Buffer.alloc(32);
u32(chunk, 24, 1);
const config = Buffer.alloc(768);
config.write("AHKC", 0, "ascii"); u16(config, 4, 2); u32(config, 12, 768);
u16(config, 16, 2); u16(config, 18, 1); u16(config, 20, 1);
const slot = Buffer.alloc(104);
slot.write("AHS1", 0, "ascii"); u16(slot, 4, 1); u16(slot, 6, 1);

const files = new Map([
  ["container-topology.seed", selected(0, Buffer.concat([header, signer, record, chunk]))],
  ["config-v2.seed", selected(1, config)],
  ["native-share-slot.seed", selected(2, slot)],
  ["record-v2.seed", selected(3, record)],
  ["chunk-v2.seed", selected(4, chunk)],
  ["signer-policy-v1.seed", selected(5, signer)],
]);
if (process.argv[2] === "--write") {
  mkdirSync(root, {recursive: true});
  for (const [name, bytes] of files) writeFileSync(path.join(root, name), bytes);
} else if (process.argv[2] === "--check") {
  for (const [name, bytes] of files) {
    if (!readFileSync(path.join(root, name)).equals(bytes)) {
      throw new Error(`M3-02 Native corpus mismatch: ${name}`);
    }
  }
} else {
  throw new Error("usage: --write|--check");
}
process.stdout.write(`OK: M3-02 Native structured corpus files=${files.size}\n`);
