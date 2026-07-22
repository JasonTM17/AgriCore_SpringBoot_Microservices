FROM node:22.13.0-alpine AS build

WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@11.0.9 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/agricore-console/package.json apps/agricore-console/package.json
RUN pnpm install --frozen-lockfile --filter @agricore/console...

COPY apps/agricore-console apps/agricore-console
COPY contracts contracts
RUN pnpm --filter @agricore/console build

FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY apps/agricore-console/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/agricore-console/dist /usr/share/nginx/html

USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz | grep -q ok || exit 1
