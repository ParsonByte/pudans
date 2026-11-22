/** @type {import("prettier").Config} */
const config = {
    printWidth: 120,
    plugins: ["@trivago/prettier-plugin-sort-imports", "prettier-plugin-tailwindcss"],
    importOrder: ["^react$", "^next(/.*)?$", "<THIRD_PARTY_MODULES>", "^@/(.*)$", "^[./]"],
    importOrderSeparation: true,
    importOrderSortSpecifiers: true,
    importOrderParserPlugins: ["typescript", "jsx"],
    tailwindStylesheet: "./src/styles/_global.css",
};

export default config;
