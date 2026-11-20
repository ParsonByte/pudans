import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const ROOTS = ["src/components", "src/modules", "src/app"];
const EXCLUDED_PREFIXES = ["src/components/ui/"];
const SOURCE = /\.(ts|tsx|css)$/;
const PATTERNS = [
  {
    name: "radius",
    pattern: /(?:[A-Za-z0-9_\-[\]=\/.:]+:)?rounded-[A-Za-z0-9_\-[\]()]+/g,
  },
  {
    name: "palette",
    pattern:
      /(?:bg|text|border|ring|from|to|via|fill|stroke|shadow)-(?:white|black|zinc|slate|gray|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)(?:\/\d+)?/g,
  },
];

function collect(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? collect(path) : [path];
  });
}

const violations = [];

for (const root of ROOTS) {
  for (const file of collect(root)) {
    if (EXCLUDED_PREFIXES.some((prefix) => file.startsWith(prefix)) || !SOURCE.test(file)) {
      continue;
    }

    const lines = readFileSync(file, "utf8").split("\n");

    lines.forEach((line, index) => {
      for (const { name, pattern } of PATTERNS) {
        pattern.lastIndex = 0;
        const match = pattern.exec(line);

        if (match) {
          violations.push(`${file}:${index + 1} [${name}] ${match[0]}`);
        }
      }
    });
  }
}

if (violations.length > 0) {
  console.error(violations.join("\n"));
  process.exit(1);
}
