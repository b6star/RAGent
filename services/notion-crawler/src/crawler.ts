import {BrowserContext, chromium, Page} from "playwright";

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
  links: string[];
  height: number;
  y: number;
  viewportHeight: number;
};

export async function crawlNotionSource(
  request: CrawlRequest
): Promise<NotionCollectionResult> {
  const canonicalUrl = normalizeNotionUrl(request.url);
  if (!canonicalUrl || canonicalUrl !== request.url) {
    throw new Error("A canonical public Notion URL is required");
  }
  const browser = await chromium.launch({
    headless: true,
    args: ["--disable-dev-shm-usage", "--no-sandbox"],
  });
  const context = await browser.newContext({
    viewport: {
      width: request.policy.viewportWidth,
      height: request.policy.viewportHeight,
    },
  });
  await installNetworkGuard(context);
  try {
    const items = await crawlPages(context, request);
    return await persistSnapshot(request, items);
  } finally {
    await browser.close();
  }
}

async function crawlPages(
  context: BrowserContext,
  request: CrawlRequest
): Promise<NotionSnapshotItem[]> {
  const startedAt = Date.now();
  const visited = new Set<string>();
  const queued = new Set<string>();
  const queue: QueuedPage[] = [{url: request.url, depth: 0}];
  const items: NotionSnapshotItem[] = [];
  let totalBytes = 0;
  queued.add(notionPageKey(request.url));

  while (queue.length && visited.size < request.policy.maximumPages) {
    assertRuntime(startedAt, request);
    const current = queue.shift();
    if (!current) break;
    const pageKey = notionPageKey(current.url);
    if (visited.has(pageKey)) continue;
    visited.add(pageKey);
    const page = await context.newPage();
    try {
      const collected = await collectNotionPage(
        page,
        current.url,
        pageKey,
        request
      );
      const byteSize = Buffer.byteLength(collected.text, "utf8");
      if (byteSize > request.policy.maximumPageBytes) {
        throw new Error("A Notion page exceeded the configured byte limit");
      }
      totalBytes += byteSize;
      if (totalBytes > request.policy.maximumTotalBytes) {
        throw new Error("Notion content exceeded the configured total limit");
      }
      items.push({
        key: pageKey,
        url: collected.finalUrl,
        title: collected.title,
        content: collected.text,
        contentHash: hashContent(collected.text),
        byteSize,
      });
      console.log("Notion page collected", {
        projectId: request.projectId,
        pageKey,
        itemCount: items.length,
        byteSize,
      });
      if (current.depth < request.policy.maximumCrawlDepth) {
        for (const link of collected.links) {
          const childKey = notionPageKey(link);
          if (!visited.has(childKey) && !queued.has(childKey)) {
            queued.add(childKey);
            queue.push({url: link, depth: current.depth + 1});
          }
        }
      }
    } finally {
      await page.close();
    }
  }
  return items;
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
}> {
  console.log("Notion page navigation started", {pageKey, url});
  await page.goto(url, {
    waitUntil: "commit",
    timeout: request.policy.navigationTimeoutMilliseconds,
  });
  console.log("Notion page navigation completed", {pageKey});
  await page.waitForTimeout(request.policy.renderWaitMilliseconds);
  console.log("Notion page expansion started", {pageKey});
  await expandPageContent(page, request);
  console.log("Notion page expansion completed", {pageKey});
  console.log("Notion page scrolling started", {pageKey});
  const snapshots = await scrollAndCollect(page, request);
  await expandPageContent(page, request);
  snapshots.push(...await scrollAndCollect(page, request));
  console.log("Notion page scrolling completed", {pageKey});
  const finalUrl = normalizeNotionUrl(page.url());
  if (!finalUrl) throw new Error("Notion navigation left the allowed domains");
  return {
    title: await page.title(),
    finalUrl,
    text: mergeTextSnapshots(snapshots.map((snapshot) => snapshot.text)),
    links: uniqueNotionLinks(
      snapshots.flatMap((snapshot) => snapshot.links),
      pageKey
    ),
  };
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
        await element.click({
          timeout: request.policy.expansionClickTimeoutMilliseconds,
        }).catch(() => undefined);
        clicked += 1;
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
        await button.click({
          timeout: request.policy.expansionClickTimeoutMilliseconds,
        }).catch(() => undefined);
        clicked += 1;
      }
    }
    if (!clicked) break;
    await page.waitForTimeout(request.policy.expansionWaitMilliseconds);
  }
}

async function scrollAndCollect(
  page: Page,
  request: CrawlRequest
): Promise<PageSnapshot[]> {
  const snapshots: PageSnapshot[] = [];
  let previousHeight = 0;
  let stableCount = 0;
  await page.evaluate(() => window.scrollTo(0, 0));
  for (let step = 0; step < request.policy.maximumScrollSteps; step += 1) {
    const snapshot = await page.evaluate(() => ({
      text: document.body.innerText,
      links: Array.from(document.querySelectorAll<HTMLAnchorElement>(
        "a[href]"
      )).map((anchor) => anchor.href).filter(Boolean),
      height: document.documentElement.scrollHeight,
      y: window.scrollY,
      viewportHeight: window.innerHeight,
    }));
    snapshots.push(snapshot);
    const reachedBottom = snapshot.y + snapshot.viewportHeight >=
      snapshot.height - 10;
    stableCount = reachedBottom && snapshot.height === previousHeight ?
      stableCount + 1 : 0;
    if (stableCount >= request.policy.stableScrollIterations) break;
    previousHeight = snapshot.height;
    await page.evaluate(() => {
      window.scrollBy(0, Math.max(window.innerHeight * 0.8, 600));
    });
    await page.waitForTimeout(request.policy.scrollWaitMilliseconds);
  }
  return snapshots;
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
