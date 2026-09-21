param(
  [int]$Tail = 200,
  [switch]$Wait
)

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$logFile = Join-Path $projectRoot 'logs\newsplay.log'
if (-not (Test-Path -LiteralPath $logFile)) {
  throw '日志文件尚未生成，请先启动后端。'
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
if ($Wait) {
  Get-Content -LiteralPath $logFile -Encoding UTF8 -Tail $Tail -Wait
} else {
  Get-Content -LiteralPath $logFile -Encoding UTF8 -Tail $Tail
}
