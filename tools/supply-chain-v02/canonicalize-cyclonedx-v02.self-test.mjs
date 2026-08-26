#!/usr/bin/env node
import { canonicalJson, canonicalize, parseStrictJson, validatePinnedSchema } from "./canonicalize-cyclonedx-v02.mjs";
import { mkdirSync, mkdtempSync, writeFileSync, readFileSync, existsSync, rmSync } from "node:fs";
import { resolve } from "node:path";
import { execFileSync, spawnSync } from "node:child_process";

function bom(timestamp = "2026-08-25T00:00:00.000Z") {
  const group = "io.github.xiaokh31.androidapphardening";
  const rootRef = `pkg:maven/${group}/android-app-hardening@0.2.0?project_path=%3A`;
  const projects = [
    [":host:apk-inspector", "apk-inspector"],
    [":host:axml", "axml"],
    [":host:cli", "cli"],
    [":host:container", "container"],
    [":host:repacker", "repacker"],
    [":runtime:bootstrap", "bootstrap"],
    [":runtime:native", "native"],
    [":runtime:policy", "policy"],
  ];
  const productComponents = projects.map(([path, name]) => {
    const ref = `pkg:maven/${group}/${name}@0.2.0?project_path=${encodeURIComponent(path)}`;
    return { type: "library", "bom-ref": ref, group, name, version: "0.2.0", purl: ref };
  });
  return Buffer.from(JSON.stringify({
    bomFormat: "CycloneDX",
    specVersion: "1.6",
    version: 1,
    metadata: {
      timestamp,
      component: {
        type: "application", "bom-ref": rootRef, group, name: "android-app-hardening", version: "0.2.0", purl: rootRef,
      },
    },
    components: [...productComponents, { type: "library", "bom-ref": "lib", name: "lib", version: "1" }],
    dependencies: [...productComponents.map((component) => ({ ref: component["bom-ref"], dependsOn: ["lib"] })),
      { ref: "lib", dependsOn: [] }],
  }), "utf8");
}

const first = canonicalize(bom("2026-08-25T01:02:03.004Z"), "2026-08-24T01:00:00.000Z");
const second = canonicalize(bom("2026-08-25T04:05:06.007Z"), "2026-08-24T01:00:00.000Z");
check(first.semanticDiffPaths.join() === "/metadata/timestamp");
check(`${canonicalJson(first.canonical)}\n` === `${canonicalJson(second.canonical)}\n`);
check(canonicalJson({ z: 1, a: -0 }) === '{"a":0,"z":1}');

const failures = [
  Buffer.from('{"a":1,"a":2}', "utf8"),
  Buffer.from([0xef, 0xbb, 0xbf, 0x7b, 0x7d]),
  Buffer.from('{"bad":"\\ud800"}', "utf8"),
];
for (const bytes of failures) checkFailure(() => parseStrictJson(bytes));
checkFailure(() => canonicalize(Buffer.from(bom().toString("utf8").replace('"version":1', '"version":1,"serialNumber":"x"')), "2026-01-01T00:00:00.000Z"));
checkFailure(() => canonicalize(Buffer.from(bom().toString("utf8").replace('"dependencies":[', '"dependencies":[{"ref":"missing","dependsOn":[]},')), "2026-01-01T00:00:00.000Z"));
checkFailure(() => canonicalize(Buffer.from(bom().toString("utf8").replace("%3Ahost%3Aapk-inspector", "%3Afixtures%3Aandroid")), "2026-01-01T00:00:00.000Z"));
for (const timestamp of [null, "invalid", "2026-01-01", "2026-01-01T00:00:00.001Z", "2026-02-30T00:00:00.000Z"]) {
  checkFailure(() => canonicalize(bom(), timestamp));
}
for (const mutation of [
  (value) => { value.components.pop(); },
  (value) => { value.components.push(value.components[0]); },
  (value) => { value.dependencies.pop(); },
  (value) => { value.dependencies[0].dependsOn.push("unknown"); },
  (value) => { value.metadata.component.version = "0.1.0"; },
  (value) => { value.metadata.buildSystem = {}; },
]) {
  const value = JSON.parse(bom()); mutation(value);
  checkFailure(() => canonicalize(Buffer.from(JSON.stringify(value)), "2026-01-01T00:00:00.000Z"));
}
mkdirSync("build/v0.2", { recursive: true });
const temporary = mkdtempSync(resolve("build/v0.2/sbom-self-test-"));
try {
  const raw = resolve(temporary, "raw.json"), output = resolve(temporary, "canonical.json"), report = resolve(temporary, "report.json");
  writeFileSync(raw, bom());
  const wrongCli = resolve(temporary, "wrong-cli.exe"); writeFileSync(wrongCli, "not the pinned executable\n");
  checkFailure(() => validatePinnedSchema(undefined, raw));
  checkFailure(() => validatePinnedSchema("relative-cli", raw));
  checkFailure(() => validatePinnedSchema(wrongCli, raw));
  const freeze = execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  const invoke = (cli, sha = freeze) => spawnSync(process.execPath, ["tools/supply-chain-v02/canonicalize-cyclonedx-v02.mjs",
    "--raw", raw, "--implementation-freeze", sha, "--output", output, "--report", report],
  { env: { ...process.env, V02_CYCLONEDX_CLI: cli ?? "" }, encoding: "utf8", windowsHide: true });
  for (const cli of [undefined, wrongCli]) {
    check(invoke(cli).status !== 0); check(!existsSync(output) && !existsSync(report));
  }
  check(invoke(process.env.V02_CYCLONEDX_CLI, "HEAD").status !== 0);
  const cli = process.env.V02_CYCLONEDX_CLI;
  if (cli) {
    // This passes the local closure check and is rejected by the actual pinned schema executable.
    const invalidSchema = JSON.parse(bom()); invalidSchema.components.at(-1).type = "invalid-schema-type";
    writeFileSync(raw, JSON.stringify(invalidSchema));
    checkFailure(() => validatePinnedSchema(cli, raw));
    check(invoke(cli).status !== 0); check(!existsSync(output) && !existsSync(report));
    writeFileSync(raw, bom());
    check(validatePinnedSchema(cli, raw) === 0);
    const result = invoke(cli); check(result.status === 0, result.stderr);
    const accepted = JSON.parse(readFileSync(report));
    check(accepted.schemaValidation.rawExitCode === 0 && accepted.schemaValidation.canonicalExitCode === 0);
    check(accepted.semanticDiffPaths.join() === "/metadata/timestamp");
    const original = readFileSync(output), originalReport = readFileSync(report);
    check(invoke(cli).status !== 0);
    check(readFileSync(output).equals(original) && readFileSync(report).equals(originalReport));
  } else {
    console.log("Pinned-CLI integration NOT RUN: V02_CYCLONEDX_CLI is required for acceptance");
  }
} finally { rmSync(temporary, { recursive: true, force: true }); }

console.log("V2-M0-02 CycloneDX canonicalizer self-test PASS");

function check(value, detail = "") {
  if (!value) throw new Error(`self-test assertion failed ${detail}`);
}

function checkFailure(action) {
  let failed = false;
  try { action(); } catch { failed = true; }
  check(failed);
}
