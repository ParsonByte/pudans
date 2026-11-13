import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const web = new URL("../../web/", import.meta.url).pathname;

function dirs(path) {
  return readdirSync(path)
    .filter((name) => !name.startsWith("."))
    .filter((name) => statSync(join(path, name)).isDirectory())
    .sort();
}

test("web root only exposes packages, src, public and one index.html", () => {
  const dirsInRoot = dirs(web).filter((name) => name !== "node_modules");
  assert.deepEqual(dirsInRoot, ["packages", "public", "src"]);
  const html = readdirSync(web).filter((name) => name.endsWith(".html"));
  assert.deepEqual(html, ["index.html"]);
});

test("packages are limited to api-client, components, shared and the bundler kit", () => {
  assert.deepEqual(dirs(join(web, "packages")), [
    "api-client",
    "components",
    "shared",
    "ui-plugin-bundler-kit",
  ]);
});

test("src keeps the seven runtime directories and a single entry", () => {
  assert.deepEqual(dirs(join(web, "src")), [
    "components",
    "constants",
    "hooks",
    "redux",
    "router",
    "session",
    "util",
  ]);
  const files = readdirSync(join(web, "src")).filter((name) =>
    statSync(join(web, "src", name)).isFile(),
  );
  assert.deepEqual(files, ["main.tsx"]);
});

test("auth, setup, console and uc keep the agreed module shape", () => {
  assert.deepEqual(dirs(join(web, "src/components")), ["auth", "console", "setup", "uc"]);
  for (const app of ["auth", "console", "setup", "uc"]) {
    const appRoot = join(web, "src/components", app);
    const requiredRoot = app === "setup" ? ["index.tsx", "modules"] : ["index.tsx", "components", "composables", "modules"];
    for (const required of requiredRoot) {
      assert.ok(readdirSync(appRoot).includes(required), app + "/" + required);
    }
    for (const page of dirs(join(appRoot, "modules"))) {
      const pageDir = join(appRoot, "modules", page);
      for (const required of ["index.tsx", "components", "composables"]) {
        assert.ok(readdirSync(pageDir).includes(required), app + "/" + page + "/" + required);
      }
    }
  }
});

test("the router mounts every application root", () => {
  const source = readFileSync(join(web, "src/router/index.tsx"), "utf8");
  for (const route of ["/auth", "/console", "/uc", "/setup", "/forbidden"]) {
    assert.ok(source.includes(route), route);
  }
});

test("no page or non-api package creates its own HTTP transport", () => {
  const roots = [join(web, "src"), join(web, "packages/components/src"), join(web, "packages/shared/src")];
  const findings = [];
  function walk(dir) {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) {
        walk(full);
      } else if (/\.(ts|tsx)$/.test(name)) {
        const source = readFileSync(full, "utf8");
        for (const pattern of [
          /axios\.create\s*\(/,
          /new\s+axios\s*\(/,
          /\bfetch\s*\(/,
          /new\s+XMLHttpRequest\s*\(/,
          /from\s+["']axios["']/,
        ]) {
          if (pattern.test(source)) findings.push(full + " matches " + pattern);
        }
      }
    }
  }
  for (const root of roots) walk(root);
  assert.deepEqual(findings, []);
});

test("the api-client owns the single Axios instance", () => {
  const source = readFileSync(join(web, "packages/api-client/src/client/http.ts"), "utf8");
  assert.match(source, /axios\.create\(/);
  const auth = readFileSync(join(web, "packages/api-client/src/client/index.ts"), "utf8");
  assert.ok(auth.includes("authApiClient"));
  assert.ok(auth.includes("consoleApiClient"));
  assert.ok(auth.includes("ucApiClient"));
});
