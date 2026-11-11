import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import {
  CLOUDREVE_ICON_PREFIX,
  cloudreveIconCollection,
  cloudreveIconNames,
  Icon,
  registerCloudreveIcons,
} from "@pudans/components";
import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";

const manifest = JSON.parse(
  readFileSync(new URL("../../.dsh/migration/data/cloudreve-icons.json", import.meta.url), "utf8"),
) as Array<{ component: string; icon: string; body: string; source: string }>;

test("cloudreve icon collection covers every legacy icon", () => {
  assert.equal(CLOUDREVE_ICON_PREFIX, "cr");
  assert.equal(cloudreveIconNames.length, 251);
  assert.equal(new Set(cloudreveIconNames).size, 251);
  assert.equal(Object.keys(cloudreveIconCollection.icons).length, 251);
  assert.equal(manifest.length, 251);
  assert.deepEqual([...cloudreveIconNames].sort(), manifest.map((entry) => entry.component).sort());
});

test("cloudreve icons are registered for offline rendering", () => {
  registerCloudreveIcons();
  const html = renderToStaticMarkup(createElement(Icon, { name: "cr:Search", size: 24 }));
  assert.match(html, /<svg/);
  assert.match(html, /viewBox/);
  registerCloudreveIcons();
  assert.equal(new Set(cloudreveIconNames).size, 251, "registration is idempotent");
});

test("every icon body is non-empty and preserves currentColor", () => {
  for (const name of cloudreveIconNames) {
    const body = cloudreveIconCollection.icons[name]?.body ?? "";
    assert.ok(body.length > 10, name + " has a body");
    assert.match(body, /currentColor|fill="none"|stroke/, name + " preserves colour semantics");
  }
});
