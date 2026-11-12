import { createContext } from "react";
import type { SessionView } from "../util/api-types";

export type SessionStatus = "loading" | "authenticated" | "anonymous";

export interface SessionContextValue {
  status: SessionStatus;
  session: SessionView | null;
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
}

export const SessionContext = createContext<SessionContextValue | null>(null);
