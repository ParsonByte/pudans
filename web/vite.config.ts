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
  base: "/auth/",
  envDir: webRoot,
  plugins: [react(), ...(useHttps ? [mkcert()] : [])],
  resolve: {
    alias: [
      {
        find: /^@pudans\/theme\/style\.css$/,
        replacement: fileURLToPath(new URL("./packages/theme/src/index.css", import.meta.url)),
      },
      {
        find: /^@pudans\/components$/,
        replacement: fileURLToPath(new URL("./packages/components/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/theme$/,
        replacement: fileURLToPath(new URL("./packages/theme/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/icons$/,
        replacement: fileURLToPath(new URL("./packages/icons/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/hooks$/,
        replacement: fileURLToPath(new URL("./packages/hooks/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/utils$/,
        replacement: fileURLToPath(new URL("./packages/utils/src/index.ts", import.meta.url)),
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
    outDir: "../build/dist/auth",
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
