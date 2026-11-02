import { create } from "zustand";
import type { PudansColorModePreference } from "@pudans/components";
import { DEFAULT_LOCALE } from "@pudans/shared";

const COLOR_MODE_KEY = "pudans:color-mode";

function readThemePreference(): PudansColorModePreference {
  if (typeof window === "undefined") {
    return "system";
  }
  const value = window.localStorage.getItem(COLOR_MODE_KEY);
  return value === "light" || value === "dark" || value === "system" ? value : "system";
}

interface AppState {
  themePreference: PudansColorModePreference;
  locale: string;
  setThemePreference: (preference: PudansColorModePreference) => void;
  setLocale: (locale: string) => void;
}

export const useAppStore = create<AppState>((set) => ({
  themePreference: readThemePreference(),
  locale: DEFAULT_LOCALE,
  setThemePreference: (themePreference) => {
    if (typeof window !== "undefined") {
      try {
        window.localStorage.setItem(COLOR_MODE_KEY, themePreference);
      } catch {
        // Private mode / quota failures must not block theme switching.
      }
    }
    set({ themePreference });
  },
  setLocale: (locale) => set({ locale }),
}));
