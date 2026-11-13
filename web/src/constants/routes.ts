/** Application roots served by the single SPA entry. */
export const AUTH_BASENAME = "/auth";

export const AUTH_ROUTES = {
  login: "/auth/",
  recovery: "/auth/recovery",
  legal: "/auth/legal",
  signedIn: "/auth/done",
} as const;

export const CONSOLE_HOME = "/console";
export const UC_HOME = "/uc";
export const SETUP_HOME = "/setup";

export type AuthRoute = (typeof AUTH_ROUTES)[keyof typeof AUTH_ROUTES];
