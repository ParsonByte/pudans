#!/usr/bin/env node
/**
 * Component-library boundary gate.
 * Fails if web/packages/components contains anything beyond component sources,
 * component CSS modules, component barrels/types and build config, or if it
 * imports application code / app-specific libraries.
 * Usage: node scripts/check-component-boundary.mjs
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(dirname(dirname(fileURLToPath(import.meta.url)))), "web");
const pkg = join(root, "packages/components");
const src = join(pkg, "src");
const problems = [];

const allowedRoot = new Set(["package.json", "tsconfig.json", "vite.config.ts", "README.md", "src", "node_modules", "dist"]);
for (const entry of readdirSync(pkg)) {
  if (!allowedRoot.has(entry)) problems.push("unexpected entry in components package: " + entry);
}
const allowedSrc = new Set(["index.ts", "index.css", "components"]);
for (const entry of readdirSync(src)) {
  if (!allowedSrc.has(entry)) problems.push("unexpected entry in components/src: " + entry);
}

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}
for (const file of walk(src)) {
  const rel = relative(pkg, file);
  if (rel === "src/index.ts" || rel === "src/index.css") continue;
  if (!rel.startsWith("src/components/")) {
    problems.push("file outside components/: " + rel);
    continue;
  }
  if (!/(\.tsx|\.module\.css|\/index\.ts)$/.test(rel)) {
    problems.push("non-component/type/style file in components/: " + rel);
  }
}

const componentDir = join(src, "components");
for (const entry of readdirSync(componentDir, { withFileTypes: true })) {
  if (!entry.isDirectory() && entry.name !== "index.ts") {
    problems.push("file directly under components/: " + entry.name);
  }
}

const forbiddenImports = [
  /from\s+"@web\//,
  /from\s+"@pudans\/api-client/,
  /from\s+"react-router-dom"/,
  /from\s+"i18next/,
  /from\s+"@pudans\/layouts/,
];
for (const file of walk(src).filter((f) => /\.(ts|tsx)$/.test(f))) {
  const source = readFileSync(file, "utf8");
  for (const rule of forbiddenImports) {
    if (rule.test(source)) problems.push("forbidden dependency in " + relative(pkg, file) + ": " + rule);
  }
  if (/from\s+"@pudans\/(?!shared(?:\/|"))[a-z-]+\//.test(source)) {
    problems.push("deep import into another package: " + relative(pkg, file));
  }
}

if (problems.length > 0) {
  console.error("Component boundary check failed:");
  for (const problem of problems) console.error(" - " + problem);
  process.exit(1);
}
console.log("Component boundary check passed: pure component package.");
