import { fileURLToPath, URL } from "node:url";
import path from "node:path";
import react from "@vitejs/plugin-react-swc";
import mkcert from "vite-plugin-mkcert";
import { VitePWA } from "vite-plugin-pwa";
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
    VitePWA({
      registerType: "autoUpdate",
      injectRegister: false,
      includeAssets: ["icons/pwa-192.png", "icons/pwa-512.png", "login-bg-poster.jpg"],
      manifest: {
        name: "pudans",
        short_name: "pudans",
        description: "pudans identity, permission and rich-media workspace for the web.",
        theme_color: "#1976d2",
        background_color: "#fafafa",
        display: "standalone",
        orientation: "any",
        scope: "/",
        start_url: "/console/",
        icons: [
          {
            src: "/icons/pwa-192.png",
            sizes: "192x192",
            type: "image/png",
          },
          {
            src: "/icons/pwa-512.png",
            sizes: "512x512",
            type: "image/png",
          },
          {
            src: "/icons/pwa-maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,woff2,png,svg,jpg,jpeg,json}"],
        maximumFileSizeToCacheInBytes: 8 * 1024 * 1024,
        navigateFallbackDenylist: [/^\/apis/, /^\/system/, /^\/api/, /^\/login/, /^\/logout/],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/tile\.openstreetmap\.org\//,
            handler: "CacheFirst",
            options: {
              cacheName: "osm-tiles",
              expiration: { maxEntries: 600, maxAgeSeconds: 7 * 24 * 3600 },
            },
          },
          {
            urlPattern: /^https:\/\/.*\.(?:png|jpe?g|svg|webp)$/,
            handler: "StaleWhileRevalidate",
            options: {
              cacheName: "remote-images",
              expiration: { maxEntries: 240, maxAgeSeconds: 7 * 24 * 3600 },
            },
          },
        ],
      },
      devOptions: {
        enabled: false,
      },
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
