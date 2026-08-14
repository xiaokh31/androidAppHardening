#!/usr/bin/env node

import { readFileSync, writeFileSync } from "node:fs";
import process from "node:process";

const output = "tools/validation/src/tamper/resources/catalog.yaml";
const fields = [
  "id", "target", "mutation", "expectedStage", "expectedCode", "payloadLoaded",
  "payloadClassLookupAttempted", "nativeHandleAcquired", "loadedPayloadPublished",
  "verifiedPayloadSessionPublished", "byteBuffersPublished", "nativeCloseCount",
  "partialJavaReferencesCleared", "partialGuardReferencesCleared",
  "completedMappingsZeroizedUnmapped", "partialMappingZeroizedUnmapped",
  "primaryCodePreserved", "cleanupFailureSuppressed",
];

function host(id, target, mutation, stage, code) {
  return {
    id, target, mutation, expectedStage: stage, expectedCode: code, payloadLoaded: false,
    ...Object.fromEntries(fields.slice(6).map((field) => [field, "not_applicable"])),
  };
}

function pre(id, mutation, stage, code, mapping = false) {
  return {
    id, target: "runtime-prehandle", mutation, expectedStage: stage, expectedCode: code,
    payloadLoaded: false, payloadClassLookupAttempted: false, nativeHandleAcquired: false,
    loadedPayloadPublished: false, verifiedPayloadSessionPublished: false,
    byteBuffersPublished: false, nativeCloseCount: 0,
    partialJavaReferencesCleared: "not_applicable", partialGuardReferencesCleared: "not_applicable",
    completedMappingsZeroizedUnmapped: "not_applicable",
    partialMappingZeroizedUnmapped: mapping ? true : "not_applicable",
    primaryCodePreserved: true, cleanupFailureSuppressed: false,
  };
}

function post(id, mutation, stage, code, cleanup = false) {
  return {
    id, target: "runtime-posthandle", mutation, expectedStage: stage, expectedCode: code,
    payloadLoaded: false, payloadClassLookupAttempted: false, nativeHandleAcquired: true,
    loadedPayloadPublished: false, verifiedPayloadSessionPublished: false,
    byteBuffersPublished: false, nativeCloseCount: 1, partialJavaReferencesCleared: true,
    partialGuardReferencesCleared: "not_applicable", completedMappingsZeroizedUnmapped: true,
    partialMappingZeroizedUnmapped: true, primaryCodePreserved: true,
    cleanupFailureSuppressed: cleanup,
  };
}

function guard(id, mutation, stage, code, cleanup = false) {
  return {
    id, target: "guard", mutation, expectedStage: stage, expectedCode: code,
    payloadLoaded: false, payloadClassLookupAttempted: false, nativeHandleAcquired: true,
    loadedPayloadPublished: true, verifiedPayloadSessionPublished: false,
    byteBuffersPublished: true, nativeCloseCount: 1,
    partialJavaReferencesCleared: "not_applicable", partialGuardReferencesCleared: true,
    completedMappingsZeroizedUnmapped: true, partialMappingZeroizedUnmapped: "not_applicable",
    primaryCodePreserved: true, cleanupFailureSuppressed: cleanup,
  };
}

const cases = [
  host("m302-apk-truncated-cd", "apk", "truncate_tail", "INSPECT", "INPUT_ZIP_STRUCTURE"),
  host("m302-apk-duplicate-entry", "apk", "duplicate_entry", "INSPECT", "INPUT_DUPLICATE_ENTRY"),
  host("m302-apk-path-traversal", "apk", "path_traversal", "INSPECT", "INPUT_PATH_UNSAFE"),
  host("m302-apk-inflate-limit", "apk", "inflate_size_limit", "INSPECT", "INPUT_LIMIT_EXCEEDED"),
  host("m302-axml-chunk-size", "axml", "chunk_size_overflow", "MANIFEST", "AXML_MALFORMED"),
  host("m302-axml-string-length", "axml", "string_length_overflow", "MANIFEST", "AXML_MALFORMED"),
  host("m302-axml-resource-map", "axml", "resource_map_collision", "MANIFEST", "AXML_RESERVED_COLLISION"),
  host("m302-axml-nesting", "axml", "nesting_overflow", "MANIFEST", "AXML_LIMIT_EXCEEDED"),
  host("m302-container-major", "container-host", "header_version", "CONTAINER_HEADER", "CONTAINER_VERSION"),
  host("m302-container-header-length", "container-host", "header_length", "CONTAINER_HEADER", "CONTAINER_FORMAT"),
  host("m302-container-record-overlap", "container-host", "record_overlap", "CONTAINER_RECORDS", "CONTAINER_FORMAT"),
  host("m302-container-chunk-order", "container-host", "chunk_order", "CONTAINER_CHUNKS", "CONTAINER_FORMAT"),
  pre("m302-runtime-different-signer", "signer_flip", "SIGNER_POLICY", "AAH-RUNTIME-INTEGRITY-SIGNER_MISMATCH"),
  pre("m302-runtime-config-version", "config_version", "NATIVE_CONFIG", "AAH-RUNTIME-INTEGRITY-CONTAINER"),
  pre("m302-runtime-factory-flags", "factory_flags", "NATIVE_CONFIG", "AAH-RUNTIME-INTEGRITY-CONTAINER"),
  pre("m302-runtime-binding-slot", "binding_slot", "NATIVE_BINDING", "AAH-RUNTIME-INTEGRITY-CONTAINER"),
  pre("m302-runtime-container", "container_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
  pre("m302-runtime-nonce", "nonce_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
  pre("m302-runtime-tag-first", "tag_first_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
  pre("m302-runtime-tag-middle", "tag_middle_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
  pre("m302-runtime-tag-last", "tag_last_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
  pre("m302-runtime-ciphertext-first", "ciphertext_first_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
  pre("m302-runtime-ciphertext-middle", "ciphertext_middle_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
  pre("m302-runtime-ciphertext-last", "ciphertext_last_flip", "NATIVE_AUTH", "AAH-RUNTIME-INTEGRITY-CONTAINER", true),
];

const openStages = [
  "native-handle", "metadata-bytes", "metadata-parse", "metadata-object", "buffer-array",
  "buffer-element", "search-path", "class-loader", "loaded-payload", "before-return",
];
for (const stage of openStages) {
  cases.push(post(`m302-open-${stage}-exception`, `open_${stage.replaceAll("-", "_")}_exception`,
    `OPEN_${stage.replaceAll("-", "_").toUpperCase()}`, "SYNTHETIC_ILLEGAL_STATE"));
  cases.push(post(`m302-open-${stage}-oom`, `open_${stage.replaceAll("-", "_")}_oom`,
    `OPEN_${stage.replaceAll("-", "_").toUpperCase()}`, "JAVA_OOM"));
}
cases.push(post("m302-open-cleanup-aggregate", "jni_cleanup_failure", "JNI_ROLLBACK",
  "SYNTHETIC_JNI_PRIMARY", true));

const guardStages = ["loaded-payload", "metadata", "identity", "configuration", "session", "before-return"];
for (const stage of guardStages) {
  const cleanup = stage === "before-return";
  const mutation = `guard_${stage.replaceAll("-", "_")}${cleanup ? "_cleanup" : ""}`;
  cases.push(guard(`m302-guard-${stage}-exception`, `${mutation}_exception`,
    `GUARD_${stage.replaceAll("-", "_").toUpperCase()}`, "AAH-RUNTIME-INTEGRITY-CONTAINER",
    cleanup));
  cases.push(guard(`m302-guard-${stage}-oom`, `${mutation}_oom`,
    `GUARD_${stage.replaceAll("-", "_").toUpperCase()}`, "JAVA_OOM", cleanup));
}

for (const [name, code] of [
  ["package", "AAH-RUNTIME-INTEGRITY-PACKAGE_MISMATCH"],
  ["current-signer", "AAH-RUNTIME-INTEGRITY-SIGNER_MISMATCH"],
  ["empty-lineage", "AAH-RUNTIME-INTEGRITY-LINEAGE_MISMATCH"],
  ["lineage-order", "AAH-RUNTIME-INTEGRITY-LINEAGE_MISMATCH"],
  ["container-major", "AAH-RUNTIME-INTEGRITY-VERSION"],
  ["container-minor", "AAH-RUNTIME-INTEGRITY-VERSION"],
  ["signer-version", "AAH-RUNTIME-INTEGRITY-VERSION"],
  ["risk-version", "AAH-RUNTIME-INTEGRITY-VERSION"],
  ["build-snapshot", "AAH-RUNTIME-INTEGRITY-SNAPSHOT_CHANGED"],
  ["key-snapshot", "AAH-RUNTIME-INTEGRITY-SNAPSHOT_CHANGED"],
  ["cross-handle", "AAH-RUNTIME-INTEGRITY-METADATA_HANDLE"],
  ["cross-session", "AAH-RUNTIME-INTEGRITY-METADATA_HANDLE"],
]) {
  cases.push(guard(`m302-metadata-${name}`, `metadata_${name.replaceAll("-", "_")}`,
    "GUARD_METADATA", code));
}

const serialized = [
  "# Generated by tools/validation/generate-m3-02-tamper-catalog.mjs. Synthetic fixtures only.",
  "cases:",
  ...cases.flatMap((entry) => fields.map((field, index) =>
    `${index === 0 ? "  - " : "    "}${field}: ${entry[field]}`)),
  "",
].join("\n");

if (process.argv[2] === "--write") {
  writeFileSync(output, serialized);
} else if (process.argv[2] === "--check") {
  if (readFileSync(output, "utf8").replaceAll("\r\n", "\n") !== serialized) {
    throw new Error("M3-02 tamper catalog differs from the versioned generator");
  }
} else {
  throw new Error("usage: --write|--check");
}
process.stdout.write(`OK: M3-02 tamper catalog cases=${cases.length}\n`);
