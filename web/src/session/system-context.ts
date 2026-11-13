import { createContext } from "react";

export type SystemStatus = "loading" | "ready" | "error";

export interface SystemContextValue {
  status: SystemStatus;
  setupRequired: boolean;
  refresh: () => Promise<void>;
}

export const SystemContext = createContext<SystemContextValue | null>(null);
