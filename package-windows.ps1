[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "dist")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$targetDirectory = Join-Path $projectRoot "target"
$stagingDirectory = Join-Path $targetDirectory "jpackage-input"
$iconPath = Join-Path $projectRoot "src\main\resources\com\commonplace\assets\commonplace_icon.ico"
$outputDirectoryFullPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$appImageDirectory = Join-Path $outputDirectoryFullPath "Commonplace"

foreach ($commandName in @("mvn", "jpackage")) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "'$commandName' was not found. Install a JDK 21 and Maven, then try again."
    }
}

[xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot "pom.xml") -Raw
$artifactId = [string]$pom.project.artifactId
$projectVersion = [string]$pom.project.version
$jarPath = Join-Path $targetDirectory "$artifactId-$projectVersion.jar"
$appVersion = $projectVersion -replace "-.*$", ""

Push-Location $projectRoot
try {
    & mvn clean package
    if ($LASTEXITCODE -ne 0) {
        throw "The Maven build failed with exit code $LASTEXITCODE."
    }

    New-Item -ItemType Directory -Path $stagingDirectory -Force | Out-Null
    $dependencyArguments = @(
        "dependency:copy-dependencies"
        "-DincludeScope=runtime"
        "-DoutputDirectory=$stagingDirectory"
    )
    & mvn @dependencyArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Copying the runtime dependencies failed with exit code $LASTEXITCODE."
    }

    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "The application JAR was not created at '$jarPath'."
    }

    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $stagingDirectory "Commonplace.jar")
    New-Item -ItemType Directory -Path $outputDirectoryFullPath -Force | Out-Null

    if (Test-Path -LiteralPath $appImageDirectory) {
        $expectedAppImageDirectory = [System.IO.Path]::GetFullPath(
            (Join-Path $outputDirectoryFullPath "Commonplace")
        )
        $resolvedAppImageDirectory = [System.IO.Path]::GetFullPath($appImageDirectory)
        if ($resolvedAppImageDirectory -ne $expectedAppImageDirectory) {
            throw "Refusing to replace the unexpected directory '$resolvedAppImageDirectory'."
        }
        Remove-Item -LiteralPath $resolvedAppImageDirectory -Recurse -Force
    }

    $jpackageArguments = @(
        "--type", "app-image"
        "--dest", $outputDirectoryFullPath
        "--name", "Commonplace"
        "--input", $stagingDirectory
        "--main-jar", "Commonplace.jar"
        "--main-class", "com.commonplace.Launcher"
        "--icon", $iconPath
        "--app-version", $appVersion
        "--vendor", "Commonplace"
        "--description", "Commonplace"
        "--add-modules", "java.base,java.desktop,java.logging,java.net.http,java.scripting,java.sql,jdk.crypto.ec,jdk.jfr,jdk.unsupported"
        "--java-options", "-Dfile.encoding=UTF-8"
    )
    & jpackage @jpackageArguments
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

$executablePath = Join-Path $appImageDirectory "Commonplace.exe"
if (-not (Test-Path -LiteralPath $executablePath -PathType Leaf)) {
    throw "Packaging finished without creating '$executablePath'."
}

Write-Host ""
Write-Host "Created: $executablePath"
Write-Host "Keep the entire '$appImageDirectory' folder together when moving the app."
Write-Host "Launch Commonplace, right-click its taskbar icon, and select 'Pin to taskbar'."
