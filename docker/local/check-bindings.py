"""Compose 구성에서 기존 프로젝트 API 및 서비스를 올바르게 참조하는지 정적 확인한다."""
from pathlib import Path
import sys
try:
    import yaml
except ImportError:
    print('PyYAML이 없어 구성 정적 검사를 건너뜁니다.')
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[2]
with (ROOT / 'compose.local.yaml').open(encoding='utf-8') as f:
    conf = yaml.safe_load(f)
services = conf['services']
assert set(services) == {'backend', 'frontend', 'admin', 'vision', 'qwen'}
assert services['vision']['build']['context'] == './vision-ai-test'
assert services['backend']['environment']['TRIPPAMINE_MANAGER_SLM_BASE_URL'].endswith('qwen:8080}')
assert '"auto-start":false' in services['backend']['environment']['SPRING_APPLICATION_JSON']
assert all(any('127.0.0.1:' in p for p in service.get('ports', [])) for service in services.values())
assert 'emotion' not in services  # Emotion 실행 코드가 없는 단계에서 가짜 컨테이너는 만들지 않는다.
for path in [
    'TripPamineFE/vite.local.config.ts',
    'TripPamineAdmin/vite.local.config.ts',
    'vision-ai-test/Dockerfile.local',
    'vision-ai-test/requirements.local.txt',
    'docker/local/check-local.ps1',
    'docker/local/prepare-local.ps1',
]:
    assert (ROOT / path).is_file(), path
print('PASS: Compose 서비스, 내부 통신 주소, 포트 제한 및 필수 파일 확인')
