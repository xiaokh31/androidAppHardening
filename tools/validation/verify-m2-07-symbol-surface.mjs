import { readFile } from "node:fs/promises";
import process from "node:process";

const mode = process.argv[2];
const root = new URL("../../", import.meta.url);
const lock = JSON.parse(await readFile(new URL("tools/validation/m2-07-native-crypto.json", root), "utf8"));
const expected = [...lock.native_profile.required_internal_symbols].sort();
const relatedName = /^(?:mbedtls_aes_crypt_ecb|mbedtls_ctr_drbg_|mbedtls_entropy_|psa_random_internal_|ctr_drbg_|entropy_|mbedtls_platform_(?:get_entropy|dev_random))/;

function fail(message) {
  throw new Error(`M2-07 symbol surface verification failed: ${message}`);
}

function parseRelated(nmOutput) {
  const found = [];
  for (const line of nmOutput.split(/\r?\n/)) {
    const match = /^\S+\s+(\S)\s+(\S+)$/.exec(line.trim());
    if (match !== null && relatedName.test(match[2])) {
      found.push(`${match[1]} ${match[2]}`);
    }
  }
  return found.sort();
}

function verifyExact(nmOutput) {
  const actual = parseRelated(nmOutput);
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    fail(`expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

function rejectRelatedExports(nmOutput) {
  const exported = parseRelated(nmOutput);
  if (exported.length !== 0) {
    fail(`unexpected related dynamic export(s): ${exported.join(", ")}`);
  }
}

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

function selfTest() {
  const address = "0000000000000000";
  const baseline = expected.map((entry) => {
    const separator = entry.indexOf(" ");
    return `${address} ${entry.slice(0, separator)} ${entry.slice(separator + 1)}`;
  }).join("\n");
  verifyExact(`${baseline}\n${address} T unrelated_symbol\n`);
  rejectRelatedExports(`${address} T unrelated_symbol\n`);

  const mutations = [
    ["extra local", `${baseline}\n${address} t ctr_drbg_future_helper\n`],
    ["extra global", `${baseline}\n${address} T entropy_future_helper\n`],
    ["extra hidden global", `${baseline}\n${address} T mbedtls_ctr_drbg_future_helper\n`],
    ["missing", expected.slice(1).map((entry) => `${address} ${entry}`).join("\n")],
    ["t to d", baseline.replace(" t ctr_drbg_update_internal", " d ctr_drbg_update_internal")],
    ["t to T", baseline.replace(" t ctr_drbg_update_internal", " T ctr_drbg_update_internal")],
  ];
  for (const [name, candidate] of mutations) {
    let rejected = false;
    try {
      verifyExact(candidate);
    } catch {
      rejected = true;
    }
    if (!rejected) {
      fail(`self-test accepted ${name}`);
    }
  }

  for (const exported of [
    `${address} T ctr_drbg_future_helper\n`,
    `${address} T entropy_future_helper\n`,
    `${address} D mbedtls_platform_dev_random\n`,
  ]) {
    let rejected = false;
    try {
      rejectRelatedExports(exported);
    } catch {
      rejected = true;
    }
    if (!rejected) {
      fail(`self-test accepted related export ${exported.trim()}`);
    }
  }
}

if (mode === "--self-test") {
  selfTest();
  console.log(`M2-07 symbol surface self-test PASS expected=${expected.length}`);
} else if (mode === "--verify") {
  verifyExact(await readStdin());
  console.log(`M2-07 exact internal symbol surface PASS expected=${expected.length}`);
} else if (mode === "--reject-exports") {
  rejectRelatedExports(await readStdin());
  console.log("M2-07 related dynamic export surface PASS expected=0");
} else {
  fail("usage: --self-test | --verify | --reject-exports");
}
