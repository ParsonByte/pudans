import assert from "node:assert/strict";
import test from "node:test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import * as lib from "@pudans/components";
import { PudansThemeProvider } from "@pudans/theme";

function render(component, props = {}) {
  return renderToStaticMarkup(createElement(component, props));
}

test("core components render server-side without a DOM", () => {
  const html = render(lib.EmptyState, { title: "No files", description: "Upload something" });
  assert.match(html, /No files/);
  assert.match(html, /role="status"/);

  const button = render(lib.Button, { children: "Save" });
  assert.match(button, /Save/);
  assert.match(button, /MuiButton/);

  const progress = render(lib.Progress, { value: 42, showValue: true });
  assert.match(progress, /aria-valuenow="42"/);

  const badge = render(lib.Badge, { tone: "success", children: "Enabled" });
  assert.match(badge, /Enabled/);

  const code = render(lib.CodeBlock, { code: "const answer = 42;", language: "ts" });
  assert.match(code, /answer/);

  const time = render(lib.RelativeTime, { datetime: "2026-01-01T00:00:00Z", tooltip: true });
  assert.match(time, /<time/);
});

test("the migrated offline icon collection renders synchronously", () => {
  const html = render(lib.Icon, { name: "cr:Search", size: 24 });
  assert.match(html, /<svg/);
  assert.match(html, /viewBox/);
});

test("theme switch exposes accessible semantics", () => {
  const html = renderToStaticMarkup(
    createElement(
      PudansThemeProvider,
      { defaultPreference: "light", storageKey: false, disableCssBaseline: true },
      createElement(lib.ThemeSwitch, { showLabels: true }),
    ),
  );
  assert.match(html, /aria-label="Color theme"/);
  assert.match(html, /<button/);
});
