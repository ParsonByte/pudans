import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react-swc";
import mkcert from "vite-plugin-mkcert";
import { defineConfig } from "vite";

const webRoot = fileURLToPath(new URL(".", import.meta.url));
const useHttps = process.env.VITE_HTTPS === "true";
const proxyTarget = process.env.VITE_PROXY_TARGET ?? "http://localhost:8090";

/**
 * The auth application is the only web entry point. The server mounts the
 * build under /auth, so assets and client-side routes share that base.
 */
export default defineConfig({
  root: webRoot,
  base: "/",
  envDir: webRoot,
  plugins: [react(), ...(useHttps ? [mkcert()] : [])],
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
        replacement: fileURLToPath(new URL("./packages/api-client/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/components$/,
        replacement: fileURLToPath(new URL("./packages/components/src/index.ts", import.meta.url)),
      },
    ],
  },
  worker: {
    format: "es",
  },
  server: {
    port: 5173,
    ...(useHttps ? { https: {} } : {}),
    fs: {
      allow: [webRoot],
    },
    proxy: {
      "/apis": {
        target: proxyTarget,
        changeOrigin: true,
      },
      "/login": {
        target: proxyTarget,
        changeOrigin: true,
      },
      "/logout": {
        target: proxyTarget,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "../build/dist",
    emptyOutDir: true,
    sourcemap: true,
    target: "es2022",
    chunkSizeWarningLimit: 2048,
    rollupOptions: {
      output: {
        entryFileNames: "assets/[name]-[hash].js",
        chunkFileNames: "assets/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash][extname]",
      },
    },
  },
});
