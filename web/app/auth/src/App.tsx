import { createAppRouter, AppRouter } from "@web/router";
import { routes } from "./router";

const basename = "/auth";
const router = createAppRouter(routes, { basename });

export function App() {
  return <AppRouter router={router} />;
}
