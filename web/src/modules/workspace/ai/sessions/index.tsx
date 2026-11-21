import { useSessionContext } from "@/modules/auth/lib/session-context";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { ConsoleState } from "@/modules/workspace/console/components/console-state";
import { hasUiPermission, useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { GatewaySessionsTab } from "./components/gateway-sessions-tab";

const GATEWAY_SESSIONS_PERMISSION = "ai-sessions:gateway";

export function SessionsView() {
  const { session } = useSessionContext();
  const allowedOperations = useAllowedOperations();
  const canViewGateway =
    allowedOperations.has("gateway") ||
    hasUiPermission(session.uiPermissions, GATEWAY_SESSIONS_PERMISSION);

  return (
    <ConsolePage title="会话">
      {canViewGateway ? (
        <GatewaySessionsTab canDelete={canViewGateway} />
      ) : (
        <ConsoleState
          wrapper
          variant="permission"
          title="没有查看网关会话的权限"
          description="请联系管理员授予“网关会话排障”操作。"
        />
      )}
    </ConsolePage>
  );
}
