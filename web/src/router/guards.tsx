import { useEffect, type ReactNode } from "react";
import { useAuth } from "../providers/auth-provider";

const SIGN_IN_LOCATION = "/auth/";
const USER_CENTER_LOCATION = "/uc/";
const CONSOLE_LOCATION = "/console/";

/**
 * Route guard for authenticated surfaces (console, uc).
 *
 * Unauthenticated visitors are bounced to the sign-in application; signed-in
 * accounts without console ui permissions never enter the console. While the
 * global session is still loading the subtree stays empty, so protected
 * content never flashes.
 */
export function RequireAuth({
  access = "any",
  children,
}: {
  access?: "any" | "console";
  children: ReactNode;
}) {
  const { status, canAccessConsole } = useAuth();

  const blocked =
    status === "loading" ||
    status === "anonymous" ||
    (access === "console" && !canAccessConsole);

  useEffect(() => {
    if (status === "anonymous") {
      window.location.replace(SIGN_IN_LOCATION);
      return;
    }
    if (status === "authenticated" && access === "console" && !canAccessConsole) {
      window.location.replace(USER_CENTER_LOCATION);
    }
  }, [status, access, canAccessConsole]);

  if (blocked) {
    return null;
  }
  return children;
}

/**
 * Route guard for the sign-in application: signed-in visitors are sent back to
 * the console or the user centre instead of seeing the login form.
 */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { status, canAccessConsole } = useAuth();

  useEffect(() => {
    if (status === "authenticated") {
      window.location.replace(canAccessConsole ? CONSOLE_LOCATION : USER_CENTER_LOCATION);
    }
  }, [status, canAccessConsole]);

  if (status === "authenticated") {
    return null;
  }
  return children;
}
