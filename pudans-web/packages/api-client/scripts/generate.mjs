import { spawn } from "node:child_process";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const packageDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = path.resolve(packageDir, "../../..");
const sourcePath = path.join(repoRoot, "api-docs/openapi/v3_0/aggregated.json");
const workDir = path.join(packageDir, ".openapi");
const preparedPath = path.join(workDir, "aggregated.json");
const tagMappingPath = path.join(packageDir, "openapi-tags.json");
const outputDir = path.join(packageDir, "src");

const [document, tagMapping] = await Promise.all([
  readFile(sourcePath, "utf8").then(JSON.parse),
  readFile(tagMappingPath, "utf8").then(JSON.parse),
]);

const unknownTags = new Set();
const httpMethods = new Set(["get", "put", "post", "delete", "options", "head", "patch", "trace"]);

for (const pathItem of Object.values(document.paths ?? {})) {
  for (const [method, operation] of Object.entries(pathItem)) {
    if (!httpMethods.has(method)) {
      continue;
    }
    operation.tags = (operation.tags ?? []).map((tag) => {
      const mapped = tagMapping[tag];
      if (!mapped) {
        unknownTags.add(tag);
      }
      return mapped ?? tag;
    });
  }
}

if (unknownTags.size > 0) {
  throw new Error(`Missing English tags in openapi-tags.json: ${[...unknownTags].sort().join(", ")}`);
}

await mkdir(workDir, { recursive: true });
await writeFile(preparedPath, `${JSON.stringify(document, null, 2)}\n`);
await rm(outputDir, { recursive: true, force: true });

const generator = process.platform === "win32" ? "openapi-generator-cli.cmd" : "openapi-generator-cli";

async function normalizeGeneratedFiles(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      await normalizeGeneratedFiles(entryPath);
      continue;
    }
    const contents = await readFile(entryPath, "utf8");
    const normalized = `${contents.replace(/[ \t]+$/gm, "").replace(/\n+$/u, "")}\n`;
    if (normalized !== contents) {
      await writeFile(entryPath, normalized);
    }
  }
}

await new Promise((resolve, reject) => {
  const child = spawn(
    generator,
    [
      "generate",
      "-i",
      preparedPath,
      "-g",
      "typescript-axios",
      "-c",
      path.join(packageDir, ".openapi_config.yaml"),
      "-o",
      outputDir,
      "--type-mappings=set=Array",
      "--global-property=apiDocs=false,modelDocs=false",
      "--skip-validate-spec",
    ],
    { cwd: packageDir, stdio: "inherit", shell: process.platform === "win32" }
  );
  child.on("error", reject);
  child.on("exit", (code) => {
    if (code === 0) {
      resolve();
    } else {
      reject(new Error(`openapi-generator-cli exited with code ${code}`));
    }
  });
});

await normalizeGeneratedFiles(outputDir);
