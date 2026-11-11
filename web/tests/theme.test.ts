import assert from "node:assert/strict";
import test from "node:test";
import {
  buildAccentColor,
  createPudansTheme,
  darkTokens,
  lightTokens,
  motionDurations,
} from "@pudans/components";

test("light and dark themes derive from the token palette", () => {
  const light = createPudansTheme("light");
  const dark = createPudansTheme("dark");
  assert.equal(light.palette.mode, "light");
  assert.equal(dark.palette.mode, "dark");
  assert.notEqual(light.palette.background.default, dark.palette.background.default);
  assert.equal(light.palette.background.default, lightTokens.canvas);
  assert.equal(dark.palette.background.default, darkTokens.canvas);
  assert.equal(light.shape.borderRadius, 12);
  assert.equal(light.typography.fontFamily, '"Roboto", "Helvetica", "Arial", sans-serif');
  assert.equal(light.transitions.duration.short, motionDurations.base);
  assert.equal(light.transitions.duration.enteringScreen, motionDurations.entering);
  assert.equal(light.transitions.easing.easeInOut, "cubic-bezier(0.4, 0, 0.2, 1)");
  assert.equal(light.zIndex.modal, 1400);
  assert.equal(light.zIndex.tooltip, 1800);
});

test("accent colour overrides the primary ramp and normalizes hex", () => {
  const theme = createPudansTheme("light", "#3366FF");
  assert.equal(theme.palette.primary.main, "#3366ff");
  assert.ok(theme.palette.primary.light);
  assert.ok(theme.palette.primary.dark);
  const accent = buildAccentColor("#36f", "light");
  assert.equal(accent?.main, "#3366ff");
  assert.equal(accent?.contrastText, "#ffffff");
});

test("invalid accent colours are ignored", () => {
  const theme = createPudansTheme("light", "not-a-color");
  assert.equal(theme.palette.primary.main, lightTokens.brand);
});
