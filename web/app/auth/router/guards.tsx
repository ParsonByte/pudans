import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Navigate, useLocation } from "react-router-dom";
import { Box } from "@mui/material";
import { Button, EmptyState } from "@pudans/components";
import { navigateToApp, useLandingPath } from "../../../src/hooks/use-auth-navigation";
import { useSession } from "../../../src/session/use-session";
import { useSystem } from "../../../src/session/use-system";

/**
 * Boots the auth app before any route renders: an uninitialized instance
 * always lands on /setup, an initialized instance never keeps showing it.
 */
export function AppBootstrap({ children }: { children: ReactNode }) {
  const { status: systemStatus, setupRequired } = useSystem();
  const { status: sessionStatus } = useSession();
  const location = useLocation();
  const landingPath = useLandingPath();

  if (systemStatus === "loading") {
    return null;
  }
  if (systemStatus === "ready") {
    if (setupRequired && !location.pathname.startsWith("/setup")) {
      return <Navigate to={`/setup?redirect_uri=${encodeURIComponent(landingPath)}`} replace />;
    }
    if (!setupRequired && location.pathname.startsWith("/setup")) {
      return <Navigate to={sessionStatus === "authenticated" ? landingPath : "/auth"} replace />;
    }
  }
  return children;
}

/** Authenticated visitors never see the sign-in or recovery forms. */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { status } = useSession();
  const landingPath = useLandingPath();

  if (status === "loading") {
    return null;
  }
  if (status === "authenticated") {
    return <Navigate to={landingPath} replace />;
  }
  return children;
}

export function RootRedirect() {
  const { status } = useSession();
  const landingPath = useLandingPath();
  return <Navigate to={status === "authenticated" ? landingPath : "/auth"} replace />;
}

export function ForbiddenView() {
  const { t } = useTranslation("common");
  const landingPath = useLandingPath();

  return (
    <Box sx={{ display: "grid", placeItems: "center", minHeight: "100svh" }}>
      <EmptyState
        variant="permission"
        title={t("forbiddenTitle")}
        description={t("forbiddenDescription")}
        action={
          <Button
            variant="primary"
            onClick={() => {
              navigateToApp(landingPath);
            }}
          >
            {t("forbiddenBack")}
          </Button>
        }
      />
    </Box>
  );
}

export function NotFoundView() {
  const { t } = useTranslation("common");
  return (
    <Box sx={{ display: "grid", placeItems: "center", minHeight: "100svh" }}>
      <EmptyState
        variant="not-found"
        title={t("notFoundTitle")}
        description={t("notFoundDescription")}
        action={
          <Button variant="primary" onClick={() => navigateToApp("/auth")}>
            {t("backToLogin")}
          </Button>
        }
      />
    </Box>
  );
}
