import { createDecipheriv, createHash, createHmac, hkdfSync, timingSafeEqual } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { inflateSync } from "node:zlib";

const reportDir = resolve("host/container/build/reports/m1-04");
const vectorPath = resolve(reportDir, "cross-language-vector.json");
const containerPath = resolve(reportDir, "work/deterministic-a.ahdc");
const outputPath = resolve(reportDir, "cross-language-consumer.json");
const vector = JSON.parse(readFileSync(vectorPath, "utf8"));
const container = readFileSync(containerPath);

function invariant(condition, message) {
  if (!condition) throw new Error(`AHDC vector mismatch: ${message}`);
}

function sha256(value) {
  return createHash("sha256").update(value).digest();
}

function hex(value) {
  return Buffer.from(value).toString("hex");
}

function u64(buffer, offset) {
  const value = buffer.readBigUInt64LE(offset);
  invariant(value <= BigInt(Number.MAX_SAFE_INTEGER), `u64 overflow at ${offset}`);
  return Number(value);
}

function decryptGcm(key, nonce, aad, ciphertextAndTag) {
  invariant(ciphertextAndTag.length >= 16, "short GCM value");
  const ciphertext = ciphertextAndTag.subarray(0, -16);
  const tag = ciphertextAndTag.subarray(-16);
  const decipher = createDecipheriv("aes-256-gcm", key, nonce);
  decipher.setAAD(aad);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

invariant(container.subarray(0, 4).equals(Buffer.from("AHDC", "ascii")), "magic");
invariant(container.readUInt16LE(4) === 2 && container.readUInt16LE(6) === 0, "version");
invariant(container.readUInt16LE(8) === 160 && container.readUInt16LE(10) === 0, "header");
const dexCount = container.readUInt32LE(12);
const signerSize = container.readUInt32LE(16);
const recordTableSize = container.readUInt32LE(20);
const chunkCount = container.readUInt32LE(24);
const chunkTableSize = container.readUInt32LE(28);
const payloadSize = u64(container, 32);
const buildId = container.subarray(40, 56);
const keySlotId = container.subarray(56, 72);
const configDigest = container.subarray(72, 104);
const manifestMac = container.subarray(104, 136);
invariant(dexCount === vector.records.length && recordTableSize === dexCount * 128, "record totals");
invariant(chunkTableSize === chunkCount * 32, "chunk totals");
invariant(hex(buildId) === vector.build_id_hex && hex(keySlotId) === vector.key_slot_id_hex, "identifiers");
invariant(hex(sha256(container)) === vector.container_sha256, "container digest");

const config = Buffer.from(vector.config_v2_hex, "hex");
const nativeShare = Buffer.from(vector.r_native_hex, "hex");
const signerDigest = Buffer.from(vector.current_signer_sha256, "hex");
const packageDigest = Buffer.from(vector.package_name_sha256, "hex");
invariant(config.length === 768 && nativeShare.length === 32, "ConfigV2 material size");
invariant(config.subarray(0, 4).equals(Buffer.from("AHKC", "ascii")), "ConfigV2 magic");
invariant(config.subarray(24, 40).equals(buildId) && config.subarray(40, 56).equals(keySlotId), "ConfigV2 ids");
invariant(config.subarray(56, 88).equals(signerDigest), "ConfigV2 signer");
invariant(timingSafeEqual(sha256(config), configDigest), "ConfigV2 digest");

const rJava = config.subarray(88, 120);
const root = Buffer.alloc(32);
for (let index = 0; index < root.length; index += 1) root[index] = rJava[index] ^ nativeShare[index];
const kekInfo = Buffer.concat([Buffer.from("AHDC offline KEK v1", "ascii"), signerDigest, packageDigest]);
const kek = Buffer.from(hkdfSync("sha256", root, buildId, kekInfo, 32));
const cek = decryptGcm(kek, config.subarray(120, 132), config.subarray(0, 132), config.subarray(132, 180));

const signerOffset = 160;
const recordOffset = signerOffset + signerSize;
const chunkOffset = recordOffset + recordTableSize;
const payloadOffset = chunkOffset + chunkTableSize;
invariant(payloadOffset + payloadSize === container.length, "file coverage");
const zeroHeader = Buffer.from(container.subarray(0, 160));
zeroHeader.fill(0, 104, 136);
const manifestKey = Buffer.from(hkdfSync("sha256", cek, buildId, Buffer.from("AHDC manifest v2", "ascii"), 32));
const actualManifest = createHmac("sha256", manifestKey)
  .update(zeroHeader)
  .update(container.subarray(signerOffset, payloadOffset))
  .digest();
invariant(timingSafeEqual(actualManifest, manifestMac), "manifest MAC");

const results = [];
for (let index = 0; index < dexCount; index += 1) {
  const rawRecord = container.subarray(recordOffset + index * 128, recordOffset + (index + 1) * 128);
  const ordinal = rawRecord.readUInt32LE(0);
  const nameLength = rawRecord.readUInt16LE(4);
  const name = rawRecord.subarray(48, 48 + nameLength).toString("ascii");
  const originalLength = u64(rawRecord, 8);
  const compressedLength = u64(rawRecord, 16);
  const recordChunkCount = rawRecord.readUInt32LE(24);
  const firstChunk = rawRecord.readUInt32LE(28);
  const recordPayloadOffset = u64(rawRecord, 32);
  const noncePrefix = rawRecord.subarray(40, 48);
  const expectedDigest = rawRecord.subarray(72, 104);
  const expected = vector.records[index];
  invariant(ordinal === index && ordinal === expected.ordinal && name === expected.name, `record ${index} identity`);
  invariant(originalLength === expected.original_length && compressedLength === expected.compressed_length, `record ${index} lengths`);
  invariant(recordChunkCount === expected.chunk_count && hex(noncePrefix) === expected.nonce_prefix_hex, `record ${index} chunks`);
  invariant(hex(expectedDigest) === expected.original_sha256, `record ${index} expected digest`);

  const ordinalBytes = Buffer.alloc(4);
  ordinalBytes.writeUInt32LE(ordinal);
  const recordKey = Buffer.from(hkdfSync(
    "sha256",
    cek,
    buildId,
    Buffer.concat([Buffer.from("AHDC record v2", "ascii"), ordinalBytes]),
    32,
  ));
  const compressedParts = [];
  let observedCompressed = 0;
  for (let chunkOrdinal = 0; chunkOrdinal < recordChunkCount; chunkOrdinal += 1) {
    const rawChunk = container.subarray(
      chunkOffset + (firstChunk + chunkOrdinal) * 32,
      chunkOffset + (firstChunk + chunkOrdinal + 1) * 32,
    );
    const plaintextLength = rawChunk.readUInt32LE(24);
    const encryptedOffset = payloadOffset + recordPayloadOffset + observedCompressed + chunkOrdinal * 16;
    const encrypted = container.subarray(encryptedOffset, encryptedOffset + plaintextLength + 16);
    const nonce = Buffer.alloc(12);
    noncePrefix.copy(nonce);
    nonce.writeUInt32LE(chunkOrdinal, 8);
    const aad = Buffer.concat([
      Buffer.from("AHDC-GCM-V2", "ascii"),
      container.subarray(4, 8),
      buildId,
      keySlotId,
      signerDigest,
      packageDigest,
      rawRecord,
      rawChunk,
    ]);
    compressedParts.push(decryptGcm(recordKey, nonce, aad, encrypted));
    observedCompressed += plaintextLength;
  }
  invariant(observedCompressed === compressedLength, `record ${index} compressed coverage`);
  const original = inflateSync(Buffer.concat(compressedParts));
  invariant(original.length === originalLength && timingSafeEqual(sha256(original), expectedDigest), `record ${index} plaintext`);
  results.push({ ordinal, name, original_length: originalLength, original_sha256: hex(expectedDigest) });
}

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify({
  schema: "ahdc-v2-independent-node-consumer-v1",
  status: "pass",
  container_sha256: vector.container_sha256,
  config_v2_sha256: hex(configDigest),
  records: results,
}, null, 2)}\n`, "utf8");
console.log(`AHDC v2 independent Node consumer PASS records=${results.length}`);
