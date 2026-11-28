import { cp, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import less from "less";

const packageDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceGlobal = path.join(packageDir, "src/global.less");
const sourceAppHeader = path.join(packageDir, "src/components/app-header/app-header.modules.less");
const sourceAppLayout = path.join(packageDir, "src/layouts/app-layout/app-layout.modules.less");
const outputGlobal = path.join(packageDir, "dist/global.less");
const outputAppHeader = path.join(packageDir, "dist/components/app-header/app-header.modules.less");
const outputAppLayout = path.join(packageDir, "dist/layouts/app-layout/app-layout.modules.less");
const outputCss = path.join(packageDir, "dist/global.css");

await mkdir(path.dirname(outputAppHeader), { recursive: true });
await mkdir(path.dirname(outputAppLayout), { recursive: true });
await cp(sourceGlobal, outputGlobal);
await cp(sourceAppHeader, outputAppHeader);
await cp(sourceAppLayout, outputAppLayout);

const [globalResult, appHeaderResult, appLayoutResult] = await Promise.all([
  less.render(await readFile(sourceGlobal, "utf8"), { filename: sourceGlobal }),
  less.render(await readFile(sourceAppHeader, "utf8"), { filename: sourceAppHeader }),
  less.render(await readFile(sourceAppLayout, "utf8"), { filename: sourceAppLayout }),
]);

const css = [globalResult.css, appHeaderResult.css, appLayoutResult.css]
  .map((content) => content.replace(/\n+$/u, ""))
  .filter(Boolean)
  .join("\n");

await writeFile(outputCss, `${css}\n`);
