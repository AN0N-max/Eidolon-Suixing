# Compatibility entry; release signing is private and not part of public builds.
param([ValidateSet('Debug','Release')][string]$Configuration='Debug', [string]$Gradle='gradle')
& (Join-Path (Split-Path $PSScriptRoot -Parent) 'build.ps1') @PSBoundParameters
