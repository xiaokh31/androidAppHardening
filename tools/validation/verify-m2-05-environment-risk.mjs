import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

function fail(message) {
  throw new Error(`M2-05 environment risk verification failed: ${message}`);
}

function read(file) {
  return fs.readFileSync(path.resolve(file), "utf8");
}

function requireText(condition, message) {
  if (!condition) fail(message);
}

const root = process.cwd();
const files = {
  adr: "docs/adr/0010-environment-risk-policy.md",
  engine: "runtime/policy/src/main/java/ah/runtime/risk/EnvironmentRiskEngine.java",
  policy: "runtime/policy/src/main/java/ah/runtime/risk/RiskPolicyV1.java",
  report: "runtime/policy/src/main/java/ah/runtime/risk/RiskReportV1.java",
  action: "runtime/policy/src/main/java/ah/runtime/risk/RiskAction.java",
  nativeJava: "runtime/native/src/main/java/ah/runtime/risk/NativeRiskSignals.java",
  nativeCpp: "runtime/native/src/main/cpp/risk_signals.cpp",
  nativeTest: "runtime/native/src/main/cpp/risk_signals_test.cpp",
  unit: "runtime/policy/src/test/java/ah/runtime/risk/EnvironmentRiskEngineSelfTest.java",
  connected: "runtime/policy/src/androidTest/java/ah/runtime/risk/RiskConnectedAssertions.java",
  m203: "fixtures/android/src/androidTestM203Fixture/java/ah/runtime/guard/M203DeviceRunner.java",
  workflow: ".github/workflows/m0-05-linux-kvm.yml",
};

for (const file of Object.values(files)) {
  requireText(fs.existsSync(path.resolve(root, file)), `missing ${file}`);
}

const adr = read(files.adr);
const engine = read(files.engine);
const policy = read(files.policy);
const report = read(files.report);
const action = read(files.action);
const nativeCpp = read(files.nativeCpp);
const unit = read(files.unit);
const connected = read(files.connected);
const m203 = read(files.m203);
const workflow = read(files.workflow);

for (const token of ["TRACER", "JDWP", "DEBUGGABLE", "INSTRUMENTATION_MAPPING", "EMULATOR_COMPOSITE"]) {
  requireText(adr.includes(token), `ADR missing ${token}`);
}
for (const [token, value] of [["TRACER_SCORE", "60"], ["JDWP_SCORE", "50"],
  ["DEBUGGABLE_SCORE", "20"], ["MAPPING_CAP", "80"], ["EMULATOR_CAP", "30"]]) {
  requireText(new RegExp(`${token}\\s*=\\s*${value}`).test(policy), `wrong ${token}`);
}
requireText(action.includes("ALLOW") && action.includes("DEGRADE") && !action.includes("DENY"),
  "RiskAction must contain only ALLOW and DEGRADE");
requireText(report.includes("Collections.unmodifiableList") && report.includes("Math.min(100"),
  "report immutability or cap missing");
requireText(engine.includes("50_000_000L") && engine.includes("/proc") === false,
  "engine budget/raw proc boundary missing");
requireText(!/SUPPORTED_ABIS|CPU_ABI|x86|arm64/.test(engine), "ABI must not contribute to scoring");
requireText(!/android\.content\.Context|PackageManager/.test(engine), "Context or PackageManager forbidden");
requireText(nativeCpp.includes('"/proc/self/status"') && nativeCpp.includes('"/proc/self/maps"'),
  "native current-process sources missing");
requireText(nativeCpp.includes("kStatusLimit") && nativeCpp.includes("kMapsLimit"),
  "native read limits missing");
requireText(nativeCpp.includes("O_NONBLOCK") && nativeCpp.includes("deadlineReached")
    && nativeCpp.includes("collectWithDependencies"), "native bounded deadline missing");
requireText(unit.includes("unavailable-zero") && unit.includes("emulator-cap")
    && unit.includes("family-dedup") && unit.includes("abi-zero-contribution"),
  "unit matrix incomplete");
requireText(read(files.nativeTest).includes("read-failure-unavailable")
    && read(files.nativeTest).includes("forced-timeout-unavailable"),
  "native failure matrix incomplete");
requireText(connected.includes("JDWP_WAIT_MILLIS = 3_000L")
    && connected.includes("debuggerDetected(last)")
    && connected.includes("SystemClock.sleep(25L)")
    && connected.includes("50_000_000L")
    && connected.includes("injection-debugger-high")
    && connected.includes("libfrida-agent-fixture.so")
    && connected.includes("timeout-all-unavailable"), "connected matrix incomplete");
requireText(m203.includes("risk_r8_jni=true") && m203.includes("EnvironmentRiskEngine.evaluate")
    && m203.includes("M2-05 R8 JNI mapping score")
    && m203.includes("libfrida-agent-r8.so"),
  "Release/R8 facade evidence missing");
requireText(workflow.includes("m205_wait_for_debugger") && workflow.includes("jdb -attach")
    && workflow.includes("-Pm204TargetAbi=x86 :runtime:policy:connectedCheck"),
  "real JDWP/x86 device evidence missing");

const samplePath = path.resolve("runtime/policy/build/reports/m2-05/risk-report-v1.json");
if (fs.existsSync(samplePath)) {
  const sample = fs.readFileSync(samplePath);
  const text = sample.toString("utf8");
  requireText(!/(?:\/proc\/|\/data\/|[A-Za-z]:\\\\)/.test(text), "sample leaks a path");
}

const sourceHash = crypto.createHash("sha256");
for (const file of Object.values(files).sort()) {
  sourceHash.update(file.replaceAll("\\", "/"));
  sourceHash.update("\0");
  sourceHash.update(fs.readFileSync(path.resolve(file)));
  sourceHash.update("\0");
}
const output = process.argv[2];
if (output) {
  const reportJson = {
    task_id: "M2-05",
    policy_version: 1,
    total_cap: 100,
    levels: { LOW: "0-39", MEDIUM: "40-79", HIGH: "80-100" },
    actions: { LOW: "ALLOW", MEDIUM: "DEGRADE", HIGH: "DEGRADE" },
    abi_risk_contribution: 0,
    source_sha256: sourceHash.digest("hex"),
    result: "PASS",
  };
  fs.mkdirSync(path.dirname(path.resolve(output)), { recursive: true });
  fs.writeFileSync(path.resolve(output), `${JSON.stringify(reportJson, null, 2)}\n`, "utf8");
}

process.stdout.write("M2-05 environment risk verification PASS\n");
