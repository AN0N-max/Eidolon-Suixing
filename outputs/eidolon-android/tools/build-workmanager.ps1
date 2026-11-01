param([string]$Jdk='', [string]$SigningStore='', [string]$SigningAlias='local-test')
$ErrorActionPreference='Stop'
$source=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'check-release-notes.ps1')
$workspace=Split-Path (Split-Path $source -Parent) -Parent
if(!$Jdk){$Jdk=(Get-ChildItem (Join-Path $workspace 'work/android-tools/jdk') -Directory | Select-Object -First 1).FullName}
if(!$SigningStore){$SigningStore=Join-Path $workspace 'work/android-signing/local-test.jks'}
if(!(Test-Path -LiteralPath $SigningStore)){throw 'Original signing keystore is required'}
$env:JAVA_HOME=$Jdk
$env:ANDROID_HOME=Join-Path $workspace 'work/android-tools/sdk'
$env:GRADLE_USER_HOME=Join-Path $workspace 'work/android-gradle-cache'
# This Windows host rejects Java AF_UNIX connects. An unavailable socket directory
# makes the JDK use its supported TCP loopback fallback, only for build processes.
$socketFallback=Join-Path $env:TEMP ('eidolon-gradle-tcp-'+[guid]::NewGuid().ToString('N'))
$env:JAVA_TOOL_OPTIONS=($env:JAVA_TOOL_OPTIONS+' "-Djdk.net.unixdomain.tmpdir='+$socketFallback+'"').Trim()
$gradle=Join-Path $workspace 'work/android-tools/gradle-8.9/bin/gradle.bat'
if(!(Test-Path -LiteralPath $gradle)){throw 'Download and unzip Gradle 8.9 into work/android-tools first (see DEVELOPMENT-1.2.md).'}
$build=Join-Path $workspace 'work/android-build-1.17'
$tools=Join-Path $workspace 'work/android-tools/build-tools/android-15'
$argsList=@('--no-daemon','--console=plain','-p',$source,'assembleRelease')
& $gradle @argsList
if($LASTEXITCODE -ne 0){throw 'Gradle build failed'}
$unsigned=Join-Path $build 'gradle/outputs/apk/release/EidolonAndroid-release-unsigned.apk'
if(!(Test-Path -LiteralPath $unsigned)){$unsigned=Join-Path $build 'gradle/outputs/apk/release/eidolon-android-release-unsigned.apk'}
& (Join-Path $tools 'zipalign.exe') -f -P 16 4 $unsigned (Join-Path $build 'aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
$apk=Join-Path $workspace 'outputs/Eidolon-1.17-original-upgrade.apk'
& (Join-Path $Jdk 'bin/java.exe') -jar (Join-Path $tools 'lib/apksigner.jar') sign --ks $SigningStore --ks-key-alias $SigningAlias --ks-pass pass:android --key-pass pass:android --out $apk (Join-Path $build 'aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Signing failed'}
& (Join-Path $Jdk 'bin/java.exe') -jar (Join-Path $tools 'lib/apksigner.jar') verify --verbose --print-certs $apk
if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
& (Join-Path $tools 'zipalign.exe') -c -P 16 4 $apk
if($LASTEXITCODE -ne 0){throw 'Alignment verification failed'}
Write-Host "APK: $apk"
