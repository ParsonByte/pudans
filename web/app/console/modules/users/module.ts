import { definePlugin, UI_PERMISSIONS } from "@pudans/shared";
import { CONSOLE_ROOT_NAME } from "../../router/routes.config";
import { ConsoleUsersView } from "./index";

export default definePlugin({
  routes: [
    {
      path: "users",
      name: "ConsoleUsersView",
      component: ConsoleUsersView,
      meta: {
        parentName: CONSOLE_ROOT_NAME,
        title: "navUsers",
        permissions: [UI_PERMISSIONS.USERS_MANAGE],
        menu: {
          name: "navUsers",
          group: "access",
          icon: "mdi:account-group-outline",
          iconActive: "mdi:account-group",
          priority: 10,
          mobile: true,
        },
      },
    },
  ],
});
