import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { getLoginSession, logout, type SessionView } from "@pudans/api-client";
import { ALL_PERMISSIONS } from "@pudans/shared";
import { SessionContext, type SessionContextValue, type SessionStatus } from "./session-context";

function hasPermission(permissions: readonly string[], required: readonly string[]): boolean {
  if (permissions.includes(ALL_PERMISSIONS)) {
    return true;
  }
  return required.some((permission) => permissions.includes(permission));
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionView | null>(null);
  const [status, setStatus] = useState<SessionStatus>("loading");
  const startedRef = useRef(false);

  const refresh = useCallback(async (): Promise<void> => {
    try {
      const next = await getLoginSession();
      setSession(next);
      setStatus(next.authenticated ? "authenticated" : "anonymous");
    } catch {
      setSession(null);
      setStatus("anonymous");
    }
  }, []);

  useEffect(() => {
    if (startedRef.current) {
      return;
    }
    startedRef.current = true;
    void refresh();
  }, [refresh]);

  const signOut = useCallback(async (): Promise<void> => {
    try {
      await logout();
    } finally {
      setSession(null);
      setStatus("anonymous");
    }
  }, []);

  const permissions = useMemo<readonly string[]>(
    () => (session?.uiPermissions ?? []) as readonly string[],
    [session?.uiPermissions],
  );

  const value = useMemo<SessionContextValue>(
    () => ({
      status,
      session,
      permissions,
      consoleAccessDisallowed: session?.consoleAccessDisallowed === true,
      hasPermission: (...required: string[]) => hasPermission(permissions, required),
      refresh,
      signOut,
    }),
    [status, session, permissions, refresh, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
