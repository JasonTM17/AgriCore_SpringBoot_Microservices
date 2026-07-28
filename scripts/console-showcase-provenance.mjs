const REVISION_PATTERN = /^[0-9a-f]{40}$/;
const SOURCE_TREE_PATH = "apps/agricore-console";
const PACKAGE_METADATA_PATH = `${SOURCE_TREE_PATH}/package.json`;
const SOURCE_DESCRIPTION = "Built React Operations Console with deterministic Playwright edge";
const REQUIRED_RUNTIME_INPUT_PATHS = [
  "pnpm-lock.yaml",
  "pnpm-workspace.yaml",
  "assets/media/agricore-showcase/manifest.json",
  "assets/media/agricore-showcase/agricore-farm-story.gif",
  "assets/media/agricore-showcase/agricore-farm-sunrise-480w.webp",
  "assets/media/agricore-showcase/agricore-farm-sunrise-960w.webp",
  "assets/media/agricore-showcase/agricore-farm-sunrise-thumbnail-240w.webp",
  "assets/media/agricore-showcase/agricore-farm-sunrise.webp",
  "assets/media/agricore-showcase/agricore-harvest-packing-480w.webp",
  "assets/media/agricore-showcase/agricore-harvest-packing-960w.webp",
  "assets/media/agricore-showcase/agricore-harvest-packing-thumbnail-240w.webp",
  "assets/media/agricore-showcase/agricore-harvest-packing.webp",
  "assets/media/agricore-showcase/agricore-traceability-produce-480w.webp",
  "assets/media/agricore-showcase/agricore-traceability-produce-960w.webp",
  "assets/media/agricore-showcase/agricore-traceability-produce-thumbnail-240w.webp",
  "assets/media/agricore-showcase/agricore-traceability-produce.webp",
];

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function canonicalizeJson(value) {
  if (Array.isArray(value)) return value.map(canonicalizeJson);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, nestedValue]) => [key, canonicalizeJson(nestedValue)]),
    );
  }
  return value;
}

function packageMetadataWithoutVersion(serializedMetadata, reference) {
  let packageMetadata;
  try {
    packageMetadata = JSON.parse(serializedMetadata);
  } catch {
    throw new Error(`Console package metadata at ${reference} is not valid JSON`);
  }

  assert(
    packageMetadata && typeof packageMetadata === "object" && !Array.isArray(packageMetadata),
    `Console package metadata at ${reference} must be an object`,
  );
  assert(
    typeof packageMetadata.version === "string" && packageMetadata.version.length > 0,
    `Console package metadata at ${reference} must declare a version`,
  );

  const { version, ...metadataWithoutVersion } = packageMetadata;
  return {
    version,
    metadata: JSON.stringify(canonicalizeJson(metadataWithoutVersion)),
  };
}

function hasVersionOnlyReleaseMetadataChange(git, captureRevision) {
  const changedPaths = git.changedPaths(captureRevision, "HEAD", SOURCE_TREE_PATH);
  if (changedPaths.length !== 1 || changedPaths[0] !== PACKAGE_METADATA_PATH) return false;

  const capturedReference = `${captureRevision}:${PACKAGE_METADATA_PATH}`;
  const currentReference = `HEAD:${PACKAGE_METADATA_PATH}`;
  const capturedMetadata = packageMetadataWithoutVersion(git.readFile(capturedReference), capturedReference);
  const currentMetadata = packageMetadataWithoutVersion(git.readFile(currentReference), currentReference);
  return capturedMetadata.version !== currentMetadata.version
    && capturedMetadata.metadata === currentMetadata.metadata;
}

function verifyRuntimeInputs(manifest, git) {
  assert(Array.isArray(manifest.runtimeInputs), "Console runtime inputs are missing");
  const runtimePaths = new Set();
  for (const input of manifest.runtimeInputs) {
    assert(input && typeof input === "object", "Console runtime input is invalid");
    assert(
      REQUIRED_RUNTIME_INPUT_PATHS.includes(input.path),
      `Console runtime input path is invalid: ${input.path}`,
    );
    assert(!runtimePaths.has(input.path), `Console runtime input is duplicated: ${input.path}`);
    assert(
      REVISION_PATTERN.test(input.gitObject),
      `Console runtime input object is invalid: ${input.path}`,
    );
    runtimePaths.add(input.path);

    const capturedReference = `${manifest.captureRevision}:${input.path}`;
    const capturedObject = git.resolveObject(capturedReference);
    assert(
      capturedObject === input.gitObject,
      `Capture revision runtime input mismatch for ${input.path}: expected ${input.gitObject}, got ${capturedObject}`,
    );

    const currentObject = git.resolveObject(`HEAD:${input.path}`);
    assert(
      currentObject === input.gitObject,
      `Console runtime input changed after capture: ${input.path}`,
    );
  }
  assert(
    runtimePaths.size === REQUIRED_RUNTIME_INPUT_PATHS.length,
    "Console runtime inputs must include every required path exactly once",
  );
}

export function verifyConsoleCaptureProvenance(manifest, git) {
  assert(manifest.source === SOURCE_DESCRIPTION, "Console capture source description is invalid");
  const capturedDate = new Date(`${manifest.capturedAt}T00:00:00Z`);
  assert(
    /^\d{4}-\d{2}-\d{2}$/.test(manifest.capturedAt)
      && !Number.isNaN(capturedDate.valueOf())
      && capturedDate.toISOString().slice(0, 10) === manifest.capturedAt,
    "Console capture date must be a valid ISO calendar date",
  );
  assert(
    manifest.containsProductionData === false,
    "Console captures must explicitly exclude production data",
  );
  assert(manifest.capture?.browser === "Chromium", "Console capture browser is invalid");
  assert(manifest.capture?.origin === "http://127.0.0.1:4174", "Console capture origin is invalid");
  assert(manifest.capture?.buildCommand === "pnpm build", "Console capture build command is invalid");
  assert(
    manifest.capture?.edgeCommand === "node apps/agricore-console/e2e/mock-edge-server.mjs",
    "Console capture edge command is invalid",
  );
  assert(
    REVISION_PATTERN.test(manifest.captureRevision),
    "Console capture revision must be a full Git commit SHA",
  );
  assert(manifest.sourceTree?.path === SOURCE_TREE_PATH, "Console source tree path is invalid");
  assert(
    REVISION_PATTERN.test(manifest.sourceTree?.gitTree),
    "Console source tree must be a full Git tree object id",
  );

  let capturedSourceTree;
  try {
    capturedSourceTree = git.resolveTree(
      `${manifest.captureRevision}:${manifest.sourceTree.path}`,
    );
  } catch {
    throw new Error("Console capture revision or source tree cannot be resolved");
  }
  assert(
    capturedSourceTree === manifest.sourceTree.gitTree,
    `Capture revision tree mismatch: expected ${manifest.sourceTree.gitTree}, got ${capturedSourceTree}`,
  );
  assert(
    git.isAncestor(manifest.captureRevision, "HEAD"),
    "Console capture revision must be an ancestor of HEAD",
  );
  verifyRuntimeInputs(manifest, git);

  const currentSourceTree = git.resolveTree(`HEAD:${manifest.sourceTree.path}`);
  if (currentSourceTree === manifest.sourceTree.gitTree) return;

  assert(
    hasVersionOnlyReleaseMetadataChange(git, manifest.captureRevision),
    `Console source tree changed after capture: expected ${manifest.sourceTree.gitTree}, got ${currentSourceTree}`,
  );
}
