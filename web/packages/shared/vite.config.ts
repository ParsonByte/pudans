import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react-swc";
import { defineConfig } from "vite";

const entry = (path: string) => fileURLToPath(new URL(path, import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": entry("./src"),
    },
  },
  build: {
    lib: {
      entry: {
        index: entry("./src/index.ts"),
        theme: entry("./src/theme/index.ts"),
        icons: entry("./src/icons/index.ts"),
        hooks: entry("./src/hooks/index.ts"),
      },
      formats: ["es"],
    },
    rollupOptions: {
      external: [
        /^react($|\/)/,
        /^react-dom($|\/)/,
        /^@mui\//,
        /^@emotion\//,
        /^@iconify\//,
      ],
      output: {
        entryFileNames: "[name].js",
        chunkFileNames: "chunks/[name]-[hash].js",
        assetFileNames: (asset) => (asset.name?.endsWith(".css") ? "theme.css" : "assets/[name][extname]"),
      },
    },
    sourcemap: true,
    cssMinify: false,
    target: "es2022",
    emptyOutDir: true,
  },
});
