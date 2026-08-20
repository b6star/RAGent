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
- GitHub / Notion은 공개 링크 입력을 기본 연결 방식으로 사용
- Cloud Functions가 변경 탐지, Chunking, Vector 갱신을 담당
- Gemini Embedding API와 Firestore Vector Search로 RAG Context 구성
- Gemini 생성 모델로 최종 답변 생성
- GitHub API, Notion API, Webhook은 필요한 경우 선택 기능으로 추가
- 초기 개발에서는 전체 기능을 한꺼번에 구현하지 않고 앱/Firebase 기본 구조부터 구축


## 2. 프로젝트 멤버벌 역할

프로젝트 멤버별 역할:
- Admin: 프로젝트 생성 및 관리
- Member: 일반 프로젝트 참여자
- Viewer: 주로 문서 확인 및 제한적인 기능을 사용하는 사용자


## 3. Firebase의 역할

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

### Cloud Functions

- 공개 GitHub / Notion 링크의 변경 확인
- 변경된 Source 데이터 처리
- Chunking 및 Embedding 요청
- Gemini 생성 모델 호출
- 사용자별 요청 제한

개념적인 데이터 구조:

projects
└─ projectId
   ├─ name
   ├─ githubUrl
   ├─ notionUrl
   ├─ visibility (Public or Private)
   ├─ latestPullRequest
   ├─ lastSync
   ├─ members
   ├─ status
   └─ ragData

중요:

RAG Vector를 특정 사용자의 스마트폰에만 저장하는 구조를 기본으로 하지 않는다.

프로젝트의 공용 RAG 데이터는 Firebase를 통해 프로젝트 참여자들이 공유하는 방향으로 설계한다.

관리자가 한 번 생성한 프로젝트 지식을 Member와 Viewer가 각자 다시 Embedding하지 않도록 한다.


## 4. Android 앱의 역할

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
- 동기화 요청 전송
- Agent 질문 요청 전송

Android 앱은 최종 답변을 직접 생성하지 않는다. 프로젝트 진입 시 동기화를 요청하고 Agent 질문을 Cloud Functions에 전달한다.
공용 프로젝트 지식과 RAG 데이터는 Firebase에서 프로젝트 참여자들이 공유한다.


## 5. 프로젝트 생성

Project List 화면에 + 버튼을 제공한다.

프로젝트 생성 시 향후 다음 정보를 연결할 수 있도록 설계한다.

- Project Name
- GitHub Repository URL
- Notion URL
- Members

초기 버전에서는 프로젝트 생성 / 저장 / 목록 표시 / 프로젝트 진입 기능부터 구현한다.


## 6. 프로젝트 내부 화면

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

사용자의 질문과 Agent의 답변 내역은 서버가 아닌 로컬 저장소에 기록되며 사용자는 이전 기록을 열람, 삭제할 수 있다.

초기에는 채팅 UI 구조만 만든다.

### Members

프로젝트 참여자 중 Admin과 Member를 보여주는 화면이다.

초기에는 Mock Data로 멤버 리스트 UI만 만든다.

표시 방향:

- 관리자 우선 표시
- 그 다음 팀원 표시
- 열람자는 멤버 리스트에서 제외
- 각 항목은 활동명, 담당 요약, DM 아이콘을 표시
- 추후 관리자가 팀원을 추가, 제외, 권한 부여 등을 할 수 있는 기능 제공.

### Chat

프로젝트 협업 메시지를 확인하는 화면이다.

채팅의 최종 목표:

- 프로젝트별 팀원 간 메시지
- 열람자가 멤버에게 보내는 문의 메시지
- 팀원 전체에게 보내는 공지 메시지
- 공지 메시지는 팀원 누구나 보낼 수 있도록 하며 본인이 보낸 공지 메시지는 수정, 삭제할 수 있다.
- 관리자는 다른 사람의 공지메시지에 대한 모든 편집권한을 가진다.
- Firebase를 통한 메시지 저장 및 동기화

진입 방식:

- Project List 헤더의 Chat: 지금까지 받은 전체 메시지 표시
- Project 내부 헤더의 Chat: 해당 프로젝트 메시지만 표시
- Members 탭의 DM 아이콘: 해당 멤버와 나눈 대화만 표시
- 멤버 DM이 특정 프로젝트 내부에서 시작된 경우 해당 프로젝트 관련 대화를 하이라이트

Phase 1에서는 실제 Firebase 채팅 전송을 구현하지 않고, Chat 화면과 필터링 흐름 Mock만 만든다.


## 7. GitHub 동기화

GitHub Repository는 공개 URL 입력을 기본 연결 방식으로 사용한다.
GitHub API와 Webhook은 더 빠르고 정확한 동기화가 필요한 경우 선택적으로 제공한다.

흐름:

프로젝트 화면 진입
→ Android 앱이 Cloud Functions에 동기화 요청
→ 서버가 마지막 확인 시각과 GitHub 최신 상태 비교
→ 변경된 파일만 처리
→ Firestore 최신화

여러 사용자가 같은 프로젝트에 진입해도 동기화가 반복되지 않도록 마지막 확인 시각과 콘텐츠 Hash를 저장한다.
Repository 전체가 아니라 변경된 파일과 영향을 받은 Chunk만 다시 계산한다.


## 8. GitHub API / Webhook (선택 기능)

공개 링크 기반 동기화를 먼저 구현한 뒤, 필요할 경우 GitHub API 또는 Webhook을 추가한다.

Webhook을 사용할 경우 Firestore에 직접 연결하지 않고 Firebase Cloud Function의 HTTP Endpoint를 사용한다.

구조:

GitHub
→ Webhook HTTP POST
→ Firebase Cloud Function
→ Firestore

Webhook 설정 시 필요한 값:

- Firebase Cloud Function Payload URL
- Webhook Secret

Repository 관리자가 GitHub Repository의 Settings → Webhooks에서 등록한다.

Webhook Secret으로 요청을 검증하고 Push Event를 활용한다.
API와 Webhook은 공개 링크 기반 동기화의 정확도와 속도를 개선할 필요가 생겼을 때 추가한다.


## 9. GitHub 변경사항 처리

Cloud Functions가 GitHub Source의 변경을 확인하면 변경된 파일을 처리한다.

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


## 10. Notion 동기화

Notion은 공개 페이지 URL 입력을 기본 연결 방식으로 사용한다.
프로젝트 화면 진입 시 Android 앱이 Cloud Functions에 동기화를 요청하고, 서버는 마지막 확인 시각과 콘텐츠 Hash를 기준으로 변경된 부분만 처리한다.

Docs 화면에서는 우선 Notion 공개 페이지를 WebView로 표시한다.
Notion API 연결은 앱 내부 편집이나 안정적인 동기화가 필요한 경우 선택적으로 제공한다.


## 11. RAG의 역할

RAG는 직접 답변을 생성하는 AI가 아니다.

사용자의 질문과 관련된 프로젝트 정보를 GitHub와 Notion 데이터에서 찾아 LLM의 Context로 제공한다.

전체 흐름:

GitHub + Notion
→ Cloud Functions 변경 탐지
→ Chunking
→ Gemini Embedding API
→ Firestore Vector 저장

사용자 질문
→ 질문 Embedding
→ Firestore Vector Search
→ 관련 Chunk Top-K 검색
→ LLM Prompt Context
→ LLM
→ 답변

프로젝트 전체 내용을 매번 LLM에 전달하지 않는다.

질문과 관련성이 높은 일부 Chunk만 LLM에 전달한다.


## 12. RAG Metadata

각 Chunk에는 원본 내용뿐 아니라 출처 Metadata를 함께 관리한다.

예:

- projectId
- sourceType (GITHUB / NOTION)
- sourceName
- filePath 또는 pageName
- content
- embedding
- sourceVersion
- contentHash

이를 이용해 향후 Agent 답변에 출처를 표시할 수 있도록 한다.


## 13. GitHub / Notion Chunking

Notion:

- 제목
- 문단
- Block

등의 구조를 기준으로 Cloud Functions에서 Chunking한다.

GitHub Source Code:

단순 글자 수 기준으로 자르기보다 가능한 경우

- Class
- Function
- Method

등 코드 구조를 기준으로 Chunking할 수 있도록 설계한다.


## 14. Gemini 기반 답변

초기 LLM은 Firebase AI Logic과 Gemini 생성 모델을 사용한다.

목표:

RAG
→ 관련 Context 구성
→ Cloud Functions
→ Gemini 생성 모델
→ 답변 + 출처

Gemini 2.5 Flash 또는 Flash-Lite를 답변 품질, 속도와 비용을 비교해 선택한다.


## 15. LLM Provider 구조

RAG가 특정 LLM에 종속되지 않도록 한다.

개념:

LlmProvider
├─ GeminiLlmProvider
├─ LocalLlmProvider (후순위)
├─ OpenAiLlmProvider (선택)
└─ ClaudeLlmProvider (선택)

초기에는 Firebase AI Logic과 Gemini를 우선 구현한다.
Local LLM과 다른 Cloud LLM Provider는 이후 필요성과 운영 여건에 따라 추가한다.


## 16. 전체 최종 데이터 흐름

GitHub / Notion 공개 링크
       ↓
Cloud Functions 동기화 요청
       ↓
변경 탐지 / Chunking / Embedding
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
질문 요청
       ↓
Cloud Functions
       ↓
Firestore Vector Search
       ↓
관련 Context
       ↓
Gemini 생성 모델
       ↓
답변 + 출처


## 17. 개발 단계

단계별로 구현한다.

### Phase 1 — 기본 UI 구현 완료

Jetpack Compose 기반 앱 구조와 Mock Data를 사용한 주요 화면을 구현했다.

구현 완료:

- Project 생성 및 선택
- Docs / Repository / Members / Agent 탭
- Admin / Member / Viewer 역할 표시
- Chat List 및 DM Mock 화면
- 기본 네비게이션과 상태 유지


### Phase 2 — Firebase 실제 데이터 (You Are Here!)

- Firebase Authentication
- Firestore
- 프로젝트 생성 / 조회
- 사용자 / Member
- Role
- 여러 사용자의 프로젝트 공유


### Phase 3 — Firebase 서버 기반

- Firebase Blaze 요금제 전환
- Firebase Cloud Function
- TypeScript 서버 구성
- 동기화 요청 Endpoint
- 사용자별 요청 제한


### Phase 4 — 공개 Source 연결

- 공개 GitHub Repository URL 입력
- 공개 Notion Page URL 입력
- Docs WebView 표시
- Repository 탐색 화면 연결
- 프로젝트 진입 시 서버 동기화 요청
- 마지막 확인 시각과 콘텐츠 Hash 저장


### Phase 5 — RAG

- 변경된 GitHub / Notion Source 감지
- 변경된 데이터만 Chunking
- Gemini Embedding API
- Firestore Vector Search
- Firebase에 공용 RAG 데이터 저장
- Top-K Retrieval
- Source Metadata
- 프로젝트 참여자 간 RAG 데이터 공유


### Phase 6 — Gemini Agent

- Firebase AI Logic
- Gemini 생성 모델
- RAG Context 기반 Prompt 구성
- Agent 답변 생성
- 답변 출처 표시


### Phase 7 — 선택 기능 확장

필요할 경우 이후 추가:

- GitHub API
- GitHub Webhook
- Notion API
- OpenAI
- Claude
- Local LLM / llama.cpp
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
- Gemini 기반 서버 답변을 우선한다.
- GitHub API, Notion API, Webhook은 선택 기능으로 둔다.
- Local LLM과 다른 Provider는 필요할 때 추가한다.
- Mock Data로 전체 앱 흐름을 먼저 완성한다.


## 20. Codex에게 요청

이 문서를 RAGent 프로젝트의 전체 방향과 요구사항으로 사용한다.

Android Studio에서 생성한 빈 프로젝트를 기준으로 작업한다.

현재는 Phase 2의 Firebase 실제 데이터 연동을 우선 구현한다.

즉:

Project List
→ 프로젝트 생성
→ 프로젝트 선택
→ 프로젝트 내부 Bottom Navigation
→ Overview / Docs / Repository / Agent

흐름이 정상적으로 동작하는 Android 앱의 기본 뼈대를 만든다.

향후 Firestore, Cloud Functions, 공개 링크 동기화, RAG, Gemini를 단계적으로 추가할 예정이므로 각 기능이 지나치게 강하게 결합되지 않도록 구조를 설계한다.

현재 단계에서 GitHub API, Notion API, Webhook, Embedding, Vector Search, Gemini를 한 번에 구현하지 않는다.

먼저 앱이 정상적으로 빌드되고 실행되는 것을 최우선으로 한다.


## 21. Gemini Agent 구현 방향

초기 Agent는 Firebase AI Logic과 Gemini 생성 모델을 사용한다.

### 답변 흐름

사용자 질문
→ Cloud Functions 요청
→ 질문 Embedding
→ Firestore Vector Search
→ 관련 Chunk Top-K 검색
→ Gemini 생성 모델에 Context 전달
→ 답변 + 출처

### 모델 선택

Gemini 2.5 Flash 또는 Flash-Lite를 답변 품질, 속도와 비용을 비교해 선택한다.

### 답변 표시

답변은 가능하면 streaming 방식으로 표시한다. 초기 구현에서는 서버 응답 구조를 먼저 안정화하고 이후 UI streaming을 추가한다.

### Local LLM

Local LLM, llama.cpp, GGUF 모델은 Gemini 기반 Agent 이후 필요성과 운영 여건을 확인한 뒤 선택적으로 검토한다.


## 22. Phase 1 완료 상태

Phase 1은 완료된 것으로 본다.

완료 기준:

- Android 앱이 정상 빌드된다.
- Project List에서 프로젝트 목록을 확인할 수 있다.
- 프로젝트 생성 UI가 Bottom Sheet로 제공된다.
- 프로젝트를 선택하면 프로젝트 내부 화면으로 진입한다.
- 프로젝트 내부에서 Docs, Repository, Members, Agent 탭을 이동할 수 있다.
- 프로젝트 내부에서만 Bottom Navigation이 표시된다.
- 프로젝트 상세 정보는 Bottom Sheet로 표시된다.
- 프로젝트 상세 정보에서 역할, GitHub, Docs, 멤버, 최신 PR, 공개 범위, 삭제/나가기 액션을 확인할 수 있다.
- GitHub LinkMarker는 외부 브라우저 이동 흐름을 가진다.
- Members 탭은 Admin과 Member를 표시하고 Viewer는 제외한다.
- 전체 메시지, 프로젝트 메시지, 1:1 DM 화면의 Mock UI가 구성되어 있다.
- Person 상세 화면으로 이동할 수 있다.
- ChatList, DM, Members 탭의 스크롤 위치가 화면 이동 후에도 복원된다.
- 시스템 뒤로가기와 앱 내부 뒤로가기 흐름이 일관되게 동작한다.
- 라이트 모드와 다크 모드 색상이 기본 적용되어 있다.
- Firebase Authentication은 구현했고, Firestore·Cloud Functions·GitHub / Notion 동기화·RAG·Gemini는 이후 단계에서 구현한다.

Phase 1 PR / 기록:

- Branch: `feature/phase-1-foundation`
- PR title: `Complete Phase 1: UI, Navigation and Mock Project Foundation`
- Initial commit: `e60c083e6e8de347b170ce4f838a4b82d9e446d5`
- Notion 기록: `RAGent Pull Requests` 데이터베이스에 Phase 1 정리 페이지를 작성했다.

다음 작업은 Phase 2의 Firestore 연동부터 진행한다.

Phase 2의 우선순위:

- Firebase Authentication 연결
- Firestore 기반 Project / Person / ProjectMember 데이터 구조 설계
- Mock Data를 Firebase 데이터로 교체할 준비
- 프로젝트 생성, 조회, 삭제의 서버 저장 흐름 구현
- 사용자 권한과 프로젝트 공개 범위 정책을 실제 데이터 기준으로 정리

Phase 2 이후에는 다음 순서로 진행한다.

1. Firebase Blaze 전환 및 TypeScript Cloud Functions 구성
2. 공개 GitHub / Notion 링크 연결과 서버 동기화 요청
3. 변경된 Source의 Chunking 및 Gemini Embedding
4. Firestore Vector Search와 Gemini Agent 답변
5. GitHub API, Notion API, Webhook, Local LLM은 필요할 경우 선택 기능으로 추가
