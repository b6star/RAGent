# Phase 2 / Step 01 - Firebase Data Foundation

## Branch

```text
feature/firebase-data-foundation
```

## Goal

로그인한 사용자를 Firestore와 연결하고, Phase 2에서 사용할 Firebase 데이터 기반을 만든다.

## Scope

- Firestore 의존성 추가
- Firestore 연결
- 로그인 사용자 정보 저장 및 조회
- Project / User / ProjectMember 데이터 구조 정의
- Firestore 기본 Security Rules 작성

## Data

```text
users/{uid}
projects/{projectId}
projects/{projectId}/members/{uid}
```

## Done

- Google 로그인 사용자가 Firestore에 저장된다.
- 앱 재실행 후 사용자 정보를 조회할 수 있다.
- 프로젝트 데이터 구조가 정해져 있다.
- 인증되지 않은 사용자의 Firestore 접근이 제한된다.
- 다음 Step에서 사용할 Firebase 데이터 접근 기준이 정리되어 있다.

## Out of Scope

- 프로젝트 생성 및 목록 UI 연동
- 멤버 초대와 역할별 권한
- GitHub / Notion 연결
- Cloud Functions, RAG, Gemini
