import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const mediaDirectory = join(repositoryRoot, "assets", "images", "agricore-console");
const manifestPath = join(mediaDirectory, "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const expectedFiles = new Set(manifest.assets.map((asset) => asset.file));
const actualFiles = readdirSync(mediaDirectory).filter((file) => /\.(gif|png)$/i.test(file));

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function dimensions(asset, bytes) {
  if (asset.mimeType === "image/png") {
    assert(
      bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])),
      `${asset.file}: invalid PNG signature`,
    );
    return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
  }
  if (asset.mimeType === "image/gif") {
    assert(/^GIF8[79]a$/.test(bytes.subarray(0, 6).toString("ascii")), `${asset.file}: invalid GIF signature`);
    return { width: bytes.readUInt16LE(6), height: bytes.readUInt16LE(8) };
  }
  throw new Error(`${asset.file}: unsupported MIME type ${asset.mimeType}`);
}

function verifyAnimation(asset, bytes) {
  const delays = [];
  for (let index = 0; index + 7 < bytes.length; index += 1) {
    if (bytes[index] === 0x21 && bytes[index + 1] === 0xf9 && bytes[index + 2] === 0x04) {
      delays.push(bytes.readUInt16LE(index + 4));
    }
  }

  assert(delays.length === asset.frames, `${asset.file}: expected ${asset.frames} frames, got ${delays.length}`);
  assert(
    JSON.stringify(delays) === JSON.stringify(asset.frameDelaysCentiseconds),
    `${asset.file}: frame delays do not match manifest`,
  );
  const durationSeconds = delays.reduce((total, delay) => total + delay, 0) / 100;
  assert(
    Math.abs(durationSeconds - asset.durationSeconds) < 0.001,
    `${asset.file}: expected ${asset.durationSeconds}s duration, got ${durationSeconds}s`,
  );
  const loopMarker = bytes.indexOf(Buffer.from("NETSCAPE2.0"));
  assert(loopMarker >= 0, `${asset.file}: missing GIF loop extension`);
  assert(
    bytes.readUInt16LE(loopMarker + 13) === asset.loopCount,
    `${asset.file}: loop count does not match manifest`,
  );
  assert(asset.frames <= 6 && durationSeconds <= 10, `${asset.file}: animation exceeds frame or duration budget`);
  assert(bytes.length <= 512 * 1024, `${asset.file}: GIF exceeds 512 KiB budget`);
}

const trackedFiles = new Set(
  execFileSync("git", ["ls-files", "assets/images/agricore-console"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  })
    .split(/\r?\n/)
    .filter(Boolean)
    .map((file) => file.replaceAll("\\", "/")),
);

assert(actualFiles.length === expectedFiles.size, "Console media file count does not match manifest");
for (const file of actualFiles) {
  assert(expectedFiles.has(file), `${file}: media file is missing from manifest`);
}

let totalBytes = 0;
for (const asset of manifest.assets) {
  const assetPath = join(mediaDirectory, asset.file);
  const bytes = readFileSync(assetPath);
  const repositoryPath = relative(repositoryRoot, assetPath).replaceAll("\\", "/");
  const digest = createHash("sha256").update(bytes).digest("hex").toUpperCase();
  const size = dimensions(asset, bytes);

  assert(trackedFiles.has(repositoryPath), `${asset.file}: asset is not tracked by Git`);
  assert(bytes.length === asset.bytes, `${asset.file}: expected ${asset.bytes} bytes, got ${bytes.length}`);
  assert(digest === asset.sha256, `${asset.file}: SHA-256 mismatch`);
  assert(
    size.width === asset.width && size.height === asset.height,
    `${asset.file}: expected ${asset.width}x${asset.height}, got ${size.width}x${size.height}`,
  );
  if (asset.mimeType === "image/gif") verifyAnimation(asset, bytes);
  totalBytes += bytes.length;
  console.log(`verified ${asset.file} (${bytes.length} bytes)`);
}

assert(totalBytes <= 2 * 1024 * 1024, `Console showcase exceeds 2 MiB budget: ${totalBytes} bytes`);
console.log(`console showcase verified: ${manifest.assets.length} assets, ${totalBytes} bytes`);
