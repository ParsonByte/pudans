import { createContext } from "react";
import type { SessionView } from "@pudans/api-client";

export type SessionStatus = "loading" | "authenticated" | "anonymous";

export interface SessionContextValue {
  status: SessionStatus;
  session: SessionView | null;
  permissions: readonly string[];
  consoleAccessDisallowed: boolean;
  hasPermission: (...permissions: string[]) => boolean;
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
}

export const SessionContext = createContext<SessionContextValue | null>(null);
