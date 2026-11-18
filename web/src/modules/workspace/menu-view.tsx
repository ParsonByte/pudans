import type { ReactNode } from "react";
import { Suspense, lazy } from "react";

import { Loader2 } from "lucide-react";

import { ConsoleFeaturePlaceholder } from "@/modules/workspace/console/feature-placeholder";
import { normalizeParameterGroupKey } from "@/modules/workspace/console/settings/lib/parameter-fields";
import type { Navigation } from "@/modules/workspace/lib/permission-api";
import { findNavigationMenu } from "@/modules/workspace/lib/workspaces";

const ConsoleOverviewView = lazy(() =>
  import("@/modules/workspace/console/overview").then((module) => ({
    default: module.ConsoleOverviewView,
  })),
);

const ConsoleSettingsView = lazy(() =>
  import("@/modules/workspace/console/settings").then((module) => ({
    default: module.ConsoleSettingsView,
  })),
);

const IdentityProviderView = lazy(() =>
  import("@/modules/workspace/console/identity-provider").then((module) => ({
    default: module.IdentityProviderView,
  })),
);

const UsersView = lazy(() =>
  import("@/modules/workspace/console/users").then((module) => ({
    default: module.UsersView,
  })),
);

const UserDetailView = lazy(() =>
  import("@/modules/workspace/console/users/detail").then((module) => ({
    default: module.UserDetailView,
  })),
);

const RolesView = lazy(() =>
  import("@/modules/workspace/console/roles").then((module) => ({
    default: module.RolesView,
  })),
);

const UserGroupsView = lazy(() =>
  import("@/modules/workspace/console/user-groups").then((module) => ({
    default: module.UserGroupsView,
  })),
);

const UserGroupDetailView = lazy(() =>
  import("@/modules/workspace/console/user-groups/detail").then((module) => ({
    default: module.UserGroupDetailView,
  })),
);

const RiskView = lazy(() =>
  import("@/modules/workspace/console/risk").then((module) => ({
    default: module.RiskView,
  })),
);

const AuditView = lazy(() =>
  import("@/modules/workspace/console/audit").then((module) => ({
    default: module.AuditView,
  })),
);

const AuditLogsView = lazy(() =>
  import("@/modules/workspace/console/audit/logs").then((module) => ({
    default: module.AuditLogsView,
  })),
);

const TracesView = lazy(() =>
  import("@/modules/workspace/console/traces").then((module) => ({
    default: module.TracesView,
  })),
);

const TraceDetailView = lazy(() =>
  import("@/modules/workspace/console/traces/detail").then((module) => ({
    default: module.TraceDetailView,
  })),
);

const JourneyDetailView = lazy(() =>
  import("@/modules/workspace/console/traces/journey-detail").then((module) => ({
    default: module.JourneyDetailView,
  })),
);

const WorkspaceIndexView = lazy(() =>
  import("@/modules/workspace/index/view").then((module) => ({
    default: module.WorkspaceIndexView,
  })),
);

type WorkspaceMenuViewProps = {
  navigation: Navigation;
  pathname: string;
  tab?: string;
  onTabChange: (tab: string) => void;
};

export function WorkspaceMenuView({
  navigation,
  pathname,
  tab,
  onTabChange,
}: WorkspaceMenuViewProps) {
  const menu = findNavigationMenu(navigation, pathname);
  if (!menu) {
    return <ConsoleFeaturePlaceholder title="页面不存在" />;
  }

  return (
    <Suspense fallback={<MenuPageFallback />}>
      {renderMenuPage({ navigation, pathname, tab, onTabChange, menu })}
    </Suspense>
  );
}

type RenderMenuPageOptions = WorkspaceMenuViewProps & {
  menu: NonNullable<ReturnType<typeof findNavigationMenu>>;
};

function renderMenuPage({
  navigation,
  pathname,
  tab,
  onTabChange,
  menu,
}: RenderMenuPageOptions): ReactNode {
  switch (menu.component) {
    case "overview":
      return <WorkspaceIndexView navigation={navigation} />;
    case "dashboard":
      return <ConsoleOverviewView />;
    case "settings":
      return (
        <ConsoleSettingsView
          activeTab={normalizeParameterGroupKey(tab)}
          onTabChange={onTabChange}
        />
      );
    case "auth-providers":
      return <IdentityProviderView />;
    case "users": {
      const relative = relativeMenuPath(menu.path, pathname);
      return relative ? <UserDetailView name={decodeURIComponent(relative)} /> : <UsersView />;
    }
    case "roles":
      return <RolesView />;
    case "risk":
      return <RiskView />;
    case "audit": {
      const relative = relativeMenuPath(menu.path, pathname);
      return relative.startsWith("logs") ? <AuditLogsView /> : <AuditView />;
    }
    case "traces": {
      const relative = relativeMenuPath(menu.path, pathname);
      if (relative.startsWith("user/")) {
        return <JourneyDetailView uid={decodeURIComponent(relative.slice("user/".length))} />;
      }
      return relative ? (
        <TraceDetailView traceFlowId={decodeURIComponent(relative)} />
      ) : (
        <TracesView />
      );
    }
    case "user-groups": {
      const relative = relativeMenuPath(menu.path, pathname);
      return relative ? (
        <UserGroupDetailView name={decodeURIComponent(relative)} />
      ) : (
        <UserGroupsView />
      );
    }
    default:
      return <ConsoleFeaturePlaceholder title={menu.label} />;
  }
}

function relativeMenuPath(menuPath: string, pathname: string): string {
  const normalized = menuPath.replace(/\/+$/, "");
  return pathname.startsWith(normalized)
    ? pathname.slice(normalized.length).replace(/^\/+/, "")
    : "";
}

function MenuPageFallback() {
  return (
    <div className="flex min-h-64 items-center justify-center">
      <Loader2 className="size-5 animate-spin text-muted-foreground" />
    </div>
  );
}
