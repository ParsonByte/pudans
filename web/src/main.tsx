import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@pudans/shared/theme/style.css";
import { BRAND_NAME } from "./constants";
import { App } from "./components/auth";
import { AppProviders } from "./session";

document.title = BRAND_NAME;

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
