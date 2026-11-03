import { createAppRouter, AppRouter } from "@web/router";
import { routes } from "./router";

const basename = "/console";
const router = createAppRouter(routes, { basename });

export function App() {
  return <AppRouter router={router} />;
}
