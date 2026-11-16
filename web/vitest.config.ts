import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vitest/config";

const webRoot = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig({
  root: webRoot,
  resolve: {
    alias: [
      {
        find: /^@pudans\/shared\/theme\/style\.css$/,
        replacement: fileURLToPath(new URL("./packages/shared/src/theme/index.css", import.meta.url)),
      },
      {
        find: /^@pudans\/shared\/theme$/,
        replacement: fileURLToPath(new URL("./packages/shared/src/theme/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/shared\/icons$/,
        replacement: fileURLToPath(new URL("./packages/shared/src/icons/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/shared\/hooks$/,
        replacement: fileURLToPath(new URL("./packages/shared/src/hooks/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/shared$/,
        replacement: fileURLToPath(new URL("./packages/shared/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/api-client$/,
        replacement: fileURLToPath(new URL("./packages/api-client/entry/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/components$/,
        replacement: fileURLToPath(new URL("./packages/components/src/index.ts", import.meta.url)),
      },
    ],
  },
  test: {
    environment: "jsdom",
    environmentOptions: {
      jsdom: {
        url: "http://localhost/console/users",
      },
    },
    include: ["app/**/*.test.ts", "app/**/*.test.tsx"],
    css: true,
    clearMocks: true,
    restoreMocks: true,
  },
});
