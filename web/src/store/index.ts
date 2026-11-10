import { configureStore, createListenerMiddleware, isAnyOf } from "@reduxjs/toolkit";
import { appReducer, setAccentColor, setLocale, setThemePreference } from "./app-slice";
import { authReducer } from "./auth-slice";

const persistence = createListenerMiddleware();

persistence.startListening({
  matcher: isAnyOf(setThemePreference, setAccentColor, setLocale),
  effect: (_action, listenerApi) => {
    if (typeof window === "undefined") {
      return;
    }
    const { app } = listenerApi.getState() as RootState;
    try {
      window.localStorage.setItem("pudans:color-mode", app.themePreference);
      window.localStorage.setItem("pudans:accent-color", app.accentColor);
      window.localStorage.setItem("pudans:locale", app.locale);
    } catch {
      // Private mode or quota failures must not block preference changes.
    }
  },
});

export const store = configureStore({
  reducer: {
    app: appReducer,
    auth: authReducer,
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().prepend(persistence.middleware),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export * from "./app-slice";
export * from "./auth-slice";
