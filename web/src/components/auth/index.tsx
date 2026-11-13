import { AppRouter } from "../../router";

/**
 * Root of the single-page application. Auth, setup, console and UC routes all
 * share one router, one session snapshot and one centralized api-client.
 */
export function App() {
  return <AppRouter />;
}
