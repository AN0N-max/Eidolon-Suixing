$ErrorActionPreference='Stop'
$workspace=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jdk=$env:JAVA_HOME
if(!$jdk){throw 'Set JAVA_HOME to JDK 17'}
$python=if($env:EIDOLON_PYTHON){$env:EIDOLON_PYTHON}else{'python'}
$android=Join-Path $env:ANDROID_HOME 'platforms/android-35/android.jar'
$json=if($env:JSON_JAR){$env:JSON_JAR}else{Join-Path $workspace 'work/android-test-tools/json.jar'}
$classes=Join-Path $workspace 'work/android-build-1.12/gradle/intermediates/javac/release/compileReleaseJavaWithJavac/classes'
$testClasses=Join-Path $workspace 'work/android-tests/classes'
New-Item -ItemType Directory -Force $testClasses | Out-Null
$cp="$json;$classes;$android"
$testSources=@(Get-ChildItem (Join-Path $PSScriptRoot 'tests/com/eidolon/sc/mobile') -Filter '*Tests.java' | ForEach-Object { $_.FullName })
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 -cp $cp -d $testClasses @testSources
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.CoreTests
if($LASTEXITCODE -ne 0){throw 'Core checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.FeatureTests
if($LASTEXITCODE -ne 0){throw 'Feature checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ScheduleTests
if($LASTEXITCODE -ne 0){throw 'Schedule checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.PromiseTests
if($LASTEXITCODE -ne 0){throw 'Promise checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.PersonalMergeTests
if($LASTEXITCODE -ne 0){throw 'Personal merge checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_migration.py')
if($LASTEXITCODE -ne 0){throw 'SQLite checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.HeartsTests (Join-Path $PSScriptRoot 'assets/hearts.json')
if($LASTEXITCODE -ne 0){throw 'Hibi hearts checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.AnniversariesTests
if($LASTEXITCODE -ne 0){throw 'Anniversary checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ScheduleGenerationTests
if($LASTEXITCODE -ne 0){throw 'Schedule generation checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.LetterPlanTests
if($LASTEXITCODE -ne 0){throw 'Letter planning checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_letters.py')
if($LASTEXITCODE -ne 0){throw 'Letter SQLite checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.DiaryTests
if($LASTEXITCODE -ne 0){throw 'Diary checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_diary.py')
if($LASTEXITCODE -ne 0){throw 'Diary SQLite checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.HeartSceneTests
if($LASTEXITCODE -ne 0){throw 'Heart scene checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.AppealTests
if($LASTEXITCODE -ne 0){throw 'Appeal checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_appeals.py')
if($LASTEXITCODE -ne 0){throw 'Appeal SQLite checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ReplyWorkTests
if($LASTEXITCODE -ne 0){throw 'Background reply timing checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ReachConfigTests
if($LASTEXITCODE -ne 0){throw 'Proactive settings checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_reach_settings.py')
if($LASTEXITCODE -ne 0){throw 'Proactive SQL checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.NightScheduleTests
if($LASTEXITCODE -ne 0){throw 'Overnight schedule checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ReachPlanTests
if($LASTEXITCODE -ne 0){throw 'Daily reach planner checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_reach_plans.py')
if($LASTEXITCODE -ne 0){throw 'Daily reach task and unread SQLite checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.TimelineTests
if($LASTEXITCODE -ne 0){throw 'Timeline and fairness checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_timeline.py')
if($LASTEXITCODE -ne 0){throw 'Timeline SQLite and source checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.CharacterPromptsTests (Join-Path $PSScriptRoot 'assets/characters.json')
if($LASTEXITCODE -ne 0){throw 'Character prompt checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ReplyTimeTests
if($LASTEXITCODE -ne 0){throw 'Reply time isolation checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.WritingMorningTests
if($LASTEXITCODE -ne 0){throw 'Morning writing checks failed'}
& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ChatEntryTests
if($LASTEXITCODE -ne 0){throw 'Chat entry checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_unread_badges.py')
if($LASTEXITCODE -ne 0){throw 'Unread badge SQLite checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.ReachPlanCheckTests
if($LASTEXITCODE -ne 0){throw 'Local planner recovery checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_plan_recovery.py')
if($LASTEXITCODE -ne 0){throw 'Local planner SQL recovery checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.UiReadTests
if($LASTEXITCODE -ne 0){throw 'UI data scope checks failed'}
& $python (Join-Path $PSScriptRoot 'tests/test_ui_queries.py')
if($LASTEXITCODE -ne 0){throw 'UI scoped SQL checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.LocalDbRetryTests
if($LASTEXITCODE -ne 0){throw 'Local database retry checks failed'}
foreach($check in @('test_shared_store.py','test_db_contention.py')){
    & $python (Join-Path $PSScriptRoot ('tests/'+$check))
    if($LASTEXITCODE -ne 0){throw ($check+' failed')}
}

& $python (Join-Path $PSScriptRoot 'tests/test_reach_open.py')
if($LASTEXITCODE -ne 0){throw 'Open proactive eligibility checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.LauncherBadgeTests
if($LASTEXITCODE -ne 0){throw 'Launcher badge rule checks failed'}

& (Join-Path $jdk 'bin/java.exe') -cp "$testClasses;$cp" com.eidolon.sc.mobile.DailyScheduleClockTests
if($LASTEXITCODE -ne 0){throw 'Schedule 03:00 boundary checks failed'}
