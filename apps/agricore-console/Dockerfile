# syntax=docker/dockerfile:1.7
FROM node:22.23.1-alpine3.23@sha256:8516dce0483394d5708d4b2ee6cacb79fb1d617ea4e2787c2120bcca92ce372e AS build

WORKDIR /workspace
RUN corepack prepare pnpm@11.17.0 --activate \
    && corepack enable pnpm \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/agricore-console/package.json apps/agricore-console/package.json
RUN --mount=type=cache,id=agricore-pnpm,target=/root/.local/share/pnpm/store,sharing=locked \
    pnpm install --frozen-lockfile --filter @agricore/console...

COPY apps/agricore-console apps/agricore-console
COPY contracts contracts
COPY assets/media/agricore-showcase assets/media/agricore-showcase
RUN pnpm --filter @agricore/console build

FROM nginxinc/nginx-unprivileged:1.30.2-alpine3.23@sha256:de3e40ec8b7debd7194fc798d4bbfb102c7f8b012b2c73032816b5f72393acdd
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.source="https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.revision="${GIT_SHA}"

USER root
RUN apk upgrade --no-cache c-ares curl libcurl libexpat
COPY apps/agricore-console/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/agricore-console/dist /usr/share/nginx/html

USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz | grep -q ok || exit 1
