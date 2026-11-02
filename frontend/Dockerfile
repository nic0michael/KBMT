FROM node:24-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

ARG APP_VERSION=1.0
LABEL org.opencontainers.image.title="KNOWLmate Frontend" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.description="Angular web frontend for KNOWLmate"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser/ /usr/share/nginx/html/

EXPOSE 8080
