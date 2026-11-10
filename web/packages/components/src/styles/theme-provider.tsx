import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { CssBaseline, ThemeProvider } from "@mui/material";
import { accentChannels, buildAccentColor, createPudansTheme } from "./create-theme";
import {
  darkTokens,
  lightTokens,
  type PudansColorMode,
  type PudansColorModePreference,
} from "./tokens";

export const PUDANS_COLOR_MODE_STORAGE_KEY = "pudans:color-mode";

export interface PudansThemeContextValue {
  preference: PudansColorModePreference;
  resolvedMode: PudansColorMode;
  setPreference: (preference: PudansColorModePreference) => void;
  toggle: () => void;
}

export interface PudansThemeProviderProps {
  children: ReactNode;
  defaultPreference?: PudansColorModePreference;
  /** Controlled preference. Provide together with onPreferenceChange. */
  preference?: PudansColorModePreference;
  onPreferenceChange?: (preference: PudansColorModePreference) => void;
  /** false disables localStorage persistence. */
  storageKey?: string | false;
  /** Optional hex accent color. Derived light/dark steps feed both MUI and CSS vars. */
  accentColor?: string;
  disableCssBaseline?: boolean;
}

const ThemeContext = createContext<PudansThemeContextValue | null>(null);

function isColorModePreference(value: unknown): value is PudansColorModePreference {
  return value === "light" || value === "dark" || value === "system";
}

function getSystemMode(): PudansColorMode {
  if (typeof window === "undefined" || !window.matchMedia) {
    return "light";
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function readStoredPreference(storageKey: string | false): PudansColorModePreference | null {
  if (!storageKey || typeof window === "undefined") {
    return null;
  }
  try {
    const value = window.localStorage.getItem(storageKey);
    return isColorModePreference(value) ? value : null;
  } catch {
    return null;
  }
}

const useIsomorphicLayoutEffect = typeof window !== "undefined" ? useLayoutEffect : useEffect;

/**
 * Applies data-theme + .dark to <html> before paint and mirrors the choice to
 * localStorage. CSS variables key off both selectors, so third-party portals
 * rendered outside the React tree still receive the active theme.
 */
export function PudansThemeProvider({
  children,
  defaultPreference = "system",
  preference: controlledPreference,
  onPreferenceChange,
  storageKey = PUDANS_COLOR_MODE_STORAGE_KEY,
  accentColor,
  disableCssBaseline = false,
}: PudansThemeProviderProps) {
  const [internalPreference, setInternalPreference] = useState<PudansColorModePreference>(
    () => readStoredPreference(storageKey) ?? defaultPreference,
  );
  const [systemMode, setSystemMode] = useState<PudansColorMode>(getSystemMode);
  const preference = controlledPreference ?? internalPreference;
  const resolvedMode: PudansColorMode = preference === "system" ? systemMode : preference;

  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) {
      return;
    }
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const handleChange = (event: MediaQueryListEvent): void => {
      setSystemMode(event.matches ? "dark" : "light");
    };
    setSystemMode(query.matches ? "dark" : "light");
    query.addEventListener("change", handleChange);
    return () => query.removeEventListener("change", handleChange);
  }, []);

  useIsomorphicLayoutEffect(() => {
    if (typeof document === "undefined") {
      return;
    }
    const root = document.documentElement;
    root.dataset.theme = resolvedMode;
    root.classList.toggle("dark", resolvedMode === "dark");
    root.classList.toggle("light", resolvedMode === "light");
    root.style.colorScheme = resolvedMode;

    const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    if (meta) {
      meta.content = resolvedMode === "dark" ? darkTokens.canvas : lightTokens.canvas;
    }
  }, [resolvedMode]);

  useIsomorphicLayoutEffect(() => {
    if (typeof document === "undefined") {
      return;
    }
    const root = document.documentElement;
    const accent = buildAccentColor(accentColor, resolvedMode);
    const channels = accentChannels(accentColor);
    const pairs: Array<[string, string | null]> = accent
      ? [
          ["--pd-brand-primary", channels],
          ["--pd-brand-hover", accentChannels(accent.light)],
          ["--pd-brand-active", accentChannels(accent.dark)],
          ["--pd-brand-contrast", accentChannels(accent.contrastText)],
        ]
      : [
          ["--pd-brand-primary", null],
          ["--pd-brand-hover", null],
          ["--pd-brand-active", null],
          ["--pd-brand-contrast", null],
        ];
    for (const [property, value] of pairs) {
      if (value === null) {
        root.style.removeProperty(property);
      } else {
        root.style.setProperty(property, value);
      }
    }
  }, [accentColor, resolvedMode]);

  const setPreference = useCallback(
    (next: PudansColorModePreference): void => {
      if (controlledPreference === undefined) {
        setInternalPreference(next);
      }
      onPreferenceChange?.(next);

      if (storageKey) {
        try {
          window.localStorage.setItem(storageKey, next);
        } catch {
          // Private mode / quota failures must not block theme switching.
        }
      }
    },
    [controlledPreference, onPreferenceChange, storageKey],
  );

  const toggle = useCallback((): void => {
    setPreference(resolvedMode === "dark" ? "light" : "dark");
  }, [resolvedMode, setPreference]);

  const theme = useMemo(
    () => createPudansTheme(resolvedMode, accentColor),
    [resolvedMode, accentColor],
  );
  const contextValue = useMemo<PudansThemeContextValue>(
    () => ({ preference, resolvedMode, setPreference, toggle }),
    [preference, resolvedMode, setPreference, toggle],
  );

  return (
    <ThemeContext.Provider value={contextValue}>
      <ThemeProvider theme={theme}>
        {disableCssBaseline ? null : <CssBaseline enableColorScheme={false} />}
        {children}
      </ThemeProvider>
    </ThemeContext.Provider>
  );
}

export function usePudansTheme(): PudansThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("usePudansTheme must be used inside <PudansThemeProvider>.");
  }
  return context;
}
