import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react";
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
      cssFileName: "style",
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
        "react-is",
        "react-transition-group",
        "recharts",
        "notistack",
        "mui-one-time-password-input",
        "material-ui-popup-state",
        "react-highlight-words",
        "react-hotkeys-hook",
        "react-image-crop",
        "dayjs",
        "clsx",
        "tailwind-merge",
      ],
    },
    sourcemap: true,
    cssMinify: false,
    target: "es2022",
    emptyOutDir: true,
  },
});
