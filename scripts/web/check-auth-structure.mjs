#!/usr/bin/env node
/**
 * Multi-page structure gate.
 *
 * Enforces the app/ layout, the reference api-client layout and the removal of
 * self-built HTTP layers.
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(dirname(dirname(fileURLToPath(import.meta.url)))), "web");
const src = join(root, "src");
const app = join(root, "app");
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

function walk(dir, out = []) {
  if (!isDir(dir)) return out;
  for (const name of entries(dir)) {
    if (["node_modules", "dist", "build", "coverage"].includes(name)) continue;
    const full = join(dir, name);
    if (isDir(full)) walk(full, out);
    else out.push(full);
  }
  return out;
}

const webDirs = entries(root).filter((name) => isDir(join(root, name)));
const allowedWebDirs = new Set(["app", "packages", "public", "src", "node_modules"]);
for (const name of webDirs) {
  if (!allowedWebDirs.has(name)) problems.push("unexpected directory in web root: " + name);
}
for (const legacy of ["console-src", "uc-src"]) {
  if (entries(root).includes(legacy)) problems.push("legacy -src app directory still present: " + legacy);
}

const htmlFiles = entries(root).filter((name) => name.endsWith(".html")).sort();
const expectedHtml = ["auth.html", "console.html", "uc.html"];
if (JSON.stringify(htmlFiles) !== JSON.stringify(expectedHtml)) {
  problems.push("web must expose auth.html, console.html and uc.html, found: " + JSON.stringify(htmlFiles));
}

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
if (srcFiles.length > 0) {
  problems.push("src is shared runtime only; unexpected root files: " + JSON.stringify(srcFiles));
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
const apiSrcEntries = entries(join(apiClient, "src")).sort();
for (const required of ["api", "models", "index.ts"]) {
  if (!apiSrcEntries.includes(required)) problems.push("api-client/src is missing " + required);
}
if (apiSrcEntries.includes("client")) problems.push("api-client/src must not contain client/");
for (const file of ["entry/api-client.ts", "entry/index.ts", "entry/utils/index.ts", "entry/patch/thumbnail.ts"]) {
  if (!entries(join(apiClient, dirname(file))).includes(file.split("/").pop())) {
    problems.push("api-client is missing " + file);
  }
}
const apiDirFiles = entries(join(apiClient, "src", "api"));
if (!apiDirFiles.some((name) => /-v1alpha1(-console|-uc|-public)?-api\.ts$/.test(name))) {
  problems.push("api-client/src/api does not contain generated resource API files");
}
if (apiDirFiles.includes("auth.ts") || apiDirFiles.includes("user.ts")) {
  problems.push("api-client/src/api contains hand-written resource modules");
}

const authRoot = join(app, "auth");
const consoleRoot = join(app, "console");
const ucRoot = join(app, "uc");
for (const [name, rootDir] of [["app/auth", authRoot], ["app/console", consoleRoot], ["app/uc", ucRoot]]) {
  if (!isDir(rootDir)) {
    problems.push("missing " + name);
    continue;
  }
  if (!entries(rootDir).includes("main.tsx")) problems.push(name + " is missing main.tsx");
}
for (const [name, appRoot] of [["app/console", consoleRoot], ["app/uc", ucRoot]]) {
  for (const required of ["app.tsx", "layouts", "router", "modules"]) {
    if (!entries(appRoot).includes(required)) problems.push(name + " is missing " + required);
  }
  for (const page of entries(join(appRoot, "modules"))) {
    const pageDir = join(appRoot, "modules", page);
    if (!isDir(pageDir)) continue;
    for (const required of ["module.ts", "index.tsx", "components", "composables"]) {
      if (!entries(pageDir).includes(required)) problems.push(name + " page " + page + " is missing " + required);
    }
    const moduleSource = readFileSync(join(pageDir, "module.ts"), "utf8");
    if (!moduleSource.includes("definePlugin")) problems.push(name + " page " + page + " does not use definePlugin");
    if (!moduleSource.includes("meta:")) problems.push(name + " page " + page + " has no route meta");
  }
}
if (!entries(join(authRoot, "router")).includes("index.tsx")) problems.push("app/auth is missing router/index.tsx");

const authRouter = readFileSync(join(authRoot, "router", "index.tsx"), "utf8");
for (const route of ["/auth", "/setup", "/forbidden", "signed-in"]) {
  if (!authRouter.includes(route)) problems.push("auth router is missing " + route);
}
if (/from\s+["'][^"']*app\/(console|uc)/.test(authRouter) || /path:\s*["']\/(console|uc)/.test(authRouter)) {
  problems.push("auth router still mounts console/uc pages");
}

const bannedTransport = [/axios\.create\s*\(/, /new\s+axios\s*\(/, /\bfetch\s*\(/, /new\s+XMLHttpRequest\s*\(/];
for (const dir of [src, app, join(root, "packages", "components", "src"), join(root, "packages", "shared", "src")]) {
  for (const file of walk(dir)) {
    if (!/\.(ts|tsx)$/.test(file)) continue;
    const source = readFileSync(file, "utf8");
    for (const pattern of bannedTransport) {
      if (pattern.test(source)) problems.push("self-built HTTP transport in " + relative(root, file));
    }
  }
}

for (const file of walk(root)) {
  if (!/\.(ts|tsx|css|html|md|json|mjs)$/.test(file)) continue;
  const source = readFileSync(file, "utf8");
  if (source.includes("/auth/?")) problems.push("trailing-slash auth redirect_uri in " + relative(root, file));
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
    "; app/auth, app/console (" +
    entries(join(consoleRoot, "modules")).filter((name) => isDir(join(consoleRoot, "modules", name))).length +
    "), app/uc (" +
    entries(join(ucRoot, "modules")).filter((name) => isDir(join(ucRoot, "modules", name))).length +
    ").",
);
