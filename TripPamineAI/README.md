# TripPamineAI 감성 데이터 파이프라인 개발 문서
## 환경 구축부터 Emotion Classification / Conversation SFT 분기까지

작성 기준: 2026-08-28  
프로젝트 경로: `D:\ESD\Workspace\TripPamine\TripPamineAI`

---

## 1. 문서 목적

이 문서는 TripPamineAI 프로젝트에서 AI-Hub 감성 대화 말뭉치를 기반으로
로컬 개발환경을 구축하고, 데이터 검증·정규화 파이프라인을 만든 뒤
최종적으로 다음 두 학습 데이터 경로로 분기하기까지의 개발 내용을 정리한 문서다.

```text
AI-Hub Raw Dataset
        ↓
Schema Model
        ↓
Rule Validator
        ↓
TripPamine Normalized Dataset
        ↓
┌──────────────────────────────┐
│                              │
↓                              ↓
Emotion Classification      Conversation SFT
HS01 → E10~E69              Multi-turn Dialogue
```

현재까지 실제 원본 데이터 전체 검증과 Normalized JSONL 생성까지 정상 확인했고,
Emotion Classification Dataset Builder 구현 단계까지 진행했다.

---

# 2. 개발 환경

## 2.1 로컬 개발 환경

```text
OS            Windows
IDE           PyCharm
Python        3.13.13
Virtual Env   .venv
Package       pip
Test          pytest
Notebook      Jupyter / IPython
```

Python 프로젝트 지원 범위:

```text
Python >= 3.12, < 3.14
```

로컬 개발은 Python 3.13.13을 기준으로 진행한다.

OCI GPU 학습/Serving 환경은 추후 실제 CUDA, PyTorch, Unsloth, vLLM
호환성을 확인한 뒤 Python 3.12 또는 3.13 중 하나로 고정한다.

---

# 3. 프로젝트 구조

```text
TripPamine/
├── TripPamineBE/
│   └── Spring Boot Backend
│
├── TripPamineFE/
│   └── React Frontend
│
└── TripPamineAI/
    └── Python AI / ML / LLM
```

TripPamineAI 주요 구조:

```text
TripPamineAI/
│
├── .venv/
│
├── src/
│   └── trippamine_ai/
│       └── datasets/
│           └── emotion/
│               ├── __init__.py
│               ├── codebook.py
│               ├── models.py
│               ├── validator.py
│               ├── normalizer.py
│               └── classification.py
│
├── tests/
│   ├── test_environment.py
│   └── datasets/
│       └── emotion/
│           ├── test_codebook.py
│           ├── test_models.py
│           ├── test_validator.py
│           ├── test_normalizer.py
│           └── test_classification.py
│
├── scripts/
│   └── emotion/
│       ├── validate_dataset.py
│       ├── normalize_dataset.py
│       └── build_classification_dataset.py
│
├── configs/
│   └── emotion/
│
├── notebooks/
│
├── data/
│
├── pyproject.toml
└── .gitignore
```

---

# 4. Python 패키지 정책

`pyproject.toml`에서 직접 사용하는 패키지만 명시적으로 관리한다.

핵심 의존성:

```text
pydantic      2.13.4
orjson        3.12.0
tqdm          4.70.0
pytest        9.1.1
pandas        3.0.5
jupyter       1.1.1
ipykernel     7.3.0
```

현재 단계에서는 다음 AI 학습 패키지를 설치하지 않았다.

```text
torch
transformers
datasets
accelerate
peft
trl
unsloth
vllm
```

이들은 OCI GPU 환경의 실제 GPU Shape, CUDA, PyTorch 호환성을 확정한 뒤 추가한다.

---

# 5. AI-Hub 감성 대화 말뭉치 원본

AI-Hub 감성 대화 말뭉치의 주요 구조:

```json
{
  "profile": {
    "persona-id": "Pro_03802",
    "persona": {
      "persona-id": "A02_G01_C01",
      "human": ["A02", "G01"],
      "computer": ["C01"]
    },
    "emotion": {
      "emotion-id": "S06_D02_E31",
      "type": "E31",
      "situation": ["S06", "D02"]
    }
  },
  "talk": {
    "id": {
      "profile-id": "Pro_03802",
      "talk-id": "Pro_03802_00028"
    },
    "content": {
      "HS01": "...",
      "SS01": "...",
      "HS02": "...",
      "SS02": "...",
      "HS03": "...",
      "SS03": "..."
    }
  }
}
```

핵심 코드 체계:

```text
Age       A01 ~ A04
Gender    G01 ~ G02
Computer  C01
Situation S01 ~ S13
Disease   D01 ~ D02
Emotion   E10 ~ E69
```

감정은 6개 대분류와 60개 세부 감정 코드로 구성된다.

```text
분노   E10 ~ E19
슬픔   E20 ~ E29
불안   E30 ~ E39
상처   E40 ~ E49
당황   E50 ~ E59
기쁨   E60 ~ E69
```

---

# 6. OCI Object Storage Raw 데이터

Bucket:

```text
trippamine-ai-data
```

구조:

```text
trippamine-ai-data/
└── raw/
    └── emotion/
        └── aihub-emotional-dialogue/
            └── v1/
                ├── source/
                │   ├── Training.zip
                │   └── Validation.zip
                │
                └── extracted/
                    ├── Training.json
                    └── Validation.json
```

원칙:

```text
raw 데이터는 수정하지 않는다.
```

로컬 개발환경에는 작업용 복사본을 둔다.

```text
data/
└── raw/
    └── emotion/
        └── aihub-emotional-dialogue/
            └── v1/
                └── extracted/
                    ├── Training.json
                    └── Validation.json
```

---

# 7. STEP 1 — Codebook

파일:

```text
src/trippamine_ai/datasets/emotion/codebook.py
```

역할:

```text
A01 ~ A04
G01 ~ G02
C01
S01 ~ S13
D01 ~ D02
E10 ~ E69
```

코드와 사람이 읽을 수 있는 Label을 한 곳에서 관리한다.

세부 감정 → 6대 감정 변환 함수도 제공한다.

테스트:

```text
tests/datasets/emotion/test_codebook.py
```

현재 정상 통과 확인 완료.

---

# 8. STEP 2 — Pydantic Schema Model

파일:

```text
src/trippamine_ai/datasets/emotion/models.py
```

역할:

```text
AI-Hub JSON
      ↓
Pydantic Schema
      ↓
Python Object
```

주요 모델:

```text
PersonaInfo
EmotionInfo
Profile
TalkId
TalkContent
Talk
EmotionDialogueRecord
```

AI-Hub 필드명에 하이픈이 있기 때문에 Pydantic Alias를 사용한다.

`HS03`, `SS03`는 값이 없는 데이터에서도 필드 자체는 존재하므로 문자열 필드로 유지한다.

`extra="forbid"` 정책을 사용해 예상하지 못한 필드가 들어오면 Schema 오류로 판단한다.

테스트:

```text
tests/datasets/emotion/test_models.py
```

현재 정상 통과 확인 완료.

---

# 9. STEP 3 — Validator

파일:

```text
src/trippamine_ai/datasets/emotion/validator.py
```

Validator는 다음 세 상태를 반환한다.

```text
VALID
WARNING
REJECTED
```

## VALID

구조와 코드 정합성이 정상이고 자동 품질 경고가 없는 데이터.

## WARNING

학습 데이터로 바로 삭제할 수준은 아니지만 검토 가치가 있는 데이터.

예:

```text
INCOMPLETE_THIRD_TURN
WORD_COUNT_OUT_OF_RANGE
TOO_MANY_SENTENCES
TOO_MANY_PUNCTUATION_MARKS
CONTAINS_ENGLISH
CONTAINS_DIGIT
```

## REJECTED

구조 또는 Annotation 정합성이 깨진 데이터.

예:

```text
UNKNOWN_EMOTION_CODE
UNKNOWN_AGE_CODE
UNKNOWN_GENDER_CODE
UNKNOWN_SITUATION_CODE
UNKNOWN_DISEASE_CODE
PROFILE_ID_MISMATCH
PERSONA_ID_MISMATCH
EMOTION_ID_MISMATCH
EMPTY_REQUIRED_TURN
```

---

# 10. 실제 전체 Dataset Validator 실행

Script:

```text
scripts/emotion/validate_dataset.py
```

Training 실행 결과:

```text
Total:     51628
Valid:     48114
Warning:    3514
Rejected:      0
```

Validation 실행 결과:

```text
Total:      6640
Valid:      6268
Warning:     372
Rejected:      0
```

실제 전체 데이터는 총 58,268 records이고,
현재 Schema 및 정합성 기준에서 REJECTED는 0건이었다.

생성 Report:

```text
data/reports/emotion/v1/
├── training-validation-report.json
└── validation-validation-report.json
```

---

# 11. 데이터 품질 정책

현재 정책:

```text
VALID
  → 학습 후보

WARNING
  → 삭제하지 않음
  → 메타데이터와 함께 보존
  → 향후 실험에서 포함 여부 비교

REJECTED
  → 학습 데이터에서 제외
```

WARNING은 형태적 경고가 대부분이므로 자동 삭제하지 않는다.

추후 다음 두 실험이 가능하도록 데이터를 보존한다.

```text
Experiment A
VALID only

Experiment B
VALID + WARNING
```

실제 Validation F1 성능을 비교한 뒤 데이터 포함 정책을 결정한다.

---

# 12. STEP 4 — Normalizer

파일:

```text
src/trippamine_ai/datasets/emotion/normalizer.py
```

AI-Hub 전용 구조를 TripPamine 내부 공통 형식으로 변환한다.

```text
AI-Hub Record
      ↓
Normalizer
      ↓
NormalizedEmotionDialogue
```

표준 구조:

```json
{
  "record_id": "...",
  "source": {},
  "persona": {},
  "emotion": {},
  "turns": [],
  "quality": {}
}
```

---

# 13. record_id 정책

AI-Hub의 `talk-id`만을 TripPamine 내부 유일 식별자로 사용하지 않는다.

대신 전체 레코드의 Canonical JSON을 SHA-256으로 해시한다.

```text
Raw Record
   ↓
Canonical JSON
   ↓
SHA-256
   ↓
record_id
```

장점:

```text
동일 데이터 → 동일 ID
내용 수정   → 다른 ID
Dataset 버전 비교 가능
중복 데이터 탐지 가능
```

---

# 14. 대화 Turn 정규화

AI-Hub:

```text
HS01 / SS01
HS02 / SS02
HS03 / SS03
```

TripPamine:

```json
{
  "turns": [
    {
      "turn": 1,
      "human": "...",
      "assistant": "..."
    },
    {
      "turn": 2,
      "human": "...",
      "assistant": "..."
    }
  ]
}
```

세 번째 Turn이 존재하면 추가하고, `HS03`, `SS03`가 둘 다 빈 문자열이면 세 번째 Turn 자체를 만들지 않는다.

---

# 15. Normalized Dataset 생성

Script:

```text
scripts/emotion/normalize_dataset.py
```

생성 구조:

```text
data/
└── processed/
    └── emotion/
        └── aihub-emotional-dialogue/
            └── v1/
                ├── training.jsonl
                └── validation.jsonl
```

JSONL을 사용해 한 줄에 하나의 JSON 객체를 저장한다.

장점:

```text
대용량 Streaming 처리
Hugging Face Dataset 변환 용이
Fine-Tuning 입력 처리 용이
한 레코드 단위 처리 용이
```

실제 JSONL 생성 정상 확인 완료.

---

# 16. UTF-8 한글 표시 문제

PowerShell에서 기본 `Get-Content`로 확인했을 때 한글이 깨져 보이는 문제가 발생했다.

원인:

```text
파일 손상 X
PowerShell 기본 인코딩 표시 문제 O
```

정상 확인 방법:

```powershell
Get-Content training.jsonl -Encoding UTF8 -TotalCount 2
```

또는 Python에서 UTF-8로 직접 읽는다.

정책:

```text
Normalizer에서 임의 encode/decode 변환을 추가하지 않는다.
UTF-8 원본을 그대로 유지한다.
```

---

# 17. 현재 데이터 파이프라인 상태

현재까지 완료된 흐름:

```text
AI-Hub Training.json
AI-Hub Validation.json
        ↓
Pydantic Schema
        ↓
Rule Validator
        ↓
Validation Report
        ↓
Normalizer
        ↓
TripPamine Standard JSONL
```

실제 Dataset:

```text
Training      51,628
Validation     6,640
────────────────────
Total         58,268
```

---

# 18. 핵심 분기점

Normalized Dataset 이후부터 학습 목적에 따라 데이터 파이프라인이 분리된다.

```text
NormalizedEmotionDialogue
             │
      ┌──────┴──────┐
      │             │
      ↓             ↓
Emotion           Conversation
Classification    SFT

HS01              Human/Assistant
 ↓                Multi-turn
E10~E69           Dialogue
```

---

# 19. 분기 A — Emotion Classification

파일:

```text
src/trippamine_ai/datasets/emotion/classification.py
```

Script:

```text
scripts/emotion/build_classification_dataset.py
```

분류 모델의 입력은 첫 사용자 발화 `HS01`만 사용한다.

```text
HS01
 ↓
Emotion Classification Model
 ↓
E10 ~ E69
```

후속 발화 `HS02`, `HS03`에는 최초 Label과 동일한 감정이 유지된다고 가정하지 않는다.

---

# 20. Classification Dataset 포맷

예:

```json
{
  "id": "bb0d8835...",
  "text": "일은 왜 해도 해도 끝이 없을까? 화가 난다.",
  "label": "E18",
  "label_name": "노여워하는",
  "coarse_label": "분노",
  "situation_code": "S06",
  "situation": "진로,취업,직장",
  "quality_status": "VALID",
  "quality_issue_codes": [],
  "source": {
    "dataset": "aihub-emotional-dialogue",
    "version": "v1",
    "split": "training",
    "profile_id": "Pro_05349",
    "talk_id": "Pro_05349_00053"
  }
}
```

모델 실제 입력:

```text
text
```

모델 Target:

```text
label
```

---

# 21. Classification Dataset에서 Persona를 입력하지 않는 이유

다음 정보는 분류 모델 입력에 직접 넣지 않는다.

```text
age
gender
disease
```

감정을 텍스트에서 판단하게 하고, Persona 정보에 과도하게 의존하는 것을 방지하기 위함이다.

원본 추적용 메타데이터는 Normalized Dataset에 유지한다.

`situation` 역시 현재는 모델 Input이 아니라 분석용 Metadata로 보존한다.

---

# 22. Classification Quality 정보

WARNING 데이터도 삭제하지 않기 위해 다음 정보를 Classification Sample에 유지한다.

```json
{
  "quality_status": "WARNING",
  "quality_issue_codes": [
    "WORD_COUNT_OUT_OF_RANGE"
  ]
}
```

이를 통해 다음 실험을 분리할 수 있다.

```text
Dataset A
VALID only

Dataset B
VALID + WARNING
```

---

# 23. Classification Dataset 출력 예정 구조

```text
data/
└── training/
    └── emotion/
        └── classification/
            └── v1/
                ├── training.jsonl
                └── validation.jsonl
```

Report:

```text
data/reports/emotion/v1/
├── training-classification-report.json
└── validation-classification-report.json
```

Classification Builder가 집계할 항목:

```text
전체 Record 수
생성 Record 수
Fine Emotion 분포
Coarse Emotion 분포
Quality 분포
Warning Issue 분포
```

현재 상태는 구현 완료, 실제 전체 Dataset 실행 검증 전이다.

---

# 24. 분기 B — Conversation SFT

Emotion Classification과 별도로 LLM 감성 대화 학습용 Dataset을 생성할 예정이다.

예상 변환 방향:

```text
HS01
→ SS01

HS01 + SS01 + HS02
→ SS02

HS01 + SS01 + HS02 + SS02 + HS03
→ SS03
```

일반적인 Chat 형식:

```json
{
  "messages": [
    {
      "role": "user",
      "content": "..."
    },
    {
      "role": "assistant",
      "content": "..."
    }
  ]
}
```

이 경로는 Emotion Classification Dataset과 독립적으로 관리한다.

---

# 25. 앞으로의 데이터 계층

```text
data/

raw/
└── AI-Hub 원본

processed/
└── TripPamine Normalized Dataset

training/
├── emotion/
│   ├── classification/
│   └── conversation-sft/
│
└── 향후 다른 AI Dataset

reports/
└── Validation / Normalization / Training Dataset Report
```

---

# 26. 현재까지 검증 완료 항목

```text
[완료] Python 3.13.13 .venv
[완료] PyCharm 개발환경
[완료] pyproject.toml
[완료] pytest 환경

[완료] AI-Hub Codebook
[완료] Pydantic Schema Model
[완료] Validator
[완료] Validator Unit Test

[완료] Training 51,628 전체 검증
[완료] Validation 6,640 전체 검증

[완료] Normalizer
[완료] Normalizer Unit Test
[완료] Normalized JSONL 생성
[완료] UTF-8 확인

[구현] Emotion Classification Model/Builder
[구현] Classification Unit Test
[구현] Classification Dataset Script

[다음 검증] Classification Training JSONL 생성
[다음 검증] Classification Validation JSONL 생성
```

---

# 27. 다음 개발 순서

Classification Dataset 생성이 실제로 정상 확인되면 바로 모델 학습으로 가지 않는다.

먼저 Dataset Quality Report를 만든다.

```text
STEP 8

Classification Dataset
        ↓
Dataset Statistics
        ↓
60 Emotion Distribution
        ↓
6 Coarse Emotion Distribution
        ↓
Class Imbalance Analysis
        ↓
Train / Validation 비교
```

그다음:

```text
STEP 9
Conversation SFT Dataset Builder
```

이후:

```text
STEP 10
Baseline Emotion Classification Model
```

그리고 OCI GPU 환경을 확정한 뒤:

```text
STEP 11
LLM Fine-Tuning
QLoRA / Unsloth
        ↓
vLLM Serving
        ↓
FastAPI
        ↓
Spring Boot
        ↓
TripPamine
```

---

# 28. 전체 아키텍처 요약

```text
                 AI-Hub
                   │
                   ▼
          Raw Emotional Dialogue
                   │
                   ▼
             Pydantic Model
                   │
                   ▼
                Validator
                   │
        ┌──────────┼──────────┐
        │          │          │
        ▼          ▼          ▼
      VALID     WARNING    REJECTED
        │          │
        └────┬─────┘
             │
             ▼
          Normalizer
             │
             ▼
  TripPamine Standard JSONL
             │
      ┌──────┴──────────┐
      │                 │
      ▼                 ▼
Emotion             Conversation
Classification      SFT
      │                 │
      ▼                 ▼
Emotion Model         LLM
      │                 │
      └────────┬────────┘
               │
               ▼
       TripPamine AI Layer
               │
               ▼
            FastAPI
               │
               ▼
         Spring Boot API
               │
               ▼
             React
```

---

# 29. 현재 기준 결론

현재까지 TripPamineAI는 AI-Hub JSON을 곧바로 모델에 넣는 구조가 아니라 다음 중간 계층을 갖도록 설계되었다.

```text
Raw Data
   ↓
Schema
   ↓
Validation
   ↓
Normalization
   ↓
Training Dataset Builder
```

이 구조를 유지하면 향후 다른 감성 데이터셋, 금융 데이터셋, 여행 데이터셋이 추가되어도
각 원본 Dataset을 TripPamine Standard Record로 변환한 뒤
Classification, SFT, RAG, Evaluation 등 목적별 Pipeline으로 재사용할 수 있다.

현재 지점은 **공통 데이터 계층 구축이 완료되고, 실제 모델 목적별 Dataset 생성으로 분기되는 시점**이다.