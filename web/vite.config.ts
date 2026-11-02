import { fileURLToPath, URL } from "node:url";
import path from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const webRoot = fileURLToPath(new URL(".", import.meta.url));
const appRoot = fileURLToPath(new URL("./app", import.meta.url));

export default defineConfig({
  root: appRoot,
  base: "/",
  envDir: webRoot,
  plugins: [react()],
  resolve: {
    alias: [
      {
        find: "@web",
        replacement: fileURLToPath(new URL("./src", import.meta.url)),
      },
      {
        // Serve the library stylesheet from source so theme tokens are never
        // stale relative to the component sources during app builds.
        find: /^@pudans\/components\/style\.css$/,
        replacement: fileURLToPath(
          new URL("./packages/components/src/styles/index.css", import.meta.url),
        ),
      },
      {
        find: /^@pudans\/components$/,
        replacement: fileURLToPath(new URL("./packages/components/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/shared$/,
        replacement: fileURLToPath(new URL("./packages/shared/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/api-client$/,
        replacement: fileURLToPath(new URL("./packages/api-client/src/index.ts", import.meta.url)),
      },
    ],
  },
  server: {
    port: 5173,
    fs: {
      allow: [webRoot],
    },
    proxy: {
      "/apis": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      "/system": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      "/api": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "../build/dist",
    emptyOutDir: true,
    sourcemap: true,
    target: "es2022",
    rollupOptions: {
      input: {
        setup: path.resolve(appRoot, "setup/index.html"),
        auth: path.resolve(appRoot, "auth/index.html"),
        console: path.resolve(appRoot, "console/index.html"),
        uc: path.resolve(appRoot, "uc/index.html"),
      },
      output: {
        entryFileNames: "assets/[name]-[hash].js",
        chunkFileNames: "assets/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash][extname]",
      },
    },
  },
});
