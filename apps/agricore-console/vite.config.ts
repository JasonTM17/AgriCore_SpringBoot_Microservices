import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

import { browserSecurityPolicyPlugin } from "./config/browser-security-policy";

const gatewayUrl = process.env["AGRICORE_GATEWAY_URL"] ?? "http://localhost:8080";

export default defineConfig(({ command }) => ({
  plugins: [browserSecurityPolicyPlugin(command), react(), tailwindcss()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api": {
        target: gatewayUrl,
        changeOrigin: true,
      },
      "/public/api": {
        target: gatewayUrl,
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: "127.0.0.1",
    port: 4173,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: true,
    maxWorkers: 6,
    coverage: {
      reporter: ["text", "lcov"],
    },
  },
}));
