### 다음 단계 (우선순위 순)

  [1] Baseline 결과 수치 확인
      B2 char TF-IDF의 fine F1이 얼마인지 실제로 돌려보기
      → 이후 "얼마나 개선됐는가"의 기준이 됨

  [2] Class imbalance 대응 결정
      analyze_profile_label_support.py 결과 확인
      → 심한 경우: focal loss, oversampling, or B1 balanced 결과 비교
      → 60개 레이블 중 support < 50인 것이 몇 개인지 파악 먼저

  [3] KLUE-RoBERTa fine-tuning (Korean BERT 계열)
      TF-IDF와 달리 문맥/어순 이해 가능
      GPU 불필요 — CPU 환경에서도 fine-tuning 가능한 규모
      목표: fine Macro F1 > 0.65

  [4] LLM SFT (QLoRA / Unsloth)
      SFT v2 데이터셋 활용
      EXAONE 3.5 / SOLAR / Qwen2.5-Korean 등 Korean LLM 후보
      Unsloth 사용 시 단일 GPU(A100 40GB)에서 7B 모델 fine-tuning 가능

  [5] TripPamine 서빙 연동
      vLLM → FastAPI → Spring Boot 연동
      이전에 설계한 WebSocket/STOMP 실시간 채널 활용