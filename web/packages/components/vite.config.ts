import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react-swc";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  build: {
    lib: {
      entry: fileURLToPath(new URL("./src/index.ts", import.meta.url)),
      formats: ["es"],
      fileName: "index",
    },
    rollupOptions: {
      external: [
        /^react($|\/)/,
        /^react-dom($|\/)/,
        /^@mui\//,
        /^@emotion\//,
        /^@iconify\//,
        /^react-dnd($|\/)/,
        "react-dnd-html5-backend",
        "react-animate-height",
        "react-transition-group",
        "recharts",
        "notistack",
        "mui-one-time-password-input",
        "material-ui-popup-state",
        "react-highlight-words",
        "react-hotkeys-hook",
        "react-image-crop",
        "dayjs",
      ],
      output: {
        assetFileNames: (assetInfo) =>
          assetInfo.name?.endsWith(".css") ? "style.css" : "assets/[name][extname]",
      },
    },
    sourcemap: true,
    cssMinify: false,
    target: "es2022",
    emptyOutDir: true,
  },
});
