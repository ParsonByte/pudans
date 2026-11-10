import { useEffect, type ReactNode } from "react";
import { Provider } from "react-redux";
import { ToastProvider } from "@pudans/components";
import { AppThemeProvider } from "../theme";
import { AuthProvider } from "./auth-provider";
import { store } from "../store";
import { registerPwa } from "../pwa";
import "../i18n";

export interface AppProvidersProps {
  children: ReactNode;
}

function StorefrontBoot({ children }: { children: ReactNode }) {
  useEffect(() => {
    registerPwa();
  }, []);
  return <>{children}</>;
}

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <Provider store={store}>
      <AppThemeProvider>
        <AuthProvider>
          <ToastProvider maxSnack={3} anchorOrigin={{ vertical: "top", horizontal: "right" }}>
            <StorefrontBoot>{children}</StorefrontBoot>
          </ToastProvider>
        </AuthProvider>
      </AppThemeProvider>
    </Provider>
  );
}
