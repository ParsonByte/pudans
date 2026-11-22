import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const [directory] = process.argv.slice(2);

if (!directory) {
    throw new Error("Usage: clean-generated.mjs <directory>");
}

function collect(path) {
    return readdirSync(path, { withFileTypes: true }).flatMap((entry) => {
        const child = join(path, entry.name);
        return entry.isDirectory() ? collect(child) : [child];
    });
}

for (const file of collect(directory)) {
    if (!file.endsWith(".ts")) {
        continue;
    }

    const source = readFileSync(file, "utf8");
    const cleaned = `${source.replace(/[ \t]+$/gm, "").replace(/\n+$/, "")}\n`;

    if (cleaned !== source) {
        writeFileSync(file, cleaned);
    }
}
