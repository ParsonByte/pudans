#!/usr/bin/env node
/**
 * Auth-only structure gate.
 *
 * Enforces the web root, src directory allow-list, auth module shape, single
 * HTML entry and the removal of non-auth roots.
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(dirname(dirname(fileURLToPath(import.meta.url)))), "web");
const src = join(root, "src");
const problems = [];

function entries(dir) {
  return readdirSync(dir).filter((name) => !name.startsWith("."));
}

const webDirs = entries(root).filter((name) => {
  try {
    return statSync(join(root, name)).isDirectory();
  } catch {
    return false;
  }
});
const allowedWebDirs = new Set(["packages", "src", "public", "node_modules"]);
for (const name of webDirs) {
  if (!allowedWebDirs.has(name)) problems.push("unexpected directory in web root: " + name);
}

const htmlFiles = [];
(function walk(dir) {
  for (const name of entries(dir)) {
    if (name === "node_modules" || name === "dist" || name === "build") continue;
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full);
    else if (name.endsWith(".html")) htmlFiles.push(relative(root, full));
  }
})(root);
if (htmlFiles.length !== 1 || htmlFiles[0] !== "index.html") {
  problems.push("web must contain exactly one index.html, found: " + JSON.stringify(htmlFiles));
}

const srcEntries = entries(src);
const allowedSrcDirs = ["components", "constants", "hooks", "redux", "router", "session", "util"];
const srcDirs = srcEntries.filter((name) => statSync(join(src, name)).isDirectory());
const srcFiles = srcEntries.filter((name) => statSync(join(src, name)).isFile());
for (const name of srcDirs) {
  if (!allowedSrcDirs.includes(name)) problems.push("unexpected directory in src: " + name);
}
for (const name of allowedSrcDirs) {
  if (!srcDirs.includes(name)) problems.push("missing src directory: " + name);
}
if (srcFiles.length !== 1 || srcFiles[0] !== "main.tsx") {
  problems.push("src may only contain main.tsx as its single build entry, found: " + JSON.stringify(srcFiles));
}

const componentDirs = entries(join(src, "components"));
if (componentDirs.length !== 1 || componentDirs[0] !== "auth") {
  problems.push("src/components must contain only auth, found: " + JSON.stringify(componentDirs));
}

const authRoot = join(src, "components/auth");
for (const required of ["index.tsx", "components", "composables", "modules"]) {
  if (!entries(authRoot).includes(required)) problems.push("auth root is missing " + required);
}
for (const page of entries(join(authRoot, "modules"))) {
  const pageDir = join(authRoot, "modules", page);
  for (const required of ["index.tsx", "components", "composables"]) {
    if (!entries(pageDir).includes(required)) problems.push("auth page " + page + " is missing " + required);
  }
}

const routerSource = readFileSync(join(src, "router/index.tsx"), "utf8");
for (const forbidden of ["console", "setup", "lab", "/uc"]) {
  if (routerSource.includes(forbidden)) problems.push("non-auth route reference in router: " + forbidden);
}

const forbiddenPaths = ["app", "tests", "scripts", "build", ".husky"].filter((name) =>
  entries(root).includes(name),
);
for (const name of forbiddenPaths) problems.push("legacy web directory still present: " + name);

const bannedBrand = ["cloud", "reve"].join("");
const repoRoot = dirname(root);
(function scan(dir) {
  for (const name of readdirSync(dir)) {
    if (["node_modules", "dist", "build", "coverage", ".git", ".gradle"].includes(name)) continue;
    const full = join(dir, name);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      scan(full);
      continue;
    }
    if (!stat.isFile() || stat.size > 4 * 1024 * 1024) continue;
    let source;
    try {
      source = readFileSync(full, "utf8");
    } catch {
      continue;
    }
    if (source.toLowerCase().includes(bannedBrand)) {
      problems.push("legacy brand reference in " + relative(repoRoot, full));
    }
  }
})(repoRoot);

if (problems.length > 0) {
  console.error("Auth structure check failed:");
  for (const problem of problems) console.error(" - " + problem);
  process.exit(1);
}
console.log(
  "Auth structure check passed: web/" +
    webDirs.join(", ") +
    "; src/" +
    srcDirs.join(", ") +
    "; " +
    entries(join(authRoot, "modules")).length +
    " auth pages.",
);
