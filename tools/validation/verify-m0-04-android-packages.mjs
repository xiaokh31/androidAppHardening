#!/usr/bin/env node

import { createHash } from "node:crypto";
import { createReadStream, readFileSync, statSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const manifestPath = new URL("./m0-04-android-packages.json", import.meta.url);

function fail(message) {
  throw new Error(`M0-04 Android package verification failed: ${message}`);
}

function readOption(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

async function hashes(file) {
  const sha1 = createHash("sha1");
  const sha256 = createHash("sha256");
  for await (const chunk of createReadStream(file)) {
    sha1.update(chunk);
    sha256.update(chunk);
  }
  return {
    sha1: sha1.digest("hex"),
    sha256: sha256.digest("hex"),
  };
}

async function main() {
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  if (manifest.schema_version !== 1 || manifest.git_policy !== "ignored") {
    fail("unsupported or unsafe package manifest");
  }
  const downloadDirectory = path.resolve(
    readOption(
      "--downloads",
      path.join(manifest.local_install_root, "downloads"),
    ),
  );
  const results = [];
  for (const package_ of manifest.packages) {
    if (!package_.archive_url.startsWith("https://dl.google.com/android/repository/")) {
      fail(`${package_.package_id} is not pinned to the official Android repository`);
    }
    const file = path.join(downloadDirectory, package_.archive);
    const bytes = statSync(file).size;
    const digests = await hashes(file);
    if (
      bytes !== package_.bytes ||
      digests.sha1 !== package_.sha1 ||
      digests.sha256 !== package_.sha256
    ) {
      fail(`${package_.package_id} archive does not match the lock`);
    }
    results.push({
      package_id: package_.package_id,
      revision: package_.revision,
      archive: package_.archive,
      bytes,
      ...digests,
    });
  }
  process.stdout.write(
    `${JSON.stringify(
      {
        task_id: "M0-04",
        catalog_snapshot_date: manifest.catalog_snapshot_date,
        packages: results,
        result: "PASS",
      },
      null,
      2,
    )}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
