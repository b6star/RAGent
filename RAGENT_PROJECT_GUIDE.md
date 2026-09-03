# RAGent Project Guide

## Project purpose

RAGent is an Android application that collects public GitHub and Notion sources, normalizes them into documents, creates searchable chunks and vectors, and uses an AI Agent to answer questions with source context.

## Completed phases

Phase 1-4 are complete, including authentication, project/member access, public source collection, Notion crawling, manifests, snapshots, normalized Documents, staging, active revision promotion, and source synchronization workers.

Phase 5 was completed on 2026-09-01.

### Phase 5 contract

- Source Revision and RAG Revision are separate.
- RAG state transitions are `pending -> chunking -> embedding -> ready/failed`.
- Only a ready revision becomes active.
- Failed revisions never replace the previous active revision.
- Embedding contract is `gemini-embedding-001`, dimension `768`, provider `gemini-developer`.

### Phase 5 chunking

- Notion chunks preserve headings, blocks, page identity, and anchors.
- GitHub chunks preserve Markdown sections, code symbols, paths, and line ranges.
- Chunk IDs are deterministic.
- Content hashes enable change detection.
- Adjacent chunks link through previous/next IDs.

### Phase 5 embedding

- Only new or changed chunks are sent to Gemini.
- Unchanged vectors are reused by chunk ID and content hash.
- Chunks and vectors are staged under one RAG revision.
- Cloud Tasks runs the embedding worker in `asia-northeast3`.
- Retry/resume state and progress counters are persisted.
- Vector index configuration is in `firestore.indexes.json`.

### Phase 5 retrieval

- Callable: `searchRagChunks`.
- The query uses `RETRIEVAL_QUERY` with the same embedding contract.
- Firestore Vector Search uses COSINE distance.
- Search is scoped to the active ready revision and authenticated project members.
- Results include chunk content, source URL, anchor, and distance.
- Production smoke testing confirmed successful Top-K retrieval.

### Phase 5 usage accounting

Usage records are stored independently at `users/{uid}/ai_usage/{usageId}`.

Categories:

- `personal`: user-provided AI API
- `developer`: developer AI API
- `server_embedding`: document embedding attributed to the user who triggered Source Sync
- `server_search`: query embedding attributed to the user who called retrieval

Server embedding records include estimated token count, chunk count, and character count. Estimated tokens use `characterCount / 4`; this is display telemetry, not an exact tokenizer result. Project deletion does not remove user usage records.

## Current deployment

Firebase project: `ragent-d6b01`

Functions and Cloud Tasks region: `asia-northeast3`

Deploy:

```powershell
firebase deploy --only functions,firestore:indexes --project ragent-d6b01
```

## Phase 6 starting point

Phase 6 is the RAG Agent integration layer. It must connect retrieval to the existing Agent without changing the existing provider/key behavior.

Recommended implementation order:

1. From the Agent question flow, call `searchRagChunks(projectId, query, topK)`.
2. If results exist, build a bounded context containing content and source anchors.
3. Add the context to the existing Gemini/OpenAI prompt.
4. If retrieval is unavailable or empty, use the existing prompt unchanged.
5. Store and render citations with the assistant answer.
6. Keep personal API and developer API usage records separate from server embedding/search usage.

Do not move user API keys to the server. Do not expose raw vectors to Android. Keep chunks and vectors server-only.

## Verification checklist for Phase 6 entry

- Active RAG revision is `ready`.
- Vector index is enabled.
- `searchRagChunks` returns content and source anchors.
- Existing non-RAG Agent answers still work.
- No user API key is sent to a server function.
- Phase 5 usage categories remain unchanged.
