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

function files(path) {
  return readdirSync(path).filter((name) => statSync(join(path, name)).isFile());
}

test("web root exposes app, packages, src and three HTML entries", () => {
  assert.deepEqual(dirs(web).filter((name) => name !== "node_modules"), ["app", "packages", "public", "src"]);
  assert.deepEqual(
    readdirSync(web).filter((name) => name.endsWith(".html")).sort(),
    ["auth.html", "console.html", "uc.html"],
  );
  assert.ok(!dirs(web).includes("console-src"));
  assert.ok(!dirs(web).includes("uc-src"));
});

test("src is shared runtime only; app owns the three applications", () => {
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
  assert.deepEqual(files(join(web, "src")), []);
  assert.deepEqual(dirs(join(web, "app")), ["auth", "console", "uc"]);
  for (const appRoot of ["auth", "console", "uc"]) {
    assert.ok(files(join(web, "app", appRoot)).includes("main.tsx"), appRoot + "/main.tsx");
  }
  for (const appRoot of ["console", "uc"]) {
    for (const required of ["app.tsx", "layouts", "router", "modules"]) {
      assert.ok(readdirSync(join(web, "app", appRoot)).includes(required), appRoot + "/" + required);
    }
  }
});

test("console and uc module routes use definePlugin", () => {
  for (const appRoot of ["console", "uc"]) {
    const appDir = join(web, "app", appRoot);
    for (const page of dirs(join(appDir, "modules")).filter((name) => name !== "index.ts")) {
      const pageDir = join(appDir, "modules", page);
      for (const required of ["module.ts", "index.tsx", "components", "composables"]) {
        assert.ok(readdirSync(pageDir).includes(required), appRoot + "/" + page + "/" + required);
      }
      const source = readFileSync(join(pageDir, "module.ts"), "utf8");
      assert.ok(source.includes("definePlugin"), appRoot + "/" + page);
      assert.ok(source.includes("meta:"), appRoot + "/" + page);
      assert.ok(source.includes("menu:"), appRoot + "/" + page);
    }
  }
});

test("auth router owns only basic routes and links to recovery/legal", () => {
  const source = readFileSync(join(web, "app/auth/router/index.tsx"), "utf8");
  for (const route of ["/auth", "/setup", "/forbidden", "signed-in"]) {
    assert.ok(source.includes(route), route);
  }
  assert.ok(!source.includes('path: "/console"'));
  assert.ok(!source.includes('path: "/uc"'));
  const login = readFileSync(
    join(web, "src/components/auth/modules/login/components/login-form.tsx"),
    "utf8",
  );
  assert.ok(login.includes('/auth/recovery'));
  assert.ok(login.includes('/auth/legal'));
});

test("api-client uses the Halo entry/src-api/src-models layout", () => {
  const pkg = join(web, "packages/api-client");
  assert.ok(dirs(pkg).includes("entry"));
  assert.deepEqual(dirs(join(pkg, "src")).filter((name) => !["api.ts", "base.ts", "common.ts", "configuration.ts", "git_push.sh", "index.ts"].includes(name)), [
    "api",
    "models",
  ]);
  assert.ok(!dirs(join(pkg, "src")).includes("client"));
  assert.ok(files(join(pkg, "entry")).includes("api-client.ts"));
  assert.ok(files(join(pkg, "entry")).includes("index.ts"));
  assert.ok(dirs(join(pkg, "entry")).includes("utils"));
  assert.ok(dirs(join(pkg, "entry")).includes("patch"));
  const apiFiles = files(join(pkg, "src", "api"));
  assert.ok(apiFiles.some((name) => /-v1alpha1-api\.ts$/.test(name)));
  assert.ok(!apiFiles.includes("auth.ts"));
  assert.ok(!apiFiles.includes("user.ts"));
});

test("no page or non-api package creates its own HTTP transport", () => {
  const roots = [
    join(web, "src"),
    join(web, "app"),
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

test("auth redirect_uri never uses the trailing slash form", () => {
  const roots = [join(web, "src"), join(web, "app")];
  const findings = [];
  function walk(dir) {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) walk(full);
      else if (/\.(ts|tsx)$/.test(name)) {
        const source = readFileSync(full, "utf8");
        if (source.includes("/auth/?")) findings.push(full);
      }
    }
  }
  for (const root of roots) walk(root);
  assert.deepEqual(findings, []);
});
