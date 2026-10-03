# 将本机默认 debug.keystore 复制到仓库 Backups/（该目录已被 .gitignore），并打印 SHA-256。
# 用法（PowerShell 7）：
#   & 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File '.\scripts\backup-debug-keystore.ps1'

$ErrorActionPreference = 'Stop'
$src = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path -LiteralPath $src)) {
    Write-Error "找不到 debug.keystore：$src"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$backups = Join-Path $repoRoot 'Backups'
New-Item -ItemType Directory -Force -Path $backups | Out-Null

$stamp = Get-Date -Format 'yyyyMMdd'
$dest = Join-Path $backups "yunayu-debug-keystore-$stamp.keystore"
Copy-Item -LiteralPath $src -Destination $dest -Force
Write-Host "已备份到：$dest"

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if ($null -eq $keytool) {
    Write-Warning '未找到 keytool，跳过指纹打印。请在 JDK bin 目录执行 keytool -list -v。'
    exit 0
}

& keytool -list -v -keystore $dest -storepass android -alias androiddebugkey
exit $LASTEXITCODE
