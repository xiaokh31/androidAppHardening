#!/usr/bin/env node

import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawnSync } from "node:child_process";
import { readEntries } from "./create-m0-04-tampered-apks.mjs";

const ABI = [
  { name: "armeabi-v7a", elfClass: 1, machine: 40, abiId: 1 },
  { name: "arm64-v8a", elfClass: 2, machine: 183, abiId: 2 },
  { name: "x86", elfClass: 1, machine: 3, abiId: 3 },
  { name: "x86_64", elfClass: 2, machine: 62, abiId: 4 },
];
const JNI_EXPORTS = [
  "Java_ah_runtime_loader_NativePayloadBridge_nativeAuthenticatedMetadata",
  "Java_ah_runtime_loader_NativePayloadBridge_nativeApplyMemoryProfile",
  "Java_ah_runtime_loader_NativePayloadBridge_nativeClosePayload",
  "Java_ah_runtime_loader_NativePayloadBridge_nativeDexBuffers",
  "Java_ah_runtime_loader_NativePayloadBridge_nativeInspectBinding",
  "Java_ah_runtime_loader_NativePayloadBridge_nativeOpenVerifiedPayload",
  "Java_ah_runtime_risk_NativeRiskSignals_collect",
].sort();
const PT_LOAD = 1;
const PT_GNU_STACK = 0x6474e551;
const PT_GNU_RELRO = 0x6474e552;
const SHF_WRITE = 1n;
const SHF_ALLOC = 2n;
const PF_X = 1;
const PF_W = 2;

function fail(message) {
  throw new Error(`M2-04 four-ABI verification failed: ${message}`);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function args() {
  const values = new Map();
  let selfTest = false;
  for (const argument of process.argv.slice(2)) {
    if (argument === "--self-test") selfTest = true;
    else if (argument.startsWith("--report=")) values.set("report", argument.slice(9));
    else fail(`unknown argument ${argument}`);
  }
  return { selfTest, report: path.resolve(values.get("report") ?? "build/m2-04/native-runtime.json") };
}

function unsigned64(buffer, offset) {
  const value = buffer.readBigUInt64LE(offset);
  if (value > BigInt(Number.MAX_SAFE_INTEGER)) fail("ELF offset exceeds safe integer range");
  return Number(value);
}

function cString(buffer, offset) {
  if (offset < 0 || offset >= buffer.length) fail("ELF section-name offset is out of bounds");
  const end = buffer.indexOf(0, offset);
  if (end < 0) fail("ELF section-name string is unterminated");
  return buffer.subarray(offset, end).toString("ascii");
}

function elfLayout(buffer) {
  if (buffer.length < 64 || !buffer.subarray(0, 4).equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46]))) {
    fail("ELF header is missing");
  }
  const elfClass = buffer[4];
  if (![1, 2].includes(elfClass) || buffer[5] !== 1 || buffer[6] !== 1) {
    fail("ELF class/data/version is unsupported");
  }
  const is64 = elfClass === 2;
  const phoff = is64 ? unsigned64(buffer, 32) : buffer.readUInt32LE(28);
  const shoff = is64 ? unsigned64(buffer, 40) : buffer.readUInt32LE(32);
  const phentsize = buffer.readUInt16LE(is64 ? 54 : 42);
  const phnum = buffer.readUInt16LE(is64 ? 56 : 44);
  const shentsize = buffer.readUInt16LE(is64 ? 58 : 46);
  const shnum = buffer.readUInt16LE(is64 ? 60 : 48);
  const shstrndx = buffer.readUInt16LE(is64 ? 62 : 50);
  if (phnum === 0 || shnum === 0 || shstrndx >= shnum ||
      phoff + phentsize * phnum > buffer.length || shoff + shentsize * shnum > buffer.length) {
    fail("ELF table bounds are invalid");
  }
  return { elfClass, is64, phoff, phentsize, phnum, shoff, shentsize, shnum, shstrndx };
}

function section(buffer, layout, index) {
  const headerOffset = layout.shoff + layout.shentsize * index;
  return {
    headerOffset,
    nameOffset: buffer.readUInt32LE(headerOffset),
    type: buffer.readUInt32LE(headerOffset + 4),
    flags: layout.is64 ? buffer.readBigUInt64LE(headerOffset + 8) : BigInt(buffer.readUInt32LE(headerOffset + 8)),
    offset: layout.is64 ? unsigned64(buffer, headerOffset + 24) : buffer.readUInt32LE(headerOffset + 16),
    size: layout.is64 ? unsigned64(buffer, headerOffset + 32) : buffer.readUInt32LE(headerOffset + 20),
  };
}

function verifyElfBuffer(buffer, expected) {
  const layout = elfLayout(buffer);
  if (layout.elfClass !== expected.elfClass || buffer.readUInt16LE(18) !== expected.machine) {
    fail(`${expected.name} ELF class/machine mismatch`);
  }
  const nameTable = section(buffer, layout, layout.shstrndx);
  if (nameTable.offset + nameTable.size > buffer.length) fail("ELF section-name table is out of bounds");
  const sections = [];
  for (let index = 0; index < layout.shnum; index += 1) {
    const current = section(buffer, layout, index);
    current.name = cString(buffer, nameTable.offset + current.nameOffset);
    if (current.type !== 8 && current.offset + current.size > buffer.length) {
      fail(`ELF section ${current.name} is out of bounds`);
    }
    sections.push(current);
  }
  const shares = sections.filter((value) => value.name === ".ah_share_v1");
  if (shares.length !== 1) fail(`${expected.name} must contain exactly one .ah_share_v1`);
  const share = shares[0];
  if (share.size !== 104 || (share.flags & SHF_ALLOC) === 0n || (share.flags & SHF_WRITE) !== 0n) {
    fail(`${expected.name} share section flags/size mismatch`);
  }
  const slot = buffer.subarray(share.offset, share.offset + share.size);
  if (slot.subarray(0, 4).toString("ascii") !== "AHP0" ||
      slot.readUInt16LE(4) !== 1 || slot.readUInt16LE(6) !== expected.abiId ||
      slot.subarray(8).some((value) => value !== 0)) {
    fail(`${expected.name} share placeholder mismatch`);
  }

  let stack = null;
  let relro = false;
  const loads = [];
  for (let index = 0; index < layout.phnum; index += 1) {
    const headerOffset = layout.phoff + layout.phentsize * index;
    const type = buffer.readUInt32LE(headerOffset);
    const flagsOffset = headerOffset + (layout.is64 ? 4 : 24);
    const flags = buffer.readUInt32LE(flagsOffset);
    if (type === PT_LOAD) {
      const offset = layout.is64 ? unsigned64(buffer, headerOffset + 8) : buffer.readUInt32LE(headerOffset + 4);
      const fileSize = layout.is64 ? unsigned64(buffer, headerOffset + 32) : buffer.readUInt32LE(headerOffset + 16);
      if (offset + fileSize > buffer.length) fail(`${expected.name} PT_LOAD exceeds file bounds`);
      loads.push({ offset, fileSize, flags, flagsOffset });
    }
    if (type === PT_GNU_STACK) stack = { flags, flagsOffset };
    if (type === PT_GNU_RELRO) relro = true;
  }
  if (stack === null || (stack.flags & PF_X) !== 0 || !relro) {
    fail(`${expected.name} GNU_STACK/RELRO contract mismatch`);
  }
  const shareLoads = loads.filter((load) => share.offset >= load.offset &&
    share.offset + share.size <= load.offset + load.fileSize);
  if (shareLoads.length !== 1 || (shareLoads[0].flags & PF_W) !== 0) {
    fail(`${expected.name} share section must be covered by one read-only PT_LOAD`);
  }
  return { layout, share, shareLoad: shareLoads[0], stack, relro };
}

function executable(name) {
  const suffix = process.platform === "win32" ? ".exe" : "";
  const sdkCandidates = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    path.resolve(".toolchains/android-m0-04/sdk"),
  ].filter(Boolean);
  for (const sdk of sdkCandidates) {
    const prebuilt = process.platform === "win32" ? "windows-x86_64" : "linux-x86_64";
    const candidate = path.join(sdk, "ndk", "29.0.14206865", "toolchains", "llvm", "prebuilt", prebuilt, "bin", `${name}${suffix}`);
    if (existsSync(candidate)) return candidate;
  }
  fail(`pinned NDK tool ${name} is unavailable`);
}

function run(command, commandArgs) {
  const result = spawnSync(command, commandArgs, {
    encoding: "utf8",
    maxBuffer: 32 * 1024 * 1024,
    windowsHide: true,
  });
  if (result.error || result.status !== 0) {
    fail(`${path.basename(command)} failed (${result.status}): ${result.stderr ?? result.error}`);
  }
  return `${result.stdout ?? ""}${result.stderr ?? ""}`;
}

function dynamicExports(nm, file) {
  const output = run(nm, ["-D", "--defined-only", "--format=posix", file]);
  return output.split(/\r?\n/u).map((line) => line.trim()).filter(Boolean)
    .map((line) => line.split(/\s+/u)[0].replace(/@.*$/u, "")).sort();
}

function requireExactExports(exports, label) {
  if (exports.length !== JNI_EXPORTS.length || exports.some((value, index) => value !== JNI_EXPORTS[index])) {
    fail(`${label} dynamic exports differ from the approved JNI whitelist: ${exports.join(",")}`);
  }
}

function walk(root) {
  const output = [];
  if (!existsSync(root)) return output;
  for (const name of readdirSync(root)) {
    const value = path.join(root, name);
    if (statSync(value).isDirectory()) output.push(...walk(value));
    else output.push(value);
  }
  return output;
}

function sourceArchitectureScan() {
  const sources = walk(path.resolve("runtime/native/src/main/cpp"))
    .filter((file) => /\.(?:c|cc|cpp|h|hpp)$/u.test(file));
  const forbidden = [];
  for (const file of sources) {
    if (path.basename(file) === "native_share_slot.cpp") continue;
    const text = readFileSync(file, "utf8");
    if (/__arm__|__aarch64__|__i386__|__x86_64__|ANDROID_ABI/u.test(text)) {
      forbidden.push(path.relative(process.cwd(), file).replaceAll("\\", "/"));
    }
  }
  if (forbidden.length !== 0) fail(`architecture-specific business branch found: ${forbidden.join(",")}`);
}

function verifyArchive() {
  const aar = path.resolve("runtime/native/build/outputs/aar/native-release.aar");
  if (!existsSync(aar)) fail("Release AAR is missing");
  const bytes = readFileSync(aar);
  const runtimeEntries = readEntries(bytes).map((entry) => entry.name)
    .filter((name) => name.endsWith("/libah_runtime.so"));
  const expected = ABI.map((abi) => `jni/${abi.name}/libah_runtime.so`).sort();
  if (runtimeEntries.length !== 4 || runtimeEntries.sort().some((value, index) => value !== expected[index])) {
    fail(`AAR Runtime entries mismatch: ${runtimeEntries.join(",")}`);
  }
  const symbolRoot = path.resolve("runtime/native/build/outputs/native-debug-symbols/release");
  const archives = walk(symbolRoot).filter((value) => value.endsWith(".zip"));
  if (archives.length !== 1) fail(`expected one Native debug symbol archive, found ${archives.length}`);
  const symbolBytes = readFileSync(archives[0]);
  const symbolNames = readEntries(symbolBytes).map((entry) => entry.name);
  for (const abi of ABI) {
    if (!symbolNames.some((name) => name.includes(`/${abi.name}/`) && name.endsWith("libah_runtime.so"))) {
      fail(`debug symbol archive is missing ${abi.name}`);
    }
  }
  return {
    aar: { path: path.relative(process.cwd(), aar).replaceAll("\\", "/"), bytes: bytes.length, sha256: sha256(bytes) },
    debug_symbols: {
      path: path.relative(process.cwd(), archives[0]).replaceAll("\\", "/"),
      bytes: symbolBytes.length,
      sha256: sha256(symbolBytes),
    },
  };
}

function selfTest(source, expected, metadata) {
  const machine = Buffer.from(source);
  machine.writeUInt16LE(expected.machine === 3 ? 40 : 3, 18);
  expectFailure(() => verifyElfBuffer(machine, expected), "machine mutation");
  const slot = Buffer.from(source);
  slot[metadata.share.offset] ^= 1;
  expectFailure(() => verifyElfBuffer(slot, expected), "slot mutation");
  const writable = Buffer.from(source);
  const flagOffset = metadata.share.headerOffset + 8;
  if (metadata.layout.is64) writable.writeBigUInt64LE(metadata.share.flags | SHF_WRITE, flagOffset);
  else writable.writeUInt32LE(Number(metadata.share.flags | SHF_WRITE), flagOffset);
  expectFailure(() => verifyElfBuffer(writable, expected), "writable share mutation");
  const executableStack = Buffer.from(source);
  executableStack.writeUInt32LE(metadata.stack.flags | PF_X, metadata.stack.flagsOffset);
  expectFailure(() => verifyElfBuffer(executableStack, expected), "executable stack mutation");
  const writableLoad = Buffer.from(source);
  writableLoad.writeUInt32LE(metadata.shareLoad.flags | PF_W, metadata.shareLoad.flagsOffset);
  expectFailure(() => verifyElfBuffer(writableLoad, expected), "writable share PT_LOAD mutation");
  expectFailure(() => requireExactExports([...JNI_EXPORTS, "unexpected_export"].sort(), expected.name),
    "export mutation");
}

function expectFailure(action, label) {
  try {
    action();
    fail(`negative self-test accepted ${label}`);
  } catch (error) {
    if (!String(error).includes("M2-04 four-ABI verification failed")) throw error;
  }
}

function main() {
  const options = args();
  const readelf = executable("llvm-readelf");
  const nm = executable("llvm-nm");
  const clang = executable("clang++");
  sourceArchitectureScan();
  const report = {
    task_id: "M2-04",
    validation_mode: "pre-cli",
    ndk: "29.0.14206865",
    toolchain: {
      clang: run(clang, ["--version"]).split(/\r?\n/u).find(Boolean),
      llvm_readelf: run(readelf, ["--version"]).split(/\r?\n/u)
        .find((line) => /LLVM version/u.test(line))?.trim(),
    },
    jni_exports: JNI_EXPORTS,
    runtime: [],
    artifacts: verifyArchive(),
    negative_self_test: options.selfTest,
    result: "PASS",
  };
  for (const expected of ABI) {
    const file = path.resolve("runtime/native/build/intermediates/stripped_native_libs/release/out/lib",
      expected.name, "libah_runtime.so");
    if (!existsSync(file)) fail(`fixed Runtime template is missing for ${expected.name}`);
    const bytes = readFileSync(file);
    const metadata = verifyElfBuffer(bytes, expected);
    const dynamic = run(readelf, ["-d", file]);
    if (!/BIND_NOW/u.test(dynamic)) fail(`${expected.name} is missing BIND_NOW`);
    const exports = dynamicExports(nm, file);
    requireExactExports(exports, expected.name);
    const unstrippedCandidates = walk(path.resolve("runtime/native/build/intermediates/cxx/RelWithDebInfo"))
      .filter((value) => value.replaceAll("\\", "/").endsWith(`/obj/${expected.name}/libah_runtime.so`));
    if (unstrippedCandidates.length === 0) fail(`unstripped Runtime is missing for ${expected.name}`);
    const unstrippedHashes = [...new Set(unstrippedCandidates.map((value) => sha256(readFileSync(value))))];
    if (unstrippedHashes.length !== 1) fail(`ambiguous unstripped Runtime bytes for ${expected.name}`);
    if (options.selfTest) selfTest(bytes, expected, metadata);
    report.runtime.push({
      abi: expected.name,
      abi_id: expected.abiId,
      elf_class: expected.elfClass === 1 ? 32 : 64,
      elf_machine: expected.machine,
      bytes: bytes.length,
      sha256: sha256(bytes),
      unstripped_sha256: unstrippedHashes[0],
      relro: true,
      bind_now: true,
      executable_stack: false,
      share_section: { name: ".ah_share_v1", bytes: 104, alloc: true, writable: false },
      exports,
    });
  }
  mkdirSync(path.dirname(options.report), { recursive: true });
  writeFileSync(options.report, `${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`M2-04 four-ABI Runtime verification PASS report=${path.relative(process.cwd(), options.report)}\n`);
}

try {
  main();
} catch (error) {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
}
