param(
    [string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug'),
    [switch]$Offline,
    [string]$JavaHome,
    [string]$AndroidHome
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
if ($AndroidHome) { $env:ANDROID_HOME = $AndroidHome }
$onWindows = $env:OS -eq 'Windows_NT'
$javaBinary = if ($onWindows) { 'java.exe' } else { 'java' }
if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
}
if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
if (-not $env:ANDROID_HOME) {
    $profileRoot = [Environment]::GetFolderPath('UserProfile')
    if ($onWindows -and $env:LOCALAPPDATA) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    elseif ($IsMacOS) { $env:ANDROID_HOME = Join-Path $profileRoot 'Library/Android/sdk' }
    elseif ($profileRoot) { $env:ANDROID_HOME = Join-Path $profileRoot 'Android/Sdk' }
}
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) { throw 'Set JAVA_HOME (JDK 17) and ANDROID_HOME (SDK 36), or pass -JavaHome and -AndroidHome.' }
if (-not (Test-Path (Join-Path $env:JAVA_HOME "bin/$javaBinary"))) { throw 'Set JAVA_HOME to a JDK 17 installation.' }
if (-not (Test-Path (Join-Path $env:ANDROID_HOME 'platforms/android-36'))) { throw 'Set ANDROID_HOME to an SDK with Android 36.' }
$javaVersion = (& (Join-Path $env:JAVA_HOME "bin/$javaBinary") -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "17\.') { throw 'JDK 17 is required; the selected runtime does not match.' }
Write-Output "Using JDK: $env:JAVA_HOME"
Write-Output "Using Android SDK: $env:ANDROID_HOME"
$wrapper = Join-Path $projectRoot $(if ($onWindows) { 'android/gradlew.bat' } else { 'android/gradlew' })
if (-not (Test-Path $wrapper)) { throw 'Gradle wrapper is missing.' }
Push-Location (Join-Path $projectRoot 'android')
try {
    $gradleArgs = @('--console=plain', '--no-daemon') + $Tasks
    if ($Offline) { $gradleArgs += '--offline' }
    & $wrapper @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Android validation failed: $LASTEXITCODE" }
} finally { Pop-Location }
