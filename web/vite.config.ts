import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [tanstackRouter({ target: "react", autoCodeSplitting: true }), react(), tailwindcss()],
  build: {
    outDir: "build/web",
    emptyOutDir: true,
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    proxy: {
      "/apis": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      "/login": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      "/logout": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
});
