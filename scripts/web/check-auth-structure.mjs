#!/usr/bin/env node
/**
 * Multi-page structure gate.
 *
 * Enforces the Halo-aligned layout: separate auth/console/uc HTML entries,
 * console-src/uc-src applications, the four allowed workspace packages, the
 * api-client entry/api/models shape and the removal of self-built HTTP layers.
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

function isDir(path) {
  try {
    return statSync(path).isDirectory();
  } catch {
    return false;
  }
}

const webDirs = entries(root).filter((name) => isDir(join(root, name)));
const allowedWebDirs = new Set(["packages", "src", "public", "console-src", "uc-src", "node_modules"]);
for (const name of webDirs) {
  if (!allowedWebDirs.has(name)) problems.push("unexpected directory in web root: " + name);
}

const htmlFiles = entries(root).filter((name) => name.endsWith(".html")).sort();
const expectedHtml = ["auth.html", "console.html", "uc.html"];
if (JSON.stringify(htmlFiles) !== JSON.stringify(expectedHtml)) {
  problems.push("web must expose auth.html, console.html and uc.html, found: " + JSON.stringify(htmlFiles));
}
if (entries(root).includes("index.html")) problems.push("single-entry index.html must be removed");

const srcEntries = entries(src);
const allowedSrcDirs = ["components", "constants", "hooks", "layouts", "redux", "router", "session", "util"];
const srcDirs = srcEntries.filter((name) => isDir(join(src, name)));
const srcFiles = srcEntries.filter((name) => !isDir(join(src, name)));
for (const name of srcDirs) {
  if (!allowedSrcDirs.includes(name)) problems.push("unexpected directory in src: " + name);
}
for (const name of allowedSrcDirs) {
  if (!srcDirs.includes(name)) problems.push("missing src directory: " + name);
}
if (srcFiles.length !== 1 || srcFiles[0] !== "main.tsx") {
  problems.push("src may only contain main.tsx as its single build entry, found: " + JSON.stringify(srcFiles));
}

const packageDirs = entries(join(root, "packages")).sort();
const expectedPackages = ["api-client", "components", "shared", "ui-plugin-bundler-kit"];
if (JSON.stringify(packageDirs) !== JSON.stringify(expectedPackages)) {
  problems.push("web/packages must contain api-client/components/shared/ui-plugin-bundler-kit, found: " + JSON.stringify(packageDirs));
}

const apiClient = join(root, "packages", "api-client");
const apiEntries = entries(apiClient).sort();
for (const required of ["entry", "src", "package.json"]) {
  if (!apiEntries.includes(required)) problems.push("api-client is missing " + required);
}
if (apiEntries.includes("client")) problems.push("api-client must not contain a self-built client layer");
const apiSrc = entries(join(apiClient, "src")).sort();
if (JSON.stringify(apiSrc) !== JSON.stringify(["api", "index.ts", "models"])) {
  problems.push("api-client/src must only contain api, models and index.ts, found: " + JSON.stringify(apiSrc));
}
for (const required of ["entry/index.ts", "entry/api-client.ts"]) {
  if (!entries(join(apiClient, required.split("/")[0])).includes(required.split("/")[1])) {
    problems.push("api-client is missing " + required);
  }
}

const appComponents = entries(join(src, "components")).sort();
if (JSON.stringify(appComponents) !== JSON.stringify(["auth", "setup"])) {
  problems.push("src/components must only contain auth and setup, found: " + JSON.stringify(appComponents));
}

for (const app of ["console-src", "uc-src"]) {
  const appRoot = join(root, app);
  if (!isDir(appRoot)) {
    problems.push("missing " + app);
    continue;
  }
  for (const required of ["main.tsx", "app.tsx", "layouts", "router", "modules"]) {
    if (!entries(appRoot).includes(required)) problems.push(app + " is missing " + required);
  }
  for (const page of entries(join(appRoot, "modules"))) {
    const pageDir = join(appRoot, "modules", page);
    if (!isDir(pageDir)) continue;
    for (const required of ["module.ts", "index.tsx", "components", "composables"]) {
      if (!entries(pageDir).includes(required)) problems.push(app + " page " + page + " is missing " + required);
    }
    const moduleSource = readFileSync(join(pageDir, "module.ts"), "utf8");
    if (!moduleSource.includes("definePlugin")) problems.push(app + " page " + page + " does not use definePlugin");
    if (!moduleSource.includes("meta:")) problems.push(app + " page " + page + " has no route meta");
  }
}

const authRouter = readFileSync(join(src, "router/index.tsx"), "utf8");
for (const route of ["/auth", "/setup", "/forbidden"]) {
  if (!authRouter.includes(route)) problems.push("auth router is missing " + route);
}
if (authRouter.includes("components/console") || authRouter.includes("components/uc")) {
  problems.push("auth router still mounts console/uc pages");
}
if (authRouter.includes('path: "/console"') || authRouter.includes('path: "/uc"')) {
  problems.push("console/uc routes must not be statically registered in src/router");
}

// Self-built HTTP transports are only allowed inside the api-client package.
const bannedTransport = [/axios\.create\s*\(/, /new\s+axios\s*\(/, /\bfetch\s*\(/, /new\s+XMLHttpRequest\s*\(/];
for (const dir of [src, join(root, "console-src"), join(root, "uc-src"), join(root, "packages", "components", "src"), join(root, "packages", "shared", "src")]) {
  for (const file of walk(dir)) {
    if (!/\.(ts|tsx)$/.test(file)) continue;
    const source = readFileSync(file, "utf8");
    for (const pattern of bannedTransport) {
      if (pattern.test(source)) problems.push("self-built HTTP transport in " + relative(root, file));
    }
  }
}

const bannedBrand = ["cloud", "reve"].join("");
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
      problems.push("legacy brand reference in " + relative(dirname(root), full));
    }
  }
})(dirname(root));

function walk(dir, out = []) {
  if (!isDir(dir)) return out;
  for (const name of entries(dir)) {
    const full = join(dir, name);
    if (isDir(full)) walk(full, out);
    else out.push(full);
  }
  return out;
}

if (problems.length > 0) {
  console.error("Structure check failed:");
  for (const problem of problems) console.error(" - " + problem);
  process.exit(1);
}
console.log(
  "Structure check passed: " +
    htmlFiles.join(", ") +
    "; src/" +
    srcDirs.join(", ") +
    "; console/uc modules: " +
    entries(join(root, "console-src", "modules")).length +
    "/" +
    entries(join(root, "uc-src", "modules")).length,
);
