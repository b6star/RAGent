# RAGent


RAGent는 GitHub Repository와 Notion 문서를 하나의 프로젝트 지식으로 연결하고,
RAG 기반 AI Agent를 통해 프로젝트의 코드와 문서를 탐색하고 질문할 수 있도록 하는 Android 애플리케이션입니다.

## RAG

RAG(Retrieval-Augmented Generation)는 LLM이 답변을 생성하기 전에
외부 데이터에서 질문과 관련된 정보를 검색하여 답변에 활용하는 방식입니다.

RAGent에서는 GitHub 코드와 Notion 문서를 프로젝트 지식으로 사용합니다.
프로젝트 전체 내용을 LLM에 전달하는 대신, 사용자의 질문과 관련된 코드와 문서만 검색하여 LLM에 제공합니다.


## 주요 기능


- 여러 프로젝트 관리
- GitHub Repository 연동 및 코드 탐색
- Notion 프로젝트 문서 연동
- 프로젝트 멤버 및 권한 관리
- RAG 기반 프로젝트 정보 검색
- Local LLM 기반 AI Agent
- Pull Request 확인 및 진행 중인 작업 확인


## RAGent의 RAG 구조


GitHub와 Notion의 프로젝트 데이터를 Chunking 및 Embedding하여 검색 가능한 형태로 관리합니다.


사용자가 Agent에게 질문하면 전체 프로젝트를 LLM에 전달하지 않고,
RAG를 통해 질문과 관련된 정보만 검색하여 LLM Context로 제공합니다.


```text
GitHub / Notion
      ↓
   Chunking
      ↓
  Embedding
      ↓
 Vector 저장

사용자 질문
      ↓
Vector Search
      ↓
관련 Chunk 검색
      ↓
  Local LLM
      ↓
    Answer

초기 Local LLM은 Qwen3-4B GGUF 모델과 llama.cpp 기반 실행을 고려하고 있으며,
향후 외부 LLM API도 선택적으로 지원할 예정입니다.

Tech Stack
Kotlin
Jetpack Compose
Firebase
GitHub API / Webhook
Notion
RAG
llama.cpp
Qwen3-4B
Development Status

현재 Phase 1에서는 Android 애플리케이션의 기본 구조와 UI를 구현하고 있습니다.

이후 Firebase → GitHub → Notion → RAG → Local LLM 순서로 기능을 확장할 예정입니다.