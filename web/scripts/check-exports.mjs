#!/usr/bin/env node
/**
 * Component export gate.
 * 1. every folder under packages/components/src/components has an index barrel
 * 2. every folder is re-exported from the package barrel
 * 3. named exports are unique across folders
 * 4. the root barrel re-exports components/hooks/layouts/styles/utils
 * Usage: node scripts/check-exports.mjs
 */
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
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
  for (const match of barrelSource.matchAll(/from\s+"\.\/([^"]+)"/g)) {
    const file = join(components, folder, match[1] + ".tsx");
    const ts = join(components, folder, match[1] + ".ts");
    const source = existsSync(file)
      ? readFileSync(file, "utf8")
      : existsSync(ts)
        ? readFileSync(ts, "utf8")
        : "";
    const declarations = [
      ...source.matchAll(
        /export\s+(?:const|function|class|interface|type|enum)\s+([A-Za-z0-9_]+)/g,
      ),
      ...source.matchAll(/export\s*\{([^}]+)\}/g),
    ];
    for (const declaration of declarations) {
      for (const token of declaration[1].split(",")) {
        const name = token
          .trim()
          .split(/\s+as\s+/)
          .pop();
        if (!name) continue;
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
for (const expected of [
  "./components",
  "./hooks",
  "./layouts",
  "./styles/create-theme",
  "./styles/theme-provider",
  "./styles/tokens",
  "./utils",
]) {
  if (!rootBarrel.includes(expected)) problems.push("root barrel missing " + expected);
}
for (const folder of ["hooks", "layouts", "utils"]) {
  if (!existsSync(join(src, folder, "index.ts")))
    problems.push("missing barrel: " + folder + "/index.ts");
}

if (problems.length > 0) {
  console.error("Export check failed:");
  for (const problem of problems) console.error(" - " + problem);
  process.exit(1);
}
console.log(
  "Export check passed: " +
    folders.length +
    " component folders, " +
    names.size +
    " unique named exports.",
);
