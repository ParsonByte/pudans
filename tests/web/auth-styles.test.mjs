import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const web = new URL("../../web/", import.meta.url).pathname;
const variables = readFileSync(join(web, "packages/shared/src/theme/variables.css"), "utf8");

function authCss(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) authCss(full, out);
    else if (name.endsWith(".css")) out.push(full);
  }
  return out;
}

test("auth styles consume the shared token layer", () => {
  for (const token of ["--pd-brand-primary", "--pd-bg-canvas", "--pd-text-primary", "--pd-radius-xl", "--pd-shadow-xl"]) {
    assert.ok(variables.includes(token + ":"), token);
  }
  for (const file of authCss(join(web, "src/components/auth"))) {
    const source = readFileSync(file, "utf8");
    assert.ok(!/#[0-9a-fA-F]{3,8}/.test(source), "hex colour in " + file);
    assert.ok(!/font-family:/.test(source), "font-family in " + file);
    assert.ok(!/\d+m?s/.test(source.replace(/--[a-z0-9-]+:/g, "")), "literal motion in " + file);
  }
});

test("auth CSS modules keep the design variables local to the layout", () => {
  const layout = readFileSync(join(web, "src/components/auth/components/auth-layout.module.css"), "utf8");
  assert.ok(layout.includes("--login-card"));
  assert.ok(layout.includes("--login-brand"));
  assert.ok(layout.includes("prefers-reduced-motion"));
});
