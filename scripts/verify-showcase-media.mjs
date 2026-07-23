import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const mediaDirectory = join(repositoryRoot, "assets", "media", "agricore-showcase");
const manifestPath = join(mediaDirectory, "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const expectedFiles = new Set(manifest.assets.map((asset) => asset.file));
const actualFiles = readdirSync(mediaDirectory).filter((file) => /\.(gif|webp)$/i.test(file));

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function verifySignature(asset, bytes) {
  if (asset.mimeType === "image/gif") {
    assert(/^GIF8[79]a$/.test(bytes.subarray(0, 6).toString("ascii")), `${asset.file}: invalid GIF signature`);
    return;
  }
  if (asset.mimeType === "image/webp") {
    assert(bytes.subarray(0, 4).toString("ascii") === "RIFF", `${asset.file}: invalid RIFF signature`);
    assert(bytes.subarray(8, 12).toString("ascii") === "WEBP", `${asset.file}: invalid WebP signature`);
    return;
  }
  throw new Error(`${asset.file}: unsupported manifest MIME type ${asset.mimeType}`);
}

const trackedFiles = new Set(
  execFileSync("git", ["ls-files", "assets/media/agricore-showcase"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  })
    .split(/\r?\n/)
    .filter(Boolean)
    .map((file) => file.replaceAll("\\", "/")),
);

assert(actualFiles.length === expectedFiles.size, "Media file count does not match manifest");
for (const file of actualFiles) {
  assert(expectedFiles.has(file), `${file}: media file is missing from manifest`);
}

let totalBytes = 0;
for (const asset of manifest.assets) {
  const assetPath = join(mediaDirectory, asset.file);
  const bytes = readFileSync(assetPath);
  const repositoryPath = relative(repositoryRoot, assetPath).replaceAll("\\", "/");
  const digest = createHash("sha256").update(bytes).digest("hex").toUpperCase();

  assert(trackedFiles.has(repositoryPath), `${asset.file}: asset is not tracked by Git`);
  assert(bytes.length === asset.bytes, `${asset.file}: expected ${asset.bytes} bytes, got ${bytes.length}`);
  assert(digest === asset.sha256, `${asset.file}: SHA-256 mismatch`);
  verifySignature(asset, bytes);
  totalBytes += bytes.length;
  console.log(`verified ${asset.file} (${bytes.length} bytes)`);
}

assert(totalBytes <= 2 * 1024 * 1024, `Showcase media exceeds 2 MiB budget: ${totalBytes} bytes`);
console.log(`showcase media verified: ${manifest.assets.length} assets, ${totalBytes} bytes`);
