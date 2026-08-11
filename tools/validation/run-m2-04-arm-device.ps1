param(
    [Parameter(Mandatory = $true)][string] $Serial,
    [string] $EvidenceRoot = "build/m2-04/device-api29-arm",
    [string] $Adb = ".toolchains/android-m0-04/sdk/platform-tools/adb.exe"
)

$ErrorActionPreference = "Stop"
$repository = (Get-Location).Path
$evidence = [System.IO.Path]::GetFullPath((Join-Path $repository $EvidenceRoot))
$allowed = @(
    [System.IO.Path]::GetFullPath((Join-Path $repository "build")),
    [System.IO.Path]::GetFullPath((Join-Path $repository "artifacts"))
)
if (-not ($allowed | Where-Object { $evidence -eq $_ -or $evidence.StartsWith("$_\") })) {
    throw "M2-04 evidence must remain under ignored build/ or artifacts/"
}

$javaHome = (Resolve-Path ".toolchains/jdk/jdk-17.0.19+10").Path
$androidHome = (Resolve-Path ".toolchains/android-m0-04/sdk").Path
$adbPath = (Resolve-Path $Adb).Path
$gradle = (Resolve-Path ".\gradlew.bat").Path
$keytool = Join-Path $javaHome "bin/keytool.exe"
$apksigner = Join-Path $androidHome "build-tools/36.1.0/apksigner.bat"

function Invoke-Checked([string] $Executable, [string[]] $Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "command failed with exit code ${LASTEXITCODE}: $([System.IO.Path]::GetFileName($Executable))"
    }
}

function Invoke-Gradle([string[]] $Arguments) {
    Invoke-Checked $gradle (@("--offline", "--no-daemon", "--console=plain") + $Arguments)
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome
$env:ANDROID_SDK_ROOT = $androidHome
$env:GRADLE_USER_HOME = (Resolve-Path ".toolchains/gradle-user-home").Path
$env:KOTLIN_DAEMON_ENABLED = "false"

$abiList = (& $adbPath -s $Serial shell getprop ro.product.cpu.abilist).Trim()
$api = (& $adbPath -s $Serial shell getprop ro.build.version.sdk).Trim()
$model = (& $adbPath -s $Serial shell getprop ro.product.model).Trim()
if ($LASTEXITCODE -ne 0 -or $api -ne "29" -or
    $abiList -notmatch "(?:^|,)arm64-v8a(?:,|$)" -or
    $abiList -notmatch "(?:^|,)armeabi-v7a(?:,|$)") {
    throw "authorized device must be API 29 and support both required ARM ABIs"
}

$runId = [Guid]::NewGuid().ToString("N")
$signingRoot = Join-Path $repository "build/m2-04/signing/$runId"
$runtimeRoot = Join-Path $repository "build/m2-04/arm-runtime"
$sourceRoot = Join-Path $repository "build/m2-04/arm-source-dex"
New-Item -ItemType Directory -Force -Path $evidence, $signingRoot, $runtimeRoot | Out-Null
$keystore = Join-Path $signingRoot "fixture.p12"
$password = [Guid]::NewGuid().ToString("N") + [Guid]::NewGuid().ToString("N")
$env:M005_TEST_KEYSTORE = $keystore
$env:M005_TEST_STORE_PASSWORD = $password
$env:M005_TEST_KEY_ALIAS = "fixture"
$env:M005_TEST_KEY_PASSWORD = $password

try {
    Invoke-Checked $keytool @(
        "-genkeypair", "-noprompt", "-storetype", "PKCS12",
        "-keystore", $keystore, "-storepass", $password, "-keypass", $password,
        "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
        "-dname", "CN=M2-04 Ephemeral Fixture,O=androidAppHardening,C=US"
    )

    Invoke-Gradle @("-Pm204TargetAbi=arm64-v8a", ":fixtures:android:assembleCompatExtractedRelease")
    $compatApk = "fixtures/android/build/outputs/apk/compatExtracted/release/android-compatExtracted-release.apk"
    $certOutput = & $apksigner verify --print-certs $compatApk
    if ($LASTEXITCODE -ne 0) { throw "apksigner certificate measurement failed" }
    $signer = ($certOutput | Select-String "SHA-256 digest: ([0-9a-fA-F]{64})").Matches.Groups[1].Value.ToLowerInvariant()
    if ($signer -notmatch "^[0-9a-f]{64}$") { throw "fixture signer digest is unavailable" }

    Invoke-Checked "node" @(
        "tools/validation/prepare-m2-02-device-fixture.mjs", "extract",
        "fixtures/android/build/generated/m0-05/compatExtractedRelease/assets/ah/runtime/payload.ahdc",
        $sourceRoot
    )
    foreach ($variant in @("extracted", "direct")) {
        $vectorRoot = Join-Path $runtimeRoot "vector-$variant"
        Invoke-Gradle @(
            ":host:container:prepareM202DeviceVector",
            "-Pm202PrimaryDex=$sourceRoot/classes.dex",
            "-Pm202SecondaryDex=$sourceRoot/classes2.dex",
            "-Pm202VectorOutput=$vectorRoot",
            "-Pm202PackageName=ah.fixtures.android.m202.$variant",
            "-Pm202SignerSha256=$signer",
            "-Pm202OriginalFactory=-"
        )
    }

    $reports = @()
    foreach ($abi in @("arm64-v8a", "armeabi-v7a")) {
        $abiRoot = Join-Path $runtimeRoot $abi
        $abiEvidence = Join-Path $evidence $abi
        New-Item -ItemType Directory -Force -Path $abiRoot, $abiEvidence | Out-Null
        Invoke-Gradle @(
            "-Pm204TargetAbi=$abi",
            ":fixtures:android:assembleM202ExtractedRelease",
            ":fixtures:android:assembleM202DirectRelease",
            ":fixtures:android:assembleM202ExtractedDebugAndroidTest",
            ":fixtures:android:assembleM202DirectDebugAndroidTest"
        )
        Copy-Item -LiteralPath "fixtures/android/build/outputs/apk/androidTest/m202Extracted/debug/android-m202Extracted-debug-androidTest.apk" -Destination (Join-Path $abiRoot "extracted-test.apk")
        Copy-Item -LiteralPath "fixtures/android/build/outputs/apk/androidTest/m202Direct/debug/android-m202Direct-debug-androidTest.apk" -Destination (Join-Path $abiRoot "direct-test.apk")
        foreach ($variant in @("extracted", "direct")) {
            $title = if ($variant -eq "extracted") { "Extracted" } else { "Direct" }
            $baseline = "fixtures/android/build/outputs/apk/m202$title/release/android-m202$title-release.apk"
            Invoke-Checked "node" @(
                "tools/validation/prepare-m2-02-device-fixture.mjs", "package",
                $baseline, (Join-Path $runtimeRoot "vector-$variant"),
                (Join-Path $abiRoot "$variant.apk")
            )
        }
        Invoke-Checked "node" @(
            "tools/validation/run-m2-02-device-acceptance.mjs",
            "--task-id", "M2-04", "--expected-abi", $abi,
            "--adb", $adbPath, "--serial", $Serial,
            "--platform", "api29-$abi-physical-nonroot",
            "--cold-starts", "1", "--command-timeout-ms", "60000",
            "--extracted-target-apk", (Join-Path $abiRoot "extracted.apk"),
            "--extracted-test-apk", (Join-Path $abiRoot "extracted-test.apk"),
            "--extracted-vector-report", (Join-Path $runtimeRoot "vector-extracted/vector-report.json"),
            "--direct-target-apk", (Join-Path $abiRoot "direct.apk"),
            "--direct-test-apk", (Join-Path $abiRoot "direct-test.apk"),
            "--direct-vector-report", (Join-Path $runtimeRoot "vector-direct/vector-report.json"),
            "--evidence", $abiEvidence
        )
        $report = Join-Path $abiEvidence "report.json"
        $reports += [ordered]@{
            abi = $abi
            report = $report.Substring($repository.Length + 1).Replace("\", "/")
            report_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $report).Hash.ToLowerInvariant()
        }
    }

    $serialBytes = [Text.Encoding]::UTF8.GetBytes($Serial)
    $serialDigest = [Security.Cryptography.SHA256]::Create().ComputeHash($serialBytes)
    $summary = [ordered]@{
        task_id = "M2-04"
        validation_mode = "pre-cli"
        platform = "api29-arm-physical-nonroot"
        serial_sha256 = (($serialDigest | ForEach-Object { $_.ToString("x2") }) -join "")
        model = $model
        api = [int] $api
        abi_list = $abiList.Split(",")
        reports = $reports
        result = "PASS"
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence "matrix.json") -Encoding UTF8
    Write-Output "M2-04 ARM device matrix PASS evidence=$EvidenceRoot"
} finally {
    $env:M005_TEST_KEYSTORE = $null
    $env:M005_TEST_STORE_PASSWORD = $null
    $env:M005_TEST_KEY_ALIAS = $null
    $env:M005_TEST_KEY_PASSWORD = $null
    $password = $null
}
