import {Browser, BrowserContext, chromium, Page} from "playwright";

import {CrawlRequest} from "./contract.js";
import {isSafeBrowserRequest} from "./network-safety.js";
import {
  normalizeNotionUrl,
  notionPageKey,
  uniqueNotionLinks,
} from "./notion-url.js";
import {
  hashContent,
  NotionCollectionResult,
  NotionSnapshotItem,
  persistSnapshot,
} from "./snapshot.js";

type QueuedPage = {url: string; depth: number};
type PageSnapshot = {
  text: string;
  height: number;
  y: number;
  viewportHeight: number;
};
type CrawlTimings = {
  navigationMs: number;
  expansionMs: number;
  scrollingMs: number;
  linkDiscoveryMs: number;
};
type CollectedPage = Awaited<ReturnType<typeof collectNotionPage>>;
const PAGE_COLLECTION_CONCURRENCY = 2;
const PAGE_COLLECTION_RETRIES = 2;

export async function crawlNotionSource(
  request: CrawlRequest
): Promise<NotionCollectionResult> {
  const canonicalUrl = normalizeNotionUrl(request.url);
  if (!canonicalUrl) {
    throw new Error("A canonical public Notion URL is required");
  }
  const canonicalRequest = {...request, url: canonicalUrl};
  const browser = await chromium.launch({
    headless: true,
    args: ["--disable-dev-shm-usage", "--no-sandbox"]
  });
  try {
    const items = await crawlPages(browser, canonicalRequest);
    if (!items.length) {
      throw new Error("Notion crawl produced no non-empty pages");
    }
    return await persistSnapshot(canonicalRequest, items);
  } finally {
    await browser.close();
  }
}

async function crawlPages(
  browser: Browser,
  request: CrawlRequest
): Promise<NotionSnapshotItem[]> {
  const startedAt = Date.now();
  const visited = new Set<string>();
  const queued = new Set<string>();
  const queue: QueuedPage[] = [{ url: request.url, depth: 0 }];
  const items: NotionSnapshotItem[] = [];
  const depthCounts = new Map<number, number>();
  const depthLinks = new Map<number, string[]>();

  const timingTotals: CrawlTimings = {
    navigationMs: 0,
    expansionMs: 0,
    scrollingMs: 0,
    linkDiscoveryMs: 0,
  };

  let totalBytes = 0;
  queued.add(notionPageKey(request.url));

  while (
    queue.length &&
    visited.size < request.policy.maximumPages
  ) {
    assertRuntime(startedAt, request);
    const batch: QueuedPage[] = [];

    while (
      queue.length &&
      batch.length < PAGE_COLLECTION_CONCURRENCY &&
      visited.size < request.policy.maximumPages
    ) {
      const current = queue.shift();
      if (!current) break;
      const pageKey = notionPageKey(current.url);
      if (visited.has(pageKey)) continue;
      visited.add(pageKey);
      batch.push(current);
    }

    const collectedBatch = await Promise.all(
      batch.map(async (current) => {
        const pageKey = notionPageKey(current.url);
        return collectPageWithRetry(
          browser,
          current,
          pageKey,
          request
        );
      })
    );

    for (const { current, pageKey, collected } of collectedBatch) {
      const byteSize = Buffer.byteLength(collected.text, "utf8");
      if (byteSize > request.policy.maximumPageBytes) {
        throw new Error(
          "A Notion page exceeded the configured byte limit"
        );
      }
      totalBytes += byteSize;
      for (
        const key of Object.keys(timingTotals) as Array<
          keyof CrawlTimings
        >
      ) {
        timingTotals[key] += collected.timings[key];
      }
      if (totalBytes > request.policy.maximumTotalBytes) {
        throw new Error(
          "Notion content exceeded the configured total limit"
        );
      }
      items.push({
        key: pageKey,
        url: collected.finalUrl,
        title: collected.title,
        content: collected.text,
        contentHash: hashContent(collected.text),
        byteSize,
      });
      depthCounts.set(
        current.depth,
        (depthCounts.get(current.depth) ?? 0) + 1
      );
      const linksAtDepth = depthLinks.get(current.depth) ?? [];
      linksAtDepth.push(collected.finalUrl);
      depthLinks.set(current.depth, linksAtDepth);

      console.log("Notion page collected", {
        projectId: request.projectId,
        pageKey,
        itemCount: items.length,
        byteSize,
      });
      if (current.depth < request.policy.maximumCrawlDepth) {
        for (const link of collected.links) {
          const childKey = notionPageKey(link);
          if (
            !visited.has(childKey) &&
            !queued.has(childKey)
          ) {
            queued.add(childKey);
            queue.push({
              url: link,
              depth: current.depth + 1,
            });
          }
        }
      }
    }
  }

  console.log("Notion crawl timing summary", {
    ...timingTotals,

    // Unlike the per-page totals above, this is the actual wall-clock time
    // observed by the caller. Parallel page work overlaps within this value.
    elapsedMs: Date.now() - startedAt,
  });

  console.log("Notion crawl depth summary", {
    itemCount: items.length,
    ...Object.fromEntries(
      Array.from(
        {
          length: request.policy.maximumCrawlDepth + 1,
        },
        (_, depth) => [
          `depth_${depth}`,
          {
            count: depthCounts.get(depth) ?? 0,
            links: depthLinks.get(depth) ?? [],
          },
        ]
      )
    ),
  });

  return items;
}
  console.log("Notion crawl timing summary", {
    ...timingTotals,
    // Unlike the per-page totals above, this is the actual wall-clock time
    // observed by the caller. Parallel page work overlaps within this value.
    elapsedMs: Date.now() - startedAt,
  });
  console.log("Notion crawl depth summary", {
    itemCount: items.length,
    ...Object.fromEntries(
      Array.from(
        {length: request.policy.maximumCrawlDepth + 1},
        (_, depth) => [`depth_${depth}`, {
          count: depthCounts.get(depth) ?? 0,
          links: depthLinks.get(depth) ?? [],
        }]
      )
    ),
  });
  return items;
}

async function collectPageWithRetry(
  browser: Browser,
  current: QueuedPage,
  pageKey: string,
  request: CrawlRequest
): Promise<{current: QueuedPage; pageKey: string; collected: CollectedPage}> {
  let lastError: unknown;
  for (let attempt = 1; attempt <= PAGE_COLLECTION_RETRIES; attempt += 1) {
    const context = await browser.newContext({
      viewport: {
        width: request.policy.viewportWidth,
        height: request.policy.viewportHeight,
      },
    });
    await installNetworkGuard(context);
    const page = await context.newPage();
    try {
      const collected = await collectNotionPage(
        page, current.url, pageKey, request
      );
      if (!collected.text.trim()) {
        throw new Error("Notion page rendered empty content");
      }
      return {current, pageKey, collected};
    } catch (error) {
      lastError = error;
      console.warn("Notion page collection attempt failed", {
        pageKey, attempt, maxAttempts: PAGE_COLLECTION_RETRIES,
        message: error instanceof Error ? error.message : String(error),
      });
      if (attempt < PAGE_COLLECTION_RETRIES) {
        await new Promise((resolve) => setTimeout(resolve, 250 * attempt));
      }
    } finally {
      await page.close().catch(() => undefined);
      await context.close().catch(() => undefined);
    }
  }
  throw lastError instanceof Error ? lastError :
    new Error(String(lastError));
}

async function collectNotionPage(
  page: Page,
  url: string,
  pageKey: string,
  request: CrawlRequest
): Promise<{
  title: string;
  finalUrl: string;
  text: string;
  links: string[];
  timings: CrawlTimings;
}> {
  const navigationStartedAt = Date.now();
  console.log("Notion page navigation started", {pageKey, url});
  await page.goto(url, {
    waitUntil: "commit",
    timeout: request.policy.navigationTimeoutMilliseconds,
  });
  console.log("Notion page navigation completed", {
    pageKey,
    durationMs: Date.now() - navigationStartedAt,
  });
  await page.waitForTimeout(request.policy.renderWaitMilliseconds);
  const navigationMs = Date.now() - navigationStartedAt;
  const initialLinkDiscoveryStartedAt = Date.now();
  const initialChildLinks = await extractChildPageLinks(page, pageKey, false);
  const initialLinkDiscoveryMs = Date.now() - initialLinkDiscoveryStartedAt;
  const isDatabaseView = new URL(url).searchParams.has("v");
  console.log("Notion child-page links discovered", {
    pageKey,
    phase: "initial",
    count: initialChildLinks.length,
  });
  const expansionStartedAt = Date.now();
    console.log("Notion page expansion started", {pageKey});
  if (isDatabaseView) {
      console.log("Notion page expansion skipped", {
        pageKey,
        reason: "database-view",
      });
  } else {
    await expandPageContent(page, request);
  }
  const expansionMs = Date.now() - expansionStartedAt;
    console.log("Notion page expansion completed", {
      pageKey,
      durationMs: expansionMs,
  });
  const scrollingStartedAt = Date.now();
    console.log("Notion page scrolling started", {pageKey});
  const scrollResult = await scrollAndCollect(
      page,
      pageKey,
      request,
      isDatabaseView
  );
  const scrollingMs = Date.now() - scrollingStartedAt;
    console.log("Notion page scrolling completed", {
      pageKey,
      durationMs: scrollingMs,
  });
  const finalLinkDiscoveryStartedAt = Date.now();
  const renderedChildLinks = await extractChildPageLinks(
      page, pageKey, true, isDatabaseView, false
  );
  const childLinks = uniqueNotionLinks(
      [...initialChildLinks, ...scrollResult.links, ...renderedChildLinks],
      pageKey
  );
  const linkDiscoveryMs = initialLinkDiscoveryMs +
      (Date.now() - finalLinkDiscoveryStartedAt);
  console.log("Notion child-page links discovered", {
      pageKey,
      phase: "after-scroll",
      count: renderedChildLinks.length,
      total: childLinks.length,
      links: childLinks,
  });
  const finalUrl = normalizeNotionUrl(page.url());
  if (!finalUrl) throw new Error("Notion navigation left the allowed domains");
  const text = mergeTextSnapshots(
      scrollResult.snapshots.map((snapshot) => snapshot.text)
  );
  if (!text.trim()) throw new Error("Notion page rendered empty content");
  return {
      title: await page.title(),
      finalUrl,
      text,
      links: childLinks,
      timings: {
        navigationMs,
        expansionMs,
        scrollingMs,
        linkDiscoveryMs,
      },
  };
}

type RawChildLink = {
  url: string;
  text: string;
  excludedRegion: boolean;
  childPageSignal: boolean;
  pageIdSignal: boolean;
  beforeFirstHeading: boolean;
};

/**
 * Finds only links that look like Notion child-page blocks in the initial DOM.
 * Navigation and breadcrumb links are intentionally excluded before scrolling.
 * @param {Page} page Rendered Notion page
 * @param {string} currentPageKey Current page ID key
 * @return {Promise<string[]>} Safe child-page URLs
 */
async function extractChildPageLinks(
  page: Page,
  currentPageKey: string,
  allowBeforeFirstHeading = false,
  broadPageLinks = false,
  logDiagnostics = true
): Promise<string[]> {
  const links = await page.locator("a[href]").evaluateAll((anchors) => {
    const firstHeading = document.querySelector("h1, h2, h3");
    return anchors.map((anchor) => {
      const element = anchor as HTMLAnchorElement;
      let node: HTMLElement | null = element;
      let excludedRegion = false;
      let childPageSignal = false;
      while (node) {
        const tag = node.tagName.toLowerCase();
        const role = node.getAttribute("role")?.toLowerCase() ?? "";
        const aria = node.getAttribute("aria-label")?.toLowerCase() ?? "";
        const className = typeof node.className === "string" ?
          node.className.toLowerCase() : "";
        const blockType = node.getAttribute("data-block-type")?.toLowerCase() ?? "";
        excludedRegion = excludedRegion || tag === "nav" || tag === "header" ||
          tag === "footer" || role === "navigation" || role === "banner" ||
          role === "contentinfo" || aria.includes("breadcrumb");
        childPageSignal = childPageSignal || blockType.includes("child_page") ||
          blockType.includes("collection_view_page") ||
          className.includes("child-page") || className.includes("page-link");
        if (tag === "main" || tag === "article" || role === "main") break;
        node = node.parentElement;
      }
      const pageIdSignal =
        /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i
          .test(element.href) || /[0-9a-f]{32}/i.test(element.href);
      const beforeFirstHeading = firstHeading !== null &&
        Boolean(firstHeading.compareDocumentPosition(element) &
          Node.DOCUMENT_POSITION_PRECEDING);
      return {
        url: element.href,
        text: element.innerText.trim(),
        excludedRegion,
        childPageSignal,
        pageIdSignal,
        beforeFirstHeading,
      } satisfies RawChildLink;
    });
  });
  const candidates = links.filter((link) => link.text &&
    (broadPageLinks ? true :
      (link.childPageSignal || link.pageIdSignal)) &&
    !link.excludedRegion &&
    (allowBeforeFirstHeading || !link.beforeFirstHeading));
  if (logDiagnostics) {
    console.log("Notion child-page discovery diagnostics", {
      currentPageKey,
      totalAnchors: links.length,
      pageIdAnchors: links.filter((link) => link.pageIdSignal).length,
      acceptedAnchors: candidates.length,
    });
  }
  return uniqueNotionLinks(
    candidates.map((link) => link.url),
    currentPageKey
  );
}

async function expandPageContent(
  page: Page,
  request: CrawlRequest
): Promise<void> {
  for (let round = 0; round < request.policy.expansionRounds; round += 1) {
    let clicked = 0;
    const closedElements = page.locator("[aria-expanded=\"false\"]");
    const closedCount = Math.min(
      await closedElements.count(),
      request.policy.maximumExpandableElements
    );
    for (let index = 0; index < closedCount; index += 1) {
      const element = closedElements.nth(index);
      if (await element.isVisible().catch(() => false)) {
        const clickedElement = await element.click({
          timeout: request.policy.expansionClickTimeoutMilliseconds,
        }).then(() => true).catch(() => false);
        if (clickedElement) clicked += 1;
      }
    }
    const replyButtons = page.locator("button, [role=\"button\"]").filter({
      hasText: /^(Show \d+ replies|Show more replies|답글 \d+개 더 보기|답글 더 보기|더 보기)$/i,
    });
    const replyCount = Math.min(
      await replyButtons.count(),
      request.policy.maximumExpandableElements
    );
    for (let index = 0; index < replyCount; index += 1) {
      const button = replyButtons.nth(index);
      if (await button.isVisible().catch(() => false)) {
        const clickedButton = await button.click({
          timeout: request.policy.expansionClickTimeoutMilliseconds,
        }).then(() => true).catch(() => false);
        if (clickedButton) clicked += 1;
      }
    }
    if (!clicked) break;
    await page.waitForTimeout(request.policy.expansionWaitMilliseconds);
  }
}

async function scrollAndCollect(
  page: Page,
  pageKey: string,
  request: CrawlRequest,
  broadPageLinks = false
): Promise<{snapshots: PageSnapshot[]; links: string[]}> {
  const snapshots: PageSnapshot[] = [];
  const discoveredLinks: string[] = [];
  let previousHeight = 0;
  let stableCount = 0;
  await page.evaluate(() => window.scrollTo(0, 0));
  for (let step = 0; step < request.policy.maximumScrollSteps; step += 1) {
    if (step === 0 || step % 5 === 0) {
      console.log("Notion scroll step started", {
        projectId: request.projectId,
        step,
      });
    }
    const snapshot = await page.evaluate(() => ({
      text: document.body.innerText,
      height: document.documentElement.scrollHeight,
      y: window.scrollY,
      viewportHeight: window.innerHeight,
    }));
    snapshots.push(snapshot);
    discoveredLinks.push(
      ...(await extractChildPageLinks(
        page, pageKey, true, broadPageLinks, false
      ))
    );
    discoveredLinks.push(
      ...(await extractDatabaseRowLinks(page, pageKey, false))
    );
    if (broadPageLinks) {
      await page.evaluate(() => {
        const elements = Array.from(document.querySelectorAll<HTMLElement>(
          "*"
        ));
        const containers = elements.filter((element) => {
          const style = getComputedStyle(element);
          const scrollable = style.overflowY === "auto" ||
            style.overflowY === "scroll";
          return scrollable && element.scrollHeight > element.clientHeight + 20;
        });
        for (const container of containers) {
          container.scrollTop += Math.max(container.clientHeight * 0.8, 500);
        }
      });
    }
    const reachedBottom = snapshot.y + snapshot.viewportHeight >=
      snapshot.height - 10;
    stableCount = reachedBottom && snapshot.height === previousHeight ?
      stableCount + 1 : 0;
    if (!broadPageLinks && stableCount >= request.policy.stableScrollIterations) {
      break;
    }
    previousHeight = snapshot.height;
    await page.evaluate(() => {
      window.scrollBy(0, Math.max(window.innerHeight * 0.8, 600));
    });
    await page.waitForTimeout(request.policy.scrollWaitMilliseconds);
    if (step === 0 || step % 5 === 0) {
      console.log("Notion scroll step completed", {
        projectId: request.projectId,
        step,
        height: snapshot.height,
        y: snapshot.y,
      });
    }
  }
  return {
    snapshots,
    links: uniqueNotionLinks(discoveredLinks, pageKey),
  };
}

/** Extracts page IDs exposed by virtualized database rows. */
async function extractDatabaseRowLinks(
  page: Page,
  currentPageKey: string,
  logDiagnostics = true
): Promise<string[]> {
  const blockIds = await page.evaluate(() => {
    const selectors = [
      ".notion-collection_view_page-block",
      ".notion-table-view-row",
      ".notion-list-view-row",
      ".notion-page-block.notion-collection-item",
      ".notion-page-block",
    ];
    const firstHeading = document.querySelector("h1, h2, h3");
    return selectors.flatMap((selector) =>
      Array.from(document.querySelectorAll<HTMLElement>(selector)).map((row) => {
        const className = typeof row.className === "string" ? row.className : "";
        if (selector === ".notion-page-block" &&
            (className.includes("notion-collection-item") ||
             className.includes("notion-collection_view-block"))) return "";
        if (selector === ".notion-page-block") {
          let ancestor: HTMLElement | null = row.parentElement;
          while (ancestor) {
            const tag = ancestor.tagName.toLowerCase();
            const role = ancestor.getAttribute("role")?.toLowerCase() ?? "";
            if (tag === "nav" || tag === "header" || tag === "footer" ||
                role === "navigation" || role === "banner" ||
                role === "contentinfo") return "";
            ancestor = ancestor.parentElement;
          }
        } else if (firstHeading && Boolean(
          firstHeading.compareDocumentPosition(row) &
          Node.DOCUMENT_POSITION_PRECEDING
        )) return "";
        return row.getAttribute("data-block-id") ??
          row.closest<HTMLElement>("[data-block-id]")?.getAttribute(
            "data-block-id"
          ) ?? "";
      })
    ).filter((blockId) =>
      /^[0-9a-f]{32}$/i.test(blockId) ||
      /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(blockId)
    );
  });
  const links = uniqueNotionLinks(
    blockIds.map((blockId) => `https://app.notion.com/p/${blockId}`),
    currentPageKey
  );
  if (logDiagnostics) {
    console.log("Notion database row pages discovered", {
      currentPageKey,
      count: links.length,
    });
  }
  return links;
}

export function mergeTextSnapshots(textSnapshots: string[]): string {
  const seenBlocks = new Set<string>();
  const mergedBlocks: string[] = [];
  for (const text of textSnapshots) {
    for (const block of text.split(/\n{2,}/)) {
      const normalized = block.trim();
      if (normalized && !seenBlocks.has(normalized)) {
        seenBlocks.add(normalized);
        mergedBlocks.push(normalized);
      }
    }
  }
  return mergedBlocks.join("\n\n");
}

async function installNetworkGuard(context: BrowserContext): Promise<void> {
  await context.route("**/*", async (route) => {
    if (await isSafeBrowserRequest(route.request().url())) {
      await route.continue();
    } else {
      await route.abort("blockedbyclient");
    }
  });
}

function assertRuntime(startedAt: number, request: CrawlRequest): void {
  if (Date.now() - startedAt >
      request.policy.maximumRuntimeMilliseconds) {
    throw new Error("Notion crawl exceeded the configured runtime limit");
  }
}
