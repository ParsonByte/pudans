import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react-swc";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  build: {
    lib: { entry: fileURLToPath(new URL("./src/index.ts", import.meta.url)), formats: ["es"], fileName: "index" },
    rollupOptions: {
      external: ["@pudans/components", "@pudans/theme", /^react($|\/)/, /^react-dom($|\/)/, /^@mui\//, /^@emotion\//],
      output: { assetFileNames: (asset) => (asset.name?.endsWith(".css") ? "style.css" : "assets/[name][extname]") },
    },
    sourcemap: true, cssMinify: false, target: "es2022", emptyOutDir: true,
  },
});
