import { createHash } from "node:crypto";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "../..");
const lockPath = path.join(root, "tools/validation/m2-07-native-crypto.json");
const lock = JSON.parse(await readFile(lockPath, "utf8"));

function fail(message) {
  throw new Error(`M2-07 dependency verification failed: ${message}`);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

async function verify(candidate) {
  if (candidate.schema_version !== 1 || candidate.task !== "M2-07") {
    fail("unexpected lock schema or task");
  }
  if (!candidate.dependency.archive_url.startsWith(
    "https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-4.1.1/",
  )) {
    fail("archive URL is not the fixed official release asset");
  }

  const archivePath = path.join(root, ...candidate.local_paths.archive.split("/"));
  const archiveStat = await stat(archivePath).catch(() => null);
  if (archiveStat === null) {
    fail(`missing archive ${candidate.local_paths.archive}`);
  }
  if (archiveStat.size !== candidate.dependency.archive_bytes) {
    fail(`archive size ${archiveStat.size} != ${candidate.dependency.archive_bytes}`);
  }
  const archiveHash = sha256(await readFile(archivePath));
  if (archiveHash !== candidate.dependency.archive_sha256) {
    fail(`archive SHA-256 ${archiveHash} != ${candidate.dependency.archive_sha256}`);
  }

  const sourceRoot = path.join(root, ...candidate.local_paths.source.split("/"));
  const rootLicense = await readFile(path.join(sourceRoot, "LICENSE")).catch(() => null);
  const tfPsaLicense = await readFile(path.join(sourceRoot, "tf-psa-crypto/LICENSE")).catch(() => null);
  const tfPsaCmake = await readFile(
    path.join(sourceRoot, "tf-psa-crypto/CMakeLists.txt"),
    "utf8",
  ).catch(() => null);
  if (rootLicense === null || tfPsaLicense === null || tfPsaCmake === null) {
    fail(`incomplete extracted source ${candidate.local_paths.source}`);
  }
  if (sha256(rootLicense) !== candidate.dependency.root_license_sha256) {
    fail("root LICENSE hash mismatch");
  }
  if (sha256(tfPsaLicense) !== candidate.dependency.tf_psa_license_sha256) {
    fail("TF-PSA-Crypto LICENSE hash mismatch");
  }
  const versionPattern = new RegExp(
    `set\\(TF_PSA_CRYPTO_VERSION\\s+${candidate.dependency.bundled_tf_psa_crypto_version.replaceAll(".", "\\.")}\\)`,
  );
  if (!versionPattern.test(tfPsaCmake)) {
    fail("bundled TF-PSA-Crypto version mismatch");
  }
  return { archiveStat, archiveHash };
}

const { archiveStat, archiveHash } = await verify(lock);
if (process.argv.includes("--self-test")) {
  for (const mutation of [
    (candidate) => { candidate.dependency.archive_sha256 = "0".repeat(64); },
    (candidate) => { candidate.dependency.bundled_tf_psa_crypto_version = "9.9.9"; },
    (candidate) => { candidate.dependency.root_license_sha256 = "f".repeat(64); },
  ]) {
    const candidate = structuredClone(lock);
    mutation(candidate);
    let rejected = false;
    try {
      await verify(candidate);
    } catch {
      rejected = true;
    }
    if (!rejected) {
      fail("self-test mutation was accepted");
    }
  }
}

console.log(JSON.stringify({
  task: lock.task,
  archive: lock.dependency.archive_name,
  bytes: archiveStat.size,
  sha256: archiveHash,
  source: lock.local_paths.source,
  bundled_tf_psa_crypto_version: lock.dependency.bundled_tf_psa_crypto_version,
  selected_license: lock.dependency.selected_license,
  negative_self_test: process.argv.includes("--self-test") ? "PASS" : "not_requested",
}, null, 2));
