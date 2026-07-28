import assert from "node:assert/strict";
import test from "node:test";

import { verifyConsoleCaptureProvenance } from "./console-showcase-provenance.mjs";

const revision = "a".repeat(40);
const tree = "b".repeat(40);
const runtimeInputPaths = [
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
const runtimeInputObjects = Object.fromEntries(
  runtimeInputPaths.map((path, index) => [path, index.toString(16).repeat(40)]),
);
const manifest = {
  capturedAt: "2026-07-27",
  source: "Built React Operations Console with deterministic Playwright edge",
  captureRevision: revision,
  sourceTree: {
    path: "apps/agricore-console",
    gitTree: tree,
  },
  runtimeInputs: runtimeInputPaths.map((path) => ({ path, gitObject: runtimeInputObjects[path] })),
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
    resolveObject: (reference) => runtimeInputObjects[reference.slice(reference.indexOf(":") + 1)],
    isAncestor: () => true,
  };
}

function gitWithReleaseVersionChange(currentPackageMetadata) {
  return {
    ...matchingGit(),
    resolveTree: (reference) => (reference.startsWith(revision) ? tree : "c".repeat(40)),
    changedPaths: () => ["apps/agricore-console/package.json"],
    readFile: (reference) => JSON.stringify(
      reference.startsWith(revision)
        ? { name: "@agricore/console", private: true, version: "0.1.0" }
        : currentPackageMetadata,
    ),
  };
}

test("accepts a capture bound to an ancestor and unchanged source tree", () => {
  assert.doesNotThrow(() => verifyConsoleCaptureProvenance(manifest, matchingGit()));
});

test("accepts a release-only console package version change", () => {
  const git = gitWithReleaseVersionChange({
    name: "@agricore/console",
    private: true,
    version: "1.0.0",
  });

  assert.doesNotThrow(() => verifyConsoleCaptureProvenance(manifest, git));
});

test("rejects a console package change beyond the release version", () => {
  const git = gitWithReleaseVersionChange({
    name: "@agricore/console",
    private: false,
    version: "1.0.0",
  });

  assert.throws(
    () => verifyConsoleCaptureProvenance(manifest, git),
    /Console source tree changed after capture/,
  );
});

test("rejects a changed console tree without a package version change", () => {
  const git = gitWithReleaseVersionChange({
    name: "@agricore/console",
    private: true,
    version: "0.1.0",
  });

  assert.throws(
    () => verifyConsoleCaptureProvenance(manifest, git),
    /Console source tree changed after capture/,
  );
});

test("rejects any changed console path other than package metadata", () => {
  const git = gitWithReleaseVersionChange({
    name: "@agricore/console",
    private: true,
    version: "1.0.0",
  });
  git.changedPaths = () => ["apps/agricore-console/src/app.tsx"];

  assert.throws(
    () => verifyConsoleCaptureProvenance(manifest, git),
    /Console source tree changed after capture/,
  );
});

test("rejects a changed workspace dependency input", () => {
  const git = matchingGit();
  git.resolveObject = (reference) => (
    reference === "HEAD:pnpm-lock.yaml"
      ? "f".repeat(40)
      : runtimeInputObjects[reference.slice(reference.indexOf(":") + 1)]
  );

  assert.throws(
    () => verifyConsoleCaptureProvenance(manifest, git),
    /Console runtime input changed after capture: pnpm-lock\.yaml/,
  );
});

test("rejects a manifest that omits a required runtime input", () => {
  const incompleteManifest = {
    ...manifest,
    runtimeInputs: manifest.runtimeInputs.slice(1),
  };

  assert.throws(
    () => verifyConsoleCaptureProvenance(incompleteManifest, matchingGit()),
    /Console runtime inputs must include every required path exactly once/,
  );
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
