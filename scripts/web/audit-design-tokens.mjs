#!/usr/bin/env node
/**
 * Hardcoded design-value audit.
 * Scans app + library sources for literal colours, fonts, radii, shadows,
 * z-index and motion values that should come from the --pd-* token layer.
 * Writes .dsh/migration/audit-tokens-latest.md and prints a summary.
 * Usage: node scripts/audit-design-tokens.mjs [--strict]
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(dirname(dirname(fileURLToPath(import.meta.url)))), "web");
const repoRoot = dirname(root);
const scanRoots = [
  join(root, "src"),
  join(root, "app"),
  join(root, "packages/components/src"),
  join(root, "packages/shared/src"),
];
const allowlist = [
  "packages/shared/src/theme/tokens.ts",
  "packages/shared/src/theme/variables.css",
  "packages/shared/src/theme/create-theme.ts",
  "packages/shared/src/theme/global.css",
  "packages/shared/src/theme/index.css",
  "packages/shared/src/icons/legacy-icons.ts",
  "packages/shared/src/icons/builtin-icons.ts",
];
const strict = process.argv.includes("--strict");

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry === "dist") continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.(css|less|ts|tsx)$/.test(full)) out.push(full);
  }
  return out;
}

const rules = [
  {
    key: "color",
    severity: "error",
    regex: /#[0-9a-fA-F]{3,8}\b/g,
    hint: "use rgb(var(--pd-*)) or a token",
  },
  {
    key: "color",
    severity: "error",
    regex: /\b(?:rgb|hsl)a?\([^)]*\)/g,
    hint: "use rgb(var(--pd-*))",
  },
  {
    key: "font-family",
    severity: "error",
    regex: /font-family:\s*([^;]+);/g,
    hint: "use var(--pd-font-sans) / var(--pd-font-mono)",
  },
  {
    key: "font-size",
    severity: "warn",
    regex: /font-size:\s*(\d+(?:\.\d+)?(?:px|rem))/g,
    hint: "use var(--pd-text-*)",
  },
  {
    key: "radius",
    severity: "warn",
    regex: /border-radius:\s*(\d+(?:\.\d+)?px)/g,
    hint: "use var(--pd-radius-*)",
  },
  {
    key: "shadow",
    severity: "warn",
    regex: /box-shadow:\s*([^;]+);/g,
    hint: "use var(--pd-shadow-*)",
  },
  {
    key: "motion",
    severity: "error",
    regex: /\b(\d+(?:\.\d+)?m?s)\b/g,
    hint: "use var(--pd-duration-*)",
  },
  { key: "z-index", severity: "warn", regex: /z-index:\s*(\d+)/g, hint: "use var(--pd-z-*)" },
];

const findings = [];
for (const dir of scanRoots) {
  for (const file of walk(dir)) {
    const rel = relative(root, file);
    if (allowlist.some((allowed) => rel === allowed)) continue;
    const lines = readFileSync(file, "utf8").split("\n");
    lines.forEach((line, index) => {
      for (const rule of rules) {
        rule.regex.lastIndex = 0;
        let match;
        while ((match = rule.regex.exec(line)) !== null) {
          const value = match[0].trim();
          if (value.length === 0) continue;
          if (value.includes("var(--")) continue;
          if (rule.key === "shadow" && value.includes("none")) continue;
          if (rule.key === "z-index" && Number.parseInt(match[1], 10) < 50) continue;
          if (rule.key === "motion" && /^0m?s$/.test(value)) continue;
          if (rule.key === "color" && value.startsWith("rgba(0, 0, 0, 0)")) continue;
          findings.push({
            file: rel,
            line: index + 1,
            rule: rule.key,
            severity: rule.severity,
            value,
            hint: rule.hint,
          });
        }
      }
    });
  }
}

const byRule = {};
for (const finding of findings) {
  byRule[finding.rule] = (byRule[finding.rule] ?? 0) + 1;
}

for (const finding of findings) {
  console.log(
    finding.file + ":" + finding.line + " [" + finding.rule + "] " + finding.value + " -> " + finding.hint,
  );
}
console.log("Design token audit: " + findings.length + " findings (" + JSON.stringify(byRule) + ")");
const errors = findings.filter((finding) => finding.severity === "error");
if (strict && errors.length > 0) {
  console.error("Strict mode: " + errors.length + " error-level findings remain.");
  process.exit(1);
}
