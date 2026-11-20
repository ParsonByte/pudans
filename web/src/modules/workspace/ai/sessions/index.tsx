import { useRouteContext } from "@tanstack/react-router";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { hasUiPermission, useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { ConversationsTab } from "./components/conversations-tab";
import { GatewaySessionsTab } from "./components/gateway-sessions-tab";

const DELETE_SESSIONS_PERMISSION = "ai-sessions:delete";

const GATEWAY_SESSIONS_PERMISSION = "ai-sessions:gateway";

export function SessionsView() {
  const { session } = useRouteContext({ from: "/_authenticated" });
  const allowedOperations = useAllowedOperations();
  const canDelete =
    allowedOperations.has("delete") ||
    hasUiPermission(session.uiPermissions, DELETE_SESSIONS_PERMISSION);
  const canViewGateway =
    allowedOperations.has("gateway") ||
    hasUiPermission(session.uiPermissions, GATEWAY_SESSIONS_PERMISSION);

  return (
    <ConsolePage title="会话">
      <Tabs defaultValue="conversations" className="gap-5">
        <TabsList variant="line" className="w-full justify-start border-b">
          <TabsTrigger value="conversations" className="flex-none">
            对话会话
          </TabsTrigger>
          {canViewGateway ? (
            <TabsTrigger value="gateway" className="flex-none">
              网关会话
            </TabsTrigger>
          ) : null}
        </TabsList>

        <TabsContent value="conversations">
          <ConversationsTab canDelete={canDelete} />
        </TabsContent>
        {canViewGateway ? (
          <TabsContent value="gateway">
            <GatewaySessionsTab canDelete={canViewGateway} />
          </TabsContent>
        ) : null}
      </Tabs>
    </ConsolePage>
  );
}
