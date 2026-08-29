# Firestore rules analysis: public source links

- Scope: `projects/{projectId}` fields `githubUrl` and `docsUrl`.
- Both fields must exist, be strings, and be no longer than 2,048 characters.
- GitHub links are restricted to canonical public repository URLs.
- Notion links are restricted to Notion-owned hosts (`notion.site`, `notion.so`, their
  subdomains, and `app.notion.com`); paths must not contain whitespace, queries, or fragments.
- Create remains restricted to the authenticated owner. Updating either source link remains
  restricted to project admins by the existing changed-key checks.
- Reviewed malformed labels, unrelated domains, HTTP URLs, oversized values, queries, and
  fragments. They remain rejected by the rules.
