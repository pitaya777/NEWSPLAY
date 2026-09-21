$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw '缺少 .env，请按 .env.example 配置数据库和初始管理员密码。' }
Get-Content -LiteralPath $envFile -Encoding UTF8 | ForEach-Object {
  if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
try { chcp 65001 | Out-Null } catch {}

# Add a UTF-8 BOM once so Windows editors can reliably detect the log encoding.
$logDirSetting = [Environment]::GetEnvironmentVariable('NEWSPLAY_LOG_DIR', 'Process')
if ([string]::IsNullOrWhiteSpace($logDirSetting)) { $logDirSetting = '../logs' }
$backendRoot = Join-Path $projectRoot 'backend'
$logDir = if ([IO.Path]::IsPathRooted($logDirSetting)) {
  [IO.Path]::GetFullPath($logDirSetting)
} else {
  [IO.Path]::GetFullPath((Join-Path $backendRoot $logDirSetting))
}
[IO.Directory]::CreateDirectory($logDir) | Out-Null
$logFile = Join-Path $logDir 'newsplay.log'
try {
  $existing = if (Test-Path -LiteralPath $logFile) { [IO.File]::ReadAllBytes($logFile) } else { [byte[]]@() }
  $hasBom = $existing.Length -ge 3 -and $existing[0] -eq 0xEF -and $existing[1] -eq 0xBB -and $existing[2] -eq 0xBF
  if (-not $hasBom) {
    $withBom = New-Object byte[] ($existing.Length + 3)
    $withBom[0] = 0xEF; $withBom[1] = 0xBB; $withBom[2] = 0xBF
    if ($existing.Length -gt 0) { [Array]::Copy($existing, 0, $withBom, 3, $existing.Length) }
    [IO.File]::WriteAllBytes($logFile, $withBom)
  }
} catch {
  Write-Warning '日志文件正在使用中，UTF-8 标记将在下次完整重启后补充。'
}
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { $mavenPath = $maven.Source }
else {
  $mavenPath = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mavenPath) { throw '未找到 Maven，请先安装 Maven 并加入 PATH。' }
Push-Location $backendRoot
try { & $mavenPath spring-boot:run }
finally { Pop-Location }
