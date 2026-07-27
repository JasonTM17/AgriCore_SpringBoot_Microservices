const REVISION_PATTERN = /^[0-9a-f]{40}$/;
const SOURCE_TREE_PATH = "apps/agricore-console";
const SOURCE_DESCRIPTION = "Built React Operations Console with deterministic Playwright edge";

function assert(condition, message) {
  if (!condition) throw new Error(message);
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

  const currentSourceTree = git.resolveTree(`HEAD:${manifest.sourceTree.path}`);
  assert(
    currentSourceTree === manifest.sourceTree.gitTree,
    `Console source tree changed after capture: expected ${manifest.sourceTree.gitTree}, got ${currentSourceTree}`,
  );
}
