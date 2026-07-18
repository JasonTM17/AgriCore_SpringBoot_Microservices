import { describe, expect, it } from "vitest";

import { buildBrowserSecurityPolicy } from "./browser-security-policy";

describe("buildBrowserSecurityPolicy", () => {
  it("enforces a strict production policy without unsafe script or style sources", () => {
    const policy = buildBrowserSecurityPolicy("build");

    expect(policy).toContain("default-src 'self'");
    expect(policy).toContain("script-src 'self'");
    expect(policy).toContain("style-src 'self'");
    expect(policy).toContain("object-src 'none'");
    expect(policy).toContain("base-uri 'none'");
    expect(policy).toContain("require-trusted-types-for 'script'");
    expect(policy).toContain("trusted-types 'none'");
    expect(policy).not.toContain("'unsafe-eval'");
    expect(policy).not.toContain("'unsafe-inline'");
    expect(policy).not.toMatch(/https?:\/\//u);
  });

  it("allows only local Vite websocket and injected development styles while serving", () => {
    const policy = buildBrowserSecurityPolicy("serve");

    expect(policy).toContain("style-src 'self' 'unsafe-inline'");
    expect(policy).toContain("ws://127.0.0.1:*");
    expect(policy).toContain("ws://localhost:*");
    expect(policy).not.toContain("'unsafe-eval'");
  });
});
