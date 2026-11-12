import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { authApi } from "../util/api";
import type { SessionView } from "../util/api-types";
import { SessionContext, type SessionContextValue, type SessionStatus } from "./session-context";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionView | null>(null);
  const [status, setStatus] = useState<SessionStatus>("loading");
  const startedRef = useRef(false);

  const refresh = useCallback(async (): Promise<void> => {
    try {
      const next = await authApi.getSession();
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
      await authApi.logout();
    } finally {
      setSession(null);
      setStatus("anonymous");
    }
  }, []);

  const value = useMemo<SessionContextValue>(
    () => ({ status, session, refresh, signOut }),
    [status, session, refresh, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
