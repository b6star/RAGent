# RAGent Android 프로젝트 구현 가이드

## 1. 프로젝트 목표

RAGent는 여러 소프트웨어 프로젝트를 하나의 Android 앱에서 관리하고, 각 프로젝트의 GitHub Repository와 Notion 문서를 하나의 프로젝트 지식으로 통합하는 협업 앱이다.

사용자는 프로젝트 문서를 직접 열람하거나, 프로젝트의 최신 정보를 기반으로 AI Agent에게 질문할 수 있다.

핵심 방향:

- 하나의 앱에서 여러 프로젝트 관리
- 프로젝트별 GitHub Repository / Notion 연결
- Notion 문서를 앱에서 원본 구조에 가깝게 열람
- GitHub 코드와 Notion 문서를 RAG 지식으로 활용
- 프로젝트별 사용자 및 권한 관리
- 프로젝트의 공용 데이터와 RAG 데이터는 Firebase에서 공유
- GitHub 변경사항은 Webhook을 이용해 Firebase와 자동 동기화
- Local LLM을 우선 사용
- GPT, Claude, Gemini 등 Cloud LLM API는 나중에 추가
- 초기 개발에서는 전체 기능을 한꺼번에 구현하지 않고 앱/Firebase 기본 구조부터 구축


## 2. 앱 전체 구조

앱 실행 시 특정 프로젝트로 바로 진입하지 않는다.

먼저 사용자가 참여 중인 모든 프로젝트를 보여주는 Project List 화면을 표시한다.

예시:

RAGent

내 프로젝트

[관리자] RAGent
[팀원] FocusWave
[열람자] Soongsil Life

+ 프로젝트 생성

프로젝트 역할:

- Admin: 프로젝트 생성 및 관리
- Member: 일반 프로젝트 참여자
- Viewer: 주로 문서 확인 및 제한적인 기능을 사용하는 사용자

세부 권한 정책은 초기 버전에서 완성하지 않아도 된다.


## 3. Navigation 구조

Project List는 앱 전체 수준의 화면이다.

Project List에서는 Bottom Navigation을 표시하지 않는다.

사용자가 특정 프로젝트를 선택하면 해당 프로젝트 내부 화면으로 이동하고, 이때부터 Bottom Navigation을 표시한다.

구조:

App
→ Project List
→ Project 선택
→ Project 내부

프로젝트 내부 Bottom Navigation:

- Docs
- Repository
- Members
- Agent

프로젝트 내부에서 뒤로 가면 Project List로 돌아간다.


## 4. Firebase의 역할

Firebase는 여러 사용자가 하나의 프로젝트 정보를 공유하기 위한 공용 Backend 역할을 한다.

초기에는 다음 기능을 중심으로 사용한다.

### Firebase Authentication

- 사용자 로그인
- 사용자 식별

### Cloud Firestore

- 프로젝트 정보
- GitHub / Notion 연결 정보
- 프로젝트 멤버
- 사용자 권한
- RAG Chunk
- Embedding / Vector
- Source Metadata
- 동기화 상태

개념적인 데이터 구조:

projects
└─ projectId
   ├─ name
   ├─ githubUrl
   ├─ notionUrl
   ├─ lastSync
   ├─ members
   └─ ragData

중요:

RAG Vector를 특정 사용자의 스마트폰에만 저장하는 구조를 기본으로 하지 않는다.

프로젝트의 공용 RAG 데이터는 Firebase를 통해 프로젝트 참여자들이 공유하는 방향으로 설계한다.

관리자가 한 번 생성한 프로젝트 지식을 Member와 Viewer가 각자 다시 Embedding하지 않도록 한다.


## 5. Android 앱의 역할

Android 앱은 Firebase의 프로젝트 정보를 읽고 쓰는 Client 역할을 한다.

주요 역할:

- 로그인
- Project List 표시
- 프로젝트 생성
- 프로젝트 참여
- 프로젝트 정보 확인
- Docs 표시
- Repository 표시
- Agent UI
- Firebase RAG 데이터 사용
- Local LLM 실행

Local LLM은 각 Android 기기에서 실행한다.

즉 공용 프로젝트 지식은 Firebase에서 공유하고, 최종 AI 답변 생성은 각 사용자 기기의 Local LLM에서 수행하는 구조를 기본 방향으로 한다.


## 6. 프로젝트 생성

Project List 화면에 + 버튼을 제공한다.

프로젝트 생성 시 향후 다음 정보를 연결할 수 있도록 설계한다.

- Project Name
- GitHub Repository URL
- Notion URL
- Members

초기 버전에서는 프로젝트 생성 / 저장 / 목록 표시 / 프로젝트 진입 기능부터 구현한다.


## 7. 프로젝트 내부 화면

### Overview

프로젝트 기본 정보와 연결 상태 등을 보여준다.

### Docs

프로젝트에 연결된 Notion 문서를 보여주는 영역이다.

AI가 Notion 내용을 다시 요약해서 표시하는 것이 아니라, 사용자가 작성한 Notion 페이지의 구조를 앱에서 최대한 자연스럽게 열람할 수 있도록 하는 것이 목표다.

향후 지원할 수 있는 Block:

- Heading
- Paragraph
- Bullet List
- Numbered List
- Code Block
- Image
- Divider

초기에는 Mock Data를 사용한다.

### Repository

프로젝트에 연결된 GitHub Repository를 GitHub 앱처럼 디렉터리 / 파일 구조로 탐색할 수 있는 화면을 목표로 한다.

향후:

- 디렉터리 탐색
- 코드 파일 열람
- README
- 파일 구조
- Commit 정보

등을 지원할 수 있도록 설계한다.

초기에는 Mock Data로 화면 구조만 구현해도 된다.

### Agent

현재 프로젝트에 대해 질문하는 AI 채팅 화면이다.

초기에는 채팅 UI 구조만 만든다.

### Members

프로젝트 참여자 중 Admin과 Member를 보여주는 화면이다.

초기에는 Mock Data로 멤버 리스트 UI만 만든다.

표시 방향:

- 관리자 우선 표시
- 그 다음 팀원 표시
- 열람자는 멤버 리스트에서 제외
- 각 항목은 활동명, 담당 요약, DM 아이콘을 표시

### Chat

프로젝트 협업 메시지를 확인하는 화면이다.

채팅의 최종 목표:

- 프로젝트별 팀원 간 메시지
- 열람자가 멤버에게 보내는 문의 메시지
- 관리자가 팀원 전체에게 보내는 공지 메시지
- Firebase를 통한 메시지 저장 및 동기화

진입 방식:

- Project List 헤더의 Chat: 지금까지 받은 전체 메시지 표시
- Project 내부 헤더의 Chat: 해당 프로젝트 메시지만 표시
- Members 탭의 DM 아이콘: 해당 멤버와 나눈 대화만 표시
- 멤버 DM이 특정 프로젝트 내부에서 시작된 경우 해당 프로젝트 관련 대화를 하이라이트

Phase 1에서는 실제 Firebase 채팅 전송을 구현하지 않고, Chat 화면과 필터링 흐름 Mock만 만든다.


## 8. GitHub 동기화

GitHub Repository의 최신 상태를 Firebase와 동기화하는 것이 중요하다.

최종적으로는 GitHub Webhook 기반 구조를 사용한다.

흐름:

GitHub Repository
→ git push
→ GitHub Webhook
→ Firebase Cloud Function
→ 변경사항 확인
→ 필요한 데이터 갱신
→ Firestore 최신화

Android 앱이 Repository 전체를 계속 확인하는 방식보다 GitHub가 변경 발생 시 서버에 알려주는 Event 기반 구조를 목표로 한다.


## 9. GitHub Webhook

Webhook은 Firestore에 직접 연결하지 않는다.

Firebase Cloud Function에 HTTP Endpoint를 만들고 GitHub Webhook이 해당 Endpoint로 요청을 보내도록 한다.

구조:

GitHub
→ Webhook HTTP POST
→ Firebase Cloud Function
→ Firestore

Webhook 설정 시 필요한 값:

- Firebase Cloud Function Payload URL
- Webhook Secret

Repository 관리자가 GitHub Repository의 Settings → Webhooks에서 등록한다.

초기에는 push event만 처리하면 된다.

Webhook Secret을 사용해 GitHub에서 전달된 요청이 정상적인 GitHub 요청인지 검증할 수 있도록 한다.

초기 구현에서는 실제 RAG 갱신까지 한 번에 만들지 않는다.

먼저:

GitHub Push
→ Firebase Function 호출
→ Push / Commit 정보 확인
→ Firestore에 최신 Commit SHA 저장

여기까지 성공시키고 이후 기능을 단계적으로 추가한다.


## 10. GitHub 변경사항 처리

향후 GitHub Webhook이 Push를 감지하면 변경된 파일을 확인한다.

Repository 전체를 매번 다시 처리하지 않는다.

예:

ChatRoutes.kt 변경
README.md 변경

→ 해당 파일의 최신 내용 가져오기
→ 기존 관련 Chunk 제거 또는 무효화
→ 변경 파일만 다시 Chunking
→ Embedding 재생성
→ Firebase RAG 데이터 갱신

변경되지 않은 파일의 Embedding은 다시 만들지 않는다.


## 11. Notion 동기화

Notion도 GitHub와 마찬가지로 최신 프로젝트 문서를 Firebase와 동기화하는 것을 목표로 한다.

초기 구현에서는 Notion 실제 API 연동을 하지 않는다.

먼저 Docs UI를 Mock Data로 구축하고 이후 Notion API 연동 및 변경된 페이지 갱신 기능을 추가한다.


## 12. RAG의 역할

RAG는 직접 답변을 생성하는 AI가 아니다.

사용자의 질문과 관련된 프로젝트 정보를 GitHub와 Notion 데이터에서 찾아 LLM의 Context로 제공한다.

전체 흐름:

GitHub + Notion
→ 데이터 수집
→ Chunking
→ Embedding
→ Vector 저장

사용자 질문
→ 질문 Embedding
→ Vector Similarity Search
→ 관련 Chunk Top-K 검색
→ LLM Prompt Context
→ LLM
→ 답변

프로젝트 전체 내용을 매번 LLM에 전달하지 않는다.

질문과 관련성이 높은 일부 Chunk만 LLM에 전달한다.


## 13. RAG Metadata

각 Chunk에는 원본 내용뿐 아니라 출처 Metadata를 함께 관리한다.

예:

- projectId
- sourceType (GITHUB / NOTION)
- sourceName
- filePath 또는 pageName
- content
- embedding
- sourceVersion

이를 이용해 향후 Agent 답변에 출처를 표시할 수 있도록 한다.


## 14. GitHub / Notion Chunking

Notion:

- 제목
- 문단
- Block

등의 구조를 기준으로 Chunking한다.

GitHub Source Code:

단순 글자 수 기준으로 자르기보다 향후 가능한 경우

- Class
- Function
- Method

등 코드 구조를 기준으로 Chunking할 수 있도록 설계한다.


## 15. Local LLM 우선

첫 번째 LLM Provider는 Local LLM으로 구현한다.

목표:

RAG
→ Prompt 생성
→ LocalLlmProvider
→ llama.cpp
→ GGUF Local Model
→ 답변

Android에서 llama.cpp 기반 GGUF 모델을 실행하는 구조를 고려한다.

모델은 APK에 반드시 포함하지 않아도 되며, 향후 Local AI 사용 시 별도로 다운로드하는 방식으로 확장할 수 있다.


## 16. LLM Provider 구조

RAG가 특정 LLM에 종속되지 않도록 한다.

개념:

LlmProvider
├─ LocalLlmProvider
├─ OpenAiLlmProvider
├─ ClaudeLlmProvider
└─ GeminiLlmProvider

초기에는 LocalLlmProvider를 우선한다.

OpenAI, Claude, Gemini 등의 Cloud API는 현재 구현하지 않는다.

나중에 Provider만 추가할 수 있도록 RAG와 LLM 호출 로직을 분리한다.


## 17. 전체 최종 데이터 흐름

GitHub / Notion
       ↓
최신 프로젝트 원본
       ↓
Webhook / Sync
       ↓
Firebase
       │
       ├─ Project
       ├─ Members / Roles
       ├─ Source Data
       ├─ Chunk
       ├─ Vector
       └─ Sync State
       ↓
Android RAGent
       ↓
RAG Retrieval
       ↓
관련 Context
       ↓
Local LLM
       ↓
답변 + 출처

향후 필요하면 Local LLM 대신 Cloud LLM Provider를 선택할 수 있도록 확장한다.


## 18. 개발 단계

모든 기능을 한 번에 구현하지 않는다.

### Phase 1 — 현재 가장 먼저 구현

Android + Firebase 기본 뼈대를 만든다.

구현:

- Jetpack Compose 기본 구조
- Firebase 연결을 고려한 아키텍처
- Project List
- + 프로젝트 생성
- Admin / Member / Viewer 표시
- 프로젝트 선택
- 프로젝트 내부에서만 Bottom Navigation 표시
- Docs
- Repository
- Members
- Agent
- Chat 화면 Mock
- 각 내부 화면은 우선 Mock / Placeholder 사용

목표 흐름:

Project List
→ Project 생성
→ Project 선택
→ Docs / Repository / Members / Agent 이동
→ Project List 또는 Project 내부 또는 Members DM에서 Chat 화면 진입

이 단계에서는 RAG / Local LLM / GitHub API / Notion API / Webhook을 억지로 구현하지 않는다.
Firebase 채팅 저장, 공지 발송, DM 전송도 이 단계에서는 구현하지 않는다.


### Phase 2 — Firebase 실제 데이터

- Firebase Authentication
- Firestore
- 프로젝트 생성 / 조회
- 사용자 / Member
- Role
- 여러 사용자의 프로젝트 공유


### Phase 3 — GitHub

- GitHub Repository 연결
- Repository 정보 가져오기
- Repository / 파일 탐색
- Firebase Cloud Function
- GitHub Webhook
- Push Event 수신
- 최신 Commit SHA 저장
- 변경된 파일 동기화


### Phase 4 — Notion

- Notion API 연결
- Notion Page / Block 가져오기
- Docs UI에 렌더링
- Firebase와 동기화


### Phase 5 — RAG

- Chunking
- Local Embedding
- Vector 생성
- Firebase에 공용 RAG 데이터 저장
- Similarity Search
- Top-K Retrieval
- Source Metadata
- 변경된 GitHub / Notion 데이터만 RAG 재생성


### Phase 6 — Local LLM

- llama.cpp
- GGUF Model
- LocalLlmProvider
- RAG Context와 사용자 질문을 Prompt로 구성
- Agent 답변 생성


### Phase 7 — Cloud LLM

필요할 경우 이후 추가:

- OpenAI
- Claude
- Gemini
- 기타 Provider


## 19. 구현 원칙

- 처음부터 과도하게 복잡하게 만들지 않는다.
- 먼저 앱이 정상적으로 빌드되고 기본 Navigation이 동작해야 한다.
- UI는 Jetpack Compose를 사용한다.
- 프로젝트별 데이터가 명확하게 분리되어야 한다.
- Firebase는 공용 프로젝트 데이터 공유 Backend로 사용한다.
- GitHub / Notion을 프로젝트 정보의 원본 Source로 취급한다.
- Firebase의 RAG 데이터는 가능한 최신 원본과 동기화한다.
- 변경되지 않은 데이터를 불필요하게 다시 Embedding하지 않는다.
- RAG와 LLM의 책임을 분리한다.
- Local LLM을 우선한다.
- Cloud LLM API는 나중에 추가한다.
- Mock Data로 전체 앱 흐름을 먼저 완성한다.


## 20. Codex에게 요청

이 문서를 RAGent 프로젝트의 전체 방향과 요구사항으로 사용한다.

Android Studio에서 생성한 빈 프로젝트를 기준으로 작업한다.

현재는 Phase 1만 우선 구현한다.

즉:

Project List
→ 프로젝트 생성
→ 프로젝트 선택
→ 프로젝트 내부 Bottom Navigation
→ Overview / Docs / Repository / Agent

흐름이 정상적으로 동작하는 Android 앱의 기본 뼈대를 만든다.

향후 Firebase, GitHub Webhook, Notion, RAG, Local LLM을 단계적으로 추가할 예정이므로 각 기능이 지나치게 강하게 결합되지 않도록 구조를 설계한다.

현재 단계에서 GitHub API, Notion API, Webhook, Embedding, Vector Search, llama.cpp, Cloud LLM API를 억지로 구현하지 않는다.

먼저 앱이 정상적으로 빌드되고 실행되는 것을 최우선으로 한다.


## 21. Local LLM 구현 방향

현재는 Phase 1 개발 중이므로 실제 Local LLM 기능을 구현하지 않는다.

다음 내용은 향후 Local LLM Phase에서의 구현 방향으로 사용한다.

### 기본 모델

기본 Local LLM은 Qwen3-4B의 GGUF Q4 양자화 모델을 우선 고려한다.

Android 기기에서 실행 가능한 크기와 품질의 균형을 우선하며, 이후 성능 테스트 결과에 따라 다른 GGUF 모델로 교체할 수 있도록 설계한다.

### 실행 방식

Android 앱에서는 llama.cpp 기반으로 GGUF 모델을 실행하는 방향을 기본으로 한다.

LLM 호출 구조는 특정 모델에 강하게 결합하지 않고, `LocalLlmProvider` 같은 Provider 계층을 통해 분리한다.

### 모델 배포

Qwen3-4B GGUF 모델 파일은 APK에 포함하지 않는다.

사용자가 Local AI 기능을 사용할 때 필요한 모델을 별도로 다운로드하는 구조로 설계한다.

초기 앱 설치 크기를 줄이고, 사용자가 필요할 때만 모델 저장 공간을 사용하도록 한다.

### 답변 표시

Agent 답변은 생성이 모두 끝날 때까지 기다렸다가 한 번에 표시하지 않는다.

기본 방향은 token streaming 방식으로, 생성되는 토큰을 실시간으로 UI에 표시한다.

이를 통해 Local LLM 응답이 느린 기기에서도 사용자가 진행 상태를 바로 확인할 수 있게 한다.

### RAG Context 구성

RAG에서는 Repository 전체나 Notion 전체 문서를 매번 LLM에 전달하지 않는다.

사용자 질문과 관련된 GitHub 코드 Chunk / Notion Chunk만 검색해서 Prompt Context로 전달한다.

초기 기준으로 Top-K 3~5개 Chunk 정도를 고려한다.

Top-K 값은 모델 컨텍스트 길이, 응답 품질, Android 기기 성능을 보면서 조정한다.

### Phase 범위

Phase 1에서는 다음을 구현하지 않는다.

- Qwen3-4B 실제 실행
- llama.cpp 연동
- GGUF 모델 다운로드
- token streaming 구현
- 실제 Embedding / Vector Search

Phase 1에서는 앱 기본 흐름과 Mock UI를 우선 완성하고, Local LLM은 이후 Phase에서 단계적으로 추가한다.
