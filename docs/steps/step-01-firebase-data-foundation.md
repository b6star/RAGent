# Phase 2 / Step 01 - Firebase Data Foundation

## Status

- Branch: `feature/firebase-data-foundation`
- PR: #2
- Result: Merged

## Goal

로그인 사용자를 Firestore에 연결하고 Phase 2에서 사용할 Firebase 데이터 기반을 구성한다.

## Completed

- Firestore 의존성과 Firebase 연결
- 로그인 사용자 프로필 저장 및 갱신
- User와 Project Firestore 모델 정의
- 인증 사용자 기준 Security Rules 구성
- Firebase 로그인 Provider 정보 저장

## Data

| 경로 | 용도 |
| --- | --- |
| `users/{uid}` | Firebase 사용자 프로필 |
| `projects/{projectId}` | 프로젝트 공용 정보 |

## Excluded

- 프로젝트 CRUD
- 멤버 초대와 역할 관리
- Gemini 및 RAG
