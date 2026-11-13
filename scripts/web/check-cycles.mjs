#!/usr/bin/env node
/**
 * Static circular-dependency check for the component library and app source.
 * Usage: node scripts/check-cycles.mjs
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(dirname(dirname(fileURLToPath(import.meta.url)))), "web");
const roots = [
  join(root, "packages/components/src"),
  join(root, "packages/shared/src"),
  join(root, "src"),
];

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry === "dist") continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.(ts|tsx|js|jsx|mjs)$/.test(full)) out.push(full);
  }
  return out;
}

function resolveImport(fromFile, specifier) {
  if (!specifier.startsWith(".")) return null;
  const base = normalize(join(dirname(fromFile), specifier));
  const candidates = [
    base,
    base + ".ts",
    base + ".tsx",
    base + ".js",
    base + ".jsx",
    base + ".mjs",
    join(base, "index.ts"),
    join(base, "index.tsx"),
  ];
  for (const candidate of candidates) {
    try {
      if (statSync(candidate).isFile()) return candidate;
    } catch {
      /* try next */
    }
  }
  return null;
}

const files = roots.flatMap((dir) => {
  try {
    return walk(dir);
  } catch {
    return [];
  }
});
const graph = new Map();
for (const file of files) {
  const source = readFileSync(file, "utf8");
  const imports = [];
  for (const match of source.matchAll(/(?:from\s+|import\s+)["']([^"']+)["']/g)) {
    const resolved = resolveImport(file, match[1]);
    if (resolved) imports.push(resolved);
  }
  graph.set(file, imports);
}

const state = new Map();
const stack = [];
const cycles = [];

function visit(node) {
  const current = state.get(node) ?? 0;
  if (current === 1) {
    const start = stack.indexOf(node);
    cycles.push(
      stack
        .slice(start)
        .concat(node)
        .map((file) => file.replace(root + "/", "")),
    );
    return;
  }
  if (current === 2) return;
  state.set(node, 1);
  stack.push(node);
  for (const next of graph.get(node) ?? []) visit(next);
  stack.pop();
  state.set(node, 2);
}

for (const node of graph.keys()) visit(node);

if (cycles.length > 0) {
  console.error("Circular dependencies detected:");
  for (const cycle of cycles) console.error(" - " + cycle.join(" -> "));
  process.exit(1);
}
console.log(
  "No circular dependencies across " + graph.size + " modules (" + roots.length + " roots).",
);
