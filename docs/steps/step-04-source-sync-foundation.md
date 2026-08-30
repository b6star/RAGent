# Phase 4 Step 3.1 - Source Sync Foundation

작성: 2026-08-30 KST

## 목표

공개 GitHub·Notion Source 동기화를 시작하기 전에 서버 정책의 단일 설정
원본과 Firestore 문서 구조, 상태 전이 규칙을 확정한다.

이 단계는 실제 Git·Chromium 수집이나 동기화 요청 Endpoint를 실행하지
않는다. 이후 coordinator와 worker가 같은 모델을 사용하도록 기반만 제공한다.

## Firestore 대상

- Project: `ragent-d6b01`
- Database: `(default)`
- Edition: Standard / Firestore Native
- Location: `asia-northeast3`

## 저장 구조

| 경로 | 공개 범위 | 용도 |
| --- | --- | --- |
| `projects/{projectId}/sourceSync/status` | 이후 프로젝트 멤버 읽기 | 앱에 표시할 집계 상태와 최근 시각·안전한 오류 |
| `projects/{projectId}/sourceSync/control` | 서버 전용 | 작업 ID, lease, throttle과 시도 횟수 |
| `projects/{projectId}/sources/github` | 이후 프로젝트 멤버 읽기 | GitHub canonical URL, manifest와 revision 상태 |
| `projects/{projectId}/sources/notion` | 이후 프로젝트 멤버 읽기 | Notion canonical URL, manifest와 revision 상태 |

현재 Firestore Rules에는 새 경로의 허용 규칙을 추가하지 않았다. 따라서
기존 default-deny에 의해 클라이언트 읽기·쓰기가 모두 거부되고 Admin SDK만
접근한다. Android 상태 UI를 연결할 때 `status`와 `sources`의 멤버 읽기만
별도 검토하고, `control`은 계속 서버 전용으로 유지한다.

## 상태 모델

```text
idle → queued → checking → ready
                      └→ changed → ready
queued/checking/changed → error → queued
ready → queued
```

같은 상태의 재저장은 진행 시각 갱신을 위해 허용한다. 그 외 전이는 서버의
상태 전이 함수에서 거부한다.

상태 문서에는 다음 값을 저장한다.

- `schemaVersion`
- `status`
- `activeRevisionId`
- `lastRequestedAt`
- `lastCheckedAt`
- `lastChangedAt`
- `lastCompletedAt`
- `lastError`
- `updatedAt`

서버 전용 control 문서에는 다음 값을 저장한다.

- `schemaVersion`
- `activeJobId`
- `leaseOwner`
- `leaseExpiresAt`
- `throttleUntil`
- `attempt`
- `updatedAt`

## 공통 설정 원본

`functions/src/sourceSync/config.ts`를 Source 동기화 서버 정책의 단일 설정
원본으로 사용한다.

- schema·policy·manifest·extractor 버전
- Hash 알고리즘
- throttle과 lease 시간
- 최대 시도 횟수와 안전한 오류 크기
- GitHub 파일 수·파일/총 용량·실행 시간
- Notion 페이지 수·탐색 깊이·페이지/총 용량·실행 시간

Android는 이 제한값을 복제하지 않는다. 이후 Callable 응답과 Firestore 상태를
통해 필요한 사용자 정보를 받는다.

## 구현 파일

- `functions/src/sourceSync/config.ts`
- `functions/src/sourceSync/model.ts`
- `functions/src/sourceSync/firestore.ts`
- `functions/src/sourceSync/model.test.ts`
- `app/src/main/java/com/yourssu/ragent/model/SourceSyncDocument.kt`

## 검증

- Functions TypeScript build 성공
- Source Sync 상태·Source type·초기 문서 단위 테스트 3개 성공
- 신규 Source Sync TypeScript 파일 ESLint 성공
- Android `testDebugUnitTest` 성공
- 전체 Functions lint는 기존 파일의 CRLF·JSDoc 오류로 실패하며 신규 파일에는
  같은 오류가 없음

## 다음 작업

인증된 `requestSourceSync` coordinator를 추가한다.

1. 프로젝트와 멤버 권한 확인
2. `status`·`control` 초기화
3. Firestore transaction으로 throttle과 lease 판정
4. 하나의 백그라운드 작업만 Queue
5. Android 요청에는 현재 상태를 즉시 반환
