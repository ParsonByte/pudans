import { createFileRoute, redirect } from "@tanstack/react-router";

import { IdentityProviderView } from "@/modules/workspace/console";
import {
  UI_PERMISSION_AUTH_PROVIDERS_MANAGE,
  hasUiPermission,
} from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute(
  "/_authenticated/_workspace/(console)/console/identity-provider",
)({
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_AUTH_PROVIDERS_MANAGE)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: IdentityProviderView,
});
