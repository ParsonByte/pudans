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
  const html = [];
  for (const name of readdirSync(web)) {
    if (name.endsWith(".html")) html.push(name);
  }
  assert.deepEqual(html, ["index.html"]);
});

test("src keeps the seven auth runtime directories and a single entry", () => {
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

test("components contains only auth and every page follows the agreed shape", () => {
  assert.deepEqual(dirs(join(web, "src/components")), ["auth"]);
  const auth = join(web, "src/components/auth");
  for (const required of ["index.tsx", "components", "composables", "modules"]) {
    assert.ok(readdirSync(auth).includes(required), required);
  }
  for (const page of dirs(join(auth, "modules"))) {
    const pageDir = join(auth, "modules", page);
    for (const required of ["index.tsx", "components", "composables"]) {
      assert.ok(readdirSync(pageDir).includes(required), page + "/" + required);
    }
  }
});

test("the router only reaches auth pages", () => {
  const source = readFileSync(join(web, "src/router/index.tsx"), "utf8");
  for (const route of ["LoginView", "RecoveryView", "LegalView", "SignedInView"]) {
    assert.ok(source.includes(route), route);
  }
  for (const forbidden of ["setup", "console", "lab", "uc"]) {
    assert.ok(!source.toLowerCase().includes(forbidden), forbidden);
  }
});
