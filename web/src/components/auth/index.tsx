import { AppRouter } from "../../router";

/**
 * Auth/setup application root. Console and UC are separate multi-page entries
 * with their own dynamic routers; this app only owns the basic auth routes.
 */
export function App() {
  return <AppRouter />;
}
