import type { ReactNode } from "react";
import { Provider } from "react-redux";
import { ConfirmProvider, SelectProvider, ToastProvider } from "@pudans/components";
import { AppThemeProvider } from "../theme";
import { AuthProvider } from "./auth-provider";
import { store } from "../store";
import "../i18n";

export interface AppProvidersProps {
  children: ReactNode;
}

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <Provider store={store}>
      <AppThemeProvider>
        <AuthProvider>
          <ConfirmProvider>
            <SelectProvider>
              <ToastProvider maxSnack={3} anchorOrigin={{ vertical: "top", horizontal: "right" }}>
                {children}
              </ToastProvider>
            </SelectProvider>
          </ConfirmProvider>
        </AuthProvider>
      </AppThemeProvider>
    </Provider>
  );
}
