import { createHash } from "node:crypto";
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const reportPath = resolve(root, "host/container/build/reports/m1-04/cross-language-vector.json");
const payloadPath = resolve(root, "host/container/build/reports/m1-04/work/deterministic-a.ahdc");
const outputRoot = resolve(root, "runtime/native/build/m2-02-vector");

const fail = (message) => {
  throw new Error(`M2-02 vector preparation failed: ${message}`);
};
const sha256 = (bytes) => createHash("sha256").update(bytes).digest();
const exactHex = (value, bytes, label) => {
  if (typeof value !== "string" || !new RegExp(`^[0-9a-f]{${bytes * 2}}$`, "u").test(value)) {
    fail(label);
  }
  return Buffer.from(value, "hex");
};

const report = JSON.parse(readFileSync(reportPath, "utf8"));
if (report.schema !== "ahdc-v2-cross-language-vector-v1" ||
    report.package_name !== "ah.fixtures.container") {
  fail("unexpected source vector identity");
}
const config = exactHex(report.config_v2_hex, 768, "config_v2_hex");
const rNative = exactHex(report.r_native_hex, 32, "r_native_hex");
const buildId = exactHex(report.build_id_hex, 16, "build_id_hex");
const keySlotId = exactHex(report.key_slot_id_hex, 16, "key_slot_id_hex");
const payload = readFileSync(payloadPath);
if (sha256(payload).toString("hex") !== report.container_sha256) {
  fail("payload SHA-256");
}
if (!config.subarray(24, 40).equals(buildId) || !config.subarray(40, 56).equals(keySlotId)) {
  fail("config build/key-slot binding");
}

const slot = Buffer.alloc(104);
slot.write("AHS1", 0, "ascii");
slot.writeUInt16LE(1, 4);
slot.writeUInt16LE(4, 6);
keySlotId.copy(slot, 8);
buildId.copy(slot, 24);
rNative.copy(slot, 40);
sha256(slot.subarray(0, 72)).copy(slot, 72);

mkdirSync(outputRoot, { recursive: true });
writeFileSync(resolve(outputRoot, "config.bin"), config);
copyFileSync(payloadPath, resolve(outputRoot, "payload.ahdc"));
writeFileSync(resolve(outputRoot, "slot-x86_64.bin"), slot);
console.log(`M2-02 native vector prepared: ${outputRoot}`);
