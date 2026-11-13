import { useEffect, useMemo } from "react";
import { RouterProvider } from "react-router-dom";
import { replaceWithApp } from "../../src/hooks/use-auth-navigation";
import { isLoggingOut } from "../../src/session/logout-state";
import { useSystem } from "../../src/session/use-system";
import { useSession } from "../../src/session/use-session";
import { AppLoadingScreen } from "../../src/session/app-loading-screen";
import { createUcRouter } from "./router";

function currentLocation(): string {
  return `${window.location.pathname}${window.location.search}`;
}

/** UC bootstrap: login required, console access is not. */
export function UcApp() {
  const { status, permissions, consoleAccessDisallowed } = useSession();
  const { status: systemStatus, setupRequired } = useSystem();

  const redirectHref = useMemo(() => {
    if (isLoggingOut()) {
      return null;
    }
    if (status === "loading" || systemStatus === "loading") {
      return null;
    }
    if (status === "anonymous") {
      return `/auth?redirect_uri=${encodeURIComponent(currentLocation())}`;
    }
    if (systemStatus === "ready" && setupRequired) {
      return `/setup?redirect_uri=${encodeURIComponent(currentLocation())}`;
    }
    return null;
  }, [setupRequired, status, systemStatus]);

  useEffect(() => {
    if (redirectHref && !isLoggingOut()) {
      replaceWithApp(redirectHref);
    }
  }, [redirectHref]);

  const ready = status === "authenticated" && systemStatus === "ready" && !setupRequired;
  const router = useMemo(
    () => (ready ? createUcRouter(permissions, !consoleAccessDisallowed) : null),
    [consoleAccessDisallowed, permissions, ready],
  );

  if (!router || redirectHref) {
    return <AppLoadingScreen />;
  }

  return <RouterProvider router={router} />;
}
