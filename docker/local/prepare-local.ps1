#requires -Version 5.1
# 원본 .env 파일을 수정하지 않고 Docker 전용 복사본을 신규 생성한다.
param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)
$ErrorActionPreference = 'Stop'
$source = Join-Path $Root 'TripPamineBE/.env'
$destination = Join-Path $Root 'docker/local/secrets/backend.env'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
  throw 'TripPamineBE/.env 파일이 없습니다. 별도의 backend.env를 직접 준비하세요.'
}
if (Test-Path -LiteralPath $destination) {
  Write-Host '기존 backend.env가 있으므로 덮어쓰지 않습니다.'
  exit 0
}
Copy-Item -LiteralPath $source -Destination $destination -ErrorAction Stop
Write-Host 'Docker 전용 backend.env 복사본을 생성했습니다.'
Write-Warning 'DB_URL의 TNS_ADMIN을 Windows 경로에서 /opt/trippamine/wallets로 수정하세요.'
Write-Warning '인증정보 파일은 절대 Git에 추가하거나 채팅에 붙여넣지 마세요.'
