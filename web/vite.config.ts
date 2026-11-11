import { fileURLToPath, URL } from "node:url";
import path from "node:path";
import react from "@vitejs/plugin-react-swc";
import mkcert from "vite-plugin-mkcert";
import { viteStaticCopy } from "vite-plugin-static-copy";
import { defineConfig } from "vite";

const webRoot = fileURLToPath(new URL(".", import.meta.url));
const appRoot = fileURLToPath(new URL("./app", import.meta.url));
const useHttps = process.env.VITE_HTTPS === "true";

export default defineConfig({
  root: appRoot,
  base: "/",
  envDir: webRoot,
  plugins: [
    react(),
    viteStaticCopy({
      targets: [
        {
          src: path.resolve(webRoot, "node_modules/pdfjs-dist/cmaps"),
          dest: "pdfjs",
        },
        {
          src: path.resolve(webRoot, "node_modules/pdfjs-dist/standard_fonts"),
          dest: "pdfjs",
        },
      ],
    }),
    ...(useHttps ? [mkcert()] : []),
  ],
  resolve: {
    alias: [
      {
        find: "@web",
        replacement: fileURLToPath(new URL("./src", import.meta.url)),
      },
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
        find: /^@pudans\/layouts$/,
        replacement: fileURLToPath(new URL("./packages/layouts/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/utils$/,
        replacement: fileURLToPath(new URL("./packages/utils/src/index.ts", import.meta.url)),
      },
      {
        find: /^@pudans\/api-client$/,
        replacement: fileURLToPath(new URL("./packages/api-client/src/index.ts", import.meta.url)),
      },
      {
        find: /^path$/,
        replacement: "path-browserify",
      },
    ],
  },
  worker: {
    format: "es",
  },
  optimizeDeps: {
    include: [
      "monaco-editor/esm/vs/editor/editor.api",
      "pdfjs-dist",
      "fuse.js",
      "lodash",
      "path-browserify",
      "hls.js",
      "mpegts.js",
      "leaflet",
      "mapbox-gl",
      "qrcode.react",
      "timeago-react",
    ],
  },
  server: {
    port: 5173,
    ...(useHttps ? { https: {} } : {}),
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
    chunkSizeWarningLimit: 4096,
    rollupOptions: {
      input: {
        setup: path.resolve(appRoot, "setup/index.html"),
        lab: path.resolve(appRoot, "lab/index.html"),
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
