#!/usr/bin/env node
import { canonicalJson, canonicalize, parseStrictJson } from "./canonicalize-cyclonedx-v02.mjs";

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

console.log("V2-M0-02 CycloneDX canonicalizer self-test PASS");

function check(value) {
  if (!value) throw new Error("self-test assertion failed");
}

function checkFailure(action) {
  let failed = false;
  try { action(); } catch { failed = true; }
  check(failed);
}
