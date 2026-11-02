import { resolve } from "node:path";
import type { Plugin, UserConfig } from "vite";
import { defineUiPluginManifest, type PudansUiPluginManifest } from "./manifest";

const HOST_EXTERNALS = [
  "react",
  "react-dom",
  "react/jsx-runtime",
  "react/jsx-dev-runtime",
  "@pudans/api-client",
  "@pudans/components",
  "@pudans/shared",
] as const;

export function isHostExternal(id: string): boolean {
  return (
    HOST_EXTERNALS.includes(id as (typeof HOST_EXTERNALS)[number]) ||
    id.startsWith("react/") ||
    id.startsWith("react-dom/") ||
    id.startsWith("@pudans/") ||
    id.startsWith("@mui/") ||
    id.startsWith("@emotion/")
  );
}

export interface PudansUiPluginBuildOptions {
  manifest: PudansUiPluginManifest;
  /** Source entry relative to process.cwd(). */
  entry?: string;
  outDir?: string;
  sourcemap?: boolean;
  minify?: boolean;
  additionalExternals?: string[];
}

/** Emits plugin.json and verifies that its entry matches the actual bundle. */
export function pudansUiPluginManifestPlugin(
  options: Pick<PudansUiPluginBuildOptions, "manifest">,
): Plugin {
  const manifest = defineUiPluginManifest(options.manifest);

  return {
    name: "pudans:ui-plugin-manifest",
    apply: "build",
    enforce: "post",
    generateBundle(_outputOptions, bundle) {
      const entryChunk = Object.values(bundle).find(
        (item) => item.type === "chunk" && item.isEntry,
      );

      if (!entryChunk) {
        this.error("pudans UI plugin build produced no JavaScript entry chunk.");
      }
      if (entryChunk.fileName !== manifest.spec.entry) {
        this.error(
          `Manifest entry "${manifest.spec.entry}" does not match emitted entry "${entryChunk.fileName}".`,
        );
      }

      this.emitFile({
        type: "asset",
        fileName: "plugin.json",
        source: JSON.stringify(manifest, null, 2),
      });
    },
  };
}

/**
 * Vite preset for a host-externalized pudans UI plugin:
 * React, MUI, Emotion and @pudans/* stay in the host bundle.
 */
export function definePudansUiPluginConfig(options: PudansUiPluginBuildOptions): UserConfig {
  const manifest = defineUiPluginManifest(options.manifest);
  const sourceEntry = options.entry ?? "src/index.ts";
  const outDir = options.outDir ?? "dist";
  const extraExternals = new Set(options.additionalExternals ?? []);

  return {
    plugins: [pudansUiPluginManifestPlugin({ manifest })],
    build: {
      outDir,
      emptyOutDir: true,
      sourcemap: options.sourcemap ?? true,
      minify: options.minify ?? false,
      cssCodeSplit: false,
      lib: {
        entry: resolve(process.cwd(), sourceEntry),
        formats: ["es"],
        // Vite 8 / Rolldown treats the returned value as the full file name.
        fileName: () => manifest.spec.entry,
        cssFileName: "style",
      },
      rollupOptions: {
        external: (id) => isHostExternal(id) || extraExternals.has(id),
      },
    },
  };
}
