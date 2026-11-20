import js from "@eslint/js";
import nextPlugin from "@next/eslint-plugin-next";
import pluginQuery from "@tanstack/eslint-plugin-query";
import reactHooks from "eslint-plugin-react-hooks";
import { defineConfig, globalIgnores } from "eslint/config";
import globals from "globals";
import tseslint from "typescript-eslint";

export default defineConfig([
  globalIgnores([
    "dist",
    "build",
    ".next",
    "next-env.d.ts",
    "src/routeTree.gen.ts",
    "packages/api-client/src",
    "packages/api-client/dist",
  ]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      pluginQuery.configs["flat/recommended"],
      nextPlugin.configs["core-web-vitals"],
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
  },
  {
    files: ["next.config.ts", "postcss.config.mjs"],
    languageOptions: {
      globals: globals.node,
    },
  },
]);
