import type { PluginModule } from "./types";

/** Identity helper with Halo's definePlugin signature. */
export function definePlugin(module: PluginModule): PluginModule {
  return module;
}
