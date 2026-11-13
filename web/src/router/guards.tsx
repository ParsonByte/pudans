import { useEffect, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Box } from "@mui/material";
import { Button, EmptyState } from "@pudans/components";
import { useLandingPath } from "../hooks/use-auth-navigation";
import { useSession } from "../session/use-session";
import { useSystem } from "../session/use-system";

/**
 * Bootstraps the single-page application before any route renders:
 * an uninitialized instance always lands on /setup; an initialized instance
 * never keeps showing /setup.
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
      return <Navigate to={sessionStatus === "authenticated" ? landingPath : "/auth/"} replace />;
    }
  }
  return children;
}

/** Halo setupAuthCheckGuard: protected routes require an authenticated session. */
export function RequireSession({
  access = "any",
  children,
}: {
  access?: "any" | "console";
  children: ReactNode;
}) {
  const { status, consoleAccessDisallowed } = useSession();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (status === "loading") {
      return;
    }
    if (access === "console") {
      // Disallowed-console check has priority over login and redirect_uri.
      if (consoleAccessDisallowed) {
        navigate("/uc", { replace: true });
        return;
      }
    }
    if (status === "anonymous") {
      if (location.pathname.startsWith("/auth")) {
        return;
      }
      const current = `${location.pathname}${location.search}`;
      navigate(`/auth/?redirect_uri=${encodeURIComponent(current)}`, {
        replace: true,
        state: { from: location.pathname },
      });
    }
  }, [access, consoleAccessDisallowed, location.pathname, location.search, navigate, status]);

  if (status !== "authenticated") {
    return null;
  }
  if (access === "console" && consoleAccessDisallowed) {
    return null;
  }
  return children;
}

/** Halo setupPermissionGuard: route metadata permissions gate direct URLs. */
export function RequirePermission({
  permissions,
  children,
}: {
  permissions: readonly string[];
  children: ReactNode;
}) {
  const { status, hasPermission } = useSession();

  if (status === "loading") {
    return null;
  }
  if (!hasPermission(...permissions)) {
    return <Navigate to="/forbidden" replace />;
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
  return <Navigate to={status === "authenticated" ? landingPath : "/auth/"} replace />;
}

export function ForbiddenView() {
  const { t } = useTranslation("common");
  const landingPath = useLandingPath();
  const navigate = useNavigate();

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
              navigate(landingPath, { replace: true });
            }}
          >
            {t("forbiddenBack")}
          </Button>
        }
      />
    </Box>
  );
}
