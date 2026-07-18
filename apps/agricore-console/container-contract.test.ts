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
    // SSE / long-lived generation proxy timeouts preserved
    expect(nginx).toMatch(/proxy_read_timeout\s+3600s\s*;/);
    expect(nginx).toMatch(/proxy_buffering\s+off\s*;/);
  });

  it("nginx template ships restrictive security headers (M3)", () => {
    const nginx = read("apps/agricore-console/nginx.conf.template");
    expect(nginx).toMatch(/add_header\s+Content-Security-Policy\s+"/);
    expect(nginx).toMatch(/frame-ancestors\s+'none'/);
    expect(nginx).toMatch(/add_header\s+X-Content-Type-Options\s+"nosniff"/);
    expect(nginx).toMatch(/add_header\s+Referrer-Policy\s+"/);
    expect(nginx).toMatch(/add_header\s+X-Frame-Options\s+"DENY"/);
    // Headers apply with always so error responses also carry them
    expect(nginx).toMatch(/add_header\s+Content-Security-Policy\s+"[^"]+"\s+always\s*;/);
    expect(nginx).toMatch(/add_header\s+X-Content-Type-Options\s+"nosniff"\s+always\s*;/);
  });

  it("Helm ships same-origin Ingress through console Service (M4)", () => {
    const ingress = read("infrastructure/helm/agricore/templates/ingress.yaml");
    expect(ingress).toMatch(/kind:\s*Ingress/);
    expect(ingress).toMatch(/name:\s*agricore-console/);
    expect(ingress).toMatch(/path:\s*\//);
    expect(ingress).toMatch(/pathType:\s*Prefix/);
    // Single front door — not a split API host that breaks cookie SameSite
    expect(ingress).not.toMatch(/agricore-gateway/);

    const values = read("infrastructure/helm/agricore/values.yaml");
    expect(values).toMatch(/ingress:[\s\S]*?enabled:\s*true/);
    expect(values).toMatch(/ingress:[\s\S]*?host:\s*console\.example\.com/);
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
