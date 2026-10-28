import { writeFileSync } from "node:fs";

const content = [
  'export * from "./api";',
  'export * from "./models";',
  'export * from "./configuration";',
  'export * from "./client";',
  "",
].join("\n");

writeFileSync(new URL("../src/index.ts", import.meta.url), content);
