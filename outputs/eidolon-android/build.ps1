param([ValidateSet('Debug','Release')][string]$Configuration='Debug', [string]$Gradle='gradle')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'tools/check-release-notes.ps1')
if(!$env:JAVA_HOME){throw 'Set JAVA_HOME to JDK 17.'}
if(!$env:ANDROID_HOME -and !$env:ANDROID_SDK_ROOT){throw 'Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0.'}
& $Gradle --no-daemon --console=plain -p $PSScriptRoot ('assemble'+$Configuration)
if($LASTEXITCODE -ne 0){throw 'Gradle build failed'}
Write-Host 'APK output: work/android-build-1.15/gradle/outputs/apk/'
