#!/usr/bin/env node
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { basename, dirname, resolve } from "node:path";
import { mkdirSync } from "node:fs";

const SHA1 = /^[0-9a-f]{40}$/u;
const FIRST_PARTY_GROUP = "io.github.xiaokh31.androidapphardening";
const PRODUCT_PROJECT_PATHS = new Set([
  ":host:apk-inspector",
  ":host:axml",
  ":host:cli",
  ":host:container",
  ":host:repacker",
  ":runtime:bootstrap",
  ":runtime:native",
  ":runtime:policy",
]);

function fail(message) {
  throw new Error(message);
}

function parseArgs(argv) {
  if (argv.length !== 8) fail("usage: --raw <path> --implementation-freeze <sha> --output <path> --report <path>");
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    if (!["--raw", "--implementation-freeze", "--output", "--report"].includes(key) || values.has(key)) {
      fail("invalid or duplicate argument");
    }
    values.set(key, argv[index + 1]);
  }
  return Object.fromEntries(values);
}

export function parseStrictJson(bytes) {
  if (!Buffer.isBuffer(bytes) || bytes.length === 0) fail("JSON must be non-empty UTF-8");
  if (bytes.subarray(0, 3).equals(Buffer.from([0xef, 0xbb, 0xbf]))) fail("JSON BOM is forbidden");
  const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  let index = 0;
  const whitespace = () => { while (/[\u0009\u000a\u000d\u0020]/u.test(text[index] ?? "")) index += 1; };
  const token = (expected) => {
    if (!text.startsWith(expected, index)) fail(`expected ${expected}`);
    index += expected.length;
  };
  const string = () => {
    const start = index;
    token('"');
    let escaped = false;
    while (index < text.length) {
      const code = text.charCodeAt(index);
      if (!escaped && code === 0x22) {
        index += 1;
        const value = JSON.parse(text.slice(start, index));
        for (let offset = 0; offset < value.length; offset += 1) {
          const current = value.charCodeAt(offset);
          if (current >= 0xd800 && current <= 0xdbff) {
            const next = value.charCodeAt(++offset);
            if (!(next >= 0xdc00 && next <= 0xdfff)) fail("unpaired JSON surrogate");
          } else if (current >= 0xdc00 && current <= 0xdfff) {
            fail("unpaired JSON surrogate");
          }
        }
        return value;
      }
      if (!escaped && code < 0x20) fail("unescaped JSON control character");
      if (!escaped && code === 0x5c) {
        escaped = true;
      } else {
        escaped = false;
      }
      index += 1;
    }
    fail("unterminated JSON string");
  };
  const value = () => {
    whitespace();
    const current = text[index];
    if (current === '"') return string();
    if (current === "{") {
      index += 1;
      whitespace();
      const result = Object.create(null);
      const keys = new Set();
      if (text[index] === "}") { index += 1; return result; }
      while (true) {
        if (text[index] !== '"') fail("object key must be a string");
        const key = string();
        if (keys.has(key)) fail(`duplicate JSON key: ${key}`);
        keys.add(key);
        whitespace();
        token(":");
        result[key] = value();
        whitespace();
        if (text[index] === "}") { index += 1; return result; }
        token(",");
        whitespace();
      }
    }
    if (current === "[") {
      index += 1;
      whitespace();
      const result = [];
      if (text[index] === "]") { index += 1; return result; }
      while (true) {
        result.push(value());
        whitespace();
        if (text[index] === "]") { index += 1; return result; }
        token(",");
      }
    }
    for (const [literal, result] of [["true", true], ["false", false], ["null", null]]) {
      if (text.startsWith(literal, index)) { index += literal.length; return result; }
    }
    const match = text.slice(index).match(/^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/u);
    if (match === null) fail("invalid JSON value");
    index += match[0].length;
    const number = Number(match[0]);
    if (!Number.isFinite(number)) fail("non-finite JSON number");
    return number;
  };
  const result = value();
  whitespace();
  if (index !== text.length) fail("trailing JSON bytes");
  return result;
}

function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function canonicalJson(value) {
  if (value === null || typeof value === "boolean" || typeof value === "string") return JSON.stringify(value);
  if (typeof value === "number") {
    if (!Number.isFinite(value)) fail("non-finite JSON number");
    return JSON.stringify(Object.is(value, -0) ? 0 : value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(",")}]`;
  if (!isObject(value)) fail("unsupported JSON value");
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(",")}}`;
}

function validateBom(root) {
  if (!isObject(root) || root.bomFormat !== "CycloneDX" || root.specVersion !== "1.6" || !Number.isInteger(root.version)) {
    fail("raw BOM is not CycloneDX JSON 1.6");
  }
  if (Object.hasOwn(root, "serialNumber")) fail("BOM serialNumber is forbidden");
  if (!isObject(root.metadata) || typeof root.metadata.timestamp !== "string" || !isObject(root.metadata.component)) {
    fail("BOM metadata timestamp/component is required");
  }
  if (Object.hasOwn(root.metadata, "buildSystem")) fail("BOM build-system metadata is forbidden");
  if (!Array.isArray(root.components) || !Array.isArray(root.dependencies)) fail("BOM component/dependency arrays are required");
  const rootRef = root.metadata.component["bom-ref"];
  if (typeof rootRef !== "string" || rootRef.length === 0) fail("root component bom-ref is required");
  const rootComponent = root.metadata.component;
  if (rootComponent.type !== "application" || rootComponent.group !== FIRST_PARTY_GROUP ||
      rootComponent.name !== "android-app-hardening" || rootComponent.version !== "0.2.0" ||
      rootComponent.purl !== rootRef || projectPath(rootComponent) !== ":") {
    fail("root product component identity mismatch");
  }
  const refs = new Set([rootRef]);
  const componentRefs = new Set();
  const productPaths = new Set();
  for (const component of root.components) {
    if (!isObject(component) || typeof component["bom-ref"] !== "string" || component["bom-ref"].length === 0) {
      fail("every component needs a bom-ref");
    }
    if (refs.has(component["bom-ref"])) fail("duplicate BOM bom-ref");
    refs.add(component["bom-ref"]);
    componentRefs.add(component["bom-ref"]);
    if (component.group === FIRST_PARTY_GROUP) {
      const path = projectPath(component);
      if (component.type !== "library" || component.version !== "0.2.0" || component.purl !== component["bom-ref"] ||
          !PRODUCT_PROJECT_PATHS.has(path) || productPaths.has(path)) {
        fail("non-product or duplicate first-party component");
      }
      productPaths.add(path);
    }
  }
  if (productPaths.size !== PRODUCT_PROJECT_PATHS.size ||
      [...PRODUCT_PROJECT_PATHS].some((path) => !productPaths.has(path))) fail("product component set is incomplete");
  const dependencyRefs = new Set();
  for (const dependency of root.dependencies) {
    if (!isObject(dependency) || typeof dependency.ref !== "string" || !Array.isArray(dependency.dependsOn)) {
      fail("invalid dependency record");
    }
    if (!refs.has(dependency.ref) || dependencyRefs.has(dependency.ref)) fail("unknown or duplicate dependency ref");
    dependencyRefs.add(dependency.ref);
    for (const child of dependency.dependsOn) if (!refs.has(child)) fail("unknown dependency edge");
  }
  for (const componentRef of componentRefs) {
    if (!dependencyRefs.has(componentRef)) fail("component dependency record is missing");
  }
}

function projectPath(component) {
  const match = /[?&]project_path=([^&]+)/u.exec(component["bom-ref"]);
  if (match === null) return null;
  try {
    return decodeURIComponent(match[1]);
  } catch {
    fail("invalid project_path encoding");
  }
}

function diffPaths(left, right, path = "") {
  if (Object.is(left, right)) return [];
  if (Array.isArray(left) && Array.isArray(right)) {
    if (left.length !== right.length) return [path || "/"];
    return left.flatMap((item, index) => diffPaths(item, right[index], `${path}/${index}`));
  }
  if (isObject(left) && isObject(right)) {
    const keys = [...new Set([...Object.keys(left), ...Object.keys(right)])].sort();
    return keys.flatMap((key) => Object.hasOwn(left, key) && Object.hasOwn(right, key)
      ? diffPaths(left[key], right[key], `${path}/${key.replaceAll("~", "~0").replaceAll("/", "~1")}`)
      : [`${path}/${key.replaceAll("~", "~0").replaceAll("/", "~1")}`]);
  }
  return [path || "/"];
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function freezeTimestamp(commit) {
  if (!SHA1.test(commit)) fail("implementation freeze must be a lowercase 40-character Git SHA-1");
  const epochText = execFileSync("git", ["show", "-s", "--format=%ct", commit], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
  if (!/^(?:0|[1-9][0-9]*)$/u.test(epochText)) fail("Git committer epoch is invalid");
  const epoch = Number(epochText);
  if (!Number.isSafeInteger(epoch) || epoch < 0) fail("Git committer epoch is out of range");
  return new Date(epoch * 1000).toISOString();
}

export function canonicalize(rawBytes, fixedTimestamp) {
  const raw = parseStrictJson(rawBytes);
  validateBom(raw);
  const originalTimestamp = raw.metadata.timestamp;
  const canonical = structuredClone(raw);
  canonical.metadata.timestamp = fixedTimestamp;
  const paths = diffPaths(raw, canonical);
  if (paths.length !== 1 || paths[0] !== "/metadata/timestamp") fail("semantic diff is not exactly /metadata/timestamp");
  validateBom(canonical);
  return { raw, canonical, originalTimestamp, semanticDiffPaths: paths };
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const rawPath = resolve(args["--raw"]);
  const outputPath = resolve(args["--output"]);
  const reportPath = resolve(args["--report"]);
  if (rawPath === outputPath || rawPath === reportPath || outputPath === reportPath) fail("input/output/report paths must differ");
  const rawBytes = readFileSync(rawPath);
  const timestamp = freezeTimestamp(args["--implementation-freeze"]);
  const result = canonicalize(rawBytes, timestamp);
  const canonicalBytes = Buffer.from(`${canonicalJson(result.canonical)}\n`, "utf8");
  const reparsed = parseStrictJson(canonicalBytes);
  validateBom(reparsed);
  const report = {
    schemaVersion: 1,
    releaseLine: "v0.2",
    releaseVersion: "0.2.0",
    implementationFreezeSha: args["--implementation-freeze"],
    raw: { path: basename(rawPath), sizeBytes: rawBytes.length, sha256: sha256(rawBytes), timestamp: result.originalTimestamp },
    canonical: { path: basename(outputPath), sizeBytes: canonicalBytes.length, sha256: sha256(canonicalBytes), timestamp },
    schemaValidation: { rawExitCode: 0, canonicalExitCode: 0, specVersion: "1.6" },
    semanticDiffPaths: result.semanticDiffPaths,
  };
  mkdirSync(dirname(outputPath), { recursive: true });
  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(outputPath, canonicalBytes, { flag: "wx" });
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, { encoding: "utf8", flag: "wx" });
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === resolve(new URL(import.meta.url).pathname.replace(/^\/(?:[A-Za-z]:)/u, (value) => value.slice(1)))) {
  try {
    main();
  } catch (error) {
    process.stderr.write(`V02_SBOM_CANONICALIZATION_FAILED: ${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}
