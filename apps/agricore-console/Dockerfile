FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@11.0.9 --activate
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/agricore-console/package.json apps/agricore-console/
RUN pnpm install --frozen-lockfile
COPY apps/agricore-console apps/agricore-console
RUN pnpm --filter @agricore/console build

FROM nginx:1.27-alpine
# Official image envsubst of /etc/nginx/templates/*.template → conf.d/
ENV GATEWAY_UPSTREAM=api-gateway:8080
COPY apps/agricore-console/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/apps/agricore-console/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:80/ >/dev/null || exit 1
