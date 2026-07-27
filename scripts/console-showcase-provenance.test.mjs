import assert from "node:assert/strict";
import test from "node:test";

import { verifyConsoleCaptureProvenance } from "./console-showcase-provenance.mjs";

const revision = "a".repeat(40);
const tree = "b".repeat(40);
const manifest = {
  capturedAt: "2026-07-27",
  source: "Built React Operations Console with deterministic Playwright edge",
  captureRevision: revision,
  sourceTree: {
    path: "apps/agricore-console",
    gitTree: tree,
  },
  capture: {
    browser: "Chromium",
    origin: "http://127.0.0.1:4174",
    buildCommand: "pnpm build",
    edgeCommand: "node apps/agricore-console/e2e/mock-edge-server.mjs",
  },
  containsProductionData: false,
};

function matchingGit() {
  return {
    resolveTree: () => tree,
    isAncestor: () => true,
  };
}

test("accepts a capture bound to an ancestor and unchanged source tree", () => {
  assert.doesNotThrow(() => verifyConsoleCaptureProvenance(manifest, matchingGit()));
});

test("rejects a missing capture revision", () => {
  const git = matchingGit();
  git.resolveTree = (reference) => {
    if (reference.startsWith(revision)) throw new Error("unknown revision");
    return tree;
  };

  assert.throws(
    () => verifyConsoleCaptureProvenance(manifest, git),
    /revision or source tree cannot be resolved/,
  );
});

test("rejects a capture revision with a different source tree", () => {
  const git = matchingGit();
  git.resolveTree = (reference) => (reference.startsWith(revision) ? "c".repeat(40) : tree);

  assert.throws(
    () => verifyConsoleCaptureProvenance(manifest, git),
    /Capture revision tree mismatch/,
  );
});

test("rejects a capture revision outside the current history", () => {
  const git = matchingGit();
  git.isAncestor = () => false;

  assert.throws(
    () => verifyConsoleCaptureProvenance(manifest, git),
    /must be an ancestor of HEAD/,
  );
});

test("rejects a manifest that may contain production data", () => {
  const unsafeManifest = {
    ...manifest,
    containsProductionData: true,
  };

  assert.throws(
    () => verifyConsoleCaptureProvenance(unsafeManifest, matchingGit()),
    /must explicitly exclude production data/,
  );
});
