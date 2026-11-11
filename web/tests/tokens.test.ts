import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import {
  brandPalette,
  breakpoints,
  controlHeights,
  darkTokens,
  fontSizes,
  fontWeights,
  lightTokens,
  modeTokens,
  motionDurations,
  motionEasing,
  neutralPalette,
  opacity,
  radii,
  shadows,
  transitions,
  zIndex,
} from "../packages/theme/src/tokens.ts";

const stylesDir = new URL("../packages/theme/src/", import.meta.url).pathname;
const componentsDir = new URL("../packages/components/src/", import.meta.url).pathname;
const variablesCss = readFileSync(join(stylesDir, "variables.css"), "utf8");
const darkSectionStart = variablesCss.indexOf('[data-theme="dark"]');
const darkBlock = variablesCss.slice(
  darkSectionStart,
  variablesCss.indexOf("@media", darkSectionStart),
);
const lightBlock = variablesCss.slice(0, darkSectionStart);

function cssVarIn(block: string, name: string): string | undefined {
  const match = new RegExp("--" + name + ":\\s*([^;]+);").exec(block);
  return match?.[1]?.trim();
}

function cssVar(name: string): string | undefined {
  return cssVarIn(lightBlock, name);
}

function hexToChannels(hex: string): string {
  return [1, 3, 5].map((start) => Number.parseInt(hex.slice(start, start + 2), 16)).join(" ");
}

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      walk(full, out);
    } else {
      out.push(full);
    }
  }
  return out;
}

test("brand and neutral ramps are valid hex and mirrored in CSS variables", () => {
  for (const [step, hex] of Object.entries(brandPalette)) {
    assert.match(hex, /^#[0-9a-f]{6}$/i, "brand " + step);
    assert.equal(cssVar("pd-brand-" + step), hexToChannels(hex), "brand " + step + " CSS parity");
  }
  for (const [step, hex] of Object.entries(neutralPalette)) {
    assert.match(hex, /^#[0-9a-f]{6}$/i, "neutral " + step);
    assert.equal(
      cssVar("pd-neutral-" + step),
      hexToChannels(hex),
      "neutral " + step + " CSS parity",
    );
  }
});

test("semantic tokens match between light/dark TypeScript and CSS", () => {
  for (const mode of ["light", "dark"] as const) {
    assert.deepEqual(Object.keys(modeTokens[mode]).sort(), Object.keys(modeTokens.light).sort());
  }
  assert.equal(hexToChannels(lightTokens.brand), cssVar("pd-brand-primary"), "light brand");
  assert.equal(hexToChannels(lightTokens.textPrimary), cssVar("pd-text-primary"), "light text");
  assert.equal(
    hexToChannels(darkTokens.brand),
    cssVarIn(darkBlock, "pd-brand-primary"),
    "dark brand",
  );
  assert.equal(
    hexToChannels(darkTokens.textPrimary),
    cssVarIn(darkBlock, "pd-text-primary"),
    "dark text",
  );
});

test("radius, type and duration tokens are fully mirrored in variables.css", () => {
  for (const [key, value] of Object.entries(radii)) {
    if (key === "none") continue;
    assert.equal(cssVar("pd-radius-" + (key === "2xl" ? "2xl" : key)), value, "radius " + key);
  }
  for (const [key, tuple] of Object.entries(fontSizes)) {
    const cssKey = key === "base" ? "base" : key;
    assert.equal(cssVar("pd-text-" + cssKey), tuple[0], "font size " + key);
    assert.equal(cssVar("pd-leading-" + cssKey), tuple[1].lineHeight, "line height " + key);
  }
  const durationMap: Record<string, number> = {
    fast: motionDurations.fast,
    base: motionDurations.base,
    slow: motionDurations.slow,
    shortest: motionDurations.fastest,
    shorter: motionDurations.faster,
    short: motionDurations.base,
    standard: motionDurations.slow,
    complex: motionDurations.complex,
    entering: motionDurations.entering,
    leaving: motionDurations.leaving,
    instant: motionDurations.instant,
  };
  for (const [key, ms] of Object.entries(durationMap)) {
    assert.equal(cssVar("pd-duration-" + key), ms + "ms", "duration " + key);
  }
  assert.equal(cssVar("pd-ease-standard"), motionEasing.standard);
  assert.equal(cssVar("pd-ease-emphasized"), motionEasing.emphasized);
});

test("control heights, opacity, z-index and font weights are tokenized", () => {
  for (const [key, value] of Object.entries(controlHeights)) {
    assert.equal(cssVar("pd-control-" + key), value + "px", "control " + key);
  }
  assert.equal(cssVar("pd-opacity-disabled"), String(opacity.disabled));
  assert.equal(cssVar("pd-focus-ring-width"), "3px");
  assert.equal(cssVar("pd-z-modal"), String(zIndex.modal));
  assert.equal(cssVar("pd-z-toast"), String(zIndex.toast));
  for (const [key, value] of Object.entries(fontWeights)) {
    assert.equal(cssVar("pd-font-weight-" + key), String(value), "weight " + key);
  }
  assert.ok(breakpoints.sm < breakpoints.md && breakpoints.md < breakpoints.lg);
  assert.ok(transitions.colors.includes("150ms"));
  assert.ok(transitions.colors.includes("cubic-bezier(0.4, 0, 0.2, 1)"));
  assert.ok(shadows.focus.includes("0 0 0 3px"));
});

test("every var(--pd-*) referenced by component styles is defined", () => {
  const sourceFiles = walk(componentsDir).filter(
    (file) => /\.(css|tsx|ts)$/.test(file) && !file.includes("/dist/"),
  );
  const defined = new Set<string>();
  const definitionPattern = /(--pd-[a-z0-9-]+)\s*:/g;
  for (const match of variablesCss.matchAll(definitionPattern)) {
    defined.add(match[1]);
  }
  for (const file of sourceFiles) {
    for (const match of readFileSync(file, "utf8").matchAll(definitionPattern)) {
      defined.add(match[1]);
    }
  }
  const missing: string[] = [];
  for (const file of sourceFiles) {
    const source = readFileSync(file, "utf8");
    for (const match of source.matchAll(/var\((--pd-[a-z0-9-]+)\)/g)) {
      if (!defined.has(match[1])) {
        missing.push(match[1] + " in " + file.replace(componentsDir, ""));
      }
    }
  }
  assert.deepEqual([...new Set(missing)], [], "undefined CSS variables");
});

test("reduced-motion override zeroes every duration token", () => {
  const reduced = variablesCss.slice(variablesCss.indexOf("prefers-reduced-motion"));
  for (const name of [
    "fast",
    "base",
    "slow",
    "shortest",
    "shorter",
    "short",
    "standard",
    "complex",
    "entering",
    "leaving",
    "instant",
  ]) {
    assert.match(reduced, new RegExp("--pd-duration-" + name + ":\\s*1ms"), "reduced " + name);
  }
});
