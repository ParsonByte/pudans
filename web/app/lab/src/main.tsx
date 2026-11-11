import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@pudans/theme/style.css";
import { ToastProvider } from "@pudans/components";
import { PudansThemeProvider } from "@pudans/theme";
import { Gallery } from "./gallery";

const defaultPreference =
  new URLSearchParams(window.location.search).get("theme") === "dark" ? "dark" : "light";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <PudansThemeProvider defaultPreference={defaultPreference} storageKey={false}>
      <ToastProvider maxSnack={2} anchorOrigin={{ vertical: "bottom", horizontal: "left" }}>
        <Gallery />
      </ToastProvider>
    </PudansThemeProvider>
  </StrictMode>,
);
