# Phase 2 / Step 02 - Project CRUD

## Status

- Branch: `feature/firebase-project-crud`
- PR: #3
- Result: Merged

## Goal

Mock 프로젝트 목록을 Firestore 데이터로 교체하고 프로젝트 CRUD 흐름을 완성한다.

## Completed

- 프로젝트 생성, 조회와 삭제
- 소유 프로젝트와 공유 프로젝트 조회
- Reference 프로젝트 조회
- `ProjectViewModel` 기반 Firestore 상태 관리
- 앱 재실행과 재설치 후 프로젝트 유지
- Firestore 조회 오류와 빈 상태 처리

## Query

| 대상 | 조건 |
| --- | --- |
| 소유 프로젝트 | `ownerId == currentUser.uid` |
| 참여 프로젝트 | `members.userId == currentUser.uid` |
| Reference 프로젝트 | 지정된 Reference ID와 Public 공개 범위 |

## Excluded

- 역할별 초대 링크
- 멤버 권한 관리
- Gemini 및 RAG
