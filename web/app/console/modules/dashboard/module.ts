import { definePlugin, UI_PERMISSIONS } from "@pudans/shared";
import { CONSOLE_ROOT_NAME } from "../../router/routes.config";
import { ConsoleDashboardView } from "./index";

export default definePlugin({
  routes: [
    {
      path: "dashboard",
      name: "ConsoleDashboard",
      component: ConsoleDashboardView,
      meta: {
        parentName: CONSOLE_ROOT_NAME,
        title: "navOverview",
        permissions: [UI_PERMISSIONS.DASHBOARD_VIEW],
        menu: {
          name: "navOverview",
          group: "overview",
          icon: "mdi:view-dashboard-outline",
          iconActive: "mdi:view-dashboard",
          priority: 0,
          mobile: true,
        },
      },
    },
  ],
});
