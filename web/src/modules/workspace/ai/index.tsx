import { useLocation, useRouteContext } from "@tanstack/react-router";

import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { findNavigationMenu } from "@/modules/workspace/lib/workspaces";

export function AiConsolePage() {
  const pathname = useLocation({ select: (location) => location.pathname });
  const { navigation } = useRouteContext({ from: "/_authenticated" });
  const menu = findNavigationMenu(navigation, pathname);

  return (
    <ConsolePage title={menu?.label ?? ""}>
      <ConsoleSection>
        <ConsoleEmpty>该页面的配置能力尚未提供。</ConsoleEmpty>
      </ConsoleSection>
    </ConsolePage>
  );
}
