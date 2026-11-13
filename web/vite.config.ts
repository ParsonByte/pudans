import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react-swc";
import mkcert from "vite-plugin-mkcert";
import { defineConfig, type Plugin } from "vite";

const webRoot = fileURLToPath(new URL(".", import.meta.url));
const useHttps = process.env.VITE_HTTPS === "true";
const proxyTarget = process.env.VITE_PROXY_TARGET ?? "http://localhost:8090";

const DEV_SERVER_PORT = 3000;

function entryForPath(pathname: string): string | null {
  if (pathname === "/console" || pathname.startsWith("/console/")) {
    return "/console.html";
  }
  if (pathname === "/uc" || pathname.startsWith("/uc/")) {
    return "/uc.html";
  }
  if (
    pathname === "/" ||
    pathname === "/auth" ||
    pathname.startsWith("/auth/") ||
    pathname === "/setup" ||
    pathname === "/forbidden"
  ) {
    return "/auth.html";
  }
  return null;
}

/**
 * Dev-only MPA history fallback, mirroring the production server mapping:
 * /console/** -> console.html, /uc/** -> uc.html, /auth/** -> auth.html.
 */
function mpaEntryFallback(): Plugin {
  return {
    name: "pudans-mpa-entry-fallback",
    apply: "serve",
    configureServer(server) {
      server.middlewares.use((request, _response, next) => {
        if (!request.url) {
          next();
          return;
        }
        const url = new URL(request.url, "http://localhost");
        const entry = entryForPath(url.pathname);
        if (entry) {
          request.url = `${entry}${url.search}`;
        }
        next();
      });
    },
  };
}

/**
 * Halo's devPlugin behaviour: HTML is proxied through the backend at :8090,
 * so root-relative module and asset URLs are rewritten to the Vite origin.
 */
function devAbsoluteUrls(): Plugin {
  const origin = `http://localhost:${DEV_SERVER_PORT}`;
  return {
    name: "pudans-dev-absolute-urls",
    apply: "serve",
    transformIndexHtml: {
      order: "post",
      handler: (html) =>
        html.replace(/ (src|href)="(\/(?!\/).+?)"/g, ` $1="${origin}$2"`),
    },
  };
}

/**
 * Production assets live under classpath:/static/assets but are served from
 * /ui-assets/**, matching the backend resource handler. Vite writes the files
 * to build/dist/assets; only the HTML references need the public prefix.
 */
function uiAssetsBase(): Plugin {
  const rewrite = (value: string): string => value.replace(/(["'(])\/assets\//g, "$1/ui-assets/");
  return {
    name: "pudans-ui-assets-base",
    apply: "build",
    transformIndexHtml: {
      order: "post",
      handler: rewrite,
    },
    generateBundle(_options, bundle) {
      const outputs = Object.values(bundle) as Array<{
        type: string;
        fileName: string;
        source?: string | Uint8Array;
      }>;
      for (const output of outputs) {
        if (output.type === "asset" && output.fileName.endsWith(".css") && typeof output.source === "string") {
          output.source = rewrite(output.source);
        }
      }
    },
  };
}

export default defineConfig({
  root: webRoot,
  base: "/",
  envDir: webRoot,
  plugins: [react(), mpaEntryFallback(), devAbsoluteUrls(), uiAssetsBase(), ...(useHttps ? [mkcert()] : [])],
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
        replacement: fileURLToPath(new URL("./packages/api-client/entry/index.ts", import.meta.url)),
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
    port: DEV_SERVER_PORT,
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
    assetsDir: "assets",
    rollupOptions: {
      input: {
        auth: fileURLToPath(new URL("./auth.html", import.meta.url)),
        console: fileURLToPath(new URL("./console.html", import.meta.url)),
        uc: fileURLToPath(new URL("./uc.html", import.meta.url)),
      },
      output: {
        entryFileNames: "assets/[name]-[hash].js",
        chunkFileNames: "assets/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash][extname]",
      },
    },
  },
});
