import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@pudans/theme/style.css";
import { BRAND_NAME } from "./constants";
import { AuthApp } from "./components/auth";
import { AppProviders } from "./session";

document.title = `${BRAND_NAME} · Authentication`;

const container = document.getElementById("root");
if (!container) {
  throw new Error("Root container is missing.");
}

createRoot(container).render(
  <StrictMode>
    <AppProviders>
      <AuthApp />
    </AppProviders>
  </StrictMode>,
);
