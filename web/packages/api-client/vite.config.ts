import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";

export default defineConfig({
  build: {
    lib: {
      entry: fileURLToPath(new URL("./entry/index.ts", import.meta.url)),
      formats: ["es"],
      fileName: "index",
    },
    rollupOptions: {
      external: ["axios"],
    },
    sourcemap: true,
    target: "es2022",
    emptyOutDir: true,
  },
});
