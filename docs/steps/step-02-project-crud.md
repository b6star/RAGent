# Phase 2 / Step 02 - Project CRUD

## Branch

```text
feature/firebase-project-crud
```

## Goal

Mock 프로젝트 데이터를 Firestore 데이터로 교체하고 프로젝트 생성 및 조회 흐름을 완성한다.

## Scope

- 프로젝트 생성
- 프로젝트 목록 조회
- 프로젝트 상세 조회
- 프로젝트 삭제 또는 나가기
- 기존 Mock 프로젝트 데이터 교체
- 로그인한 사용자의 프로젝트 목록 조회

## Done

- 프로젝트를 생성하면 Firestore에 저장된다.
- Project List가 Firestore 데이터를 표시한다.
- 프로젝트를 선택하면 Firestore 상세 데이터가 표시된다.
- 앱을 다시 실행해도 프로젝트 데이터가 유지된다.
- 프로젝트 삭제 또는 나가기 동작이 실제 데이터에 반영된다.
- 데이터가 없는 상태와 오류 상태를 처리한다.

## Out of Scope

- 멤버 초대
- Admin / Member / Viewer 권한의 세부 처리
- 프로젝트 공유
- GitHub / Notion 연결
