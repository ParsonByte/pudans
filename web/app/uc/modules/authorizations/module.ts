import { definePlugin } from "@pudans/shared";
import { UC_ROOT_NAME } from "../../router/routes.config";
import { UcAuthorizationsView } from "./index";

export default definePlugin({
  ucRoutes: [
    {
      path: "authorizations",
      name: "UcAuthorizationsView",
      component: UcAuthorizationsView,
      meta: {
        parentName: UC_ROOT_NAME,
        title: "navAuthorizations",
        menu: {
          name: "navAuthorizations",
          group: "security",
          icon: "mdi:key-variant",
          iconActive: "mdi:key-variant",
          priority: 20,
          mobile: true,
        },
      },
    },
  ],
});
