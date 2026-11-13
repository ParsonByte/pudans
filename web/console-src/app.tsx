import { useEffect, useMemo } from "react";
import { RouterProvider } from "react-router-dom";
import { AppLoadingScreen } from "../src/session/app-loading-screen";
import { replaceWithApp } from "../src/hooks/use-auth-navigation";
import { useSession } from "../src/session/use-session";
import { useSystem } from "../src/session/use-system";
import { createConsoleRouter } from "./router";

function currentLocation(): string {
  return `${window.location.pathname}${window.location.search}`;
}

/**
 * Console bootstrap. The disallowed-console check runs before the ordinary
 * login/redirect_uri flow: an authenticated account that may not use the
 * console never registers a console module route at all and is sent to UC.
 */
export function ConsoleApp() {
  const { status, permissions, consoleAccessDisallowed } = useSession();
  const { status: systemStatus, setupRequired } = useSystem();

  const redirectHref = useMemo(() => {
    if (status === "loading" || systemStatus === "loading") {
      return null;
    }
    if (status === "authenticated" && consoleAccessDisallowed) {
      return "/uc";
    }
    if (status === "anonymous") {
      return `/auth/?redirect_uri=${encodeURIComponent(currentLocation())}`;
    }
    if (systemStatus === "ready" && setupRequired) {
      return `/setup?redirect_uri=${encodeURIComponent(currentLocation())}`;
    }
    return null;
  }, [consoleAccessDisallowed, setupRequired, status, systemStatus]);

  useEffect(() => {
    if (redirectHref) {
      replaceWithApp(redirectHref);
    }
  }, [redirectHref]);

  const ready =
    status === "authenticated" &&
    !consoleAccessDisallowed &&
    systemStatus === "ready" &&
    !setupRequired;

  const router = useMemo(() => (ready ? createConsoleRouter(permissions) : null), [permissions, ready]);

  if (!router || redirectHref) {
    return <AppLoadingScreen />;
  }

  return <RouterProvider router={router} />;
}
