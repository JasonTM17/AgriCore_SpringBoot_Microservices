/**
 * @vitest-environment node
 *
 * Structural contract (Node only — not part of the browser app graph).
 * Console image must be non-root-compatible with Helm runAsNonRoot + console.runAsUser 101
 * and must listen on 8080 (not privileged 80).
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const consoleRoot = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(consoleRoot, "../..");

function read(relFromRepo: string): string {
  return readFileSync(resolve(repoRoot, relFromRepo), "utf8");
}

describe("console container non-root contract", () => {
  it("uses nginx-unprivileged base listening on 8080 as uid 101", () => {
    const dockerfile = read("apps/agricore-console/Dockerfile");
    expect(dockerfile).toMatch(/FROM\s+nginxinc\/nginx-unprivileged:1\.27-alpine/);
    expect(dockerfile).toMatch(/USER\s+101/);
    expect(dockerfile).toMatch(/EXPOSE\s+8080/);
    expect(dockerfile).toMatch(/127\.0\.0\.1:8080/);
    expect(dockerfile).not.toMatch(/EXPOSE\s+80\b/);
  });

  it("nginx template listens on 8080 and parameterizes gateway upstream", () => {
    const nginx = read("apps/agricore-console/nginx.conf.template");
    expect(nginx).toMatch(/listen\s+8080\s*;/);
    expect(nginx).not.toMatch(/listen\s+80\s*;/);
    expect(nginx).toContain("${GATEWAY_UPSTREAM}");
  });

  it("Helm values and template force console non-root uid 101 and port 8080", () => {
    const values = read("infrastructure/helm/agricore/values.yaml");
    expect(values).toMatch(/console:[\s\S]*?port:\s*8080/);
    expect(values).toMatch(/console:[\s\S]*?runAsUser:\s*101/);

    const deployment = read("infrastructure/helm/agricore/templates/deployment.yaml");
    expect(deployment).toContain('eq $name "console"');
    expect(deployment).toMatch(/runAsUser:\s*\{\{\s*\$svc\.runAsUser\s*\|\s*default\s*101/);
  });

  it("compose maps host 5173 to container 8080", () => {
    const compose = read("docker-compose.yml");
    expect(compose).toMatch(/5173:8080/);
    expect(compose).toMatch(/127\.0\.0\.1:8080/);
  });
});
