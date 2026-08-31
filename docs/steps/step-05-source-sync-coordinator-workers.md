# Phase 4 Step 3.2~3.3 - Source Sync Coordinator와 Collectors

작성: 2026-08-30 KST

## 결과

프로젝트 진입 요청을 인증된 단일 작업으로 바꾸는 coordinator와 공개
GitHub·Notion Source를 실제로 수집해 SHA-256 manifest를 만드는 worker를
구현했다.

## Step 3.2 - Coordinator

- Android가 프로젝트에 진입하거나 Source URL이 바뀌면 `requestSourceSync`를 호출한다.
- Callable은 App Check와 Firebase Auth를 요구한다.
- 요청자가 프로젝트 소유자 또는 `members/{uid}` 문서가 있는 멤버인지 확인한다.
- 클라이언트가 보낸 URL을 신뢰하지 않고 `projects/{projectId}`에 저장된 URL만 사용한다.
- Firestore transaction에서 `throttleUntil`과 `leaseExpiresAt`을 함께 판정한다.
- 활성 lease가 있으면 `in-progress`, 최근 완료 요청이면 `throttled`를 즉시 반환한다.
- URL이 바뀌었거나 실행 가능한 요청이면 상태를 `queued`로 바꾸고 Cloud Tasks에 하나만 등록한다.
- Queue 등록 실패 시 lease를 해제하고 멤버에게 안전한 오류만 상태 문서에 남긴다.

## Step 3.3 - Collectors

### GitHub

- GitHub API 없이 공개 Git 프로토콜을 사용해 depth 1 clone을 수행한다.
- checkout 없이 commit tree의 추적 파일을 순회한다.
- UTF-8 텍스트 파일만 수집하고 파일 수·개별 크기·총 텍스트 용량·실행 시간 제한을 적용한다.
- 파일별 SHA-256과 전체 manifest hash를 계산한다.

### Notion

- Chromium 작업은 Cloud Functions에서 분리한 private Cloud Run 서비스에서 실행한다.
- Playwright로 JavaScript 렌더링 후 토글·댓글을 펼치고 끝까지 스크롤한다.
- 스크롤 구간별 text와 href snapshot을 수집·병합한다.
- 32자리 Notion Page ID를 방문 키로 사용해 URL 변형에 따른 중복 방문을 막는다.
- 허용된 공개 Notion 링크만 깊이·페이지·용량·시간 제한 안에서 탐색한다.
- 렌더링 중 private/local 네트워크 요청을 차단한다.

참고 구현: [Playwright로 공개 Notion 페이지 수집하기](https://app.notion.com/p/3c3974b809de81bb8997ea3e6bceb703)

## 저장 결과

- Firebase Storage: `source-sync/{projectId}/{sourceType}/{revisionId}.json.gz`
- Firestore `sources/{github|notion}`: manifest hash, snapshot object path,
  upstream revision, item count, byte count, active/staging revision
- Firestore `sourceSync/status`: 프로젝트 집계 상태와 combined revision
- Firestore `sourceSyncJobs/{jobId}`: Queue·실행·재시도·완료 감사 상태

원문 snapshot은 immutable하게 저장한다. 기존 manifest와 새 manifest가 같으면
변경 없음으로 처리하고, 다르면 `checking → changed → ready` 순서로 새 revision을
승격한다.

## 검증

- Functions TypeScript build와 ESLint 통과
- Source Sync 단위 테스트 8개 통과
- Notion crawler TypeScript build와 단위 테스트 4개 통과
- 설치된 Playwright Chromium headless launch 성공
- Android `:app:compileDebugKotlin` 성공

## 배포 전 필요한 외부 설정

- private Cloud Run `ragent-notion-crawler` 배포
- Functions 실행 서비스 계정의 Cloud Run Invoker와 Cloud Tasks Enqueuer 권한
- crawler 서비스 계정의 Firebase Storage object creator 권한
- Functions parameter `NOTION_CRAWLER_URL` 설정

이 작업은 코드 구현과 로컬 검증까지 완료했으며 Cloud Run·Functions 실제 배포와
IAM 변경은 아직 수행하지 않았다.

## 다음 작업

Phase 4 Step 4에서 snapshot의 파일·페이지를 공통 Document·Metadata 모델로
정규화한다. 이후 Phase 5에서 변경된 Document의 Chunk만 Embedding한다.
