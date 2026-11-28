import { cp, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import less from "less";

const packageDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceGlobal = path.join(packageDir, "src/global.less");
const sourceAppHeader = path.join(packageDir, "src/components/app-header/app-header.modules.less");
const outputGlobal = path.join(packageDir, "dist/global.less");
const outputAppHeader = path.join(packageDir, "dist/components/app-header/app-header.modules.less");
const outputCss = path.join(packageDir, "dist/global.css");

await mkdir(path.dirname(outputAppHeader), { recursive: true });
await cp(sourceGlobal, outputGlobal);
await cp(sourceAppHeader, outputAppHeader);

const [globalResult, appHeaderResult] = await Promise.all([
  less.render(await readFile(sourceGlobal, "utf8"), { filename: sourceGlobal }),
  less.render(await readFile(sourceAppHeader, "utf8"), { filename: sourceAppHeader }),
]);

const css = [globalResult.css, appHeaderResult.css]
  .map((content) => content.replace(/\n+$/u, ""))
  .filter(Boolean)
  .join("\n");

await writeFile(outputCss, `${css}\n`);
