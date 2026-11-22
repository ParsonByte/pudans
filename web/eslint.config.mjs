import js from "@eslint/js";
import nextPlugin from "@next/eslint-plugin-next";
import queryPlugin from "@tanstack/eslint-plugin-query";
import reactHooks from "eslint-plugin-react-hooks";
import { defineConfig, globalIgnores } from "eslint/config";
import globals from "globals";
import tseslint from "typescript-eslint";

export default defineConfig([
    globalIgnores([
        ".next/**",
        "out/**",
        "build/**",
        "next-env.d.ts",
        "packages/api-client/src/**",
        "packages/api-client/dist/**",
    ]),
    js.configs.recommended,
    ...tseslint.configs.recommended,
    reactHooks.configs.flat.recommended,
    ...queryPlugin.configs["flat/recommended"],
    {
        files: ["**/*.{js,mjs,cjs,ts,tsx}"],
        languageOptions: {
            globals: {
                ...globals.browser,
                ...globals.node,
            },
        },
    },
    {
        files: ["**/*.{ts,tsx}"],
        plugins: {
            "@next/next": nextPlugin,
        },
        rules: {
            ...nextPlugin.configs.recommended.rules,
            ...nextPlugin.configs["core-web-vitals"].rules,
        },
    },
]);
