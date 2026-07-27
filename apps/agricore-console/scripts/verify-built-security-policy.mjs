import { readFile } from "node:fs/promises";

const builtIndexUrl = new URL("../dist/index.html", import.meta.url);
const html = await readFile(builtIndexUrl, "utf8");

const requiredFragments = [
  'http-equiv="Content-Security-Policy"',
  "default-src &#39;self&#39;",
  "script-src &#39;self&#39;",
  "style-src &#39;self&#39;",
  "object-src &#39;none&#39;",
  "require-trusted-types-for &#39;script&#39;",
  "trusted-types &#39;none&#39;",
];

for (const fragment of requiredFragments) {
  if (!html.includes(fragment)) {
    throw new Error(`Built index is missing required browser policy: ${fragment}`);
  }
}

if (html.includes("&#39;unsafe-inline&#39;") || html.includes("&#39;unsafe-eval&#39;")) {
  throw new Error("Production browser policy contains an unsafe script or style source");
}

const scriptTags = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/giu)];
if (scriptTags.length === 0) {
  throw new Error("Built index does not contain the application script");
}

for (const [, attributes = "", body = ""] of scriptTags) {
  const source = /\bsrc="([^"]+)"/iu.exec(attributes)?.[1];
  if (!source || (!source.startsWith("/") && !source.startsWith("./")) || source.startsWith("//")) {
    throw new Error("Built index contains an inline or external runtime script");
  }
  if (body.trim().length > 0) {
    throw new Error("Built index contains inline script content");
  }
}

console.log("Verified production CSP and same-origin runtime scripts.");
