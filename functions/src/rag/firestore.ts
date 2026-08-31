import {Firestore} from "firebase-admin/firestore";

export const RAG_FIRESTORE = Object.freeze({
  projectsCollection: "projects",
  ragRevisionsCollection: "ragRevisions",
  chunksCollection: "chunks",
  vectorsCollection: "vectors",
  embeddingJobsCollection: "embeddingJobs",
  metadataDocument: "ragMetadata",
});

/**
 * Returns stable Firestore references for the RAG data hierarchy.
 * @param {Firestore} db Admin Firestore instance
 * @param {string} projectId Project document ID
 * @return {object} RAG metadata, revision, chunk, vector, and job references
 */
export function ragReferences(db: Firestore, projectId: string) {
  const project = db.collection(
    RAG_FIRESTORE.projectsCollection
  ).doc(projectId);
  const revisions = project.collection(RAG_FIRESTORE.ragRevisionsCollection);
  return {
    project,
    metadata: project.collection("rag").doc(RAG_FIRESTORE.metadataDocument),
    revisions,
    revision: (revisionId: string) => revisions.doc(revisionId),
    chunks: (revisionId: string) => revisions.doc(revisionId)
      .collection(RAG_FIRESTORE.chunksCollection),
    vectors: (revisionId: string) => revisions.doc(revisionId)
      .collection(RAG_FIRESTORE.vectorsCollection),
    embeddingJobs: (revisionId: string) => revisions.doc(revisionId)
      .collection(RAG_FIRESTORE.embeddingJobsCollection),
  };
}
