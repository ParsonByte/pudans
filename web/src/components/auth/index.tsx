import { AuthRouter } from "../../router";

/**
 * Root of the auth-only application. Public providers live in src/session so
 * every auth page shares one session snapshot and one theme/AI context.
 */
export function AuthApp() {
  return <AuthRouter />;
}
