FROM node:24-alpine AS build

WORKDIR /app

ARG PUDANS_API_ORIGIN=http://api:8090
ENV PUDANS_API_ORIGIN=${PUDANS_API_ORIGIN}
ENV NEXT_TELEMETRY_DISABLED=1

COPY web/package.json web/package-lock.json ./
COPY web/packages/api-client/package.json packages/api-client/package.json
RUN npm ci

COPY web/ ./
RUN npm run build:standalone

FROM node:24-alpine AS runtime

WORKDIR /app

ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

COPY --from=build /app/.next/standalone ./

EXPOSE 3000

CMD ["node", "server.js"]