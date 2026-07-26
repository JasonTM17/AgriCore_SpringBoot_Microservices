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

function verifyDimensions(asset, bytes) {
  const dimensions =
    asset.mimeType === "image/gif"
      ? {
          width: bytes.readUInt16LE(6),
          height: bytes.readUInt16LE(8),
        }
      : readWebpDimensions(asset, bytes);
  assert(
    dimensions.width === asset.width && dimensions.height === asset.height,
    `${asset.file}: expected ${asset.width}x${asset.height}, got ${dimensions.width}x${dimensions.height}`,
  );
}

function readWebpDimensions(asset, bytes) {
  let offset = 12;
  while (offset + 8 <= bytes.length) {
    const chunkType = bytes.subarray(offset, offset + 4).toString("ascii");
    const chunkSize = bytes.readUInt32LE(offset + 4);
    const payloadOffset = offset + 8;
    assert(payloadOffset + chunkSize <= bytes.length, `${asset.file}: truncated WebP chunk`);

    if (chunkType === "VP8X") {
      return {
        width: 1 + readUInt24LE(bytes, payloadOffset + 4),
        height: 1 + readUInt24LE(bytes, payloadOffset + 7),
      };
    }
    if (chunkType === "VP8L") {
      assert(bytes[payloadOffset] === 0x2f, `${asset.file}: invalid VP8L signature`);
      const bits = bytes.readUInt32LE(payloadOffset + 1);
      return {
        width: 1 + (bits & 0x3fff),
        height: 1 + ((bits >>> 14) & 0x3fff),
      };
    }
    if (chunkType === "VP8 ") {
      assert(
        bytes.subarray(payloadOffset + 3, payloadOffset + 6).equals(Buffer.from([0x9d, 0x01, 0x2a])),
        `${asset.file}: invalid VP8 frame header`,
      );
      return {
        width: bytes.readUInt16LE(payloadOffset + 6) & 0x3fff,
        height: bytes.readUInt16LE(payloadOffset + 8) & 0x3fff,
      };
    }
    offset = payloadOffset + chunkSize + (chunkSize % 2);
  }
  throw new Error(`${asset.file}: WebP dimensions not found`);
}

function readUInt24LE(bytes, offset) {
  return bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16);
}

function verifyGifAnimation(asset, bytes) {
  const delays = [];
  for (let index = 0; index + 7 < bytes.length; index += 1) {
    if (
      bytes[index] === 0x21 &&
      bytes[index + 1] === 0xf9 &&
      bytes[index + 2] === 0x04
    ) {
      delays.push(bytes.readUInt16LE(index + 4));
    }
  }

  assert(delays.length === asset.frames, `${asset.file}: expected ${asset.frames} frames, got ${delays.length}`);
  assert(
    delays.every((delay) => delay === asset.frameDelayCentiseconds && delay > 0),
    `${asset.file}: frame delays do not match ${asset.frameDelayCentiseconds} centiseconds`,
  );

  const durationSeconds = delays.reduce((total, delay) => total + delay, 0) / 100;
  const framesPerSecond = asset.frames / durationSeconds;
  assert(
    Math.abs(durationSeconds - asset.durationSeconds) < 0.001,
    `${asset.file}: expected ${asset.durationSeconds}s duration, got ${durationSeconds}s`,
  );
  assert(
    Math.abs(framesPerSecond - asset.framesPerSecond) < 0.001,
    `${asset.file}: expected ${asset.framesPerSecond} fps, got ${framesPerSecond} fps`,
  );
  assert(asset.width <= 1280 && asset.height <= 720, `${asset.file}: GIF dimensions exceed budget`);
  assert(asset.frames <= 12 && durationSeconds <= 15, `${asset.file}: GIF animation exceeds budget`);
  assert(bytes.length <= 1024 * 1024, `${asset.file}: GIF exceeds 1 MiB budget`);

  const loopMarker = bytes.indexOf(Buffer.from("NETSCAPE2.0"));
  assert(loopMarker >= 0, `${asset.file}: missing GIF loop extension`);
  const loopCount = bytes.readUInt16LE(loopMarker + 13);
  assert(loopCount === asset.loopCount, `${asset.file}: expected loop count ${asset.loopCount}, got ${loopCount}`);
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
  verifyDimensions(asset, bytes);
  if (asset.mimeType === "image/gif") {
    verifyGifAnimation(asset, bytes);
  }
  totalBytes += bytes.length;
  console.log(`verified ${asset.file} (${bytes.length} bytes)`);
}

assert(totalBytes <= 2 * 1024 * 1024, `Showcase media exceeds 2 MiB budget: ${totalBytes} bytes`);
console.log(`showcase media verified: ${manifest.assets.length} assets, ${totalBytes} bytes`);
