FROM node:22-alpine

WORKDIR /app

COPY package.json pnpm-lock.yaml ./

RUN corepack enable \
    && pnpm install --frozen-lockfile --prod

COPY server.mjs ./server.mjs

ENV HOST=0.0.0.0
ENV PORT=1235

EXPOSE 1235

CMD ["node", "server.mjs"]
