#!/usr/bin/env node
/**
 * Component export gate.
 * - verifies every folder under packages/components/src/components has a barrel and is re-exported
 * - verifies named exports are unique across folders
 * - verifies the component package root barrel exports ONLY ./components
 * - verifies the shared package exposes its expected entries
 * Usage: node scripts/check-exports.mjs
 */
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(dirname(dirname(fileURLToPath(import.meta.url)))), "web");
const src = join(root, "packages/components/src");
const components = join(src, "components");
const problems = [];

const folders = readdirSync(components, { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
  .sort();
const barrel = readFileSync(join(components, "index.ts"), "utf8");

for (const folder of folders) {
  if (!existsSync(join(components, folder, "index.ts"))) {
    problems.push("missing folder barrel: components/" + folder + "/index.ts");
  }
  if (!barrel.includes('"./' + folder + '"')) {
    problems.push("folder not re-exported: " + folder);
  }
}

const names = new Map();
for (const folder of folders) {
  const barrelPath = join(components, folder, "index.ts");
  if (!existsSync(barrelPath)) continue;
  const barrelSource = readFileSync(barrelPath, "utf8");
  const files = [...new Set(barrelSource.split("\n").filter((line) => line.includes('from "./')).map((line) => line.split('"')[1].replace("./", "")))];
  for (const file of files) {
    const tsx = join(components, folder, file + ".tsx");
    const ts = join(components, folder, file + ".ts");
    const source = existsSync(tsx) ? readFileSync(tsx, "utf8") : existsSync(ts) ? readFileSync(ts, "utf8") : "";
    const declarations = [
      ...source.matchAll(/export\s+(?:const|function|class|interface|type|enum)\s+([A-Za-z0-9_]+)/g),
      ...source.matchAll(/export\s*\{([^}]+)\}/g),
    ];
    for (const declaration of declarations) {
      for (const token of declaration[1].split(",")) {
        const name = token.trim().split(/\s+as\s+/).pop();

        const existing = names.get(name);
        if (existing !== undefined && existing !== folder) {
          problems.push("duplicate export " + name + " in " + existing + " and " + folder);
        } else {
          names.set(name, folder);
        }
      }
    }
  }
}

const rootBarrel = readFileSync(join(src, "index.ts"), "utf8");
if (!rootBarrel.includes('"./components"')) problems.push("root barrel must export ./components");
for (const forbidden of ["./hooks", "./layouts", "./styles", "./utils"]) {
  if (rootBarrel.includes(forbidden)) problems.push("root barrel must not export non-component entry " + forbidden);
}
for (const folder of ["hooks", "layouts", "utils", "styles"]) {
  if (existsSync(join(src, folder))) problems.push("non-component directory found in component package: " + folder);
}

const sharedEntries = [
  "index.ts",
  "theme/tokens.ts",
  "theme/create-theme.ts",
  "theme/theme-provider.tsx",
  "theme/variables.css",
  "theme/global.css",
  "theme/index.ts",
  "icons/index.ts",
  "icons/legacy-icons.ts",
  "hooks/index.ts",
  "hooks/use-overflow.ts",
  "utils/classnames.ts",
];
for (const entry of sharedEntries) {
  if (!existsSync(join(root, "packages", "shared", "src", entry))) {
    problems.push("missing shared entry: " + entry);
  }
}

if (problems.length > 0) {
  console.error("Export check failed:");
  for (const problem of problems) console.error(" - " + problem);
  process.exit(1);
}
console.log("Export check passed: " + folders.length + " component folders, " + names.size + " unique named exports, pure root barrel.");
