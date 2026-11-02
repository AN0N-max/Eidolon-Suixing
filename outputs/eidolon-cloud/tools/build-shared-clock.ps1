param([Parameter(Mandatory=$true)][string]$AndroidSource, [string]$Jdk=$env:JAVA_HOME, [string]$Python='python')
$ErrorActionPreference='Stop'
if (-not $Jdk) { throw 'Set JAVA_HOME or pass -Jdk' }
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskAndroid=(Resolve-Path -LiteralPath $AndroidSource).Path
& $Python (Join-Path $PSScriptRoot 'verify_domain.py') --android-source $taskAndroid
if ($LASTEXITCODE -ne 0) { throw 'Existing business baseline differs; review before extracting' }
$taskBuild=Join-Path $taskRoot ('.local/clock-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
$taskClasses=Join-Path $taskBuild 'classes'
$taskTests=Join-Path $taskBuild 'tests'
New-Item -ItemType Directory -Force -Path $taskClasses,$taskTests | Out-Null
$taskPackage='com/eidolon/sc/mobile'
& (Join-Path $Jdk 'bin/javac.exe') --release 8 -encoding UTF-8 -d $taskClasses (Join-Path $taskAndroid ('src/'+$taskPackage+'/DailyScheduleClock.java')) (Join-Path $taskRoot ('shared-clock/'+$taskPackage+'/SharedClockBridge.java'))
if ($LASTEXITCODE -ne 0) { throw 'Shared clock compilation failed' }
& (Join-Path $Jdk 'bin/javac.exe') --release 8 -encoding UTF-8 -cp $taskClasses -d $taskTests (Join-Path $taskAndroid ('tests/'+$taskPackage+'/DailyScheduleClockTests.java'))
if ($LASTEXITCODE -ne 0) { throw 'Original clock test compilation failed' }
& (Join-Path $Jdk 'bin/java.exe') -cp "$taskClasses;$taskTests" com.eidolon.sc.mobile.DailyScheduleClockTests
if ($LASTEXITCODE -ne 0) { throw 'Original clock tests failed' }
$taskJar=Join-Path $taskBuild 'shared-clock.jar'
& (Join-Path $Jdk 'bin/jar.exe') --create --file $taskJar --main-class com.eidolon.sc.mobile.SharedClockBridge -C $taskClasses .
if ($LASTEXITCODE -ne 0) { throw 'Shared clock packaging failed' }
Write-Output $taskJar
