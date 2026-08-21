# Phase 2 / Step 03 - Members, Roles and Sharing

## Status

- Branch: `feature/firebase-members-roles`
- PR: #4
- Result: Open, implementation complete

## Goal

여러 사용자가 프로젝트를 공유하고 역할에 맞는 권한으로 참여할 수 있도록 Firebase 협업 구조를 완성한다.

## Completed

- Admin, Member, Viewer 역할 저장과 조회
- Member 및 Viewer 초대 링크 분리
- 초대 링크 재사용과 재발급
- Android App Links 기반 프로젝트 참여
- 참여 프로젝트 조회와 Collection Group 인덱스
- Member와 Viewer 역할 변경
- Admin 강등 및 삭제 차단
- 일반 멤버 삭제와 프로젝트 나가기
- 프로젝트 공개 여부 변경
- Firestore Security Rules 세부 적용
- Project List Pull-to-Refresh

## Permission

| 기능 | Admin | Member | Viewer |
| --- | --- | --- | --- |
| 프로젝트 조회 | Yes | Yes | Yes |
| 프로젝트 관리 | Yes | No | No |
| 초대 및 멤버 관리 | Yes | No | No |
| 댓글 작성 | Yes | Yes | Yes |
| 프로젝트 나가기 | No | Yes | Yes |

## Verification

- 초대 참여 후 Firestore 멤버 저장 확인
- 다른 계정과 기기에서 참여 프로젝트 조회 확인
- 앱 재실행 후 참여 프로젝트 유지 확인
- 역할 변경, 멤버 삭제와 나가기 흐름 확인
- 최종 Pull-to-Refresh 변경 이후 Debug 빌드는 별도로 실행하지 않음

## Next

PR #4 병합 후 Phase 3의 Gemini AI 기본 연동을 시작한다.
