"use client";

import { type ReactNode, createContext, useContext } from "react";

import type { SessionView } from "@pudans-dev/api-client";

import type { Navigation } from "@/modules/workspace/lib/permission-api";

export type SessionContextValue = {
  session: SessionView;
  navigation: Navigation;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({
  value,
  children,
}: {
  value: SessionContextValue;
  children: ReactNode;
}) {
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSessionContext(): SessionContextValue {
  const value = useContext(SessionContext);

  if (!value) {
    throw new Error("Session context is only available inside an authenticated layout.");
  }

  return value;
}
