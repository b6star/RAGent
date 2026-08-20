# Phase 2 / Step 03 - Members, Roles and Sharing

## Branch

```text
feature/firebase-members-roles
```

## Goal

프로젝트를 여러 사용자가 공유할 수 있도록 멤버와 역할별 접근 권한을 구현한다.

## Scope

- 프로젝트 멤버 저장 및 조회
- Admin / Member / Viewer 역할 저장
- 프로젝트 공유 구조
- 역할별 생성, 수정, 조회 권한
- Firestore Security Rules 세부 적용

## Permission

| Role | Read | Create | Edit | Manage Members |
| --- | --- | --- | --- | --- |
| Admin | Yes | Yes | Yes | Yes |
| Member | Yes | No | Limited | No |
| Viewer | Yes | No | No | No |

## Done

- 프로젝트에 멤버를 추가할 수 있다.
- 멤버별 역할을 조회할 수 있다.
- 역할에 따라 UI 액션이 제한된다.
- Firestore Security Rules에서도 동일한 권한이 적용된다.
- 프로젝트 참여자는 자신이 공유받은 프로젝트만 조회할 수 있다.

## Out of Scope

- GitHub / Notion 연결
- Cloud Functions
- RAG 및 Gemini 답변
