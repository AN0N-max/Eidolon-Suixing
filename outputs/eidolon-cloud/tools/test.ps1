param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskLocal = Join-Path $taskRoot '.local'
New-Item -ItemType Directory -Force -Path $taskLocal | Out-Null
$taskTemp = [IO.Path]::GetFullPath((Join-Path $taskLocal ('t-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))))
if (-not $taskTemp.StartsWith([IO.Path]::GetFullPath($taskLocal) + [IO.Path]::DirectorySeparatorChar)) { throw 'Test path must stay inside .local' }
if (Test-Path -LiteralPath $taskTemp) { throw 'Test path must be new; never remove an existing directory' }
Push-Location $taskRoot
try {
    & $Python -m pytest -q --tb=short -p no:cacheprovider "--basetemp=$taskTemp" tests
    if ($LASTEXITCODE -ne 0) { throw 'Cloud framework tests failed' }
} finally { Pop-Location }
