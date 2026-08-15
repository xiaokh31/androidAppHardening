param(
    [Parameter(Mandatory = $true)][string] $Serial,
    [string] $EvidenceRoot = "build/m3-04/device-api29-arm",
    [string] $Adb = ".toolchains/android-m0-04/sdk/platform-tools/adb.exe"
)

$ErrorActionPreference = "Stop"
$repository = (Get-Location).Path
$evidence = [System.IO.Path]::GetFullPath((Join-Path $repository $EvidenceRoot))
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $repository "build"))
$artifactRoot = [System.IO.Path]::GetFullPath((Join-Path $repository "artifacts"))
if (-not ($evidence.StartsWith("$buildRoot\") -or $evidence.StartsWith("$artifactRoot\"))) {
    throw "M3-04 evidence must remain under ignored build/ or artifacts/"
}

$javaHome = (Resolve-Path ".toolchains/jdk/jdk-17.0.19+10").Path
$androidHome = (Resolve-Path ".toolchains/android-m0-04/sdk").Path
$adbPath = (Resolve-Path $Adb).Path
$gradle = (Resolve-Path ".\gradlew.bat").Path
$sourceCommit = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceCommit -notmatch "^[0-9a-f]{40}$") { throw "source commit is unavailable" }

function Invoke-Checked([string] $Executable, [string[]] $Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "command failed with exit code ${LASTEXITCODE}: $([System.IO.Path]::GetFileName($Executable))"
    }
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome
$env:ANDROID_SDK_ROOT = $androidHome
$env:GRADLE_USER_HOME = (Resolve-Path ".toolchains/gradle-user-home").Path
$env:KOTLIN_DAEMON_ENABLED = "false"
$env:ANDROID_SERIAL = $Serial

$api = (& $adbPath -s $Serial shell getprop ro.build.version.sdk).Trim()
$abiList = (& $adbPath -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$buildType = (& $adbPath -s $Serial shell getprop ro.build.type).Trim()
$identity = (& $adbPath -s $Serial shell id).Trim()
if ($LASTEXITCODE -ne 0 -or $api -ne "29" -or $buildType -ne "user" -or $identity -match "(?:^|\s)uid=0\b" -or
    $abiList -notmatch "(?:^|,)arm64-v8a(?:,|$)" -or $abiList -notmatch "(?:^|,)armeabi-v7a(?:,|$)") {
    throw "authorized device must be a non-root API 29 user build with both ARM ABIs"
}

New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$runtimeEvidence = Join-Path $evidence "runtime"
Invoke-Checked "powershell.exe" @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "tools/validation/run-m2-04-arm-device.ps1",
    "-Serial", $Serial, "-EvidenceRoot", ($runtimeEvidence.Substring($repository.Length + 1)), "-Adb", $Adb
)

foreach ($abi in @("arm64-v8a", "armeabi-v7a")) {
    $cellRoot = Join-Path $evidence $abi
    New-Item -ItemType Directory -Force -Path $cellRoot | Out-Null
    Invoke-Checked $gradle @(
        "--offline", "--no-daemon", "--console=plain", "-Pm304ExpectedAbi=$abi",
        ":integration-tests:runFixtureMatrix"
    )
    $fixtureReport = Join-Path $cellRoot "fixture-results.json"
    Copy-Item -LiteralPath "integration-tests/build/reports/fixture-results.json" -Destination $fixtureReport -Force
    $runtimeReport = Join-Path $runtimeEvidence "$abi/report.json"
    $cellOutput = Join-Path $cellRoot "cell-api29-$abi.json"
    Invoke-Checked "node" @(
        "tools/device-capability-probe/index.mjs", "cell",
        "--api", "29", "--abi", $abi,
        "--platform", "api29-$abi-physical-nonroot-user",
        "--source-commit", $sourceCommit,
        "--fixture-report", $fixtureReport,
        "--runtime-report", $runtimeReport,
        "--output", $cellOutput
    )
}

foreach ($package in @(
    "ah.fixtures.android.m301.java_single", "ah.fixtures.android.m301.kotlin_single",
    "ah.fixtures.android.m301.kotlin_multidex", "ah.fixtures.android.m301.custom_application",
    "ah.fixtures.android.m301.custom_factory", "ah.fixtures.android.m301.startup_provider",
    "ah.fixtures.android.m301.multi_process", "ah.fixtures.android.m301.jni_four",
    "ah.fixtures.android.m301.jni_arm"
)) {
    $path = (& $adbPath -s $Serial shell pm path $package 2>$null).Trim()
    if ($path) { throw "M3-04 package cleanup failed" }
}

Write-Output "M3-04 API29 ARM32/ARM64 campaign PASS evidence=$EvidenceRoot"
