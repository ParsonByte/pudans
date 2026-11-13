import type { PluginModule } from "@pudans/shared";

const modules = import.meta.glob("./**/module.ts", {
  eager: true,
  import: "default",
}) as Record<string, PluginModule>;

export default modules;
