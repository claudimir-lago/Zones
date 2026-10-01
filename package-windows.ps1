[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SourceDir,
    [Parameter(Mandatory)][ValidatePattern('^v\d+\.\d+\.\d+$')][string]$Tag,
    [Parameter(Mandatory)][string]$JavaHome,
    [string]$Maven = 'mvn.cmd',
    [string]$MavenRepository,
    [string]$OutputDir = (Join-Path $PSScriptRoot 'dist')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not [Environment]::Is64BitOperatingSystem -or $env:OS -ne 'Windows_NT') { throw 'Windows x64 is required.' }
$SourceDir = (Resolve-Path -LiteralPath $SourceDir).Path
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"
[xml]$pom = Get-Content -LiteralPath (Join-Path $SourceDir 'pom.xml') -Raw
$version = [string]$pom.project.version
if ($Tag -cne "v$version") { throw 'Release tag and pom.xml version must match.' }
$javaRelease = Get-Content -LiteralPath (Join-Path $JavaHome 'release') -Raw
if ($javaRelease -notmatch 'IMPLEMENTOR="Eclipse Adoptium"' -or $javaRelease -notmatch 'JAVA_VERSION="17\.' -or $javaRelease -notmatch 'OS_ARCH="(x86_64|amd64)"') { throw 'Use Eclipse Temurin JDK 17 for Windows x64.' }
function Run-Native([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}
$sourceCommit = (& git -C $SourceDir rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Source must be a Git checkout.' }
$tagCommit = (& git -C $SourceDir rev-parse "$Tag^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or $sourceCommit -cne $tagCommit) { throw 'Source checkout must point to the requested release tag.' }
if (@(& git -C $SourceDir status --porcelain --untracked-files=no).Count -ne 0) { throw 'Tracked source files must be clean.' }
$buildId = [Guid]::NewGuid().ToString('N').Substring(0,8)
$work = Join-Path ([IO.Path]::GetFullPath($OutputDir)) "build-$version-$buildId"
$inputDir = Join-Path $work 'input'
$runtime = Join-Path $work 'runtime'
$imageDest = Join-Path $work 'image'
$deliverables = Join-Path $work 'deliverables'
New-Item -ItemType Directory -Path $inputDir,$imageDest,$deliverables | Out-Null
$mavenArgs = @('-B','--no-transfer-progress','clean','verify')
if ($MavenRepository) { $mavenArgs = @("-Dmaven.repo.local=$([IO.Path]::GetFullPath($MavenRepository))") + $mavenArgs }
Push-Location $SourceDir
try { Run-Native $Maven $mavenArgs } finally { Pop-Location }
Copy-Item -LiteralPath (Join-Path $SourceDir "target/zones-$version.jar") -Destination $inputDir
Copy-Item -LiteralPath (Join-Path $SourceDir 'target/lib') -Destination $inputDir -Recurse
Run-Native (Join-Path $JavaHome 'bin/jlink.exe') @('--add-modules','java.desktop,java.logging,java.xml,java.naming,jdk.unsupported','--strip-debug','--no-header-files','--no-man-pages','--output',$runtime)
Run-Native (Join-Path $JavaHome 'bin/jpackage.exe') @('--type','app-image','--name','Zones','--app-version',$version,'--vendor','Claudimir Lucio do Lago','--description','Capillary electrophoresis data analysis','--input',$inputDir,'--main-jar',"zones-$version.jar",'--main-class','zones.Main','--runtime-image',$runtime,'--dest',$imageDest)
$packageName = "Zones-$version-windows-x64"
$package = Join-Path $imageDest $packageName
Rename-Item -LiteralPath (Join-Path $imageDest 'Zones') -NewName $packageName
foreach ($name in @('LICENSE','THIRD-PARTY-NOTICES.md','CITATION.cff','licenses')) { Copy-Item -LiteralPath (Join-Path $SourceDir $name) -Destination $package -Recurse }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'USER_GUIDE.html') -Destination $package
New-Item -ItemType Directory -Path (Join-Path $package 'source') | Out-Null
Run-Native 'git' @('-C',$SourceDir,'archive','--format=zip',"--prefix=Zones-$version/","--output=$(Join-Path $package "source/Zones-$version-source.zip")",$sourceCommit)
$toolCommit = (& git -C $PSScriptRoot rev-parse HEAD).Trim()
@"
Zones $version - Windows x64 portable package
Application source: https://github.com/claudimir-lago/Zones/tree/$sourceCommit
Application commit: $sourceCommit
Packaging checkout commit: $toolCommit
Guide SHA256: $((Get-FileHash (Join-Path $package 'USER_GUIDE.html')).Hash)
Build UTC: $([DateTime]::UtcNow.ToString('o'))
Runtime distribution: Eclipse Temurin (unmodified modules linked with jlink)
$javaRelease
"@ | Set-Content -LiteralPath (Join-Path $package 'BUILD-INFO.txt') -Encoding utf8
$runtimeTag = if ($javaRelease -match 'JAVA_RUNTIME_VERSION="([^"]+)"') { 'jdk-' + $Matches[1] } else { throw 'Missing runtime version' }
@"
This package includes an Eclipse Temurin OpenJDK 17 runtime.
Retain the original license and legal notices in runtime/legal.
OpenJDK uses GPL version 2 with the Classpath Exception; individual components
may have additional notices. Zones remains GPL-3.0-or-later.
Exact upstream binary release and corresponding source archive:
https://github.com/adoptium/temurin17-binaries/releases/tag/$runtimeTag
Obtain the OpenJDK17U-sources archive from that release for the full runtime source.
Build scripts and configuration: https://github.com/adoptium/temurin-build
The application source archive is included in source/. JFreeChart source is
included in licenses/. Runtime libraries remain replaceable JARs in app/lib/.
"@ | Set-Content -LiteralPath (Join-Path $package 'JAVA-NOTICES.txt') -Encoding utf8
@"
Zones $version

1. Extract the ENTIRE ZIP to a folder.
2. Double-click Zones.exe. No Java installation is needed.
3. Open USER_GUIDE.html for the English user guide (works offline).

Keep app/ and runtime/ next to Zones.exe. Windows x64 is required.
Keep original data, exports and calibration notes outside the application folder.
Official releases: https://github.com/claudimir-lago/Zones/releases
License: GPL-3.0-or-later; see LICENSE and third-party notices.
"@ | Set-Content -LiteralPath (Join-Path $package 'START-HERE.txt') -Encoding utf8
# Exercise scientific processing, exports and Swing using only the shipped runtime/libraries.
$smokeDir = Join-Path $work 'smoke'
New-Item -ItemType Directory -Path $smokeDir | Out-Null
$classPath = "$(Join-Path $package "app/zones-$version.jar");$(Join-Path $package 'app/lib/*')"
Run-Native (Join-Path $JavaHome 'bin/javac.exe') @('--release','17','-cp',$classPath,'-d',$smokeDir,(Join-Path $PSScriptRoot 'PackageSmoke.java'))
Run-Native (Join-Path $package 'runtime/bin/java.exe') @('-cp',"$smokeDir;$classPath",'PackageSmoke',$smokeDir)
# Check the native launcher, without leaving a window or process behind.
$launch = Start-Process -FilePath (Join-Path $package 'Zones.exe') -WorkingDirectory $package -WindowStyle Hidden -PassThru
try { Start-Sleep -Seconds 4; if ($launch.HasExited) { throw "Native launcher exited unexpectedly: $($launch.ExitCode)" } }
finally { if (-not $launch.HasExited) { Stop-Process -Id $launch.Id } }
$zipPath = Join-Path $deliverables "$packageName.zip"
Compress-Archive -LiteralPath $package -DestinationPath $zipPath -CompressionLevel Optimal
# Inspect the final archive rather than only the staging directory.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    foreach ($required in @('Zones.exe','USER_GUIDE.html','START-HERE.txt','LICENSE','JAVA-NOTICES.txt','BUILD-INFO.txt',"app/zones-$version.jar",'runtime/bin/java.exe',"source/Zones-$version-source.zip",'licenses/jfreechart-1.5.6-sources.jar')) {
        if (-not ($archive.Entries.FullName.Replace('\','/') -contains "$packageName/$required")) { throw "Missing ZIP entry: $required" }
    }
} finally { $archive.Dispose() }
$checksum = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
"$checksum  $packageName.zip" | Set-Content -LiteralPath "$zipPath.sha256" -Encoding ascii
Copy-Item -LiteralPath (Join-Path $package 'USER_GUIDE.html') -Destination (Join-Path $deliverables "Zones-$version-User-Guide.html")
Write-Host "Verified portable package: $zipPath"
if ($env:GITHUB_OUTPUT) { "asset_dir=$deliverables" >> $env:GITHUB_OUTPUT }
