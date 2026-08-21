# Contexto de build: raíz del monorepo (ver docker-compose.yml -> frontend.build.context: .)

FROM node:20-alpine AS build
WORKDIR /build
COPY frontend/package.json .
RUN npm install
COPY frontend/ .
ARG VITE_API_BASE_URL=http://localhost:8080/api/v1
ARG VITE_MAP_TILE_URL=https://tile.openstreetmap.org/{z}/{x}/{y}.png
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_MAP_TILE_URL=$VITE_MAP_TILE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
