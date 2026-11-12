import type { ReactNode } from "react";
import { Provider } from "react-redux";
import { ConfirmProvider, SelectProvider, ToastProvider } from "@pudans/components";
import { PudansThemeProvider } from "@pudans/theme";
import { useAppDispatch, useAppSelector } from "../redux";
import { setThemePreference } from "../redux/app-slice";
import { store } from "../redux/store";
import "../util/i18n";
import { SessionProvider } from "./session-provider";

function ThemedTree({ children }: { children: ReactNode }) {
  const dispatch = useAppDispatch();
  const preference = useAppSelector((state) => state.app.themePreference);
  const accentColor = useAppSelector((state) => state.app.accentColor);

  return (
    <PudansThemeProvider
      preference={preference}
      accentColor={accentColor}
      onPreferenceChange={(next) => dispatch(setThemePreference(next))}
    >
      <SessionProvider>
        <ConfirmProvider>
          <SelectProvider>
            <ToastProvider maxSnack={3} anchorOrigin={{ vertical: "top", horizontal: "right" }}>
              {children}
            </ToastProvider>
          </SelectProvider>
        </ConfirmProvider>
      </SessionProvider>
    </PudansThemeProvider>
  );
}

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <Provider store={store}>
      <ThemedTree>{children}</ThemedTree>
    </Provider>
  );
}
