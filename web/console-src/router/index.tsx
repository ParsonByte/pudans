import { createBrowserRouter } from "react-router-dom";
import type { PluginModule } from "@pudans/shared";
import { registerPluginRoutes } from "../../src/router/plugin-routes";
import { BasicLayout } from "../layouts/basic-layout";
import modules from "../modules";
import { ConsoleForbiddenPage, ConsoleNotFoundPage } from "./fallbacks";
import {
  CONSOLE_BASE_PATH,
  CONSOLE_GROUP_LABELS,
  CONSOLE_ROOT_NAME,
  CONSOLE_SWITCH_TARGET,
} from "./routes.config";

/**
 * Builds the console router once the session and uiPermissions are known.
 * The router is only created for accounts that may access the console; its
 * module routes and menu entries come from the same filtered registration.
 */
export function createConsoleRouter(permissions: readonly string[]) {
  const registration = registerPluginRoutes({
    modules: modules as Record<string, PluginModule>,
    platform: "console",
    permissions,
    basePath: CONSOLE_BASE_PATH,
    rootName: CONSOLE_ROOT_NAME,
    renderLayout: (menu) => (
      <BasicLayout
        menu={menu}
        groupLabels={CONSOLE_GROUP_LABELS}
        switchTarget={CONSOLE_SWITCH_TARGET}
      />
    ),
    emptyFallback: <ConsoleForbiddenPage />,
    deniedFallback: <ConsoleForbiddenPage />,
    notFoundFallback: <ConsoleNotFoundPage />,
  });

  return createBrowserRouter(registration.routes, { basename: CONSOLE_BASE_PATH });
}
