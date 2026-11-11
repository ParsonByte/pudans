import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@pudans/theme/style.css";
import { AppProviders } from "@web/providers";
import { App } from "./App";

const container = document.getElementById("root");
if (!container) {
  throw new Error("Root container is missing.");
}

createRoot(container).render(
  <StrictMode>
    <AppProviders>
      <App />
    </AppProviders>
  </StrictMode>,
);
