import { definePlugin } from "@pudans/shared";
import { UC_ROOT_NAME } from "../../router/routes.config";
import { UcSecurityView } from "./index";

export default definePlugin({
  ucRoutes: [
    {
      path: "security",
      name: "UcSecurityView",
      component: UcSecurityView,
      meta: {
        parentName: UC_ROOT_NAME,
        title: "navSecurity",
        menu: {
          name: "navSecurity",
          group: "security",
          icon: "mdi:shield-lock-outline",
          iconActive: "mdi:shield-lock",
          priority: 10,
          mobile: true,
        },
      },
    },
  ],
});
