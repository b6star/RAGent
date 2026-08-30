# Phase 2 Development Steps

Phase 2의 Firebase 실제 데이터 구현을 Step과 PR 단위로 기록한다.

## Status

| Step | Branch | PR | Status |
| --- | --- | --- | --- |
| [Step 01 - Firebase Data Foundation](step-01-firebase-data-foundation.md) | `feature/firebase-data-foundation` | #2 | Merged |
| [Step 02 - Project CRUD](step-02-project-crud.md) | `feature/firebase-project-crud` | #3 | Merged |
| [Step 03 - Members, Roles and Sharing](step-03-members-roles-sharing.md) | `feature/firebase-members-roles` | #4 | Open |

## Phase 4

| Step | Branch | Status |
| --- | --- | --- |
| [Step 3.1 - Source Sync Foundation](step-04-source-sync-foundation.md) | `feature/public-source-sync` | Complete |
| [Step 3.2~3.3 - Coordinator와 Collectors](step-05-source-sync-coordinator-workers.md) | `feature/public-source-sync` | Complete (deployment pending) |

Phase 2 구현은 완료되었고 Step 03의 `main` 병합만 남아 있다.

## Current Work

Phase 4 Step 3의 coordinator와 GitHub·Notion 수집 worker 구현을 완료했다.
다음 작업은 snapshot을 공통 Document·Metadata로 정규화하는 Step 4다.
