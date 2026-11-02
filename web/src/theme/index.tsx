import type { ReactNode } from "react";
import { PudansThemeProvider } from "@pudans/components";
import { useAppStore } from "../store/app-store";

export interface AppThemeProviderProps {
  children: ReactNode;
}

/** App-level bridge between the persisted Zustand preference and UI tokens. */
export function AppThemeProvider({ children }: AppThemeProviderProps) {
  const preference = useAppStore((state) => state.themePreference);
  const setPreference = useAppStore((state) => state.setThemePreference);

  return (
    <PudansThemeProvider
      preference={preference}
      onPreferenceChange={setPreference}
    >
      {children}
    </PudansThemeProvider>
  );
}
