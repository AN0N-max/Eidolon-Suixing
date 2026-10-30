param([string]$GradlePath='',[string]$NotesPath='')
$ErrorActionPreference='Stop'
$source=Split-Path $PSScriptRoot -Parent
if(!$GradlePath){$GradlePath=Join-Path $source 'build.gradle'}
if(!$NotesPath){$NotesPath=Join-Path $source 'assets/update-notices.json'}
$gradle=Get-Content -LiteralPath $GradlePath -Raw -Encoding UTF8
$version=[regex]::Match($gradle,"versionName\s+'([^']+)'").Groups[1].Value
$codeText=[regex]::Match($gradle,'versionCode\s+(\d+)').Groups[1].Value
if(!$version -or !$codeText){throw 'Release version could not be read from build.gradle'}
$notes=Get-Content -LiteralPath $NotesPath -Raw -Encoding UTF8 | ConvertFrom-Json
if($notes.schema -ne 1 -or !$notes.releases -or $notes.releases.Count -gt 200){throw 'Update notices must contain 1 to 200 releases'}
$previous=[int]::MaxValue
$versions=New-Object 'System.Collections.Generic.HashSet[string]'
foreach($release in $notes.releases){
 if($release.version -notmatch '^\d+\.\d+(\.\d+)?$' -or !$versions.Add($release.version)){throw 'Invalid or duplicate update version'}
 if(($release.code -isnot [int] -and $release.code -isnot [long]) -or $release.code -le 0 -or $release.code -ge $previous){throw 'Update codes must be unique and newest first'}
 $previous=$release.code
 if([string]::IsNullOrWhiteSpace($release.title) -or $release.title.Length -gt 80 -or !$release.sections){throw 'Update title and sections are required'}
 foreach($section in $release.sections){
  if([string]::IsNullOrWhiteSpace($section.title) -or !$section.items){throw 'Update sections must describe the changes'}
  foreach($item in $section.items){if($item -isnot [string] -or [string]::IsNullOrWhiteSpace($item) -or $item.Length -gt 1000){throw 'Invalid update item'}}
 }
}
if($notes.releases[0].version -ne $version -or $notes.releases[0].code -ne [int]$codeText){throw 'Add the current version update notice before building'}
Write-Host "Update notice verified: $version / $codeText"
