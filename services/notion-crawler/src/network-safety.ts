import {lookup} from "node:dns/promises";
import {isIP} from "node:net";

const hostSafetyCache = new Map<string, Promise<boolean>>();

/** Prevents rendered public pages from requesting local or private networks. */
export async function isSafeBrowserRequest(rawUrl: string): Promise<boolean> {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return false;
  }
  if (url.protocol === "data:" || url.protocol === "blob:") return true;
  if (url.protocol !== "https:" && url.protocol !== "http:") return false;
  if (url.username || url.password) return false;
  const hostname = url.hostname.toLowerCase();
  if (hostname === "localhost" || hostname.endsWith(".localhost") ||
      hostname.endsWith(".local") || hostname.endsWith(".internal")) {
    return false;
  }
  let safety = hostSafetyCache.get(hostname);
  if (!safety) {
    safety = resolvePublicHostname(hostname);
    hostSafetyCache.set(hostname, safety);
  }
  return safety;
}

async function resolvePublicHostname(hostname: string): Promise<boolean> {
  if (isIP(hostname)) return !isPrivateAddress(hostname);
  try {
    const addresses = await lookup(hostname, {all: true, verbatim: true});
    return addresses.length > 0 && addresses.every(
      ({address}) => !isPrivateAddress(address)
    );
  } catch {
    return false;
  }
}

export function isPrivateAddress(address: string): boolean {
  const normalized = address.toLowerCase();
  if (normalized === "::" || normalized === "::1" ||
      normalized.startsWith("fc") || normalized.startsWith("fd") ||
      normalized.startsWith("fe8") || normalized.startsWith("fe9") ||
      normalized.startsWith("fea") || normalized.startsWith("feb")) {
    return true;
  }
  if (normalized.startsWith("::ffff:")) {
    return isPrivateAddress(normalized.slice("::ffff:".length));
  }
  if (isIP(normalized) !== 4) return false;
  const [first, second] = normalized.split(".").map(Number);
  return first === 0 || first === 10 || first === 127 ||
    (first === 100 && second >= 64 && second <= 127) ||
    (first === 169 && second === 254) ||
    (first === 172 && second >= 16 && second <= 31) ||
    (first === 192 && second === 168) || first >= 224;
}
