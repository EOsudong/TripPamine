@echo off
:: 실행 위치를 현재 배치 파일이 있는 폴더로 고정한다.
cd /d "%~dp0"

:: 감정/Qwen3 환경 대신 Vision 전용 venv만 사용한다.
if not exist "venv\Scripts\python.exe" (
    echo [Vision AI] vision-ai-test\venv\Scripts\python.exe not found.
    exit /b 1
)

:: 외부 공개 및 cmd start 중복 실행 없이 로컬 FastAPI를 시작한다.
"venv\Scripts\python.exe" -m uvicorn main:app --host 127.0.0.1 --port 8000
