const DASHED_PAGE_ID =
  /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;
const PAGE_ID = /[0-9a-f]{32}/gi;

export function isNotionHostname(hostname: string): boolean {
  const normalized = hostname.toLowerCase();
  return normalized === "notion.so" || normalized.endsWith(".notion.so") ||
    normalized === "notion.site" || normalized.endsWith(".notion.site") ||
    normalized === "app.notion.com";
}

export function normalizeNotionUrl(rawUrl: string): string | null {
  try {
    const url = new URL(rawUrl);
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

export function notionPageKey(rawUrl: string): string {
  const normalized = normalizeNotionUrl(rawUrl);
  if (!normalized) return rawUrl;
  const dashedMatches = normalized.match(DASHED_PAGE_ID);
  if (dashedMatches?.length) {
    return dashedMatches[dashedMatches.length - 1]
      .replace(/-/g, "").toLowerCase();
  }
  const matches = normalized.match(PAGE_ID);
  return matches?.length ? matches[matches.length - 1].toLowerCase() :
    normalized;
}

export function uniqueNotionLinks(
  links: string[],
  currentPageKey: string
): string[] {
  const unique = new Map<string, string>();
  for (const link of links) {
    const normalized = normalizeNotionUrl(link);
    if (!normalized) continue;
    const key = notionPageKey(normalized);
    if (key !== currentPageKey && !unique.has(key)) {
      unique.set(key, normalized);
    }
  }
  return [...unique.values()];
}
