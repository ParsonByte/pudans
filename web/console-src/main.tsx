import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@pudans/shared/theme/style.css";
import { BRAND_NAME } from "../src/constants";
import { AppProviders } from "../src/session";
import { ConsoleApp } from "./app";

document.title = `${BRAND_NAME} Console`;

const container = document.getElementById("root");
if (!container) {
  throw new Error("Console root container is missing.");
}

createRoot(container).render(
  <StrictMode>
    <AppProviders>
      <ConsoleApp />
    </AppProviders>
  </StrictMode>,
);
