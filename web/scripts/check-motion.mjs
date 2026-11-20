import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const ROOTS = ["src/components", "src/modules", "src/app"];
const UI_ROOT = "src/components/ui";
const EXCLUDED_PREFIXES = ["src/components/ui/"];
const SOURCE = /\.(ts|tsx|css)$/;
const BRIDGE = "src/components/motion/motion-bridge.tsx";
const GLOBAL_STYLES = "src/styles/_global.css";
const ALLOWED_MOTION_PATHS = [
  "src/components/motion/",
  "src/lib/motion.ts",
  "src/lib/gsap.ts",
  "src/app/providers.tsx",
];

const CUSTOM_ANIMATION =
  /(?:^|[\s"'`])(?:[A-Za-z0-9_\-[\]=\/.:]+:)*(?:animate-in|animate-out|fade-in-\d|fade-out-\d|zoom-in-\d|zoom-out-\d|slide-in-from-|slide-out-to-)/;
const MOTION_IMPORT = /from\s+"(?:motion|motion\/react|gsap)"/;
const SLOT_PATTERN = /data-slot="([^"]+)"/g;

function collect(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? collect(path) : [path];
  });
}

function isExcluded(path) {
  return EXCLUDED_PREFIXES.some((prefix) => path.startsWith(prefix));
}

function isAllowedMotionPath(path) {
  return ALLOWED_MOTION_PATHS.some((allowed) =>
    allowed.endsWith("/") ? path.startsWith(allowed) : path === allowed,
  );
}

const violations = [];

for (const root of ROOTS) {
  for (const file of collect(root)) {
    if (isExcluded(file) || !SOURCE.test(file)) {
      continue;
    }

    const lines = readFileSync(file, "utf8").split("\n");

    lines.forEach((line, index) => {
      if (CUSTOM_ANIMATION.test(line)) {
        violations.push(`${file}:${index + 1} [custom-animation] ${line.trim()}`);
      }

      if (MOTION_IMPORT.test(line) && !isAllowedMotionPath(file)) {
        violations.push(`${file}:${index + 1} [motion-import] ${line.trim()}`);
      }
    });
  }
}

for (const file of collect("src")) {
  if (!file.endsWith(".css") || file === GLOBAL_STYLES) {
    continue;
  }

  const content = readFileSync(file, "utf8");

  if (/@keyframes|(^|[;{\s])animation\s*:/.test(content)) {
    violations.push(`${file} [custom-keyframes] move the animation to ${GLOBAL_STYLES}`);
  }
}

const bridge = readFileSync(BRIDGE, "utf8");

if (!bridge.includes("'[data-slot], [data-motion=\"enter\"]'")) {
  violations.push(`${BRIDGE} [motion-coverage] missing the generic [data-slot] entrance selector`);
}

const slots = new Set();

for (const file of collect(UI_ROOT)) {
  if (!file.endsWith(".tsx")) {
    continue;
  }

  for (const match of readFileSync(file, "utf8").matchAll(SLOT_PATTERN)) {
    slots.add(match[1]);
  }
}

for (const match of bridge.matchAll(/\[data-slot="([^"]+)"\]/g)) {
  if (!slots.has(match[1])) {
    violations.push(`${BRIDGE} [motion-coverage] unknown data-slot "${match[1]}"`);
  }
}

if (violations.length > 0) {
  console.error(violations.join("\n"));
  process.exit(1);
}

console.log(`motion coverage: ${slots.size} ui slots classified`);
