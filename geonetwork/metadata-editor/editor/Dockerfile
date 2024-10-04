FROM node:20-bookworm AS builder

WORKDIR /usr/src/app

RUN git clone --depth=1 https://github.com/geonetwork/geonetwork-ui.git . && \
 npm install && \
 npx nx build metadata-editor --base-href='/metadata-editor/'

FROM nginx:latest
COPY --from=builder /usr/src/app/dist/apps/metadata-editor /usr/share/nginx/html/metadata-editor
COPY nginx.conf /etc/nginx/conf.d/default.conf
