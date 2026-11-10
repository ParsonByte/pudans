import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { PudansColorModePreference } from "@pudans/components";
import { DEFAULT_LOCALE } from "@pudans/shared";

export const COLOR_MODE_STORAGE_KEY = "pudans:color-mode";
export const ACCENT_COLOR_STORAGE_KEY = "pudans:accent-color";
export const LOCALE_STORAGE_KEY = "pudans:locale";
export const DEFAULT_ACCENT_COLOR = "#1976d2";

function readStorage(key: string): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function readThemePreference(): PudansColorModePreference {
  const value = readStorage(COLOR_MODE_STORAGE_KEY);
  return value === "light" || value === "dark" || value === "system" ? value : "system";
}

function readAccentColor(): string {
  const value = readStorage(ACCENT_COLOR_STORAGE_KEY);
  return value && /^#[0-9a-f]{6}$/i.test(value) ? value : DEFAULT_ACCENT_COLOR;
}

function readLocale(): string {
  return readStorage(LOCALE_STORAGE_KEY) || DEFAULT_LOCALE;
}

export interface AppState {
  themePreference: PudansColorModePreference;
  accentColor: string;
  locale: string;
}

const initialState: AppState = {
  themePreference: readThemePreference(),
  accentColor: readAccentColor(),
  locale: readLocale(),
};

const appSlice = createSlice({
  name: "app",
  initialState,
  reducers: {
    setThemePreference(state, action: PayloadAction<PudansColorModePreference>) {
      state.themePreference = action.payload;
    },
    setAccentColor(state, action: PayloadAction<string>) {
      state.accentColor = action.payload;
    },
    setLocale(state, action: PayloadAction<string>) {
      state.locale = action.payload;
    },
  },
});

export const { setAccentColor, setLocale, setThemePreference } = appSlice.actions;
export const appReducer = appSlice.reducer;
export default appReducer;
