import { definePlugin } from "@pudans/shared";
import { UC_ROOT_NAME } from "../../router/routes.config";
import { UcProfileView } from "./index";

export default definePlugin({
  ucRoutes: [
    {
      path: "profile",
      name: "UcProfileView",
      component: UcProfileView,
      meta: {
        parentName: UC_ROOT_NAME,
        title: "navProfile",
        menu: {
          name: "navProfile",
          group: "account",
          icon: "mdi:account-circle-outline",
          priority: 0,
          mobile: true,
        },
      },
    },
  ],
});
