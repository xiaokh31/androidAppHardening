import { createHash } from "node:crypto";
import { readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { isDeepStrictEqual } from "node:util";

const root = path.resolve(import.meta.dirname, "../..");
const lockPath = path.join(root, "tools/validation/m2-07-native-crypto.json");
const lock = JSON.parse(await readFile(lockPath, "utf8"));
const archiveOnly = process.argv.includes("--archive-only");
const selfTest = process.argv.includes("--self-test");
const prePromote = process.argv.includes("--pre-promote");
const sourceRootArgument = process.argv.find((argument) => argument.startsWith("--source-root="));
const sourceRootOverride = sourceRootArgument === undefined
  ? null
  : path.resolve(root, sourceRootArgument.slice("--source-root=".length));
const verifiedStampName = ".aah-m2-07-verified";
const verifiedStamp = "archive_sha256=3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c\nsource_tree_sha256=7c4ba6554fed6eb67c201054bc75b124fcdc0649e2f56cd762746e01a25d2140\n";
const ubuntuWorkflowPaths = [
  ".github/workflows/build.yml",
  ".github/workflows/m0-05-linux-kvm.yml",
  ".github/workflows/cross-platform-equivalence.yml",
];
const windowsWorkflowPath = ".github/workflows/build.yml";

const expectedLock = {
  schema_version: 1,
  task: "M2-07",
  reviewed_at: "2026-08-07",
  dependency: {
    name: "Mbed TLS",
    version: "4.1.1",
    release_url: "https://github.com/Mbed-TLS/mbedtls/releases/tag/mbedtls-4.1.1",
    archive_url: "https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-4.1.1/mbedtls-4.1.1.tar.bz2",
    archive_name: "mbedtls-4.1.1.tar.bz2",
    archive_bytes: 7099934,
    archive_sha256: "3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c",
    tag: "mbedtls-4.1.1",
    tag_object: "783058d12831aedd3ef57a64577f6f8a88d23bd3",
    commit: "0a8fda272a5a0abef3b47c91bed37185d5a726b1",
    tag_signature: "unsigned",
    bundled_tf_psa_crypto_version: "1.1.1",
    license_expression: "Apache-2.0 OR GPL-2.0-or-later",
    selected_license: "Apache-2.0",
    root_license_sha256: "9b405ef4c89342f5eae1dd828882f931747f71001cfba7d114801039b52ad09b",
    tf_psa_license_sha256: "da8c58f05f135a9d15e9ffad4ecf854cfcc1f014c8abfd75ba05f62630ccc118",
    source_regular_files: 3927,
    source_regular_bytes: 60515866,
    source_tree_sha256: "7c4ba6554fed6eb67c201054bc75b124fcdc0649e2f56cd762746e01a25d2140",
    source_symlinks_unix: 147,
    source_symlink_prefix: "tf-psa-crypto/drivers/pqcp/mldsa-native/examples/",
  },
  local_paths: {
    archive: ".toolchains/native-crypto/downloads/mbedtls-4.1.1.tar.bz2",
    source: ".toolchains/native-crypto/src/mbedtls-4.1.1",
  },
  official_evidence: {
    release_published_at: "2026-07-07T14:43:41Z",
    archive_asset_id: 464486390,
    archive_asset_digest: "sha256:3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c",
    checksum_asset_name: "mbedtls-4.1.1-sha256sum.txt",
    checksum_asset_bytes: 88,
    checksum_asset_digest: "sha256:bbf04627efb60c5e3ad620d903994804c275681d1a1948c6b0c0a5acdc77d4a4",
    checksum_entry: "3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c  mbedtls-4.1.1.tar.bz2\n",
  },
  allowed_algorithms: [
    "AES-256-GCM-DECRYPT",
    "HKDF-SHA-256",
    "HMAC-SHA-256",
    "SHA-256",
  ],
  native_profile: {
    config_path: "runtime/native/src/main/cpp/ah_crypto_config.h",
    config_sha256: "75094e9ef8dbb381cfadddc7e7c89eed9c622d471331ce95a30574e93156ef35",
    required_internal_symbols: [
      "d mbedtls_platform_dev_random",
      "t ctr_drbg_update_internal",
      "t entropy_gather_internal",
      "t entropy_update",
      "t mbedtls_aes_crypt_ecb",
      "t mbedtls_ctr_drbg_free",
      "t mbedtls_ctr_drbg_init",
      "t mbedtls_ctr_drbg_reseed_internal",
      "t mbedtls_ctr_drbg_seed",
      "t mbedtls_entropy_free",
      "t mbedtls_entropy_func",
      "t mbedtls_entropy_init",
      "t mbedtls_entropy_poll_platform",
      "t mbedtls_platform_get_entropy",
      "t psa_random_internal_free",
      "t psa_random_internal_init",
      "t psa_random_internal_seed",
    ],
  },
  android_abis: ["armeabi-v7a", "arm64-v8a", "x86", "x86_64"],
  ci_toolchains: {
    reviewed_at: "2026-08-22",
    ubuntu: {
      runs_on: "ubuntu-24.04",
      image_os: "ubuntu24",
      reviewed_images: [
        {
          image_version: "20260720.247.2",
          manifest_ref: "ubuntu24/20260720.247",
        },
        {
          image_version: "20260804.265.1",
          manifest_ref: "ubuntu24/20260804.265",
        },
        {
          image_version: "20260810.271.1",
          manifest_ref: "ubuntu24/20260810.271",
        },
        {
          image_version: "20260816.277.1",
          manifest_ref: "ubuntu24/20260816.277",
        },
      ],
      c_compiler: "gcc",
      cxx_compiler: "g++",
      compiler_version: "13.3.0",
    },
    windows: {
      runs_on: "windows-2025",
      image_os: "win25-vs2026",
      reviewed_images: [
        {
          image_version: "20260728.188.1",
          manifest_ref: "win25-vs2026/20260728.188",
          visual_studio_version: "18.8.12023.21",
          visual_studio_x64_tools: "18.8.11901.359",
        },
        {
          image_version: "20260803.193.1",
          manifest_ref: "win25-vs2026/20260803.193",
          visual_studio_version: "18.8.12023.21",
          visual_studio_x64_tools: "18.8.11901.359",
        },
        {
          image_version: "20260810.198.2",
          manifest_ref: "win25-vs2026/20260810.198",
          visual_studio_version: "18.8.12023.21",
          visual_studio_x64_tools: "18.8.11901.359",
        },
        {
          image_version: "20260818.207.1",
          manifest_ref: "win25-vs2026/20260818.207",
          visual_studio_version: "18.9.12112.369",
          visual_studio_x64_tools: "18.9.12009.112",
        },
      ],
      clang_cl_version: "20.1.8",
      cl_runtime_version: "19.51.36252",
      windows_sdk_version: "10.0.26100.0",
    },
  },
};

function fail(message) {
  throw new Error(`M2-07 dependency verification failed: ${message}`);
}

if (prePromote && sourceRootOverride === null) {
  fail("--pre-promote requires an explicit temporary --source-root");
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function verifyNativeProfileBytes(candidate, bytes) {
  const actual = sha256(bytes);
  if (actual !== candidate.native_profile.config_sha256) {
    fail(`Native profile hash mismatch: expected ${candidate.native_profile.config_sha256}, got ${actual}`);
  }
  return actual;
}

function verifyLockContract(candidate) {
  if (!isDeepStrictEqual(candidate, expectedLock)) {
    fail("lock does not exactly match the reviewed immutable identity");
  }
}

function extractUbuntuWorkflowMappings(text) {
  const mappings = [];
  for (const match of text.matchAll(/^\s*(\d{8}\.\d+\.\d+)\)\s+reviewed_manifest='(ubuntu24\/[^']+)'\s+;;\s*$/gm)) {
    mappings.push({ image_version: match[1], manifest_ref: match[2] });
  }
  for (const match of text.matchAll(/^\s*'(\d{8}\.\d+\.\d+)'\s*=\s*'(ubuntu24\/[^']+)'\s*$/gm)) {
    mappings.push({ image_version: match[1], manifest_ref: match[2] });
  }
  return mappings;
}

function countExact(text, needle) {
  return text.split(needle).length - 1;
}

function verifyUbuntuWorkflowBindings(workflows) {
  const expected = expectedLock.ci_toolchains.ubuntu.reviewed_images;
  for (const workflowPath of ubuntuWorkflowPaths) {
    const actual = extractUbuntuWorkflowMappings(workflows[workflowPath]);
    const wanted = workflowPath.endsWith("cross-platform-equivalence.yml")
      ? [...expected, ...expected]
      : expected;
    if (!isDeepStrictEqual(actual, wanted)) {
      fail(`Ubuntu workflow mapping/order mismatch: ${workflowPath}`);
    }
  }
  const equivalence = workflows[".github/workflows/cross-platform-equivalence.yml"];
  if (countExact(equivalence, "runner_manifest_ref=%s\\n") !== 1 ||
      countExact(equivalence, "runner_manifest_ref=$($reviewedUbuntuImages[$env:ImageVersion])") !== 1) {
    fail("cross-platform equivalence does not emit both reviewed manifest refs exactly once");
  }
}

function verifyWindowsWorkflowBinding(text) {
  const required = [
    "$reviewedImages[[string] $reviewedImage.image_version] = $reviewedImage",
    "$selectedImage = $reviewedImages[$env:ImageVersion]",
    "runner_manifest_ref=$($selectedImage.manifest_ref)",
    "Assert-ExactVersion ([string] $instance.installationVersion) ([string] $selectedImage.visual_studio_version) \"Visual Studio\"",
    "Assert-ExactVersion $x64ToolsVersion ([string] $selectedImage.visual_studio_x64_tools) \"Visual Studio x64 tools component\"",
    "Windows Kits/10/Include/$($toolchainLock.windows_sdk_version)",
    "$escapedClangClVersion = [regex]::Escape([string] $toolchainLock.clang_cl_version)",
    "clang version $escapedClangClVersion(?:\\s|$)",
  ];
  for (const needle of required) {
    if (countExact(text, needle) !== 1) {
      fail(`Windows workflow binding missing or duplicated: ${needle}`);
    }
  }
}

function verifyArchiveBytes(candidate, archiveBytes) {
  if (archiveBytes.length !== candidate.dependency.archive_bytes) {
    fail(`archive size ${archiveBytes.length} != ${candidate.dependency.archive_bytes}`);
  }
  const archiveHash = sha256(archiveBytes);
  if (archiveHash !== candidate.dependency.archive_sha256) {
    fail(`archive SHA-256 ${archiveHash} != ${candidate.dependency.archive_sha256}`);
  }
  const checksumBytes = Buffer.from(candidate.official_evidence.checksum_entry, "utf8");
  if (checksumBytes.length !== candidate.official_evidence.checksum_asset_bytes ||
      `sha256:${sha256(checksumBytes)}` !== candidate.official_evidence.checksum_asset_digest ||
      candidate.official_evidence.archive_asset_digest !== `sha256:${archiveHash}`) {
    fail("offline official release/checksum evidence summary mismatch");
  }
  return archiveHash;
}

async function collectSourceEntries(directory, relative = "", inventory = { files: [], symlinks: [] }) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const absolute = path.join(directory, entry.name);
    const normalized = relative === "" ? entry.name : `${relative}/${entry.name}`;
    if (entry.isDirectory()) {
      await collectSourceEntries(absolute, normalized, inventory);
    } else if (entry.isFile()) {
      if (normalized !== verifiedStampName) {
        inventory.files.push({ absolute, normalized });
      }
    } else if (entry.isSymbolicLink()) {
      inventory.symlinks.push(normalized);
    } else {
      fail(`unsupported source-tree entry ${normalized}`);
    }
  }
  return inventory;
}

function verifySymlinkSurface(candidate, symlinks, platform = process.platform) {
  const unixCount = candidate.dependency.source_symlinks_unix;
  const validCount = platform === "win32"
    ? symlinks.length === 0 || symlinks.length === unixCount
    : symlinks.length === unixCount;
  if (!validCount ||
      symlinks.some((entry) => !entry.startsWith(candidate.dependency.source_symlink_prefix))) {
    const expected = platform === "win32" ? `0_or_${unixCount}` : String(unixCount);
    fail(`unexpected source symlink surface platform=${platform} count=${symlinks.length} expected=${expected}`);
  }
}

async function verifySourceTree(candidate, sourceRoot) {
  const sourceStat = await stat(sourceRoot).catch(() => null);
  if (sourceStat === null || !sourceStat.isDirectory()) {
    fail(`missing extracted source ${candidate.local_paths.source}`);
  }

  const inventory = await collectSourceEntries(sourceRoot);
  const { files, symlinks } = inventory;
  verifySymlinkSurface(candidate, symlinks);
  files.sort((left, right) => left.normalized < right.normalized ? -1 : left.normalized > right.normalized ? 1 : 0);
  const treeHash = createHash("sha256");
  let totalBytes = 0;
  for (const file of files) {
    const bytes = await readFile(file.absolute);
    totalBytes += bytes.length;
    treeHash.update(file.normalized, "utf8");
    treeHash.update("\0");
    treeHash.update(String(bytes.length), "utf8");
    treeHash.update("\0");
    treeHash.update(bytes);
    treeHash.update("\0");
  }
  const treeDigest = treeHash.digest("hex");
  if (files.length !== candidate.dependency.source_regular_files ||
      totalBytes !== candidate.dependency.source_regular_bytes ||
      treeDigest !== candidate.dependency.source_tree_sha256) {
    fail(`source tree mismatch files=${files.length} bytes=${totalBytes} sha256=${treeDigest}`);
  }

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
  if (!prePromote) {
    const stamp = await readFile(path.join(sourceRoot, verifiedStampName), "utf8").catch(() => null);
    if (stamp !== verifiedStamp) {
      fail("verified source stamp is missing or does not match the reviewed archive/tree identity");
    }
  }
  return { files: files.length, totalBytes, treeDigest, symlinks: symlinks.length };
}

async function expectRejected(action, label) {
  try {
    await action();
  } catch {
    return;
  }
  fail(`self-test mutation was accepted: ${label}`);
}

verifyLockContract(lock);
const ubuntuWorkflows = Object.fromEntries(await Promise.all(ubuntuWorkflowPaths.map(async (workflowPath) => [
  workflowPath,
  await readFile(path.join(root, ...workflowPath.split("/")), "utf8"),
])));
verifyUbuntuWorkflowBindings(ubuntuWorkflows);
const windowsWorkflow = await readFile(path.join(root, ...windowsWorkflowPath.split("/")), "utf8");
verifyWindowsWorkflowBinding(windowsWorkflow);
const archivePath = path.join(root, ...lock.local_paths.archive.split("/"));
const archiveBytes = await readFile(archivePath).catch(() => null);
if (archiveBytes === null) {
  fail(`missing archive ${lock.local_paths.archive}`);
}
const archiveHash = verifyArchiveBytes(lock, archiveBytes);
const sourceRoot = sourceRootOverride ?? path.join(root, ...lock.local_paths.source.split("/"));
const sourceSummary = archiveOnly ? null : await verifySourceTree(lock, sourceRoot);
const nativeProfilePath = path.join(root, ...lock.native_profile.config_path.split("/"));
const nativeProfileBytes = await readFile(nativeProfilePath).catch(() => null);
if (nativeProfileBytes === null) {
  fail(`missing Native profile ${lock.native_profile.config_path}`);
}
const nativeProfileHash = verifyNativeProfileBytes(lock, nativeProfileBytes);

if (selfTest) {
  const mutatedArchive = Buffer.from(archiveBytes);
  mutatedArchive[Math.floor(mutatedArchive.length / 2)] ^= 1;
  await expectRejected(
    () => Promise.resolve(verifyArchiveBytes(lock, mutatedArchive)),
    "one archive byte",
  );
  const mutatedNativeProfile = Buffer.from(nativeProfileBytes);
  mutatedNativeProfile[Math.floor(mutatedNativeProfile.length / 2)] ^= 1;
  await expectRejected(
    () => Promise.resolve(verifyNativeProfileBytes(lock, mutatedNativeProfile)),
    "one Native profile byte",
  );
  await expectRejected(
    () => Promise.resolve(verifySymlinkSurface(lock, [], "linux")),
    "missing Unix symlink set",
  );
  const syntheticUnixSymlinks = Array.from(
    { length: lock.dependency.source_symlinks_unix },
    (_, index) => `${lock.dependency.source_symlink_prefix}synthetic-${index}`,
  );
  verifySymlinkSurface(lock, syntheticUnixSymlinks, "linux");
  const wrongPrefixSymlinks = [...syntheticUnixSymlinks];
  wrongPrefixSymlinks[0] = "outside-reviewed-prefix/synthetic-0";
  await expectRejected(
    () => Promise.resolve(verifySymlinkSurface(lock, wrongPrefixSymlinks, "linux")),
    "wrong-prefix Unix symlink set",
  );
  verifySymlinkSurface(lock, [], "win32");
  verifySymlinkSurface(lock, syntheticUnixSymlinks, "win32");
  await expectRejected(
    () => Promise.resolve(verifySymlinkSurface(lock, wrongPrefixSymlinks, "win32")),
    "wrong-prefix Windows symlink set",
  );
  await expectRejected(
    () => Promise.resolve(verifySymlinkSurface(lock, syntheticUnixSymlinks.slice(1), "win32")),
    "partial Windows symlink set",
  );

  const mutations = [
    ["schema", (candidate) => { candidate.schema_version = 2; }],
    ["task", (candidate) => { candidate.task = "M2-XX"; }],
    ["review date", (candidate) => { candidate.reviewed_at = "1970-01-01"; }],
    ["name", (candidate) => { candidate.dependency.name = "replacement"; }],
    ["version", (candidate) => { candidate.dependency.version = "4.1.2"; }],
    ["release URL", (candidate) => { candidate.dependency.release_url += "?changed"; }],
    ["archive URL", (candidate) => { candidate.dependency.archive_url += "?changed"; }],
    ["archive name", (candidate) => { candidate.dependency.archive_name += ".changed"; }],
    ["archive bytes", (candidate) => { candidate.dependency.archive_bytes += 1; }],
    ["archive hash", (candidate) => { candidate.dependency.archive_sha256 = "0".repeat(64); }],
    ["tag", (candidate) => { candidate.dependency.tag = "mbedtls-4.1.2"; }],
    ["tag object", (candidate) => { candidate.dependency.tag_object = "0".repeat(40); }],
    ["commit", (candidate) => { candidate.dependency.commit = "0".repeat(40); }],
    ["tag signature", (candidate) => { candidate.dependency.tag_signature = "changed"; }],
    ["TF-PSA version", (candidate) => { candidate.dependency.bundled_tf_psa_crypto_version = "9.9.9"; }],
    ["license expression", (candidate) => { candidate.dependency.license_expression = "GPL-2.0-only"; }],
    ["selected license", (candidate) => { candidate.dependency.selected_license = "GPL-2.0-or-later"; }],
    ["root license", (candidate) => { candidate.dependency.root_license_sha256 = "f".repeat(64); }],
    ["TF-PSA license", (candidate) => { candidate.dependency.tf_psa_license_sha256 = "f".repeat(64); }],
    ["source file count", (candidate) => { candidate.dependency.source_regular_files += 1; }],
    ["source bytes", (candidate) => { candidate.dependency.source_regular_bytes += 1; }],
    ["source tree hash", (candidate) => { candidate.dependency.source_tree_sha256 = "0".repeat(64); }],
    ["source symlink count", (candidate) => { candidate.dependency.source_symlinks_unix += 1; }],
    ["source symlink prefix", (candidate) => { candidate.dependency.source_symlink_prefix = "changed/"; }],
    ["archive path", (candidate) => { candidate.local_paths.archive += ".changed"; }],
    ["source path", (candidate) => { candidate.local_paths.source += ".changed"; }],
    ["release evidence", (candidate) => { candidate.official_evidence.release_published_at = "1970-01-01T00:00:00Z"; }],
    ["asset evidence", (candidate) => { candidate.official_evidence.archive_asset_digest = "sha256:changed"; }],
    ["checksum evidence", (candidate) => { candidate.official_evidence.checksum_entry += "changed"; }],
    ["algorithm list", (candidate) => { candidate.allowed_algorithms.push("RSA"); }],
    ["Native profile path", (candidate) => { candidate.native_profile.config_path += ".changed"; }],
    ["Native profile hash", (candidate) => { candidate.native_profile.config_sha256 = "0".repeat(64); }],
    ["Native internal symbol name", (candidate) => { candidate.native_profile.required_internal_symbols[0] += "_changed"; }],
    ["Native internal symbol type", (candidate) => { candidate.native_profile.required_internal_symbols[0] = `t${candidate.native_profile.required_internal_symbols[0].slice(1)}`; }],
    ["Native internal symbol order", (candidate) => { candidate.native_profile.required_internal_symbols.reverse(); }],
    ["ABI list", (candidate) => { candidate.android_abis.reverse(); }],
    ["CI toolchain review date", (candidate) => { candidate.ci_toolchains.reviewed_at = "1970-01-01"; }],
    ["Ubuntu runner label", (candidate) => { candidate.ci_toolchains.ubuntu.runs_on = "ubuntu-latest"; }],
    ["Ubuntu image OS", (candidate) => { candidate.ci_toolchains.ubuntu.image_os = "changed"; }],
    ["Ubuntu reviewed image", (candidate) => { candidate.ci_toolchains.ubuntu.reviewed_images[0].image_version += ".changed"; }],
    ["Ubuntu manifest ref", (candidate) => { candidate.ci_toolchains.ubuntu.reviewed_images[1].manifest_ref += ".changed"; }],
    ["Ubuntu reviewed image removal", (candidate) => { candidate.ci_toolchains.ubuntu.reviewed_images.pop(); }],
    ["Ubuntu unreviewed image addition", (candidate) => { candidate.ci_toolchains.ubuntu.reviewed_images.push({ image_version: "20990101.1.1", manifest_ref: "ubuntu24/20990101.1" }); }],
    ["Ubuntu reviewed image order", (candidate) => { candidate.ci_toolchains.ubuntu.reviewed_images.reverse(); }],
    ["Ubuntu C compiler", (candidate) => { candidate.ci_toolchains.ubuntu.c_compiler = "clang"; }],
    ["Ubuntu CXX compiler", (candidate) => { candidate.ci_toolchains.ubuntu.cxx_compiler = "clang++"; }],
    ["Ubuntu compiler version", (candidate) => { candidate.ci_toolchains.ubuntu.compiler_version = "changed"; }],
    ["Windows runner label", (candidate) => { candidate.ci_toolchains.windows.runs_on = "windows-latest"; }],
    ["Windows image OS", (candidate) => { candidate.ci_toolchains.windows.image_os = "changed"; }],
    ["Windows reviewed image", (candidate) => { candidate.ci_toolchains.windows.reviewed_images[0].image_version += ".changed"; }],
    ["Windows manifest ref", (candidate) => { candidate.ci_toolchains.windows.reviewed_images[3].manifest_ref += ".changed"; }],
    ["Windows reviewed image removal", (candidate) => { candidate.ci_toolchains.windows.reviewed_images.pop(); }],
    ["Windows unreviewed image addition", (candidate) => { candidate.ci_toolchains.windows.reviewed_images.push({ image_version: "20990101.1.1", manifest_ref: "win25-vs2026/20990101.1", visual_studio_version: "changed", visual_studio_x64_tools: "changed" }); }],
    ["Windows reviewed image order", (candidate) => { candidate.ci_toolchains.windows.reviewed_images.reverse(); }],
    ["Windows clang-cl", (candidate) => { candidate.ci_toolchains.windows.clang_cl_version = "changed"; }],
    ["Windows Visual Studio", (candidate) => { candidate.ci_toolchains.windows.reviewed_images[3].visual_studio_version = "changed"; }],
    ["Windows x64 tools", (candidate) => { candidate.ci_toolchains.windows.reviewed_images[3].visual_studio_x64_tools = "changed"; }],
    ["Windows cl runtime", (candidate) => { candidate.ci_toolchains.windows.cl_runtime_version = "changed"; }],
    ["Windows SDK", (candidate) => { candidate.ci_toolchains.windows.windows_sdk_version = "changed"; }],
  ];
  for (const [label, mutate] of mutations) {
    const candidate = structuredClone(lock);
    mutate(candidate);
    await expectRejected(() => Promise.resolve(verifyLockContract(candidate)), label);
  }

  const workflowMutations = [
    ["Ubuntu workflow mapping removal", (candidate) => {
      candidate[ubuntuWorkflowPaths[2]] = candidate[ubuntuWorkflowPaths[2]].replace(
        "            20260816.277.1) reviewed_manifest='ubuntu24/20260816.277' ;;\n",
        "",
      );
    }],
    ["Ubuntu workflow mapping addition", (candidate) => {
      candidate[ubuntuWorkflowPaths[2]] = candidate[ubuntuWorkflowPaths[2]].replace(
        "            20260816.277.1) reviewed_manifest='ubuntu24/20260816.277' ;;\n",
        "            20260816.277.1) reviewed_manifest='ubuntu24/20260816.277' ;;\n            20990101.1.1) reviewed_manifest='ubuntu24/20990101.1' ;;\n",
      );
    }],
    ["Ubuntu workflow mapping order", (candidate) => {
      candidate[ubuntuWorkflowPaths[2]] = candidate[ubuntuWorkflowPaths[2]].replace(
        "            20260720.247.2) reviewed_manifest='ubuntu24/20260720.247' ;;\n            20260804.265.1) reviewed_manifest='ubuntu24/20260804.265' ;;\n",
        "            20260804.265.1) reviewed_manifest='ubuntu24/20260804.265' ;;\n            20260720.247.2) reviewed_manifest='ubuntu24/20260720.247' ;;\n",
      );
    }],
    ["Ubuntu workflow manifest drift", (candidate) => {
      candidate[ubuntuWorkflowPaths[0]] = candidate[ubuntuWorkflowPaths[0]].replace(
        "ubuntu24/20260816.277' ;;",
        "ubuntu24/changed' ;;",
      );
    }],
  ];
  for (const [label, mutate] of workflowMutations) {
    const candidate = structuredClone(ubuntuWorkflows);
    mutate(candidate);
    await expectRejected(() => Promise.resolve(verifyUbuntuWorkflowBindings(candidate)), label);
  }
  const windowsWorkflowMutations = [
    ["Windows selected image binding", (candidate) => candidate.replace(
      "$selectedImage = $reviewedImages[$env:ImageVersion]",
      "$selectedImage = $null",
    )],
    ["Windows per-image Visual Studio binding", (candidate) => candidate.replace(
      "$selectedImage.visual_studio_version",
      "$toolchainLock.visual_studio_version",
    )],
    ["Windows per-image x64 tools binding", (candidate) => candidate.replace(
      "$selectedImage.visual_studio_x64_tools",
      "$toolchainLock.visual_studio_x64_tools",
    )],
    ["Windows SDK binding", (candidate) => candidate.replace(
      "Windows Kits/10/Include/$($toolchainLock.windows_sdk_version)",
      "Windows Kits/10/Include/latest",
    )],
    ["Windows clang-cl lock binding", (candidate) => candidate.replace(
      "$toolchainLock.clang_cl_version",
      "'20.1.8'",
    )],
  ];
  for (const [label, mutate] of windowsWorkflowMutations) {
    await expectRejected(() => Promise.resolve(verifyWindowsWorkflowBinding(mutate(windowsWorkflow))), label);
  }
}

console.log(JSON.stringify({
  task: lock.task,
  archive: lock.dependency.archive_name,
  bytes: archiveBytes.length,
  sha256: archiveHash,
  source: archiveOnly ? "not_checked" : sourceRoot,
  source_regular_files: sourceSummary?.files ?? "not_checked",
  source_regular_bytes: sourceSummary?.totalBytes ?? "not_checked",
  source_tree_sha256: sourceSummary?.treeDigest ?? "not_checked",
  source_symlinks: sourceSummary?.symlinks ?? "not_checked",
  tag_object: lock.dependency.tag_object,
  commit: lock.dependency.commit,
  official_archive_asset_digest: lock.official_evidence.archive_asset_digest,
  official_checksum_asset_digest: lock.official_evidence.checksum_asset_digest,
  bundled_tf_psa_crypto_version: lock.dependency.bundled_tf_psa_crypto_version,
  selected_license: lock.dependency.selected_license,
  native_profile_sha256: nativeProfileHash,
  negative_self_test: selfTest ? "PASS" : "not_requested",
  phase: archiveOnly ? "archive_only" : "archive_and_source",
  verified_stamp: archiveOnly || prePromote ? "not_checked" : "PASS",
}, null, 2));
