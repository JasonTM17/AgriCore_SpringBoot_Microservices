import type { ConfigEnv, Plugin } from "vite";

type ViteCommand = ConfigEnv["command"];

const SHARED_DIRECTIVES = [
  "default-src 'self'",
  "base-uri 'none'",
  "font-src 'self'",
  "form-action 'self'",
  "frame-src 'none'",
  "img-src 'self' data:",
  "manifest-src 'self'",
  "media-src 'self'",
  "object-src 'none'",
  "worker-src 'self'",
  "require-trusted-types-for 'script'",
  "trusted-types 'none'",
] as const;

export function buildBrowserSecurityPolicy(command: ViteCommand): string {
  const scriptSources =
    command === "serve" ? "script-src 'self' 'unsafe-inline'" : "script-src 'self'";
  const connectSources =
    command === "serve"
      ? "connect-src 'self' ws://127.0.0.1:* ws://localhost:*"
      : "connect-src 'self'";
  const styleSources =
    command === "serve" ? "style-src 'self' 'unsafe-inline'" : "style-src 'self'";

  return [...SHARED_DIRECTIVES, scriptSources, connectSources, styleSources].join("; ");
}

export function browserSecurityPolicyPlugin(command: ViteCommand): Plugin {
  return {
    name: "agricore-browser-security-policy",
    transformIndexHtml() {
      return [
        {
          tag: "meta",
          attrs: {
            "http-equiv": "Content-Security-Policy",
            content: buildBrowserSecurityPolicy(command),
          },
          injectTo: "head-prepend",
        },
      ];
    },
  };
}
