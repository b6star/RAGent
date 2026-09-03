# RAGent

RAGent is an Android workspace assistant that connects public GitHub repositories and Notion documents to an AI agent.

## Current status

Phase 5 is complete as of 2026-09-01. The RAG infrastructure is implemented and verified through build, lint, unit tests, and a production Vector Retrieval smoke test.

## Phase 5 completed

- RAG Revision and Embedding Contract
  - Separate Source Revision and RAG Revision
  - `pending -> chunking -> embedding -> ready/failed` state machine
  - `gemini-embedding-001`, 768 dimensions, pinned model contract
  - Active revision promotion only after successful completion
- Stable Chunking
  - Notion heading/block-aware chunking
  - GitHub Markdown and code-symbol chunking with fallback
  - Deterministic `chunkId`, `contentHash`, anchors, and adjacent chunk links
- Incremental Embedding
  - Reuse unchanged vectors by `chunkId` and `contentHash`
  - Embed only new or changed chunks
  - Cloud Tasks retry/resume and progress tracking
  - Embedding worker deployed in `asia-northeast3` with 1GiB memory
- Vector Retrieval
  - `searchRagChunks` callable function
  - Query embedding with `RETRIEVAL_QUERY`
  - Firestore Vector Search using COSINE distance
  - Active revision, project membership, and source metadata validation
- Usage telemetry
  - Separate `personal`, `developer`, `server_embedding`, and `server_search` categories
  - Server usage is stored under `users/{uid}/ai_usage`
  - Embedding usage stores estimated tokens, chunk count, and character count
  - Project deletion does not delete user usage history

## Important paths

```text
functions/src/rag/model.ts              RAG contracts and revision models
functions/src/rag/chunking/             Notion/GitHub chunkers
functions/src/rag/embedding.ts          Gemini document embedding
functions/src/rag/task.ts               Cloud Tasks embedding worker
functions/src/rag/retrieval.ts          Vector Retrieval callable
functions/src/usage.ts                  Server usage persistence
firestore.indexes.json                  Vector index configuration
firestore.rules                         Client/server access boundaries
```

## Firestore RAG structure

```text
projects/{projectId}/rag/ragMetadata
projects/{projectId}/ragRevisions/{revisionId}
projects/{projectId}/ragRevisions/{revisionId}/chunks/{chunkId}
projects/{projectId}/ragRevisions/{revisionId}/vectors/{chunkId}
users/{uid}/ai_usage/{usageId}
```

Vectors are stored in the `embedding` field with dimension 768. Chunks and vectors are server-only; callable functions enforce project access.

## Deployment and verification

```powershell
firebase deploy --only functions,firestore:indexes --project ragent-d6b01
```

Verify that the active RAG revision is `ready`, its chunk/vector counts match, and the `vectors.embedding` index is enabled. `searchRagChunks` has been verified to return Top-K content, COSINE distance, and source anchors.

## Next: Phase 6

Phase 6 connects retrieved Context to the existing Agent response flow.

Recommended starting point:

1. Call `searchRagChunks` from the Agent question flow.
2. Build a bounded Context from returned chunks and source anchors.
3. Inject Context into the existing Gemini/OpenAI prompt.
4. Display citations and source locations in the answer UI.
5. Preserve the existing personal/developer API selection and usage accounting.

Phase 6 must keep retrieval optional and fall back to the existing Agent flow when no active RAG revision or search result is available.
