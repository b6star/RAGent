const GITHUB_OWNER_PATTERN = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/;
const GITHUB_REPOSITORY_PATTERN = /^[A-Za-z0-9._-]{1,100}$/;
const NOTION_PAGE_ID_PATTERN = /[0-9a-f]{32}/gi;
const NOTION_DASHED_PAGE_ID_PATTERN =
  /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;

/**
 * Canonicalizes a public GitHub repository URL without using the GitHub API.
 * @param {unknown} value Candidate URL
 * @return {string|null} Canonical URL or null when unsupported
 */
export function canonicalizeGithubUrl(value: unknown): string | null {
  if (typeof value !== "string" || !value.trim()) return null;
  try {
    const url = new URL(value.trim());
    if (url.protocol !== "https:" || url.hostname.toLowerCase() !==
        "github.com" || url.username || url.password || url.port) {
      return null;
    }
    const segments = url.pathname.split("/").filter(Boolean);
    if (segments.length !== 2) return null;
    const owner = segments[0];
    const repository = segments[1].replace(/\.git$/i, "");
    if (!GITHUB_OWNER_PATTERN.test(owner) ||
        !GITHUB_REPOSITORY_PATTERN.test(repository)) {
      return null;
    }
    return `https://github.com/${owner}/${repository}`;
  } catch {
    return null;
  }
}

/**
 * Returns whether a hostname belongs to a supported public Notion surface.
 * @param {string} hostname Hostname to inspect
 * @return {boolean} Whether it is a Notion hostname
 */
export function isNotionHostname(hostname: string): boolean {
  const normalized = hostname.toLowerCase();
  return normalized === "notion.so" || normalized.endsWith(".notion.so") ||
    normalized === "notion.site" || normalized.endsWith(".notion.site") ||
    normalized === "app.notion.com";
}

/**
 * Canonicalizes a public Notion URL while removing tracking parameters.
 * @param {unknown} value Candidate URL
 * @return {string|null} Canonical URL or null when unsupported
 */
export function canonicalizeNotionUrl(value: unknown): string | null {
  if (typeof value !== "string" || !value.trim()) return null;
  try {
    const url = new URL(value.trim());
    if (url.protocol !== "https:" || !isNotionHostname(url.hostname) ||
        url.username || url.password || url.port) {
      return null;
    }
    url.hash = "";
    for (const parameter of [
      "pvs",
      "source",
      "utm_source",
      "utm_medium",
      "utm_campaign",
    ]) {
      url.searchParams.delete(parameter);
    }
    url.hostname = url.hostname.toLowerCase();
    url.pathname = url.pathname.replace(/\/$/, "");
    return url.toString();
  } catch {
    return null;
  }
}

/**
 * Produces the stable crawl key recommended for rendered Notion pages.
 * @param {string} rawUrl Notion page URL
 * @return {string} Lower-case page ID or canonical URL fallback
 */
export function notionPageKey(rawUrl: string): string {
  const canonicalUrl = canonicalizeNotionUrl(rawUrl);
  if (!canonicalUrl) return rawUrl;
  const dashedMatches = canonicalUrl.match(NOTION_DASHED_PAGE_ID_PATTERN);
  if (dashedMatches?.length) {
    return dashedMatches[dashedMatches.length - 1]
      .replace(/-/g, "").toLowerCase();
  }
  const matches = canonicalUrl.match(NOTION_PAGE_ID_PATTERN);
  return matches?.length ? matches[matches.length - 1].toLowerCase() :
    canonicalUrl;
}
