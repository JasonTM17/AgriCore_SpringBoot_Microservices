# syntax=docker/dockerfile:1.7
FROM node:22.13.0-alpine@sha256:f2dc6eea95f787e25f173ba9904c9d0647ab2506178c7b5b7c5a3d02bc4af145 AS build

WORKDIR /workspace
RUN npm install --global pnpm@11.0.9

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/agricore-console/package.json apps/agricore-console/package.json
RUN --mount=type=cache,id=agricore-pnpm,target=/root/.local/share/pnpm/store,sharing=locked \
    pnpm install --frozen-lockfile --filter @agricore/console...

COPY apps/agricore-console apps/agricore-console
COPY contracts contracts
RUN pnpm --filter @agricore/console build

FROM nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0

COPY apps/agricore-console/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/agricore-console/dist /usr/share/nginx/html

USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz | grep -q ok || exit 1
