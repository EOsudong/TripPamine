@echo off
:: 실행 위치를 현재 batch 파일이 있는 디렉터리로 강제 이동
cd /d "%~dp0"

:: 파이썬 가상환경 활성화
call venv\Scripts\activate.bat

:: FastAPI 서버 구동
python -m uvicorn main:app --host 0.0.0.0 --port 8000