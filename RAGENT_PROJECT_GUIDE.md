# RAGent 프로젝트 구현 가이드

## 1. 프로젝트 목표

RAGent는 여러 소프트웨어 프로젝트의 GitHub Repository와 Notion 문서를 하나의 지식으로 연결하는 Android 협업 앱이다. 사용자는 프로젝트 원문을 직접 탐색하거나, 최신 프로젝트 정보를 바탕으로 Gemini Agent에게 질문할 수 있다.

핵심 원칙:

- GitHub와 Notion은 공개 링크 연결을 우선한다.
- Android 앱은 화면과 사용자 요청을 담당한다.
- Firebase는 인증, 협업 데이터와 서버 AI 실행을 담당한다.
- 프로젝트 지식과 Vector는 참여자가 Firebase에서 공유한다.
- GitHub API, Notion API와 Webhook은 선택 기능으로 둔다.
- Local LLM과 다른 AI Provider는 현재 범위에 포함하지 않는다.

## 2. 현재 구현 상태

### Phase 1 - Android 앱 기반 완료

- Project List와 프로젝트 내부 Navigation
- Overview, Docs, Repository, Members, Agent 화면
- 프로젝트 및 사용자 상세 화면
- Chat과 DM Mock 화면
- 라이트 모드와 다크 모드

### Phase 2 - Firebase 협업 데이터 완료

- Firebase Authentication과 Google 로그인
- Firestore 사용자 정보 저장
- 프로젝트 생성, 조회, 수정, 삭제
- 소유 프로젝트와 참여 프로젝트 통합 조회
- Admin, Member, Viewer 역할 저장과 권한 적용
- 역할별 초대 링크 생성, 재사용과 재발급
- Android App Links 기반 프로젝트 참여
- 멤버 역할 변경, 삭제와 프로젝트 나가기
- 프로젝트 공개 여부 변경
- Firestore Security Rules와 Collection Group 인덱스
- 프로젝트 목록 Pull-to-Refresh

### Phase 3 - AI 연동 및 고도화 (진행 중)

- Firebase Cloud Functions (TypeScript) 기반 `askGemini` Endpoint 구축
- 하이브리드 데이터 흐름: 메시지 저장은 안드로이드 앱에서 직접 Firestore 수행, AI 호출은 서버에서 처리
- iOS 스타일 글래스모피즘 입력창 및 현대적인 채팅 UI 적용
- 프로젝트별 다중 대화 세션 목록화 및 독립된 채팅 화면 (`AgentChatScreen`)
- 마크다운 렌더링 지원 및 AI 응답 메타데이터 표시
- 사용자별 AI 사용량(토큰) 서버 기반 실시간 집계 기초 로직

## 3. 현재 Firebase 구조

| 경로 | 용도 |
| --- | --- |
| `users/{uid}` | 로그인 사용자 프로필 및 `totalAiTokens` 누적 통계 |
| `users/{uid}/ai_chats/{projectId}/sessions/{sessionId}` | 프로젝트별 AI 채팅 세션 정보 |
| `users/{uid}/ai_chats/{projectId}/sessions/{sessionId}/messages/{messageId}` | 개별 대화 내역 (하이브리드 저장) |
| `projects/{projectId}` | 프로젝트 공용 정보와 연결 URL |
| `projects/{projectId}/members/{uid}` | 사용자 역할과 참여 정보 |
| `projects/{projectId}/invites/{inviteId}` | Member 또는 Viewer 초대 정보 |

## 4. Android와 서버의 책임

### Android 앱

- 로그인과 프로젝트 선택
- 대화 내역 직접 저장 (실시간성 확보)
- 프로젝트 생성 및 관리 UI
- Agent 질문 전송과 답변 표시 (Glassmorphism UI)

### Firebase 서버

- 사용자 인증 및 보안 규칙 검증
- Gemini 요청의 비밀키 보호
- **멀티 프로바이더 엔진 (준비 중)**
- **사용자별 무료 체험 제한 및 토큰 집계**

## 5. Phase 3 - AI 연동 및 고도화 상세 스텝

현재 **하이브리드 아키텍처**를 기반으로 기능을 고도화하고 있다.

### 스텝 1: UI 고도화 및 기초 연동 (완료)
- `AgentTab` 세션 목록화 및 `AgentChatScreen` 독립
- 글래스모피즘 입력창 및 가변 메시지 버블 구현
- `askGemini` 서버 호출 및 기초 메시지 저장 로직 완성

### 스텝 2: Backend - 멀티 AI 허브 및 사용량 제한 (예정)
- OpenAI(GPT), Anthropic(Claude) SDK 통합
- 개발자 키 사용 시 계정당 무료 10회 제한 로직 (Firestore 필드 기반)
- 유저 개인 API 키 파라미터 처리 엔진 구현

### 스텝 3: Android - AI 설정 및 상세 통계 (예정)
- AI 설정 UI: 프로바이더 선택 및 개인 API 키 입력 필드
- 세션별 누적 토큰 및 유저 전체 통계 실시간 UI 반영
- 에러 처리 가이드 및 로딩 상태 정교화

## 6. 이후 개발 순서

### Phase 4 - 공개 Source 연결
- 공개 GitHub Repository URL 수집
- 공개 Notion Page URL 수집
- Docs WebView와 Repository 탐색 연결

### Phase 5 - RAG 연결
- 변경된 Source만 Chunking 및 Gemini Embedding
- Firestore Vector Search (Top-K Retrieval)

### Phase 6 - Gemini RAG Agent
- 검색 Context를 Gemini Prompt에 연결하여 답변 및 출처 표시

## 10. 현재 작업 위치

- 완료: Phase 1, Phase 2
- 진행 중: **Phase 3 / 스텝 2 시작 예정**
- 특징: 하이브리드 저장 방식 적용 완료, UI 리뉴얼 완료
