#requires -Version 5.1
# 현재 PC의 기존 환경을 변경하지 않는 읽기 전용 진단 스크립트.
param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $Root

Write-Host '=== Docker Desktop / Compose ==='
try { docker version --format '{{.Server.Version}}' } catch { Write-Warning 'Docker Engine에 연결할 수 없습니다.' }
try { docker compose version } catch { Write-Warning 'Docker Compose를 사용할 수 없습니다.' }

Write-Host '=== 필수 소스 파일 ==='
$files = @(
  'TripPamineBE/gradlew',
  'TripPamineBE/build.gradle',
  'TripPamineFE/package-lock.json',
  'TripPamineAdmin/package-lock.json',
  'vision-ai-test/main.py',
  'vision-ai-test/vision_metrics.py',
  'compose.local.yaml'
)
foreach ($file in $files) {
  if (Test-Path -LiteralPath $file -PathType Leaf) { Write-Host "OK: $file" }
  else { Write-Warning "MISSING: $file" }
}

Write-Host '=== 모델/비밀정보 파일 존재 여부(내용 미표시) ==='
if (Test-Path 'docker/local/secrets/backend.env') { Write-Host 'OK: backend.env' }
else { Write-Warning 'MISSING: docker/local/secrets/backend.env' }
if (Test-Path 'TripPamineBE/src/main/resources/wallets/tnsnames.ora') { Write-Host 'OK: tnsnames.ora' }
else { Write-Warning 'MISSING: tnsnames.ora' }

Write-Host '=== 기존 포트 점유 ==='
foreach ($port in @(5173,5174,8080,8000,18001,18002)) {
  $used = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
  if ($used) { Write-Host "IN USE: $port" } else { Write-Host "FREE: $port" }
}

Write-Host '=== Docker Compose 정적 검증 ==='
if (Test-Path 'docker/local/secrets/backend.env') {
  docker compose --env-file docker/local/local.env.example -f compose.local.yaml config --quiet
} else {
  Write-Warning 'backend.env를 준비한 다음 docker compose config를 수행하세요.'
}
