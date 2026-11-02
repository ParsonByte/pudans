import { useEffect } from "react";
import { useAuth } from "../providers/auth-provider";

/**
 * Client-side companion of the server sign-in guard: whenever the auth layout
 * mounts for a visitor that already holds a session, bounce them to the
 * console or the user centre. Reads the global auth context — no extra
 * requests. Disabled for routes that stay public (e.g. legal documents).
 */
export function useSignedInRedirect(enabled: boolean): void {
  const { status, canAccessConsole } = useAuth();

  useEffect(() => {
    if (!enabled || status !== "authenticated") {
      return;
    }
    window.location.replace(canAccessConsole ? "/console/" : "/uc/");
  }, [enabled, status, canAccessConsole]);
}
