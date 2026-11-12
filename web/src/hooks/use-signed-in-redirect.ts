import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { AUTH_BASENAME, AUTH_ROUTES } from "../constants";
import { useSession } from "../session/use-session";
import { safeRedirectPath } from "../util/url";

/**
 * Signed-in visitors never see the sign-in or recovery forms. The destination
 * comes from the backend session snapshot (configurable post-login URL) and is
 * restricted to this origin before it is used.
 */
export function toRouterPath(path: string): string | null {
  if (path === AUTH_BASENAME || path === `${AUTH_BASENAME}/`) {
    return "/";
  }
  if (path.startsWith(`${AUTH_BASENAME}/`)) {
    return path.slice(AUTH_BASENAME.length);
  }
  return null;
}

export function useSignedInRedirect(enabled: boolean): void {
  const { status, session } = useSession();
  const navigate = useNavigate();

  useEffect(() => {
    if (!enabled || status !== "authenticated") {
      return;
    }
    const target = safeRedirectPath(session?.redirectUrl, AUTH_ROUTES.signedIn);
    const routerPath = toRouterPath(target);
    if (routerPath) {
      navigate(routerPath, { replace: true });
      return;
    }
    window.location.replace(target);
  }, [enabled, status, session?.redirectUrl, navigate]);
}
