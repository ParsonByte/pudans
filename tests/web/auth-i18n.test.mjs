import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const web = new URL("../../web/", import.meta.url).pathname;
const resources = readFileSync(join(web, "src/util/i18n-resources.ts"), "utf8");

function keysFor(locale) {
  const start = resources.indexOf('"' + locale + '": {');
  assert.ok(start >= 0, locale);
  const end = locale === "en-US" ? resources.indexOf('"zh-CN": {') : resources.length;
  const block = resources.slice(start, end);
  return [...new Set([...block.matchAll(/^\s{6}([A-Za-z0-9_]+):/gm)].map((match) => match[1]))].sort();
}

function componentSources(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) componentSources(full, out);
    else if (/\.(ts|tsx)$/.test(name)) out.push(full);
  }
  return out;
}

test("English and Chinese bundles expose the same keys", () => {
  assert.deepEqual(keysFor("zh-CN"), keysFor("en-US"));
  assert.ok(keysFor("en-US").length > 100);
});

test("every literal translation key exists in the bundle", () => {
  const known = new Set(keysFor("en-US"));
  const missing = new Set();
  for (const file of [
    ...componentSources(join(web, "src/components")),
    ...componentSources(join(web, "console-src")),
    ...componentSources(join(web, "uc-src")),
    ...componentSources(join(web, "src/layouts")),
  ]) {
    const source = readFileSync(file, "utf8");
    for (const match of source.matchAll(/\bt\(["']([A-Za-z0-9_]+)["']/g)) {
      if (!known.has(match[1])) missing.add(match[1] + " in " + file);
    }
  }
  assert.deepEqual([...missing], []);
});

test("auth, setup, console and uc namespaces are bundled", () => {
  for (const namespace of ["common:", "auth:", "setup:", "console:", "uc:"]) {
    assert.ok(resources.includes("    " + namespace), namespace);
  }
});
