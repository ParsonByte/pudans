import { definePlugin, UI_PERMISSIONS } from "@pudans/shared";
import { CONSOLE_ROOT_NAME } from "../../router/routes.config";
import { ConsoleAuditView } from "./index";

export default definePlugin({
  routes: [
    {
      path: "audit",
      name: "ConsoleAuditView",
      component: ConsoleAuditView,
      meta: {
        parentName: CONSOLE_ROOT_NAME,
        title: "navAudit",
        permissions: [UI_PERMISSIONS.AUDIT_VIEW],
        menu: {
          name: "navAudit",
          group: "access",
          icon: "mdi:file-document-outline",
          iconActive: "mdi:file-document",
          priority: 20,
          mobile: true,
        },
      },
    },
  ],
});
