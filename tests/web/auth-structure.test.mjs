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

test("web root exposes packages, src, public, console-src, uc-src and three HTML entries", () => {
  assert.deepEqual(dirs(web).filter((name) => name !== "node_modules"), [
    "console-src",
    "packages",
    "public",
    "src",
    "uc-src",
  ]);
  const html = readdirSync(web)
    .filter((name) => name.endsWith(".html"))
    .sort();
  assert.deepEqual(html, ["auth.html", "console.html", "uc.html"]);
});

test("packages are limited to api-client, components, shared and the bundler kit", () => {
  assert.deepEqual(dirs(join(web, "packages")), [
    "api-client",
    "components",
    "shared",
    "ui-plugin-bundler-kit",
  ]);
});

test("src keeps the runtime directories and a single auth entry", () => {
  assert.deepEqual(dirs(join(web, "src")), [
    "components",
    "constants",
    "hooks",
    "layouts",
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

test("auth and setup keep the agreed module shape", () => {
  assert.deepEqual(dirs(join(web, "src/components")), ["auth", "setup"]);
  for (const app of ["auth"]) {
    const appRoot = join(web, "src/components", app);
    for (const required of ["index.tsx", "components", "composables", "modules"]) {
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

test("console and uc are separate applications with module.ts route definitions", () => {
  for (const app of ["console-src", "uc-src"]) {
    const appRoot = join(web, app);
    for (const required of ["main.tsx", "app.tsx", "layouts", "router", "modules"]) {
      assert.ok(readdirSync(appRoot).includes(required), app + "/" + required);
    }
    for (const page of dirs(join(appRoot, "modules")).filter((name) => name !== "index.ts")) {
      const pageDir = join(appRoot, "modules", page);
      for (const required of ["module.ts", "index.tsx", "components", "composables"]) {
        assert.ok(readdirSync(pageDir).includes(required), app + "/" + page + "/" + required);
      }
      const source = readFileSync(join(pageDir, "module.ts"), "utf8");
      assert.ok(source.includes("definePlugin"), app + "/" + page);
      assert.ok(source.includes("meta:"), app + "/" + page);
      assert.ok(source.includes("menu:"), app + "/" + page);
    }
  }
});

test("auth router no longer registers console or uc business routes", () => {
  const source = readFileSync(join(web, "src/router/index.tsx"), "utf8");
  assert.ok(source.includes("/auth"));
  assert.ok(source.includes("/setup"));
  assert.ok(source.includes("/forbidden"));
  assert.ok(!source.includes('path: "/console"'));
  assert.ok(!source.includes('path: "/uc"'));
});

test("no page or non-api package creates its own HTTP transport", () => {
  const roots = [
    join(web, "src"),
    join(web, "console-src"),
    join(web, "uc-src"),
    join(web, "packages/components/src"),
    join(web, "packages/shared/src"),
  ];
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

test("api-client follows the entry/src-api/src-models layout", () => {
  const apiClient = join(web, "packages/api-client");
  assert.ok(readdirSync(apiClient).includes("entry"));
  assert.ok(readdirSync(join(apiClient, "src")).includes("api"));
  assert.ok(readdirSync(join(apiClient, "src")).includes("models"));
  assert.ok(readdirSync(join(apiClient, "src")).includes("index.ts"));
  assert.ok(!readdirSync(join(apiClient, "src")).includes("client"));
  const entry = readFileSync(join(apiClient, "entry/api-client.ts"), "utf8");
  assert.ok(entry.includes("axios.create("));
  assert.ok(entry.includes("interceptors.request.use"));
  assert.ok(entry.includes("interceptors.response.use"));
  assert.ok(entry.includes("redirectToLogin"));
  assert.ok(entry.includes("redirectToForbidden"));
});
