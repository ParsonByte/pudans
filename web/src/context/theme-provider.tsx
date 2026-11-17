import { type ReactNode, createContext, useContext, useEffect, useMemo, useState } from "react";

import { getCookie, removeCookie, setCookie } from "@/lib/cookies";

export type Theme = "dark" | "light" | "system";

type ResolvedTheme = Exclude<Theme, "system">;

const DEFAULT_THEME: Theme = "system";
const THEME_COOKIE_NAME = "pudans-ui-theme";
const THEME_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

type ThemeProviderProps = {
  children: ReactNode;
  defaultTheme?: Theme;
  storageKey?: string;
};

type ThemeProviderState = {
  defaultTheme: Theme;
  resolvedTheme: ResolvedTheme;
  theme: Theme;
  setTheme: (theme: Theme) => void;
  resetTheme: () => void;
};

const ThemeContext = createContext<ThemeProviderState | null>(null);

export function ThemeProvider({
  children,
  defaultTheme = DEFAULT_THEME,
  storageKey = THEME_COOKIE_NAME,
}: ThemeProviderProps) {
  const [theme, setThemeState] = useState<Theme>(
    () => (getCookie(storageKey) as Theme) || defaultTheme,
  );

  const resolvedTheme = useMemo<ResolvedTheme>(() => {
    if (theme !== "system") {
      return theme;
    }

    if (typeof window === "undefined") {
      return "light";
    }

    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }, [theme]);

  useEffect(() => {
    const root = window.document.documentElement;
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");

    const applyTheme = (nextTheme: ResolvedTheme) => {
      root.classList.remove("light", "dark");
      root.classList.add(nextTheme);
    };

    const handleChange = () => {
      if (theme === "system") {
        applyTheme(mediaQuery.matches ? "dark" : "light");
      }
    };

    applyTheme(resolvedTheme);
    mediaQuery.addEventListener("change", handleChange);

    return () => mediaQuery.removeEventListener("change", handleChange);
  }, [theme, resolvedTheme]);

  const setTheme = (nextTheme: Theme) => {
    setCookie(storageKey, nextTheme, THEME_COOKIE_MAX_AGE);
    setThemeState(nextTheme);
  };

  const resetTheme = () => {
    removeCookie(storageKey);
    setThemeState(DEFAULT_THEME);
  };

  return (
    <ThemeContext.Provider
      value={{
        defaultTheme,
        resolvedTheme,
        resetTheme,
        theme,
        setTheme,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);

  if (!context) {
    throw new Error("useTheme 必须在 ThemeProvider 内使用");
  }

  return context;
}
