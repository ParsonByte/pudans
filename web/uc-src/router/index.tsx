import { createBrowserRouter } from "react-router-dom";
import type { PluginModule } from "@pudans/shared";
import { registerPluginRoutes } from "../../src/router/plugin-routes";
import { BasicLayout } from "../layouts/basic-layout";
import modules from "../modules";
import { UcForbiddenPage, UcNotFoundPage } from "./fallbacks";
import { UC_BASE_PATH, UC_GROUP_LABELS, UC_ROOT_NAME, UC_SWITCH_TARGET } from "./routes.config";

export function createUcRouter(permissions: readonly string[], canSwitchToConsole: boolean) {
  const registration = registerPluginRoutes({
    modules: modules as Record<string, PluginModule>,
    platform: "uc",
    permissions,
    basePath: UC_BASE_PATH,
    rootName: UC_ROOT_NAME,
    renderLayout: (menu) => (
      <BasicLayout
        menu={menu}
        groupLabels={UC_GROUP_LABELS}
        switchTarget={canSwitchToConsole ? UC_SWITCH_TARGET : undefined}
      />
    ),
    emptyFallback: <UcForbiddenPage />,
    deniedFallback: <UcForbiddenPage />,
    notFoundFallback: <UcNotFoundPage />,
  });

  return createBrowserRouter(registration.routes, { basename: UC_BASE_PATH });
}
