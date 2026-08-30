import {createServer, IncomingMessage, ServerResponse} from "node:http";

import {
  parseCrawlRequest,
  RequestValidationError,
} from "./contract.js";
import {crawlNotionSource} from "./crawler.js";

const MAXIMUM_REQUEST_BYTES = 64 * 1024;
const port = Number.parseInt(process.env.PORT ?? "8080", 10);

const server = createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/healthz") {
    sendJson(response, 200, {status: "ok"});
    return;
  }
  if (request.method !== "POST" || request.url !== "/crawl") {
    sendJson(response, 404, {error: "not_found"});
    return;
  }
  console.log("Notion crawl request received");
  try {
    const body = await readJson(request);
    const crawlRequest = parseCrawlRequest(body);
    console.log("Notion crawl started", {
      projectId: crawlRequest.projectId,
      url: crawlRequest.url,
    });
    const result = await crawlNotionSource(crawlRequest);
    console.log("Notion crawl completed", {
      projectId: crawlRequest.projectId,
      itemCount: result.itemCount,
      totalBytes: result.totalBytes,
    });
    sendJson(response, 200, result);
  } catch (error) {
    if (error instanceof RequestValidationError) {
      sendJson(response, 400, {
        error: "invalid_request",
        message: error.message,
      });
      return;
    }
    console.error("Notion crawl failed", error);
    sendJson(response, 500, {error: "notion_crawl_failed"});
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Notion crawler listening on port ${port}`);
});

async function readJson(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let totalBytes = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    totalBytes += buffer.byteLength;
    if (totalBytes > MAXIMUM_REQUEST_BYTES) {
      throw new RequestValidationError("Request body is too large");
    }
    chunks.push(buffer);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new RequestValidationError("Request body must be valid JSON");
  }
}

function sendJson(
  response: ServerResponse,
  statusCode: number,
  body: unknown
): void {
  response.writeHead(statusCode, {"content-type": "application/json"});
  response.end(JSON.stringify(body));
}
