import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { getLoginStatus } from "@pudans/api-client";
import { SystemContext, type SystemContextValue, type SystemStatus } from "./system-context";

export function SystemProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<SystemStatus>("loading");
  const [setupRequired, setSetupRequired] = useState(false);
  const startedRef = useRef(false);

  const refresh = useCallback(async (): Promise<void> => {
    try {
      const result = await getLoginStatus();
      setSetupRequired(result.setupRequired);
      setStatus("ready");
    } catch {
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    if (startedRef.current) {
      return;
    }
    startedRef.current = true;
    void refresh();
  }, [refresh]);

  const value = useMemo<SystemContextValue>(
    () => ({ status, setupRequired, refresh }),
    [status, setupRequired, refresh],
  );

  return <SystemContext.Provider value={value}>{children}</SystemContext.Provider>;
}
