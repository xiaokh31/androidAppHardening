param(
    [Parameter(Mandatory = $true)]
    [string]$AvdName,
    [Parameter(Mandatory = $true)]
    [ValidateSet(5556, 5558, 5560, 5562)]
    [int]$Port,
    [ValidateRange(30, 180)]
    [int]$BootTimeoutSeconds = 90,
    [ValidateRange(15, 120)]
    [int]$CommandTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$toolchainRoot = Join-Path $repositoryRoot '.toolchains\android-m0-04'
$sdkRoot = Join-Path $toolchainRoot 'sdk'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$avdHome = Join-Path $toolchainRoot 'avd'
$serial = "emulator-$Port"
$evidenceRoot = Join-Path $repositoryRoot "build\m0-05\device-$($AvdName)"
$stdoutPath = Join-Path $evidenceRoot 'command.stdout.txt'
$stderrPath = Join-Path $evidenceRoot 'command.stderr.txt'
$emulatorStdoutPath = Join-Path $evidenceRoot 'emulator.stdout.txt'
$emulatorStderrPath = Join-Path $evidenceRoot 'emulator.stderr.txt'
$emulatorProcess = $null
$baselineProcessIds = @(
    Get-Process -Name emulator,qemu-system-x86_64,qemu-system-aarch64 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Id
)

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [int]$TimeoutSeconds = $CommandTimeoutSeconds,
        [switch]$AllowFailure
    )

    Remove-Item -LiteralPath $stdoutPath,$stderrPath -ErrorAction SilentlyContinue
    $process = Start-Process -FilePath $adb `
        -ArgumentList (@('-s', $serial) + $Arguments) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -PassThru
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "adb command timed out after $TimeoutSeconds seconds: $($Arguments -join ' ')"
    }
    $stdout = if (Test-Path $stdoutPath) { Get-Content -Raw $stdoutPath } else { '' }
    $stderr = if (Test-Path $stderrPath) { Get-Content -Raw $stderrPath } else { '' }
    if (-not $AllowFailure -and $process.ExitCode -ne 0) {
        throw "adb command failed ($($process.ExitCode)): $($Arguments -join ' ')`n$stdout`n$stderr"
    }
    [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdout
        Stderr = $stderr
    }
}

function Assert-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "required APK is missing: $Path"
    }
}

function Invoke-Variant {
    param(
        [string]$Name,
        [string]$PackageName,
        [string]$TargetApk,
        [string]$TestApk
    )

    Invoke-Adb -Arguments @('uninstall', "$PackageName.test") -AllowFailure | Out-Null
    Invoke-Adb -Arguments @('uninstall', $PackageName) -AllowFailure | Out-Null
    Invoke-Adb -Arguments @('install', '-r', '-t', $TargetApk) | Out-Null
    Invoke-Adb -Arguments @('install', '-r', '-t', $TestApk) | Out-Null
    $instrumentation = Invoke-Adb -Arguments @(
        'shell', 'am', 'instrument', '-w',
        "$PackageName.test/ah.fixtures.android.CompatibilityPocRunner"
    ) -TimeoutSeconds $CommandTimeoutSeconds
    if ($instrumentation.Stdout -notmatch 'OK \(1 test\)' -or
        $instrumentation.Stdout -notmatch 'INSTRUMENTATION_CODE: -1') {
        throw "$Name instrumentation did not pass:`n$($instrumentation.Stdout)`n$($instrumentation.Stderr)"
    }
    $instrumentation.Stdout | Set-Content -Encoding UTF8 (Join-Path $evidenceRoot "$Name.instrumentation.txt")
    Invoke-Adb -Arguments @('uninstall', "$PackageName.test") -AllowFailure | Out-Null
    Invoke-Adb -Arguments @('uninstall', $PackageName) -AllowFailure | Out-Null
}

New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
Assert-File $adb
Assert-File $emulator
$variants = @(
    [pscustomobject]@{
        Name = 'extracted'
        Package = 'ah.fixtures.android.m005.extracted'
        Target = Join-Path $repositoryRoot 'fixtures\android\build\outputs\apk\compatExtracted\release\android-compatExtracted-release.apk'
        Test = Join-Path $repositoryRoot 'fixtures\android\build\outputs\apk\androidTest\compatExtracted\debug\android-compatExtracted-debug-androidTest.apk'
    },
    [pscustomobject]@{
        Name = 'direct'
        Package = 'ah.fixtures.android.m005.direct'
        Target = Join-Path $repositoryRoot 'fixtures\android\build\outputs\apk\compatDirect\release\android-compatDirect-release.apk'
        Test = Join-Path $repositoryRoot 'fixtures\android\build\outputs\apk\androidTest\compatDirect\debug\android-compatDirect-debug-androidTest.apk'
    }
)
foreach ($variant in $variants) {
    Assert-File $variant.Target
    Assert-File $variant.Test
}

$env:ANDROID_AVD_HOME = $avdHome
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot

try {
    $existing = & $adb devices
    if ($existing -match "(?m)^$([regex]::Escape($serial))\s") {
        throw "$serial already exists; refusing to take ownership of an existing emulator"
    }
    $emulatorProcess = Start-Process -FilePath $emulator `
        -ArgumentList @(
            '-avd', $AvdName,
            '-port', $Port,
            '-no-window',
            '-no-audio',
            '-no-boot-anim',
            '-no-snapshot-save',
            '-gpu', 'swiftshader_indirect'
        ) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $emulatorStdoutPath `
        -RedirectStandardError $emulatorStderrPath `
        -PassThru

    $deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSeconds)
    $booted = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($emulatorProcess.HasExited) {
            throw "emulator exited before boot completed with code $($emulatorProcess.ExitCode)"
        }
        $state = Invoke-Adb -Arguments @('get-state') -TimeoutSeconds 5 -AllowFailure
        if ($state.ExitCode -eq 0 -and $state.Stdout.Trim() -eq 'device') {
            $complete = Invoke-Adb -Arguments @('shell', 'getprop', 'sys.boot_completed') -TimeoutSeconds 5 -AllowFailure
            if ($complete.ExitCode -eq 0 -and $complete.Stdout.Trim() -eq '1') {
                $booted = $true
                break
            }
        }
        Start-Sleep -Seconds 2
    }
    if (-not $booted) {
        throw "emulator boot exceeded the $BootTimeoutSeconds second limit"
    }

    foreach ($variant in $variants) {
        Invoke-Variant -Name $variant.Name `
            -PackageName $variant.Package `
            -TargetApk $variant.Target `
            -TestApk $variant.Test
    }

    $environment = Invoke-Adb -Arguments @(
        'shell', 'sh', '-c',
        'getprop ro.build.version.sdk; getprop ro.product.cpu.abilist; getprop ro.build.fingerprint; id'
    )
    $environment.Stdout | Set-Content -Encoding UTF8 (Join-Path $evidenceRoot 'environment.txt')
    Write-Output "PASS: $AvdName ($serial); extracted/direct release instrumentation 2/2"
}
finally {
    foreach ($variant in $variants) {
        if (Test-Path $adb) {
            Invoke-Adb -Arguments @('uninstall', "$($variant.Package).test") -AllowFailure -TimeoutSeconds 10 | Out-Null
            Invoke-Adb -Arguments @('uninstall', $variant.Package) -AllowFailure -TimeoutSeconds 10 | Out-Null
        }
    }
    if (Test-Path $adb) {
        Invoke-Adb -Arguments @('emu', 'kill') -AllowFailure -TimeoutSeconds 10 | Out-Null
    }
    if ($emulatorProcess -and -not $emulatorProcess.HasExited) {
        if (-not $emulatorProcess.WaitForExit(10000)) {
            Stop-Process -Id $emulatorProcess.Id -Force -ErrorAction SilentlyContinue
        }
    }
    $newProcesses = Get-Process -Name emulator,qemu-system-x86_64,qemu-system-aarch64 -ErrorAction SilentlyContinue |
        Where-Object { $baselineProcessIds -notcontains $_.Id }
    foreach ($process in $newProcesses) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
    $remaining = & $adb devices 2>$null
    if ($remaining -match "(?m)^$([regex]::Escape($serial))\s") {
        Write-Error "$serial remains visible after cleanup"
    }
}
