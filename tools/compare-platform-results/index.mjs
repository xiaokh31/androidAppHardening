#!/usr/bin/env node
import { createDecipheriv, createHash, createHmac, hkdfSync, timingSafeEqual } from "node:crypto";
import { inflateRawSync, inflateSync } from "node:zlib";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const mode = process.argv[2];
const classification = JSON.parse(readFileSync(repo("tools/compare-platform-results/field-classification-v1.json"), "utf8"));
invariant(classification.schema_version === 1 && classification.unknown_report_fields === "reject", "field classification version");
if (mode === "platform") verifyPlatform(resolve(process.argv[3]));
else if (mode === "compare") compare(resolve(process.argv[3]), resolve(process.argv[4]), resolve(process.argv[5]));
else if (mode === "self-test") selfTest();
else fail("usage: index.mjs platform <platform-root> | compare <windows-root> <ubuntu-root> <summary> | self-test");

function verifyPlatform(root) {
  const schema = JSON.parse(readFileSync(repo("docs/specs/report-v1.schema.json"), "utf8"));
  const environment = JSON.parse(readFileSync(join(root, "environment.json"), "utf8"));
  invariant(environment.java_version === "17.0.19" && environment.gradle_version === "9.5.0" && environment.build_tools_version === "36.1.0", "pinned toolchain environment");
  invariant(environment.timezone === "UTC" && environment.locale === "und" && environment.encoding.toUpperCase() === "UTF-8", "pinned locale environment");
  invariant(environment.fixture_count === 9 && environment.runs_per_fixture === 2 && environment.input_immutable === true && environment.test_signing_cleanup === true, "environment contract");
  const semantics = [];
  const projections = [];
  const random = [];
  const hashes = [];
  for (const run of ["run1", "run2"]) {
    for (const id of fixtureIds()) {
      const base = join(root, "runs", run, id);
      const inputPath = join(base, "input.apk");
      const outputPath = join(base, "output.apk");
      const reportPath = join(base, "report.json");
      const report = JSON.parse(readFileSync(reportPath, "utf8"));
      validateSchemaKeys(report, schema, schema, "$");
      invariant(report.result.status === "success" && report.signing.performed === false, `${run}/${id}: report status`);
      const inspected = inspectOutput(outputPath, report);
      invariant(inspected.unsigned, `${run}/${id}: output signed`);
      const row = { schema_version: 1, run, fixture_id: id, ...inspected.semantic };
      semantics.push(row);
      projections.push({ schema_version: 1, run, fixture_id: id, projection: reportProjection(report) });
      random.push({ schema_version: 1, run, fixture_id: id, ...inspected.random });
      for (const [kind, path] of [["input", inputPath], ["output", outputPath], ["report", reportPath]]) {
        hashes.push(`${sha(readFileSync(path))}  runs/${run}/${id}/${kind}${kind === "report" ? ".json" : ".apk"}`);
      }
    }
  }
  verifyLocalEquivalence(semantics, projections, random);
  verifyNegatives(root, schema);
  writeJsonl(join(root, "semantic-manifests.jsonl"), semantics);
  writeJsonl(join(root, "reports.jsonl"), projections);
  writeJsonl(join(root, "random-fields.jsonl"), random);
  writeFileSync(join(root, "hashes.sha256"), hashes.sort().join("\n") + "\n", "ascii");
  const zipDiff = semantics.filter(x => x.run === "run1").map(row => ({
    fixture_id: row.fixture_id,
    differences: [],
    entry_count: row.zip.entries.length,
    fixed_timestamp: row.zip.entries.every(entry => entry.dos_time === 0 && entry.dos_date === 33),
  }));
  writeJson(join(root, "zip-metadata-diff.json"), { schema_version: 1, status: "pass", fixtures: zipDiff });
  scanForPaths(root, ["semantic-manifests.jsonl", "reports.jsonl", "random-fields.jsonl", "hashes.sha256", "zip-metadata-diff.json", "negative-results.json", "environment.json"]);
  process.stdout.write(`M3-03 platform verification PASS: ${root}\n`);
}

function compare(windowsRoot, ubuntuRoot, summaryPath) {
  const windows = loadSnapshot(windowsRoot);
  const ubuntu = loadSnapshot(ubuntuRoot);
  invariant(windows.environment.os_family === "windows" && ubuntu.environment.os_family === "ubuntu", "platform identity");
  invariant(windows.semantics.length === 18 && ubuntu.semantics.length === 18, "pair count");
  const pairs = [];
  for (const id of fixtureIds()) {
    for (const run of ["run1", "run2"]) {
      const left = find(windows.semantics, id, run);
      const right = find(ubuntu.semantics, id, run);
      invariant(canonical(left, ["run"]) === canonical(right, ["run"]), `${id}/${run}: semantic divergence`);
      invariant(canonical(find(windows.projections, id, run), ["run"]) === canonical(find(ubuntu.projections, id, run), ["run"]), `${id}/${run}: report divergence`);
      const lw = find(windows.random, id, run);
      const lu = find(ubuntu.random, id, run);
      for (const field of ["output_sha256", "container_sha256", "build_id", "key_slot_id", "manifest_mac_sha256", "tag_set_sha256", "ciphertext_sha256"])
        invariant(lw[field] !== lu[field], `${id}/${run}: randomized ${field} reused across platforms`);
      pairs.push({ fixture_id: id, run, semantic_equal: true, report_equal: true, randomized_output_different: true });
    }
  }
  const allRandom = [...windows.random, ...ubuntu.random];
  for (const field of ["output_sha256", "container_sha256", "build_id", "key_slot_id", "manifest_mac_sha256", "tag_set_sha256", "ciphertext_sha256"])
    unique(allRandom.map(row => row[field]), field);
  unique(allRandom.flatMap(row => row.nonce_prefixes), "nonce_prefix");
  invariant(canonical(windows.negatives) === canonical(ubuntu.negatives), "negative error divergence");
  const summary = {
    schema_version: 1,
    status: "pass",
    fixture_count: 9,
    platform_runs: 4,
    compared_outputs: 36,
    deterministic_fields_equal: true,
    randomized_fields_distinct: true,
    independent_authentication_and_decryption: true,
    input_immutable: true,
    outputs_unsigned: true,
    negative_error_equivalence: true,
    absolute_path_scan_findings: 0,
    pairs,
  };
  writeJson(summaryPath, summary);
  process.stdout.write(`M3-03 equivalence PASS: ${sha(readFileSync(summaryPath))}\n`);
}

function inspectOutput(path, report) {
  const bytes = readFileSync(path);
  const archive = parseZip(bytes);
  const names = archive.entries.map(entry => entry.name);
  invariant(new Set(names).size === names.length, "duplicate ZIP entry");
  const unsigned = !names.some(name => /^META-INF\/[^/]+\.(?:RSA|DSA|EC|SF)$/i.test(name)) &&
    bytes.subarray(0, archive.centralOffset).indexOf(Buffer.from("APK Sig Block 42", "ascii")) < 0;
  const extracted = new Map(archive.entries.map(entry => [entry.name, extract(bytes, entry)]));
  const payload = requireEntry(extracted, "assets/ah/runtime/payload.ahdc");
  const config = requireEntry(extracted, "assets/ah/runtime/config.bin");
  const runtimeEntries = archive.entries.filter(entry => /^lib\/(?:armeabi-v7a|arm64-v8a|x86|x86_64)\/libah_runtime\.so$/.test(entry.name));
  invariant(runtimeEntries.length > 0, "missing Runtime SO");
  const runtime = runtimeEntries.map(entry => ({ name: entry.name, bytes: requireEntry(extracted, entry.name) }));
  const container = verifyContainer(payload, config, runtime.map(x => x.bytes), report);
  const zipEntries = archive.entries.map(entry => {
    const content = requireEntry(extracted, entry.name);
    const randomized = entry.name === "assets/ah/runtime/payload.ahdc" || entry.name === "assets/ah/runtime/config.bin" ||
      /^lib\/.+\/libah_runtime\.so$/.test(entry.name);
    const normalized = /^lib\/.+\/libah_runtime\.so$/.test(entry.name) ? normalizeRuntime(content) : content;
    return {
      name: entry.name,
      method: entry.method,
      flags: entry.flags,
      dos_time: entry.dosTime,
      dos_date: entry.dosDate,
      internal_attributes: entry.internalAttributes,
      external_attributes: entry.externalAttributes,
      compressed_size: entry.compressedSize,
      uncompressed_size: entry.uncompressedSize,
      crc32: randomized ? null : entry.crc32,
      data_offset: entry.dataOffset,
      alignment: alignment(entry.name),
      aligned: entry.dataOffset % alignment(entry.name) === 0,
      content_class: randomized ? "randomized" : "deterministic",
      normalized_sha256: randomized && !/^lib\//.test(entry.name) ? null : sha(normalized),
      preserved_compressed_sha256: randomized || ["AndroidManifest.xml", "classes.dex"].includes(entry.name) ? null : sha(bytes.subarray(entry.dataOffset, entry.dataOffset + entry.compressedSize)),
    };
  });
  invariant(zipEntries.every(entry => entry.aligned), `ZIP alignment ${zipEntries.filter(entry => !entry.aligned).map(entry => `${entry.name}:${entry.data_offset}/${entry.alignment}`).join(",")}`);
  return {
    unsigned,
    semantic: {
      zip: { entry_order: names, entries: zipEntries },
      manifest_sha256: sha(requireEntry(extracted, "AndroidManifest.xml")),
      bootstrap_sha256: sha(requireEntry(extracted, "classes.dex")),
      runtime_normalized: runtime.map(value => ({ name: value.name, sha256: sha(normalizeRuntime(value.bytes)) })),
      container: container.semantic,
    },
    random: {
      output_sha256: sha(bytes),
      container_sha256: sha(payload),
      config_sha256: sha(config),
      build_id: container.random.buildId,
      key_slot_id: container.random.keySlotId,
      nonce_prefixes: container.random.nonces,
      manifest_mac_sha256: container.random.manifestMacHash,
      tag_set_sha256: container.random.tagSetHash,
      ciphertext_sha256: container.random.ciphertextHash,
    },
  };
}

function verifyContainer(container, config, runtimes, report) {
  invariant(container.length >= 160 && config.length === 768, "container/config size");
  invariant(container.subarray(0, 4).toString("ascii") === "AHDC", "AHDC magic");
  invariant(u16(container, 4) === 2 && u16(container, 6) === 0 && u16(container, 8) === 160, "AHDC version");
  const dexCount = u32(container, 12), signerSize = u32(container, 16), recordSize = u32(container, 20);
  const chunkCount = u32(container, 24), chunkSize = u32(container, 28), payloadSize = Number(u64(container, 32));
  invariant(recordSize === dexCount * 128 && chunkSize === chunkCount * 32, "AHDC table size");
  const buildId = container.subarray(40, 56), keySlotId = container.subarray(56, 72);
  invariant(eq(shaBuffer(config), container.subarray(72, 104)), "config digest");
  invariant(config.subarray(0, 4).toString("ascii") === "AHKC" && u16(config, 4) === 2, "config format");
  invariant(eq(buildId, config.subarray(24, 40)) && eq(keySlotId, config.subarray(40, 56)), "config identifiers");
  const signer = config.subarray(56, 88);
  const slots = runtimes.map(runtime => nativeSlot(runtime));
  for (const slot of slots) {
    invariant(eq(slot.subarray(8, 24), keySlotId) && eq(slot.subarray(24, 40), buildId), "Runtime slot binding");
    invariant(eq(shaBuffer(slot.subarray(0, 72)), slot.subarray(72, 104)), "Runtime slot digest");
  }
  const rNative = slots[0].subarray(40, 72);
  invariant(slots.every(slot => eq(rNative, slot.subarray(40, 72))), "Runtime share mismatch");
  const root = xor(rNative, config.subarray(88, 120));
  const packageDigest = shaBuffer(Buffer.from(report.application.package_name, "utf8"));
  const kek = Buffer.from(hkdfSync("sha256", root, buildId, Buffer.concat([Buffer.from("AHDC offline KEK v1"), signer, packageDigest]), 32));
  const cek = decryptGcm(kek, config.subarray(120, 132), config.subarray(0, 132), config.subarray(132, 180));
  const signerOffset = 160, recordOffset = signerOffset + signerSize, chunkOffset = recordOffset + recordSize;
  const payloadOffset = chunkOffset + chunkSize;
  invariant(payloadOffset + payloadSize === container.length, "AHDC total size");
  const spv1 = container.subarray(signerOffset, recordOffset);
  invariant(spv1.subarray(0, 4).toString("ascii") === "SPV1" && eq(spv1.subarray(12, 44), signer), "SPV1 binding");
  const headerZero = Buffer.from(container.subarray(0, 160)); headerZero.fill(0, 104, 136);
  const manifestKey = Buffer.from(hkdfSync("sha256", cek, buildId, Buffer.from("AHDC manifest v2"), 32));
  const computedMac = createHmac("sha256", manifestKey).update(headerZero).update(container.subarray(signerOffset, payloadOffset)).digest();
  invariant(eq(computedMac, container.subarray(104, 136)), "manifest MAC");
  const records = [], nonceValues = [], tags = [], ciphertextParts = [];
  for (let index = 0; index < dexCount; index++) {
    const raw = container.subarray(recordOffset + index * 128, recordOffset + (index + 1) * 128);
    const ordinal = u32(raw, 0), nameLength = u16(raw, 4), name = raw.subarray(48, 48 + nameLength).toString("ascii");
    const originalLength = Number(u64(raw, 8)), compressedLength = Number(u64(raw, 16));
    const count = u32(raw, 24), first = u32(raw, 28), recordPayload = Number(u64(raw, 32));
    const noncePrefix = raw.subarray(40, 48), originalHash = raw.subarray(72, 104);
    invariant(ordinal === index && name === (index === 0 ? "classes.dex" : `classes${index + 1}.dex`), "record order");
    const key = Buffer.from(hkdfSync("sha256", cek, buildId, Buffer.concat([Buffer.from("AHDC record v2"), le32(ordinal)]), 32));
    const compressed = [];
    const topology = [];
    let expectedCompressedOffset = 0;
    let expectedChunkPayload = recordPayload;
    for (let chunkIndex = 0; chunkIndex < count; chunkIndex++) {
      const global = first + chunkIndex;
      const rawChunk = container.subarray(chunkOffset + global * 32, chunkOffset + (global + 1) * 32);
      const plainLength = u32(rawChunk, 24), chunkPayload = Number(u64(rawChunk, 16));
      invariant(u32(rawChunk, 0) === ordinal && u32(rawChunk, 4) === chunkIndex, "chunk order");
      invariant(Number(u64(rawChunk, 8)) === expectedCompressedOffset && chunkPayload === expectedChunkPayload, "chunk topology");
      const encrypted = container.subarray(payloadOffset + chunkPayload, payloadOffset + chunkPayload + plainLength + 16);
      invariant(encrypted.length === plainLength + 16, "chunk payload bounds");
      const nonce = Buffer.concat([noncePrefix, le32(chunkIndex)]);
      const aad = Buffer.concat([Buffer.from("AHDC-GCM-V2"), container.subarray(4, 8), buildId, keySlotId, signer, packageDigest, raw, rawChunk]);
      compressed.push(decryptGcm(key, nonce, aad, encrypted));
      nonceValues.push(nonce.toString("hex")); tags.push(encrypted.subarray(-16)); ciphertextParts.push(encrypted.subarray(0, -16));
      topology.push({ ordinal: chunkIndex, compressed_offset: Number(u64(rawChunk, 8)), payload_offset: chunkPayload, plaintext_length: plainLength });
      expectedCompressedOffset += plainLength;
      expectedChunkPayload += plainLength + 16;
    }
    const compressedBytes = Buffer.concat(compressed);
    invariant(compressedBytes.length === compressedLength, "compressed length");
    const dex = inflateSync(compressedBytes);
    invariant(dex.length === originalLength && eq(shaBuffer(dex), originalHash), "DEX authentication/decompression");
    records.push({ ordinal, name, original_length: originalLength, compressed_length: compressedLength, chunk_count: count, first_chunk_index: first, payload_offset: recordPayload, original_sha256: originalHash.toString("hex"), chunks: topology });
  }
  unique(nonceValues, "container nonce");
  return {
    semantic: { major: 2, minor: 0, record_count: dexCount, chunk_count: chunkCount, signer_lineage_count: u16(spv1, 8), records, authenticated: true, decrypted_dex_verified: true },
    random: { buildId: buildId.toString("hex"), keySlotId: keySlotId.toString("hex"), nonces: nonceValues, manifestMacHash: sha(container.subarray(104, 136)), tagSetHash: sha(Buffer.concat(tags)), ciphertextHash: sha(Buffer.concat(ciphertextParts)) },
  };
}

function parseZip(bytes) {
  const eocd = lastIndexOf(bytes, Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  invariant(eocd >= 0 && eocd + 22 <= bytes.length, "ZIP EOCD");
  const count = u16(bytes, eocd + 10), centralOffset = u32(bytes, eocd + 16);
  const entries = []; let cursor = centralOffset;
  for (let index = 0; index < count; index++) {
    invariant(u32(bytes, cursor) === 0x02014b50, "central signature");
    const flags = u16(bytes, cursor + 8), method = u16(bytes, cursor + 10), dosTime = u16(bytes, cursor + 12), dosDate = u16(bytes, cursor + 14);
    const crc32 = u32(bytes, cursor + 16);
    const compressedSize = u32(bytes, cursor + 20), uncompressedSize = u32(bytes, cursor + 24);
    const nameLength = u16(bytes, cursor + 28), extraLength = u16(bytes, cursor + 30), commentLength = u16(bytes, cursor + 32);
    const internalAttributes = u16(bytes, cursor + 36), externalAttributes = u32(bytes, cursor + 38), localOffset = u32(bytes, cursor + 42);
    const name = bytes.subarray(cursor + 46, cursor + 46 + nameLength).toString(flags & 0x800 ? "utf8" : "ascii");
    invariant(u32(bytes, localOffset) === 0x04034b50, "local signature");
    const dataOffset = localOffset + 30 + u16(bytes, localOffset + 26) + u16(bytes, localOffset + 28);
    invariant(dataOffset + compressedSize <= centralOffset, "ZIP data bounds");
    entries.push({ name, flags, method, dosTime, dosDate, crc32, compressedSize, uncompressedSize, internalAttributes, externalAttributes, dataOffset });
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return { entries, centralOffset };
}

function extract(bytes, entry) {
  const compressed = bytes.subarray(entry.dataOffset, entry.dataOffset + entry.compressedSize);
  const result = entry.method === 0 ? Buffer.from(compressed) : entry.method === 8 ? inflateRawSync(compressed) : fail("unsupported ZIP method");
  invariant(result.length === entry.uncompressedSize, `ZIP size ${entry.name}`); return result;
}

function reportProjection(value) {
  const result = structuredClone(value);
  for (const path of [...classification.report_randomized, ...classification.report_run_metadata]) deleteClassified(result, path);
  return result;
}

function deleteClassified(root, path) {
  const parts = path.split(".");
  function remove(value, index) {
    const part = parts[index], array = part.endsWith("[]"), key = array ? part.slice(0, -2) : part;
    if (index === parts.length - 1) {
      if (array) delete value[key]; else delete value[key];
      return;
    }
    const child = value?.[key];
    if (array) for (const item of child ?? []) remove(item, index + 1);
    else if (child !== undefined) remove(child, index + 1);
  }
  remove(root, 0);
}

function validateSchemaKeys(value, schema, root, path) {
  if (schema.$ref) return validateSchemaKeys(value, pointer(root, schema.$ref), root, path);
  if (schema.anyOf) { invariant(schema.anyOf.some(candidate => { try { validateSchemaKeys(value, candidate, root, path); return true; } catch { return false; } }), `${path}: schema`); return; }
  if (schema.type === "object" || schema.properties) {
    invariant(value && typeof value === "object" && !Array.isArray(value), `${path}: object`);
    const allowed = new Set(Object.keys(schema.properties ?? {}));
    if (schema.additionalProperties === false) for (const key of Object.keys(value)) invariant(allowed.has(key), `${path}: unknown field ${key}`);
    for (const key of schema.required ?? []) invariant(Object.hasOwn(value, key), `${path}: missing ${key}`);
    for (const [key, child] of Object.entries(schema.properties ?? {})) if (Object.hasOwn(value, key)) validateSchemaKeys(value[key], child, root, `${path}.${key}`);
  } else if (schema.type === "array" && Array.isArray(value)) value.forEach((item, index) => validateSchemaKeys(item, schema.items, root, `${path}[${index}]`));
}

function verifyLocalEquivalence(semantics, projections, random) {
  for (const id of fixtureIds()) {
    invariant(canonical(find(semantics, id, "run1"), ["run"]) === canonical(find(semantics, id, "run2"), ["run"]), `${id}: local semantic drift`);
    invariant(canonical(find(projections, id, "run1"), ["run"]) === canonical(find(projections, id, "run2"), ["run"]), `${id}: local report drift`);
    const first = find(random, id, "run1"), second = find(random, id, "run2");
    for (const field of ["output_sha256", "container_sha256", "build_id", "key_slot_id"]) invariant(first[field] !== second[field], `${id}: local ${field} reused`);
  }
  for (const field of ["output_sha256", "container_sha256", "build_id", "key_slot_id"]) unique(random.map(row => row[field]), field);
  unique(random.flatMap(row => row.nonce_prefixes), "nonce_prefix");
}

function verifyNegatives(root, schema) {
  const rows = {};
  for (const name of ["unsigned", "invalid"]) {
    const report = JSON.parse(readFileSync(join(root, "negative", `${name}-report.json`), "utf8"));
    validateSchemaKeys(report, schema, schema, "$");
    const exit = Number(readFileSync(join(root, "negative", `${name}-exit.txt`), "ascii").trim());
    invariant(exit !== 0 && report.result.status !== "success", `${name}: negative accepted`);
    rows[name] = { exit, error_code: report.result.error_code, stage: report.errors[0]?.stage ?? null, partial_output: false };
  }
  writeJson(join(root, "negative-results.json"), { schema_version: 1, cases: rows });
}

function loadSnapshot(root) {
  return {
    semantics: readJsonl(join(root, "semantic-manifests.jsonl")), projections: readJsonl(join(root, "reports.jsonl")),
    random: readJsonl(join(root, "random-fields.jsonl")), negatives: JSON.parse(readFileSync(join(root, "negative-results.json"), "utf8")),
    environment: JSON.parse(readFileSync(join(root, "environment.json"), "utf8")),
  };
}

function scanForPaths(root, names) {
  const pattern = /(?:[A-Za-z]:[\\/]|\/(?:home|Users|runner|tmp)\/)/;
  for (const name of names) invariant(!pattern.test(readFileSync(join(root, name), "utf8")), `${name}: absolute path leak`);
}

function normalizeRuntime(bytes) { const copy = Buffer.from(bytes), offset = copy.indexOf(Buffer.from("AHS1")); invariant(offset >= 0 && offset + 104 <= copy.length, "Runtime slot"); copy.fill(0, offset, offset + 104); return copy; }
function nativeSlot(bytes) { const offset = bytes.indexOf(Buffer.from("AHS1")); invariant(offset >= 0 && offset + 104 <= bytes.length, "native share slot"); return bytes.subarray(offset, offset + 104); }
function decryptGcm(key, nonce, aad, encrypted) { invariant(encrypted.length >= 16, "GCM length"); const cipher = createDecipheriv("aes-256-gcm", key, nonce); cipher.setAAD(aad); cipher.setAuthTag(encrypted.subarray(-16)); return Buffer.concat([cipher.update(encrypted.subarray(0, -16)), cipher.final()]); }
function alignment(name) { if (name === "assets/ah/runtime/payload.ahdc" || name === "assets/ah/runtime/config.bin" || /^lib\/.+\/libah_runtime\.so$/.test(name)) return 4096; return 1; }
function fixtureIds() { return ["java-single-dex", "kotlin-single-dex", "kotlin-multidex", "custom-application", "custom-factory", "startup-provider", "multi-process", "jni-four-abi", "jni-arm-only"]; }
function requireEntry(map, name) { const value = map.get(name); invariant(value, `missing ${name}`); return value; }
function find(rows, fixture_id, run) { const values = rows.filter(row => row.fixture_id === fixture_id && row.run === run); invariant(values.length === 1, `${fixture_id}/${run}: row count`); return values[0]; }
function unique(values, field) { invariant(new Set(values).size === values.length, `${field}: reuse`); }
function canonical(value, omitted = []) { const clone = structuredClone(value); for (const key of omitted) delete clone[key]; return JSON.stringify(clone); }
function pointer(root, ref) { invariant(ref.startsWith("#/"), "external schema ref"); return ref.slice(2).split("/").reduce((value, key) => value[key.replaceAll("~1", "/").replaceAll("~0", "~")], root); }
function readJsonl(path) { return readFileSync(path, "utf8").trim().split("\n").filter(Boolean).map(line => JSON.parse(line)); }
function writeJsonl(path, rows) { writeFileSync(path, rows.map(JSON.stringify).join("\n") + "\n", "utf8"); }
function writeJson(path, value) { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, JSON.stringify(value, null, 2) + "\n", "utf8"); }
function repo(path) { return resolve(dirname(fileURLToPath(import.meta.url)), "../..", path); }
function sha(bytes) { return createHash("sha256").update(bytes).digest("hex"); }
function shaBuffer(bytes) { return createHash("sha256").update(bytes).digest(); }
function xor(left, right) { invariant(left.length === right.length, "xor length"); return Buffer.from(left.map((value, index) => value ^ right[index])); }
function eq(left, right) { return left.length === right.length && timingSafeEqual(left, right); }
function le32(value) { const result = Buffer.alloc(4); result.writeUInt32LE(value); return result; }
function u16(bytes, offset) { invariant(offset >= 0 && offset + 2 <= bytes.length, "u16"); return bytes.readUInt16LE(offset); }
function u32(bytes, offset) { invariant(offset >= 0 && offset + 4 <= bytes.length, "u32"); return bytes.readUInt32LE(offset); }
function u64(bytes, offset) { invariant(offset >= 0 && offset + 8 <= bytes.length, "u64"); return bytes.readBigUInt64LE(offset); }
function lastIndexOf(bytes, needle) { for (let i = bytes.length - needle.length; i >= 0; i--) if (bytes.subarray(i, i + needle.length).equals(needle)) return i; return -1; }
function invariant(condition, message) { if (!condition) fail(message); }
function fail(message) { throw new Error(`M3-03 equivalence failure: ${message}`); }
function selfTest() { const schema = JSON.parse(readFileSync(repo("docs/specs/report-v1.schema.json"), "utf8")); let rejected = false; try { validateSchemaKeys({ unexpected: true }, schema, schema, "$"); } catch { rejected = true; } invariant(rejected, "unknown-field self-test"); process.stdout.write("M3-03 comparator self-test PASS\n"); }
