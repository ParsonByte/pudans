import type { ReactNode } from "react";
import { ThemeProvider as StyledThemeProvider } from "styled-components";
import { PudansThemeProvider, usePudansTheme } from "@pudans/components";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { setAccentColor, setThemePreference } from "../store/app-slice";

export interface AppThemeProviderProps {
  children: ReactNode;
}

/**
 * Bridges the Redux-persisted appearance preferences into MUI, the CSS variable
 * layer and the styled-components theme used by embedded editors.
 */
function StyledBridge({ children }: { children: ReactNode }) {
  const { resolvedMode } = usePudansTheme();
  const accentColor = useAppSelector((state) => state.app.accentColor);
  return (
    <StyledThemeProvider theme={{ mode: resolvedMode, accentColor }}>
      {children}
    </StyledThemeProvider>
  );
}

export function AppThemeProvider({ children }: AppThemeProviderProps) {
  const dispatch = useAppDispatch();
  const preference = useAppSelector((state) => state.app.themePreference);
  const accentColor = useAppSelector((state) => state.app.accentColor);

  return (
    <PudansThemeProvider
      preference={preference}
      accentColor={accentColor}
      onPreferenceChange={(next) => dispatch(setThemePreference(next))}
    >
      <StyledBridge>{children}</StyledBridge>
    </PudansThemeProvider>
  );
}

export interface AppearanceActions {
  setAccentColor: (color: string) => void;
}

export function useAppearanceActions(): AppearanceActions {
  const dispatch = useAppDispatch();
  return {
    setAccentColor: (color) => dispatch(setAccentColor(color)),
  };
}
