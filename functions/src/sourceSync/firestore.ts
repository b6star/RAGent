import {Firestore} from "firebase-admin/firestore";

export const SOURCE_SYNC_FIRESTORE = Object.freeze({
  projectsCollection: "projects",
  syncCollection: "sourceSync",
  statusDocument: "status",
  controlDocument: "control",
  sourcesCollection: "sources",
  jobsCollection: "sourceSyncJobs",
});

/**
 * Returns all Firestore references needed by the synchronization coordinator.
 * @param {Firestore} db Admin Firestore instance
 * @param {string} projectId Project document ID
 * @return {object} Stable project, status, control, and source references
 */
export function sourceSyncReferences(db: Firestore, projectId: string) {
  const project = db.collection(
    SOURCE_SYNC_FIRESTORE.projectsCollection
  ).doc(projectId);
  const sync = project.collection(SOURCE_SYNC_FIRESTORE.syncCollection);
  const sources = project.collection(
    SOURCE_SYNC_FIRESTORE.sourcesCollection
  );

  return {
    project,
    status: sync.doc(SOURCE_SYNC_FIRESTORE.statusDocument),
    control: sync.doc(SOURCE_SYNC_FIRESTORE.controlDocument),
    github: sources.doc("github"),
    notion: sources.doc("notion"),
    jobs: project.collection(SOURCE_SYNC_FIRESTORE.jobsCollection),
  };
}
